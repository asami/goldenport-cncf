package org.goldenport.cncf.entity

import scala.collection.mutable.ListBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.log.{
  LogBackend,
  LogBackendHolder,
  StructuredLogEvent
}
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.{
  CallTreeValueSummary,
  EntityConditionalTransitionObservation
}
import org.goldenport.observation.{Cause, Descriptor}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConditionalTransitionDiagnosticsSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _root_collection =
    EntityCollectionId("test", "phase49", "diagnostic_root")
  private val _successor_collection =
    EntityCollectionId("test", "phase49", "diagnostic_successor")
  private val _root_id =
    EntityId("test", "diagnostic_root", _root_collection)
  private val _successor_id =
    EntityId("test", "diagnostic_successor", _successor_collection)

  "Entity conditional-transition diagnostics" should {
    "classify typed outcomes and structured failures without display parsing" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R19-R20; Examples: E5-E15; typed results and structured Conclusions"
      )
      val revision = EntityRevision.createC(1L).TAKE
      val transitioned =
        Consequence.success(
          EntityConditionalTransitionResult.Transitioned(
            EntitySnapshot("private-root-payload", revision),
            EntitySnapshot("private-successor-payload", revision)
          )
        )
      val notmatched =
        Consequence.success(
          EntityConditionalTransitionResult.NotMatched(
            EntitySnapshot("private-existing-payload", revision)
          )
        )
      val conflict = _failure(
        Consequence.operationConflict(
          "first human message",
          Vector(
            Descriptor.Facet.Reason(
              "bound-successor-revision-conflict"
            )
          )
        )
      )
      val rewrittenconflict = _failure(
        Consequence.operationConflict(
          "completely different display text",
          Vector(
            Descriptor.Facet.Reason(
              "bound-successor-revision-conflict"
            )
          )
        )
      )
      val unauthorized = _failure(
        Consequence.securityPermissionDenied(
          "private authorization explanation",
          Cause.Kind.Permission,
          Seq(Descriptor.Facet.Permission("write"))
        )
      )
      val unsupported = _failure(
        Consequence.operationInvalid(
          "entity-conditional-transition",
          Vector(
            Descriptor.Facet.Reason("unsupported-capability"),
            Descriptor.Facet.Capability(
              "datastore.entity-conditional-transition"
            )
          )
        )
      )
      val providerfailure = _failure(
        Consequence.operationInvalid(
          "provider detail must stay structured",
          Vector(Descriptor.Facet.Reason("provider-failure"))
        )
      )
      val transactionfailure = _failure(
        Consequence.operationInvalid(
          "transaction detail must stay structured",
          Vector(Descriptor.Facet.Reason("transaction-failure"))
        )
      )

      When("the common transition classifier projects each outcome")
      val outcomes = Vector(
        transitioned,
        notmatched,
        conflict,
        unauthorized,
        unsupported,
        providerfailure,
        transactionfailure
      ).map(EntityConditionalTransitionObservation.classify(_).outcome.name)

      Then("typed and structured categories are deterministic")
      outcomes shouldBe Vector(
        "transitioned",
        "not-matched",
        "conflict",
        "unauthorized",
        "unsupported-capability",
        "provider-failure",
        "transaction-failure"
      )
      EntityConditionalTransitionObservation
        .classify(conflict)
        .outcome shouldBe
        EntityConditionalTransitionObservation
          .classify(rewrittenconflict)
          .outcome
    }

    "emit bounded audit and low-cardinality metrics without payload values" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rule: R20; one authorized transitioned result containing sentinel payload values"
      )
      val backend = new StructuredMemoryBackend
      LogBackendHolder.install(backend)
      given ExecutionContext = ExecutionContext.create()
      val revision = EntityRevision.createC(2L).TAKE
      val result =
        Consequence.success(
          EntityConditionalTransitionResult.Transitioned(
            EntitySnapshot("sentinel-root-payload", revision),
            EntitySnapshot("sentinel-successor-payload", revision)
          )
        )
      val context = EntityConditionalTransitionObservation.Context(
        "entity-conditional-transition",
        Some("phase49-test"),
        _root_id,
        _successor_collection.name,
        Some(_successor_id)
      )
      val before =
        RuntimeDashboardMetrics
          .entityConditionalTransitionSnapshot
          .summary
          .cumulative
          .total

      When("the UnitOfWork boundary records the authoritative result")
      try
        EntityConditionalTransitionObservation.observe(context, result)
      finally
        LogBackendHolder.reset()

      Then("the metric records only outcome and structured diagnostic labels")
      val after =
        RuntimeDashboardMetrics
          .entityConditionalTransitionSnapshot
          .summary
          .cumulative
          .total
      after shouldBe before + 1L
      val point =
        RuntimeDashboardMetrics
          .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
          .points
          .find(point =>
            point.scope == "entity.conditional-transition" &&
              point.labels.get("outcome").contains("transitioned")
          )
          .getOrElse(fail("transition metric missing"))
      point.labels.keySet shouldBe Set("outcome")

      And("the structured audit contains identity and revision metadata only")
      val event = backend.events
        .find(_.message.contains("entity.conditional-transition"))
        .getOrElse(fail("transition audit missing"))
      val rendered = event.attributes.print
      rendered should include("diagnostic_root")
      rendered should include("diagnostic_successor")
      rendered should include("entity-conditional-transition")
      rendered should include("test-user-principal")
      rendered should include("trace")
      rendered should not include "sentinel-root-payload"
      rendered should not include "sentinel-successor-payload"
      rendered should not include "expected"
    }

    "project mismatch and structured failure outcomes into bounded diagnostics" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R19-R20; typed mismatch plus structured conflict, authorization, capability, provider, and transaction failures"
      )
      val backend = new StructuredMemoryBackend
      LogBackendHolder.install(backend)
      given ExecutionContext = ExecutionContext.create()
      val revision = EntityRevision.createC(3L).TAKE
      val context = EntityConditionalTransitionObservation.Context(
        "entity-conditional-transition",
        Some("phase49-test"),
        _root_id,
        _successor_collection.name,
        Some(_successor_id)
      )
      val results = Vector(
        Consequence.success(
          EntityConditionalTransitionResult.NotMatched(
            EntitySnapshot("sentinel-not-matched-payload", revision)
          )
        ),
        _failure(
          Consequence.operationConflict(
            "sentinel-conflict-display",
            Vector(
              Descriptor.Facet.Reason(
                "bound-successor-revision-conflict"
              )
            )
          )
        ),
        _failure(
          Consequence.securityPermissionDenied(
            "sentinel-authorization-display",
            Cause.Kind.Permission,
            Seq(Descriptor.Facet.Permission("read"))
          )
        ),
        _failure(
          Consequence.operationInvalid(
            "sentinel-capability-display",
            Vector(
              Descriptor.Facet.Reason("unsupported-capability"),
              Descriptor.Facet.Capability(
                "datastore.entity-conditional-transition"
              )
            )
          )
        ),
        _failure(
          Consequence.operationInvalid(
            "sentinel-provider-display",
            Vector(Descriptor.Facet.Reason("provider-failure"))
          )
        ),
        _failure(
          Consequence.operationInvalid(
            "sentinel-transaction-display",
            Vector(Descriptor.Facet.Reason("transaction-failure"))
          )
        )
      )

      When("the common observation boundary records every result")
      try
        results.foreach(
          EntityConditionalTransitionObservation.observe(context, _)
        )
      finally
        LogBackendHolder.reset()

      Then("runtime metrics retain only stable outcome and diagnostic labels")
      val outcomes =
        RuntimeDashboardMetrics
          .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
          .points
          .filter(_.scope == "entity.conditional-transition")
          .flatMap(_.labels.get("outcome"))
          .toSet
      outcomes should contain allOf (
        "not-matched",
        "conflict",
        "unauthorized",
        "unsupported-capability",
        "provider-failure",
        "transaction-failure"
      )

      And("audit records contain neither Entity payload nor display messages")
      val rendered = backend.events
        .filter(_.message.contains("entity.conditional-transition"))
        .map(_.attributes.print)
        .mkString("\n")
      rendered should not include "sentinel-not-matched-payload"
      rendered should not include "sentinel-conflict-display"
      rendered should not include "sentinel-authorization-display"
      rendered should not include "sentinel-capability-display"
      rendered should not include "sentinel-provider-display"
      rendered should not include "sentinel-transaction-display"
    }

    "summarize CallTree failures from Conclusion structure without error text" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rule: R20; one structured failure with a sentinel display message"
      )
      val failure = _failure(
        Consequence.operationConflict(
          "sentinel-private-expected-value",
          Vector(
            Descriptor.Facet.Reason(
              "bound-successor-revision-conflict"
            )
          )
        )
      )

      When("CallTree failure attributes are projected")
      val attributes =
        failure match {
          case Consequence.Failure(conclusion) =>
            CallTreeValueSummary.failureAttributes(conclusion)
          case _ =>
            fail("expected failure")
        }

      Then("only structured bounded fields remain")
      attributes.keySet should contain allOf (
        "status",
        "diagnostic_key",
        "taxonomy_category",
        "taxonomy_symptom"
      )
      attributes should not contain key("error")
      attributes.values.mkString(" ") should not include
        "sentinel-private-expected-value"
    }
  }

  private def _failure[A](
    consequence: Consequence[A]
  ): Consequence[EntityConditionalTransitionResult[String, String]] =
    consequence.asInstanceOf[
      Consequence[EntityConditionalTransitionResult[String, String]]
    ]

  private final class StructuredMemoryBackend extends LogBackend {
    private val _events = ListBuffer.empty[StructuredLogEvent]

    def events: Vector[StructuredLogEvent] =
      _events.synchronized(_events.toVector)

    override def logStructured(event: StructuredLogEvent): Unit =
      _events.synchronized(_events += event)

    override def writeLine(line: String): Unit = {
      val _ = line
    }
  }
}
