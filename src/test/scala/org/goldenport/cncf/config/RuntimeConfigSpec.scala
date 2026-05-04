package org.goldenport.cncf.config

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.blob.{BlobStoreConfig, BlobStoreFactory}
import org.goldenport.cncf.context.IdGenerationContext
import org.goldenport.cncf.log.LogBackend
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 *  version Apr. 28, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeConfigSpec extends AnyWordSpec with Matchers {
  "RuntimeConfig" should {
    "use develop operation mode and anonymous admin enabled by default" in {
      val config = RuntimeConfig.from(ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))

      config.operationMode shouldBe OperationMode.Develop
      config.webDevelopAnonymousAdmin shouldBe true
      config.webProductionAdminEnabled shouldBe false
      config.webProductionAdminSystemRoles shouldBe Vector("system_admin")
      config.webProductionAdminComponentRoles shouldBe Vector("component_operator", "system_admin")
      config.webProductionAdminJobsRoles shouldBe Vector("system_admin", "audit_viewer")
      config.idNamespace shouldBe IdGenerationContext.DefaultNamespace
    }

    "parse id namespace configuration and aliases" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RuntimeIdNamespaceMajorKey -> ConfigurationValue.StringValue("Customer-A"),
          RuntimeConfig.RuntimeIdNamespaceMinorKey -> ConfigurationValue.StringValue("Tokyo.01")
        )),
        ConfigurationTrace.empty
      )

      RuntimeConfig.getString(configuration, RuntimeConfig.IdNamespaceMajorKey) shouldBe Some("Customer-A")
      RuntimeConfig.getString(configuration, RuntimeConfig.IdNamespaceMinorKey) shouldBe Some("Tokyo.01")
      val config = RuntimeConfig.from(configuration)
      config.idNamespace shouldBe IdGenerationContext.IdNamespace("customer_a", "tokyo_01")
    }

    "reject invalid id namespace configuration deterministically" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.IdNamespaceMajorKey -> ConfigurationValue.StringValue("!!!"),
          RuntimeConfig.IdNamespaceMinorKey -> ConfigurationValue.StringValue("global")
        )),
        ConfigurationTrace.empty
      )

      an[IllegalArgumentException] should be thrownBy {
        RuntimeConfig.from(configuration)
      }
    }

    "parse production admin gate configuration and aliases" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RuntimeWebProductionAdminEnabledKey -> ConfigurationValue.StringValue("true"),
          RuntimeConfig.RuntimeWebProductionAdminSystemRolesKey -> ConfigurationValue.StringValue("system_admin,platform_admin"),
          RuntimeConfig.RuntimeWebProductionAdminComponentRolesKey -> ConfigurationValue.StringValue("component_operator system_admin"),
          RuntimeConfig.RuntimeWebProductionAdminJobsRolesKey -> ConfigurationValue.StringValue("audit_viewer|system_admin")
        )),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.webProductionAdminEnabled shouldBe true
      config.webProductionAdminSystemRoles shouldBe Vector("system_admin", "platform_admin")
      config.webProductionAdminComponentRoles shouldBe Vector("component_operator", "system_admin")
      config.webProductionAdminJobsRoles shouldBe Vector("audit_viewer", "system_admin")
    }

    "keep CSV parsing for execution history filters independent from admin role token parsing" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ExecutionHistoryFilterOperationContainsKey ->
            ConfigurationValue.StringValue("notice search|with pipe,admin jobs")
        )),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.executionHistoryConfig.filters.map(_.operationContains) shouldBe Vector(
        Some("notice search|with pipe"),
        Some("admin jobs")
      )
    }

    "parse operation mode independently from run mode" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        )),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.mode shouldBe RunMode.Server
      config.operationMode shouldBe OperationMode.Production
    }

    "parse BlobStore configuration and aliases" in {
      val root = java.nio.file.Files.createTempDirectory("cncf-runtime-blob-store")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RuntimeBlobStoreBackendKey -> ConfigurationValue.StringValue("local"),
          RuntimeConfig.RuntimeBlobStoreNameKey -> ConfigurationValue.StringValue("media-store"),
          RuntimeConfig.RuntimeBlobStoreContainerKey -> ConfigurationValue.StringValue("media"),
          RuntimeConfig.RuntimeBlobStoreLocalRootKey -> ConfigurationValue.StringValue(root.toString),
          RuntimeConfig.RuntimeBlobStorePublicBasePathKey -> ConfigurationValue.StringValue("/assets/blob"),
          RuntimeConfig.RuntimeBlobStoreProviderClassKey -> ConfigurationValue.StringValue("example.BlobProvider"),
          RuntimeConfig.RuntimeBlobMaxByteSizeKey -> ConfigurationValue.StringValue("12345")
        )),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.blobStoreConfig.backend shouldBe "local"
      config.blobStoreConfig.effectiveName shouldBe "media-store"
      config.blobStoreConfig.container shouldBe "media"
      config.blobStoreConfig.localRoot shouldBe Some(root)
      config.blobStoreConfig.publicBasePath shouldBe Some("/assets/blob")
      config.blobStoreConfig.providerClass shouldBe Some("example.BlobProvider")
      config.blobStoreConfig.maxByteSize shouldBe 12345L
    }

    "reject invalid BlobStore runtime configuration deterministically at factory boundary" in {
      BlobStoreFactory.create(BlobStoreConfig(backend = "s3")) shouldBe a[org.goldenport.Consequence.Failure[_]]
      BlobStoreFactory.create(BlobStoreConfig(backend = BlobStoreConfig.BackendLocal)) shouldBe a[org.goldenport.Consequence.Failure[_]]
      BlobStoreFactory.create(BlobStoreConfig(publicBasePath = Some("https://cdn.example.test/blob"))) shouldBe a[org.goldenport.Consequence.Failure[_]]
      BlobStoreFactory.create(BlobStoreConfig(maxByteSize = -1)) shouldBe a[org.goldenport.Consequence.Failure[_]]
      BlobStoreFactory.create(BlobStoreConfig(maxByteSizeParseError = Some("bad max size"))) shouldBe a[org.goldenport.Consequence.Failure[_]]

      val invalid = RuntimeConfig.from(ResolvedConfiguration(
        Configuration(Map(RuntimeConfig.BlobMaxByteSizeKey -> ConfigurationValue.StringValue("1.9"))),
        ConfigurationTrace.empty
      ))
      BlobStoreFactory.create(invalid.blobStoreConfig) shouldBe a[org.goldenport.Consequence.Failure[_]]
      val negative = RuntimeConfig.from(ResolvedConfiguration(
        Configuration(Map(RuntimeConfig.BlobMaxByteSizeKey -> ConfigurationValue.StringValue("-1"))),
        ConfigurationTrace.empty
      ))
      BlobStoreFactory.create(negative.blobStoreConfig) shouldBe a[org.goldenport.Consequence.Failure[_]]
    }

    "use the default Blob max byte size" in {
      val config = RuntimeConfig.from(ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))

      config.blobStoreConfig.maxByteSize shouldBe BlobStoreConfig.DefaultMaxByteSize
    }

    "parse CNCF Blob max byte size alias" in {
      val config = RuntimeConfig.from(ResolvedConfiguration(
        Configuration(Map("cncf.blob.max-byte-size" -> ConfigurationValue.StringValue("2048"))),
        ConfigurationTrace.empty
      ))

      config.blobStoreConfig.maxByteSize shouldBe 2048L
    }

    "honor operation mode aliases and runtime config aliases" in {
      OperationMode.from("prod") shouldBe Some(OperationMode.Production)
      OperationMode.from("dev") shouldBe Some(OperationMode.Develop)

      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.RuntimeOperationModeKey -> ConfigurationValue.StringValue("test"),
          RuntimeConfig.RuntimeWebDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        )),
        ConfigurationTrace.empty
      )

      RuntimeConfig.getString(configuration, RuntimeConfig.OperationModeKey) shouldBe Some("test")
      RuntimeConfig.getString(configuration, RuntimeConfig.WebDevelopAnonymousAdminKey) shouldBe Some("false")
      val config = RuntimeConfig.from(configuration)
      config.operationMode shouldBe OperationMode.Test
      config.webDevelopAnonymousAdmin shouldBe false
    }

    "limit develop anonymous admin support to develop and test operation modes" in {
      OperationMode.Production.allowsDevelopAnonymousAdmin shouldBe false
      OperationMode.Demo.allowsDevelopAnonymousAdmin shouldBe false
      OperationMode.Develop.allowsDevelopAnonymousAdmin shouldBe true
      OperationMode.Test.allowsDevelopAnonymousAdmin shouldBe true
    }

    "suppress console log backends during executable specs while preserving file logging" in {
      val serverConfiguration = ResolvedConfiguration(
        Configuration(Map(RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"))),
        ConfigurationTrace.empty
      )
      val stderrConfiguration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.LogBackendKey -> ConfigurationValue.StringValue("stderr")
        )),
        ConfigurationTrace.empty
      )
      val fileConfiguration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.ModeKey -> ConfigurationValue.StringValue("server"),
          RuntimeConfig.LogBackendKey -> ConfigurationValue.StringValue("file"),
          RuntimeConfig.LogFilePathKey -> ConfigurationValue.StringValue("/tmp/textus-runtime-test.log")
        )),
        ConfigurationTrace.empty
      )

      _with_system_property("textus.test", None) {
        RuntimeConfig.from(serverConfiguration).logBackend shouldBe LogBackend.StdoutBackend
      }
      _with_system_property("textus.test", Some("true")) {
        RuntimeConfig.from(serverConfiguration).logBackend shouldBe LogBackend.NopLogBackend
        RuntimeConfig.from(stderrConfiguration).logBackend shouldBe LogBackend.NopLogBackend
        RuntimeConfig.from(fileConfiguration).logBackend shouldBe LogBackend.FileLogBackend("/tmp/textus-runtime-test.log")
      }
    }
  }

  private def _with_system_property[A](
    key: String,
    value: Option[String]
  )(body: => A): A = {
    val previous = sys.props.get(key)
    value match {
      case Some(v) => System.setProperty(key, v)
      case None => System.clearProperty(key)
    }
    try {
      body
    } finally {
      previous match {
        case Some(v) => System.setProperty(key, v)
        case None => System.clearProperty(key)
      }
    }
  }
}
