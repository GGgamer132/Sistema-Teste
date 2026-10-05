# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Circula Book: a system for managing a network of community libraries (CEFET/RJ course project).

**This branch (`dev`) is a REDUCED version** built for an evaluator. It contains only six flows:
login (seeded users only), book search, book details, reservation, loan and return. Two profiles:
`COMUM` (menu: "Buscar livros") and `BIBLIOTECARIO` (menu: "Empréstimo", "Devolução").
There is no Admin, no self sign-up, no transfers, notifications, acquisition demands, circulation
history, dashboard, reports, "my loans/reservations/history" screens, or catalog/copy registration.

`CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md` describes the **complete system** that lives in branch
`sistema-final`. Use it only to understand intent (§3 rules, §4 copy state machine, §5.1 reservation);
for this branch, `MANUAL_DO_DESENVOLVEDOR.md` and the code are the source of truth. Do not
reintroduce removed features. Rules:

- If something is ambiguous, ask instead of deciding.
- Code, messages and UI text are in **Brazilian Portuguese**.
- Make small, descriptive commits.

## Commands

Requires a local PostgreSQL with database `circula_book_db`, user `circula_user`, password `circula_senha_123` (see `backend/circula-book/src/main/resources/application.properties`).

Backend (Java 17, Spring Boot 4.x, Maven; there is no Maven wrapper):

```
cd backend/circula-book
mvn spring-boot:run          # http://localhost:8080
mvn compile
```

`ddl-auto=create` drops and recreates the schema on every start, then `data.sql` reseeds it (`defer-datasource-initialization=true`). Any data created at runtime is lost on restart.

Frontend (React 19, TypeScript, Vite, Tailwind v4, react-router-dom v7):

```
cd frontend
npm install
npm run dev      # http://localhost:5173, proxies /api -> localhost:8080
npm run build    # tsc -b && vite build (this is the type check)
npm run lint
```

## Architecture

**Blackboard pattern.** The database is the shared "blackboard", and the `exemplar` table's `status` column is the central state. Services in `service/` act as knowledge sources. `FilaEsperaService` is called **synchronously, inside the same transaction**, whenever a copy becomes free (return or expiry of a ready reservation): no queue → `DISPONIVEL`; queue → `RESERVADO` and the first reservation becomes `DISPONIVEL` with 3 days to pick up. A `@Scheduled` job expires overdue ready reservations. No DB triggers or `LISTEN`/`NOTIFY`.

Backend layout (`com.circulabook`): `controller/` (REST under `/api/...`), `service/` (domain logic and state transitions), `repository/` (Spring Data JPA, derived queries), `model/` (JPA entities with Lombok), `dto/`. Statuses are plain `String`s compared by literal. Errors go back as `badRequest()` with a plain-text body, which the frontend shows directly.

State machines (this branch):

- Copy: `DISPONIVEL`, `EMPRESTADO`, `EMPRESTADO_RESERVADO`, `RESERVADO`. Only `EstadoExemplarService.mudarStatus` changes it, validated by `StatusExemplar`.
- Reservation: `PENDENTE` → `DISPONIVEL` → `RETIRADA` | `EXPIRADA`. Pickup is always at the queue library (`bibliotecaFila`).
- Loan: `ATIVO`/`ATRASADO` → `DEVOLVIDO`. 14-day fixed term, max 3, no two copies of the same title, 2 blocked days per late day.
- Invariant: for each (book, library), every borrowed copy is `EMPRESTADO_RESERVADO` iff there is a `PENDENTE` reservation there (`sincronizarMarcaDeFila` after every queue/loan change).

Security: stateless Spring Security + JWT (`SecurityConfig`, `AuthController`); the actor always comes from the token (`Ator.de(jwt)`). Routes not listed in `SecurityConfig` are denied. Librarians only act on their own library (checked in controllers).

Frontend layout: `src/api/client.ts` is the single HTTP client. `src/types.ts` mirrors the backend JSON. `src/App.tsx` defines the routes per profile. `components/` holds the shared UI (`Layout`, `Header`, `TabelaPaginada`, `ModalConfirmacao`, `ui.tsx`).

Docs: `README.md`, `MANUAL_DO_USUARIO.md`, `MANUAL_DO_DESENVOLVEDOR.md` describe only this reduced version.

## Token economy

- Locate before reading: use Grep/Glob first, then read only the needed line ranges.
- Never read or list: `node_modules/`, `dist/`, `target/`, `package-lock.json`, `*.svg`, `frontend/src/assets/`, `.git/`.
- The spec is long and describes the full system: grep its headings and read only the section you need.
- Do not re-read a file you just edited; filter command output.
- Prefer Edit over rewriting a file. Do not refactor, rename or reformat code outside the task.
- Reuse existing pieces (`TabelaPaginada`, `ModalConfirmacao`, `ui.tsx`, `api/client.ts`).

## Working rules

- Smallest change that satisfies the task; if the task touches both sides, define the contract first and keep `types.ts` in sync.
- Schema changes: there is no Flyway. Change the entities and `data.sql`.
- Never hardcode secrets; `JWT_SECRET` comes from the environment with a dev-only default.
- Never change branch `sistema-final`.

## Verification (before the final answer)

1. Backend: `mvn compile`, then start it and confirm a clean boot (no stack traces).
2. Frontend: `npm run build` and `npm run lint` must pass.
3. API: exercise changed endpoints with curl (success + 401/403/400 cases).
4. UI: exercise the changed screens against the real backend and frontend with the demo users.
5. Never claim something works without having run it; if you could not run a check, say so.

## Final answer format (pt-BR, max 20 lines)

1. **Alterações:** one line per file: what changed and why.
2. **Como funciona agora:** the flow in plain language (3-5 lines).
3. **Verificação:** what you ran and the result (pass/fail).
4. **Pendências/Dúvidas:** only if any; otherwise omit.
