package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Multi-phase constant throughput on actuator health - compares several RPS plateaus in one run.
 * Tune with {@code GATLING_TIER1_RPS} … {@code GATLING_TIER3_RPS} and matching {@code *_SEC} durations.
 */
class ActuatorThroughputTiersSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-tiered-actuator/1.0")

  private val scn = scenario("actuator_tiers")
    .forever {
      exec(
        http("GET /actuator/health")
          .get("/actuator/health")
          .check(status.is(200))
      )
    }

  private val r1 = Env.envDouble("GATLING_TIER1_RPS", 2.0)
  private val r2 = Env.envDouble("GATLING_TIER2_RPS", 8.0)
  private val r3 = Env.envDouble("GATLING_TIER3_RPS", 3.0)
  private val s1 = Env.envInt("GATLING_TIER1_SEC", 25)
  private val s2 = Env.envInt("GATLING_TIER2_SEC", 35)
  private val s3 = Env.envInt("GATLING_TIER3_SEC", 20)

  setUp(
    scn.inject(
      nothingFor(2.seconds),
      constantUsersPerSec(r1) during s1.seconds,
      constantUsersPerSec(r2) during s2.seconds,
      constantUsersPerSec(r3) during s3.seconds
    )
  ).protocols(httpProtocol).maxDuration((s1 + s2 + s3 + 30).seconds).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
