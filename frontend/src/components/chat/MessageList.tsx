'use client'

import { useEffect, useRef } from 'react'
import MessageBubble, { StreamingBubble } from './MessageBubble'
import type { AgentMessage } from '@/types'

interface MessageListProps {
  messages: AgentMessage[]
  isStreaming: boolean
  streamingContent: string
}

export default function MessageList({
  messages,
  isStreaming,
  streamingContent,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length, streamingContent])

  return (
    <div className="flex-1 overflow-y-auto px-6 py-4">
      <div className="space-y-3">
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {isStreaming && <StreamingBubble content={streamingContent} />}
      </div>
      <div ref={bottomRef} />
    </div>
  )
}
