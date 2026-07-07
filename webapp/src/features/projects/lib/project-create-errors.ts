/** User-facing create failure categories (not shown verbatim in UI). */
export type ProjectCreateFailureKind =
  | "CREATE_FAILED"
  | "PROJECT_CREATED_REFRESH_FAILED"
  | "PROJECT_CREATED_RESPONSE_INCOMPLETE";

export class ProjectCreateError extends Error {
  readonly kind: ProjectCreateFailureKind;

  constructor(kind: ProjectCreateFailureKind, message?: string) {
    super(message ?? kind);
    this.name = "ProjectCreateError";
    this.kind = kind;
  }
}
