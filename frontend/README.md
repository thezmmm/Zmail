# Zmail Frontend

Next.js 15 + React 19 + TypeScript + Tauri 2 桌面端前端。

## 技术栈

| 依赖 | 用途 |
|---|---|
| Next.js 15 (App Router) | 页面框架 |
| React 19 | UI |
| TypeScript | 类型安全 |
| Tailwind CSS v4 | 样式 |
| TanStack Query v5 | 服务端数据缓存 |
| Zustand v5 | 客户端 UI 状态 |
| Axios | HTTP 请求 |
| Lucide React | 图标 |
| date-fns | 时间格式化 |
| Tauri 2 | 桌面应用封装 |

## 启动

```bash
# 安装依赖
npm install

# Web 开发模式（需要后端运行在 localhost:8080）
npm run dev

# Tauri 桌面开发模式
npm run tauri dev

# 类型检查
npm run type-check

# 打包桌面应用
npm run tauri build
```

环境变量：复制根目录 `.env.example` 为 `.env`，设置 `NEXT_PUBLIC_API_URL`（默认 `http://localhost:8080/api/v1`）。

---

## 路由结构

```
src/app/
├── (app)/                      # 需要登录，共享侧边栏 layout
│   ├── layout.tsx              # App shell：认证守卫 + 侧边栏 + 内容区
│   ├── page.tsx                # 收件箱（AI 处理结果列表）
│   ├── emails/[id]/page.tsx    # 邮件详情
│   ├── drafts/page.tsx         # 草稿审批队列
│   ├── digest/page.tsx         # 今日日报（AI 生成每日邮件摘要）
│   ├── chat/page.tsx           # Agent 对话
│   └── accounts/page.tsx       # 账户管理
├── login/page.tsx              # OAuth 登录入口
├── auth/callback/page.tsx      # OAuth 回调，存 token 后跳转
├── layout.tsx                  # 根 layout
└── globals.css
```

`(app)` Route Group 使认证页（login、callback）天然不渲染侧边栏。`(app)/layout.tsx` 统一做认证守卫——未登录直接跳 `/login`。

---

## 组件结构

```
src/components/
├── layout/
│   ├── Sidebar.tsx             # 导航链接、badge 计数、用户信息
│   └── AppShell.tsx            # 侧边栏 + 内容区容器
├── email/
│   ├── ResultCard.tsx          # 收件箱列表项
│   ├── PriorityBadge.tsx       # HIGH / MEDIUM / LOW 彩色标签
│   └── CategoryBadge.tsx       # 邮件分类标签
├── drafts/
│   └── DraftCard.tsx           # 草稿卡片（含批准 / 拒绝操作）
├── chat/
│   ├── SessionList.tsx         # 左侧会话列表
│   ├── MessageList.tsx         # 消息历史
│   ├── MessageBubble.tsx       # 单条消息气泡
│   └── ChatInput.tsx           # 输入框
├── accounts/
│   ├── AccountCard.tsx         # 单个账户卡片
│   └── ReauthBanner.tsx        # 需要重新授权时的警告条
└── ui/                         # 基础原子组件
    ├── Button.tsx
    ├── Badge.tsx
    ├── Spinner.tsx
    └── EmptyState.tsx
```

---

## 状态管理

| 数据类型 | 方案 | 说明 |
|---|---|---|
| 服务端数据（邮件、草稿、账户、会话） | TanStack Query | 缓存、后台刷新、乐观更新 |
| SSE 流式聊天内容 | 自定义 `useChat` hook | 原生 `fetch` + `ReadableStream`，axios 不支持 SSE |
| UI 状态（侧边栏、选中邮件等） | Zustand | 轻量跨组件共享 |

hooks 统一放 `src/hooks/`，每个模块一个文件：`useResults.ts`、`useDrafts.ts`、`useAccounts.ts`、`useChat.ts`、`useSessions.ts`。

---

## 页面设计

### 收件箱 `/`

显示 AI 已处理的邮件列表，数据来自 `GET /results`（分页）。

- 顶部筛选栏：全部 / 工作 / 个人 / 财务 / 推广 / 其他
- 卡片显示：发件人、主题、AI 摘要摘录、优先级 badge、分类 badge、已执行动作、时间
- 向下滚动自动加载（`useInfiniteQuery`）
- 点击卡片跳转邮件详情

### 邮件详情 `/emails/[id]`

数据来自 `GET /results/{id}`。

- 完整 AI 摘要
- 待办事项列表（`actionItems[]`）
- 若有草稿（`draftStatus: PENDING_REVIEW`）：显示草稿内容并提供批准 / 拒绝操作
- 底部手动操作：归档、标记、已读

### 草稿审批 `/drafts`

数据来自 `GET /drafts/pending`。

- 每张卡片展示原邮件主题、收件人、草稿内容预览
- 批准 → `POST /drafts/{id}/approve`，拒绝 → `POST /drafts/{id}/reject`
- 操作后做乐观更新，立刻从列表移除，失败时回滚

### Agent 对话 `/chat`

- 左侧会话列表（`GET /agent/sessions`），顶部新建会话按钮
- 右侧聊天区：消息历史 + SSE 流式输出 + 输入框
- `POST /agent/chat` 返回 `text/event-stream`，事件：`token`（逐字）、`done`（完成）
- 支持附加邮件上下文（将选中邮件的 `providerId` + `accountId` 随消息发送）

### 账户管理 `/accounts`

数据来自 `GET /accounts` + `GET /accounts/sync-status`。

- 每个账户显示 provider 图标、邮箱地址、同步状态、上次同步时间
- `needsReauth: true` 时显示橙色重新授权提示
- 移除账户：`DELETE /accounts/{accountId}`
- 添加账户：跳转对应 OAuth 入口（同 login 页）

---

## Auth 流程

1. 用户点击登录按钮 → 浏览器跳转 `/api/v1/auth/gmail/login`（或 msgraph）
2. 后端完成 OAuth2，redirect 到 `/auth/callback?token=<jwt>&provider=<name>`
3. `/auth/callback` 页面将 token 存入 localStorage，跳转到 `/`
4. `lib/api.ts` 拦截器自动在请求头附加 JWT，并在 token 剩余 <5 分钟时主动刷新
5. 401 响应时清除 token，跳转 `/login`

---

## 视觉规范

全局暗色主题（`bg-gray-950 text-gray-100`）。

| 元素 | 样式 |
|---|---|
| 优先级 HIGH | `text-red-400 bg-red-900/30` |
| 优先级 MEDIUM | `text-yellow-400 bg-yellow-900/30` |
| 优先级 LOW | `text-gray-400 bg-gray-800` |
| 情感 POSITIVE | `text-green-400` |
| 情感 NEGATIVE | `text-red-400` |
| 重新授权警告 | `text-orange-400 bg-orange-900/30` |

