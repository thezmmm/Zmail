# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Zmail — Email Agent Project

## Overview
Zmail is an AI-powered email agent desktop application that reads, summarizes, categorizes, and acts on emails intelligently using OpenAI LLM. It is packaged as a native desktop app via Tauri and backed by a Spring Boot service that orchestrates multi-step agent workflows with LangGraph4j.

## Tech Stack

| Layer | Technology |
|---|---|
| Desktop UI | Tauri 2.x (Rust shell) |
| Frontend | Next.js 15 + React 19 + TypeScript |
| Backend | Spring Boot 3.4 |
| Agent orchestration | LangGraph4j 0.6 |
| LLM interaction | LangChain4j 1.0 + OpenAI API (gpt-4o / gpt-4o-mini) |
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
│   ├── package.json
│   └── next.config.ts
├── backend/                   # Spring Boot service
│   ├── src/main/java/com/zmail/
│   │   ├── agent/             # LangGraph4j graph + chat agent + digest agent
│   │   ├── config/            # Spring beans (Security, LangChain4j, JWT, CORS)
│   │   ├── controller/        # REST controllers
│   │   ├── email/             # Gmail + MS Graph port + adapters
│   │   ├── model/             # JPA entities + repositories
│   │   ├── scheduler/         # Spring Scheduler jobs
│   │   └── service/           # Business logic
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-dev.yml
│   └── pom.xml
├── docker-compose.yml
├── .env.example
└── CLAUDE.md
```

## Development Setup

### Prerequisites
- Java 17, Node.js 22+, Rust (stable), Docker + Docker Compose

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

## Key Commands
```bash
# Backend tests
cd backend && ./mvnw test

# Frontend type check
cd frontend && npm run type-check

# Build Tauri release
cd frontend && npm run tauri build

# Reset DB schema (dev only)
docker-compose down -v && docker-compose up -d
```

## Environment Variables
Copy `.env.example` to `.env`. The backend reads these via `application.yml` property bindings.

Key variables:
- `OPENAI_API_KEY` — OpenAI API key
- `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` — Google OAuth app credentials
- `MSGRAPH_CLIENT_ID` / `MSGRAPH_CLIENT_SECRET` — Azure app registration
- `DB_URL` / `DB_USER` / `DB_PASSWORD` — PostgreSQL connection
- `REDIS_HOST` / `REDIS_PORT` — Redis connection

## API Conventions
- Base URL: `http://localhost:8080/api/v1/`
- All responses: `{ data, error, timestamp }`
- Auth: JWT in `Authorization: Bearer <token>` header
- Pagination: Spring Data `Pageable` — `?page=0&size=20&sort=processedAt,desc`; `GET /results` returns `Page<ProcessingResult>` (wrapped in `data`)
- SSE streaming: `POST /agent/chat` returns `text/event-stream`; events are `token` (partial text) and `done` (signals completion)

## Auth Flow
1. Frontend redirects browser to `/api/v1/auth/gmail/login` or `/api/v1/auth/msgraph/login`
2. Backend completes OAuth2, then redirects to `{FRONTEND_URL}/auth/callback?token=<jwt>&provider=<name>`
3. Frontend (`/auth/callback`) stores JWT in localStorage via `setToken()`, then navigates to `/`
4. `lib/api.ts` attaches JWT to every request and proactively refreshes when <5 min from expiry
5. On 401, `lib/api.ts` clears token and redirects to `/login`

## Frontend Routes (implemented)
| Route | Description |
|---|---|
| `/` | Backend health-check / landing |
| `/login` | OAuth entry — Gmail and Outlook buttons |
| `/auth/callback` | Receives `?token=` after OAuth, stores it, redirects to `/` |

## Agent Design

```
[EmailFetch] → [Classify] → [Summarize] → [ActionDecide]
                                              ↓
                                    [Reply / Archive / Flag]
```

- **EmailFetch**: pulls emails from Gmail/Graph adapters
- **Classify**: LLM assigns category, priority, sentiment (`gpt-4o-mini`)
- **Summarize**: LLM generates concise summary + action items (`gpt-4o`)
- **ActionDecide**: routes to appropriate action node based on user rules
- Memory: pgvector (semantic search) + Redis (recent context window); old messages are compressed via `ConversationSummaryAgent` when window exceeds `memoryWindowSize`

## Scheduled Jobs
| Job | Schedule | Description |
|---|---|---|
| EmailSyncJob | every 5 min | Fetch new emails from all providers |
| DailySummaryJob | 08:00 daily | Generate daily digest via OpenAI |
| MemoryConsolidationJob | 00:00 daily | Compress old email memories into pgvector |

## Default Models
- Primary reasoning / summarize / compress: `gpt-4o`
- Bulk classification: `gpt-4o-mini`
- Embeddings: `text-embedding-3-small` via LangChain4j OpenAI embedding model