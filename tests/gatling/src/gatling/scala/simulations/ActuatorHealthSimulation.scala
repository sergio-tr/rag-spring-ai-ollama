package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Short warmup / connectivity check (few VUs, short duration).
 */
class ActuatorHealthSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-health/1.0")

  private val scn = scenario("actuator_health")
    .exec(
      http("GET /actuator/health")
        .get("/actuator/health")
        .check(status.is(200))
    )

  private val users = Env.envInt("GATLING_HEALTH_USERS", 5)
  private val seconds = Env.envInt("GATLING_HEALTH_DURATION_SEC", 15)

  setUp(
    scn.inject(rampUsers(users) during seconds.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
