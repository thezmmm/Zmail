import { AlertTriangle } from 'lucide-react'
import type { EmailProvider } from '@/types'

interface ReauthBannerProps {
  provider: EmailProvider
  onReauth: () => void
}

const providerLabels: Record<EmailProvider, string> = {
  GMAIL:   'Gmail',
  MSGRAPH: 'Outlook',
}

export default function ReauthBanner({ provider, onReauth }: ReauthBannerProps) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-orange-800/50 bg-orange-900/20 px-4 py-3">
      <AlertTriangle className="h-4 w-4 shrink-0 text-orange-400" />
      <p className="flex-1 text-xs text-orange-300">
        {providerLabels[provider]} 授权已过期，请重新授权以继续同步邮件
      </p>
      <button
        onClick={onReauth}
        className="shrink-0 rounded-md bg-orange-900/50 px-3 py-1.5 text-xs font-medium text-orange-300 transition-colors hover:bg-orange-900/80"
      >
        重新授权
      </button>
    </div>
  )
}
