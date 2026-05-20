import type { Page } from "@playwright/test";

/**
 * Stubs common authenticated GET/POST calls so app-shell routes render without a live Spring stack.
 * Keeps smoke tests independent of Postgres/Ollama/classifier.
 */
export async function installMinimalProductApiStub(page: Page): Promise<void> {
  const meJson = JSON.stringify({
    userId: "smoke-user",
    email: "smoke@example.com",
    name: "Smoke User",
    roleName: "USER",
    emailVerified: true,
    emailVerifiedAt: null,
  });

  const labStatusJson = JSON.stringify({
    referenceBundleAvailable: false,
    referenceBundleValid: false,
    datasetKindsReady: false,
    countsByDatasetKind: {
      llmReaderQuestions: 0,
      embeddingRetrievalQueries: 0,
      ragPresetQuestions: 0,
    },
    datasets: {
      enabled: false,
      datasetKindsReady: false,
    },
    evaluations: { llm: false, rag: false, classifierProxy: false, asyncJobs: false },
    classifier: { configured: false, train: false, evaluate: false },
    message: "Offline smoke stub — canonical benchmarks use POST …/lab/benchmarks/{kind}/runs.",
  });

  await page.route("**/api/v5/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v5/, "") || "/";
    const method = req.method();

    const fulfillJson = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
      });

    if (method === "POST" && path === "/auth/refresh") {
      await fulfillJson({
        accessToken: "smoke-access-token",
        refreshToken: "smoke-refresh-token",
        user: {
          id: "smoke-user",
          email: "smoke@example.com",
          name: "Smoke User",
          role: "USER",
        },
      });
      return;
    }

    if (method !== "GET") {
      await fulfillJson({});
      return;
    }

    if (path === "/auth/me") {
      await fulfillJson(JSON.parse(meJson));
      return;
    }

    if (path === "/projects") {
      await fulfillJson({ items: [], total: 0 });
      return;
    }

    if (path === "/config/schema") {
      await fulfillJson({ version: 1, fields: [] });
      return;
    }

    if (path === "/config/user") {
      await fulfillJson({});
      return;
    }

    if (path === "/me/preferences") {
      await fulfillJson({ schemaVersion: 1, preferences: {} });
      return;
    }

    if (path === "/me/personalization") {
      await fulfillJson({ schemaVersion: 1, personalization: {} });
      return;
    }

    if (path === "/me/summary") {
      await fulfillJson({
        projectCount: 0,
        conversationCount: 0,
        documentCount: 0,
        estimatedStorageBytes: 0,
      });
      return;
    }

    if (path.startsWith("/me/documents")) {
      await fulfillJson({ items: [], total: 0 });
      return;
    }

    if (path === "/lab/status") {
      await fulfillJson(JSON.parse(labStatusJson));
      return;
    }

    if (path === "/lab/experimental-datasets") {
      await fulfillJson([]);
      return;
    }

    if (path === "/presets") {
      await fulfillJson([]);
      return;
    }

    if (path === "/model-registry") {
      await fulfillJson({
        ollamaReachable: false,
        ollamaErrorMessage: "offline smoke stub",
        llmModels: [],
        embeddingModels: [],
      });
      return;
    }

    await fulfillJson({});
  });
}

/** Cookie checked by middleware so `(app)` routes are reachable without calling login APIs. */
export async function addSmokeAccessCookie(page: Page): Promise<void> {
  const base = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";
  const hostname = new URL(base).hostname;
  await page.context().addCookies([
    {
      name: "rag_access_token",
      value: "playwright-smoke-opaque-token",
      domain: hostname,
      path: "/",
      secure: base.startsWith("https:"),
      sameSite: "Lax",
    },
  ]);
}
