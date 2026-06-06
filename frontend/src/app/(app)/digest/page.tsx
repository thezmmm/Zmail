'use client'

import Link from 'next/link'
import { useDigest } from '@/hooks/useDigest'
import Button from '@/components/ui/Button'
import Spinner from '@/components/ui/Spinner'
import type { EmailDigest } from '@/types'

export default function DigestPage() {
  const { digest, loading, generating, error, generate } = useDigest()

  const today = new Date().toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
  })

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-gray-800 px-6 py-4">
        <div>
          <h1 className="text-base font-semibold text-gray-100">今日日报</h1>
          <p className="mt-0.5 text-xs text-gray-500">{today}</p>
        </div>
        <div className="flex items-center gap-2">
          {generating && (
            <span className="flex items-center gap-1.5 text-xs text-gray-500">
              <Spinner className="h-3 w-3" />生成中…
            </span>
          )}
          {digest && !generating && (
            <Button size="sm" variant="ghost" onClick={() => generate(true)}>重新生成</Button>
          )}
        </div>
      </div>

      {/* Single scrollable body */}
      <div className="flex-1 overflow-y-auto px-6 py-6">
        {loading ? (
          <div className="flex justify-center pt-20"><Spinner /></div>
        ) : error ? (
          <div className="flex flex-col items-center gap-3 pt-20 text-center">
            <p className="text-sm text-red-400">{error}</p>
            <Button size="sm" variant="secondary" onClick={() => generate(false)}>重试</Button>
          </div>
        ) : !digest ? (
          <EmptyDigest onGenerate={() => generate(false)} generating={generating} />
        ) : (
          <div className="space-y-6">
            {/* Top row: overview + priorities side by side */}
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
              <div className="lg:col-span-2 rounded-lg border border-gray-800 bg-gray-900/40 px-5 py-4">
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wider text-gray-500">概览</p>
                <p className="text-sm leading-relaxed text-gray-300">{digest.overview}</p>
              </div>

              {digest.priorities.length > 0 && (
                <div className="rounded-lg border border-gray-800 bg-gray-900/40 px-5 py-4">
                  <p className="mb-3 text-xs font-medium uppercase tracking-wider text-gray-500">今日重点</p>
                  <ul className="space-y-2.5">
                    {digest.priorities.map((p, i) => (
                      <li key={i} className="flex gap-2.5 text-sm text-gray-300">
                        <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-blue-600/20 text-[10px] font-medium text-blue-400">
                          {i + 1}
                        </span>
                        {p}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Email cards grid */}
            {digest.emails.length > 0 && (
              <div>
                <p className="mb-3 text-xs font-medium uppercase tracking-wider text-gray-500">
                  邮件摘要 ({digest.emails.length})
                </p>
                <div className="grid grid-cols-1 gap-3 lg:grid-cols-2 xl:grid-cols-3">
                  {digest.emails.map(email => (
                    <EmailCard key={email.emailId} email={email} />
                  ))}
                </div>
              </div>
            )}

            <p className="text-center text-[10px] text-gray-700">
              生成于 {new Date(digest.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

function EmptyDigest({ onGenerate, generating }: { onGenerate: () => void; generating: boolean }) {
  return (
    <div className="flex flex-col items-center gap-4 pt-20 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-800 text-2xl">
        📰
      </div>
      <div>
        <p className="text-sm font-medium text-gray-300">今天还没有日报</p>
        <p className="mt-1 text-xs text-gray-500">点击生成，AI 将汇总过去 24 小时的邮件</p>
      </div>
      <Button variant="primary" loading={generating} onClick={onGenerate}>
        生成今日日报
      </Button>
    </div>
  )
}

function EmailCard({ email }: { email: EmailDigest }) {
  return (
    <Link
      href={`/emails/${email.emailId}`}
      className="flex flex-col rounded-lg border border-gray-800 bg-gray-900/40 p-4 transition-colors hover:border-gray-700 hover:bg-gray-800/60"
    >
      <p className="text-sm font-medium text-gray-200 leading-snug line-clamp-2">
        {email.subject || '(无主题)'}
      </p>
      <p className="mt-1 text-xs text-gray-500">{email.sender}</p>
      <p className="mt-2 flex-1 text-sm text-gray-400 leading-relaxed line-clamp-3">
        {email.summary}
      </p>
      {email.actionItems.length > 0 && (
        <ul className="mt-3 space-y-1 border-t border-gray-800 pt-3">
          {email.actionItems.map((item, i) => (
            <li key={i} className="flex gap-2 text-xs text-blue-400/80">
              <span className="mt-0.5 shrink-0 text-blue-600">→</span>
              {item}
            </li>
          ))}
        </ul>
      )}
    </Link>
  )
}
