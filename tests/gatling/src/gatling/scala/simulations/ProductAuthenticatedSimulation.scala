package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Login via POST {product}/auth/login then hit product REST (projects list).
 */
class ProductAuthenticatedSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("gatling-rag-product/1.0")

  private val loginBody =
    s"""{"email":"${RagPaths.loginEmail.replace("\"", "\\\"")}","password":"${RagPaths.loginPassword.replace("\"", "\\\"")}"}"""

  private val authenticate =
    exec(
      http("POST product auth login")
        .post(s"${RagPaths.productPrefix}/auth/login")
        .body(StringBody(loginBody))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

  private val productCalls =
    exec(
      http("GET product /projects")
        .get(s"${RagPaths.productPrefix}/projects")
        .queryParam("page", "0")
        .queryParam("size", "10")
        .header("Authorization", session => "Bearer " + session("accessToken").as[String])
        .check(status.is(200))
    ).exec(
      http("GET product /config/schema")
        .get(s"${RagPaths.productPrefix}/config/schema")
        .header("Authorization", session => "Bearer " + session("accessToken").as[String])
        .check(status.is(200))
    )

  private val scn = scenario("product_authenticated")
    .exec(authenticate)
    .during(Env.envInt("GATLING_PRODUCT_ITERATION_SEC", 30).seconds) {
      exec(productCalls).pause(1.seconds, 3.seconds)
    }

  private val users = Env.envInt("GATLING_PRODUCT_VUS", 8)

  setUp(
    scn.inject(rampUsers(users) during 30.seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(RagPaths.maxFailPercent),
    global.responseTime.percentile4.lte(RagPaths.p99Ms)
  )
}
