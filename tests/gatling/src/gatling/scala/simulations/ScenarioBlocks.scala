package simulations

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

/**
 * Reusable HTTP fragments for realistic mix scenarios (legacy RAG query, auth, admin/product).
 * Feeders must be applied in the [[io.gatling.core.structure.ScenarioBuilder]] before these chains
 * when the chain uses session attributes (e.g. {@code ${email}}, {@code ${question}}).
 */
object ScenarioBlocks {

  /** Standard client settings for mixed workloads (real Ollama / product API). */
  def mixedHttpProtocol: io.gatling.http.protocol.HttpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-mixed/1.0")

  /** GET legacy RAG query — requires {@code question} in session (from questions feeder). */
  def legacyRagQuery: ChainBuilder =
    exec(
      http("mix GET legacy query")
        .get(s"${RagPaths.legacyPrefix}/query")
        .queryParam("question", "${question}")
        .check(status.in(200, 503, 504, 502))
    )

  /** POST /api/auth/login — requires {@code email} and {@code password} (users feeder). */
  def authLogin: ChainBuilder =
    exec(
      http("mix POST login")
        .post("/api/auth/login")
        .header("Content-Type", "application/json")
        .body(StringBody("""{"email":"${email}","password":"${password}"}"""))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

  /**
   * Admin path: when {@code GATLING_ADMIN_EMAIL} is set, login as admin and GET /api/admin/health.
   * Otherwise login as feed user and GET product projects (light authenticated read).
   */
  def adminOrAuthenticatedRead: ChainBuilder =
    exec(
      http("mix POST login (feed user)")
        .post("/api/auth/login")
        .header("Content-Type", "application/json")
        .body(StringBody("""{"email":"${email}","password":"${password}"}"""))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("accessToken"))
    ).doIfOrElse(_ => RagPaths.adminEmail.trim.nonEmpty)(
      exec(
        http("mix POST login (admin)")
          .post("/api/auth/login")
          .header("Content-Type", "application/json")
          .body(
            StringBody(
              s"""{"email":"${RagPaths.adminEmail.replace("\"", "\\\"")}","password":"${RagPaths.adminPassword.replace("\"", "\\\"")}"}"""
            )
          )
          .check(status.is(200))
          .check(jsonPath("$.accessToken").saveAs("adminToken"))
      ).exec(
        http("mix GET /api/admin/health")
          .get("/api/admin/health")
          .header("Authorization", session => "Bearer " + session("adminToken").as[String])
          .check(status.is(200))
      )
    )(
      exec(
        http("mix GET product /projects (no admin configured)")
          .get(s"${RagPaths.productPrefix}/projects")
          .queryParam("page", "0")
          .queryParam("size", "10")
          .header("Authorization", session => "Bearer " + session("accessToken").as[String])
          .check(status.is(200))
      )
    )

}
