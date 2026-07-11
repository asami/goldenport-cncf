package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  6, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentDataStoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component DataStore selection" should {
    "prefer a named component datastore over the basic runtime datastore" in {
      val root = Files.createTempDirectory("cncf-component-datastore")
      val basic = root.resolve("basic.db")
      val dedicated = root.resolve("dedicated.db")
      val params = _params(
        "textus.datastore.sqlite.path" -> basic.toString,
        "textus.component.art-scene.datastores.application.sqlite.path" -> dedicated.toString
      )
      given ExecutionContext = ExecutionContext.create()

      Given("both a basic runtime datastore and a component dedicated datastore")
      val selected = ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the dedicated datastore contains the record")
      _load_marker(dedicated) shouldBe Some("selected")
      _load_marker(basic) shouldBe None
    }

    "keep the legacy application datastore alias for compatibility" in {
      val root = Files.createTempDirectory("cncf-component-legacy-datastore")
      val dedicated = root.resolve("dedicated.db")
      val params = _params(
        "textus.component.art-scene.datastore.sqlite.path" -> dedicated.toString
      )
      given ExecutionContext = ExecutionContext.create()

      Given("a legacy component application datastore setting")
      val selected = ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the legacy dedicated datastore is used")
      _load_marker(dedicated) shouldBe Some("selected")
    }

    "select a component datastore from generic local test path configuration" in {
      val root = Files.createTempDirectory("cncf-component-local-path-datastore")
      val dedicated = root.resolve("application.db")
      val params = _params(
        "textus.component.art-scene.datastores.application.kind" -> "local",
        "textus.component.art-scene.datastores.application.path" -> dedicated.toString
      )
      given ExecutionContext = ExecutionContext.create()

      Given("a test descriptor style component datastore path")
      val selected = ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the target-owned local datastore is used without SQLite-specific public keys")
      _load_marker(dedicated) shouldBe Some("selected")
    }

    "reject an explicit component local datastore without a path" in {
      Given("a component datastore explicitly configured as local without a path")
      val params = _params(
        "textus.component.art-scene.datastores.application.kind" -> "local"
      )

      When("the component datastore is resolved")
      val thrown = intercept[IllegalArgumentException] {
        ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))
      }

      Then("configuration fails instead of selecting another datastore")
      thrown.getMessage should include ("textus.component.art-scene.datastores.application.path is required")
    }

    "use the basic runtime datastore when it is persistent and no dedicated datastore is configured" in {
      val root = Files.createTempDirectory("cncf-component-basic-datastore")
      val basic = root.resolve("basic.db")
      val params = _params("textus.datastore.sqlite.path" -> basic.toString)
      given ExecutionContext = ExecutionContext.create()

      Given("a persistent basic runtime datastore")
      val selected = ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the basic datastore contains the record")
      _load_marker(basic) shouldBe Some("selected")
    }

    "create the runtime datastore from generic local test path configuration" in {
      val root = Files.createTempDirectory("cncf-runtime-local-path-datastore")
      val runtime = root.resolve("runtime.db")
      val configuration = _config(
        "textus.datastore.kind" -> "local",
        "textus.datastore.path" -> runtime.toString
      )
      given ExecutionContext = ExecutionContext.create()

      Given("a test descriptor style runtime datastore path")
      val space = DataStoreSpace.create(configuration)

      When("the runtime writes through the datastore space")
      _write_marker(space.dataStore(DataStore.CollectionId("component_selection")).toOption.get)

      Then("the target-owned runtime datastore is used without SQLite-specific public keys")
      _load_marker(runtime) shouldBe Some("selected")
    }

    "reject an explicit runtime local datastore without a path" in {
      Given("a runtime datastore explicitly configured as local without a path")
      val configuration = _config(
        "textus.datastore.kind" -> "local"
      )

      When("the runtime datastore space is created")
      val thrown = intercept[IllegalArgumentException] {
        DataStoreSpace.create(configuration)
      }

      Then("configuration fails instead of falling back to in-memory storage")
      thrown.getMessage should include ("textus.datastore.path is required")
    }

    "fall back to the component local datastore for local-default CAR policy" in {
      val root = Files.createTempDirectory("cncf-component-local-datastore")
      val params = _params(
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("no dedicated datastore and no persistent basic runtime datastore")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the local datastore is used")
      _load_marker(expected) shouldBe Some("selected")
    }

    "default to in-memory for external-default policy when no persistent datastore is configured" in {
      val root = Files.createTempDirectory("cncf-component-external-default")
      val params = _params(
        "textus.local-data.root" -> root.toString
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("no policy and no persistent datastore")
      val selected = ComponentDataStore.resolve(params, ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the local datastore is not created")
      _load_marker(expected) shouldBe None
    }

    "keep an existing datastore space store for external-default when no persistent datastore is configured" in {
      val root = Files.createTempDirectory("cncf-component-existing-space")
      val existing = root.resolve("existing.db")
      val space = new DataStoreSpace().useDataStore(SqlDataStore.sqlite(existing.toString))
      given ExecutionContext = ExecutionContext.create()

      Given("a runtime datastore space already has a datastore fixture")
      space.useApplicationDataStore(ComponentDataStore.Environment(_params()), "art-scene", "application")

      When("the component writes through the space after application datastore selection")
      val selected = space.dataStore(DataStore.CollectionId("component_selection")).toOption.get
      _write_marker(selected)

      Then("the existing datastore remains the selected store")
      _load_marker(existing) shouldBe Some("selected")
    }

    "ignore an in-memory basic datastore and fall back to local persistence for local-default policy" in {
      val root = Files.createTempDirectory("cncf-component-inmemory-basic")
      val params = _params(
        "textus.datastore.kind" -> "in-memory",
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("the basic runtime datastore is explicitly in-memory")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the component still uses local persistent storage")
      _load_marker(expected) shouldBe Some("selected")
    }

    "let runtime policy override CAR component policy" in {
      val root = Files.createTempDirectory("cncf-component-policy-override")
      val params = _params(
        "textus.component.art-scene.datastores.application.policy" -> "external-default",
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("runtime and CAR component policies disagree")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the runtime policy wins")
      _load_marker(expected) shouldBe None
    }

    "let a runtime compatibility policy key override a CAR primary policy key" in {
      val root = Files.createTempDirectory("cncf-component-compat-policy-override")
      val params = _params(
        "cncf.component.art-scene.datastores.application.policy" -> "external-default",
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("a runtime compatibility key and a CAR primary key disagree")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("all runtime keys are preferred before CAR config keys")
      _load_marker(expected) shouldBe None
    }

    "bind a component datastore to the current action without replacing the shared datastore space" in {
      val root = Files.createTempDirectory("cncf-component-scoped-datastore")
      val existing = root.resolve("existing.db")
      val localroot = root.resolve("local")
      val space = new DataStoreSpace().useDataStore(SqlDataStore.sqlite(existing.toString))
      val params = _params(
        "textus.local-data.root" -> localroot.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val local = localroot.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("a shared datastore space with an existing entity store")
      space.bindApplicationDataStore(_env(params, config), "art-scene", "application")

      When("the current action writes while the component datastore is bound")
      val scoped = space.dataStore(DataStore.CollectionId("component_selection")).toOption.get
      _write_marker(scoped)
      space.clearBoundDataStore()
      val shared = space.dataStore(DataStore.CollectionId("component_selection")).toOption.get
      _write_marker(shared)

      Then("the scoped write uses local storage and the shared store remains available after clearing")
      _load_marker(local) shouldBe Some("selected")
      _load_marker(existing) shouldBe Some("selected")
    }

    "restore an outer component datastore after a nested action binding ends" in {
      val root = Files.createTempDirectory("cncf-component-nested-datastore")
      val outer = SqlDataStore.sqlite(root.resolve("outer.db").toString)
      val inner = SqlDataStore.sqlite(root.resolve("inner.db").toString)
      val space = new DataStoreSpace()
      given ExecutionContext = ExecutionContext.create()

      Given("an outer action datastore binding")
      space.bindDataStore(outer)
      val outerbinding = space.captureBinding()

      When("a nested action temporarily replaces and then restores the binding")
      space.bindDataStore(inner)
      _write_marker(space.dataStore(DataStore.CollectionId("component_selection")).toOption.get)
      space.restoreBinding(outerbinding)
      _write_marker(space.dataStore(DataStore.CollectionId("component_selection")).toOption.get)

      Then("the nested and outer writes remain isolated in their respective datastores")
      _load_marker(root.resolve("inner.db")) shouldBe Some("selected")
      _load_marker(root.resolve("outer.db")) shouldBe Some("selected")
    }

    "ignore external settings for local-only policy" in {
      val root = Files.createTempDirectory("cncf-component-local-only")
      val basic = root.resolve("basic.db")
      val params = _params(
        "textus.datastore.sqlite.path" -> basic.toString,
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-only"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("local-only policy and an external basic datastore")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("art-scene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the local datastore is used")
      _load_marker(expected) shouldBe Some("selected")
      _load_marker(basic) shouldBe None
    }

    "fail when external-required has no persistent datastore" in {
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "external-required"
      )

      Given("external-required policy without a configured persistent datastore")

      When("the component datastore is resolved")
      val thrown = intercept[IllegalArgumentException] {
        ComponentDataStore.resolve(_env(_params(), config), ComponentDataStore.Request("art-scene"))
      }

      Then("selection reports the missing datastore")
      thrown.getMessage should include ("Persistent datastore is required")
    }

    "normalize a Scala-style component name to the local data directory name" in {
      val root = Files.createTempDirectory("cncf-component-normalized-local")
      val params = _params(
        "textus.local-data.root" -> root.toString
      )
      val config = _config(
        "textus.component.art-scene.datastores.application.policy" -> "local-default"
      )
      val expected = root.resolve("art-scene").resolve("application.db")
      given ExecutionContext = ExecutionContext.create()

      Given("a component name from a generated Scala component")
      val selected = ComponentDataStore.resolve(_env(params, config), ComponentDataStore.Request("ArtScene"))

      When("the component writes through the selected datastore")
      _write_marker(selected)

      Then("the local datastore directory uses the component name")
      _load_marker(expected) shouldBe Some("selected")
    }
  }

  private def _write_marker(
    datastore: DataStore
  )(using ExecutionContext): Unit = {
    val result = datastore.create(
      DataStore.CollectionId("component_selection"),
      DataStore.StringEntryId("marker"),
      Record.data("value" -> "selected")
    )
    result match {
      case Consequence.Success(_) => ()
      case other => fail(s"unexpected write result: $other")
    }
  }

  private def _load_marker(
    path: Path
  )(using ExecutionContext): Option[String] = {
    if (!Files.exists(path)) {
      None
    } else {
      val datastore = SqlDataStore.sqlite(path.toString)
      datastore.load(
        DataStore.CollectionId("component_selection"),
        DataStore.StringEntryId("marker")
      ) match {
        case Consequence.Success(Some(record)) => record.getString("value")
        case Consequence.Success(None) => None
        case other => fail(s"unexpected load result: $other")
      }
    }
  }

  private def _params(
    values: (String, String)*
  ): ResolvedParameters =
    ResolvedParameters.fromResolvedConfiguration(
      _config(values*)
    )

  private def _config(
    values: (String, String)*
  ): ResolvedConfiguration =
      ResolvedConfiguration(
        Configuration(values.map { case (k, v) => k -> ConfigurationValue.StringValue(v) }.toMap),
        ConfigurationTrace.empty
      )

  private def _env(
    params: ResolvedParameters,
    config: ResolvedConfiguration
  ): ComponentDataStore.Environment =
    ComponentDataStore.Environment(params, Some(config))
}
