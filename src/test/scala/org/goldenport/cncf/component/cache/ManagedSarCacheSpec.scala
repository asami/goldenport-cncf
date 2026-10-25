package org.goldenport.cncf.component.cache

import java.nio.file.{Files, NotDirectoryException, Path, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.Consequence

/** Local SAR composition and immutable replacement contracts.
  * @since   Oct. 21, 2026
  * @version Oct. 22, 2026
  */
class ManagedSarCacheSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ManagedCarCacheFixture.{withCacheFixture, runtime, fileIdentity, Holder}
  import ManagedSarCacheFixture.*

  "Managed SAR backing and SNAPSHOT generations" should {
    "runtime composition" which {
      "E1 retain directory-backed SAR trees through final runtime shutdown" must afterWord("in spec:car-sar-cache-management, example:E1, rules:C1,C2.1,C3,C4,C6, phase:116") {
        "preserve old lazy readers across replacement" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2.1,C3,C4,C6; Example: E1; a local SNAPSHOT SAR containing a CAR directory and two runtime owners")
          val repository = Files.createDirectory(root.resolve("repository"))
          val sar = archive(repository.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val first = runtime(repository, cache.root)
          val second = runtime(repository, cache.root)
          try {
            first.tree shouldBe second.tree
            val oldtree = first.tree
            val identity = fileIdentity(oldtree)
            When("replacement publishes new bytes while both original readers remain live")
            replace(sar, root, false, "new")
            val fresh = runtime(repository, cache.root)
            try {
              Then("new readers select the replacement and original readers retain lazy access")
              fresh.tree should not be oldtree
              fresh.lazyResource("managed-cache/lazy.txt") shouldBe "new"
              first.close()
              second.lazyResource("managed-cache/lazy.txt") shouldBe "old"
              fileIdentity(oldtree) shouldBe identity
              When("the final original reader closes")
              second.close()
              Then("its retired SAR backing disappears immediately without an idle collection")
              Files.exists(oldtree) shouldBe false
              Files.isDirectory(fresh.tree) shouldBe true
              Files.isRegularFile(sar) shouldBe true
            } finally fresh.close()
          } finally { first.close(); second.close() }
        }
      }

      "E2 share independent packed CAR expansions without immortal embedded aliases" must afterWord("in spec:car-sar-cache-management, example:E2, rules:C2.1,C3,C4, phase:116") {
        "retire the parent independently from the child's live tree" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E2; a packed CAR inside a local SAR and two runtime owners")
          val repository = Files.createDirectory(root.resolve("repository"))
          val sar = archive(repository.resolve("fixture-SNAPSHOT.sar"), root, true, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val parent = cache.acquireSarC(sar).TAKE
          val parenttree = parent.value.root
          parent.close()
          val first = runtime(repository, cache.root)
          val second = runtime(repository, cache.root)
          try {
            first.tree shouldBe second.tree
            first.tree.toString should include ("extracted/car/")
            When("the parent coordinate and packed child change generation")
            replace(sar, root, true, "new")
            val fresh = runtime(repository, cache.root)
            try {
              Then("the retired parent can disappear while independently expanded children stay live")
              Files.exists(parenttree) shouldBe false
              first.close()
              second.lazyResource("managed-cache/lazy.txt") shouldBe "old"
              second.close()
              Then("the final child's release immediately retires its old expansion")
              Files.exists(second.tree) shouldBe false
              fresh.lazyResource("managed-cache/lazy.txt") shouldBe "new"
            } finally fresh.close()
          } finally { first.close(); second.close() }
        }
      }
    }

    "selection and independent retirement" which {
      "E3 collect source and tree only after each payload's own final release" must afterWord("in spec:car-sar-cache-management, example:E3, rules:C2.1,C3,C4, phase:116") {
        "support both source-first and tree-first release orders" in {
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E3; generated independent source and tree ownership histories for a replaced SAR")
          val property = Prop.forAllNoShrink(Gen.oneOf(true, false)) { sourcefirst => withCacheFixture { root =>
            val sar = archive(root.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
            val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
            val old = cache.acquireSarC(sar).TAKE
            val source = cache.acquireSourceC(sar, ArchiveKind.Sar).TAKE
            val original = source.path
            val tree = old.value.root
            replace(sar, root, false, "new")
            val fresh = cache.acquireSarC(sar).TAKE
            try {
              if (sourcefirst) {
                source.close()
                Files.exists(original) shouldBe false
                Files.isDirectory(tree) shouldBe true
                old.close()
              } else {
                old.close()
                Files.exists(tree) shouldBe false
                Files.isRegularFile(original) shouldBe true
                source.close()
              }
              Files.exists(original) shouldBe false
              Files.exists(tree) shouldBe false
              Files.isDirectory(fresh.value.root) shouldBe true
              true
            } finally { old.close(); source.close(); fresh.close() }
          } }
          When("both generated release orders are interpreted through native usage leases")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8).withWorkers(1), property)
          Then("neither payload waits for unrelated backing usage and neither live payload is removed")
          withClue(checked.toString)(checked.passed shouldBe true)
        }
      }

      "E4 preserve current aliases, reuse identical bytes and retain old selection after invalid refresh" must afterWord("in spec:car-sar-cache-management, example:E4, rules:C1,C2,C2.1,C6, phase:116") {
        "protect the last current reference" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2,C2.1,C6; Example: E4; two coordinate aliases selecting identical SAR content")
          val firstpath = archive(root.resolve("first-SNAPSHOT.sar"), root, false, "old")
          val secondpath = Files.copy(firstpath, root.resolve("second-SNAPSHOT.sar"))
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val first = cache.acquireSarC(firstpath).TAKE
          val tree = first.value.root
          val identity = fileIdentity(tree)
          val second = cache.acquireSarC(secondpath).TAKE
          second.value.root shouldBe tree
          first.close(); second.close()
          When("the same bytes are selected again and one alias then changes")
          val reused = cache.acquireSarC(firstpath).TAKE
          try fileIdentity(reused.value.root) shouldBe identity finally reused.close()
          replace(firstpath, root, false, "new")
          val fresh = cache.acquireSarC(firstpath).TAKE
          fresh.close()
          Then("the other current alias still protects the original generation")
          Files.isDirectory(tree) shouldBe true
          val protocol = new ManagedGenerationSelection(cache.root, cache.policy)
          val coordinate = protocol.coordinate(secondpath, ArchiveKind.Sar)
          val selection = protocol.observe(coordinate)
          When("an invalid replacement is presented at the remaining coordinate")
          Files.writeString(secondpath, "invalid archive")
          cache.acquireSarC(secondpath).isSuccess shouldBe false
          Then("the authoritative current pointer and old tree survive the failed refresh")
          protocol.observe(coordinate) shouldBe selection
          Files.isDirectory(tree) shouldBe true
          replace(secondpath, root, false, "last")
          val last = cache.acquireSarC(secondpath).TAKE
          last.close()
          Files.exists(tree) shouldBe false
          Files.isRegularFile(firstpath) shouldBe true
          Files.isRegularFile(secondpath) shouldBe true
        }
      }

      "E5 reject stale publication after an away-and-back selection" must afterWord("in spec:car-sar-cache-management, example:E5, rules:C2.1,C3, phase:116") {
        "compare the actual revision rather than only content identity" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3; Example: E5; protected prepared generations and an observed coordinate revision")
          val sar = archive(root.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val old = cache.acquireSarC(sar).TAKE
          val oldsource = cache.acquireSourceC(sar, ArchiveKind.Sar).TAKE
          val protocol = new ManagedGenerationSelection(cache.root, cache.policy)
          val coordinate = protocol.coordinate(sar, ArchiveKind.Sar)
          val observed = protocol.observe(coordinate)
          replace(sar, root, false, "new")
          val fresh = cache.acquireSarC(sar).TAKE
          try {
            val freshsource = cache.acquireSourceC(sar, ArchiveKind.Sar).TAKE
            try {
              When("another publisher switches away and then back while stale work remains prepared")
              protocol.commit(coordinate, protocol.observe(coordinate), old.generation.source) shouldBe true
              Then("the old revision cannot overwrite a newer selection even when content matches again")
              protocol.commit(coordinate, observed, fresh.generation.source) shouldBe false
              protocol.observe(coordinate).map(_.source) shouldBe Some(old.generation.source)
              protocol.observe(coordinate).map(_.revision) should not be observed.map(_.revision)
            } finally freshsource.close()
          } finally { old.close(); oldsource.close(); fresh.close() }
        }
      }
    }

    "contended retirement and protected source reads" which {
      "E7 retry a contended source independently from a retired tree" must afterWord("in spec:car-sar-cache-management, example:E7, rules:C2.1,C3,C4, phase:116") {
        "leave pending collection retryable without holding the coordinate" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E7; an exclusively protected source generation and its independently leased SAR expansion")
          val sar = archive(root.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val old = cache.acquireSarC(sar).TAKE
          val source = cache.root.resolve("sources/sar").resolve(old.generation.source.digest).resolve("archive.sar")
          val oldtree = old.value.root
          val protocol = new ManagedGenerationSelection(cache.root, cache.policy)
          val coordinate = protocol.coordinate(sar, ArchiveKind.Sar)
          val exclusion = ManagedCacheLocks.acquire(cache.root.resolve("locks/generations").resolve(old.generation.source.key + ".lock"), false, cache.policy)
          try {
            When("selection meets generation contention and the coordinate then changes")
            protocol.pinCurrentSource(coordinate) shouldBe None
            protocol.observe(coordinate).map(_.source) shouldBe Some(old.generation.source)
            replace(sar, root, false, "new")
            val fresh = cache.acquireSarC(sar).TAKE
            try {
              old.close()
              Then("tree retirement does not wait for its original's exclusive holder")
              Files.exists(oldtree) shouldBe false
              Files.isRegularFile(source) shouldBe true
              Files.isRegularFile(cache.root.resolve("metadata/generations").resolve(old.generation.source.key).resolve("retirement.json")) shouldBe true
              val other = archive(root.resolve("other-SNAPSHOT.sar"), root, false, "other")
              val unrelated = cache.acquireSarC(other).TAKE
              unrelated.close()
              Files.isDirectory(fresh.value.root) shouldBe true
            } finally fresh.close()
          } finally { exclusion.close(); old.close() }
          When("the external native exclusion ends and managed collection retries")
          cache.collectC().TAKE
          Then("the pending source is reclaimed while caller-owned archives survive")
          Files.exists(source) shouldBe false
          Files.isRegularFile(sar) shouldBe true
        }
      }

      "E8 protect managed originals when they are themselves ingestion inputs" must afterWord("in spec:car-sar-cache-management, example:E8, rules:C1,C2.1,C3,C4, phase:116") {
        "retain the old original through a managed reingestion lease" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2.1,C3,C4; Example: E8; a cache-owned SAR original reused as an ingestion input")
          val sar = archive(root.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val old = cache.acquireSarC(sar).TAKE
          val source = cache.acquireSourceC(sar, ArchiveKind.Sar).TAKE
          val original = source.path
          val tree = old.value.root
          val captured = cache.acquireSourceC(original, ArchiveKind.Sar).TAKE
          try {
            captured.generation shouldBe source.generation
            source.close()
            When("the coordinate switches and the old tree no longer has a reader")
            replace(sar, root, false, "new")
            val fresh = cache.acquireSarC(sar).TAKE
            fresh.close()
            old.close()
            Then("the independently reingested original remains protected until its own release")
            Files.isRegularFile(captured.path) shouldBe true
            Files.exists(tree) shouldBe false
          } finally { old.close(); source.close(); captured.close() }
          Files.exists(original) shouldBe false
        }
      }
    }

    "exceptional finalization and targeted embedded retirement" which {
      "E9 release every acquired protection when finalization or cleanup fails" must afterWord("in spec:car-sar-cache-management, example:E9, rules:C2.1,C3,C4, phase:116") {
        "preserve the primary filesystem failure and suppress reverse-order cleanup failures" in {
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E9; native source and extraction protections with generated filesystem failure points")
          val property = Prop.forAllNoShrink(Gen.oneOf(true, false), Gen.oneOf(true, false)) { (sourcefails, extractionfails) => withCacheFixture { root =>
            val policy = ManagedCachePolicy(root.resolve("cache"))
            val generation = SourceGeneration(ArchiveKind.Sar, "1" * 64)
            val sourcelock = policy.root.resolve("locks/generations").resolve(generation.key + ".lock")
            val extractionlock = policy.root.resolve("locks/generations").resolve(ExtractionGeneration(generation).key + ".lock")
            val sourcefailure = Files.writeString(root.resolve("source-finalization-file"), "not a directory")
            val extractionfailure = Files.writeString(root.resolve("extraction-cleanup-file"), "not a directory")
            val finalizationfailure = Files.writeString(root.resolve("retirement-finalization-file"), "not a directory")
            val sourcepin = ManagedCacheLocks.acquire(sourcelock, true, policy)
            val extractionpin = ManagedCacheLocks.acquire(extractionlock, true, policy)
            var releases = Vector.empty[String]
            val source = new SourceLease(generation, sourcefailure, () => {
              sourcepin.close()
              releases :+= "source"
              if (sourcefails) Using.resource(Files.list(sourcefailure))(_ => ())
            })
            val extraction = new ExtractionLease(ExtractionGeneration(generation), root, () => {
              extractionpin.close()
              releases :+= "extraction"
              if (extractionfails) Using.resource(Files.list(extractionfailure))(_ => ())
            })
            try {
              val failure = intercept[NotDirectoryException] {
                ManagedArchiveCache.withAcquisitionCleanup { cleanup =>
                  cleanup.retain(source)
                  cleanup.retain(extraction)
                  source.close()
                  Using.resource(Files.list(finalizationfailure))(_ => ())
                  cleanup.transfer(extraction)
                }
              }
              failure.getFile shouldBe (if (sourcefails) sourcefailure else finalizationfailure).toString
              failure.getSuppressed.toVector.map(_.asInstanceOf[NotDirectoryException].getFile) shouldBe
                (if (extractionfails) Vector(extractionfailure.toString) else Vector.empty)
              releases shouldBe Vector("source", "extraction")
              val sourceexclusion = ManagedCacheLocks.tryAcquire(sourcelock, false)
              val extractionexclusion = ManagedCacheLocks.tryAcquire(extractionlock, false)
              try {
                sourceexclusion.isDefined shouldBe true
                extractionexclusion.isDefined shouldBe true
              } finally {
                extractionexclusion.foreach(_.close())
                sourceexclusion.foreach(_.close())
              }
              // A failed extraction close must also leave the source release owned.
              releases = Vector.empty
              val latepin = ManagedCacheLocks.acquire(sourcelock, true, policy)
              val earlypin = ManagedCacheLocks.acquire(extractionlock, true, policy)
              val cleanupfailure = intercept[NotDirectoryException] {
                ManagedArchiveCache.withAcquisitionCleanup { cleanup =>
                  cleanup.retain(new AutoCloseable { def close(): Unit = {
                    latepin.close()
                    releases :+= "source"
                    Using.resource(Files.list(sourcefailure))(_ => ())
                  } })
                  cleanup.retain(new AutoCloseable { def close(): Unit = {
                    earlypin.close()
                    releases :+= "extraction"
                    Using.resource(Files.list(extractionfailure))(_ => ())
                  } })
                }
              }
              cleanupfailure.getFile shouldBe extractionfailure.toString
              cleanupfailure.getSuppressed.toVector.map(_.asInstanceOf[NotDirectoryException].getFile) shouldBe Vector(sourcefailure.toString)
              releases shouldBe Vector("extraction", "source")
              val finalsource = ManagedCacheLocks.tryAcquire(sourcelock, false)
              val finalextraction = ManagedCacheLocks.tryAcquire(extractionlock, false)
              try {
                finalsource.isDefined shouldBe true
                finalextraction.isDefined shouldBe true
              } finally { finalextraction.foreach(_.close()); finalsource.foreach(_.close()) }
              true
            } finally { extraction.close(); source.close() }
          } }
          When("source finalization and later retirement fail across generated cleanup histories")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(12).withWorkers(1), property)
          Then("neither native protection is stranded and every cleanup preserves primary and suppressed errors")
          withClue(checked.toString)(checked.passed shouldBe true)
        }
      }

      "E10 retire embedded originals beyond the scan limit while retaining live trees and current aliases" must afterWord("in spec:car-sar-cache-management, example:E10, rules:C2.1,C3,C4, phase:116") {
        "reconcile the replaced parent's dependents independently of unrelated metadata" in {
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E10; a packed child with generated alias histories and unrelated pending metadata beyond a one-entry scan")
          val property = Prop.forAllNoShrink(Gen.oneOf(true, false)) { aliased => withCacheFixture { root =>
            val sar = archive(root.resolve("fixture-SNAPSHOT.sar"), root, true, "old")
            val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), collectionMaxEntries = 1))
            val protocol = new ManagedGenerationSelection(cache.root, cache.policy)
            // A retryable orphan record is valid even when its payload was already deleted.
            protocol.markRetired(SourceGeneration(ArchiveKind.Car, "0" * 64))
            val parent = cache.acquireSarC(sar).TAKE
            val aliaspath = Files.copy(parent.value.carArtifacts.head, root.resolve("alias.car"))
            val alias = if (aliased) Some(cache.acquireCarC(aliaspath).TAKE) else None
            var childlease: Option[AutoCloseable] = None
            val child = cache.withRetainedEmbeddedCarC(parent.value.carArtifacts.head, parent.generation.source,
              lease => { childlease = Some(lease); Consequence.success(()) })(value => Consequence.success(value)).TAKE
            val childsource = cache.acquireSourceC(aliaspath).TAKE
            val original = childsource.path
            val generation = childsource.generation
            childsource.close()
            parent.close()
            alias.foreach(_.close())
            try {
              Files.isRegularFile(cache.root.resolve("metadata/generations")
                .resolve(SourceGeneration(ArchiveKind.Car, "0" * 64).key).resolve("retirement.json")) shouldBe true
              protocol.dependentSources(parent.generation.source) should contain(generation)
              replace(sar, root, true, "new")
              val fresh = cache.acquireSarC(sar).TAKE
              try {
                Files.isDirectory(child.root) shouldBe true
                Files.isRegularFile(original) shouldBe aliased
                if (aliased) {
                  // The independent coordinate remains authoritative until its own switch.
                  ManagedCarCacheFixture.archive(aliaspath, root, "replacement alias")
                  val replacement = cache.acquireCarC(aliaspath).TAKE
                  replacement.close()
                  Files.exists(original) shouldBe false
                  Files.isDirectory(child.root) shouldBe true
                }
                Files.isDirectory(fresh.value.root) shouldBe true
                Files.isRegularFile(aliaspath) shouldBe true
              } finally fresh.close()
              childlease.foreach(_.close())
              Files.exists(child.root) shouldBe false
              true
            } finally { childlease.foreach(_.close()); parent.close(); alias.foreach(_.close()) }
          } }
          When("replacement reconciles the parent's embedded children with and without another current alias")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8).withWorkers(1), property)
          Then("each unused old child original disappears immediately while its leased tree and other current aliases survive")
          withClue(checked.toString)(checked.passed shouldBe true)
        }
      }
    }

    "cross-process replacement" which {
      "E6 preserve another JVM's old directory reader while selecting the new SAR" must afterWord("in spec:car-sar-cache-management, example:E6, rules:C2.1,C3,C4, phase:116") {
        "retire only after the native remote reader ends" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2.1,C3,C4; Example: E6; a fixture JVM holding a directory-backed SAR and a replacement reader in this JVM")
          val repository = Files.createDirectory(root.resolve("repository"))
          val sar = archive(repository.resolve("fixture-SNAPSHOT.sar"), root, false, "old")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val remote = new Holder(repository, cache.root, root.resolve("holder-stderr.log"))
          try {
            val oldtree = remote.tree
            When("another JVM selects a complete new generation")
            replace(sar, root, false, "new")
            val fresh = runtime(repository, cache.root)
            try {
              Then("the remote old generation and local new generation both retain lazy access")
              fresh.tree should not be oldtree
              remote.load() shouldBe "old"
              fresh.lazyResource("managed-cache/lazy.txt") shouldBe "new"
              When("the remote owner closes its loader and native lease")
              remote.close()
              Then("its retired generation disappears immediately")
              Files.exists(oldtree) shouldBe false
            } finally fresh.close()
          } finally remote.close()
        }
      }
    }
  }
}

private[cache] object ManagedSarCacheFixture {
  def archive(target: Path, assets: Path, packed: Boolean, payload: String): Path = {
    val car = ManagedCarCacheFixture.archive(Files.createTempFile(assets, "embedded-", ".car"), assets, payload)
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      zip.putNextEntry(new ZipEntry("subsystem-descriptor.json"))
      zip.write("""{"name":"fixture","version":"0.1.0","subsystem":"fixture","components":[{"namespace":"org.goldenport.fixture","id":"CanonicalArchiveFixture","version":"0.1.0"}]}""".getBytes(java.nio.charset.StandardCharsets.UTF_8))
      zip.closeEntry()
      if (packed) {
        zip.putNextEntry(new ZipEntry("component/fixture.car"))
        Files.copy(car, zip)
        zip.closeEntry()
      } else Using.resource(new ZipInputStream(Files.newInputStream(car))) { input =>
        var entry = input.getNextEntry
        while (entry != null) {
          zip.putNextEntry(new ZipEntry("component/fixture/" + entry.getName))
          input.transferTo(zip)
          zip.closeEntry()
          input.closeEntry()
          entry = input.getNextEntry
        }
      }
    }
    target
  }

  def replace(target: Path, assets: Path, packed: Boolean, payload: String): Unit = {
    val prepared = Files.createTempFile(assets, "replacement-", ".sar")
    archive(prepared, assets, packed, payload)
    Files.move(prepared, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  }
}
