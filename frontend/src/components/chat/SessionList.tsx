import { Plus, MessageSquare, Trash2 } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { cn } from '@/lib/cn'
import type { AgentSession } from '@/types'

interface SessionListProps {
  sessions: AgentSession[]
  selectedId: string | null
  onSelect: (id: string) => void
  onNew: () => void
  onDelete: (id: string) => void
  isCreating: boolean
  isDeletingId: string | null
}

export default function SessionList({
  sessions,
  selectedId,
  onSelect,
  onNew,
  onDelete,
  isCreating,
  isDeletingId,
}: SessionListProps) {
  return (
    <aside className="flex h-full w-56 shrink-0 flex-col border-r border-gray-800 bg-gray-900">
      <div className="flex h-12 items-center justify-between px-3 border-b border-gray-800">
        <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
          会话
        </span>
        <button
          onClick={onNew}
          disabled={isCreating}
          title="新建会话"
          className="rounded p-1 text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300 disabled:opacity-40"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {sessions.length === 0 ? (
          <p className="px-2 py-3 text-center text-xs text-gray-600">
            点击 + 开始新对话
          </p>
        ) : (
          <ul className="space-y-0.5">
            {sessions.map(s => (
              <li key={s.id} className="group relative">
                <button
                  onClick={() => onSelect(s.id)}
                  className={cn(
                    'flex w-full items-start gap-2 rounded-md px-2.5 py-2 text-left transition-colors pr-8',
                    selectedId === s.id
                      ? 'bg-gray-800 text-gray-100'
                      : 'text-gray-400 hover:bg-gray-800/60 hover:text-gray-200',
                    isDeletingId === s.id && 'opacity-40 pointer-events-none',
                  )}
                >
                  <MessageSquare className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium">
                      {s.title ?? '新对话'}
                    </p>
                    <p className="text-[10px] text-gray-600">
                      {formatDistanceToNow(new Date(s.updatedAt), {
                        addSuffix: true,
                        locale: zhCN,
                      })}
                    </p>
                  </div>
                </button>

                {/* Delete button — visible on hover */}
                <button
                  onClick={e => { e.stopPropagation(); onDelete(s.id) }}
                  disabled={isDeletingId === s.id}
                  title="删除会话"
                  className={cn(
                    'absolute right-1.5 top-1/2 -translate-y-1/2 rounded p-1',
                    'text-gray-600 opacity-0 transition-opacity',
                    'hover:bg-gray-700 hover:text-red-400',
                    'group-hover:opacity-100',
                    'disabled:pointer-events-none',
                  )}
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  )
}
