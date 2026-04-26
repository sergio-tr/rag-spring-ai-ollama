package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Ramp VUs on a cheap endpoint to observe error rate / latency (breakpoint-style, documented for thesis).
 * Use actuator health by default to avoid burning LLM capacity.
 */
class StressRampSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-stress/1.0")

  private val scn = scenario("stress_actuator")
    .forever {
      exec(
        http("stress GET actuator health")
          .get("/actuator/health")
          .check(status.is(200))
      )
    }

  private val peakUsers = Env.envInt("GATLING_STRESS_PEAK_USERS", 80)
  private val rampSec = Env.envInt("GATLING_STRESS_RAMP_SEC", 120)
  private val holdSec = Env.envInt("GATLING_STRESS_HOLD_SEC", 60)

  setUp(
    scn.inject(
      rampUsers(peakUsers) during rampSec.seconds,
      constantUsersPerSec(peakUsers.toDouble / holdSec.max(1).toDouble) during holdSec.seconds
    )
  ).protocols(httpProtocol).maxDuration((rampSec + holdSec + 30).seconds).assertions(
    // Stress probe: allow more failures than normal load tests; override with env.
    global.failedRequests.percent.lte(Env.envDouble("GATLING_STRESS_MAX_FAIL_PCT", 25.0)),
    global.responseTime.percentile4.lte(Env.envInt("GATLING_STRESS_P99_MS", 30000))
  )
}
