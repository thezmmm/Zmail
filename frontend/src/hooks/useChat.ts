'use client'

import { useState, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getToken } from '@/lib/auth'
import type { AgentMessage, ChatRequest } from '@/types'

/**
 * Parse one SSE message block (content between two \n\n separators).
 *
 * Spring MVC's SseEmitter writes "event:token\ndata:Hello\n\n" — NO space
 * after the colon. The SSE spec allows both "field:value" and "field: value"
 * (leading space is stripped). We use indexOf(':') to handle both forms.
 */
function parseSseBlock(raw: string): { eventType: string; eventData: string } {
  let eventType = ''
  const dataParts: string[] = []
  for (const rawLine of raw.split('\n')) {
    const line = rawLine.replace(/\r$/, '') // normalise \r\n → \n
    const colonIdx = line.indexOf(':')
    if (colonIdx === -1) continue
    const field = line.slice(0, colonIdx)
    const value = line.slice(colonIdx + 1)
    if (field === 'event') {
      // Event names never contain meaningful whitespace; trim for safety
      eventType = value.trim()
    } else if (field === 'data') {
      // Spring's SseEmitter writes "data:<content>" with NO separator space,
      // so any leading space here is part of the actual token text (e.g. " world").
      // Do NOT strip it — stripping would collapse word boundaries in English output.
      dataParts.push(value)
    }
  }
  return { eventType, eventData: dataParts.join('\n') }
}

export function useChat(sessionId: string | null) {
  const qc = useQueryClient()
  const [isStreaming, setIsStreaming]         = useState(false)
  const [streamingContent, setStreamingContent] = useState('')
  const abortRef = useRef<AbortController | null>(null)

  const sendMessage = useCallback(
    async (message: string) => {
      if (!sessionId || isStreaming || !message.trim()) return

      // Optimistically add user message to cache
      qc.setQueryData<AgentMessage[]>(['messages', sessionId], prev => [
        ...(prev ?? []),
        {
          id: crypto.randomUUID(),
          sessionId,
          role: 'USER',
          content: message,
          createdAt: new Date().toISOString(),
        },
      ])

      setIsStreaming(true)
      setStreamingContent('')
      abortRef.current = new AbortController()

      let accumulated = ''
      let committed   = false

      /** Commit the accumulated assistant reply exactly once. */
      function commitAssistant() {
        if (committed || !accumulated) return
        committed = true
        qc.setQueryData<AgentMessage[]>(['messages', sessionId!], prev => [
          ...(prev ?? []),
          {
            id: crypto.randomUUID(),
            sessionId: sessionId!,
            role: 'ASSISTANT',
            content: accumulated,
            createdAt: new Date().toISOString(),
          },
        ])
        qc.invalidateQueries({ queryKey: ['sessions'] })
      }

      try {
        const baseUrl =
          process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'
        const res = await fetch(`${baseUrl}/agent/chat`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getToken()}`,
          },
          body: JSON.stringify({ sessionId, message } satisfies ChatRequest),
          signal: abortRef.current.signal,
        })

        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

        const reader  = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const blocks = buffer.split('\n\n')
          // Keep the last (potentially incomplete) block in the buffer
          buffer = blocks.pop() ?? ''

          for (const block of blocks) {
            const { eventType, eventData } = parseSseBlock(block)
            if (eventType === 'token' && eventData) {
              accumulated += eventData
              setStreamingContent(accumulated)
            } else if (eventType === 'done') {
              commitAssistant()
            }
          }
        }

        // Flush remaining buffer — handles missing trailing \n\n on last event
        if (buffer.trim()) {
          const { eventType, eventData } = parseSseBlock(buffer)
          if (eventType === 'token' && eventData) accumulated += eventData
          if (eventType === 'done') commitAssistant()
        }

        // Fallback commit: stream closed without a done event (e.g. backend error)
        commitAssistant()

      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          console.error('Chat stream error', err)
        }
        // Preserve partial response on unexpected disconnect
        commitAssistant()
      } finally {
        setIsStreaming(false)
        setStreamingContent('')
      }
    },
    [sessionId, isStreaming, qc],
  )

  const abort = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  return { sendMessage, isStreaming, streamingContent, abort }
}
