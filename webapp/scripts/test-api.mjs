import { spawn } from "node:child_process";

const apiBaseUrl =
  process.env.API_BASE_URL ??
  process.env.INTEGRATION_BACKEND_URL ??
  "http://127.0.0.1:9000";

function withTimeout(ms, promise) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), ms);
  return promise(controller.signal).finally(() => clearTimeout(t));
}

async function checkBackendReachable() {
  const healthUrl = `${apiBaseUrl.replace(/\/$/, "")}/actuator/health`;
  try {
    const res = await withTimeout(2500, (signal) =>
      fetch(healthUrl, { method: "GET", signal }),
    );
    return res.ok;
  } catch {
    return false;
  }
}

function runPlaywrightApi() {
  return new Promise((resolve) => {
    const child = spawn(
      process.platform === "win32" ? "npx.cmd" : "npx",
      [
        "playwright",
        "test",
        "--project=api",
        ...(process.env.RUN_CHAT_ACCEPTANCE === "1" ? [] : ["--grep-invert", "@chatAcceptance"]),
      ],
      {
        stdio: "inherit",
        env: {
          ...process.env,
          PLAYWRIGHT_SKIP_WEBSERVER: "1",
        },
      },
    );
    child.on("close", (code) => resolve(code ?? 1));
  });
}

const ok = await checkBackendReachable();
if (!ok) {
  // Keep the message short and actionable (shows up in CI logs too).
  console.error(
    [
      `API smoke tests require a running backend at ${apiBaseUrl}.`,
      `No server responded to ${apiBaseUrl}/actuator/health.`,
      "",
      "Start the backend first (example):",
      "  cd rag-service && ./mvnw -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e",
      "",
      "Or set API_BASE_URL / INTEGRATION_BACKEND_URL to a reachable server.",
    ].join("\n"),
  );
  process.exit(1);
}

process.exit(await runPlaywrightApi());

