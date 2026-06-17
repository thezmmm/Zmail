'use client'

import { useEffect, useRef } from 'react'
import MessageBubble, { StreamingBubble } from './MessageBubble'
import type { AgentMessage } from '@/types'

interface MessageListProps {
  messages: AgentMessage[]
  isStreaming: boolean
  streamingContent: string
  streamingDone: boolean
  toolStatus: string | null
}

export default function MessageList({
  messages,
  isStreaming,
  streamingContent,
  streamingDone,
  toolStatus,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null)

  // Smooth-scroll when a new message is added (user send or assistant commit).
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  // Instant-scroll on every streaming token to keep up without animation stutter.
  useEffect(() => {
    if (!streamingContent) return
    bottomRef.current?.scrollIntoView({ behavior: 'instant' as ScrollBehavior })
  }, [streamingContent])

  return (
    <div className="flex-1 overflow-y-auto px-6 py-4">
      <div className="space-y-3">
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {isStreaming && !streamingDone && <StreamingBubble content={streamingContent} toolStatus={toolStatus} />}
      </div>
      <div ref={bottomRef} />
    </div>
  )
}
