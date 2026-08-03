package org.goldenport.cncf.backend.collaborator

import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters._

import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CollaboratorRepositoryRuntimeProjectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-collaborator-repository-bootstrap-projection, example:E3, rules:GCF09J-C4, phase:55, slice:GCF-09J"
  )

  "Collaborator repository runtime projection" should {
    "E3 keep the raw compatibility overload outside typed runtime discovery" must _e1 {
      "when raw configuration A and an admitted Global binding B name different directories" in {
        Given("a compatibility-only raw configuration A and runtime binding B")
        val cwd = _work_directory("gcf09j-collaborator-runtime")
        try {
          val rawdirectory = cwd.resolve("raw-repository")
          val admitteddirectory = cwd.resolve("admitted-repository")
          val rawconfiguration = ResolvedConfiguration(
            Configuration(Map(
              CncfConfigurationParameterCatalog.COLLABORATOR_REPOSITORIES_KEY ->
                ConfigurationValue.StringValue(rawdirectory.toString)
            )),
            ConfigurationTrace.empty
          )
          val runtimeargs = Array(
            s"--textus.binding=${CncfConfigurationParameterCatalog.COLLABORATOR_REPOSITORIES_KEY}=$admitteddirectory",
            "command"
          )

          When("the raw compatibility factory and the production runtime construction seam are each invoked")
          val compatibilityfactory = CollaboratorFactory.create(rawconfiguration)
          val bootstrap = CncfRuntime.bootstrap(cwd, runtimeargs)
          val runtimefactory = CncfRuntime.collaboratorFactory(bootstrap)

          Then("the actual runtime seam gives discovery only B, while raw configuration remains isolated")
          compatibilityfactory._repository_directories shouldBe Vector(rawdirectory.normalize)
          runtimefactory._repository_directories shouldBe Vector(admitteddirectory.normalize)
          runtimefactory.entries shouldBe empty
        } finally {
          _delete_tree(cwd)
        }
      }
    }
  }

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
