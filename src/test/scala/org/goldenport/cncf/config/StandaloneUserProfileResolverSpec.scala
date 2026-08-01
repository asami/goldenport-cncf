package org.goldenport.cncf.config

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.SubsystemExecutionProfile
import org.goldenport.configuration.ConfigurationOrigin
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class StandaloneUserProfileResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "StandaloneUserProfileResolver" should {
    "admit only the exact HOME documents in Textus then CNCF order" in {
      _with_home { home =>
        Given("both standalone HOME profile documents")
        val textus = home.resolve(".textus/user-profile.yaml")
        val cncf = home.resolve(".cncf/user-profile.yaml")
        _write(textus, _profile("textus-user"))
        _write(cncf, _profile("cncf-user"))
        _write_sentinels(home)
        val reader = new RecordingReader
        val before = _snapshot(home)

        val nonnormalizedhome = home.resolve("normalization-probe").resolve("..")
        val lookup = new StaticHomeLookup(nonnormalizedhome)

        When("fixed current-user evidence resolves profile layers from a non-normalized HOME spelling")
        val result = StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.Fixed, lookup, reader)

        Then("the two HOME paths are the complete ordered admission set")
        result shouldBe a[Consequence.Success[_]]
        result.toOption.get.map(_.layer) shouldBe Vector(
          StandaloneUserProfileResolver.Layer.TextusHome,
          StandaloneUserProfileResolver.Layer.CncfHome
        )
        result.toOption.get.map(_.path) shouldBe Vector(textus, cncf)
        result.toOption.get.foreach(_.origin shouldBe ConfigurationOrigin.Home)
        reader.paths.toVector shouldBe Vector(textus, cncf)
        _snapshot(home) shouldBe before
      }
    }

    "leave profile and unrelated CNCF state unchanged when fixed admission fails closed" in {
      _with_home { home =>
        Given("a malformed Textus profile plus CNCF and project/CWD sentinel state")
        val textus = home.resolve(".textus/user-profile.yaml")
        val cncf = home.resolve(".cncf/user-profile.yaml")
        _write(textus, "apiVersion: textus/v1\nkind: WrongProfile\n")
        _write(cncf, _profile("cncf-user"))
        _write_sentinels(home)
        val reader = new RecordingReader
        val before = _snapshot(home)

        When("fixed admission rejects the malformed first HOME document")
        val result = StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.Fixed, home, reader)

        Then("it reads only the failing document and does not alter any state")
        result shouldBe a[Consequence.Failure[_]]
        reader.paths.toVector shouldBe Vector(textus)
        _snapshot(home) shouldBe before
      }
    }

    "normalize an injected HOME lookup without mutating JVM-global state" in {
      _with_home { home =>
        Given("a non-normalized HOME lookup containing both standalone profile documents")
        val textus = home.resolve(".textus/user-profile.yaml")
        val cncf = home.resolve(".cncf/user-profile.yaml")
        _write(textus, _profile("textus-user"))
        _write(cncf, _profile("cncf-user"))
        val reader = new RecordingReader
        val nonnormalizedhome = home.resolve("probe").resolve("..")
        val lookup = new StaticHomeLookup(nonnormalizedhome)

        When("the resolver uses the injected lookup")
        val result = StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.Fixed, lookup, reader)

        Then("it normalizes HOME and admits only its two profile documents")
        result shouldBe a[Consequence.Success[_]]
        result.toOption.get.map(_.path) shouldBe Vector(textus, cncf)
        reader.paths.toVector shouldBe Vector(textus, cncf)
      }
    }

    "avoid every profile read or state change for authenticated or controlled test execution" in {
      _with_home { home =>
        Given("profile and unrelated sentinel state plus a reader that would record every access")
        _write(home.resolve(".textus/user-profile.yaml"), _profile("textus-user"))
        _write(home.resolve(".cncf/user-profile.yaml"), _profile("cncf-user"))
        _write_sentinels(home)
        val reader = new RecordingReader
        val before = _snapshot(home)

        When("non-standalone profiles resolve")
        val authenticated = StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.Authenticated, home, reader)
        val controlled = StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.ControlledTest, home, reader)

        Then("they receive no layers, no standalone fallback, no reads, and no mutations")
        authenticated.toOption.get shouldBe empty
        controlled.toOption.get shouldBe empty
        reader.paths shouldBe empty
        _snapshot(home) shouldBe before
      }
    }

    "reject non-HOME source origins before any profile read" in {
      Given("an explicit project candidate")
      val reader = new RecordingReader
      val candidate = Path.of("project", "user-profile.yaml")

      When("the HOME-only admission seam is used with project origin")
      val result = StandaloneUserProfileResolver.admit(
        StandaloneUserProfileResolver.Layer.TextusHome,
        candidate,
        ConfigurationOrigin.Project,
        reader
      )

      Then("the candidate is rejected without reading it")
      result shouldBe a[Consequence.Failure[_]]
      reader.paths shouldBe empty
    }
  }

  private final class RecordingReader extends StandaloneUserProfileResolver.Reader {
    val paths: ArrayBuffer[Path] = ArrayBuffer.empty

    def load(path: Path): Consequence[Option[StandaloneUserProfile.Document]] = {
      paths += path
      StandaloneUserProfile.load(path)
    }
  }

  private final class StaticHomeLookup(path: Path) extends StandaloneUserProfileResolver.HomeLookup {
    def resolve(): Consequence[Path] = Consequence.success(path)
  }

  private def _profile(id: String): String =
    s"""apiVersion: textus/v1
       |kind: StandaloneUserProfile
       |user:
       |  id: $id
       |""".stripMargin

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }

  private def _write_sentinels(home: Path): Unit = {
    _write(home.resolve(".textus/unrelated.txt"), "textus sentinel")
    _write(home.resolve(".cncf/unrelated.txt"), "cncf sentinel")
    _write(home.resolve("project/user-profile.yaml"), _profile("project-user"))
    _write(home.resolve("cwd/user-profile.yaml"), _profile("cwd-user"))
  }

  private def _snapshot(root: Path): Map[String, String] = {
    val stream = Files.walk(root)
    try {
      stream.iterator.asScala.toVector.sortBy(_.toString).map { path =>
        val relative = root.relativize(path).toString
        if (Files.isDirectory(path)) s"directory:$relative" -> ""
        else if (Files.isRegularFile(path)) s"file:$relative" -> _sha256(Files.readAllBytes(path))
        else s"other:$relative" -> path.toString
      }.toMap
    } finally stream.close()
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _with_home(f: Path => Unit): Unit = {
    val home = Files.createTempDirectory("standalone-user-profile-home")
    try f(home)
    finally {
      val stream = Files.walk(home)
      try stream.sorted(java.util.Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
