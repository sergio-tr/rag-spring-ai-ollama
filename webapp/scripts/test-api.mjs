import { spawn } from "node:child_process";
import { applyDemoProxyEnvDefaults, actuatorHealthUrl, productBasePath, resolveE2eBases } from "./e2e-bases.mjs";

applyDemoProxyEnvDefaults();
const { apiBase, healthBase, webHealthBase } = resolveE2eBases();
const PRODUCT_PREFIX = productBasePath();
const PREFLIGHT_REQUEST_MS = Number.parseInt(
  process.env.E2E_STACK_PREFLIGHT_REQUEST_MS ?? "20000",
  10,
);
const SEED_EMAIL = process.env.E2E_SEED_EMAIL ?? process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test";
const SEED_PASSWORD = process.env.E2E_SEED_PASSWORD ?? process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev";
const SEED_PROJECT_ID =
  process.env.E2E_SEED_PROJECT_ID ?? "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22";

function blockedExit(detail) {
  console.error(`BLOCKED_ENVIRONMENT: ${detail}`);
  console.error(
    `Hint: Docker dev reverse-proxy → PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 ` +
      `E2E_PRODUCT_URL=https://127.0.0.1:8444 (not :9000). ` +
      `Direct backend → E2E_PRODUCT_URL=http://127.0.0.1:9000`,
  );
  process.exit(2);
}

async function fetchJson(url, init = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PREFLIGHT_REQUEST_MS);
  try {
    const res = await fetch(url, { ...init, signal: controller.signal, redirect: "follow" });
    const text = await res.text().catch(() => "");
    return { res, text };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(`${msg} (url=${url})`);
  } finally {
    clearTimeout(timer);
  }
}

async function runMandatoryPreflight() {
  if (process.env.E2E_SKIP_STACK_PREFLIGHT === "1") {
    console.log("test-api: stack preflight skipped (E2E_SKIP_STACK_PREFLIGHT=1)");
    return;
  }

  const livenessUrl = actuatorHealthUrl(healthBase, "/liveness");
  try {
    const { res, text } = await fetchJson(livenessUrl);
    if (!res.ok) {
      blockedExit(`backend liveness HTTP ${res.status} ${text.slice(0, 160)}`);
    }
  } catch (e) {
    blockedExit(`backend liveness unreachable at ${livenessUrl}: ${e instanceof Error ? e.message : e}`);
  }

  try {
    const { res } = await fetchJson(`${webHealthBase}/en/login`);
    if (!res.ok) {
      blockedExit(`frontend /en/login HTTP ${res.status}`);
    }
  } catch (e) {
    blockedExit(`frontend unreachable: ${e instanceof Error ? e.message : e}`);
  }

  const loginUrl = `${apiBase}${PRODUCT_PREFIX}/auth/login`;
  let accessToken;
  try {
    const { res, text } = await fetchJson(loginUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: SEED_EMAIL, password: SEED_PASSWORD }),
    });
    if (res.status !== 200) {
      blockedExit(`seed login HTTP ${res.status} ${text.slice(0, 160)}`);
    }
    accessToken = JSON.parse(text).accessToken;
    if (!accessToken) {
      blockedExit("seed login missing accessToken");
    }
  } catch (e) {
    blockedExit(`seed login failed: ${e instanceof Error ? e.message : e}`);
  }

  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
  };

  const modelsUrl = `${apiBase}${PRODUCT_PREFIX}/me/llm/selectable-models?capability=CHAT`;
  try {
    const { res, text } = await fetchJson(modelsUrl, { headers: authHeaders });
    if (res.status !== 200) {
      blockedExit(`selectable models HTTP ${res.status} ${text.slice(0, 160)}`);
    }
    const models = JSON.parse(text).models ?? [];
    const selectable = models.filter((m) => m.selectable && String(m.modelName ?? "").trim());
    if (selectable.length < 1) {
      blockedExit("no selectable CHAT models (check LiteLLM / model registry)");
    }
  } catch (e) {
    blockedExit(`selectable models check failed: ${e instanceof Error ? e.message : e}`);
  }

  const projectsUrl = `${apiBase}${PRODUCT_PREFIX}/projects?page=0&size=20`;
  try {
    const { res, text } = await fetchJson(projectsUrl, { headers: authHeaders });
    if (res.status !== 200) {
      blockedExit(`projects list HTTP ${res.status} ${text.slice(0, 160)}`);
    }
    const items = JSON.parse(text).items ?? [];
    if (!items.some((p) => p.id === SEED_PROJECT_ID)) {
      blockedExit(`seed project fixture missing (id=${SEED_PROJECT_ID})`);
    }
  } catch (e) {
    blockedExit(`projects check failed: ${e instanceof Error ? e.message : e}`);
  }

  console.log(
    `test-api: preflight OK api=${apiBase} prefix=${PRODUCT_PREFIX} seed=${SEED_EMAIL} project=${SEED_PROJECT_ID}`,
  );
}

function runPlaywrightApi(extraArgs = []) {
  return new Promise((resolve) => {
    const child = spawn(
      process.platform === "win32" ? "npx.cmd" : "npx",
      [
        "playwright",
        "test",
        "--project=api",
        ...(process.env.RUN_CHAT_ACCEPTANCE === "1" ? [] : ["--grep-invert", "@chatAcceptance"]),
        ...extraArgs,
      ],
      {
        stdio: "inherit",
        env: {
          ...process.env,
          PLAYWRIGHT_SKIP_WEBSERVER: "1",
          PLAYWRIGHT_IGNORE_HTTPS_ERRORS: process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS ?? "1",
          NODE_TLS_REJECT_UNAUTHORIZED: process.env.NODE_TLS_REJECT_UNAUTHORIZED ?? "0",
          PLAYWRIGHT_API_TEST_TIMEOUT_MS:
            process.env.PLAYWRIGHT_API_TEST_TIMEOUT_MS ?? "120000",
        },
      },
    );
    child.on("close", (code) => resolve(code ?? 1));
  });
}

const cliArgs = process.argv.slice(2);
await runMandatoryPreflight();
process.exit(await runPlaywrightApi(cliArgs));
