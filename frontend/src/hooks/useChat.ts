'use client'

import { useState, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getToken } from '@/lib/auth'
import { refreshTokenIfNeeded, handleUnauthorized } from '@/lib/api'
import type { AgentMessage, AgentSession, ChatRequest, EmailRef } from '@/types'

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
      eventType = value.trim()
    } else if (field === 'data') {
      dataParts.push(value)
    }
  }
  return { eventType, eventData: dataParts.join('\n') }
}

export function useChat(sessionId: string | null) {
  const qc = useQueryClient()
  const [isStreaming, setIsStreaming]           = useState(false)
  const [streamingContent, setStreamingContent] = useState('')
  const [streamingDone, setStreamingDone]       = useState(false)
  const [toolStatus, setToolStatus]             = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const sendMessage = useCallback(
    async (message: string, emails?: EmailRef[]) => {
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

      // Proactively refresh JWT before opening the SSE stream — raw fetch bypasses
      // the axios interceptors, so we share the same refresh helper as api.ts.
      await refreshTokenIfNeeded()

      setIsStreaming(true)
      setStreamingContent('')
      setToolStatus(null)
      abortRef.current = new AbortController()

      let accumulated = ''
      let committed   = false

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
          body: JSON.stringify({
            sessionId,
            message,
            ...(emails && emails.length > 0 && { emails }),
          } satisfies ChatRequest),
          signal: abortRef.current.signal,
        })

        if (res.status === 401) {
          handleUnauthorized()
          return
        }
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

        const reader  = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const blocks = buffer.split('\n\n')
          buffer = blocks.pop() ?? ''

          for (const block of blocks) {
            const { eventType, eventData } = parseSseBlock(block)
            if (eventType === 'token' && eventData) {
              accumulated += eventData
              setStreamingContent(accumulated)
              setToolStatus(null)
            } else if (eventType === 'tool_start') {
              setToolStatus(eventData)
            } else if (eventType === 'done') {
              commitAssistant()
              setStreamingContent('')
              setStreamingDone(true)
            } else if (eventType === 'error') {
              accumulated = eventData || '请求失败，请稍后重试。'
              commitAssistant()
            } else if (eventType === 'session_title' && eventData.trim()) {
              const title = eventData.trim()
              qc.setQueryData<AgentSession[]>(['sessions'], prev =>
                prev?.map(s => s.id === sessionId ? { ...s, title } : s) ?? []
              )
            }
          }
        }

        if (buffer.trim()) {
          const { eventType, eventData } = parseSseBlock(buffer)
          if (eventType === 'token' && eventData) accumulated += eventData
          if (eventType === 'done') commitAssistant()
        }

        commitAssistant()

      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          console.error('Chat stream error', err)
        }
        commitAssistant()
      } finally {
        setIsStreaming(false)
        setStreamingContent('')
        setStreamingDone(false)
        setToolStatus(null)
        // Refetch after the full SSE stream (including session_title) is consumed,
        // so the sessions list always sees the final title written to DB.
        qc.invalidateQueries({ queryKey: ['sessions'] })
      }
    },
    [sessionId, isStreaming, qc],
  )

  const abort = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  return { sendMessage, isStreaming, streamingContent, streamingDone, toolStatus, abort }
}