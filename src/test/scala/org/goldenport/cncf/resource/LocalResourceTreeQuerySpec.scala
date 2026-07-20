package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 43 bounded local resource-tree queries.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class LocalResourceTreeQuerySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _directories =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(8))

  "LocalResourceTreeAccess query" should {
    "select matching regular files while strict snapshots retain deny-all symlink behavior" in {
      Given("a local tree with matching files and an unrelated symbolic link")
      val root = Files.createTempDirectory("resource-tree-query")
      val foreignroot = Files.createTempDirectory("resource-tree-query-foreign")
      try {
        val foreign = foreignroot.resolve("private.txt")
        Files.createDirectories(root.resolve("alpha"))
        Files.createDirectories(root.resolve("links"))
        Files.createDirectories(root.resolve("zeta"))
        _write(root, "alpha/project.yaml", "alpha")
        _write(root, "zeta/project.yaml", "zeta")
        _write(root, "readme.md", "ignore")
        Files.writeString(foreign, "outside", StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("links/unrelated-link"), foreign)
        val reference = ResourceTreeReference.parseC("working").toOption.get
        val access = ResourceTreeAccess.local(ResourceTreePolicy(Map("working" -> root)))
        val query = _query_c(reference)

        When("the bounded exact-name query and strict snapshot are requested")
        val result = access.query(query)
        val snapshot = access.snapshot(reference)

        Then("the query skips the unrelated link while snapshot behavior remains unchanged")
        result.toOption.map(_.entries.map(_.relativePath)) shouldBe
          Some(Vector("alpha/project.yaml", "zeta/project.yaml"))
        result.toOption.map(_.visitedDirectoryCount) shouldBe Some(4)
        snapshot.isFaillure shouldBe true
        result.toString should not include root.toString
        result.toString should not include foreignroot.toString
      } finally {
        _delete_tree(root)
        _delete_tree(foreignroot)
      }
    }

    "reject symbolic roots and matching symbolic or non-regular entries" in {
      Given("separate local roots containing each prohibited query target")
      val root = Files.createTempDirectory("resource-tree-query-target")
      val foreignroot = Files.createTempDirectory("resource-tree-query-target-foreign")
      val linkedroot = root.getParent.resolve(s"${root.getFileName}-link")
      val symlinkroot = Files.createTempDirectory("resource-tree-query-symlink")
      val directoryroot = Files.createTempDirectory("resource-tree-query-directory")
      try {
        val foreign = foreignroot.resolve("project.yaml")
        Files.writeString(foreign, "outside", StandardCharsets.UTF_8)
        Files.createSymbolicLink(linkedroot, root)
        Files.createDirectories(symlinkroot.resolve("nested"))
        Files.createSymbolicLink(symlinkroot.resolve("nested/project.yaml"), foreign)
        Files.createDirectories(directoryroot.resolve("project.yaml"))
        val linked = ResourceTreeReference.parseC("linked").toOption.get
        val symlink = ResourceTreeReference.parseC("symlink").toOption.get
        val directory = ResourceTreeReference.parseC("directory").toOption.get
        val access = ResourceTreeAccess.local(ResourceTreePolicy(Map(
          "linked" -> linkedroot,
          "symlink" -> symlinkroot,
          "directory" -> directoryroot
        )))

        When("each root is queried for the selected leaf name")
        val linkedresult = access.query(_query_c(linked))
        val symlinkresult = access.query(_query_c(symlink))
        val directoryresult = access.query(_query_c(directory))

        Then("every prohibited target is a structured failure without host-path disclosure")
        Vector(linkedresult, symlinkresult, directoryresult).forall(_.isFaillure) shouldBe true
        Vector(linkedresult, symlinkresult, directoryresult).map(_.toString).mkString("\n") should not include root.toString
        Vector(linkedresult, symlinkresult, directoryresult).map(_.toString).mkString("\n") should not include foreignroot.toString
      } finally {
        Files.deleteIfExists(linkedroot)
        _delete_tree(root)
        _delete_tree(foreignroot)
        _delete_tree(symlinkroot)
        _delete_tree(directoryroot)
      }
    }

    "enforce depth visit match entry-byte aggregate-byte and provider-limit boundaries" in {
      Given("local trees that independently exceed each query bound")
      val depthrow = Files.createTempDirectory("resource-tree-query-depth")
      val visitroot = Files.createTempDirectory("resource-tree-query-visit")
      val matchroot = Files.createTempDirectory("resource-tree-query-match")
      val entryroot = Files.createTempDirectory("resource-tree-query-entry")
      val aggregateroot = Files.createTempDirectory("resource-tree-query-aggregate")
      try {
        _write(depthrow, "nested/project.yaml", "depth")
        _write(visitroot, "nested/project.yaml", "visit")
        _write(matchroot, "alpha/project.yaml", "one")
        _write(matchroot, "zeta/project.yaml", "two")
        _write(entryroot, "project.yaml", "oversize")
        _write(aggregateroot, "alpha/project.yaml", "two")
        _write(aggregateroot, "zeta/project.yaml", "two")
        val depth = ResourceTreeReference.parseC("depth").toOption.get
        val visit = ResourceTreeReference.parseC("visit").toOption.get
        val matching = ResourceTreeReference.parseC("matching").toOption.get
        val entry = ResourceTreeReference.parseC("entry").toOption.get
        val aggregate = ResourceTreeReference.parseC("aggregate").toOption.get
        val unknown = ResourceTreeReference.parseC("unknown").toOption.get
        val access = ResourceTreeAccess.local(ResourceTreePolicy(Map(
          "depth" -> depthrow,
          "visit" -> visitroot,
          "matching" -> matchroot,
          "entry" -> entryroot,
          "aggregate" -> aggregateroot
        )))

        When("queries are constrained at each local traversal boundary")
        val depthresult = access.query(_query_c(depth, ResourceTreeQueryLimits(maxDepth = 1)))
        val visitresult = access.query(_query_c(visit, ResourceTreeQueryLimits(maxDepth = 2, maxVisitedDirectories = 1)))
        val matchresult = access.query(_query_c(matching, ResourceTreeQueryLimits(maxEntries = 1)))
        val entryresult = access.query(_query_c(entry, ResourceTreeQueryLimits(maxEntryBytes = 4L)))
        val aggregateresult = access.query(_query_c(aggregate, ResourceTreeQueryLimits(maxTotalBytes = 3L)))
        val unknownresult = access.query(_query_c(unknown))
        val capped = ResourceTreeAccess.local(ResourceTreePolicy(
          Map("depth" -> depthrow),
          queryLimits = ResourceTreeQueryLimits(maxEntries = 1)
        )).query(_query_c(depth))

        Then("each query failure remains deterministic and provider caps reject broad requests")
        Vector(depthresult, visitresult, matchresult, entryresult, aggregateresult, unknownresult, capped)
          .forall(_.isFaillure) shouldBe true
      } finally {
        Vector(depthrow, visitroot, matchroot, entryroot, aggregateroot).foreach(_delete_tree)
      }
    }

    "preserve deterministic ordering for generated local directory layouts" in {
      Given("generated local directory names containing one selected leaf each")
      val property = Prop.forAll(Gen.listOfN(8, _directories)) { names =>
        val root = Files.createTempDirectory("resource-tree-query-order")
        try {
          val reference = ResourceTreeReference.parseC("working").toOption.get
          val paths = names.distinct.zipWithIndex.map { case (name, index) =>
            val path = s"$name-$index/project.yaml"
            _write(root, path, index.toString)
            path
          }.toVector.reverse
          val result = ResourceTreeAccess.local(ResourceTreePolicy(Map("working" -> root)))
            .query(_query_c(reference))
          result.toOption.exists(_.entries.map(_.relativePath) == paths.sorted)
        } finally {
          _delete_tree(root)
        }
      }

      When("the local provider queries each generated directory layout")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(16), property)

      Then("returned logical paths remain deterministic independently of directory enumeration")
      checked.passed shouldBe true
    }
  }

  private def _query_c(
    reference: ResourceTreeReference,
    limits: ResourceTreeQueryLimits = ResourceTreeQueryLimits.default
  ): ResourceTreeQuery =
    ResourceTreeQuery.exactLeafNameC(reference, "project.yaml", limits).toOption.get

  private def _write(root: Path, relative: String, content: String): Unit = {
    val path = root.resolve(relative)
    Option(path.getParent).foreach(x => Files.createDirectories(x))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }

  private def _delete_tree(root: Path): Unit =
    if (Files.exists(root)) {
      val stream = Files.walk(root)
      try {
        stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
