package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.time.{Clock, Duration, Instant, ZoneOffset}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobRuntimeModelDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Job runtime semantic timestamps" should {
    "replay Job input and definition lifecycle values from explicit time" in {
      Given("a generated instant supplied by the execution boundary")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val createdat = Instant.ofEpochSecond(epochsecond)
        val updatedat = createdat.plus(Duration.ofMinutes(5L))
        val bytes = "deterministic-job-input".getBytes(StandardCharsets.UTF_8)
        val inline = JobInput.inline(
          "content",
          bytes,
          Some("input.txt"),
          Some("text/plain"),
          createdat
        )
        val blob = JobInput.blob(
          "attachment",
          "blob-1",
          Some("attachment.bin"),
          Some("application/octet-stream"),
          Some(128L),
          Some("sha256"),
          createdat
        )
        val input = JobInput(
          payloads = Vector(inline, blob),
          retentionPolicy = JobInputRetentionPolicy.Ttl,
          ttl = Duration.ofDays(1L),
          createdAt = createdat
        )
        val definition = JobDefinitionEntity.create(
          key = "deterministic-job",
          jclSource = "job deterministic-job {}",
          profile = None,
          flowSource = None,
          eventsSource = None,
          onEventSource = None,
          status = JobDefinitionStatus.Draft,
          targetAction = None,
          now = createdat
        )
        val updated = JobDefinitionEntity.updated(
          current = definition,
          jclSource = definition.jclSource,
          profile = definition.normalizedProfile,
          flowSource = definition.flowSource,
          eventsSource = definition.eventsSource,
          onEventSource = definition.onEventSource,
          status = Some(JobDefinitionStatus.Active),
          targetAction = definition.targetAction,
          now = updatedat
        )

        input.createdAt == createdat &&
          input.payloads.forall(_.createdAt == createdat) &&
          definition.createdAt == createdat &&
          definition.updatedAt == createdat &&
          updated.createdAt == createdat &&
          updated.updatedAt == updatedat
      }

      When("Job input and definition records are created and updated with that instant")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("every semantic timestamp must reproduce without consulting ambient time")
      checked.passed shouldBe true
    }

    "replay Job and Task identity sequences from equivalent execution capabilities" in {
      Given("two equivalent invocation-local ID capabilities and a fixed execution clock")
      val timestamp = Instant.parse("2026-07-16T01:00:00Z")
      val clock = Clock.fixed(timestamp, ZoneOffset.UTC)

      When("Job and Task IDs are generated with equal purposes and interleaving")
      val replayproperty = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { seedbody =>
        val seed = s"job-runtime-$seedbody"
        val leftids = IdGenerationContext.deterministic(IdGenerationContext.DefaultNamespace, clock, seed)
        val rightids = IdGenerationContext.deterministic(IdGenerationContext.DefaultNamespace, clock, seed)
        val leftcontext = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), leftids)
        val rightcontext = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), rightids)
        val leftjobs = Vector(
          JobId.create("submit", timestamp, leftcontext.idGeneration),
          JobId.create("submit", timestamp, leftcontext.idGeneration)
        )
        val lefttasks = Vector(
          TaskId.create("execute", timestamp, leftcontext.idGeneration),
          TaskId.create("execute", timestamp, leftcontext.idGeneration)
        )
        val rightjobs = Vector(
          JobId.create("submit", timestamp, rightcontext.idGeneration),
          JobId.create("submit", timestamp, rightcontext.idGeneration)
        )
        val righttasks = Vector(
          TaskId.create("execute", timestamp, rightcontext.idGeneration),
          TaskId.create("execute", timestamp, rightcontext.idGeneration)
        )

        leftjobs == rightjobs &&
          lefttasks == righttasks &&
          leftjobs.distinct.size == leftjobs.size &&
          lefttasks.distinct.size == lefttasks.size &&
          (leftjobs ++ lefttasks).forall(_.parts.timestamp == timestamp)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("the sequences replay exactly while remaining collision-free within each invocation")
      checked.passed shouldBe true
    }
  }
}
