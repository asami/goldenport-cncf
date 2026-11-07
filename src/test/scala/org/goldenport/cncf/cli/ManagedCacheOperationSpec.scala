package org.goldenport.cncf.cli

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ManagedCacheOperationSpec extends AnyWordSpec with Matchers {
  "The cache operation" should {
    "require a root, use dry-run by default, and require exact retirement inputs" in {
      ManagedCacheOperation.parse(Vector("collect")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache")).toOption.get.applyChanges shouldBe false
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--dry-run")).toOption.get.applyChanges shouldBe false
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--dry-run", "--apply")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("collect", "--root", "/tmp/cache", "--batches", "2")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("legacy-retire", "--root", "/tmp/cache", "--kind", "car")).isLeft shouldBe true
      ManagedCacheOperation.parse(Vector("inspect", "--root", "/tmp/cache", "--apply")).isLeft shouldBe true
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
