package org.goldenport.cncf.resource

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
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

    "preserve resource-tree query capability through the observed decorator" in {
      Given("an observed in-memory tree with one matching entry")
      val reference = ResourceTreeReference.parseC("source-tree").toOption.get
      val entry = ResourceTreeEntry.createC("review/project.yaml", Vector(1.toByte)).toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.create(),
        ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      )
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get

      When("component code queries through ExecutionContext.resourceTrees")
      val result = context.resourceTrees.query(query)

      Then("the decorator delegates to the installed provider instead of changing query semantics")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe Some(Vector("review/project.yaml"))
    }
  }
}
