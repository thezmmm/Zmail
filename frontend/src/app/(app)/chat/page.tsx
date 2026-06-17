'use client'

import { useState } from 'react'
import { useSessions, useMessages, useCreateSession, useDeleteSession } from '@/hooks/useSessions'
import { useChat } from '@/hooks/useChat'
import SessionList from '@/components/chat/SessionList'
import MessageList from '@/components/chat/MessageList'
import ChatInput from '@/components/chat/ChatInput'
import EmailPickerPanel from '@/components/chat/EmailPickerPanel'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import type { ProcessingResult, EmailRef } from '@/types'

export default function ChatPage() {
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [selectedEmails, setSelectedEmails]       = useState<ProcessingResult[]>([])
  const [showPicker, setShowPicker]               = useState(false)

  const { data: sessions = [], isLoading: sessionsLoading } = useSessions()
  const { data: messages = [], isLoading: messagesLoading } = useMessages(selectedSessionId)
  const createSession = useCreateSession()
  const deleteSession = useDeleteSession()
  const { sendMessage, isStreaming, streamingContent, streamingDone, toolStatus, abort } =
    useChat(selectedSessionId)

  async function handleNewSession() {
    const session = await createSession.mutateAsync()
    setSelectedSessionId(session.id)
    setSelectedEmails([])
    setShowPicker(false)
  }

  function handleDeleteSession(id: string) {
    if (selectedSessionId === id) {
      setSelectedSessionId(null)
      setSelectedEmails([])
      setShowPicker(false)
    }
    deleteSession.mutate(id)
  }

  function handleToggleEmail(result: ProcessingResult) {
    setSelectedEmails(prev =>
      prev.some(e => e.id === result.id)
        ? prev.filter(e => e.id !== result.id)
        : [...prev, result],
    )
  }

  function handleSend(message: string) {
    const refs: EmailRef[] = selectedEmails.map(e => ({
      providerId: e.emailProviderId,
      accountId:  e.accountId,
    }))
    sendMessage(message, refs.length > 0 ? refs : undefined)
    setSelectedEmails([])
    setShowPicker(false)
  }

  return (
    <div className="flex h-full">
      {/* Session sidebar */}
      {sessionsLoading ? (
        <div className="flex h-full w-56 shrink-0 items-center justify-center border-r border-gray-800">
          <Spinner />
        </div>
      ) : (
        <SessionList
          sessions={sessions}
          selectedId={selectedSessionId}
          onSelect={id => { setSelectedSessionId(id); setSelectedEmails([]); setShowPicker(false) }}
          onNew={handleNewSession}
          onDelete={handleDeleteSession}
          isCreating={createSession.isPending}
          isDeletingId={deleteSession.isPending ? (deleteSession.variables ?? null) : null}
        />
      )}

      {/* Chat area */}
      <div className="flex flex-1 flex-col">
        {!selectedSessionId ? (
          <div className="flex flex-1 items-center justify-center">
            <EmptyState
              title="选择或新建会话"
              description="从左侧选择一个会话，或点击 + 开始新对话"
            />
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="flex h-12 items-center border-b border-gray-800 px-6">
              <p className="text-sm font-medium text-gray-200">
                {sessions.find(s => s.id === selectedSessionId)?.title ?? '新对话'}
              </p>
            </div>

            {/* Messages */}
            {messagesLoading ? (
              <div className="flex flex-1 items-center justify-center">
                <Spinner />
              </div>
            ) : (
              <MessageList
                messages={messages}
                isStreaming={isStreaming}
                streamingContent={streamingContent}
                streamingDone={streamingDone}
                toolStatus={toolStatus}
              />
            )}

            {/* Email picker panel */}
            {showPicker && (
              <EmailPickerPanel
                selected={selectedEmails}
                onToggle={handleToggleEmail}
                onClose={() => setShowPicker(false)}
              />
            )}

            {/* Input */}
            <ChatInput
              onSend={handleSend}
              onAbort={abort}
              onTogglePicker={() => setShowPicker(v => !v)}
              selectedEmails={selectedEmails}
              onRemoveEmail={id => setSelectedEmails(prev => prev.filter(e => e.id !== id))}
              isStreaming={isStreaming}
              disabled={messagesLoading}
            />
          </>
        )}
      </div>
    </div>
  )
}