package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * End-to-end chat path: login → create project → create conversation → POST message (SSE).
 * Keep [[GATLING_SSE_VUS]] low by default: each request may invoke the full RAG stack.
 */
class ChatSseSimulation extends Simulation {

  // Long SSE responses: override in gatling.conf (gatling.http.ahc.*) or JVM if defaults are too low for real LLMs.

  private val httpProtocol = http
    .baseUrl(RagPaths.baseUrl)
    .shareConnections
    .userAgentHeader("gatling-rag-sse/1.0")

  private val loginBody =
    s"""{"email":"${RagPaths.loginEmail.replace("\"", "\\\"")}","password":"${RagPaths.loginPassword.replace("\"", "\\\"")}"}"""

  private val auth =
    exec(
      http("POST /api/auth/login")
        .post("/api/auth/login")
        .header("Content-Type", "application/json")
        .body(StringBody(loginBody))
        .check(status.is(200))
        .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

  private val bearer = (session: io.gatling.core.session.Session) =>
    "Bearer " + session("accessToken").as[String]

  private val bootstrapProject =
    exec { session =>
      session.set("projName", "gat-chat-" + System.nanoTime())
    }.exec(
      http("POST product /projects")
        .post(s"${RagPaths.productPrefix}/projects")
        .header("Content-Type", "application/json")
        .header("Authorization", bearer)
        .body(StringBody(session => {
          val n = session("projName").as[String]
          s"""{"name":"$n","description":"gatling"}"""
        }))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("projectId"))
    ).exec(
      http("POST project conversation")
        .post(session => {
          val p = session("projectId").as[String]
          s"${RagPaths.productPrefix}/projects/$p/conversations"
        })
        .header("Content-Type", "application/json")
        .header("Authorization", bearer)
        .body(StringBody("""{"title":"sse-load"}"""))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("conversationId"))
    )

  private val sseMessage =
    exec(
      http("POST conversation messages (SSE)")
        .post(session => {
          val c = session("conversationId").as[String]
          s"${RagPaths.productPrefix}/conversations/$c/messages"
        })
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .header("Authorization", bearer)
        .body(StringBody(session => {
          val q =
            Env.env("GATLING_SSE_QUESTION", "One-line status check from Gatling load test.")
          val esc = q.replace("\\", "\\\\").replace("\"", "\\\"")
          s"""{"content":"$esc","llmModel":null}"""
        }))
        .check(status.in(200, 202))
    )

  private val scn = scenario("chat_sse").exec(auth).exec(bootstrapProject).exec(sseMessage)

  private val vus = Env.envInt("GATLING_SSE_VUS", 2)

  setUp(
    scn.inject(rampUsers(vus) during Env.envInt("GATLING_SSE_RAMP_SEC", 30).seconds)
  ).protocols(httpProtocol).assertions(
    global.failedRequests.percent.lte(Env.envDouble("GATLING_SSE_MAX_FAIL_PCT", RagPaths.maxFailPercent)),
    global.responseTime.percentile4.lte(Env.envInt("GATLING_SSE_P99_MS", RagPaths.p99Ms))
  )
}
