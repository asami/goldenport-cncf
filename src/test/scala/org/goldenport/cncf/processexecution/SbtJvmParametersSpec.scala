package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtJvmParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Runtime-owned SBT heap" should {
    "default to four GiB and preserve a larger project value across all typed operations" in {
      Given("a workspace with no JVM file and unchanged typed operation definitions")
      _with_root { root =>
        SbtOperation.values.foreach { operation =>
          val original = _execution(root, operation)
          When("the runtime resolves the default and subsequently reads larger project settings")
          Files.deleteIfExists(root.resolve(".jvmopts"))
          val defaulted = SbtJvmParameters.applyC(ResolvedParameters.empty(), original).toOption.get
          Files.writeString(root.resolve(".jvmopts"), "# -Xmx100G\n-Xms1G\n-Xmx8G # project\n-Xss6M\n")
          val enlarged = SbtJvmParameters.applyC(ResolvedParameters.empty(), original).toOption.get
          Then("the maximum changes while command, request, environment and lifecycle limits remain intact")
          defaulted.effectiveArguments.head shouldBe "-J-Xmx4294967296"
          enlarged.effectiveArguments.head shouldBe "-J-Xmx8589934592"
          enlarged.effectiveArguments.tail shouldBe original.effectiveArguments
          enlarged.request shouldBe original.request
          enlarged.effectiveLimits shouldBe original.effectiveLimits
          enlarged.definition._environment.values shouldBe original.definition._environment.values
          Files.readString(root.resolve(".jvmopts")) should include("-Xss6M")
        }
      }
    }

    "take the larger config or project heap without rewriting either source" in {
      Given("numeric ordinary config and project settings in MiB")
      _with_root { root =>
        val original = _execution(root, SbtOperation.Compile)
        val property = Prop.forAll(Gen.choose(4096, 16384), Gen.choose(1024, 16384)) { (configured, project) =>
          Files.writeString(root.resolve(".jvmopts"), s"-Xmx${project}m\n")
          When("the runtime resolves both sources")
          val result = SbtJvmParameters.applyC(_parameters(ConfigurationValue.NumberValue(BigDecimal(configured))), original)
          Then("neither an increase nor the four GiB floor is lost")
          result.toOption.exists(_.effectiveArguments.head == s"-J-Xmx${configured.max(project).toLong * 1048576L}")
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(25), property).passed shouldBe true
      }
    }

    "respect standard config and explicit environment while preventing a later small Xmx override" in {
      _with_root { root =>
        Given("a larger project heap with smaller shared and environment defaults")
        val shared = Files.writeString(root.resolve("shared.sbtopts"), "--mem 4096\n")
        Files.writeString(root.resolve(".jvmopts"), "-Xmx8g\n")
        val environment = Map("SBT_ETC_FILE" -> shared.toString, "SBT_OPTS" -> "-Xmx1g -Dkeep=value",
          "JAVA_TOOL_OPTIONS" -> "-Xmx2g", "JDK_JAVA_OPTIONS" -> "-Xmx3g")
        val original = _execution(root, SbtOperation.Test, environment)
        When("the runtime prepares the actual launcher input")
        val result = SbtJvmParameters.applyC(ResolvedParameters.empty(), original).toOption.get
        Then("later environment options agree with the selected heap and unrelated settings survive")
        result.definition._environment.values("SBT_OPTS") shouldBe "-Xmx8589934592 -Dkeep=value"
        result.definition._environment.values("JAVA_TOOL_OPTIONS") shouldBe "-Xmx8589934592"
        result.definition._environment.values("JDK_JAVA_OPTIONS") shouldBe "-Xmx8589934592"
        result.definition._environment.values("SBT_ETC_FILE") shouldBe shared.toString
        When("project sbtopts and then shared config request still larger heaps")
        Files.writeString(root.resolve(".sbtopts"), "--mem 12288\n")
        SbtJvmParameters.applyC(ResolvedParameters.empty(), original).toOption.get.effectiveArguments.head shouldBe "-J-Xmx12884901888"
        Files.writeString(shared, "-J-Xmx16G\n")
        SbtJvmParameters.applyC(ResolvedParameters.empty(), original).toOption.get.effectiveArguments.head shouldBe "-J-Xmx17179869184"
      }
    }

    "reject invalid numeric config as configuration failure before any execution" in {
      Given("explicit values which cannot express the Textus heap policy")
      Vector(ConfigurationValue.NumberValue(BigDecimal(1024)), ConfigurationValue.NumberValue(BigDecimal("4096.5")),
        ConfigurationValue.StringValue("8192"), ConfigurationValue.BooleanValue(true)).foreach { value =>
        When("the runtime resolves its ordinary property")
        val result = SbtJvmParameters.resolveC(_parameters(value))
        Then("it retains a configuration failure rather than silently using the default")
        result.isFaillure shouldBe true
        result.toString should include(SbtJvmParameters.maximumHeapMiBKey)
      }
    }

    "pass the selected heap through the local driver without changing the generic process route" in {
      _with_root { root =>
        Given("a runtime-selected SBT definition and a local launcher capturing the actual argument vector")
        var observed = Vector.empty[String]
        val launcher = new LocalProcessLauncher {
          def startBlocking(command: Vector[String]): Process = throw new IllegalStateException("cwd required")
          override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
            observed = command
            new ProcessBuilder("/usr/bin/true").start()
          }
        }
        val driver = new LocalProcessExecutionDriver("heap-spec", launcher)
        try {
          Files.writeString(root.resolve(".jvmopts"), "-Xmx8g\n")
          val execution = SbtJvmParameters.applyC(ResolvedParameters.empty(), _execution(root, SbtOperation.Compile)).toOption.get
          When("the owning driver starts and awaits the process")
          val result = driver.startC(execution).flatMap(_.awaitC).toOption.get
          Then("the launcher receives eight GiB and the ordinary compile command with a real terminal result")
          observed(1) shouldBe "-J-Xmx8589934592"
          observed.last shouldBe "compile"
          result.termination shouldBe ProcessExecutionTermination.Exited(0)
          driver.activeProcessCount shouldBe 0
        } finally driver.close()
      }
    }

    "preserve the selected heap through the installed SBT shell parser" in {
      val executable = sys.props.get("cncf.spec.sbt-launcher").map(Path.of(_))
        .getOrElse(cancel("Select cncf.spec.sbt-launcher to inspect a real SBT distribution with a fake JVM"))
      _with_root { root =>
        Given("the selected real SBT launcher and a fake Java executable which only records arguments")
        val jdk = Files.createDirectories(root.resolve("jdk/bin"))
        val fakejava = Files.writeString(jdk.resolve("java"), """#!/bin/sh
case " $* " in
  *" -version "*) echo 'openjdk version "17.0.1"' >&2; exit 0;;
  *" --rt-ext-dir "*) echo "$FAKE_RT"; exit 0;;
esac
printf '%s\n' "$@" > "$CAPTURE"
exit 0
""")
        fakejava.toFile.setExecutable(true) shouldBe true
        val rt = Files.createDirectory(root.resolve("java9-rt-ext-fixture"))
        Files.createFile(rt.resolve("rt.jar"))
        Files.createDirectories(root.resolve("project"))
        Files.writeString(root.resolve("project/build.properties"), "sbt.version=1.9.7\n")
        Files.writeString(root.resolve("build.sbt"), "")
        val shared = Files.writeString(root.resolve("shared.sbtopts"), "--mem 1024\n")
        val capture = root.resolve("java-arguments.txt")
        val environment = Map("HOME" -> root.toString, "JAVA_HOME" -> jdk.getParent.toString,
          "PATH" -> s"$jdk:/usr/bin:/bin", "FAKE_RT" -> rt.toString, "CAPTURE" -> capture.toString,
          "SBT_ETC_FILE" -> shared.toString, "SBT_OPTS" -> "-Xmx1g -Dkept=yes")
        val driver = new LocalProcessExecutionDriver()
        try {
          Vector((None, 4096, 4294967296L), (Some("-Xmx8G\n-Xss6M\n"), 4096, 8589934592L),
            (Some("-Xmx8G\n"), 12288, 12884901888L), (Some("-Xmx1G\n"), 4096, 4294967296L)).foreach {
            case (options, configured, expected) =>
              Files.deleteIfExists(root.resolve(".jvmopts"))
              Files.deleteIfExists(capture)
              options.foreach(Files.writeString(root.resolve(".jvmopts"), _))
              When("the runtime drives the normal launcher using the selected heap")
              val original = _execution(root, SbtOperation.Compile, environment, executable)
              val execution = SbtJvmParameters.applyC(_parameters(ConfigurationValue.NumberValue(BigDecimal(configured))), original).toOption.get
              val result = driver.startC(execution).flatMap(_.awaitC).toOption.get
              Then("the final Java argv retains the effective maximum despite the smaller shared and environment defaults")
              withClue(new String(result.stderr.content.toArray, java.nio.charset.StandardCharsets.UTF_8)) {
                result.termination shouldBe ProcessExecutionTermination.Exited(0)
                val arguments = Files.readString(capture).linesIterator.toVector
                arguments.filter(_.startsWith("-Xmx")).last shouldBe s"-Xmx$expected"
                arguments should contain("-Dkept=yes")
                arguments should contain("-jar")
              }
          }
        } finally driver.close()
      }
    }
  }

  private def _parameters(value: ConfigurationValue): ResolvedParameters =
    ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(
      Configuration(Map(SbtJvmParameters.maximumHeapMiBKey -> value)), ConfigurationTrace.empty))

  private def _execution(root: Path, operation: SbtOperation, environment: Map[String, String] = Map.empty,
    executable: Path = Path.of("/usr/bin/true")): ResolvedProcessExecution = {
    val binding = WorkspaceBinding.createC(WorkspaceId.parseC("project").toOption.get, root).toOption.get
    val capability = ProcessCapabilityId.parseC("project-build").toOption.get
    val limits = ProcessExecutionLimits(Some(5000L), Some(5000L), Some(100L), Some(65536L), Some(65536L),
      Some(65536L), Some(16L), Some(65536L), Some(16L), Some(65536L), Some(65536L))
    val definition = SbtProgramDefinition.createC(binding, operation, capability, "project-build", executable,
      limits, environment, if (operation == SbtOperation.TestOnly) Vector("org.example.SelectedSpec") else Vector.empty).toOption.get
    ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      .resolveSbtC(definition, ProcessExecutionGrant(capability)).toOption.get
  }

  private def _with_root(body: Path => Unit): Unit = {
    val parent = Files.createDirectories(Path.of("target", "sbt-jvm-parameters-spec").toAbsolutePath)
    val root = Files.createTempDirectory(parent, "case-")
    val cleanup = new ProcessExecutionWorkArea(root)
    try body(root) finally cleanup.close()
  }
}
