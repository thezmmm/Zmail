'use client'

import { useState, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getToken } from '@/lib/auth'
import type { AgentMessage, ChatRequest } from '@/types'

export function useChat(sessionId: string | null) {
  const qc = useQueryClient()
  const [isStreaming, setIsStreaming] = useState(false)
  const [streamingContent, setStreamingContent] = useState('')
  const abortRef = useRef<AbortController | null>(null)

  const sendMessage = useCallback(
    async (message: string) => {
      if (!sessionId || isStreaming || !message.trim()) return

      // Optimistically add the user's message to the cache
      const userMsg: AgentMessage = {
        id: crypto.randomUUID(),
        sessionId,
        role: 'USER',
        content: message,
        createdAt: new Date().toISOString(),
      }
      qc.setQueryData<AgentMessage[]>(['messages', sessionId], prev => [
        ...(prev ?? []),
        userMsg,
      ])

      setIsStreaming(true)
      setStreamingContent('')
      abortRef.current = new AbortController()

      try {
        const baseUrl =
          process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'
        const body: ChatRequest = { sessionId, message }
        const res = await fetch(`${baseUrl}/agent/chat`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getToken()}`,
          },
          body: JSON.stringify(body),
          signal: abortRef.current.signal,
        })

        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let accumulated = ''
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          // SSE messages are separated by double newlines
          const messages = buffer.split('\n\n')
          buffer = messages.pop() ?? ''

          for (const msg of messages) {
            let eventType = ''
            let eventData = ''
            for (const line of msg.split('\n')) {
              if (line.startsWith('event: ')) eventType = line.slice(7).trim()
              else if (line.startsWith('data: ')) eventData = line.slice(6)
            }

            if (eventType === 'token' && eventData) {
              accumulated += eventData
              setStreamingContent(accumulated)
            } else if (eventType === 'done') {
              // Commit the completed assistant message
              const assistantMsg: AgentMessage = {
                id: crypto.randomUUID(),
                sessionId,
                role: 'ASSISTANT',
                content: accumulated,
                createdAt: new Date().toISOString(),
              }
              qc.setQueryData<AgentMessage[]>(['messages', sessionId], prev => [
                ...(prev ?? []),
                assistantMsg,
              ])
              qc.invalidateQueries({ queryKey: ['sessions'] })
            }
          }
        }
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          console.error('Chat stream error', err)
        }
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
