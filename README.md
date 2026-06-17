# Zmail

AI 驱动的邮件助手桌面应用。通过对话的方式帮你分析邮件、规划今日任务、起草回复，支持 Gmail 和 Outlook，LLM 核心使用 OpenAI GPT-4o。

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                  Tauri 桌面壳                        │
│  ┌─────────────────────────────────────────────┐    │
│  │          Next.js 15 + React 19              │    │
│  └──────────────────┬──────────────────────────┘    │
└─────────────────────┼───────────────────────────────┘
                      │ REST / SSE
┌─────────────────────▼───────────────────────────────┐
│              Spring Boot 3.4 后端                    │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │           多 Agent 架构                        │   │
│  │                                              │   │
│  │  用户 ←── SSE ──→ [MainAgent]                │   │
│  │                       │                     │   │
│  │            ┌──────────┴──────────┐          │   │
│  │      [DigestAgent]        [ActionAgent]     │   │
│  │      邮件摘要 + 规划         单封邮件处理      │   │
│  └──────────────────────────────────────────────┘   │
│                                                     │
│  ┌─────────────┐  ┌────────────┐  ┌─────────────┐  │
│  │  Gmail API  │  │ MS Graph   │  │  Scheduler  │  │
│  └─────────────┘  └────────────┘  └─────────────┘  │
└──────────┬──────────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────┐
│  PostgreSQL 16 + pgvector        Redis 7             │
│  (向量记忆 / 会话历史)         (同步水印 / 会话缓存)  │
└─────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 技术 |
|---|---|
| 桌面壳 | Tauri 2 (Rust) |
| 前端 | Next.js 15 · React 19 · TypeScript · Tailwind CSS 4 |
| 后端 | Spring Boot 3.4 · Java 17 |
| Agent 编排 | LangGraph4j 1.8 |
| LLM 交互 | LangChain4j 1.0 · OpenAI API |
| Embedding | OpenAI `text-embedding-3-small` (1536 维) |
| 向量记忆 | PostgreSQL 16 + pgvector |
| 短期记忆 / 同步水印 | Redis 7 |
| 邮件接入 | Gmail API · Microsoft Graph API |
| 定时任务 | Spring Scheduler |

## 快速开始

### 前置依赖

- Java 17
- Node.js 22+
- Rust (stable) — 仅桌面模式需要
- Docker + Docker Compose

### 1. 配置环境变量

```bash
cp backend/.env.example backend/.env
# 编辑 backend/.env，填入 API Key 和 OAuth 凭据
```

| 变量 | 必填 | 说明 |
|---|---|---|
| `OPENAI_API_KEY` | 是 | OpenAI API 密钥（LLM + Embedding） |
| `OPENAI_BASE_URL` | 否 | 自定义接口地址，默认 `https://api.openai.com/v1` |
| `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` | 是 | Google Cloud Console OAuth 凭据 |
| `JWT_SECRET` | 是 | 随机字符串，至少 32 位 |
| `MSGRAPH_CLIENT_ID` / `MSGRAPH_CLIENT_SECRET` | 否 | Azure 应用注册凭据（接 Outlook） |

> **国内网络**：设置 `OPENAI_BASE_URL` 指向代理地址（如 `https://your-proxy.com/v1`）即可，无需其他改动。

### 2. 启动基础设施

```bash
docker-compose up -d
```

### 3. 启动后端

**Windows (PowerShell)**
```powershell
cd backend
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**macOS / Linux**
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

验证：`http://localhost:8080/api/v1/health`

### 4. 启动前端

```bash
cd frontend && npm install

# 浏览器模式
npm run dev

# Tauri 桌面模式
npm run tauri dev
```

## Agent 架构详解

Zmail 采用三层 Agent 设计：

### MainAgent
对话核心（GPT-4o，SSE 流式输出）。理解用户意图，决定调用哪个子 Agent，维护每个会话的对话历史。拥有四个工具：
- `analyzeSelectedEmails` — 触发 DigestAgent 分析所选邮件
- `draftEmailReply` — 通过 ActionAgent 起草回复
- `askAboutEmail` — 通过 ActionAgent 回答关于某封邮件的问题
- `searchEmailsByMeaning` — 通过向量相似度搜索历史邮件

#### 对话记忆管理（SessionMemoryManager）

每条对话消息持久化到 PostgreSQL，同时通过 `SessionMemoryManager` 在 LangChain4j 侧维护一个**增量摘要 + 滑动窗口**的记忆层：

```
PostgreSQL（agent_messages）← 完整记录，永久保留
agent_sessions.compressed_until ← 已压缩到第几条（重启后恢复用）

ensureSeeded（首次请求时）
  └─ LangChain4j Memory = [SystemMessage: 历史摘要]
                        + messages[compressed_until..end]
                          （未压缩消息 + 活跃窗口，最多 49 条）

maybeCompress（每次 ASSISTANT 回复后）
  └─ 窗口外新增消息 >= batchSize(20) 时触发
     → 仅压缩新增批次（增量），非全量重压缩
     → compress(旧摘要, messages[compressed_until..outsideWindow])
     → 更新 summary + compressed_until
```

| 参数 | 默认值 | 说明 |
|---|---|---|
| `memory-window-size` | 30 | LLM 始终看到的最近 N 条消息（须为偶数） |
| `memory-compress-batch-size` | 20 | 攒满 N 条再压缩（须为偶数，保证完整对话轮次） |

- 服务重启后从 `compressed_until` 恢复，LLM 上下文不丢失
- 每次压缩只送新批次消息，token 消耗恒定（O(1)，不随会话变长而增加）
- `windowSize` 和 `batchSize` 必须为偶数，启动时自动校验

### DigestAgent（LangGraph4j 图）
```
FetchSelected → Summarize（并发）→ GenerateDigest
```
接收前端传入的邮件列表，并发摘要每封邮件，最后由 LLM 综合生成今日总览和优先行动清单。

**摘要复用优化**：`FetchSelectedNode` 在发起 API 请求前先查询 DB，对已完成深度分析（`analyzed=true`）的邮件直接复用已有摘要，跳过该封邮件的 LLM 调用。只有从未分析过的邮件才触发 `SummarizeNode` 中的 LLM 请求，大幅降低重复 digest 的 token 消耗。

### ActionAgent
针对单封邮件的辅助服务，支持起草回复和邮件内容问答。**所有操作均不自动执行**，生成结果后由用户决定。

## 邮件同步机制

### 触发时机

| 触发源 | 时机 | 覆盖范围 |
|---|---|---|
| **初始同步**（`InitialSyncService`） | OAuth 授权完成后（新登录、绑定新账户、重新授权） | 最近 3 天，最多 100 封/账户 |
| **定时同步**（`EmailSyncJob`） | 每 5 分钟自动执行 | 上次水印时间之后，最多 50 封/账户 |
| **手动拉取**（`GET /emails`） | 前端主动调用 | 当前未读邮件，实时透传，**不入库不分类** |

### 同步水印（SyncWatermarkService）

水印持久化到 **Redis**，服务重启后自动恢复，不会丢失同步进度。格式：`zmail:sync:watermark:{userId}`，TTL 90 天。

仅当无历史水印时（新用户）回退到 `now - 24h` 兜底。

### 并发保护

- 同一用户的初始同步正在进行中时，定时任务会自动跳过该用户，避免 LLM classify 重复调用
- `RunGuard` 保证同一用户定时同步不并发（60s 最小间隔）
- `isAlreadyProcessed()` + DB unique 约束（`user_id, account_id, email_provider_id`）双重防止重复入库
- 多账户中单个账户失败时，其余账户继续正常同步

### AI 处理流水线（三阶段按需触发）

```
阶段 1 — 后台同步（自动，每 5 分钟）
─────────────────────────────────────────────────────
fetchRecent(since=watermark)（Gmail / Graph，跳过 needsReauth 账号）
    │
    ├─ 已存在 processing_results → 跳过
    │
    └─ 新邮件 → EmailProcessingAgent（gpt-4o-mini）
                  输出：category / priority / sentiment /
                        requiresResponse / recommendedAction
                  analyzed = false
                  → 写 processing_results

阶段 2 — 按需深度分析（用户首次打开邮件详情页时自动触发）
─────────────────────────────────────────────────────
POST /results/{id}/analyze
    └─ SELECT FOR UPDATE（防并发重复调用）
       → 拉取邮件原文 → EmailSummarizeAgent（gpt-4o）
           输出：summary / actionItems
           analyzed = true → 向量 Embedding 写入 pgvector

阶段 3 — 手动生成草稿（用户主动点击"生成草稿"）
─────────────────────────────────────────────────────
POST /results/{id}/draft
    └─ 若 draftStatus == PENDING_REVIEW → 直接返回，不重复调用 LLM
       否则 → ActionAgentService.draftReply()（gpt-4o）
              → draftStatus = PENDING_REVIEW
              用户可在详情页审批发送或拒绝后重新生成
```

## API 参考

所有接口需要在请求头携带 JWT：`Authorization: Bearer <token>`

响应格式统一为 `{ "data": ..., "error": null, "timestamp": "..." }`

### 用户

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/users/me` | 当前登录用户信息（id、email、name） |

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/auth/gmail/login` | 发起 Gmail OAuth2 授权 |
| `GET` | `/auth/gmail/callback` | Gmail OAuth2 回调，返回 JWT |
| `POST` | `/auth/gmail/link-init` | 已登录用户绑定新 Gmail 账户，返回授权 URL |
| `GET` | `/auth/msgraph/login` | 发起 Microsoft OAuth2 授权 |
| `GET` | `/auth/msgraph/callback` | Microsoft OAuth2 回调，返回 JWT |
| `POST` | `/auth/msgraph/link-init` | 已登录用户绑定新 Outlook 账户，返回授权 URL |
| `POST` | `/auth/token/refresh` | 用当前有效 JWT 换取新 JWT（无需重走 OAuth） |

> **JWT 续期建议**：前端解析 JWT payload 的 `exp` 字段，在过期前 5 分钟调用 `/auth/token/refresh` 静默续期，避免用户感知到登录中断。JWT 过期后该接口不可用，需重走 OAuth。

### 邮件处理结果

AI 同步处理完的邮件存在 `processing_results` 表，是前端主收件箱视图的主要数据来源。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/results?page=0&size=20` | 分页获取当前用户所有处理结果，按邮件接收时间倒序 |
| `GET` | `/results?page=0&size=20&category=WORK` | 按分类过滤（`WORK` / `PERSONAL` / `FINANCE` / `PROMOTIONS` / `OTHER`） |
| `GET` | `/results/{id}` | 获取单条处理结果详情 |
| `POST` | `/results/{id}/analyze` | 触发按需深度分析（幂等，`analyzed=true` 后直接返回） |
| `POST` | `/results/{id}/draft` | 手动生成 AI 回复草稿（幂等，存在待审批草稿时直接返回） |

处理结果包含字段：`category`、`priority`、`sentiment`、`receivedAt`、`summary`、`actionItems`、`analyzed`、`actionTaken`、`draftStatus` 等。

### 草稿管理

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/drafts/pending?page=0&size=20` | 待审批草稿列表（AI 生成 + 用户手写） |
| `POST` | `/drafts` | 手动新建草稿（`accountId`、`to`、`subject`、`body`） |
| `PATCH` | `/drafts/{id}` | 修改草稿内容（`body` / `subject`，仅 PENDING_REVIEW 状态可修改） |
| `POST` | `/drafts/{id}/approve` | 审批通过并发送邮件（重复请求返回 409） |
| `POST` | `/drafts/{id}/reject` | 拒绝草稿（拒绝后可重新生成） |

### Agent 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/agent/sessions` | 创建新会话 |
| `GET` | `/agent/sessions` | 列出当前用户的所有会话 |
| `GET` | `/agent/sessions/{id}/messages` | 获取会话完整消息历史 |
| `DELETE` | `/agent/sessions/{id}` | 删除会话（同步清理内存中的 LangChain4j memory） |
| `POST` | `/agent/chat` | 发送消息，返回 SSE 流 |

#### 发送消息（SSE）

```http
POST /api/v1/agent/chat
Content-Type: application/json
Authorization: Bearer <token>

{
  "sessionId": "uuid",
  "message": "帮我分析一下今天的邮件",
  "emails": [
    { "providerId": "<gmail/graph message id>", "accountId": "<uuid>" }
  ]
}
```

`emails` 可选，传入时 MainAgent 可以对这些邮件进行分析和回复起草。

#### SSE 事件

| 事件名 | 数据 | 说明 |
|---|---|---|
| `token` | 文本片段 | LLM 流式输出的增量 token |
| `tool_start` | 工具描述文本 | Agent 开始调用某个工具（如"正在分析邮件…"） |
| `session_title` | 标题字符串 | 首轮对话后异步生成的会话标题 |
| `done` | `[DONE]` | 流结束 |
| `error` | 错误描述 | LLM 或工具调用失败，前端展示为 assistant 消息 |

### 账号管理

一个用户可绑定多个邮件账号（多个 Gmail、多个 Outlook 均支持）。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/accounts` | 列出当前用户所有已绑定邮件账号 |
| `DELETE` | `/accounts/{id}` | 移除一个绑定账号（邮件处理历史保留） |
| `GET` | `/accounts/{id}/reauth` | 获取该账号的 OAuth 重授权 URL |
| `GET` | `/accounts/sync-status` | 查询初始同步状态（`RUNNING` / `DONE` / `FAILED`） |

`GET /accounts` 返回字段：

```json
{
  "id": "uuid",
  "provider": "GMAIL",
  "accountEmail": "user@gmail.com",
  "needsReauth": false,
  "createdAt": "2026-05-20T10:00:00Z"
}
```

#### OAuth Token 自动刷新

后端每次访问邮件账号前会检查 access token 是否在 5 分钟内过期，到期则自动用 refresh token 换取新令牌（Microsoft 会同时返回新 refresh token，后端会一并保存）。**正常情况下用户无需重新登录邮箱账号。**

若 refresh token 本身失效（长期未使用、用户在 Google/Azure 侧手动撤销授权），后端会将该账号标记为 `needsReauth: true` 并停止对其同步，不影响其他账号。前端可通过 `GET /accounts` 检测该字段并提示用户重新授权：

1. 调用 `GET /accounts/{id}/reauth` 获取 `loginPath`
2. 通过 Tauri `shell.open()` 在浏览器中打开完整 OAuth URL
3. OAuth 完成后 `needsReauth` 自动清除，同步恢复正常

### 邮件

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/emails?maxResults=20` | 获取所有账号未读邮件原始列表（实时，不入库） |
| `GET` | `/emails/{id}?accountId=` | 获取单封邮件原文 |
| `POST` | `/emails/{id}/archive?accountId=` | 归档邮件 |
| `POST` | `/emails/{id}/flag?accountId=` | 标记邮件为待办 |
| `POST` | `/emails/{id}/read?accountId=` | 标为已读 |
| `POST` | `/emails/send` | 发送邮件（`accountId`、`to`、`subject`、`body` 均必填） |

## 项目结构

```
zmail/
├── frontend/
│   ├── src/
│   │   ├── app/          # Next.js App Router 页面
│   │   ├── components/   # React 组件
│   │   ├── hooks/        # 自定义 Hooks（useResults、useDrafts 等）
│   │   ├── lib/          # API 客户端（api.ts）、工具函数
│   │   └── types/        # 共享 TypeScript 类型
│   └── src-tauri/        # Tauri Rust 层
├── backend/
│   ├── .env              # 本地凭据（gitignored）
│   ├── .env.example      # 配置模板
│   └── src/main/java/com/zmail/
│       ├── agent/
│       │   ├── action/   # ActionAgentService、EmailProcessingAgent（邮件分析 AI Service）
│       │   ├── chat/     # MainAgent、MainAgentService、MainAgentTools
│       │   │             # SessionMemoryManager、ConversationSummaryAgent
│       │   ├── digest/   # DigestAgentGraph、DigestAgentState
│       │   │   └── node/ # FetchSelectedNode、SummarizeNode、GenerateDigestNode
│       │   └── model/    # 共享数据类型（EmailRef、DigestResult 等）
│       ├── config/       # Spring 配置（Security、LangChain4j、Agent 属性）
│       ├── controller/   # REST 控制器
│       ├── email/        # EmailPort、GmailAdapter、MsGraphAdapter
│       ├── model/        # JPA 实体（User、AgentSession、ProcessingResult 等）
│       ├── scheduler/    # 定时任务（EmailSyncJob）
│       └── service/      # 业务逻辑（EmailProcessingService、DraftService 等）
├── docker-compose.yml
└── CLAUDE.md
```

## 使用的模型

| 场景 | 模型 | 可配置 |
|---|---|---|
| 主对话（MainAgent） | `gpt-4o` | `zmail.agent.main-model` |
| 邮件摘要 + 总览生成 | `gpt-4o` | `zmail.agent.summarize-model` |
| 对话历史压缩 | `gpt-4o-mini` | `zmail.agent.compress-model` |
| 邮件同步分类（category / priority / sentiment） | `gpt-4o-mini` | `zmail.agent.classify-model` |
| Embedding | `text-embedding-3-small` | `zmail.embedding.model-name` |

## 定时任务

| 任务 | 触发时间 | 状态 | 说明 |
|---|---|---|---|
| `EmailSyncJob` | 每 5 分钟 | ✅ 已实现 | 按水印时间拉取新邮件 → 仅分类（category / priority / sentiment）→ 写 `processing_results`；摘要和草稿按需触发 |
| `MemoryConsolidationJob` | 每天 00:30 | ✅ 已实现 | 对已完成深度分析（`analyzed=true`）但尚未 Embedding 的邮件批量写入 pgvector |

## 常用命令

```bash
# 后端测试
cd backend && ./mvnw test

# 前端类型检查
cd frontend && npm run type-check

# 构建 Tauri 发行版
cd frontend && npm run tauri build

# 重置数据库（dev）
docker-compose down -v && docker-compose up -d
```

## OAuth 配置指引

### Gmail

1. [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials → 创建 OAuth 2.0 客户端 ID（Web 应用）
2. 授权回调 URI：`http://localhost:8080/api/v1/auth/gmail/callback`
3. 启用 Gmail API（Library → Gmail API → Enable）
4. OAuth 同意屏幕 → Test users 中添加测试账号

### Microsoft Graph（Outlook）

1. [Azure 门户](https://portal.azure.com) → App Registrations → New registration
2. 重定向 URI：`http://localhost:8080/api/v1/auth/msgraph/callback`
3. API Permissions → 添加 `Mail.Read`、`Mail.Send`、`Mail.ReadWrite`、`offline_access`

## License

MIT
