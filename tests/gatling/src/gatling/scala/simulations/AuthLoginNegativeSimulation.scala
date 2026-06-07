package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Negative auth: wrong password and invalid refresh token expect 401; invalid email on login expects 400.
 */
class AuthLoginNegativeSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("gatling-rag-auth-negative/1.0")

  private val loginWrongPassword =
    s"""{"email":"${RagPaths.loginEmail.replace("\"", "\\\"")}","password":"gatling-wrong-password"}"""

  private val scn = scenario("auth_login_negative")
    .during(Env.envInt("GATLING_AUTH_NEG_ITERATION_SEC", 15).seconds) {
      exec(
        http("POST product auth login (wrong password)")
          .post(s"${RagPaths.productPrefix}/auth/login")
          .body(StringBody(loginWrongPassword))
          .check(status.is(401))
      ).pause(300.millis, 900.millis).exec(
        http("POST product auth login (invalid email)")
          .post(s"${RagPaths.productPrefix}/auth/login")
          .body(StringBody("""{"email":"not-an-email","password":"x"}"""))
          .check(status.is(400))
      ).pause(300.millis, 900.millis).exec(
        http("POST product auth refresh (invalid token)")
          .post(s"${RagPaths.productPrefix}/auth/refresh")
          .body(StringBody("""{"refreshToken":"invalid"}"""))
          .check(status.is(401))
      ).pause(300.millis, 900.millis)
    }

  private val users = Env.envInt("GATLING_AUTH_NEG_VUS", 3)

  setUp(
    scn.inject(rampUsers(users) during 10.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
