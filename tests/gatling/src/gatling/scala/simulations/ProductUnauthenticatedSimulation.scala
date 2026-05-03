package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Product REST without JWT: expect 401 or 403 on protected routes.
 */
class ProductUnauthenticatedSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-product-unauth/1.0")

  private val scn = scenario("product_unauthenticated")
    .during(Env.envInt("GATLING_UNAUTH_ITERATION_SEC", 20).seconds) {
      exec(
        http("GET product /presets (no auth)")
          .get(s"${RagPaths.productPrefix}/presets")
          .check(status.in(401, 403))
      ).pause(500.millis, 1500.millis).exec(
        http("GET product /config/schema (no auth)")
          .get(s"${RagPaths.productPrefix}/config/schema")
          .check(status.in(401, 403))
      ).pause(500.millis, 1500.millis)
    }

  private val users = Env.envInt("GATLING_UNAUTH_VUS", 4)

  setUp(
    scn.inject(rampUsers(users) during 15.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
