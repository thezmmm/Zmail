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

| 层 | 技术                                                  |
|---|-----------------------------------------------------|
| 桌面壳 | Tauri 2 (Rust)                                      |
| 前端 | Next.js 15 · React 19 · TypeScript · Tailwind CSS 4 |
| 后端 | Spring Boot 3.4 · Java 17                            |
| Agent 编排 | LangGraph4j 1.0                                     |
| LLM 交互 | LangChain4j 1.0 · Claude API                        |
| 向量记忆 | PostgreSQL 16 + pgvector                            |
| 短期记忆 | Redis 7                                             |
| 邮件接入 | Gmail API · Microsoft Graph API                     |
| 定时任务 | Spring Scheduler                                    |

## 快速开始

### 前置依赖

- Java 17
- Node.js 22+
- Rust (stable) — 仅桌面模式需要
- Docker + Docker Compose

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入真实的 API Key 和 OAuth 凭据
```

最低限度需要填写：

| 变量 | 说明 |
|---|---|
| `ANTHROPIC_API_KEY` | Claude API 密钥，从 console.anthropic.com 获取 |
| `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` | Google Cloud Console OAuth 应用凭据 |
| `MSGRAPH_CLIENT_ID` / `MSGRAPH_CLIENT_SECRET` | Azure 应用注册凭据 |

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

### 4a. 启动前端（浏览器）

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

### 4b. 启动桌面应用（Tauri）

```bash
cd frontend
npm run tauri dev
```

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
│   └── src/main/java/com/zmail/
│       ├── agent/        # LangGraph4j 图定义
│       ├── config/       # Spring 配置类
│       ├── controller/   # REST 控制器
│       ├── email/        # Gmail / MS Graph 适配器
│       ├── memory/       # Redis + pgvector 记忆服务
│       ├── model/        # JPA 实体
│       ├── scheduler/    # 定时任务
│       └── service/      # 业务逻辑
├── docker-compose.yml
├── .env.example
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
    ├──► Reply     (需要回复)                            │
    ├──► Archive   (低优先级归档)                        │
    └──► Flag      (需人工处理)  ◄──────────────────────┘
```

- **短期记忆**：Redis 缓存最近 N 封邮件的上下文窗口
- **长期记忆**：pgvector 存储邮件内容向量，支持语义搜索

## 定时任务

| 任务 | 触发时间 | 说明 |
|---|---|---|
| EmailSyncJob | 每 5 分钟 | 从 Gmail / Graph 拉取新邮件 |
| DailySummaryJob | 每天 08:00 | 生成每日摘要推送 |
| MemoryConsolidationJob | 每天 00:00 | 压缩历史邮件记忆到 pgvector |

## 使用的 Claude 模型

| 场景 | 模型 |
|---|---|
| 摘要、推理、回复生成 | `claude-sonnet-4-6` |
| 批量分类（降低成本） | `claude-haiku-4-5-20251001` |

## 常用命令

```bash
# 后端测试（需要 Docker 运行）
cd backend && ./mvnw test

# 前端类型检查
cd frontend && npm run type-check

# 构建 Tauri 发行版
cd frontend && npm run tauri build

# 重置数据库（仅 dev）
docker-compose down -v && docker-compose up -d
```

## OAuth 配置指引

**Gmail**
1. 前往 [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials
2. 创建 OAuth 2.0 客户端 ID（应用类型：Web）
3. 授权回调 URI：`http://localhost:8080/api/v1/auth/gmail/callback`
4. 启用 Gmail API

**Microsoft Graph / Outlook**
1. 前往 [Azure 门户](https://portal.azure.com) → App Registrations → New registration
2. 重定向 URI：`http://localhost:8080/api/v1/auth/msgraph/callback`
3. 在 API Permissions 中添加 `Mail.Read`、`Mail.Send`、`Mail.ReadWrite`

## License

MIT