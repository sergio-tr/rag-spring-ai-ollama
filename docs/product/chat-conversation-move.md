# Moving a conversation to another project

Users can assign a conversation from one owned project to another via the product API (`POST …/projects/{projectId}/conversations/{conversationId}/move` with `destinationProjectId`). After a successful move:

- The conversation lists under the destination project.
- Per-chat **`document_filter`** is cleared so it does not reference documents from the old project.
- Rows in **`project_documents`** with **`CHAT_LOCAL`** scope are updated to the destination project id; **`PROJECT_SHARED`** corpus for the source project stays where it was.

Operational detail for integrators lives in [`rag-service/README.md`](../../rag-service/README.md) (product REST section).
