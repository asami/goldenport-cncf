package org.goldenport.cncf.config

import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters._

import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.configuration.{ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class RepositoryBootstrapPolicySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-repository-bootstrap-policy, example:E1, rules:GCF09A-R1,R2, phase:55, slice:GCF-09A"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-repository-bootstrap-policy, example:E2, rules:GCF09A-R1,R3, phase:55, slice:GCF-09A"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-repository-bootstrap-policy, example:E3, rules:GCF09A-R2,R4, phase:55, slice:GCF-09A"
  )
  private val _e4 = afterWord(
    "in spec:phase-55-repository-bootstrap-policy, example:E4, rules:GCF09F-C1,C2, phase:55, slice:GCF-09F"
  )
  private val _e5 = afterWord(
    "in spec:phase-55-collaborator-repository-bootstrap-projection, example:E2, rules:GCF09J-C2,C3,C4, phase:55, slice:GCF-09J"
  )

  "Repository bootstrap configuration policy" should {
    "resolve only Global typed repository inputs" which {
      "E1 preserve canonical values and decode-only alias provenance" must _e1 {
        "when a global bootstrap document contains repository and activation values" in {
          Given("Global repository settings in canonical and legacy input spellings")
          val candidates = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.REPOSITORY_DIR_KEY, "https://repo.invalid, /tmp/repository", "canonical"),
            _batch("cncf.component.dev.dir", "/tmp/component-dev", "legacy")
          )))

          When("the global typed collection is resolved into its value-only policy")
          val context = _take(CncfConfigurationResolutionContext.globalOnly)
          val bindings = _take(ConfigurationBindingResolver.resolve(candidates, context.generic))
          val policy = _take(RepositoryBootstrapPolicy.resolve(bindings))

          Then("repository consumers receive typed values while provenance retains the input alias")
          policy.repositoryDirs shouldBe Vector("https://repo.invalid", "/tmp/repository")
          policy.componentDevDirs shouldBe Vector("/tmp/component-dev")
          val binding = _take(bindings.binding(CncfConfigurationParameterCatalog.componentDevDir)).getOrElse(fail("component development binding missing"))
          binding.provenance.inputSpelling shouldBe Some("cncf.component.dev.dir")
        }
      }

      "E2 reject a repository setting at a Subsystem target" must _e2 {
        "when target admission is attempted outside the bootstrap boundary" in {
          Given("the Global-only component-directory parameter definition")
          val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(CncfConfigurationParameterCatalog.COMPONENT_DIR_KEY))
          val subsystem = _take(SubsystemInstanceId.default("repository-policy"))
          val target = _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem))

          When("a Subsystem-scoped document attempts to admit the bootstrap setting")
          val result = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            CncfConfigurationDocumentBatch(
              new CncfConfigurationDocumentLocation.SubsystemInstance(target),
              _admission(CncfConfigurationParameterCatalog.COMPONENT_DIR_KEY, "/tmp/component-dir", "subsystem")
            )
          ))

          Then("the setting fails before it can become a Subsystem authority")
          definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.Global)
          result.isSuccess shouldBe false
        }
      }

      "E3 apply an admitted argv binding before repository selection" must _e3 {
        "when runtime bootstrap receives a Global component development binding" in {
          Given("one canonical Global argument envelope and an otherwise empty runtime directory")
          val cwd = _work_directory("gcf09a-repository-bootstrap")
          try {
            val componentdir = Files.createDirectories(cwd.resolve("component-directory"))
            val archive = Files.createDirectories(cwd.resolve("target")).resolve("unrelated-0.1.0.car")
            Files.writeString(archive, "placeholder")
            val binding = s"--textus.binding=${CncfConfigurationParameterCatalog.COMPONENT_DIR_KEY}=${componentdir}"

            When("the runtime bootstraps its repository selection")
            val bootstrap = CncfRuntime.bootstrap(cwd, Array(binding, "command"))

            Then("the resolved typed value becomes the active repository before Subsystem construction")
            bootstrap.repositories.activeRepositories.toOption.get should contain (
              ComponentRepository.ComponentDirRepository.Specification(componentdir)
            )
            bootstrap.configuration.configuration.values.contains("textus.binding") shouldBe false
            bootstrap.configuration.configuration.values.contains(RuntimeConfig.componentFileKey) shouldBe false
          } finally {
            _delete_tree(cwd)
          }
        }
      }

      "E4 admit every legacy repository argv value before runtime consumers run" must _e4 {
        "when the bootstrap boundary receives all repository value families" in {
          Given("canonical legacy repository flags and one non-repository command argument")
          val args = Array(
            "--repository-dir=repository",
            "--cncf.repository.component.dev.dir=repository-development",
            "--component-dir=component-directory",
            "--component-dev-dir=component-development",
            "--component-car-dir=component-car-directory",
            "--component-file=component.car",
            "--subsystem-dev-dir=subsystem-development",
            "--subsystem-sar-dir=subsystem-sar",
            "command"
          )

          When("legacy values are admitted into the bootstrap policy")
          val (policy, residual) = RepositoryBootstrapPolicy.admitArguments(RepositoryBootstrapPolicy(), args)

          Then("each value is represented once in the policy and no value-bearing flag reaches runtime")
          policy.repositoryDirs shouldBe Vector("repository")
          policy.repositoryComponentDevDirs shouldBe Vector("repository-development")
          policy.componentDirs shouldBe Vector("component-directory")
          policy.componentDevDirs shouldBe Vector("component-development")
          policy.componentCarDirs shouldBe Vector("component-car-directory")
          policy.componentFiles shouldBe Vector("component.car")
          policy.subsystemDevDirs shouldBe Vector("subsystem-development")
          policy.subsystemSarDirs shouldBe Vector("subsystem-sar")
          residual.toVector shouldBe Vector("command")
        }

        "when repeatable repository flags use their established comma-separated form" in {
          Given("search and active development repository lists")
          val args = Array(
            "--textus.repository.component.dev.dir=search-one, search-two",
            "--textus.component.dev.dir=active-one,active-two",
            "server"
          )

          When("the bootstrap boundary admits the repository lists")
          val (policy, residual) = RepositoryBootstrapPolicy.admitArguments(RepositoryBootstrapPolicy(), args)

          Then("each list member retains its repository type")
          policy.repositoryComponentDevDirs shouldBe Vector("search-one", "search-two")
          policy.componentDevDirs shouldBe Vector("active-one", "active-two")
          residual.toVector shouldBe Vector("server")
        }
      }

      "E2 resolve collaborator paths only from the Global typed policy" must _e5 {
        "when values are blank, explicit, relative, or duplicated" in {
          Given("a bootstrap directory and Global collaborator repository values")
          val cwd = _work_directory("gcf09j-collaborator-bootstrap")
          try {
            val candidates = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
              _batch(CncfConfigurationParameterCatalog.COLLABORATOR_REPOSITORIES_KEY, " collaborators/a/../b , collaborators/b , collaborators/c ", "canonical")
            )))
            val bindings = _take(ConfigurationBindingResolver.resolve(
              candidates,
              _take(CncfConfigurationResolutionContext.globalOnly).generic
            ))
            val explicit = _take(RepositoryBootstrapPolicy.resolve(bindings)).copy(baseDirectory = cwd)
            val blank = RepositoryBootstrapPolicy(collaboratorRepositories = Vector(" ", "")).copy(baseDirectory = cwd)
            val nonexistent = RepositoryBootstrapPolicy(collaboratorRepositories = Vector("does-not-exist")).copy(baseDirectory = cwd)

            When("the typed policy projects paths for the runtime collaborator factory")
            val explicitpaths = explicit.collaboratorRepositoryPaths

            Then("relative paths normalize before deduplication, blanks use the default, and explicit absence does not revive it")
            explicitpaths shouldBe Vector(
              cwd.resolve("collaborators/b").normalize,
              cwd.resolve("collaborators/c").normalize
            )
            blank.collaboratorRepositoryPaths shouldBe Vector(cwd.resolve("collaborator.d").normalize)
            nonexistent.collaboratorRepositoryPaths shouldBe Vector(cwd.resolve("does-not-exist").normalize)
          } finally {
            _delete_tree(cwd)
          }
        }
      }

    }
  }

  private def _batch(
    spelling: String,
    value: String,
    source: String
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      CncfConfigurationDocumentLocation.Global,
      _admission(spelling, value, source)
    )

  private def _admission(
    spelling: String,
    value: String,
    source: String
  ): ConfigurationSourceAdmission[ConfigurationDocument] =
    _take(ConfigurationSourceAdmission.create(
      ConfigurationOrigin.Home,
      "home",
      source,
      10,
      source,
      () => Consequence.success(ConfigurationDocument.Object(Vector(
        ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value)))
      )))
    ))

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))

  private val _target_directory: Path =
    Files.createDirectories(Paths.get("target").toAbsolutePath.normalize)

  private def _work_directory(prefix: String): Path =
    Files.createTempDirectory(_target_directory, prefix)

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
