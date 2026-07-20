package org.goldenport.cncf.resource

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 43 bounded resource-tree query models.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceTreeQuerySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _directories =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(8))

  "ResourceTreeQuery" should {
    "use finite defaults that admit a bounded multi-project development workspace" in {
      Given("the standard resource-tree query policy")
      val limits = ResourceTreeQueryLimits.default

      When("its traversal boundaries are inspected")
      val dimensions = Vector(
        limits.maxDepth.toLong,
        limits.maxVisitedDirectories.toLong,
        limits.maxEntries.toLong,
        limits.maxEntryBytes,
        limits.maxTotalBytes
      )

      Then("every dimension is finite and the workspace traversal caps remain practical")
      dimensions.forall(x => x > 0L && x < Long.MaxValue) shouldBe true
      limits.maxDepth shouldBe 32
      limits.maxVisitedDirectories shouldBe 100000
    }

    "select only an exact safe leaf name from an injected tree" in {
      Given("an in-memory tree containing matching and non-matching logical entries")
      val reference = ResourceTreeReference.parseC("working").toOption.get
      val entries = Vector(
        ResourceTreeEntry.createC("zeta/project.yaml", Vector(1.toByte)).toOption.get,
        ResourceTreeEntry.createC("alpha/readme.md", Vector(2.toByte)).toOption.get,
        ResourceTreeEntry.createC("alpha/project.yaml", Vector(3.toByte)).toOption.get
      )
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get
      val access = ResourceTreeAccess.inMemory(Map(reference -> entries))

      When("the exact leaf-name query is evaluated")
      val result = access.query(query)

      Then("only matching entries are returned in deterministic logical-path order")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe
        Some(Vector("alpha/project.yaml", "zeta/project.yaml"))
      result.toOption.map(_.visitedDirectoryCount) shouldBe Some(0)
      result.toOption.map(_.totalByteSize) shouldBe Some(2L)
    }

    "reject unsafe selector names and limit widening while composing runtime caps" in {
      Given("a logical tree and an admitted query limit")
      val reference = ResourceTreeReference.parseC("working").toOption.get
      val admitted = ResourceTreeQueryLimits(
        maxDepth = 3,
        maxVisitedDirectories = 8,
        maxEntries = 4,
        maxEntryBytes = 16L,
        maxTotalBytes = 32L
      )

      When("a caller supplies unsafe selector names or tries to broaden one limit")
      val unsafe = Vector("", ".", "..", "nested/project.yaml", "../project.yaml", "project\\.yaml")
        .map(ResourceTreeEntrySelector.exactLeafNameC)
      val widened = admitted.tightenC(admitted.copy(maxEntries = 5))
      val narrowed = admitted.tightenC(admitted.copy(maxEntries = 2))
      val runtimecapped = admitted.narrowC(admitted.copy(maxEntries = 2, maxTotalBytes = 16L))
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml", admitted)
      val narrowedquery = query.flatMap(_.narrowC(admitted.copy(maxEntries = 2)))

      Then("invalid selectors and widening fail while component and runtime limits only narrow")
      unsafe.forall(_.isFaillure) shouldBe true
      widened.isFaillure shouldBe true
      narrowed.toOption.map(_.maxEntries) shouldBe Some(2)
      runtimecapped.toOption.map(_.maxTotalBytes) shouldBe Some(16L)
      query.toOption.map(_.selector) shouldBe Some(ResourceTreeEntrySelector.ExactLeafName("project.yaml"))
      narrowedquery.toOption.map(_.limits.maxEntries) shouldBe Some(2)
    }

    "preserve exact-name query ordering for generated provider entry permutations" in {
      Given("generated logical directories whose entries share one selected leaf name")
      val property = Prop.forAll(Gen.listOfN(8, _directories)) { names =>
        val reference = ResourceTreeReference.parseC("generated-working").toOption.get
        val entries = names.distinct.zipWithIndex.map { case (name, index) =>
          ResourceTreeEntry.createC(s"$name-$index/project.yaml", Vector(index.toByte)).toOption.get
        }.toVector.reverse
        val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get
        val result = ResourceTreeAccess.inMemory(Map(reference -> entries)).query(query)
        result.toOption.exists(_.entries.map(_.relativePath) == entries.map(_.relativePath).sorted)
      }

      When("the query is checked across generated provider enumeration orders")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("the returned logical-path order remains deterministic")
      checked.passed shouldBe true
    }
  }
}
