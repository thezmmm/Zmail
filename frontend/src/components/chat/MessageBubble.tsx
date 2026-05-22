import { cn } from '@/lib/cn'
import type { AgentMessage } from '@/types'

export default function MessageBubble({ message }: { message: AgentMessage }) {
  const isUser = message.role === 'USER'
  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[75%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
          isUser
            ? 'rounded-br-sm bg-blue-600 text-white'
            : 'rounded-bl-sm bg-gray-800 text-gray-200',
        )}
      >
        {message.content}
      </div>
    </div>
  )
}

/** Streaming bubble shown while the assistant is typing. */
export function StreamingBubble({ content }: { content: string }) {
  return (
    <div className="flex justify-start">
      <div className="max-w-[75%] rounded-2xl rounded-bl-sm bg-gray-800 px-4 py-2.5 text-sm leading-relaxed text-gray-200">
        {content}
        <span className="ml-0.5 inline-block h-3.5 w-0.5 animate-pulse bg-gray-400 align-middle" />
      </div>
    </div>
  )
}
