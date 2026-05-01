package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * RBAC on {@code /api/admin}: anonymous → 401/403; USER JWT → 403; optional ADMIN JWT → 200.
 * Set {@code GATLING_ADMIN_EMAIL} (and password) when the backend runs with profile {@code e2e}.
 */
class AdminApiSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("gatling-rag-admin-api/1.0")

  private val userLoginBody =
    s"""{"email":"${RagPaths.loginEmail.replace("\"", "\\\"")}","password":"${RagPaths.loginPassword.replace("\"", "\\\"")}"}"""

  private val optionalAdminLoginBody =
    s"""{"email":"${RagPaths.adminEmail.replace("\"", "\\\"")}","password":"${RagPaths.adminPassword.replace("\"", "\\\"")}"}"""

  private val loginUser =
    exec(
      http("POST /api/auth/login (USER)")
        .post("/api/auth/login")
        .body(StringBody(userLoginBody))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("userAccessToken"))
    )

  private val loginAdmin =
    exec(
      http("POST /api/auth/login (ADMIN)")
        .post("/api/auth/login")
        .body(StringBody(optionalAdminLoginBody))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("adminAccessToken"))
    )

  private val scn = scenario("admin_api_rbac")
    .exec(
      http("GET /api/admin/health (no auth)")
        .get("/api/admin/health")
        .check(status.in(401, 403))
    )
    .pause(300.millis, 800.millis)
    .exec(loginUser)
    .exec(
      http("GET /api/admin/health (USER token)")
        .get("/api/admin/health")
        .header("Authorization", session => "Bearer " + session("userAccessToken").as[String])
        .check(status.is(403))
    )
    .doIf(_ => RagPaths.adminEmail.nonEmpty) {
      pause(400.millis, 1.seconds)
        .exec(loginAdmin)
        .exec(
          http("GET /api/admin/health (ADMIN token)")
            .get("/api/admin/health")
            .header("Authorization", session => "Bearer " + session("adminAccessToken").as[String])
            .check(status.is(200))
            .check(jsonPath("$.status").is("UP"))
        )
        .exec(
          http("GET /api/admin/allowlist (ADMIN token)")
            .get("/api/admin/allowlist")
            .header("Authorization", session => "Bearer " + session("adminAccessToken").as[String])
            .check(status.is(200))
        )
    }

  private val users = Env.envInt("GATLING_ADMIN_API_VUS", 3)

  setUp(
    scn.inject(rampUsers(users) during 12.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
