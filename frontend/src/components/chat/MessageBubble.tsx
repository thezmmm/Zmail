import ReactMarkdown from 'react-markdown'
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
        {isUser ? (
          <span className="whitespace-pre-wrap">{message.content}</span>
        ) : (
          <ReactMarkdown
            components={{
              p:      ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
              ul:     ({ children }) => <ul className="mb-2 list-disc pl-4 space-y-0.5">{children}</ul>,
              ol:     ({ children }) => <ol className="mb-2 list-decimal pl-4 space-y-0.5">{children}</ol>,
              li:     ({ children }) => <li>{children}</li>,
              strong: ({ children }) => <strong className="font-semibold text-gray-100">{children}</strong>,
              em:     ({ children }) => <em className="italic">{children}</em>,
              code:   ({ children }) => (
                <code className="rounded bg-gray-700 px-1 py-0.5 text-xs font-mono text-gray-200">
                  {children}
                </code>
              ),
              h1: ({ children }) => <h1 className="mb-1 text-base font-semibold text-gray-100">{children}</h1>,
              h2: ({ children }) => <h2 className="mb-1 text-sm font-semibold text-gray-100">{children}</h2>,
              h3: ({ children }) => <h3 className="mb-1 text-sm font-medium text-gray-200">{children}</h3>,
            }}
          >
            {message.content}
          </ReactMarkdown>
        )}
      </div>
    </div>
  )
}

/** Streaming bubble shown while the assistant is typing. */
export function StreamingBubble({
  content,
  toolStatus,
}: {
  content: string
  toolStatus: string | null
}) {
  return (
    <div className="flex justify-start">
      <div className="max-w-[75%] rounded-2xl rounded-bl-sm bg-gray-800 px-4 py-2.5 text-sm leading-relaxed text-gray-200">
        {toolStatus && !content ? (
          <span className="flex items-center gap-1.5 text-gray-400 text-xs">
            <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-blue-400" />
            {toolStatus}
          </span>
        ) : (
          <>
            <ReactMarkdown
              components={{
                p:      ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                ul:     ({ children }) => <ul className="mb-2 list-disc pl-4 space-y-0.5">{children}</ul>,
                ol:     ({ children }) => <ol className="mb-2 list-decimal pl-4 space-y-0.5">{children}</ol>,
                li:     ({ children }) => <li>{children}</li>,
                strong: ({ children }) => <strong className="font-semibold text-gray-100">{children}</strong>,
              }}
            >
              {content}
            </ReactMarkdown>
            <span className="ml-0.5 inline-block h-3.5 w-0.5 animate-pulse bg-gray-400 align-middle" />
          </>
        )}
      </div>
    </div>
  )
}