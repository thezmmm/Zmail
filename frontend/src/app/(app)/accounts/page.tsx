'use client'

import { toast } from 'sonner'
import {
  useAccounts,
  useSyncStatus,
  useDeleteAccount,
  useReauthAccount,
  useGmailLinkUrl,
  useMsgraphLinkUrl,
} from '@/hooks/useAccounts'
import AccountCard from '@/components/accounts/AccountCard'
import ReauthBanner from '@/components/accounts/ReauthBanner'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import Button from '@/components/ui/Button'
import { RefreshCw } from 'lucide-react'

export default function AccountsPage() {
  const { data: accounts = [], isLoading, isError } = useAccounts()
  const { data: syncStatus } = useSyncStatus()
  const deleteAccount = useDeleteAccount()
  const reauthAccount = useReauthAccount()
  const gmailLink     = useGmailLinkUrl()
  const msgraphLink   = useMsgraphLinkUrl()

  async function addGmail() {
    try {
      const url = await gmailLink.mutateAsync()
      window.location.href = url
    } catch {
      toast.error('获取 Gmail 授权链接失败')
    }
  }

  async function addOutlook() {
    try {
      const url = await msgraphLink.mutateAsync()
      window.location.href = url
    } catch {
      toast.error('获取 Outlook 授权链接失败')
    }
  }

  async function handleReauth(accountId: string) {
    try {
      const dto = await reauthAccount.mutateAsync(accountId)
      const base = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'
      window.location.href = `${base}${dto.loginPath}`
    } catch {
      toast.error('获取重授权链接失败')
    }
  }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-base font-semibold text-gray-100">账户管理</h1>
            <p className="mt-0.5 text-xs text-gray-500">管理已连接的邮箱账户</p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              loading={gmailLink.isPending}
              onClick={addGmail}
            >
              + Gmail
            </Button>
            <Button
              variant="secondary"
              size="sm"
              loading={msgraphLink.isPending}
              onClick={addOutlook}
            >
              + Outlook
            </Button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-6 py-4">
        {isLoading ? (
          <div className="flex justify-center pt-20">
            <Spinner />
          </div>
        ) : isError ? (
          <EmptyState title="加载失败" description="请检查后端服务是否正常运行" />
        ) : (
          <div className="space-y-3">
            {/* Sync status banner */}
            {syncStatus === 'RUNNING' && (
              <div className="flex items-center gap-3 rounded-lg border border-blue-800/50 bg-blue-950/20 px-4 py-3">
                <RefreshCw className="h-4 w-4 shrink-0 animate-spin text-blue-400" />
                <p className="text-xs text-blue-300">正在同步邮件，请稍候…</p>
              </div>
            )}
            {syncStatus === 'FAILED' && (
              <div className="rounded-lg border border-red-800/40 bg-red-950/20 px-4 py-3">
                <p className="text-xs text-red-400">上次同步失败，请检查账户授权状态</p>
              </div>
            )}

            {accounts.length === 0 ? (
              <EmptyState
                title="未连接任何账户"
                description="点击右上角按钮添加 Gmail 或 Outlook 账户"
              />
            ) : (
              <>
                {/* Reauth banners for accounts needing reauth */}
                {accounts
                  .filter(a => a.needsReauth)
                  .map(a => (
                    <ReauthBanner
                      key={a.id}
                      provider={a.provider}
                      onReauth={() => handleReauth(a.id)}
                    />
                  ))}

                {/* Account cards */}
                {accounts.map(account => (
                  <AccountCard
                    key={account.id}
                    account={account}
                    onDelete={id => deleteAccount.mutate(id)}
                    isDeleting={
                      deleteAccount.isPending && deleteAccount.variables === account.id
                    }
                  />
                ))}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
