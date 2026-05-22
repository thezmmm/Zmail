'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import {
  Inbox,
  FileText,
  MessageSquare,
  Mail,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react'
import { cn } from '@/lib/cn'
import { useUser } from '@/hooks/useUser'
import { clearToken } from '@/lib/auth'
import { useUiStore } from '@/stores/uiStore'
import api from '@/lib/api'
import type { ApiResponse, PagedResponse, ProcessingResult } from '@/types'

const navItems = [
  { href: '/',         label: '收件箱',     icon: Inbox },
  { href: '/drafts',   label: '草稿审批',   icon: FileText },
  { href: '/chat',     label: 'Agent 对话', icon: MessageSquare },
  { href: '/accounts', label: '账户管理',   icon: Mail },
]

export default function Sidebar() {
  const pathname   = usePathname()
  const router     = useRouter()
  const { data: user } = useUser()
  const { sidebarCollapsed, toggleSidebar } = useUiStore()

  const { data: draftCount = 0 } = useQuery({
    queryKey: ['drafts', 'pending', 'count'],
    queryFn: () =>
      api
        .get<ApiResponse<PagedResponse<ProcessingResult>>>('/drafts/pending', {
          params: { size: 1 },
        })
        .then(r => r.data.data?.totalElements ?? 0),
    refetchInterval: 30_000,
  })

  function signOut() {
    clearToken()
    router.replace('/login')
  }

  const initials = user?.name
    ? user.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()
    : '?'

  return (
    <aside
      className={cn(
        'flex h-screen shrink-0 flex-col border-r border-gray-800 bg-gray-900 transition-[width] duration-200',
        sidebarCollapsed ? 'w-14' : 'w-56',
      )}
    >
      {/* Logo + toggle */}
      <div className="flex h-14 items-center border-b border-gray-800 px-3">
        {!sidebarCollapsed && (
          <div className="flex flex-1 items-center gap-2 overflow-hidden">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-blue-600 text-xs font-bold text-white">
              Z
            </div>
            <span className="truncate text-sm font-semibold tracking-tight">Zmail</span>
          </div>
        )}
        {sidebarCollapsed && (
          <div className="mx-auto flex h-7 w-7 items-center justify-center rounded-md bg-blue-600 text-xs font-bold text-white">
            Z
          </div>
        )}
        <button
          onClick={toggleSidebar}
          title={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
          className={cn(
            'shrink-0 rounded p-1 text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300',
            sidebarCollapsed && 'mx-auto mt-2 hidden',
          )}
        >
          {sidebarCollapsed
            ? <PanelLeftOpen className="h-4 w-4" />
            : <PanelLeftClose className="h-4 w-4" />}
        </button>
      </div>

      {/* Nav */}
      <nav className="flex-1 space-y-0.5 p-2 pt-3">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active = href === '/' ? pathname === '/' : pathname.startsWith(href)
          return (
            <Link
              key={href}
              href={href}
              title={sidebarCollapsed ? label : undefined}
              className={cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                active
                  ? 'bg-gray-800 text-white'
                  : 'text-gray-400 hover:bg-gray-800/60 hover:text-gray-200',
                sidebarCollapsed && 'justify-center px-2',
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && (
                <>
                  <span className="flex-1">{label}</span>
                  {href === '/drafts' && draftCount > 0 && (
                    <span className="rounded-full bg-blue-600 px-1.5 py-0.5 text-[10px] font-medium leading-none text-white">
                      {draftCount > 99 ? '99+' : draftCount}
                    </span>
                  )}
                </>
              )}
              {sidebarCollapsed && href === '/drafts' && draftCount > 0 && (
                <span className="absolute ml-4 mt-[-10px] h-1.5 w-1.5 rounded-full bg-blue-500" />
              )}
            </Link>
          )
        })}
      </nav>

      {/* Toggle button at bottom when collapsed */}
      {sidebarCollapsed && (
        <div className="border-t border-gray-800 p-2">
          <button
            onClick={toggleSidebar}
            title="展开侧边栏"
            className="flex w-full items-center justify-center rounded-md p-2 text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300"
          >
            <PanelLeftOpen className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* User */}
      {!sidebarCollapsed && (
        <div className="border-t border-gray-800 p-3">
          <div className="flex items-center gap-2 rounded-md px-2 py-1.5">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gray-700 text-xs font-medium text-gray-200">
              {initials}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-gray-200">
                {user?.name ?? '—'}
              </p>
              <p className="truncate text-[10px] text-gray-500">
                {user?.email ?? '—'}
              </p>
            </div>
            <button
              onClick={signOut}
              title="退出登录"
              className="rounded p-1 text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300"
            >
              <LogOut className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </aside>
  )
}
