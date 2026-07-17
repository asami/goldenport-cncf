package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 36 RB-05 admitted read-only resource trees.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceTreeAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _path_segments =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(8))

  "ResourceTreeAccess" should {
    "return a deterministic immutable snapshot from an explicitly injected in-memory tree" in {
      Given("an in-memory tree with entries in non-canonical order")
      val reference = ResourceTreeReference.parseC("review-target").toOption.get
      val entries = Vector(
        ResourceTreeEntry.createC("zeta.txt", Vector(3.toByte)).toOption.get,
        ResourceTreeEntry.createC("alpha/readme.txt", Vector(1.toByte, 2.toByte)).toOption.get
      )
      val access = ResourceTreeAccess.inMemory(Map(reference -> entries))

      When("the component-visible tree capability takes a snapshot")
      val result = access.snapshot(reference)

      Then("entries are ordered by logical path and expose no provider root")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe Some(Vector("alpha/readme.txt", "zeta.txt"))
      result.toOption.map(_.totalByteSize) shouldBe Some(3L)
      access.providerMetadata(reference).safeAttributes.map(_._1) should not contain "path"
    }

    "sort every generated in-memory entry permutation by its logical path" in {
      Given("generated safe logical entry names")
      val property = Prop.forAll(Gen.listOfN(8, _path_segments)) { names =>
        val reference = ResourceTreeReference.parseC("generated-tree").toOption.get
        val distinct = names.distinct.zipWithIndex.map { case (name, index) =>
          ResourceTreeEntry.createC(s"$name-$index.txt", Vector(index.toByte)).toOption.get
        }.toVector.reverse
        val snapshot = ResourceTreeAccess.inMemory(Map(reference -> distinct)).snapshot(reference)
        snapshot.toOption.exists(_.entries.map(_.relativePath) == distinct.map(_.relativePath).sorted)
      }

      When("the generated trees are snapshotted")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("ordering remains deterministic regardless of provider enumeration order")
      checked.passed shouldBe true
    }

    "reject unknown trees, unsafe traversal entries, and configured limits deterministically" in {
      Given("a bounded in-memory resource tree and invalid logical requests")
      val reference = ResourceTreeReference.parseC("bounded-tree").toOption.get
      val unknown = ResourceTreeReference.parseC("unknown-tree").toOption.get
      val unsafe = ResourceTreeEntry("../escape.txt", Vector(1.toByte))
      val entries = Vector(
        ResourceTreeEntry.createC("a.txt", Vector(1.toByte, 2.toByte)).toOption.get,
        ResourceTreeEntry.createC("nested/b.txt", Vector(3.toByte)).toOption.get
      )
      val access = ResourceTreeAccess.inMemory(Map(reference -> entries))
      val unsafeaccess = ResourceTreeAccess.inMemory(Map(reference -> Vector(unsafe)))

      When("the runtime admits unknown, unsafe, and over-limit requests")
      val unknownresult = access.snapshot(unknown)
      val traversalresult = unsafeaccess.snapshot(reference)
      val depthresult = access.snapshot(reference, ResourceTreeLimits(maxDepth = 1))
      val countresult = access.snapshot(reference, ResourceTreeLimits(maxEntries = 1))
      val fileresult = access.snapshot(reference, ResourceTreeLimits(maxFileBytes = 1))
      val totalresult = access.snapshot(reference, ResourceTreeLimits(maxTotalBytes = 2))

      Then("every rejected request is a structured failure without content or host path")
      Vector(unknownresult, traversalresult, depthresult, countresult, fileresult, totalresult)
        .forall(_.isFaillure) shouldBe true
    }

    "bind a logical tree to a local runtime root while refusing symbolic links" in {
      Given("a configured local root with one ordinary file and one escaping symbolic link")
      val root = Files.createTempDirectory("resource-tree-root")
      val foreignroot = Files.createTempDirectory("resource-tree-foreign")
      Files.createDirectories(root.resolve("metadata"))
      Files.writeString(root.resolve("metadata/readme.txt"), "catalog", StandardCharsets.UTF_8)
      val foreign = foreignroot.resolve("secret.txt")
      Files.writeString(foreign, "outside", StandardCharsets.UTF_8)
      Files.createSymbolicLink(root.resolve("escape.txt"), foreign)
      val reference = ResourceTreeReference.parseC("catalog").toOption.get
      val access = ResourceTreeAccess.local(ResourceTreePolicy(Map("catalog" -> root)))

      When("the configured provider walks the admitted tree")
      val result = access.snapshot(reference)

      Then("the symlink policy rejects the tree and no physical root appears in its diagnostic")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
      result.toString should not include root.toString
      result.toString should not include foreignroot.toString
    }

    "parse only absolute named local-root bindings at the runtime configuration boundary" in {
      Given("valid and invalid logical-tree to physical-root bindings")
      val valid = Vector("catalog=/tmp/catalog", "review-target=/tmp/review-target")
      val invalid = Vector("catalog_tree=/tmp/catalog", "catalog=relative/root", "catalog=/tmp/a", "catalog=/tmp/b")

      When("the runtime-owned policy is built before a provider is installed")
      val parsed = ResourceTreePolicy.fromValuesC(valid)
      val rejected = ResourceTreePolicy.fromValuesC(invalid)

      Then("only normalized logical identities are retained and malformed bindings fail")
      parsed.toOption.map(_.normalizedFileRoots.keySet) shouldBe Some(Set("catalog", "review-target"))
      rejected.isFaillure shouldBe true
    }
  }
}
