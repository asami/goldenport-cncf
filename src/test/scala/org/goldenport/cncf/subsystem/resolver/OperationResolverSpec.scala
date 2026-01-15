package org.goldenport.cncf.subsystem.resolver

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

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
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set(
            "c1.s1.find",
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
}
