'use client'

import { useState } from 'react'
import { PenLine } from 'lucide-react'
import { usePendingDrafts, useApproveDraft, useRejectDraft } from '@/hooks/useDrafts'
import DraftCard from '@/components/drafts/DraftCard'
import ComposeSheet from '@/components/drafts/ComposeSheet'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import Button from '@/components/ui/Button'

export default function DraftsPage() {
  const [composeOpen, setComposeOpen] = useState(false)

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
  } = usePendingDrafts()

  const approve = useApproveDraft()
  const reject  = useRejectDraft()

  const drafts = data?.pages.flatMap(p => p.content) ?? []
  const anyPending = approve.isPending || reject.isPending

  return (
    <>
      <div className="flex h-full flex-col">
        <div className="border-b border-gray-800 px-6 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-base font-semibold text-gray-100">草稿箱</h1>
              <p className="mt-0.5 text-xs text-gray-500">
                AI 生成或手动撰写的草稿，审阅后发送
              </p>
            </div>
            <Button variant="secondary" size="sm" onClick={() => setComposeOpen(true)}>
              <PenLine className="h-3.5 w-3.5" />
              新建草稿
            </Button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-4">
          {isLoading ? (
            <div className="flex justify-center pt-20">
              <Spinner />
            </div>
          ) : isError ? (
            <EmptyState title="加载失败" description="请检查后端服务是否正常运行" />
          ) : drafts.length === 0 ? (
            <EmptyState
              title="暂无待审草稿"
              description="AI 生成草稿后将显示在这里，也可点击右上角新建"
            />
          ) : (
            <div className="space-y-3">
              {drafts.map(draft => (
                <DraftCard
                  key={draft.id}
                  draft={draft}
                  onApprove={(id, body) => approve.mutate({ id, body })}
                  onReject={id => reject.mutate(id)}
                  isApprovePending={approve.isPending && approve.variables?.id === draft.id}
                  isRejectPending={reject.isPending && reject.variables === draft.id}
                  disabled={anyPending}
                />
              ))}

              {hasNextPage && (
                <div className="flex justify-center pt-4 pb-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    loading={isFetchingNextPage}
                    onClick={() => fetchNextPage()}
                  >
                    加载更多
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <ComposeSheet open={composeOpen} onClose={() => setComposeOpen(false)} />
    </>
  )
}
