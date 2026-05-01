import { describe, it, expect } from "vitest";
import { POST as v5SessionPost } from "./session/route";
import { POST as canonicalSessionPost } from "../../auth/session/route";
import { POST as v5LogoutPost } from "./logout/route";
import { POST as canonicalLogoutPost } from "../../auth/logout/route";
import { POST as v5RefreshPost } from "./refresh/route";
import { POST as canonicalRefreshPost } from "../../auth/refresh/route";

describe("api/v5/auth BFF route re-exports", () => {
  it("re-exports session POST from canonical handler", () => {
    expect(v5SessionPost).toBe(canonicalSessionPost);
  });

  it("re-exports logout POST from canonical handler", () => {
    expect(v5LogoutPost).toBe(canonicalLogoutPost);
  });

  it("re-exports refresh POST from canonical handler", () => {
    expect(v5RefreshPost).toBe(canonicalRefreshPost);
  });
});
