package simulations

/** Paths aligned with Spring {@code RagApiPathProperties} (env overrides). */
object RagPaths {

  val baseUrl: String = Env.stripTrailingSlash(Env.env("GATLING_BASE_URL", "http://localhost:9000"))

  val productPrefix: String = Env.stripTrailingSlash(Env.env("GATLING_PRODUCT_PREFIX", "/api/v5"))

  val loginEmail: String = Env.env("GATLING_LOGIN_EMAIL", "dev@local.test")

  val loginPassword: String = Env.env("GATLING_LOGIN_PASSWORD", "dev")

  /** When non-empty, optional admin-success steps run (e.g. e2e-seeded admin@e2e.local). */
  val adminEmail: String = Env.env("GATLING_ADMIN_EMAIL", "")

  val adminPassword: String = Env.env("GATLING_ADMIN_PASSWORD", "e2e")

  /** Threshold: max share of failed requests (0–100). */
  val maxFailPercent: Double = Env.envDouble("GATLING_MAX_FAIL_PCT", 5.0)

  /** Threshold: p99 response time in ms. */
  val p99Ms: Int = Env.envInt("GATLING_P99_MS", 15000)
}
