/**
 * Centralized credentials and env for E2E. Defaults match Flyway seed (local / Spring profile {@code e2e}).
 */

export function seedEmail(): string {
  return process.env.E2E_SEED_EMAIL ?? "dev@local.test";
}

export function seedPassword(): string {
  return process.env.E2E_SEED_PASSWORD ?? "dev";
}

export function adminEmail(): string {
  return process.env.E2E_ADMIN_EMAIL ?? "admin@dev.local";
}

export function adminPassword(): string {
  return process.env.E2E_ADMIN_PASSWORD ?? "dev";
}
