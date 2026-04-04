# ADR 0004: React component tests — behavior-first with Testing Library

## Status

Accepted

## Context

The webapp (`webapp/`) uses Vitest, `@testing-library/react`, `@testing-library/jest-dom`, and `@testing-library/user-event`. We need a consistent rule: tests should reflect **what users perceive**, not internal implementation details.

## Decision

- Prefer **queries by role and accessible name** (`getByRole` / `findByRole`) over CSS selectors or snapshots.
- Prefer **`userEvent`** over `fireEvent` for interactions unless a rare edge case requires otherwise.
- Mock at **boundaries** (API client, router, i18n), not every internal helper.
- Do **not** gate component UI coverage in the same way as `src/lib/**` until the team explicitly expands Vitest coverage scope; see [../testing/README.md](../testing/README.md) for the full guide.

## Consequences

- New UI tests should follow the patterns in [../testing/README.md](../testing/README.md) (section **React / Testing Library (webapp)**).
- Shared helpers such as `renderWithProviders` or MSW are added only when feature-level tests justify them (documented in the same section).
