'use client'

import { usePendingDrafts, useApproveDraft, useRejectDraft } from '@/hooks/useDrafts'
import DraftCard from '@/components/drafts/DraftCard'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import Button from '@/components/ui/Button'

export default function DraftsPage() {
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
    <div className="flex h-full flex-col">
      <div className="border-b border-gray-800 px-6 py-4">
        <h1 className="text-base font-semibold text-gray-100">草稿审批</h1>
        <p className="mt-0.5 text-xs text-gray-500">
          审批 AI 生成的草稿回复，批准后将自动发送
        </p>
      </div>

      <div className="flex-1 overflow-y-auto px-6 py-4">
        {isLoading ? (
          <div className="flex justify-center pt-20">
            <Spinner />
          </div>
        ) : isError ? (
          <EmptyState title="加载失败" description="请检查后端服务是否正常运行" />
        ) : drafts.length === 0 ? (
          <EmptyState title="暂无待审草稿" description="AI 生成草稿后将显示在这里" />
        ) : (
          <div className="space-y-3">
            {drafts.map(draft => (
              <DraftCard
                key={draft.id}
                draft={draft}
                onApprove={id => approve.mutate(id)}
                onReject={id => reject.mutate(id)}
                isPending={anyPending}
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
  )
}
