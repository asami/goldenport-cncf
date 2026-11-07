package org.goldenport.cncf.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.CncfMain

class ManagedCacheOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The cache operation" should {
    "reach the managed owner through the public main before application bootstrap" in {
      Given("an explicit cache root and the launcher's exact standalone argument prefix")
      val parent = Files.createDirectories(Path.of("target/managed-cache-cli-spec"))
      val root = Files.createTempDirectory(parent, "main-")
      val originalOut = System.out
      val originalErr = System.err
      val bytes = new ByteArrayOutputStream()
      val output = new PrintStream(bytes, true, StandardCharsets.UTF_8)
      val prefix = Array("--no-default-components", "command", "cache")
      try {
        Console.withOut(output) {
          Console.withErr(output) {
            When("the public main inspects and dry-runs collection, including the direct cache form")
            CncfMain.main(prefix ++ Array("inspect", "--root", root.toString))
            CncfMain.main(Array("cache", "collect", "--root", root.toString))
            Then("the managed owner reports both results")
            val report = bytes.toString(StandardCharsets.UTF_8)
            report should include ("CacheInspection")
            report should include ("CacheCollectionResult")

            When("the complete tail contains an invalid option or a retirement is refused")
            val invalid = intercept[CncfMain.CliFailed] {
              CncfMain.main(prefix ++ Array("inspect", "--root", root.toString, "--no-default-components"))
            }
            val refused = intercept[CncfMain.CliFailed] {
              CncfMain.main(prefix ++ Array("legacy-retire", "--root", root.toString,
                "--kind", "car", "--key", "0" * 64, "--generation", "1" * 64, "--apply"))
            }
            Then("the public failure convention preserves the parser and owner status")
            invalid.code shouldBe 2
            refused.code shouldBe 1
          }
        }
      } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
        output.close()
        Using.resource(Files.walk(root))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_)))
      }
    }

    "require a root, use dry-run by default, and require exact retirement inputs" in {
      ManagedCacheOperation.parse(Vector("collect")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache")).toOption.get.applyChanges shouldBe false
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--dry-run")).toOption.get.applyChanges shouldBe false
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--dry-run", "--apply")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--batches", "2")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("legacy-retire", "--root", "/tmp/cache", "--kind", "car")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("inspect", "--root", "/tmp/cache", "--apply")).isLeft shouldBe true
    }

    "route launcher-style command cache arguments through the runtime entry" in {
      Given("an explicit managed cache root and a runtime without default components")
      val parent = Files.createDirectories(Path.of("target/managed-cache-cli-spec"))
      val root = Files.createTempDirectory(parent, "runtime-")
      try {
        val prefix = Array("--no-default-components", "command", "cache")
        When("the runtime receives inspection and bounded collection commands")
        val inspect = CncfRuntime.run(prefix ++ Array("inspect", "--root", root.toString))
        val dryrun = CncfRuntime.run(prefix ++ Array("collect", "--root", root.toString))
        val apply = CncfRuntime.run(prefix ++ Array("collect", "--root", root.toString, "--apply", "--batches", "2"))
        Then("each operation returns the managed cache owner's success status")
        inspect shouldBe 0
        dryrun shouldBe 0
        apply shouldBe 0

        When("invalid options or a mismatched retirement are passed through the same runtime entry")
        val invalid = CncfRuntime.run(prefix ++ Array("collect", "--root", root.toString, "--batches", "2"))
        val duplicate = CncfRuntime.run(prefix ++ Array("inspect", "--root", root.toString, "--root", root.toString))
        val unknown = CncfRuntime.run(prefix ++ Array("inspect", "--root", root.toString, "--unknown-cache-option"))
        val frameworkoption = CncfRuntime.run(prefix ++ Array("inspect", "--root", root.toString, "--no-default-components"))
        val missingroot = CncfRuntime.run(prefix ++ Array("inspect"))
        val refused = CncfRuntime.run(prefix ++ Array("legacy-retire", "--root", root.toString,
          "--kind", "car", "--key", "0" * 64, "--generation", "1" * 64, "--apply"))
        Then("argument failures and owner refusal retain their distinct failure statuses")
        invalid shouldBe 2
        duplicate shouldBe 2
        unknown shouldBe 2
        frameworkoption shouldBe 2
        missingroot shouldBe 2
        refused shouldBe 1
      } finally Using.resource(Files.walk(root))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_)))
    }

    "report bounded inspection, dry-run and a refused unknown legacy pointer" in {
      val parent = Files.createDirectories(Path.of("target/managed-cache-cli-spec"))
      val root = Files.createTempDirectory(parent, "cache-")
      try {
        var output = Vector.empty[String]
        def run(args: String*): Int = ManagedCacheOperation.execute(
          ManagedCacheOperation.parse(args.toVector).toOption.get, text => output :+= text)
        CncfRuntime.run(Array("cache", "inspect", "--root", root.toString)) shouldBe 0
        run("inspect", "--root", root.toString) shouldBe 0
        output.last should include ("CacheInspection")
        run("collect", "--root", root.toString) shouldBe 0
        output.last should include ("CacheCollectionResult")
        run("legacy-retire", "--root", root.toString, "--kind", "car", "--key", "0" * 64,
          "--generation", "1" * 64, "--apply") shouldBe 1
        output.exists(_.contains("refused")) shouldBe true
      } finally Using.resource(Files.walk(root))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_)))
    }
  }
}
