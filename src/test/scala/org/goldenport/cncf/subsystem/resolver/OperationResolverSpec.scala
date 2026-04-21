package org.goldenport.cncf.subsystem.resolver

import cats.data.NonEmptyVector
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentletDescriptor}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage

class OperationResolverSpec extends AnyWordSpec with Matchers {
  "OperationResolver.resolve" should {
    "resolve 2-dot selector component.service.operation uniquely" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1", "c2.s2.op2"))
      resolver.resolve("c1.s1.op1") match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "c1.s1.op1"
        case other => fail(s"unexpected result: $other")
      }
    }

    "resolve 2-dot selector using prefix matching when unique" in {
      val resolver = OperationResolver.fromFqns(
        Seq("admin.user.find", "admin.user.list")
      )
      resolver.resolve("ad.us.fi") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "admin.user.find"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "resolve 1-dot selector service.operation when unique across components" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1", "c2.s2.op2"))
      resolver.resolve("s1.op1") match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "c1.s1.op1"
        case other => fail(s"unexpected result: $other")
      }
    }

    "resolve 1-dot selector using prefix matching when unique" in {
      val resolver = OperationResolver.fromFqns(
        Seq("admin.user.find", "admin.group.list")
      )
      resolver.resolve("user.fi") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "admin.user.find"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "resolve 0-dot selector operation when unique across all services" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.unique", "c2.s2.other"))
      resolver.resolve("unique") match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "c1.s1.unique"
        case other => fail(s"unexpected result: $other")
      }
    }

    "resolve 0-dot selector using prefix matching when unique" in {
      val resolver = OperationResolver.fromFqns(
        Seq("c1.s1.find", "c2.s2.list")
      )
      resolver.resolve("fi") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "c1.s1.find"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "return Ambiguous for 0-dot prefix matching when multiple candidates exist" in {
      val resolver = OperationResolver.fromFqns(
        Seq("c1.s1.find", "c2.s2.findAll")
      )
      resolver.resolve("find") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "c1.s1.find"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "return Ambiguous for prefix-only 0-dot matching when multiple candidates exist" in {
      val resolver = OperationResolver.fromFqns(
        Seq("c1.s1.findOne", "c2.s2.findAll")
      )
      resolver.resolve("find") match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set(
            "c1.s1.findOne",
            "c2.s2.findAll"
          )
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "return NotFound when no match exists (0-dot)" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1"))
      resolver.resolve("missing") match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Operation
        case other => fail(s"unexpected result: $other")
      }
    }

    "return NotFound when operation is missing (1-dot)" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1"))
      resolver.resolve("s1.does_not_exist") match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Operation
        case other => fail(s"unexpected result: $other")
      }
    }

    "return NotFound when component is unknown (2-dot)" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1"))
      resolver.resolve("unknown.s1.op1") match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Component
        case other => fail(s"unexpected result: $other")
      }
    }

    "return Ambiguous and list candidates as FQN (0-dot)" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op", "c2.s2.op"))
      resolver.resolve("op") match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set("c1.s1.op", "c2.s2.op")
        case other => fail(s"unexpected result: $other")
      }
    }

    "prefer exact component match over component prefix matches in FQN selector" in {
      val resolver = OperationResolver.fromFqns(
        Seq(
          "job_control.job.await_job_result",
          "job_control.job_admin.cancel_job",
          "job.job.get_job_status"
        )
      )

      resolver.resolve("job_control.job.await_job_result") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "job_control.job.await_job_result"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "return Ambiguous and list candidates as FQN (1-dot)" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.svc.op", "c2.svc.op"))
      resolver.resolve("svc.op") match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set("c1.svc.op", "c2.svc.op")
        case other => fail(s"unexpected result: $other")
      }
    }

    "treat 3+ dots as Invalid" in {
      val resolver = OperationResolver.fromFqns(Seq("c1.s1.op1"))
      resolver.resolve("a.b.c.d") match {
        case ResolutionResult.Invalid(_) => succeed
        case other => fail(s"unexpected result: $other")
      }
    }

    "resolve real componentlet as component selector when built from runtime components" in {
      val resolver = OperationResolver.build(Seq(_component_with_componentlet_metadata(), _componentlet_runtime_component()))

      resolver.resolve("public-notice.notice.search-notices") match {
        case ResolutionResult.Resolved(fqn, component, service, operation) =>
          fqn shouldBe "public-notice.notice.search-notices"
          component shouldBe "public-notice"
          service shouldBe "notice"
          operation shouldBe "search-notices"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "not resolve componentlet metadata alone as runtime component selector" in {
      val resolver = OperationResolver.build(Seq(_component_with_componentlet_metadata()))

      resolver.resolve("public-notice.notice.search-notices") match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
    }
  }

  "Single Operation Optimization (CLI / Script, FQN-only)" should {

    "resolve when exactly one non-builtin operation exists and selector is FQN" in {
      val resolver = OperationResolver.fromFqns(
        Seq("app.health.check")
      )

      resolver.resolve("app.health.check") match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "app.health.check"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "not apply to operation-name-only input such as ping" in {
      val resolver = OperationResolver.fromFqns(
        Seq("admin.default.ping")
      )

      resolver.resolve("ping") match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "not apply prefix matching during single operation optimization" in {
      val resolver = OperationResolver.fromFqns(
        Seq("app.health.check")
      )

      resolver.resolve("app.health.ch") match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
    }
  }

  private def _component_with_componentlet_metadata(): Component = {
    val component = new Component() {}
    component.withComponentDescriptors(
      Vector(
        ComponentDescriptor(
          name = Some("notice-board"),
          componentName = Some("notice-board"),
          componentlets = Vector(
            ComponentletDescriptor(name = "public-notice")
          )
        )
      )
    )
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(_NoopOperation("search-notices"))
            )
          )
        )
      )
    )
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = "notice-board",
      componentid = org.goldenport.cncf.component.ComponentId("notice_board"),
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(
        org.goldenport.cncf.component.ComponentId("notice_board")
      ),
      protocol = protocol
    )
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("notice-board"),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = component.componentDescriptors
      )
    )
  }

  private def _componentlet_runtime_component(): Component = {
    val component = new Component() {}
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(_NoopOperation("search-notices"))
            )
          )
        )
      )
    )
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = "public-notice",
      componentid = org.goldenport.cncf.component.ComponentId("public_notice"),
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(
        org.goldenport.cncf.component.ComponentId("public_notice")
      ),
      protocol = protocol
    )
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("public-notice"),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = Vector.empty
      )
    )
  }
}

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )
  override def createOperationRequest(req: org.goldenport.protocol.Request): org.goldenport.Consequence[org.goldenport.protocol.operation.OperationRequest] =
    org.goldenport.Consequence.notImplemented("not used")
}
