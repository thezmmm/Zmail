'use client'

import { useState, KeyboardEvent, FormEvent } from 'react'
import { Send, Square, Paperclip, X } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { ProcessingResult } from '@/types'

interface ChatInputProps {
  onSend: (message: string) => void
  onAbort: () => void
  onTogglePicker: () => void
  selectedEmails: ProcessingResult[]
  onRemoveEmail: (id: string) => void
  isStreaming: boolean
  disabled?: boolean
}

export default function ChatInput({
  onSend,
  onAbort,
  onTogglePicker,
  selectedEmails,
  onRemoveEmail,
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

  function handleInput(e: FormEvent<HTMLTextAreaElement>) {
    const el = e.currentTarget
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 144)}px`
  }

  return (
    <div className="border-t border-gray-800 px-6 py-4">
      {/* Selected email chips */}
      {selectedEmails.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {selectedEmails.map(email => (
            <span
              key={email.id}
              className="flex max-w-[200px] items-center gap-1 rounded-full border border-blue-700/50 bg-blue-900/30 px-2 py-0.5 text-[10px] text-blue-300"
            >
              <span className="truncate">{email.subject ?? '（无主题）'}</span>
              <button
                onClick={() => onRemoveEmail(email.id)}
                className="shrink-0 rounded-full p-0.5 text-blue-400 hover:bg-blue-800/50 hover:text-blue-200"
              >
                <X className="h-2.5 w-2.5" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="flex items-end gap-2 rounded-xl border border-gray-700 bg-gray-900 px-3 py-2 focus-within:border-gray-600">
        {/* Attach emails button */}
        <button
          onClick={onTogglePicker}
          disabled={isStreaming}
          title="选择邮件"
          className={cn(
            'shrink-0 rounded-lg p-1.5 transition-colors',
            selectedEmails.length > 0
              ? 'text-blue-400 hover:bg-blue-900/30'
              : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
            'disabled:opacity-30',
          )}
        >
          <Paperclip className="h-4 w-4" />
        </button>

        <textarea
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onInput={handleInput}
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