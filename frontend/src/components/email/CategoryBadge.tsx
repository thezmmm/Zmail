import { cn } from '@/lib/cn'
import type { Category } from '@/types'

const styles: Record<Category, string> = {
  WORK:       'bg-blue-900/30 text-blue-400',
  PERSONAL:   'bg-purple-900/30 text-purple-400',
  FINANCE:    'bg-emerald-900/30 text-emerald-400',
  PROMOTIONS: 'bg-orange-900/30 text-orange-400',
  OTHER:      'bg-gray-800 text-gray-500',
}

const labels: Record<Category, string> = {
  WORK:       '工作',
  PERSONAL:   '个人',
  FINANCE:    '财务',
  PROMOTIONS: '推广',
  OTHER:      '其他',
}

export default function CategoryBadge({ category }: { category: Category }) {
  if (!(category in labels)) return null
  return (
    <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium leading-none', styles[category])}>
      {labels[category]}
    </span>
  )
}