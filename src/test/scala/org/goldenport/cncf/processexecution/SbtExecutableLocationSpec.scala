package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtExecutableLocationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _executable(directory: Path): Path = {
    Files.createDirectories(directory)
    val file = Files.writeString(directory.resolve("sbt"), "fixture, never executed")
    file.toFile.setExecutable(true) shouldBe true
    file.toRealPath()
  }

  private def _directory(): Path = Files.createTempDirectory(
    Files.createDirectories(Paths.get("target", "cncf", "work", "sbt-executable-location").toAbsolutePath), "case-")

  private def _close(root: Path): Unit =
    Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }

  "The normal SBT executable location" should {
    "select the first available executable in the declared PATH" in {
      Given("an unavailable first entry followed by two executable fixtures")
      val root = _directory()
      try {
      val first = _executable(root.resolve("first"))
      _executable(root.resolve("second"))
      val path = Vector(root.resolve("missing"), first.getParent, root.resolve("second"))
        .mkString(java.io.File.pathSeparator)
      When("the reserved normal sbt location is resolved")
      val result = SbtExecutableLocation.resolveC(Paths.get("sbt"), Map("PATH" -> path))
      Then("the first available canonical path is selected without running it")
      result.toOption shouldBe Some(first)
      } finally _close(root)
    }

    "honor an explicit executable without using the declared PATH" in {
      Given("a PATH fixture and a different explicit executable")
      val root = _directory()
      try {
      val path = _executable(root.resolve("path"))
      val explicit = _executable(root.resolve("explicit"))
      When("the explicit absolute path is resolved")
      val result = SbtExecutableLocation.resolveC(explicit, Map("PATH" -> path.getParent.toString))
      Then("the explicit executable wins")
      result.toOption shouldBe Some(explicit)
      } finally _close(root)
    }

    "report a missing default as configuration failure and never fall back for invalid explicit paths" in {
      Given("an empty declared PATH and an available alternate PATH fixture")
      val root = _directory()
      try {
      val executable = _executable(root.resolve("path"))
      val plain = Files.createFile(root.resolve("plain"))
      When("the default, unavailable explicit paths and unrecognized relative paths are resolved")
      val missing = SbtExecutableLocation.resolveC(Paths.get("sbt"), Map("PATH" -> ""))
      val invalid = Vector(root.resolve("missing"), plain, Paths.get("bin/sbt"), Paths.get("sbt --batch"))
        .map(path => SbtExecutableLocation.resolveC(path, Map("PATH" -> executable.getParent.toString)))
      Then("the missing default describes the environment and every invalid location is rejected")
      missing.isFaillure shouldBe true
      missing.toString should include ("unavailable on PATH")
      invalid.foreach(_.isFaillure shouldBe true)
      } finally _close(root)
    }
  }
}
