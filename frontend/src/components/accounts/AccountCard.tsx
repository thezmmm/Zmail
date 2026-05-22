import { Trash2, Mail } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import Button from '@/components/ui/Button'
import type { AccountDto } from '@/types'

interface AccountCardProps {
  account: AccountDto
  onDelete: (id: string) => void
  isDeleting: boolean
}

const providerLabels: Record<string, string> = {
  GMAIL:   'Gmail',
  MSGRAPH: 'Outlook',
}

const providerColors: Record<string, string> = {
  GMAIL:   'bg-red-900/30 text-red-400',
  MSGRAPH: 'bg-blue-900/30 text-blue-400',
}

export default function AccountCard({ account, onDelete, isDeleting }: AccountCardProps) {
  const addedAgo = formatDistanceToNow(new Date(account.createdAt), {
    addSuffix: true,
    locale: zhCN,
  })

  return (
    <div className="flex items-center gap-4 rounded-lg border border-gray-800 bg-gray-900 p-4">
      {/* Provider icon placeholder */}
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gray-800">
        <Mail className="h-5 w-5 text-gray-400" />
      </div>

      {/* Info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-medium leading-none ${
              providerColors[account.provider] ?? 'bg-gray-800 text-gray-400'
            }`}
          >
            {providerLabels[account.provider] ?? account.provider}
          </span>
        </div>
        <p className="mt-1 truncate text-sm font-medium text-gray-100">
          {account.accountEmail}
        </p>
        <p className="text-[10px] text-gray-600">添加于 {addedAgo}</p>
      </div>

      {/* Delete */}
      <Button
        variant="danger"
        size="sm"
        loading={isDeleting}
        onClick={() => onDelete(account.id)}
        title="移除账户"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  )
}
