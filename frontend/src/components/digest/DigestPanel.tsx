'use client'

import { useState, useEffect } from 'react'
import { useDigest } from '@/hooks/useDigest'
import Button from '@/components/ui/Button'
import Spinner from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'

export default function DigestPanel() {
  const { digest, loading, generating, error, generate } = useDigest()
  const [expanded, setExpanded] = useState(false)

  // Auto-expand when a digest is first loaded from the server
  useEffect(() => {
    if (digest) setExpanded(true)
  }, [digest])

  const handleGenerate = async (force = false) => {
    await generate(force)
    setExpanded(true)
  }

  if (loading) {
    return (
      <div className="border-b border-gray-800 px-6 py-3 flex items-center gap-2 text-xs text-gray-500">
        <Spinner className="h-3 w-3" />
        加载日报…
      </div>
    )
  }

  return (
    <div className="border-b border-gray-800">
      {/* Panel header */}
      <div className="flex items-center justify-between px-6 py-3">
        <div className="flex items-center gap-3">
          <span className="text-xs font-medium text-gray-400">今日日报</span>
          {!digest && !generating && (
            <Button size="sm" variant="secondary" onClick={() => handleGenerate(false)}>
              生成今日日报
            </Button>
          )}
          {generating && (
            <span className="flex items-center gap-1.5 text-xs text-gray-500">
              <Spinner className="h-3 w-3" /> 生成中…
            </span>
          )}
          {digest && !generating && (
            <button
              onClick={() => handleGenerate(true)}
              className="text-xs text-gray-600 hover:text-gray-400 transition-colors"
            >
              重新生成
            </button>
          )}
          {error && <span className="text-xs text-red-400">{error}</span>}
        </div>

        {digest && (
          <button
            onClick={() => setExpanded(v => !v)}
            className="text-xs text-gray-600 hover:text-gray-400 transition-colors select-none"
          >
            {expanded ? '收起 ▴' : '展开 ▾'}
          </button>
        )}
      </div>

      {/* Digest body */}
      {digest && expanded && (
        <div className="px-6 pb-4 space-y-3">
          {/* Overview */}
          {digest.overview && (
            <p className="text-sm text-gray-300 leading-relaxed">{digest.overview}</p>
          )}

          {/* Priorities */}
          {digest.priorities.length > 0 && (
            <div>
              <p className="text-xs font-medium text-gray-500 mb-1">优先事项</p>
              <ul className="space-y-0.5">
                {digest.priorities.map((p, i) => (
                  <li key={i} className="text-xs text-gray-400 flex gap-1.5">
                    <span className="text-gray-600">•</span>
                    {p}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Email summaries */}
          {digest.emails.length > 0 && (
            <div>
              <p className="text-xs font-medium text-gray-500 mb-1.5">
                邮件摘要 ({digest.emails.length})
              </p>
              <div className="space-y-2">
                {digest.emails.map((e) => (
                  <div
                    key={e.emailId}
                    className={cn(
                      'rounded-md border border-gray-800 bg-gray-900/50 px-3 py-2',
                    )}
                  >
                    <p className="text-xs font-medium text-gray-300 truncate">{e.subject}</p>
                    <p className="text-xs text-gray-600 mb-1">{e.sender}</p>
                    <p className="text-xs text-gray-400 leading-relaxed">{e.summary}</p>
                    {e.actionItems.length > 0 && (
                      <ul className="mt-1 space-y-0.5">
                        {e.actionItems.map((item, i) => (
                          <li key={i} className="text-xs text-blue-400/80 flex gap-1.5">
                            <span className="text-blue-600">→</span>
                            {item}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
