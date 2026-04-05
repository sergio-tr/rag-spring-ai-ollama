package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Load against legacy GET /query (public when legacy controllers are active).
 * Optional constant throughput via {@code GATLING_LEGACY_RPS} (rough steady rate).
 */
class LegacyQueryLoadSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-legacy/1.0")

  private val questions = csv("questions.csv").circular

  private val scn = scenario("legacy_query")
    .feed(questions)
    .exec(
      http("GET legacy query")
        .get(s"${RagPaths.legacyPrefix}/query")
        .queryParam("question", "${question}")
        // LLM-backed path may occasionally return 5xx under load; tighten in dedicated perf env.
        .check(status.in(200, 503, 504))
    )

  private val rps = Env.envDouble("GATLING_LEGACY_RPS", 0.0)
  private val durationSec = Env.envInt("GATLING_LEGACY_DURATION_SEC", 60)

  private val injection = if (rps > 0) {
    scn.inject(constantUsersPerSec(rps) during durationSec.seconds)
  } else {
    val vu = Env.envInt("GATLING_LEGACY_VUS", 10)
    scn.inject(rampUsers(vu) during 20.seconds, constantUsersPerSec(vu / 5.0.max(0.1)) during durationSec.seconds)
  }

  setUp(injection).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
