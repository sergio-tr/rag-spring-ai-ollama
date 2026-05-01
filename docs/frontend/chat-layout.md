# Chat UI layout notes

On the Chat route (`webapp/src/app/[locale]/(app)/chat/page.tsx`):

- **Conversation list sidebar** — can be collapsed with the panel control next to “New conversation”; collapse state is optionally restored from `sessionStorage` (`chat-conv-list-collapsed`).
- **Move conversation** — `MoveConversationDialog` posts to the backend move endpoint and refreshes TanStack Query caches (`useMoveConversation`).
