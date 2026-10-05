# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Circula Book: a system for managing a network of community libraries (CEFET/RJ course project). Users find a book anywhere in the network, join a per-library waiting queue, and can pick it up at another library via a transfer.

**`CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md` is the source of truth** for required behavior: profiles and JWT auth, business rules RN01–RN21, the copy (`exemplar`) state machine and its valid transitions, the reservation/transfer lifecycle, notifications, per-profile features with their implementation status, demo seed data, and test scripts. Read the relevant section before changing domain logic. Rules from that document:

- Do not change the business rules in sections 3–6. If something is ambiguous, ask instead of deciding.
- Code, messages and UI text are in **Brazilian Portuguese**.
- Make small, descriptive commits.

## Commands

Requires a local PostgreSQL with database `circula_book_db`, user `circula_user`, password `circula_senha_123` (see `backend/circula-book/src/main/resources/application.properties`).

Backend (Java 17, Spring Boot 4.x, Maven; there is no Maven wrapper):

```
cd backend/circula-book
mvn spring-boot:run          # http://localhost:8080
mvn compile
mvn test                     # there is no src/test yet
mvn test -Dtest=ClassName#method
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

**Blackboard pattern.** The database is the shared "blackboard", and the `exemplar` table's `status` column is the central state. Services in `service/` act as knowledge sources that read and write that state. `FilaEsperaService` (the queue specialist) is called **synchronously, inside the same transaction**, whenever a copy becomes free: on return, transfer arrival, reservation expiry or cancellation, and copy registration. It either sets the reservation to ready for pickup (3 days) or binds the copy to a transfer request. There must be **no** DB triggers or `LISTEN`/`NOTIFY`. Any leftover of that would be dead or buggy code.

Backend layout (`com.circulabook`): `controller/` (REST under `/api/...`), `service/` (domain logic and state transitions), `repository/` (Spring Data JPA, mostly derived-query methods), `model/` (JPA entities with Lombok), `dto/` (request and response payloads). Statuses (copy, loan, reservation, transfer) are plain `String`s compared by literal, such as `"DISPONIVEL"`, not enums. Changes to a state must follow the transition tables in spec section 4. Errors go back as `badRequest()` with a plain-text body, which the frontend shows directly.

Key cross-cutting invariants (spec §4.2, §5.3):

- For each (book, library) pair, every borrowed copy is `EMPRESTADO_RESERVADO` when there is at least one `PENDENTE` reservation in that library's queue, and `EMPRESTADO` otherwise. This has to be re-synced after every operation that touches the queue or loans.
- RN15: a transfer may never leave the origin library without at least one copy of the title. It is checked when a reservation is created, when the Admin approves, at dispatch, and for ad-hoc (avulsa) transfers.

Frontend layout: `src/api/client.ts` is the single HTTP client (`api.get/post/patch`, `qs()` querystring builder, date helpers). `src/types.ts` mirrors the backend JSON. `src/App.tsx` defines all routes, grouped by profile (`COMUM`, `BIBLIOTECARIO`, `ADMIN`). `components/` holds the shared UI (`Layout`, `Header`, `TabelaPaginada`, `ModalConfirmacao`, `ui.tsx` primitives).

## Current state

All features of spec §7 are implemented (stages 1 to 8 done):

- Stateless Spring Security + JWT (`SecurityConfig`, `AuthController`); the actor always comes from the token. Frontend has `/login`, `/cadastro`, `AuthContext`, `RotaProtegida` and the notification bell for every profile.
- Copy state machine (§4.3) centralized in `EstadoExemplarService` (`mudarStatus`, `sincronizarMarcaDeFila`); RN15/RN22 in `RegrasTransferenciaService`; notifications, history, demands, dashboard and table-only reports are in place.
- Barcodes, `PERDIDO`, RN11 and the variable loan period were removed.
- The demo seed (§8) is in `data.sql`; `SeedDemonstracaoTest` and `RoteirosSeedTest` run on the real seed in the `seed_teste` schema. Playwright restarts the backend before each spec (`frontend/e2e/backend.mjs`); `demo-gravacao.spec.ts` runs the §9.0 recording script.
- Developer and user documentation: `MANUAL_DO_DESENVOLVEDOR.md` and `MANUAL_DO_USUARIO.md` at the repository root.

## Token economy

- Locate before reading: use Grep/Glob first, then read only the needed line ranges. Do not read whole files "to get context".
- Never read or list: `node_modules/`, `dist/`, `target/`, `package-lock.json`, `mvnw*`, `.mvn/`, `*.svg`, `frontend/src/assets/`, `.git/`.
- The spec is long: grep its headings (`grep -n "^##" CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md`) and read only the section for the task (e.g. §2 for auth, §4 for the state machine). Read the test script rows (§9) only for the profile you are changing.
- `data.sql` is large: edit it by targeted ranges, never print it whole.
- Do not re-read a file you just edited; do not paste large outputs back. Filter command output (`| tail -30`, `grep -i error`).
- Prefer Edit over rewriting a file. Do not refactor, rename or reformat code outside the task.
- Reuse existing pieces (`TabelaPaginada`, `ModalConfirmacao`, `ui.tsx`, `api/client.ts`).

## Working rules

- Smallest change that satisfies the task; if the task touches both sides, define the contract (endpoint, DTO, fields) first and keep `types.ts` in sync.
- Schema changes: there is no Flyway. Change the entities and `data.sql` (the schema is recreated on every start).
- Never hardcode secrets; `JWT_SECRET` comes from the environment with a dev-only default.
- Ask only when the spec is ambiguous or a rule would have to change (max 2 objective questions).

## Verification (before the final answer)

1. Backend: `mvn compile`, then start it and confirm a clean boot (no stack traces).
2. Frontend: `npm run build` and `npm run lint` must pass.
3. API: exercise changed endpoints with curl or `mvn test` (success + 401/403/400 cases).
4. UI flows: Playwright against the real backend and frontend. Cover the main flow and one error case per changed screen; use the demo users from spec §8.1.
5. Fix failures and re-test. Never claim something works without having run it; if you could not run a check, say so.

## Final answer format (pt-BR, max 20 lines)

1. **Alterações:** one line per file: what changed and why.
2. **Como funciona agora:** the flow in plain language (3-5 lines).
3. **Testes:** what you ran and the result (pass/fail).
4. **Pendências/Dúvidas:** only if any; otherwise omit.
   No code dumps, no step-by-step narration of your process.
