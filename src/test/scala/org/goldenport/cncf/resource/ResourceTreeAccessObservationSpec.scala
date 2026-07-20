package org.goldenport.cncf.resource

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for payload-safe resource-tree diagnostics.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceTreeAccessObservationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ResourceTreeAccess observation" should {
    "record only structural tree metadata in CallTree and runtime metrics" in {
      Given("an observed in-memory tree containing private payload bytes")
      val reference = ResourceTreeReference.parseC("source-tree").toOption.get
      val content = "private-review-content".getBytes("UTF-8").toVector
      val entry = ResourceTreeEntry.createC("review/source.txt", content).toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true),
        ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      )
      val before = RuntimeDashboardMetrics.resourceTreeSnapshot.summary.cumulative.total

      When("component code requests the logical tree through ExecutionContext")
      val result = context.resourceTrees.snapshot(reference)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.toString).getOrElse("")
      val after = RuntimeDashboardMetrics.resourceTreeSnapshot.summary.cumulative.total

      Then("the snapshot is recorded with safe logical metadata and without payload content")
      result.toOption.map(_.entries.size) shouldBe Some(1)
      rendered should include("dsl:resource-tree.snapshot")
      rendered should not include "private-review-content"
      after shouldBe before + 1
    }

    "record query structural metadata without selector values or payload content" in {
      Given("an observed in-memory tree with a matching private payload")
      val reference = ResourceTreeReference.parseC("source-tree").toOption.get
      val content = "private-project-content".getBytes("UTF-8").toVector
      val entry = ResourceTreeEntry.createC("review/project.yaml", content).toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true),
        ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      )
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get
      val before = RuntimeDashboardMetrics.resourceTreeQuerySnapshot.summary.cumulative.total

      When("component code queries through ExecutionContext.resourceTrees")
      val result = context.resourceTrees.query(query)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.toString).getOrElse("")
      val metrics = RuntimeDashboardMetrics.runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
      val after = RuntimeDashboardMetrics.resourceTreeQuerySnapshot.summary.cumulative.total

      Then("the decorator preserves query semantics and emits only safe diagnostics")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe Some(Vector("review/project.yaml"))
      rendered should include("dsl:resource-tree.query")
      rendered should not include "project.yaml"
      rendered should not include "private-project-content"
      metrics.points.exists(point =>
        point.scope == "resource-tree.query" &&
          point.labels.get("selector").contains("exact-leaf-name") &&
          point.labels.get("matched_entries").contains("1")
      ) shouldBe true
      after shouldBe before + 1
    }

    "record structured diagnostics for failed resource-tree queries" in {
      Given("an observed context without the requested logical tree")
      val reference = ResourceTreeReference.parseC("missing-tree").toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.create(),
        ResourceTreeAccess.inMemory(Map.empty)
      )
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get
      val before = RuntimeDashboardMetrics.resourceTreeQuerySnapshot.summary.cumulative.errors

      When("component code queries a missing logical tree")
      val result = context.resourceTrees.query(query)
      val after = RuntimeDashboardMetrics.resourceTreeQuerySnapshot.summary.cumulative.errors

      Then("the original structured failure is preserved and counted")
      result.isFaillure shouldBe true
      after shouldBe before + 1
    }
  }
}
