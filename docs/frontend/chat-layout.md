# Chat UI layout notes

On the Chat route (`webapp/src/app/[locale]/(app)/chat/page.tsx`):

- **Conversation list sidebar** — can be collapsed with the panel control next to “New conversation”; collapse state is optionally restored from `sessionStorage` (`chat-conv-list-collapsed`).
- **Move conversation** — `MoveConversationDialog` posts to the backend move endpoint and refreshes TanStack Query caches (`useMoveConversation`).

---

## UI enhancements branch — post-closure follow-up (May 2026)

**Status:** Implemented on branch `ui-enhancements` (see repo history). This section records scope, touchpoints, and how to validate.

### Delivered

| Area | Summary |
| --- | --- |
| Shell branding | Collapsed rail width `72px` (`sidebar-layout.ts`); product mark uses `webapp/public/logo.svg` in `AppSidebar`. |
| Chat chrome | Model, preset, limit retrieval, manage documents, move, and delete live in the shell **Chat actions** overflow (`ChatToolbarOverflowMenu`, `data-testid="chat-actions-menu-trigger"`). The chat page registers callbacks via `useChatToolbarStore` (`chat-toolbar.store.ts`). Main chat column stays title + project + help popover only. |
| Layout tokens | Readable column width token `--max-width-chat-readable` in `globals.css`; conversation list stays left; center column ~half usable width (capped). |
| Dark mode | User-message edit `Textarea` uses theme foreground/background tokens (no white-on-white). |
| Session | Default access JWT **3600s**, refresh **604800s** in `rag-service` `application.properties`; proactive refresh ~2 min before `exp` (`auth-access-scheduler.ts`); SSE POST retries once after refresh on 401 (`sse-post.ts`). |
| CORS (dev) | `rag.cors.allowed-origins` defaults to `http(s)://localhost:*` and `http(s)://127.0.0.1:*` patterns; `SecurityConfiguration` `@Value` defaults aligned. Override with `RAG_CORS_ALLOWED_ORIGINS` for LAN IPs (comma-separated patterns). |
| Local LAN dev | `npm run dev:lan` — Next binds `0.0.0.0:3000`. Point phone to `http://<host-ip>:3000`; set backend CORS and `NEXT_PUBLIC_API_BASE_URL` to reachable backend URL. |
| HTTPS dev | Optional nginx reverse proxy profile: `docker/compose.dev-proxy.yml` (ports 80 / 8444, TLS paths via env). Use mkcert or team certs as documented in `reverse-proxy` image README if needed. |

### Files touched (high level)

- Webapp: `AppSidebar.tsx`, `sidebar-layout.ts`, `AppSectionActions.tsx`, `ChatToolbarOverflowMenu.tsx`, `chat-toolbar.store.ts`, `chat/page.tsx`, `globals.css`, `api-client.ts`, `auth-access-scheduler.ts`, `session-client.ts`, `sse-post.ts`, `package.json` (`dev:lan`), tests under `src/**` and `e2e/chat`, `e2e/smoke`.
- Backend: `application.properties` (JWT TTL, CORS defaults), `SecurityConfiguration.java`, `CorsConfig.java`.

### Tests (recommended commands)

- `cd webapp && npm run test` — Vitest (includes chat page toolbar overflow, sidebar logo, scheduler, session-client).
- `cd webapp && npm run test:e2e:smoke` — smoke incl. mobile viewport shell check.
- `cd webapp && npm run test:e2e:fullstack` — chat runtime incl. overflow + limit retrieval (requires stack).
- `cd rag-service && mvn test` — backend unit tests after CORS/security edits.

### Risks / notes

- **CORS on LAN:** wildcard `localhost` patterns do **not** cover `http://192.168.x.x:3000`; set `RAG_CORS_ALLOWED_ORIGINS` explicitly for device IPs.
- **E2E assumptions:** Playwright chat specs must open `chat-actions-menu-trigger` before preset/limit/delete/documents controls.
- **JWT:** Changing TTL affects security posture; production should override via env, not rely on dev defaults.

### Evidence

- Green Vitest + targeted Playwright + `mvn test` on the integration branch after the follow-up commit(s).

---

## E2E fullstack stabilization (chat + lab)

**Status:** Completed alongside `ui-enhancements` — specs aligned to overflow menu, sheet scoping, and Lab warning contract.

### Original failures (pre-fix)

| Spec | Symptom | Root cause |
| --- | --- | --- |
| `chat-delete-conversation.spec.ts` | `sendChatMessage` timeout / page closed; marker text still present after delete | Composer/selectors not scoped to readable column; delete assumed sidebar trash + global `getByText`; streaming/send flake |
| `chat-documents-sheet.spec.ts` | Two `Close` buttons (sheet chrome + footer); checkbox unstable | Global `getByRole('button', Close)`; “Manage documents” moved under ⋮ menu |
| `conversation-history.spec.ts` | Expected 2 sidebar buttons, got 4 | Selector counted all buttons (new chat, collapse, conv rows, delete, …) instead of conversation rows |
| `lab-rag-eval.spec.ts` | Flaky disabled assertion | Broad `/dataset/` regex matched unrelated copy while `datasets.enabled` was true |

### Changes

- **Stable `data-testid`s:** `chat-conversation-sidebar`, `conversation-list`, `conversation-item-{id}`, `chat-actions-menu-trigger`, `chat-delete-menu-item`, `chat-delete-confirm-dialog`, `chat-delete-confirm-button`, `chat-open-documents-sheet`, `chat-documents-sheet`, `chat-documents-sheet-close`, `chat-document-include-checkbox-{id}`, `chat-message-composer`, `chat-send-button`, `lab-datasets-disabled-warn`.
- **Accessibility:** `aria-current="true"` on the active conversation row button.
- **Helpers:** `sendChatMessage` targets composer/send inside `chat-readable-column`; guards `page.isClosed()` before `clear()`.
- **Lab:** E2E waits until `lab-datasets-disabled-warn` visibility matches Run disabled; sync-mode JSON path only when datasets enabled.

### Files touched (E2E wave)

- Specs: `e2e/chat/chat-delete-conversation.spec.ts`, `e2e/chat/chat-documents-sheet.spec.ts`, `e2e/chat/conversation-history.spec.ts`, `e2e/research/lab-rag-eval.spec.ts`, `e2e/chat/project-chat-runtime.spec.ts`, `e2e/support/helpers.ts`.
- UI: `chat/page.tsx`, `ChatToolbarOverflowMenu.tsx`, `DeleteConversationDialog.tsx`, `ChatConversationDocumentsSheet.tsx`, `lab-evaluation-run-card.tsx`.
- Unit tests: `page.test.tsx`, `AppSectionActions.test.tsx`.

### Tests run

- `npm run typecheck`, `npm run lint`, `npm run test` (Vitest).
- `npm run test:e2e:fullstack` when Spring e2e + DB + webapp are available; otherwise run the four `@fullstack` specs listed in `webapp/README.md` / CI.

### Limitations

- **Lab JSON branch:** sync RAG eval can take up to **180s** and depends on backend/classifier/LLM; may still flake on overloaded CI — investigate timeouts before weakening assertions.
- **Fullstack:** Playwright requires the configured `webServer` and seed user (see `e2e/README.md`).

### Final state

- No intentional removal of functional coverage; no gratuitous `test.skip`.
- Chat delete validates list removal + absence of deleted title in the active header, not global message text.
