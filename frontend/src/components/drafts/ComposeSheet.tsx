'use client'

import { useState } from 'react'
import { X, Send } from 'lucide-react'
import { cn } from '@/lib/cn'
import Button from '@/components/ui/Button'
import Spinner from '@/components/ui/Spinner'
import { useAccounts } from '@/hooks/useAccounts'
import { useCreateDraft } from '@/hooks/useDrafts'

interface ComposeSheetProps {
  open: boolean
  onClose: () => void
}

export default function ComposeSheet({ open, onClose }: ComposeSheetProps) {
  const { data: accounts = [], isLoading: accountsLoading } = useAccounts()
  const createDraft = useCreateDraft()

  const [accountId, setAccountId] = useState('')
  const [to, setTo]         = useState('')
  const [subject, setSubject] = useState('')
  const [body, setBody]     = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const toList = to.split(',').map(s => s.trim()).filter(Boolean)
    if (!accountId || toList.length === 0 || !body.trim()) return

    await createDraft.mutateAsync({
      accountId,
      to: toList,
      subject: subject.trim(),
      body: body.trim(),
    })
    // Reset and close on success
    setTo(''); setSubject(''); setBody(''); setAccountId('')
    onClose()
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className={cn(
          'fixed inset-0 z-40 bg-black/50 transition-opacity duration-200',
          open ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none',
        )}
        onClick={onClose}
      />

      {/* Panel */}
      <div
        className={cn(
          'fixed bottom-0 right-0 top-0 z-50 flex w-full max-w-lg flex-col',
          'border-l border-gray-800 bg-gray-900 shadow-2xl',
          'transition-transform duration-200',
          open ? 'translate-x-0' : 'translate-x-full',
        )}
      >
        {/* Header */}
        <div className="flex h-14 items-center justify-between border-b border-gray-800 px-5">
          <h2 className="text-sm font-semibold text-gray-100">新建草稿</h2>
          <button
            onClick={onClose}
            className="rounded p-1 text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex flex-1 flex-col overflow-y-auto p-5 gap-4">
          {/* Account selector */}
          <div className="space-y-1.5">
            <label className="text-[11px] font-medium text-gray-400">发件账户</label>
            {accountsLoading ? (
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <Spinner className="h-3 w-3" />加载中…
              </div>
            ) : (
              <select
                value={accountId}
                onChange={e => setAccountId(e.target.value)}
                required
                className="w-full rounded-md border border-gray-700 bg-gray-950 px-3 py-2 text-xs text-gray-200
                           focus:outline-none focus:ring-1 focus:ring-blue-600"
              >
                <option value="">选择账户…</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.accountEmail} ({a.provider})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* To */}
          <div className="space-y-1.5">
            <label className="text-[11px] font-medium text-gray-400">
              收件人 <span className="text-gray-600">（多个地址用逗号分隔）</span>
            </label>
            <input
              type="text"
              value={to}
              onChange={e => setTo(e.target.value)}
              required
              placeholder="example@email.com"
              className="w-full rounded-md border border-gray-700 bg-gray-950 px-3 py-2 text-xs text-gray-200
                         placeholder:text-gray-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
          </div>

          {/* Subject */}
          <div className="space-y-1.5">
            <label className="text-[11px] font-medium text-gray-400">主题</label>
            <input
              type="text"
              value={subject}
              onChange={e => setSubject(e.target.value)}
              placeholder="（可选）"
              className="w-full rounded-md border border-gray-700 bg-gray-950 px-3 py-2 text-xs text-gray-200
                         placeholder:text-gray-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
          </div>

          {/* Body */}
          <div className="flex flex-1 flex-col space-y-1.5">
            <label className="text-[11px] font-medium text-gray-400">正文</label>
            <textarea
              value={body}
              onChange={e => setBody(e.target.value)}
              required
              placeholder="在此输入邮件内容…"
              className="flex-1 resize-none rounded-md border border-gray-700 bg-gray-950 px-3 py-2 text-xs
                         leading-relaxed text-gray-200 placeholder:text-gray-600
                         focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
          </div>

          {/* Submit */}
          <div className="flex justify-end gap-2 pt-1">
            <Button variant="ghost" size="sm" type="button" onClick={onClose}>
              取消
            </Button>
            <Button
              variant="primary"
              size="sm"
              type="submit"
              loading={createDraft.isPending}
              disabled={!accountId || !to.trim() || !body.trim()}
            >
              <Send className="h-3.5 w-3.5" />
              保存草稿
            </Button>
          </div>
        </form>
      </div>
    </>
  )
}
