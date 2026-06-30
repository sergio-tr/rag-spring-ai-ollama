import type { APIRequestContext } from "@playwright/test";
import { BlockedEnvironmentError } from "./blocked-environment";
import {
  actuatorHealthUrl,
  apiBaseUrl,
  integrationCredentials,
  productBasePath,
  productUrl,
  publicBaseUrl,
} from "./env";

const DEFAULT_SEED_PROJECT_ID = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22";
const PREFLIGHT_REQUEST_MS = Number.parseInt(
  process.env.E2E_STACK_PREFLIGHT_REQUEST_MS ?? "20000",
  10,
);

function shouldProbeWebLogin(): boolean {
  if (process.env.E2E_API_PREFLIGHT_SKIP_WEB === "1") {
    return false;
  }
  if (process.env.E2E_WEB_HEALTH_URL === "") {
    return false;
  }
  const isCi = process.env.CI === "true" || process.env.CI === "1";
  const configuredApi =
    process.env.E2E_PRODUCT_URL ?? process.env.API_BASE_URL ?? process.env.INTEGRATION_BACKEND_URL;
  if (
    isCi &&
    configuredApi &&
    !process.env.PLAYWRIGHT_BASE_URL &&
    !process.env.E2E_PUBLIC_BASE_URL
  ) {
    return false;
  }
  return true;
}

type ProjectListResponse = { items?: Array<{ id?: string; name?: string }> };
type MeSelectableLlmModelsResponse = {
  capability?: string;
  models?: Array<{ modelName?: string; selectable?: boolean }>;
};

async function assertOk(
  label: string,
  status: number,
  body: string,
  allowedStatuses?: number[],
): Promise<void> {
  const allowed = allowedStatuses ?? [200];
  if (!allowed.includes(status)) {
    throw new BlockedEnvironmentError(
      `${label}: HTTP ${status} ${body.slice(0, 200)} (api=${apiBaseUrl()})`,
    );
  }
}

/**
 * Mandatory API harness preflight. Throws {@link BlockedEnvironmentError} when the stack is unreachable
 * or missing seed fixtures — not a functional regression.
 */
export async function runApiStackPreflight(request: APIRequestContext): Promise<void> {
  const webBase = publicBaseUrl();
  const healthUrl = actuatorHealthUrl("/liveness");

  let liveness;
  try {
    liveness = await request.get(healthUrl, { timeout: PREFLIGHT_REQUEST_MS });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new BlockedEnvironmentError(
      `backend liveness unreachable at ${healthUrl} (${msg}). ` +
        `Set E2E_PRODUCT_URL / PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 for dev reverse-proxy.`,
    );
  }
  await assertOk("backend liveness", liveness.status(), await liveness.text());

  if (shouldProbeWebLogin()) {
    let loginPage;
    try {
      loginPage = await request.get(`${webBase}/en/login`, { timeout: PREFLIGHT_REQUEST_MS });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      throw new BlockedEnvironmentError(`frontend /en/login unreachable at ${webBase}/en/login (${msg})`);
    }
    await assertOk("frontend login page", loginPage.status(), await loginPage.text());
  }

  const { email, password } = integrationCredentials();
  let loginRes;
  try {
    loginRes = await request.post(productUrl("/auth/login"), {
      data: { email, password },
      timeout: PREFLIGHT_REQUEST_MS,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new BlockedEnvironmentError(`seed login POST failed (${msg}) url=${productUrl("/auth/login")}`);
  }
  const loginText = await loginRes.text();
  await assertOk("seed login", loginRes.status(), loginText);
  const loginJson = JSON.parse(loginText) as { accessToken?: string };
  if (!loginJson.accessToken) {
    throw new BlockedEnvironmentError("seed login response missing accessToken");
  }
  const token = loginJson.accessToken;
  const auth = { Authorization: `Bearer ${token}`, Accept: "application/json" };

  const modelsRes = await request.get(productUrl("/me/llm/selectable-models?capability=CHAT"), {
    headers: auth,
    timeout: PREFLIGHT_REQUEST_MS,
  });
  const modelsText = await modelsRes.text();
  await assertOk("selectable CHAT models", modelsRes.status(), modelsText);
  const models = JSON.parse(modelsText) as MeSelectableLlmModelsResponse;
  const selectableCount = (models.models ?? []).filter(
    (m) => m.selectable && (m.modelName ?? "").trim().length > 0,
  ).length;
  if (selectableCount < 1) {
    throw new BlockedEnvironmentError(
      "no selectable CHAT models for seed user (LiteLLM / model registry may be down)",
    );
  }

  const projectsRes = await request.get(productUrl("/projects?page=0&size=20"), {
    headers: auth,
    timeout: PREFLIGHT_REQUEST_MS,
  });
  const projectsText = await projectsRes.text();
  await assertOk("seed projects list", projectsRes.status(), projectsText);
  const projects = JSON.parse(projectsText) as ProjectListResponse;
  const items = projects.items ?? [];
  if (items.length < 1) {
    throw new BlockedEnvironmentError("seed user has no projects");
  }

  const expectedProjectId = process.env.E2E_SEED_PROJECT_ID ?? DEFAULT_SEED_PROJECT_ID;
  if (!items.some((p) => p.id === expectedProjectId)) {
    throw new BlockedEnvironmentError(
      `seed project fixture missing (expected id=${expectedProjectId}, prefix=${productBasePath()})`,
    );
  }
}
