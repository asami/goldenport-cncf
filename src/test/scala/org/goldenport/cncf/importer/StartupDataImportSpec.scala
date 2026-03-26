package org.goldenport.cncf.importer

import java.nio.file.{Files, Path}
import java.net.InetSocketAddress
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
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
    "import data from a URL via UnitOfWork-backed fetch" in {
      Given("a bootstrap URL source served over HTTP")
      val cwd = Files.createTempDirectory("cncf-startup-import-url-data")
      val server = _http_server(
        "/data.yaml",
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: url-data
          |""".stripMargin
      )
      server.start()

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/data.yaml"
        given ExecutionContext = ExecutionContext.create()
        val subsystem = _initialize(runtime, cwd, url)

        Then("the URL content is imported")
        val space = subsystem.globalRuntimeContext.dataStoreSpace
        val cid = DataStore.CollectionId("startup_people")
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "url-data")))
      } finally {
        runtime.closeEmbedding()
        server.stop(0)
      }
    }

    "import a default data directory at bootstrap when no explicit config is set" in {
      Given("a bootstrap cwd with a default data.d directory")
      val cwd = Files.createTempDirectory("cncf-startup-import-default-data")
      val dir = Files.createDirectories(cwd.resolve("data.d"))
      val file = dir.resolve("a.yaml")
      Files.writeString(
        file,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: default-data
          |""".stripMargin
      )

      When("bootstrapping the runtime without explicit import config")
      val runtime = new CncfRuntime()
      try {
        given ExecutionContext = ExecutionContext.create()
        val subsystem = runtime.initializeForEmbedding(
          cwd = cwd,
          args = Array(s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}")
        ) match {
          case Consequence.Success(subsystem) => subsystem
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }

        Then("the default data directory is imported")
        val space = subsystem.globalRuntimeContext.dataStoreSpace
        val cid = DataStore.CollectionId("startup_people")
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "default-data")))
      } finally {
        runtime.closeEmbedding()
      }
    }

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

    "import both explicit data config and the default data directory" in {
      Given("a bootstrap cwd with both explicit data config and a default data.d directory")
      val cwd = Files.createTempDirectory("cncf-startup-import-data-override")
      val dir = Files.createDirectories(cwd.resolve("data.d"))
      val defaultFile = dir.resolve("default.yaml")
      val explicitFile = cwd.resolve("explicit.yaml")
      Files.writeString(
        defaultFile,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p2
          |        name: default-data
          |""".stripMargin
      )
      Files.writeString(
        explicitFile,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: explicit-data
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        given ExecutionContext = ExecutionContext.create()
        val subsystem = _initialize(runtime, cwd, explicitFile.toString)

        Then("both sources are imported in deterministic order")
        val space = subsystem.globalRuntimeContext.dataStoreSpace
        val cid = DataStore.CollectionId("startup_people")
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p1"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p1", "name" -> "explicit-data")))
        space.dataStore(cid).flatMap(_.load(cid, DataStore.StringEntryId("p2"))) shouldBe
          Consequence.success(Some(Record.dataAuto("id" -> "p2", "name" -> "default-data")))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "do nothing when no explicit config and no default data directory exist" in {
      Given("a bootstrap cwd without explicit config or data.d")
      val cwd = Files.createTempDirectory("cncf-startup-import-data-none")

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        given ExecutionContext = ExecutionContext.create()
        runtime.initializeForEmbedding(
          cwd = cwd,
          args = Array(s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}")
        ) match {
          case Consequence.Success(_) => succeed
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a data URL fetch fails" in {
      Given("a bootstrap URL source whose fetch returns an error")
      val cwd = Files.createTempDirectory("cncf-startup-import-url-fail")
      val server = _http_server("/data.yaml", "not-found", 404)
      server.start()

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/data.yaml"
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = Array(
            s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
            s"--cncf.runtime.http.driver=real",
            s"--cncf.import.data.file=${url}"
          )
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) => fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import URL fetch failed")
        }
      } finally {
        runtime.closeEmbedding()
        server.stop(0)
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

  private def _http_server(
    path: String,
    body: String,
    status: Int = 200
  ): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext(path, new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        val os = exchange.getResponseBody
        try os.write(bytes)
        finally os.close()
      }
    })
    server
  }
