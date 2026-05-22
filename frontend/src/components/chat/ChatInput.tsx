'use client'

import { useState, KeyboardEvent } from 'react'
import { Send, Square } from 'lucide-react'
import { cn } from '@/lib/cn'

interface ChatInputProps {
  onSend: (message: string) => void
  onAbort: () => void
  isStreaming: boolean
  disabled?: boolean
}

export default function ChatInput({
  onSend,
  onAbort,
  isStreaming,
  disabled,
}: ChatInputProps) {
  const [value, setValue] = useState('')

  function handleSend() {
    const trimmed = value.trim()
    if (!trimmed || isStreaming) return
    onSend(trimmed)
    setValue('')
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="border-t border-gray-800 px-6 py-4">
      <div className="flex items-end gap-2 rounded-xl border border-gray-700 bg-gray-900 px-3 py-2 focus-within:border-gray-600">
        <textarea
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="发送消息… (Enter 发送, Shift+Enter 换行)"
          disabled={disabled || isStreaming}
          rows={1}
          className={cn(
            'flex-1 resize-none bg-transparent text-sm text-gray-200 placeholder-gray-600 outline-none',
            'min-h-[1.5rem] max-h-36 overflow-y-auto',
          )}
          style={{ fieldSizing: 'content' } as React.CSSProperties}
        />
        <button
          onClick={isStreaming ? onAbort : handleSend}
          disabled={!isStreaming && (!value.trim() || disabled)}
          className={cn(
            'shrink-0 rounded-lg p-1.5 transition-colors',
            isStreaming
              ? 'text-red-400 hover:bg-red-900/30'
              : 'text-blue-400 hover:bg-blue-900/30 disabled:opacity-30',
          )}
          title={isStreaming ? '停止' : '发送'}
        >
          {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
        </button>
      </div>
      <p className="mt-1.5 text-center text-[10px] text-gray-700">
        AI 生成内容可能存在错误，请自行核实重要信息
      </p>
    </div>
  )
}
