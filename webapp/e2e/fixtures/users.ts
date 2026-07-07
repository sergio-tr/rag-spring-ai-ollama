/**
 * Centralized credentials and env for E2E. Defaults match Flyway seed (local / Spring profile {@code e2e}).
 */

export function seedEmail(): string {
  return process.env.E2E_SEED_EMAIL ?? "dev@local.test";
}

export function seedPassword(): string {
  return process.env.E2E_SEED_PASSWORD ?? "dev";
}

/** E2e profile seeds admin@e2e.local (see E2eAdminUserSeeder / CI SQL seed). */
function e2eAdminEnabled(): boolean {
  return process.env.E2E_ADMIN_ENABLED === "1";
}

export function adminEmail(): string {
  if (process.env.E2E_ADMIN_EMAIL) {
    return process.env.E2E_ADMIN_EMAIL;
  }
  return e2eAdminEnabled() ? "admin@e2e.local" : "admin@dev.local";
}

export function adminPassword(): string {
  if (process.env.E2E_ADMIN_PASSWORD) {
    return process.env.E2E_ADMIN_PASSWORD;
  }
  return e2eAdminEnabled() ? "e2e" : "dev";
}
