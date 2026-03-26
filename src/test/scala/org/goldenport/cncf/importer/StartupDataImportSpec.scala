package org.goldenport.cncf.importer

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, Query, QueryDirective}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class StartupDataImportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Startup import for cncf.import.data.file" should {
    "import a single yaml file at bootstrap" in {
      Given("a bootstrap config that points to one yaml file")
      val cwd = Files.createTempDirectory("cncf-startup-import-single")
      val file = cwd.resolve("data.yaml")
      Files.writeString(
        file,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: taro
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        given ExecutionContext = ExecutionContext.create()
        val subsystem = _initialize(runtime, cwd, file.toString)

        Then("the datastore contains the imported record")
        val space = subsystem.globalRuntimeContext.dataStoreSpace
        val cid = DataStore.CollectionId("startup_people")
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "taro")))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "import yaml files from a directory recursively in deterministic order" in {
      Given("a bootstrap config that points to a directory with nested yaml files")
      val cwd = Files.createTempDirectory("cncf-startup-import-dir")
      val nested = Files.createDirectories(cwd.resolve("a"))
      val later = cwd.resolve("b.yaml")
      val earlier = nested.resolve("a.yaml")
      Files.writeString(
        later,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: root-second
          |""".stripMargin
      )
      Files.writeString(
        earlier,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: nested-first
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        given ExecutionContext = ExecutionContext.create()
        val subsystem = _initialize(runtime, cwd, cwd.toString)

        Then("the records are imported in sorted path order")
        val space = subsystem.globalRuntimeContext.dataStoreSpace
        val cid = DataStore.CollectionId("startup_people")
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "root-second")))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail fast when an invalid file is encountered in a directory" in {
      Given("a bootstrap config with one invalid yaml file before one valid yaml file")
      val cwd = Files.createTempDirectory("cncf-startup-import-failfast")
      val nested = Files.createDirectories(cwd.resolve("a"))
      val invalid = nested.resolve("a.yaml")
      val valid = cwd.resolve("b.yaml")
      Files.writeString(
        invalid,
        "datastore: ["
      )
      Files.writeString(
        valid,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - name: should-not-import
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, cwd.toString)
        )

        Then("startup import fails deterministically before the valid file is imported")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("failed to load yaml")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a selected yaml file has no datastore section" in {
      Given("a bootstrap config that points to a yaml file without datastore")
      val cwd = Files.createTempDirectory("cncf-startup-import-empty")
      val file = cwd.resolve("empty.yaml")
      Files.writeString(
        file,
        """entitystore:
          |  - collection: test.1.person
          |    records:
          |      - id: p1
          |        name: taro
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, file.toString)
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import file has no datastore entries")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a selected yaml file has an empty datastore section" in {
      Given("a bootstrap config that points to a yaml file with an empty datastore section")
      val cwd = Files.createTempDirectory("cncf-startup-import-empty-datastore")
      val file = cwd.resolve("empty-datastore.yaml")
      Files.writeString(
        file,
        """datastore: []
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, file.toString)
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import file has no datastore entries")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }
  }

  private def _initialize(
    runtime: CncfRuntime,
    cwd: Path,
    importPath: String
  ) = {
    val result = runtime.initializeForEmbedding(
      cwd = cwd,
      args = _args(cwd, importPath)
    )
    result match {
      case Consequence.Success(subsystem) =>
        subsystem
      case Consequence.Failure(conclusion) =>
        fail(conclusion.show)
    }
  }

  private def _args(
    cwd: Path,
    importPath: String
  ): Array[String] =
    Array(
      s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
      s"--cncf.import.data.file=${importPath}"
    )
}
