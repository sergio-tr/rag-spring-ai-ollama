package simulations

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

/**
 * Realistic mixed workload: weighted mix of login and admin (or product fallback).
 *
 * Profiles (see tests/gatling/README.md): {@code smoke}, {@code load}, {@code stress}, {@code spike}, {@code soak}.
 * Default traffic mix: 70% auth / 30% admin (or product fallback) - override with {@code GATLING_MIX_*_PCT}.
 *
 * Uses [[users.csv]] (multi-row feeder; replace with distinct accounts for realistic isolation) and
 * stable product reads (no snapshot dependency).
 */
abstract class MixedRealisticSimulationBase(forcedProfile: Option[String]) extends Simulation {

  private val profile: String = forcedProfile.getOrElse(Env.env("GATLING_PROFILE", "load"))

  private val authW: Double = Env.envDouble("GATLING_MIX_AUTH_PCT", 70.0)
  private val adminW: Double = Env.envDouble("GATLING_MIX_ADMIN_PCT", 30.0)
  private val wSum: Double = authW + adminW

  private val usersFeeder = csv("users.csv").circular

  private val authBranch: ChainBuilder =
    feed(usersFeeder).exec(ScenarioBlocks.authLogin)

  private val adminBranch: ChainBuilder =
    feed(usersFeeder).exec(ScenarioBlocks.adminOrAuthenticatedRead)

  /** Relative weights for [[randomSwitch]] (sum-normalized). */
  private val mixChain: ChainBuilder =
    randomSwitch(
      (authW / wSum) -> authBranch,
      (adminW / wSum) -> adminBranch
    )

  private val loopSec: Int = profile match {
    case "smoke"   => Env.envInt("GATLING_MIX_SMOKE_DURATION_SEC", 90)
    case "soak"    => Env.envInt("GATLING_MIX_SOAK_DURATION_MIN", 120) * 60
    case "stress"  => Env.envInt("GATLING_MIX_STRESS_PHASE_SEC", 180)
    case "spike"   => Env.envInt("GATLING_MIX_SPIKE_PHASE_SEC", 120)
    case _         => Env.envInt("GATLING_MIX_DURATION_SEC", 120)
  }

  private val scn = scenario("mixed_realistic").during(loopSec.seconds) {
    mixChain.pause(Env.envInt("GATLING_MIX_PAUSE_MS_MIN", 50).millis, Env.envInt("GATLING_MIX_PAUSE_MS_MAX", 500).millis)
  }

  private val injection = profile match {
    case "smoke" =>
      val vus = Env.envInt("GATLING_MIX_SMOKE_VUS", 4)
      val ramp = Env.envInt("GATLING_MIX_SMOKE_RAMP_SEC", 10)
      scn.inject(rampUsers(vus) during ramp.seconds)

    case "load" =>
      val vus = Env.envInt("GATLING_MIX_VUS", 20)
      val ramp = Env.envInt("GATLING_MIX_RAMP_SEC", 30)
      val hold = Env.envInt("GATLING_MIX_HOLD_SEC", 90)
      scn.inject(
        rampUsers(vus) during ramp.seconds,
        constantUsersPerSec(vus.toDouble / hold.max(1).toDouble) during hold.seconds
      )

    case "stress" =>
      val peak = Env.envInt("GATLING_MIX_STRESS_PEAK_USERS", Env.envInt("GATLING_STRESS_PEAK_USERS", 60))
      val ramp = Env.envInt("GATLING_MIX_STRESS_RAMP_SEC", Env.envInt("GATLING_STRESS_RAMP_SEC", 90))
      val hold = Env.envInt("GATLING_MIX_STRESS_HOLD_SEC", Env.envInt("GATLING_STRESS_HOLD_SEC", 60))
      scn.inject(
        rampUsers(peak) during ramp.seconds,
        constantUsersPerSec(peak.toDouble / hold.max(1).toDouble) during hold.seconds
      )

    case "spike" =>
      val burst = Env.envInt("GATLING_MIX_SPIKE_BURST_USERS", 30)
      val pause = Env.envInt("GATLING_MIX_SPIKE_PAUSE_SEC", 15)
      val tail = Env.envInt("GATLING_MIX_SPIKE_TAIL_USERS", 8)
      scn.inject(
        atOnceUsers(burst),
        nothingFor(pause.seconds),
        rampUsers(tail) during 20.seconds
      )

    case "soak" =>
      val rps = Env.envDouble("GATLING_MIX_SOAK_RPS", 2.0)
      val minutes = Env.envInt("GATLING_MIX_SOAK_DURATION_MIN", 180)
      scn.inject(constantUsersPerSec(rps) during (minutes * 60).seconds)

    case _ =>
      scn.inject(rampUsers(Env.envInt("GATLING_MIX_VUS", 20)) during Env.envInt("GATLING_MIX_RAMP_SEC", 30).seconds)
  }

  private val maxDur = profile match {
    case "soak" =>
      (Env.envInt("GATLING_MIX_SOAK_DURATION_MIN", 180) * 60 + 120).seconds
    case "smoke" =>
      (loopSec + 60).seconds
    case _ =>
      (Env.envInt("GATLING_MIX_MAX_DURATION_SEC", loopSec + 300)).seconds
  }

  private val failPct: Double = profile match {
    case "stress" => Env.envDouble("GATLING_STRESS_MAX_FAIL_PCT", 25.0)
    case "spike"  => Env.envDouble("GATLING_SPIKE_MAX_FAIL_PCT", 10.0)
    case "soak"   => Env.envDouble("GATLING_MIX_SOAK_MAX_FAIL_PCT", 8.0)
    case _        => Env.envDouble("GATLING_MIX_MAX_FAIL_PCT", RagPaths.maxFailPercent)
  }

  private val p99: Int = profile match {
    case "stress" => Env.envInt("GATLING_STRESS_P99_MS", 30000)
    case "spike"  => Env.envInt("GATLING_MIX_SPIKE_P99_MS", RagPaths.p99Ms)
    case "soak"   => Env.envInt("GATLING_MIX_SOAK_P99_MS", 45000)
    case "smoke"  => Env.envInt("GATLING_MIX_SMOKE_P99_MS", 12000)
    case _        => Env.envInt("GATLING_MIX_P99_MS", RagPaths.p99Ms)
  }

  setUp(injection)
    .protocols(ScenarioBlocks.mixedHttpProtocol)
    .maxDuration(maxDur)
    .assertions(
      global.failedRequests.percent.lte(failPct),
      global.responseTime.percentile4.lte(p99)
    )
}

/** Uses {@code GATLING_PROFILE} (default {@code load}). */
class MixedRealisticSimulation extends MixedRealisticSimulationBase(None)

class MixedRealisticSmokeSimulation extends MixedRealisticSimulationBase(Some("smoke"))

class MixedRealisticLoadSimulation extends MixedRealisticSimulationBase(Some("load"))

class MixedRealisticStressSimulation extends MixedRealisticSimulationBase(Some("stress"))

class MixedRealisticSpikeSimulation extends MixedRealisticSimulationBase(Some("spike"))

class MixedRealisticSoakSimulation extends MixedRealisticSimulationBase(Some("soak"))
