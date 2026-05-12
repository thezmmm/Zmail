# Zmail

AI 驱动的邮件 Agent 桌面应用。自动读取、分类、摘要并对邮件采取行动，支持 Gmail 和 Outlook，以 Claude 作为推理核心。

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                  Tauri 桌面壳                        │
│  ┌─────────────────────────────────────────────┐    │
│  │          Next.js 15 + React 19              │    │
│  └──────────────────┬──────────────────────────┘    │
└─────────────────────┼───────────────────────────────┘
                      │ REST / HTTP
┌─────────────────────▼───────────────────────────────┐
│              Spring Boot 3.4 后端                    │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │  LangGraph4j Agent 工作流                     │   │
│  │  EmailFetch → Classify → Summarize → Action  │   │
│  └──────────────────────────────────────────────┘   │
│                                                     │
│  ┌─────────────┐  ┌────────────┐  ┌─────────────┐  │
│  │  Gmail API  │  │ MS Graph   │  │  Scheduler  │  │
│  └─────────────┘  └────────────┘  └─────────────┘  │
└──────────┬──────────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────┐
│  PostgreSQL 16 + pgvector        Redis 7             │
│  (向量记忆 / 邮件元数据)           (会话上下文缓存)   │
└─────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 技术 |
|---|---|
| 桌面壳 | Tauri 2 (Rust) |
| 前端 | Next.js 15 · React 19 · TypeScript · Tailwind CSS 4 |
| 后端 | Spring Boot 3.4 · Java 17 |
| Agent 编排 | LangGraph4j 1.8 |
| LLM 交互 | LangChain4j 1.0 · Claude API |
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

| 变量 | 说明 |
|---|---|
| `ANTHROPIC_API_KEY` | Claude API 密钥 |
| `OPENAI_API_KEY` | OpenAI API 密钥（用于 Embedding） |
| `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` | Google Cloud Console OAuth 凭据 |
| `JWT_SECRET` | 随机字符串，至少 32 位 |

> **国内网络**：在 `backend/.env` 中设置 `PROXY_HOST=127.0.0.1` 和 `PROXY_PORT=<端口>`，所有对外请求均走代理。

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

## 邮件账号接入

### 连接 Gmail

1. 访问 `http://localhost:8080/api/v1/auth/gmail/login`
2. 完成 Google OAuth2 授权
3. 授权后从回调 URL 取 JWT：`?token=<JWT>&provider=gmail`

### 连接 Outlook

1. 访问 `http://localhost:8080/api/v1/auth/msgraph/login`
2. 完成 Microsoft OAuth2 授权
3. 授权后从回调 URL 取 JWT

## API 参考

所有接口需要在请求头携带 JWT：`Authorization: Bearer <token>`

响应格式统一为 `{ "data": ..., "error": null, "timestamp": "..." }`

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
│       ├── agent/        # LangGraph4j 图定义
│       ├── config/       # Spring 配置（Security、JWT、代理）
│       ├── controller/   # REST 控制器
│       ├── email/        # EmailPort、GmailAdapter、MsGraphAdapter
│       ├── memory/       # Redis + pgvector 记忆服务
│       ├── model/        # JPA 实体
│       ├── scheduler/    # 定时任务
│       └── service/      # 业务逻辑
├── docker-compose.yml
└── CLAUDE.md
```

## Agent 工作流

```
EmailFetch
    │
    ▼
Classify ──────────────────────────────────────────────┐
    │  LLM 分配类别 / 优先级 / 情感                      │
    ▼                                                   │
Summarize                                              │
    │  LLM 生成摘要 + 行动项                             │
    ▼                                                   │
ActionDecide                                           │
    │                                                   │
    ├──► Reply     （需要回复）                           │
    ├──► Archive   （低优先级归档）                       │
    └──► Flag      （需人工处理）◄──────────────────────┘
```

- **短期记忆**：Redis 缓存最近 N 封邮件的处理上下文
- **长期记忆**：pgvector 存储邮件内容向量，支持语义检索历史邮件

## 定时任务

| 任务 | 触发时间 | 说明 |
|---|---|---|
| EmailSyncJob | 每 5 分钟 | 从 Gmail / Graph 拉取新邮件并触发 Agent 处理 |
| DailySummaryJob | 每天 08:00 | 生成每日邮件摘要 |
| MemoryConsolidationJob | 每天 00:00 | 压缩历史邮件记忆到 pgvector |

## 使用的 Claude 模型

| 场景 | 模型 |
|---|---|
| 摘要、推理、回复生成 | `claude-sonnet-4-6` |
| 批量分类（降低成本） | `claude-haiku-4-5-20251001` |

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

**Gmail**
1. [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials → 创建 OAuth 2.0 客户端 ID（Web 应用）
2. 授权回调 URI：`http://localhost:8080/api/v1/auth/gmail/callback`
3. 启用 Gmail API（Library → Gmail API → Enable）
4. OAuth consent screen → Test users 中添加测试账号

**Microsoft Graph**
1. [Azure 门户](https://portal.azure.com) → App Registrations → New registration
2. 重定向 URI：`http://localhost:8080/api/v1/auth/msgraph/callback`
3. API Permissions → 添加 `Mail.Read`、`Mail.Send`、`Mail.ReadWrite`、`offline_access`

## License

MIT