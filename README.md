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
│  (向量记忆 / 会话历史)             (会话上下文缓存)   │
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
| 短期记忆 | Redis 7 |
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
对话核心（GPT-4o，SSE 流式输出）。理解用户意图，决定调用哪个子 Agent，维护每个会话的对话历史。拥有三个工具：
- `analyzeSelectedEmails` — 触发 DigestAgent 分析所选邮件
- `draftEmailReply` — 通过 ActionAgent 起草回复
- `askAboutEmail` — 通过 ActionAgent 回答关于某封邮件的问题

### DigestAgent（LangGraph4j 图）
```
FetchSelected → Summarize（并发）→ GenerateDigest
```
接收前端传入的邮件列表，并发摘要每封邮件，最后由 LLM 综合生成今日总览和优先行动清单。

### ActionAgent
针对单封邮件的辅助服务，支持起草回复和邮件内容问答。**所有操作均不自动执行**，生成结果后由用户决定。

## API 参考

所有接口需要在请求头携带 JWT：`Authorization: Bearer <token>`

响应格式统一为 `{ "data": ..., "error": null, "timestamp": "..." }`

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/auth/gmail/login` | 发起 Gmail OAuth2 授权 |
| `GET` | `/auth/gmail/callback` | Gmail OAuth2 回调 |
| `GET` | `/auth/msgraph/login` | 发起 Microsoft OAuth2 授权 |
| `GET` | `/auth/msgraph/callback` | Microsoft OAuth2 回调 |

### Agent 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/agent/sessions` | 创建新会话 |
| `GET` | `/agent/sessions` | 列出当前用户的所有会话 |
| `GET` | `/agent/sessions/{id}` | 获取会话详情及完整消息历史 |
| `DELETE` | `/agent/sessions/{id}` | 删除会话 |
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
| `done` | `[DONE]` | 流结束 |

### 邮件

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/emails?maxResults=20` | 获取所有账号未读邮件 |
| `GET` | `/emails/accounts` | 列出已连接的邮件账号 |
| `POST` | `/emails/{id}/archive?accountId=` | 归档邮件 |
| `POST` | `/emails/{id}/read?accountId=` | 标为已读 |
| `POST` | `/emails/send` | 发送邮件 |

## 项目结构

```
zmail/
├── frontend/
│   ├── src/
│   │   ├── app/          # Next.js App Router 页面
│   │   ├── components/   # React 组件
│   │   ├── hooks/        # 自定义 Hooks
│   │   ├── lib/          # API 客户端、工具函数
│   │   └── types/        # 共享 TypeScript 类型
│   └── src-tauri/        # Tauri Rust 层
├── backend/
│   ├── .env              # 本地凭据（gitignored）
│   ├── .env.example      # 配置模板
│   └── src/main/java/com/zmail/
│       ├── agent/        # 多 Agent 实现（MainAgent / DigestAgent / ActionAgent）
│       │   └── node/     # LangGraph4j 节点
│       ├── config/       # Spring 配置（Security、LangChain4j、Agent 属性）
│       ├── controller/   # REST 控制器（Auth / Chat / Session / Email）
│       ├── email/        # EmailPort、GmailAdapter、MsGraphAdapter
│       ├── model/        # JPA 实体（User、AgentSession、AgentMessage 等）
│       ├── scheduler/    # 定时任务
│       └── service/      # 业务逻辑
├── docker-compose.yml
└── CLAUDE.md
```

## 使用的模型

| 场景 | 模型 | 可配置 |
|---|---|---|
| 主对话（MainAgent） | `gpt-4o` | `zmail.agent.main-model` |
| 邮件摘要 + 总览生成 | `gpt-4o` | `zmail.agent.summarize-model` |
| 批量分类（预留） | `gpt-4o-mini` | `zmail.agent.classify-model` |
| Embedding | `text-embedding-3-small` | `zmail.embedding.model-name` |

## 定时任务

| 任务 | 触发时间 | 说明 |
|---|---|---|
| `EmailSyncJob` | 每 5 分钟 | 从 Gmail / Graph 拉取新邮件 |
| `DailySummaryJob` | 每天 08:00 | 生成每日摘要 |
| `MemoryConsolidationJob` | 每天 00:00 | 压缩历史邮件记忆到 pgvector |

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