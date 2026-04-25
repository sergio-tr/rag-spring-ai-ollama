# Webapp module map (`webapp/`)

**Navigation only** — thesis-friendly map. All setup, scripts, and testing: [webapp/README.md](../../webapp/README.md). API types: TypeDoc (`cd webapp && npm run doc`).

| Area | Path (typical) | Role |
| ------ | ---------------- | ------ |
| App Router | `src/app/[locale]/` | Pages: auth, projects, chat, settings, lab, admin |
| UI components | `src/components/` | Shared UI (shadcn-style) |
| API client | `src/lib/` | `fetch` wrappers, auth headers, `NEXT_PUBLIC_RAG_API_PREFIX` |
| Types | `src/types/` | DTOs aligned with Spring OpenAPI (maintain manually or generate) |
| i18n | `messages/`, `next-intl` config | Locales for UI strings |

**Security note:** Browser calls **product** paths with JWT (cookie or header per implementation); legacy paths may be used only for specific tooling.
