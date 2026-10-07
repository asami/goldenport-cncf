package org.goldenport.cncf.config

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.subsystem.SystemNode
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Settings consumers must not acquire the runtime owner's physical stores.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 */
final class RuntimeSettingsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _resolved(entries: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(entries.map { case (key, value) =>
      key -> ConfigurationValue.StringValue(value)
    }.toMap), ConfigurationTrace.empty)

  "Runtime settings projection" should {
    "read ordinary runtime configuration without creating private pools before managed binding" in {
      Given("an absent SQLite destination and ordinary runtime configuration")
      val file = Files.createTempDirectory("runtime-config-managed-").resolve("runtime.sqlite")
      val configuration = _resolved(
        "textus.datastore.kind" -> "sqlite",
        "textus.datastore.path" -> file.toString)
      // Ordinary configuration callers need no settings-specific workaround.
      When("ordinary callers project two store views before managed runtime binding")
      val first = RuntimeConfig.from(configuration).dataStoreSpace
      val second = RuntimeConfig.from(configuration).dataStoreSpace
      Then("configuration projection alone creates no SQLite file")
      Files.exists(file) shouldBe false
      Given("one owning SystemNode, binding, lease and collection for both views")
      val node = SystemNode.create()
      val binding = node.bind()
      def take[A](result: Consequence[A]): A = result match {
        case Consequence.Success(value) => value
        case failure => fail(s"Managed configuration fixture failed: $failure")
      }
      val lease = take(binding.acquireLeaseC())
      val environment = ComponentDataStore.Environment(ExecutionContext.create().runtime.resolvedParameters,
        Some(configuration))
      val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "settings", "records"))
      try {
        When("both component views bind through that same owning lease")
        take(first.bindManagedApplicationDataStoreC(environment,
          ComponentDataStore.Request("org.example.First"), binding, lease, node.hmacKey))
        take(second.bindManagedApplicationDataStoreC(environment,
          ComponentDataStore.Request("org.example.Second"), binding, lease, node.hmacKey))
        val left = take(first.dataStore(collection)).asInstanceOf[SqlDataStore]
        val right = take(second.dataStore(collection)).asInstanceOf[SqlDataStore]
        Then("they share one open managed physical store and its SQLite file")
        left.managedResourceOption.get should be theSameInstanceAs right.managedResourceOption.get
        node.managedDataStoreCount shouldBe 1
        left.managedResourceOption.get.isOpen shouldBe true
        Files.exists(file) shouldBe true
        // Clearing the scoped view after the lease is not an unbound read.
        first.clearBoundDataStore()
        second.clearBoundDataStore()
      } finally {
        lease.release()
        binding.release()
        take(node.shutdownC())
      }
      Then("releasing the binding and shutting down the node leaves no managed stores")
      node.managedDataStoreCount shouldBe 0
    }

    "decode settings repeatedly without creating the configured SQLite file" in {
      Given("a valid SQLite destination which does not yet exist")
      val file = Files.createTempDirectory("runtime-settings-").resolve("runtime.sqlite")
      val configuration = _resolved(
        "textus.datastore.kind" -> "sqlite",
        "textus.datastore.path" -> file.toString,
        RuntimeConfig.runtimeModeKey -> "server",
        RuntimeConfig.runtimeOperationModeKey -> "test",
        "cncf.blob.max-byte-size" -> "2048",
        RuntimeConfig.RUNTIME_OPERATION_TOOL_POLICY_KEY -> "config/tools.yaml")
      When("settings-only consumers parse the same runtime declaration twice")
      val first = RuntimeConfig.settingsFrom(configuration)
      val second = RuntimeConfig.settingsFrom(configuration, Some(RunMode.Command))
      Then("aliases, overrides and component values are retained without creating storage")
      first.mode shouldBe RunMode.Server
      first.operationMode shouldBe OperationMode.Test
      first.blobStoreConfig.maxByteSize shouldBe 2048L
      first.operationToolPolicyPath.map(_.endsWith("config/tools.yaml")) shouldBe Some(true)
      second.mode shouldBe RunMode.Command
      Files.exists(file) shouldBe false
    }

    "retain the common settings validation before any SQLite resource acquisition" in {
      Given("an absent SQLite destination and an invalid preview-limit setting")
      val file = Files.createTempDirectory("runtime-settings-invalid-").resolve("runtime.sqlite")
      val configuration = _resolved(
        "textus.datastore.kind" -> "sqlite",
        "textus.datastore.path" -> file.toString,
        RuntimeConfig.WEB_RENDERER_PREVIEW_LIMIT_KEY -> "0")
      // Both settings-only and resource-owning entries share the same rejection.
      When("both settings-only and ordinary runtime entries decode that invalid configuration")
      val settingsFailure = intercept[IllegalArgumentException] {
        RuntimeConfig.settingsFrom(configuration)
      }
      val runtimeFailure = intercept[IllegalArgumentException] {
        RuntimeConfig.from(configuration)
      }
      Then("both entries reject the same setting before creating the SQLite file")
      settingsFailure.getMessage should include (RuntimeConfig.WEB_RENDERER_PREVIEW_LIMIT_KEY)
      runtimeFailure.getMessage shouldBe settingsFailure.getMessage
      Files.exists(file) shouldBe false
    }

    "preserve the existing production security validation for settings consumers" in {
      Given("production settings with debug authentication enabled")
      val configuration = _resolved(
        RuntimeConfig.operationModeKey -> "production",
        RuntimeConfig.DEBUG_AUTH_ENABLED_KEY -> "true")
      When("a settings-only consumer decodes the configuration")
      val thrown = intercept[IllegalArgumentException] {
        RuntimeConfig.settingsFrom(configuration)
      }
      Then("the production security check rejects debug authentication")
      thrown.getMessage should include (RuntimeConfig.DEBUG_AUTH_ENABLED_KEY)
    }
  }
}
