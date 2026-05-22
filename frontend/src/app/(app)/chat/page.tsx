'use client'

import { useState } from 'react'
import { useSessions, useMessages, useCreateSession } from '@/hooks/useSessions'
import { useChat } from '@/hooks/useChat'
import SessionList from '@/components/chat/SessionList'
import MessageList from '@/components/chat/MessageList'
import ChatInput from '@/components/chat/ChatInput'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'

export default function ChatPage() {
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)

  const { data: sessions = [], isLoading: sessionsLoading } = useSessions()
  const { data: messages = [], isLoading: messagesLoading } = useMessages(selectedSessionId)
  const createSession = useCreateSession()
  const { sendMessage, isStreaming, streamingContent, abort } = useChat(selectedSessionId)

  async function handleNewSession() {
    const session = await createSession.mutateAsync()
    setSelectedSessionId(session.id)
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
          onSelect={setSelectedSessionId}
          onNew={handleNewSession}
          isCreating={createSession.isPending}
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
              />
            )}

            {/* Input */}
            <ChatInput
              onSend={sendMessage}
              onAbort={abort}
              isStreaming={isStreaming}
              disabled={messagesLoading}
            />
          </>
        )}
      </div>
    </div>
  )
}
