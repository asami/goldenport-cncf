package org.goldenport.cncf.projection

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.rule.{RuleFamily, RuleId, RulePriority, RuleSet, RuleSetId, RuleSetIdentity, RuleSetVersion, Rule}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for read-only RuleSet metadata in standard
 * subsystem introspection projections.
 *
 * @since   Jul. 16, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleSetProjectionIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "DescribeProjection and SchemaProjection" should {
    "expose descriptor RuleSets only on the subsystem-level read-only surface" in {
      Given("a subsystem with a descriptor-owned RuleSet")
      val component = _component_with_rule_set()

      When("subsystem describe and schema projections are requested")
      val describe = DescribeProjection.project(component)
      val schema = SchemaProjection.project(component)

      Then("both projections contain deterministic rule declaration metadata")
      _rule_sets(describe).map(_.getString("id").getOrElse("")) shouldBe Vector("tax")
      _rule_sets(schema).map(_.getString("version").getOrElse("")) shouldBe Vector("2026-07")

      And("component-level projections do not duplicate subsystem-owned RuleSets")
      DescribeProjection.project(component, Some(component.name)).getAny("ruleSets") shouldBe None
      SchemaProjection.project(component, Some(component.name)).getAny("ruleSets") shouldBe None
    }
  }

  private def _component_with_rule_set(): Component = {
    val ruleset = _success(RuleSet.createC(
      RuleSetIdentity(RuleSetId("tax"), RuleSetVersion("2026-07")),
      Vector(Rule(RuleId("calculate-tax"), RuleFamily.Calculation, RulePriority(10)))
    ))
    val subsystem = Subsystem(
      name = "rule-projection",
      scopeContext = Some(ScopeContext(
        kind = ScopeKind.Subsystem,
        name = "rule-projection",
        parent = None,
        observabilitycontext = ExecutionContext.create().observability
      )),
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    ).withDescriptor(GenericSubsystemDescriptor(
      path = Path.of("<memory>"),
      subsystemName = "rule-projection",
      componentBindings = Vector(GenericSubsystemComponentBinding("catalog")),
      ruleSets = Vector(ruleset)
    ))
    val owner = subsystem
    val component = new Component() {
      override val core: Component.Core = Component.Core.create(
        "org.goldenport.cncf.test.Catalog",
        ComponentId("org.goldenport.cncf.test.Catalog"),
        ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.Catalog")),
        Protocol.empty
      )

      override def subsystem: Option[Subsystem] = Some(owner)
    }
    subsystem.add(Vector(component)).findComponent(ComponentId("org.goldenport.cncf.test.Catalog")).get
  }

  private def _rule_sets(record: Record): Vector[Record] =
    record.getAny("ruleSets") match {
      case Some(values: Seq[?]) => values.collect { case value: Record => value }.toVector
      case _ => Vector.empty
    }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(s"expected success: ${result}"))
}
