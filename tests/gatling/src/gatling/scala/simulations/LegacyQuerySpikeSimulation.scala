package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Spike pattern: sudden burst of users against legacy query, then pause (soak-style gap).
 * Use to observe recovery after a traffic spike. Questions from [[questions.csv]].
 */
class LegacyQuerySpikeSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .userAgentHeader("gatling-rag-legacy-spike/1.0")

  private val questions = csv("questions.csv").circular

  private val scn = scenario("legacy_query_spike")
    .feed(questions)
    .exec(
      http("spike GET legacy query")
        .get(s"${RagPaths.legacyPrefix}/query")
        .queryParam("question", "${question}")
        .check(status.in(200, 503, 504, 502))
    )

  private val spikeUsers = Env.envInt("GATLING_LEGACY_SPIKE_USERS", 40)
  private val pauseSec = Env.envInt("GATLING_LEGACY_SPIKE_PAUSE_SEC", 15)

  setUp(
    scn.inject(
      atOnceUsers(spikeUsers),
      nothingFor(pauseSec.seconds),
      rampUsers(Env.envInt("GATLING_LEGACY_SPIKE_TAIL_USERS", 10)) during 20.seconds
    )
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(Env.envDouble("GATLING_SPIKE_MAX_FAIL_PCT", 10.0)),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
