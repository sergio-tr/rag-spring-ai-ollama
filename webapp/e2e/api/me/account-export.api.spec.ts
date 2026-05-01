import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { apiBaseUrl, integrationCredentials, productUrl } from "../fixtures/env";

function pollUrlFromAccepted(pollPath: string): string {
  return pollPath.startsWith("http") ? pollPath : `${apiBaseUrl()}${pollPath}`;
}

test.describe("Account export API @api", () => {
  test("POST me/account/export returns 202 JSON + poll job returns structured JSON @api", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);

    const res = await request.post(productUrl("/me/account/export"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(202);
    const raw = await res.text();
    const accepted = parseJsonExpectNonHtml(raw, "POST me/account/export") as {
      jobId: string;
      status?: string;
      pollPath: string;
    };
    expect(accepted.jobId).toBeTruthy();
    expect(accepted.pollPath).toContain("/me/account/jobs/");

    const pollHref = pollUrlFromAccepted(accepted.pollPath);
    let pollRes = await request.get(pollHref, {
      headers: authHeaders(token),
    });
    let pollText = await pollRes.text();
    for (let i = 0; i < 12 && pollRes.status() === 404; i++) {
      await new Promise((r) => setTimeout(r, 250));
      pollRes = await request.get(pollHref, {
        headers: authHeaders(token),
      });
      pollText = await pollRes.text();
    }
    expect(pollRes.status(), pollText).toBe(200);
    const statusBody = parseJsonExpectNonHtml(pollText, "GET me/account/jobs/{id}") as {
      id?: string;
      status?: string;
      terminal?: boolean;
    };
    expect(typeof statusBody.terminal).toBe("boolean");
    expect(statusBody.status).toBeTruthy();
  });
});
