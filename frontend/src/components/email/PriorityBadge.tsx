import { cn } from '@/lib/cn'
import type { Priority } from '@/types'

const styles: Record<Priority, string> = {
  HIGH:   'bg-red-900/30 text-red-400',
  MEDIUM: 'bg-yellow-900/30 text-yellow-400',
  LOW:    'bg-gray-800 text-gray-400',
}

const labels: Record<Priority, string> = {
  HIGH:   '高',
  MEDIUM: '中',
  LOW:    '低',
}

export default function PriorityBadge({ priority }: { priority: Priority }) {
  if (!(priority in labels)) return null
  return (
    <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium leading-none', styles[priority])}>
      {labels[priority]}
    </span>
  )
}