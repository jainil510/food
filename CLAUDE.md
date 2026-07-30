# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Task 1 (scaffolding) has run: `backend/` is a Spring Boot 4.1.0 + Java 21 project (`com.foodrush.backend`
base package) with dev/prod profiles, a Flyway migration for the full schema, and JPA entities for
all 9 domain tables, validated against the database. `frontend/` is a Vite + React project with
Tailwind CSS v4 and the PRD's folder structure already in place. Treat `.taskmaster/docs/prd.txt`
(the PRD) and `.taskmaster/tasks/tasks.json` (17 dependency-ordered tasks) as the source of truth
for scope and design — don't improvise architecture that contradicts them.

## Working with Task Master

Task progress is tracked in `.taskmaster/tasks/tasks.json`, not GitHub issues or a TODO file.

- `task-master list` — see all tasks and their status
- `task-master next` — get the next unblocked task to work on (respects `dependencies`)
- `task-master show <id>` — full detail + test strategy for one task
- `task-master set-status --id=<id> --status=<pending|in-progress|done>`
- `task-master expand --id=<id>` — break a task into subtasks

Inside Claude Code, prefer the `task-master-ai` MCP tools (`get_tasks`, `next_task`,
`set_task_status`, etc.) over the CLI — they operate on the same `tasks.json`. Never hand-edit
`.taskmaster/tasks/tasks.json` or `.taskmaster/config.json` directly; go through the CLI/MCP.

## Database access via MCP

The `mysql` MCP server (`.mcp.json`) connects to the `foodrush` database as `foodrush_mcp`, a
user scoped to `SELECT` only (see `database/setup_mcp_user.sql`), with
`ALLOW_INSERT/UPDATE/DELETE_OPERATION` also explicitly disabled in the MCP server env. Use it for
read-only inspection/debugging only. Schema changes and data writes belong in the application
layer (JPA entities/migrations) or a manual `mysql` session as root — not through this MCP
connection. Connection settings live in `.env` (gitignored); see `.env.example` for the required
keys (`MYSQL_HOST/PORT/USER/PASS/DB`).

The backend itself connects as a separate, non-MCP user: `foodrush_app`, a read/write MySQL user
(see `database/setup_app_user.sql`) used for actually running the application (migrations, normal
CRUD). Its credentials are the `DB_USERNAME`/`DB_PASSWORD` keys in `.env`/`.env.example` and are
required to run the backend locally — distinct from the MCP server's read-only `foodrush_mcp`
access above.

## Architecture (built in Task 1, from the PRD)

**Backend:** Spring Boot 4.1.0 (not 3.x — 3.x is past Spring Initializr's support window as of
2026-07-30), Java 21, base package `com.foodrush.backend`, layered
`controller -> service -> repository -> entity`, plus `dto`/`config`/`security`/`exception`
packages. Spring Security + JWT (24h expiry, role claim `USER`/`ADMIN`) via a
`JwtAuthenticationFilter`. MySQL via JPA, with separate dev/prod profiles
(`application-dev.yml` / `application-prod.yml`).

**Frontend:** React 18+ + Vite (currently ships React 19), `react-router-dom`, `axios`, Tailwind CSS v4 via `@tailwindcss/vite`
(v4's Vite plugin supersedes the v3 postcss/autoprefixer toolchain), with breakpoints declared via
`@theme` in `src/index.css` rather than a `tailwind.config.js`. Folder layout: `src/components`,
`src/pages`, `src/services`, `src/contexts`, `src/utils`, `src/hooks`. An `AuthContext` holds the
JWT (in `localStorage`) and sets it as the axios default `Authorization` header; a response
interceptor auto-logs-out on 401.

**Core entities:** `User`, `Restaurant`, `Category`, `FoodItem`, `Cart`/`CartItem`,
`Order`/`OrderItem`, `Address` — full fields and FK relationships are in PRD section 10.

**Rule to preserve wherever cart logic is touched:** a cart may only hold items from one
restaurant at a time (FR-14) — adding an item from a different restaurant must prompt the user to
clear the cart first, never silently mix restaurants.

**Access control tiers:** public (`/api/auth/**`, restaurant/menu browsing), authenticated
(`/api/cart/**`, `/api/orders/**`, `/api/users/**`), admin-only (`/api/admin/**`) — full endpoint
table is in PRD section 11.

**Explicit non-goals for this MVP** (don't add speculatively): payment gateway integration,
real-time/WebSocket features (live tracking, chat, push notifications), multi-currency,
multi-tier admin roles, multi-vendor cart.

**Deployment targets:** frontend → Vercel, backend → Render, database → Railway MySQL
(free/low-cost tiers — expect Render cold starts on the live demo).

## Task order

`1` Scaffold backend+frontend+DB schema → `2` Auth/JWT → `3` Restaurant/Category APIs → `4`
FoodItem/Menu APIs → `5` Cart → `6` Address → `7` Checkout/Order → `8` Profile → `9` Admin order
APIs → `10`–`15` matching frontend pages (browsing, cart/checkout, order history/profile, auth
pages, admin panel) → `16` error handling/validation/polish → `17` seeding/docs/deployment.
Dependencies are encoded in `tasks.json`; use `task-master next` rather than assuming this order
is strictly sequential.

## Commands

These are the real, verified commands for the scaffolded project (backend: Maven wrapper — no
system Maven needed; frontend: npm):

- Backend dev: `./mvnw spring-boot:run`
- Backend build: `./mvnw clean package -DskipTests`
- Frontend dev: `npm run dev` (in `frontend/`)
- Frontend build: `npm run build` (in `frontend/`)
