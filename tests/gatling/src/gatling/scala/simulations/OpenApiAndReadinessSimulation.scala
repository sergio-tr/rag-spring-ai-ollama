package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Cheap probes: OpenAPI document and readiness (no auth). OpenAPI may 404 when springdoc is off.
 */
class OpenApiAndReadinessSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-openapi-readiness/1.0")

  private val scn = scenario("openapi_readiness")
    .exec(
      http("GET /v3/api-docs")
        .get("/v3/api-docs")
        .check(status.in(200, 404))
    )
    .exec(
      http("GET /actuator/health/readiness")
        .get("/actuator/health/readiness")
        .check(status.in(200, 503))
    )
    .exec(
      http("GET /actuator/health/liveness")
        .get("/actuator/health/liveness")
        .check(status.is(200))
    )

  private val users = Env.envInt("GATLING_PROBE_USERS", 6)

  setUp(
    scn.inject(rampUsers(users) during 15.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent)
  )
}
