package org.goldenport.cncf.finding

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.finding.annotation.FindingDisposition
import FindingDispositionV1.*
import FindingDispositionRepresentation.*

/** Executable ABI, target binding and actual model-reinspection contract.
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
class FindingDispositionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = Target("component:orders", "operation:submit")
  private val _key = Key("car.required-operation", "missing-submit", _target)
  private val _finding = Finding(_key, "The submit operation is required")
  private val _coverage = Set(Coverage(_key.rule, _target))

  "Finding Disposition V1" should {
    "read actual compiled Scala annotations and round-trip accept and defer through portable properties" in {
      Given("handwritten Scala methods with runtime-retained public ABI annotations")
      val fixture = classOf[FindingDispositionFixture]
      When("a deterministic tool reads each exact semantic target")
      val accept = readC(fixture.getDeclaredMethod("accepted"), _target).take
      val defer = readC(fixture.getDeclaredMethod("deferred"), _target).take
      Then("annotation values, portable model properties and normalized decisions agree")
      accept.head.kind shouldBe Kind.Accept
      defer.head.kind shouldBe Kind.Defer
      Vector(accept.head, defer.head).foreach { declaration =>
        decodeC(encodeC(declaration).take).take shouldBe declaration
        val report = _interpret(Vector(declaration))
        report.results.head.status shouldBe (if (declaration.kind == Kind.Accept) Status.Accepted else Status.Deferred)
        report.actionableBacklog shouldBe empty
      }
      _interpret(defer).deferredBacklog shouldBe Vector(_finding)
      readC(fixture.getDeclaredMethod("accepted"), _target.copy(identity = "operation:cancel")).isSuccess shouldBe false
      readC(fixture.getDeclaredMethod("unannotated"), _target).take shouldBe empty
    }

    "observe a real model correction through the same lint before and after the edit" in {
      Given("a model without submit, an explicit fix decision and the actual model lint")
      val before = FindingDispositionFixtures.inspect(Vector("cancel"), "model-1", _finding)
      val after = FindingDispositionFixtures.inspect(Vector("cancel", "submit"), "model-2", _finding)
      val fix = _decision(Kind.Fix)
      When("the model is corrected and the tool supplies attributed changed-revision evidence")
      val result = interpretC(before, after, Vector(fix), Vector(ChangeEvidence(_key, "model-1", "model-2", "diff:model-1..model-2"))).take
      Then("the underlying lint finding disappears without an accept/suppression annotation")
      before.findings shouldBe Vector(_finding)
      after.findings shouldBe empty
      result.results.head.status shouldBe Status.Corrected
      result.actionableBacklog shouldBe empty
      interpretC(before, before, Vector(fix)).take.actionableBacklog shouldBe Vector(_finding)
    }

    "keep disappearing warnings unresolved without a decision, exact coverage and matching change evidence" in {
      Given("an old finding and an empty subsequent warning list")
      val before = Inspection("r1", Vector(_finding), _coverage)
      val absent = Inspection("r2", Vector.empty, _coverage)
      val fix = Vector(_decision(Kind.Fix))
      val change = Vector(ChangeEvidence(_key, "r1", "r2", "review:actual-diff"))
      When("any necessary observation or explicit correction decision is missing")
      val reports = Vector(
        interpretC(before, absent, Vector.empty, change),
        interpretC(before, absent, fix),
        interpretC(before, absent.copy(coverage = Set.empty), fix, change),
        interpretC(before, absent, fix, Vector(change.head.copy(after = "other-revision"))),
        interpretC(before, absent, Vector(_decision(Kind.Accept)), change)
      ).map(_.take)
      Then("the original finding remains in actionable backlog")
      reports.foreach(_.actionableBacklog shouldBe Vector(_finding))
    }

    "require actual reinspection of admitted configuration changes rather than treating configure as suppression" in {
      Given("a configuration decision whose rule still reports a finding")
      _interpret(Vector(_decision(Kind.Configure))).results.head.status shouldBe Status.Unresolved
      When("an adapter reports a changed effective configuration and completed reinspection")
      val result = interpretC(Inspection("config-1", Vector(_finding), _coverage),
        Inspection("config-2", Vector.empty, _coverage), Vector(_decision(Kind.Configure)),
        Vector(ChangeEvidence(_key, "config-1", "config-2", "configuration:approved-policy"))).take
      Then("configuration resolution has its own explicit outcome")
      result.results.head.status shouldBe Status.Configured
    }

    "bind decisions to the complete rule, finding, scope and target tuple" in {
      Given("an accepted decision for a nearby but different finding")
      val decision = _decision(Kind.Accept)
      val mismatches = Vector(
        decision.copy(key = _key.copy(rule = "car.other-rule")),
        decision.copy(key = _key.copy(finding = "another-occurrence")),
        decision.copy(key = _key.copy(target = _target.copy(scope = "component:other"))),
        decision.copy(key = _key.copy(target = _target.copy(identity = "operation:cancel")))
      )
      When("the tool interprets each decision against the original finding")
      Then("no wildcard or location-only suppression applies")
      mismatches.foreach(d => _interpret(Vector(d)).actionableBacklog shouldBe Vector(_finding))
    }

    "retain extensible new rule identities and every disposition kind without changing the ABI" in {
      Given("arbitrary future rule IDs and human-readable Unicode reasons")
      val generator = for {
        suffix <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
        kind <- Gen.oneOf(Kind.values.toVector)
      } yield ("future.rule." + suffix, kind)
      When("portable model declarations are encoded and decoded")
      val property = Prop.forAll(generator) { case (rule, kind) =>
        val declaration = _decision(kind).copy(key = _key.copy(rule = rule), reason = "業務上の判断: " + rule)
        val decoded = decodeC(encodeC(declaration).take).take
        decoded == declaration && canonicalRuleC(rule).take == rule && decoded.abiVersion == ABI_VERSION
      }
      Then("all generated identities and reasons survive unchanged")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property).passed shouldBe true
    }

    "resolve explicit deprecated rule names and reject ambiguous or cyclic compatibility maps" in {
      Given("an explicit rename path with retained deprecation provenance")
      val aliases = Vector(RuleAlias("old-rule", _key.rule, "release:2"))
      val olddecision = _decision(Kind.Accept).copy(key = _key.copy(rule = "old-rule"))
      When("the normalized interpreter uses the declared compatibility path")
      val report = interpretC(Inspection("r1", Vector(_finding), _coverage),
        Inspection("r2", Vector(_finding), _coverage), Vector(olddecision), aliases = aliases).take
      Then("the renamed rule matches, while invalid maps fail instead of guessing")
      report.results.head.status shouldBe Status.Accepted
      canonicalRuleC("new-rule").take shouldBe "new-rule"
      canonicalRuleC("old-rule", aliases :+ RuleAlias("old-rule", "different", "release:3")).isSuccess shouldBe false
      canonicalRuleC("unrelated", aliases :+ RuleAlias(_key.rule, "old-rule", "release:3")).isSuccess shouldBe false
    }

    "validate compatibility maps even when no finding or inspection coverage is present" in {
      Given("empty completed inspections and an otherwise unused compatibility map")
      val before = Inspection("r1", Vector.empty, Set.empty)
      val after = Inspection("r2", Vector.empty, Set.empty)
      val invalidmaps = Vector[Vector[RuleAlias]](
        null,
        Vector(null),
        Vector(RuleAlias("a", "b", "release:1"), RuleAlias("b", "a", "release:2")),
        Vector(RuleAlias("a", "b", "release:1"), RuleAlias("a", "c", "release:2")),
        Vector(RuleAlias("a", "b", ""))
      )
      When("the same interpreter receives invalid maps without traversing any finding keys")
      Then("invalid compatibility fails independently of observation cardinality")
      invalidmaps.foreach { aliases =>
        interpretC(before, after, Vector.empty, aliases = aliases).isSuccess shouldBe false
      }
      interpretC(before, after, Vector.empty).take.results shouldBe empty
      interpretC(before, after, Vector.empty,
        aliases = Vector(RuleAlias("a", "b", "release:1"), RuleAlias("b", "c", "release:2"))).take.results shouldBe empty
    }

    "reject incompatible versions, incomplete reasons, duplicate decisions and conflicting normalized bindings" in {
      Given("an otherwise valid accepted disposition")
      val accepted = _decision(Kind.Accept)
      val properties = encodeC(accepted).take
      When("invalid or incompatible data crosses a representation boundary")
      Then("it cannot silently suppress a finding")
      decodeC(properties.copy(abiVersion = 2)).isSuccess shouldBe false
      decodeC(properties.copy(kind = "suppress")).isSuccess shouldBe false
      Vector("", " ", " trailing ", "reason\nwith-control").foreach { reason =>
        validateC(accepted.copy(reason = reason)).isSuccess shouldBe false
      }
      validateC(accepted.copy(provenance = "")).isSuccess shouldBe false
      validateC(accepted.copy(key = _key.copy(finding = ""))).isSuccess shouldBe false
      val inspection = Inspection("r1", Vector(_finding), _coverage)
      interpretC(inspection, inspection, Vector(accepted, _decision(Kind.Defer))).isSuccess shouldBe false
      interpretC(inspection, inspection.copy(findings = Vector(_finding, _finding)), Vector.empty).isSuccess shouldBe false
      interpretC(inspection, inspection.copy(coverage = Set.empty), Vector.empty).isSuccess shouldBe false
      interpretC(inspection, inspection, Vector(accepted, accepted.copy(key = _key.copy(rule = "old"))),
        aliases = Vector(RuleAlias("old", _key.rule, "release:2"))).isSuccess shouldBe false
      validateC(null).isSuccess shouldBe false
      interpretC(inspection, inspection, null).isSuccess shouldBe false
    }

    "report a stable actionable backlog independently of warning count and input order" in {
      Given("one accepted, one deferred and one unresolved finding")
      val second = _finding.copy(key = _key.copy(finding = "second"))
      val third = _finding.copy(key = _key.copy(finding = "third"))
      val findings = Vector(_finding, second, third)
      val decisions = Vector(_decision(Kind.Accept), _decision(Kind.Defer).copy(key = second.key))
      val inspection = Inspection("r1", findings, _coverage)
      When("the interpreter receives equivalent reversed observations")
      val report = interpretC(inspection, inspection, decisions).take
      val reversed = interpretC(inspection.copy(findings = findings.reverse), inspection.copy(findings = findings.reverse), decisions.reverse).take
      Then("raw warnings remain three and actionable unresolved backlog is one")
      report shouldBe reversed
      report.results.size shouldBe 3
      report.actionableBacklog shouldBe Vector(third)
      report.deferredBacklog shouldBe Vector(second)
    }
  }

  private def _decision(kind: Kind): Declaration =
    Declaration(_key, kind, "Explicit domain decision", "journal:orders:decision-1")

  private def _interpret(decisions: Vector[Declaration]): Report = {
    val inspection = Inspection("revision-1", Vector(_finding), _coverage)
    interpretC(inspection, inspection, decisions).take
  }
}

object FindingDispositionFixtures {
  def inspect(operations: Vector[String], revision: String, finding: Finding): Inspection =
    Inspection(revision, if (operations.contains("submit")) Vector.empty else Vector(finding),
      Set(Coverage(finding.key.rule, finding.key.target)))
}

final class FindingDispositionFixture {
  @FindingDisposition(rule = "car.required-operation", finding = "missing-submit", scope = "component:orders",
    target = "operation:submit", kind = "accept", reason = "A separate component owns submission", provenance = "journal:orders:accept")
  def accepted(): Unit = ()

  @FindingDisposition(rule = "car.required-operation", finding = "missing-submit", scope = "component:orders",
    target = "operation:submit", kind = "defer", reason = "Implement submission in the next milestone", provenance = "issue:orders:42")
  def deferred(): Unit = ()

  def unannotated(): Unit = ()
}
