package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationParameterCatalogSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-parameter-catalog, example:E1, rules:GCF07-C1,C2,C3, phase:55, slice:GCF-07A"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-parameter-catalog, example:E2, rules:GCF07-C3,C4, phase:55, slice:GCF-07A"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-cncf-parameter-catalog, example:E3, rules:GCF07-C2,C4, phase:55, slice:GCF-07A"
  )
  private val _e4 = afterWord(
    "in spec:phase-55-cncf-parameter-catalog, example:E4, rules:GCF07-C3,C4, phase:55, slice:GCF-07A"
  )
  private val _e5 = afterWord(
    "in spec:phase-55-cncf-parameter-catalog, example:E5, rules:GCF09D-C1,C2, phase:55, slice:GCF-09D"
  )
  private val _e6 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E4, rules:GCF09H-C1,C2,C3, phase:55, slice:GCF-09H"
  )
  private val _e7 = afterWord(
    "in spec:phase-55-startup-import-configuration, example:E1, rules:GCF09I-C1,C2,C3, phase:55, slice:GCF-09I"
  )
  private val _e8 = afterWord(
    "in spec:phase-55-collaborator-repository-bootstrap-projection, example:E1, rules:GCF09J-C1,C2, phase:55, slice:GCF-09J"
  )
  private val _e9 = afterWord(
    "in spec:phase-55-runtime-operation-web-authorization-policy, example:E1, rules:GCF09L-C1,C2,C3, phase:55, slice:GCF-09L"
  )
  private val _e10 = afterWord(
    "in spec:phase-55-runtime-execution-profile-configuration, example:E1, rules:GCF09M-C1,C2,C3, phase:55, slice:GCF-09M"
  )
  private val _e11 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E1, rules:GCF09N-C1,C2, phase:55, slice:GCF-09N"
  )

  "CNCF configuration parameter catalog" should {
    "register authoritative Subsystem-scoped catalog witnesses" which {
      "E1 expose the canonical identity and strict Phase 53 enum codec" must _e1 {
        "when the closed catalog is inspected" in {
          Given("the closed GCF-07A catalog and the Phase 53 Subsystem user-mode witness")

          When("the canonical definition and its codec are resolved")
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(SubsystemUserMode.CONFIGURATION_KEY))
          val parameter = CncfConfigurationParameterCatalog.subsystemUserMode

          Then("the canonical spelling and exact witness remain coupled")
          CncfConfigurationParameterCatalog.closed.definitions.size shouldBe 52
          definition.canonicalSpelling shouldBe SubsystemUserMode.CONFIGURATION_KEY
          definition.decodeOnlyAliases shouldBe Vector(
            "textus.runtime.subsystem.user-mode",
            "cncf.subsystem.user-mode",
            "cncf.runtime.subsystem.user-mode"
          )
          definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
          parameter.codec.decode(ConfigurationValue.StringValue("standalone")).toOption shouldBe Some(SubsystemUserMode.Standalone)
          parameter.codec.decode(ConfigurationValue.StringValue("multi-user")).toOption shouldBe Some(SubsystemUserMode.MultiUser)
          parameter.codec.decode(ConfigurationValue.StringValue("multi_user")).isSuccess shouldBe false
        }
      }

      "E2 preserve aliases as original provenance while resolving the canonical witness" must _e2 {
        "when every decode-only alias is supplied for one Subsystem target" in {
          Given("one canonical spelling and each frozen decode-only alias")
          val spellings = Vector(
            SubsystemUserMode.CONFIGURATION_KEY,
            "textus.runtime.subsystem.user-mode",
            "cncf.subsystem.user-mode",
            "cncf.runtime.subsystem.user-mode"
          )
          val target = _subsystem_target

          When("each one-field document is decoded through the closed catalog")
          val candidates = spellings.map { spelling =>
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, "standalone", target, spelling))))
          }

          Then("all aliases retain their input spelling but share the exact parameter witness")
          candidates.zip(spellings).foreach { case (candidate, spelling) =>
            candidate.bindings.size shouldBe 1
            (candidate.bindings.head.parameter.asInstanceOf[AnyRef] eq CncfConfigurationParameterCatalog.subsystemUserMode.asInstanceOf[AnyRef]) shouldBe true
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
            candidate.bindings.head.provenance.inputPath shouldBe Some(s"config.$spelling")
          }
        }
      }

      "E5 admit service-container canonical and alias witnesses only for Subsystems" must _e5 {
        "when driver and executable spellings are decoded" in {
          Given("both service-container parameter families and one Subsystem target")
          val target = _subsystem_target
          val spellings = Vector(
            CncfConfigurationParameterCatalog.SERVICE_CONTAINER_DRIVER_KEY,
            "textus.runtime.service-container.driver",
            "cncf.service-container.driver",
            "cncf.runtime.service-container.driver",
            CncfConfigurationParameterCatalog.SERVICE_CONTAINER_DOCKER_EXECUTABLE_KEY,
            "textus.runtime.service-container.docker.executable",
            "cncf.service-container.docker.executable",
            "cncf.runtime.service-container.docker.executable"
          )
          val parameters = Vector.fill(4)(CncfConfigurationParameterCatalog.serviceContainerDriver) ++
            Vector.fill(4)(CncfConfigurationParameterCatalog.serviceContainerDockerExecutable)

          When("each spelling is decoded through the closed catalog")
          val candidates = spellings.map { spelling =>
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, "docker", target, spelling))))
          }
          val global = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(CncfConfigurationParameterCatalog.SERVICE_CONTAINER_DRIVER_KEY, "docker", CncfConfigurationTarget.Global, "global")))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.SERVICE_CONTAINER_DRIVER_KEY, "docker", target, "canonical", "service-container"),
            _batch("cncf.service-container.driver", "docker", target, "alias", "service-container")
          ))

          Then("aliases retain provenance and their exact parameter witness, Global is rejected, and canonical collisions fail")
          candidates.zip(spellings.zip(parameters)).foreach { case (candidate, (spelling, parameter)) =>
            candidate.bindings.size shouldBe 1
            (candidate.bindings.head.parameter.asInstanceOf[AnyRef] eq parameter.asInstanceOf[AnyRef]) shouldBe true
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
            candidate.bindings.head.provenance.inputPath shouldBe Some(s"config.$spelling")
          }
          global.isSuccess shouldBe false
          collision.isSuccess shouldBe false
        }
      }

      "E6 admit the alias-free bounded SystemNode shutdown witness only for Subsystems" must _e6 {
        "when canonical, alias, target, and numeric-bound forms are decoded" in {
          Given("the closed catalog's canonical SystemNode timeout parameter and every disallowed target kind")
          val key = CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY
          val target = _subsystem_target

          When("its exact definition and candidate inputs are resolved")
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(key))
          val parameter = CncfConfigurationParameterCatalog.systemNodeShutdownDrainTimeoutMillis
          val canonical = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "120", target, "canonical")))
          val alias = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch("textus.runtime.system-node.shutdown.drain-timeout-millis", "120", target, "alias")))
          val global = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "120", CncfConfigurationTarget.Global, "global")))
          val componentclass = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "120", _component_class_target, "component-class")))
          val componentinstance = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "120", _component_instance_target, "component-instance")))

          Then("only the canonical Subsystem witness accepts an integral bounded value")
          definition.canonicalSpelling shouldBe key
          definition.decodeOnlyAliases shouldBe Vector.empty
          definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
          canonical.toOption.map(_.bindings.head.parameter.asInstanceOf[AnyRef] eq parameter.asInstanceOf[AnyRef]) shouldBe Some(true)
          alias.isSuccess shouldBe false
          global.isSuccess shouldBe false
          componentclass.isSuccess shouldBe false
          componentinstance.isSuccess shouldBe false
          parameter.codec.decode(ConfigurationValue.StringValue("1")).toOption shouldBe Some(1L)
          parameter.codec.decode(ConfigurationValue.NumberValue(BigDecimal(300000L))).toOption shouldBe Some(300000L)
          parameter.codec.decode(ConfigurationValue.StringValue("0")).isSuccess shouldBe false
          parameter.codec.decode(ConfigurationValue.StringValue("300001")).isSuccess shouldBe false
          parameter.codec.decode(ConfigurationValue.StringValue("three-seconds")).isSuccess shouldBe false
          parameter.codec.decode(ConfigurationValue.NumberValue(BigDecimal("120.5"))).isSuccess shouldBe false
        }
      }

      "E1 admit startup-import canonical and alias spellings only for Subsystems" must _e7 {
        "when both source families and every target kind are decoded" in {
          Given("the data and entity canonical keys, their frozen aliases, and one Subsystem target")
          val target = _subsystem_target
          val dataspellings = Vector(
            CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY,
            "textus.runtime.import.data.file",
            "cncf.import.data.file",
            "cncf.runtime.import.data.file"
          )
          val entityspellings = Vector(
            CncfConfigurationParameterCatalog.STARTUP_IMPORT_ENTITY_FILE_KEY,
            "textus.runtime.import.entity.file",
            "cncf.import.entity.file",
            "cncf.runtime.import.entity.file"
          )

          When("each spelling is decoded and invalid scopes, collisions, and non-strings are supplied")
          val datadefinition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY))
          val entitydefinition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(CncfConfigurationParameterCatalog.STARTUP_IMPORT_ENTITY_FILE_KEY))
          val data = dataspellings.map(spelling => _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, " data.yaml ", target, spelling)))))
          val entity = entityspellings.map(spelling => _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, " entity.yaml ", target, spelling)))))
          val global = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY, "data.yaml", CncfConfigurationTarget.Global, "global")))
          val componentclass = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY, "data.yaml", _component_class_target, "component-class")))
          val componentinstance = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY, "data.yaml", _component_instance_target, "component-instance")))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY, "data.yaml", target, "canonical", "startup-import"),
            _batch("cncf.import.data.file", "data.yaml", target, "alias", "startup-import")
          ))

          Then("aliases preserve their spelling, but one typed Subsystem witness is the only accepted authority")
          datadefinition.decodeOnlyAliases shouldBe dataspellings.tail
          entitydefinition.decodeOnlyAliases shouldBe entityspellings.tail
          datadefinition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
          entitydefinition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
          (CncfConfigurationParameterCatalog.startupImportDataFile.codec.asInstanceOf[AnyRef] eq ConfigurationValueCodec.string.asInstanceOf[AnyRef]) shouldBe true
          (CncfConfigurationParameterCatalog.startupImportEntityFile.codec.asInstanceOf[AnyRef] eq ConfigurationValueCodec.string.asInstanceOf[AnyRef]) shouldBe true
          data.zip(dataspellings).foreach { case (candidate, spelling) =>
            (candidate.bindings.head.parameter.asInstanceOf[AnyRef] eq CncfConfigurationParameterCatalog.startupImportDataFile.asInstanceOf[AnyRef]) shouldBe true
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
          }
          entity.zip(entityspellings).foreach { case (candidate, spelling) =>
            (candidate.bindings.head.parameter.asInstanceOf[AnyRef] eq CncfConfigurationParameterCatalog.startupImportEntityFile.asInstanceOf[AnyRef]) shouldBe true
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
          }
          global.isSuccess shouldBe false
          componentclass.isSuccess shouldBe false
          componentinstance.isSuccess shouldBe false
          collision.isSuccess shouldBe false
          CncfConfigurationParameterCatalog.startupImportDataFile.codec.decode(ConfigurationValue.BooleanValue(true)).isSuccess shouldBe false
        }
      }

      "E1 admit collaborator repository canonical and alias spellings only for Global bootstrap" must _e8 {
        "when comma-separated repository paths and invalid targets are decoded" in {
          Given("the canonical collaborator repository key, its established aliases, and a Global target")
          val key = CncfConfigurationParameterCatalog.COLLABORATOR_REPOSITORIES_KEY
          val spellings = Vector(
            key,
            "textus.runtime.collaborator.repositories",
            "cncf.collaborator.repositories",
            "cncf.runtime.collaborator.repositories"
          )
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(key))
          val candidates = spellings.map(spelling =>
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, " first path , second  path ", CncfConfigurationTarget.Global, spelling))))
          )
          val subsystem = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "repository", _subsystem_target, "subsystem")))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(key, "one", CncfConfigurationTarget.Global, "canonical", "collaborator"),
            _batch("cncf.collaborator.repositories", "two", CncfConfigurationTarget.Global, "alias", "collaborator")
          ))

          When("the values are decoded through the closed Global-only catalog")
          val decoded = CncfConfigurationParameterCatalog.collaboratorRepositories.codec.decode(
            ConfigurationValue.StringValue(" first path , , second  path , path|with whitespace ")
          )

          Then("only comma delimiters split outer-trimmed paths while aliases retain their input spelling")
          definition.decodeOnlyAliases shouldBe spellings.tail
          definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.Global)
          decoded.toOption shouldBe Some(Vector("first path", "second  path", "path|with whitespace"))
          candidates.zip(spellings).foreach { case (candidate, spelling) =>
            (candidate.bindings.head.parameter.asInstanceOf[AnyRef] eq CncfConfigurationParameterCatalog.collaboratorRepositories.asInstanceOf[AnyRef]) shouldBe true
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
          }
          subsystem.isSuccess shouldBe false
          collision.isSuccess shouldBe false
          CncfConfigurationParameterCatalog.collaboratorRepositories.codec.decode(ConfigurationValue.BooleanValue(true)).isSuccess shouldBe false
        }
      }

      "E1 admit the operation and Web authorization witnesses only for Subsystems" must _e9 {
        "when every canonical family, alias, target, collision, and codec form is decoded" in {
          Given("the seven runtime operation-security parameters and their established aliases")
          val target = _subsystem_target
          val entries = Vector(
            CncfConfigurationParameterCatalog.OPERATION_MODE_KEY -> "prod",
            CncfConfigurationParameterCatalog.WEB_DEVELOP_ANONYMOUS_ADMIN_KEY -> "true",
            CncfConfigurationParameterCatalog.WEB_DEMO_ASSIST_ENABLED_KEY -> "false",
            CncfConfigurationParameterCatalog.WEB_PRODUCTION_ADMIN_ENABLED_KEY -> "true",
            CncfConfigurationParameterCatalog.WEB_PRODUCTION_ADMIN_SYSTEM_ROLES_KEY -> "system_admin operator",
            CncfConfigurationParameterCatalog.WEB_PRODUCTION_ADMIN_COMPONENT_ROLES_KEY -> "component_operator,system_admin",
            CncfConfigurationParameterCatalog.WEB_PRODUCTION_ADMIN_JOBS_ROLES_KEY -> "system_admin|audit_viewer"
          )

          When("the catalog resolves each value through canonical and alias spellings")
          val canonical = entries.map { case (key, value) =>
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, value, target, key))))
          }
          val aliases = entries.map { case (key, value) =>
            val alias = s"cncf.runtime.${key.stripPrefix("textus.")}"
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(alias, value, target, alias))))
          }
          val global = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.OPERATION_MODE_KEY, "production", CncfConfigurationTarget.Global, "global")
          ))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.OPERATION_MODE_KEY, "production", target, "canonical", "operation-security"),
            _batch("cncf.operation-mode", "production", target, "alias", "operation-security")
          ))

          Then("all values share exact Subsystem-only witnesses and reject ambiguous or malformed admission")
          entries.zip(canonical.zip(aliases)).foreach { case ((key, _), (canonicalcandidate, aliascandidate)) =>
            val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(key))
            definition.decodeOnlyAliases shouldBe Vector(
              s"textus.runtime.${key.stripPrefix("textus.")}",
              s"cncf.${key.stripPrefix("textus.")}",
              s"cncf.runtime.${key.stripPrefix("textus.")}"
            )
            definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
            canonicalcandidate.bindings.head.parameter.id.value shouldBe key
            aliascandidate.bindings.head.parameter.id.value shouldBe key
          }
          global.isSuccess shouldBe false
          collision.isSuccess shouldBe false
          CncfConfigurationParameterCatalog.operationMode.codec.decode(ConfigurationValue.StringValue("dev")).toOption shouldBe Some(OperationMode.Develop)
          CncfConfigurationParameterCatalog.operationMode.codec.decode(ConfigurationValue.StringValue("invalid")).isSuccess shouldBe false
          CncfConfigurationParameterCatalog.webDevelopAnonymousAdmin.codec.decode(ConfigurationValue.BooleanValue(true)).toOption shouldBe Some(true)
          CncfConfigurationParameterCatalog.webDevelopAnonymousAdmin.codec.decode(ConfigurationValue.StringValue("invalid")).isSuccess shouldBe false
        }
      }

      "E1 admit complete execution-profile witnesses with typed confidential values" must _e10 {
        "when canonical and decode-only alias spellings enter one Subsystem target" in {
          Given("the complete runtime execution-profile family, including seed and environment-value confidentiality")
          val key = CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY
          val alias = "cncf.runtime.execution.random.seed"
          val target = _subsystem_target

          When("canonical and alias candidates and representative codecs are decoded")
          val canonical = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(key, "secret", target, "canonical"))))
          val aliascandidate = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(alias, "secret", target, "alias"))))
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(key))
          val environmentdefinition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(CncfConfigurationParameterCatalog.EXECUTION_ENVIRONMENT_VALUES_KEY))

          Then("the closed Subsystem-only schema preserves aliases, validation, and secret marking")
          definition.decodeOnlyAliases shouldBe Vector(
            "textus.runtime.execution.random.seed",
            "cncf.execution.random.seed",
            alias
          )
          definition.isConfidential shouldBe true
          environmentdefinition.isConfidential shouldBe true
          definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
          canonical.bindings.head.parameter.id.value shouldBe key
          aliascandidate.bindings.head.parameter.id.value shouldBe key
          CncfConfigurationParameterCatalog.executionProfile.codec.decode(ConfigurationValue.StringValue("controlled")).isSuccess shouldBe true
          CncfConfigurationParameterCatalog.executionProfile.codec.decode(ConfigurationValue.StringValue("unsafe")).isSuccess shouldBe false
          CncfConfigurationParameterCatalog.executionLineSeparator.codec.decode(ConfigurationValue.StringValue("crlf")).toOption shouldBe Some("\r\n")
          CncfConfigurationParameterCatalog.executionMathContext.codec.decode(ConfigurationValue.StringValue("decimal128")).isSuccess shouldBe true
          CncfConfigurationParameterCatalog.executionEnvironmentValues.codec.decode(
            ConfigurationValue.ObjectValue(Map("TOKEN" -> ConfigurationValue.StringValue("secret")))
          ).toOption shouldBe Some(Map("TOKEN" -> "secret"))
        }
      }

      "E1 admit process-exit Boolean witnesses only for Global bootstrap" must _e11 {
        "when canonical and decode-only alias forms enter the closed catalog" in {
          Given("the paired process-exit keys, their established aliases, and the Global target")
          val keys = Vector(
            CncfConfigurationParameterCatalog.FORCE_EXIT_KEY,
            CncfConfigurationParameterCatalog.NO_EXIT_KEY
          )
          val entries = keys.flatMap { key =>
            Vector(key, s"textus.runtime.${key.stripPrefix("textus.")}", s"cncf.${key.stripPrefix("textus.")}", s"cncf.runtime.${key.stripPrefix("textus.")}")
              .map(_ -> key)
          }

          When("each spelling is decoded with structural-invalid alternatives")
          val candidates = entries.map { case (spelling, key) =>
            _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(spelling, "true", CncfConfigurationTarget.Global, spelling)))) -> (spelling, key)
          }
          val subsystem = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.FORCE_EXIT_KEY, "true", _subsystem_target, "subsystem")
          ))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.FORCE_EXIT_KEY, "true", CncfConfigurationTarget.Global, "canonical", "process-exit"),
            _batch("cncf.force-exit", "false", CncfConfigurationTarget.Global, "alias", "process-exit")
          ))

          Then("both witnesses keep aliases decode-only and reject malformed or wrong-target input")
          keys.foreach { key =>
            val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(key))
            definition.decodeOnlyAliases shouldBe Vector(
              s"textus.runtime.${key.stripPrefix("textus.")}",
              s"cncf.${key.stripPrefix("textus.")}",
              s"cncf.runtime.${key.stripPrefix("textus.")}"
            )
            definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.Global)
          }
          candidates.foreach { case (candidate, (spelling, key)) =>
            candidate.bindings.head.parameter.id.value shouldBe key
            candidate.bindings.head.provenance.inputSpelling shouldBe Some(spelling)
          }
          subsystem.isSuccess shouldBe false
          collision.isSuccess shouldBe false
          CncfConfigurationParameterCatalog.forceExit.codec.decode(ConfigurationValue.StringValue("invalid")).isSuccess shouldBe false
          CncfConfigurationParameterCatalog.noExit.codec.decode(ConfigurationValue.BooleanValue(true)).toOption shouldBe Some(true)
        }
      }
    }

    "reject every configuration form outside the closed target policy" which {
      "E3 reject unknown, obsolete Web, malformed, and non-Subsystem target inputs" must _e3 {
        "when they enter the catalog-owned decoding route" in {
          Given("unknown and obsolete spellings, malformed values, and every disallowed target kind")
          val target = _subsystem_target

          When("each invalid input is decoded")
          val unknown = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch("textus.unknown", "standalone", target, "unknown")))
          val obsolete = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch("textus.web.application-mode", "standalone", target, "obsolete")))
          val malformed = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(SubsystemUserMode.CONFIGURATION_KEY, "multi_user", target, "malformed")))
          val global = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(SubsystemUserMode.CONFIGURATION_KEY, "standalone", CncfConfigurationTarget.Global, "global")))
          val componentclass = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(SubsystemUserMode.CONFIGURATION_KEY, "standalone", _component_class_target, "component-class")))
          val componentinstance = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(_batch(SubsystemUserMode.CONFIGURATION_KEY, "standalone", _component_instance_target, "component-instance")))
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(SubsystemUserMode.CONFIGURATION_KEY))
          val duplicate = CncfConfigurationParameterCatalog.create(Vector(definition, definition))
          val bypass = definition.input(
            "textus.unknown",
            target,
            ConfigurationValue.StringValue("standalone"),
            "config.textus.unknown"
          )

          Then("each form fails structurally before resolution or catalog publication")
          unknown.isSuccess shouldBe false
          obsolete.isSuccess shouldBe false
          malformed.isSuccess shouldBe false
          global.isSuccess shouldBe false
          componentclass.isSuccess shouldBe false
          componentinstance.isSuccess shouldBe false
          duplicate.isSuccess shouldBe false
          bypass.isSuccess shouldBe false
        }
      }

      "E4 reject canonical-plus-alias collisions in one source target" must _e4 {
        "when one collision domain contains both spellings" in {
          Given("one Subsystem target, one collision domain, and canonical-plus-alias documents")
          val target = _subsystem_target
          val batches = Vector(
            _batch(SubsystemUserMode.CONFIGURATION_KEY, "standalone", target, "canonical", "home"),
            _batch("cncf.subsystem.user-mode", "standalone", target, "alias", "home")
          )

          When("the catalog creates canonical candidates")
          val result = CncfConfigurationCandidateDecoder.decodeCatalog(batches)

          Then("the duplicate canonical parameter and target fail before resolution")
          result.isSuccess shouldBe false
        }
      }
    }
  }

  private def _batch(
    spelling: String,
    value: String,
    target: CncfConfigurationTarget,
    source: String,
    collisiondomain: String = "catalog"
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      _location(target),
      _take(
        ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          "home",
          source,
          10,
          collisiondomain,
          () => Consequence.success(_document(target, spelling, value))
        )
      )
    )

  private def _location(target: CncfConfigurationTarget): CncfConfigurationDocumentLocation =
    target match {
      case CncfConfigurationTarget.Global => CncfConfigurationDocumentLocation.Consolidated
      case value: CncfConfigurationTarget.ComponentClass =>
        new CncfConfigurationDocumentLocation.ComponentClass(value)
      case value: CncfConfigurationTarget.SubsystemInstance =>
        new CncfConfigurationDocumentLocation.SubsystemInstance(value)
      case value: CncfConfigurationTarget.ComponentInstance =>
        new CncfConfigurationDocumentLocation.ComponentInstance(value)
    }

  private def _config(spelling: String, value: String): ConfigurationDocument =
    ConfigurationDocument.Object(
      Vector(ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value))))
    )

  private def _document(
    target: CncfConfigurationTarget,
    spelling: String,
    value: String
  ): ConfigurationDocument =
    target match {
      case CncfConfigurationTarget.Global =>
        ConfigurationDocument.Object(
          Vector(
            ConfigurationDocument.Field(
              "global",
              ConfigurationDocument.Object(
                Vector(ConfigurationDocument.Field("config", _config(spelling, value)))
              )
            )
          )
        )
      case _ => _config(spelling, value)
    }

  private def _subsystem_target: CncfConfigurationTarget.SubsystemInstance =
    _take(
      for {
        identity <- SubsystemInstanceId.create("platform", "default")
        target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
      } yield target
    )

  private def _component_class_target: CncfConfigurationTarget.ComponentClass =
    _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("Catalog")))

  private def _component_instance_target: CncfConfigurationTarget.ComponentInstance =
    _take(
      CncfConfigurationTarget.ComponentInstance.create(
        _take(SubsystemInstanceId.create("platform", "default")),
        ComponentInstanceId("Catalog", "default")
      )
    )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
