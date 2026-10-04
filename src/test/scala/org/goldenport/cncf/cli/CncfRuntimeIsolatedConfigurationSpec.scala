package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.config.{CncfConfigurationArgumentBindingCodec, CncfConfigurationParameterCatalog, RuntimeConfig}
import org.goldenport.configuration.source.ConfigurationSource
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeIsolatedConfigurationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-runtime-owner, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1B")

  "IC1 Isolated native source provenance and CLI precedence" must _scenario("IC1", "R18") {
    "retain one argument source while CLI wins and isolated local data overrides every supplied root" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R18 IC1: an isolated home, canonical provider binding, and conflicting CNCF/Textus files and CLI values")
      _with_workspace { cwd =>
        val userhome = sys.props.get("user.home")
        val home = cwd.resolve("home")
        val compatibility = cwd.resolve("cncf.yaml")
        val primary = cwd.resolve("textus.yaml")
        Files.writeString(compatibility, s"textus.web.descriptor: compatibility\ntextus.local-data.root: ${cwd.resolve("compatibility-data")}\n", StandardCharsets.UTF_8)
        Files.writeString(primary, s"textus.web.descriptor: primary\ntextus.local-data.root: ${cwd.resolve("primary-data")}\n", StandardCharsets.UTF_8)
        val args = _arguments(home, false) ++ Array(s"--cncf.config.file=$compatibility", s"--textus.config.file=$primary",
          "--textus.web.descriptor=cli", s"--textus.local-data.root=${cwd.resolve("cli-data")}", "command")
        When("the actual runtime bootstrap resolves and projects the isolated configuration")
        val result = CncfRuntime.bootstrapC(cwd, args, Map.empty)
        Then("bootstrap succeeds with one Args and Env, exact typed provider, CLI web precedence, isolated local data, and unchanged user.home")
        result.isSuccess shouldBe true
        val bootstrap = result.toOption.get
        val sources = bootstrap.configurationSnapshot.get.sources
        sources.count(_.source.isInstanceOf[ConfigurationSource.Args]) shouldBe 1
        sources.count(_.source.isInstanceOf[ConfigurationSource.Env]) shouldBe 1
        RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("cli")
        RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(home.resolve(".cncf").toString)
        bootstrap.configurationArgumentBindings.size shouldBe 1
        val codec = CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed).toOption.get
        codec.encode(bootstrap.configurationArgumentBindings.head).toOption.get shouldBe _binding
        sys.props.get("user.home") shouldBe userhome
      }
    }
  }

  "IC2 Inherited local data and primary explicit file" must _scenario("IC2", "R18") {
    "retain the primary Textus web and root values when local data inheritance is explicit" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R18 IC2: isolated runtime/repositories, inherited local data, canonical binding, and two distinct explicit files")
      _with_workspace { cwd =>
        val userhome = sys.props.get("user.home")
        val home = cwd.resolve("home")
        val compatibility = cwd.resolve("cncf.yaml")
        val primary = cwd.resolve("textus.yaml")
        val primaryroot = cwd.resolve("primary-data")
        Files.writeString(compatibility, s"textus.web.descriptor: compatibility\ntextus.local-data.root: ${cwd.resolve("compatibility-data")}\n", StandardCharsets.UTF_8)
        Files.writeString(primary, s"textus.web.descriptor: primary\ntextus.local-data.root: $primaryroot\n", StandardCharsets.UTF_8)
        val args = _arguments(home, true) ++ Array(s"--cncf.config.file=$compatibility", s"--textus.config.file=$primary", "command")
        When("the actual bootstrap projects both explicit files without a direct web or root override")
        val result = CncfRuntime.bootstrapC(cwd, args, Map.empty)
        Then("one Args and Env retain the exact provider, Textus file wins, inherited root stays unredirected, and user.home is unchanged")
        result.isSuccess shouldBe true
        val bootstrap = result.toOption.get
        val sources = bootstrap.configurationSnapshot.get.sources
        sources.count(_.source.isInstanceOf[ConfigurationSource.Args]) shouldBe 1
        sources.count(_.source.isInstanceOf[ConfigurationSource.Env]) shouldBe 1
        RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("primary")
        RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(primaryroot.toString)
        primaryroot should not be home.resolve(".cncf")
        bootstrap.configurationArgumentBindings.size shouldBe 1
        val codec = CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed).toOption.get
        codec.encode(bootstrap.configurationArgumentBindings.head).toOption.get shouldBe _binding
        sys.props.get("user.home") shouldBe userhome
      }
    }
  }

  "IC3 Shrinking native descriptor and CLI precedence" must _scenario("IC3", "R18,R19") {
    "preserve source provenance and isolated paths across bounded generated descriptor and file values" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R18,R19 IC3: bounded nonempty ASCII letters with seed 69709702 and normal shrinking")
      val tokens = Gen.choose(1, 8).flatMap(size => Gen.listOfN(size, Gen.alphaChar).map(_.mkString))
      val property = Prop.forAll(tokens) { token =>
        _with_workspace { cwd =>
          Given("docs/spec/durable-job-runtime-owner-contract.md R18,R19 IC3 generated case: unique owned config, test descriptor, isolated home, and canonical binding")
          val userhome = sys.props.get("user.home")
          val home = cwd.resolve("home")
          val file = cwd.resolve("config.yaml")
          val descriptor = cwd.resolve("test.yaml")
          Files.writeString(file, s"textus.web.descriptor: file_$token\n", StandardCharsets.UTF_8)
          Files.writeString(descriptor, s"""kind: test-descriptor
            |home:
            |  mode: isolated
            |  path: $home
            |  inherit:
            |    runtime: false
            |    repositories: false
            |    credentials: false
            |    local-data: false
            |config:
            |  textus.web.descriptor: descriptor_$token
            |""".stripMargin, StandardCharsets.UTF_8)
          val args = Array(_binding, "--no-default-components", s"--textus.config.file=$file",
            "--textus.test.descriptor=./test.yaml", s"--textus.web.descriptor=cli_$token", "command")
          When("the generated case crosses actual native bootstrap and typed projection with an empty environment")
          val result = CncfRuntime.bootstrapC(cwd, args, Map.empty)
          Then("one Args and Env retain CLI precedence, normalized descriptor, isolated root, exact provider binding, and unchanged user.home")
          result.isSuccess shouldBe true
          val bootstrap = result.toOption.get
          val sources = bootstrap.configurationSnapshot.get.sources
          sources.count(_.source.isInstanceOf[ConfigurationSource.Args]) shouldBe 1
          sources.count(_.source.isInstanceOf[ConfigurationSource.Env]) shouldBe 1
          RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some(s"cli_$token")
          RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY) shouldBe Some(descriptor.toString)
          RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_PATH_KEY) shouldBe Some(home.toString)
          RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(home.resolve(".cncf").toString)
          bootstrap.configurationArgumentBindings.size shouldBe 1
          val codec = CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed).toOption.get
          codec.encode(bootstrap.configurationArgumentBindings.head).toOption.get shouldBe _binding
          sys.props.get("user.home") shouldBe userhome
          true
        }
      }
      When("ScalaCheck executes the ordinary shrinking property with at least 24 successful examples")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709702L)), property)
      Then("every generated actual bootstrap preserves the isolated configuration contract")
      result.passed shouldBe true
      result.succeeded should be >= 24
    }
  }

  private val _binding = "--textus.binding=@s/platform/default:textus.job.durable.provider=fixture_provider"

  private def _arguments(home: Path, inheritlocaldata: Boolean): Array[String] =
    Array(_binding, "--no-default-components", s"--textus.test.home.path=$home",
      "--textus.test.home.inherit.runtime=false", "--textus.test.home.inherit.repositories=false",
      s"--textus.test.home.inherit.local-data=$inheritlocaldata")

  private def _with_workspace[A](body: Path => A): A = {
    val work = Paths.get("target/configured-durable-runtime/work").toAbsolutePath.normalize
    Files.createDirectories(work)
    val cwd = Files.createTempDirectory(work, "case-cli-").toAbsolutePath.normalize
    try body(cwd) finally _remove(cwd)
  }

  private def _remove(path: Path): Unit = {
    val stream = Files.walk(path)
    try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
    finally stream.close()
  }
}
