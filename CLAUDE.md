# Zmail — Email Agent Project

## Overview
Zmail is an AI-powered email agent desktop application that reads, summarizes, categorizes, and acts on emails intelligently using Claude LLM. It is packaged as a native desktop app via Tauri and backed by a Spring Boot service that orchestrates multi-step agent workflows with LangGraph4j.

## Tech Stack

| Layer | Technology |
|---|---|
| Desktop UI | Tauri 2.x (Rust shell) |
| Frontend | Next.js 15 + React 19 + TypeScript |
| Backend | Spring Boot 3.4 |
| Agent orchestration | LangGraph4j 0.6 |
| LLM interaction | LangChain4j 1.0 + Claude API (claude-sonnet-4-6) |
| Vector memory | PostgreSQL 16 + pgvector |
| Cache / session memory | Redis 7 |
| Email providers | Gmail API + Microsoft Graph API |
| Scheduled tasks | Spring Scheduler |

## Project Structure

```
zmail/
├── frontend/                  # Next.js + Tauri app
│   ├── src/
│   │   ├── app/               # Next.js App Router pages
│   │   ├── components/        # React components
│   │   ├── hooks/             # Custom React hooks
│   │   ├── lib/               # API clients, utilities
│   │   └── types/             # Shared TypeScript types
│   ├── src-tauri/             # Tauri Rust layer
│   │   ├── src/
│   │   ├── Cargo.toml
│   │   └── tauri.conf.json
│   ├── package.json
│   └── next.config.ts
├── backend/                   # Spring Boot service
│   ├── src/main/java/com/zmail/
│   │   ├── agent/             # LangGraph4j graph definitions
│   │   ├── config/            # Spring config classes
│   │   ├── controller/        # REST controllers
│   │   ├── email/             # Gmail + MS Graph adapters
│   │   ├── memory/            # Redis + pgvector memory services
│   │   ├── model/             # JPA entities
│   │   ├── scheduler/         # Spring Scheduler jobs
│   │   └── service/           # Business logic
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-dev.yml
│   └── pom.xml
├── docker-compose.yml         # PostgreSQL + pgvector + Redis
├── .env.example
└── CLAUDE.md
```

## Development Setup

### Prerequisites
- Java 17
- Node.js 22+
- Rust (stable, for Tauri)
- Docker + Docker Compose

### Start infrastructure
```bash
docker-compose up -d
```

### Start backend
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Start frontend (web dev mode)
```bash
cd frontend
npm run dev
```

### Start frontend (Tauri desktop)
```bash
cd frontend
npm run tauri dev
```

## Environment Variables
Copy `.env.example` to `.env` and fill in all required values before starting. The backend reads these via `application.yml` property bindings.

Key variables:
- `ANTHROPIC_API_KEY` — Claude API key
- `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` — Google OAuth app credentials
- `MSGRAPH_CLIENT_ID` / `MSGRAPH_CLIENT_SECRET` — Azure app registration
- `DB_URL` / `DB_USER` / `DB_PASSWORD` — PostgreSQL connection
- `REDIS_HOST` / `REDIS_PORT` — Redis connection

## Agent Design

The core agent is a LangGraph4j state graph with the following nodes:

```
[EmailFetch] → [Classify] → [Summarize] → [ActionDecide]
                                              ↓
                                    [Reply / Archive / Flag]
```

- **EmailFetch**: pulls emails from Gmail/Graph adapters
- **Classify**: LLM assigns category, priority, sentiment
- **Summarize**: LLM generates concise summary + action items
- **ActionDecide**: routes to appropriate action node based on user rules
- Memory is persisted to pgvector (semantic search) and Redis (recent context window)

## Scheduled Jobs
| Job | Schedule | Description |
|---|---|---|
| EmailSyncJob | every 5 min | Fetch new emails from all providers |
| DailySummaryJob | 08:00 daily | Generate daily digest via Claude |
| MemoryConsolidationJob | 00:00 daily | Compress old email memories into pgvector |

## API Conventions
- REST: `http://localhost:8080/api/v1/...`
- All responses: `{ data, error, timestamp }`
- Auth: JWT via Spring Security (OAuth2 Resource Server)

## Default Models
- Primary reasoning: `claude-sonnet-4-6`
- Bulk classification (cost saving): `claude-haiku-4-5-20251001`
- Embeddings: pgvector with text embeddings via LangChain4j embedding model

## Key Commands
```bash
# Run backend tests
cd backend && ./mvnw test

# Run frontend type check
cd frontend && npm run type-check

# Build Tauri release
cd frontend && npm run tauri build

# Reset DB schema (dev only)
docker-compose down -v && docker-compose up -d
```