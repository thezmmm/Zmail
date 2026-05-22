import { cn } from '@/lib/cn'

interface Props {
  title: string
  description?: string
  className?: string
}

export default function EmptyState({ title, description, className }: Props) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-2 py-20 text-center', className)}>
      <p className="text-sm font-medium text-gray-400">{title}</p>
      {description && (
        <p className="text-xs text-gray-600">{description}</p>
      )}
    </div>
  )
}