package org.goldenport.cncf.component.cache

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.{Callable, ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentId, ComponentOrigin}
import org.goldenport.cncf.component.repository.{CanonicalArchiveFixtureComponent, CanonicalArchiveFixtureFactory, ComponentRepository}
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory

/** Local archive, real repository/owner/ClassLoader and native cross-JVM locks.
  * @since   Oct. 21, 2026
  * @version Oct. 22, 2026
  */
class ManagedCarCacheSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ManagedCarCacheFixture.*

  "Repository coordinate selection" should {
    "switch across archive paths while preserving separate repositories and explicit files" in withCacheFixture { root =>
      val firstPath = archive(root.resolve("first.car"), root, "old")
      val secondPath = archive(root.resolve("second.car"), root, "new")
      val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
      val identity = Some("repository-a" -> "example:1-SNAPSHOT")
      val old = cache.acquireCarC(firstPath, identity).TAKE
      val oldTree = old.value.root
      val updated = cache.acquireCarC(secondPath, identity).TAKE
      try {
        updated.generation should not be (old.generation)
        Files.isDirectory(oldTree) shouldBe true
        val separate = cache.acquireCarC(firstPath, Some("repository-b" -> "example:1-SNAPSHOT")).TAKE
        val explicit = cache.acquireCarC(firstPath).TAKE
        try {
          separate.generation shouldBe old.generation
          explicit.generation shouldBe old.generation
          val selection = new ManagedGenerationSelection(cache.root, cache.policy)
          selection.observe(selection.repositoryCoordinate("repository-a", "example:1-SNAPSHOT", ArchiveKind.Car)).get.source shouldBe updated.generation.source
          selection.observe(selection.repositoryCoordinate("repository-b", "example:1-SNAPSHOT", ArchiveKind.Car)).get.source shouldBe old.generation.source
          selection.observe(selection.coordinate(firstPath, ArchiveKind.Car)).get.source shouldBe old.generation.source
        } finally { separate.close(); explicit.close() }
      } finally { updated.close(); old.close(); cache.close() }
    }

    "require the expected generation and no active lease for explicit legacy retirement" in withCacheFixture { root =>
      val car = archive(root.resolve("legacy.car"), root)
      val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
      val active = cache.acquireCarC(car).TAKE
      val (key, generation) = cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE.get
      cache.retireLegacyCurrentC(key, generation, dryRun = true).TAKE shouldBe false
      cache.retireLegacyCurrentC(key, generation, dryRun = false).TAKE shouldBe false
      cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE.get._2 shouldBe generation
      active.close()
      val wrong = SourceGeneration(ArchiveKind.Car, "0" * 64)
      cache.retireLegacyCurrentC(key, wrong, dryRun = false).TAKE shouldBe false
      cache.retireLegacyCurrentC(key, generation, dryRun = true).TAKE shouldBe true
      cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE should not be empty
      cache.retireLegacyCurrentC(key, generation, dryRun = false).TAKE shouldBe true
      cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE shouldBe empty
      cache.close()
    }
  }

  "Legacy retirement" should {
    "leave the current pointer usable after metadata failure and allow a retry" in withCacheFixture { root =>
      val car = archive(root.resolve("legacy-failure.car"), root)
      var fail = false
      val files = new ManagedCacheFiles {
        override def writeMetadata(path: Path, text: String): Unit = {
          if (fail && path.getFileName.toString.startsWith("retirement.json"))
            throw new java.io.IOException("injected retirement write failure")
          super.writeMetadata(path, text)
        }
        def deleteTree(path: Path): Unit = ManagedCacheFiles.deleteTree(path)
      }
      val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
      val lease = cache.acquireCarC(car).TAKE
      val (key, generation) = cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE.get
      lease.close()
      Files.deleteIfExists(cache.root.resolve("metadata/generations").resolve(generation.key).resolve("retirement.json"))
      fail = true
      cache.retireLegacyCurrentC(key, generation, dryRun = false).isSuccess shouldBe false
      cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE.get._2 shouldBe generation
      fail = false
      cache.retireLegacyCurrentC(key, generation, dryRun = false).TAKE shouldBe true
      cache.inspectLegacyCurrentC(car, ArchiveKind.Car).TAKE shouldBe empty
      cache.close()
    }
  }

  "The repository resolver" should {
    "reject simultaneous archives before selection for requested and unrequested discovery" in withCacheFixture { root =>
      Vector(Some("0.1.0-SNAPSHOT"), None).zipWithIndex.foreach { case (requested, index) =>
        val repository = Files.createDirectory(root.resolve(s"ambiguous-$index"))
        val oldPath = archive(repository.resolve("old.car"), root, "old", "0.1.0-SNAPSHOT")
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve(s"ambiguous-cache-$index")))
        val old = runtime(repository, cache.root, requested)
        val selection = new ManagedGenerationSelection(cache.root, cache.policy)
        val key = selection.repositoryCoordinate(repository.toAbsolutePath.normalize.toUri.toString,
          "org.goldenport.fixture.CanonicalArchiveFixture:0.1.0-SNAPSHOT", ArchiveKind.Car)
        val original = selection.observe(key).get.source
        val newPath = archive(repository.resolve("new.car"), root, "new", "0.1.0-SNAPSHOT")
        try {
          val failure = intercept[RuntimeException] { runtime(repository, cache.root, requested) }
          failure.getMessage should include ("Ambiguous CAR repository coordinate")
          selection.observe(key).get.source shouldBe original
          old.lazyResource("managed-cache/lazy.txt") shouldBe "old"
          val explicit = fileRuntime(newPath, cache.root)
          try explicit.lazyResource("managed-cache/lazy.txt") shouldBe "new" finally explicit.close()
          selection.observe(key).get.source shouldBe original
          Files.delete(oldPath)
          val updated = runtime(repository, cache.root, requested)
          val current = selection.observe(key).get.source
          try {
            current should not be original
            updated.lazyResource("managed-cache/lazy.txt") shouldBe "new"
            old.lazyResource("managed-cache/lazy.txt") shouldBe "old"
            val repeated = runtime(repository, cache.root, requested)
            try {
              selection.observe(key).get.source shouldBe current
              repeated.lazyResource("managed-cache/lazy.txt") shouldBe "new"
            } finally repeated.close()
          } finally updated.close()
        } finally { old.close(); cache.close() }
      }
    }

    "keep one SNAPSHOT selection across different flat archive names and isolate other origins" in withCacheFixture { root =>
      val repository = Files.createDirectory(root.resolve("repository"))
      val otherRepository = Files.createDirectory(root.resolve("other-repository"))
      val oldPath = archive(repository.resolve("old.car"), root, "old", "0.1.0-SNAPSHOT")
      val otherPath = otherRepository.resolve("other.car")
      Files.copy(oldPath, otherPath)
      val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
      val old = runtime(repository, cache.root, Some("0.1.0-SNAPSHOT"))
      val other = runtime(otherRepository, cache.root, Some("0.1.0-SNAPSHOT"))
      val explicit = fileRuntime(otherPath, cache.root)
      val newPath = root.resolve("new.car")
      archive(newPath, root, "new", "0.1.0-SNAPSHOT")
      Files.delete(oldPath)
      Files.move(newPath, repository.resolve("new.car"))
      val updated = runtime(repository, cache.root, Some("0.1.0-SNAPSHOT"))
      try {
        old.lazyResource("managed-cache/lazy.txt") shouldBe "old"
        updated.lazyResource("managed-cache/lazy.txt") shouldBe "new"
        other.lazyResource("managed-cache/lazy.txt") shouldBe "old"
        val selection = new ManagedGenerationSelection(cache.root, cache.policy)
        val coordinate = "org.goldenport.fixture.CanonicalArchiveFixture:0.1.0-SNAPSHOT"
        val firstKey = selection.repositoryCoordinate(repository.toAbsolutePath.normalize.toUri.toString, coordinate, ArchiveKind.Car)
        val otherKey = selection.repositoryCoordinate(otherRepository.toAbsolutePath.normalize.toUri.toString, coordinate, ArchiveKind.Car)
        selection.observe(firstKey).get.source should not be (selection.observe(otherKey).get.source)
        selection.observe(selection.coordinate(otherPath, ArchiveKind.Car)).get.source shouldBe selection.observe(otherKey).get.source
        explicit.lazyResource("managed-cache/lazy.txt") shouldBe "old"
      } finally { updated.close(); explicit.close(); other.close(); old.close(); cache.close() }
    }
  }

  "The managed CAR repository route" should {
    "same-JVM ownership and reuse" which {
    "E1 share one completed tree across runtimes, protect lazy loading and rebuild offline after collection" must afterWord("in spec:car-sar-cache-management, example:E1, rules:C1,C2,C3,C5,C6, phase:116") {
      "share one completed tree across runtimes, protect lazy loading and rebuild offline after collection" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2,C3,C5,C6; Example: E1; a local CAR repository and a cache shared by independent runtime owners")
        val repository = Files.createDirectory(root.resolve("repository"))
        archive(repository.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0))
        When("two runtimes resolve the same CAR")
        val first = runtime(repository, cache.root)
        val second = try runtime(repository, cache.root) catch {
          case error: Throwable => first.close(); throw error
        }
        try {
          Then("both runtimes use the same published tree and active collection skips it")
          first.tree shouldBe second.tree
          val identity = fileIdentity(first.tree)
          cache.collectC().TAKE.entries.map(_.disposition) should contain(CacheCollectionDisposition.InUse)
          When("one runtime closes and the other performs a first lazy resource read")
          first.close()
          first.close()
          Then("the remaining runtime retains its complete backing tree")
          second.lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload"
          fileIdentity(second.tree) shouldBe identity
          cache.collectC().TAKE.entries.map(_.disposition) should contain(CacheCollectionDisposition.InUse)
          var closedbeforerelease = false
          second.owner.systemNode.ownRuntimeResourceC(new AutoCloseable {
            def close(): Unit = {
              closedbeforerelease = second.loaders.forall(_.findResource("managed-cache/after-close.txt") == null) &&
                Files.isDirectory(second.tree) && cache.collectC().TAKE.entries.exists(_.disposition == CacheCollectionDisposition.InUse)
            }
          }).TAKE
          When("the final runtime closes, then managed collection runs")
          second.close()
          Then("ClassLoaders closed while the lease was held, and the now-unused tree can be reclaimed")
          closedbeforerelease shouldBe true
          cache.collectC().TAKE.reclaimedCount shouldBe 1
          Files.exists(second.tree) shouldBe false
          cache.inspectC().TAKE.sourceBytes should be > 0L
          Files.isRegularFile(repository.resolve("fixture.car")) shouldBe true
          // ComponentDirRepository has no network resolver. The retained repository
          // body and immutable source suffice after removing only the expansion.
          When("the local repository resolves the retained CAR again without a network resolver")
          val relaunched = runtime(repository, cache.root)
          try {
            Then("the same generation is reexpanded and supports lazy loading")
            relaunched.tree shouldBe second.tree
            relaunched.lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload"
          } finally relaunched.close()
        } finally { first.close(); second.close() }
      }
    }

    "E2 preserve every remaining runtime under generated close order and repeated close requests" must afterWord("in spec:car-sar-cache-management, example:E2, rules:C3,C5, phase:116") {
      "preserve every remaining runtime under generated close order and repeated close requests" in {
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5; Example: E2; two to four independent runtime owners and generated shutdown requests followed by final release")
        val samples = Gen.choose(2, 4).flatMap(count =>
          Gen.listOfN(8, Gen.choose(0, count - 1)).map(requests => (count, requests ++ (0 until count))))
        val property = Prop.forAllNoShrink(samples) { case (count, requests) => withCacheFixture { root =>
          val repository = Files.createDirectory(root.resolve("repository"))
          archive(repository.resolve("fixture.car"), root)
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0))
          var owners = Vector.empty[Runtime]
          try {
            (0 until count).foreach(_ => owners :+= runtime(repository, cache.root))
            var closed = Set.empty[Int]
            requests.foreach { index =>
              owners(index).close()
              owners(index).close()
              closed += index
              val remaining = owners.indices.filterNot(closed)
              if (remaining.nonEmpty) {
                remaining.foreach(index => owners(index).lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload")
                cache.collectC().TAKE.entries.map(_.disposition) should contain(CacheCollectionDisposition.InUse)
              }
            }
            cache.collectC().TAKE.reclaimedCount shouldBe 1
            Files.exists(owners.head.tree) shouldBe false
            true
          } finally owners.foreach(_.close())
        } }
        When("the generated ownership histories are interpreted through the actual repository and shutdown route")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8).withWorkers(1), property)
        Then("no live runtime loses lazy access, and collection succeeds after every owner has ended")
        withClue(checked.toString)(checked.passed shouldBe true)
      }
    }

    "E3 reuse an existing expansion without republishing its files" must afterWord("in spec:car-sar-cache-management, example:E3, rules:C2,C3, phase:116") {
      "reuse an existing expansion without republishing its files" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3; Example: E3; a completed CAR expansion that survives its first lease")
        val car = archive(root.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
        val first = cache.acquireCarC(car).TAKE
        val tree = first.value.root
        val key = fileIdentity(tree)
        Files.writeString(tree.resolve("reuse-marker"), "existing expansion")
        first.close()
        When("the same archive is acquired again")
        val second = cache.acquireCarC(car).TAKE
        try {
          Then("the entry keeps its identity and existing files")
          second.value.root shouldBe tree
          fileIdentity(tree) shouldBe key
          Files.readString(tree.resolve("reuse-marker")) shouldBe "existing expansion"
        } finally second.close()
      }
    }

    "E4 capture independently owned source bytes instead of aliasing a mutable caller archive" must afterWord("in spec:car-sar-cache-management, example:E4, rules:C1,C2, phase:116") {
      "capture independently owned source bytes instead of aliasing a mutable caller archive" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2; Example: E4; a leased immutable capture of a caller-owned CAR")
        val car = archive(root.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
        val source = cache.acquireSourceC(car).TAKE
        try {
          val original = Files.readAllBytes(car).toVector
          When("the caller overwrites its own input archive")
          Files.writeString(car, "replaced caller bytes")
          Then("the protected capture still contains its original bytes")
          Files.readAllBytes(source.path).toVector shouldBe original
        } finally source.close()
        Files.readString(car) shouldBe "replaced caller bytes"
        intercept[IllegalStateException](source.path)
      }
    }

    }

    "concurrent acquisition and collection" which {
    "E5 converge concurrent builders on one published generation" must afterWord("in spec:car-sar-cache-management, example:E5, rules:C2,C3, phase:116") {
      "converge concurrent builders on one published generation" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3; Example: E5; four independent cache instances and a cold local CAR")
        val car = archive(root.resolve("fixture.car"), root)
        val policy = ManagedCachePolicy(root.resolve("cache"), acquisitionMaxAttempts = 128)
        val start = new CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val acquired = new ConcurrentLinkedQueue[ExtractionLease[org.goldenport.cncf.component.CarExtracted]]()
        try {
          val tasks = (1 to 4).map { _ => pool.submit(new Callable[ExtractionLease[org.goldenport.cncf.component.CarExtracted]] {
            def call() = {
              start.await()
              val lease = new ManagedArchiveCache(policy).acquireCarC(car).TAKE
              acquired.add(lease)
              lease
            }
          }) }
          When("all builders request the generation concurrently")
          start.countDown()
          val leases = tasks.map(_.get(60, TimeUnit.SECONDS))
          try {
            Then("every request obtains a lease on the same completed tree and staging is empty")
            leases.map(_.value.root).distinct.size shouldBe 1
            leases.map(value => fileIdentity(value.value.root)).distinct.size shouldBe 1
            new ManagedArchiveCache(policy).inspectC().TAKE.stagingBytes shouldBe 0L
          } finally leases.foreach(_.close())
        } finally {
          pool.shutdownNow()
          pool.awaitTermination(10, TimeUnit.SECONDS)
          acquired.iterator().asScala.foreach(_.close())
        }
      }
    }

    "E6 return only leased complete trees while collection races acquisition" must afterWord("in spec:car-sar-cache-management, example:E6, rules:C2,C3,C5, phase:116") {
      "return only leased complete trees while collection races acquisition" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3,C5; Example: E6; an idle-eligible CAR generation and independent acquisition and collection requests")
        val car = archive(root.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0, acquisitionMaxAttempts = 256))
        val start = new CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
          val reader = pool.submit(new Callable[Unit] {
            def call(): Unit = {
              start.await()
              (1 to 20).foreach { _ =>
                val lease = cache.acquireCarC(car).TAKE
                try {
                  Files.isRegularFile(lease.value.componentMain) shouldBe true
                  Files.isRegularFile(lease.value.root.getParent.resolve("entry.json")) shouldBe true
                } finally lease.close()
              }
            }
          })
          val collector = pool.submit(new Callable[Unit] {
            def call(): Unit = { start.await(); (1 to 50).foreach(_ => cache.collectC().TAKE) }
          })
          When("acquisition and collection are interpreted concurrently")
          start.countDown()
          Then("every returned lease protects a complete JAR and completion metadata")
          reader.get(60, TimeUnit.SECONDS)
          collector.get(60, TimeUnit.SECONDS)
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS) }
      }
    }

    "E7 keep unrelated generations available while another entry is exclusively held" must afterWord("in spec:car-sar-cache-management, example:E7, rules:C2,C3, phase:116") {
      "keep unrelated generations available while another entry is exclusively held" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3; Example: E7; one exclusively held generation and another independent CAR generation")
        val firstcar = archive(root.resolve("first.car"), root)
        val secondcar = archive(root.resolve("second.car"), root, "other payload")
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
        val first = cache.acquireCarC(firstcar).TAKE
        val generation = first.generation
        first.close()
        val exclusion = ManagedCacheLocks.acquire(cache.root.resolve("locks/generations").resolve(generation.key + ".lock"), false, cache.policy)
        try {
          When("the unrelated CAR is acquired without releasing the first generation")
          val other = cache.acquireCarC(secondcar).TAKE
          Then("its complete expansion is available independently")
          try Files.isRegularFile(other.value.componentMain) shouldBe true
          finally other.close()
        } finally exclusion.close()
      }
    }

    }

    "failure and repository placement" which {
    "E8 release protection on exceptional startup and rejected ownership registration" must afterWord("in spec:car-sar-cache-management, example:E8, rules:C3,C6, phase:116") {
      "release protection on exceptional startup and rejected ownership registration" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C6; Example: E8; a managed CAR and the existing SystemNode resource owner")
        val car = archive(root.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0))
        val owner = TestComponentFactory.emptySubsystem("failed-startup")
        try {
          When("startup raises an exception after lease registration")
          val failed = cache.withRetainedCarC(car, owner.systemNode.ownRuntimeResourceC) { _ =>
            throw new IllegalStateException("fixture startup failure")
          }
          Then("failure releases protection so ordinary collection can reclaim the tree")
          failed.isSuccess shouldBe false
          cache.collectC().TAKE.reclaimedCount shouldBe 1
        } finally Subsystem.shutdownOwned(owner)
        When("the already-closed owner rejects a new registration")
        val rejected = cache.withRetainedCarC(car, owner.systemNode.ownRuntimeResourceC)(_ => Consequence.success(()))
        Then("no new lease escapes and ordinary collection remains possible")
        rejected.isSuccess shouldBe false
        cache.collectC().TAKE.reclaimedCount shouldBe 1
      }
    }

    "E9 respect the StandardRepository's explicit cache root" must afterWord("in spec:car-sar-cache-management, example:E9, rules:C1,C6, phase:116") {
      "respect the StandardRepository's explicit cache root" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C6; Example: E9; different configured and explicit roots with an existing local CAR")
        val explicit = Files.createDirectory(root.resolve("explicit"))
        archive(explicit.resolve("fixture.car"), root)
        val owner = TestComponentFactory.subsystemWithConfig(Map("cncf.component.cache.root" -> ConfigurationValue.StringValue(root.resolve("unused").toString)))
        try {
          val repository = new ComponentRepository.StandardRepository(ComponentRepository.StandardRepositoryKind.Car,
            "http://127.0.0.1:1/unavailable", explicit, ComponentCreate(owner, ComponentOrigin.Repository("standard-car")),
            ComponentRepository.resolvePackagePrefixes())
          When("the existing StandardRepository resolves the local artifact")
          val components = repository.discover()
          Then("only the explicit repository root owns the managed expansion")
          components should not be empty
          Files.isDirectory(explicit.resolve("extracted/car")) shouldBe true
          Files.exists(root.resolve("unused")) shouldBe false
        } finally Subsystem.shutdownOwned(owner)
      }
    }

    }

    "cross-process ownership" which {
    "E10 share real runtime leases across two JVMs until the final owner closes" must afterWord("in spec:car-sar-cache-management, example:E10, rules:C1,C2,C3,C5,C6, phase:116") {
      "share real runtime leases across two JVMs until the final owner closes" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2,C3,C5,C6; Example: E10; two fixture-owned JVMs using the same local CAR repository and cold cache")
        val repository = Files.createDirectory(root.resolve("repository"))
        archive(repository.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0))
        val first = new Holder(repository, cache.root, root.resolve("first-stderr.log"))
        try {
          val second = new Holder(repository, cache.root, root.resolve("second-stderr.log"))
          try {
            When("both JVMs complete repository discovery")
            val trees = Vector(first.tree, second.tree)
            Then("both processes share the same complete generation and protect it from collection")
            trees.distinct.size shouldBe 1
            first.tree shouldBe second.tree
            cache.collectC().TAKE.entries.map(_.disposition) should contain(CacheCollectionDisposition.InUse)
            When("the first JVM closes and the second requests its unread resource")
            first.close()
            val payload = second.load()
            Then("the second JVM retains lazy access and its live lease still prevents collection")
            payload shouldBe "lazy payload"
            cache.collectC().TAKE.entries.map(_.disposition) should contain(CacheCollectionDisposition.InUse)
            When("the final JVM closes and managed collection runs")
            second.close()
            Then("the completed tree is reclaimed")
            cache.collectC().TAKE.reclaimedCount shouldBe 1
            Files.exists(second.tree) shouldBe false
            When("a new JVM resolves the retained repository body without networking")
            val relaunched = new Holder(repository, cache.root, root.resolve("relaunched-stderr.log"))
            Then("offline reconstruction restores lazy access")
            try relaunched.load() shouldBe "lazy payload"
            finally relaunched.close()
          } finally second.close()
        } finally first.close()
      }
    }
    }

    "E11 relaunch a repository runtime from its retained expansion before offline reconstruction" must afterWord("in spec:car-sar-cache-management, example:E11, rules:C1,C2,C3,C5,C6, phase:116") {
      "relaunch a repository runtime from its retained expansion before offline reconstruction" in withCacheFixture { root =>
        Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2,C3,C5,C6; Example: E11; a local CAR component loaded through ComponentRepository into a real Subsystem resource owner")
        val repository = Files.createDirectory(root.resolve("repository"))
        archive(repository.resolve("fixture.car"), root)
        val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L))
        try {
          val first = runtime(repository, cache.root)
          val tree = first.tree
          val identity = fileIdentity(tree)
          try {
            first.lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload"
            Files.writeString(tree.resolve("reuse-marker"), "completed first runtime")
          } finally first.close()
          When("a fresh runtime resolves the component after the first runtime has closed")
          val second = runtime(repository, cache.root)
          try {
            Then("the real repository route reuses the same completed tree and preserves lazy access")
            second.tree shouldBe tree
            fileIdentity(second.tree) shouldBe identity
            Files.readString(second.tree.resolve("reuse-marker")) shouldBe "completed first runtime"
            second.lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload"
            cache.collectC().TAKE.inUseCount should be > 0
          } finally second.close()
          When("the relaunched owner has closed and managed collection reclaims its expansion")
          cache.collectC().TAKE.reclaimedCount shouldBe 1
          Then("the expansion is gone but its managed source and repository body remain")
          Files.exists(tree) shouldBe false
          cache.inspectC().TAKE.sourceBytes should be > 0L
          Files.isRegularFile(repository.resolve("fixture.car")) shouldBe true
          When("a third runtime discovers the retained local CAR without a network resolver")
          val third = runtime(repository, cache.root)
          try {
            Then("offline reconstruction restores resources without reviving files from the previous expansion")
            third.tree shouldBe tree
            third.lazyResource("managed-cache/lazy.txt") shouldBe "lazy payload"
            Files.exists(third.tree.resolve("reuse-marker")) shouldBe false
          } finally third.close()
        } finally cache.close()
      }
    }
  }
}

private[cache] object ManagedCarCacheFixture {
  def withCacheFixture[A](body: Path => A): A = {
    val parent = Files.createDirectories(Path.of("target/managed-car-cache-spec").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(parent, "case-")
    try body(root)
    finally Using.resource(Files.walk(root))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_)))
  }

  def fileIdentity(path: Path): Any = Files.readAttributes(path, classOf[BasicFileAttributes]).fileKey()

  def archive(target: Path, assets: Path, payload: String = "lazy payload", version: String = "0.1.0"): Path = {
    val jar = Files.createTempFile(assets, "component-", ".jar")
    Using.resource(new ZipOutputStream(Files.newOutputStream(jar))) { zip =>
      Vector(classOf[CanonicalArchiveFixtureFactory], classOf[CanonicalArchiveFixtureComponent]).foreach { cls =>
        val name = cls.getName.replace('.', '/') + ".class"
        zip.putNextEntry(new ZipEntry(name))
        Using.resource(cls.getClassLoader.getResourceAsStream(name))(_.transferTo(zip))
        zip.closeEntry()
      }
      Vector("managed-cache/lazy.txt" -> payload, "managed-cache/after-close.txt" -> "closed").foreach { case (name, value) =>
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(UTF_8)); zip.closeEntry()
      }
    }
    val descriptor = Files.createTempFile(assets, "descriptor-", ".json")
    Files.writeString(descriptor, s"""{"schemaVersion":3,"component":{"namespace":"org.goldenport.fixture","id":"CanonicalArchiveFixture","version":"$version"}}""")
    CarArchiveFixture.write(target, Vector("component-descriptor.json" -> descriptor, "component/main.jar" -> jar))
    target
  }

  final class Runtime(val owner: Subsystem) extends AutoCloseable {
    val loaders = owner.componentClassLoaderSnapshot
    val tree: Path = loaders.flatMap(_.getURLs.toVector).filter(_.getProtocol == "file")
      .map(url => Path.of(url.toURI)).find(_.getFileName.toString == "main.jar").get.getParent.getParent
    def lazyResource(name: String): String = {
      val input = loaders.iterator.flatMap(loader => Option(loader.getResourceAsStream(name))).next()
      Using.resource(input)(stream => new String(stream.readAllBytes(), UTF_8))
    }
    def close(): Unit = Subsystem.shutdownOwned(owner)
  }

  def runtime(repository: Path, cache: Path, requestedVersion: Option[String] = None): Runtime = {
    val owner = TestComponentFactory.subsystemWithConfig(Map("cncf.component.cache.root" -> ConfigurationValue.StringValue(cache.toString)))
    try {
      val id = ComponentId("org.goldenport.fixture.CanonicalArchiveFixture")
      val descriptors = requestedVersion.toVector.map(version => ComponentDescriptor(name = Some(id.name),
        componentName = Some(id.name), version = Some(version), schemaVersion = Some(3), componentId = Some(id)))
      val components = new ComponentRepository.ComponentDirRepository(repository,
        ComponentCreate(owner, ComponentOrigin.Repository("component-dir"), descriptors), ComponentRepository.resolvePackagePrefixes()).discover()
      require(components.exists(_.componentId.name == "org.goldenport.fixture.CanonicalArchiveFixture"), "fixture CAR discovery failed")
      new Runtime(owner)
    } catch { case error: Throwable => Subsystem.shutdownOwned(owner); throw error }
  }

  def fileRuntime(file: Path, cache: Path): Runtime = {
    val owner = TestComponentFactory.subsystemWithConfig(Map("cncf.component.cache.root" -> ConfigurationValue.StringValue(cache.toString)))
    try {
      val components = new ComponentRepository.ComponentFileRepository(file,
        ComponentCreate(owner, ComponentOrigin.Repository("component-file")), ComponentRepository.resolvePackagePrefixes()).discover()
      require(components.nonEmpty, "explicit file discovery failed")
      new Runtime(owner)
    } catch { case error: Throwable => Subsystem.shutdownOwned(owner); throw error }
  }

  def runtimeClasspath(): String = {
    def _file_(url: java.net.URL): Vector[String] =
      if (url.getProtocol == "file") Vector(Path.of(url.toURI).toAbsolutePath.normalize.toString) else Vector.empty
    val properties = Option(System.getProperty("java.class.path")).toVector
      .flatMap(_.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toVector).filter(_.nonEmpty)
    var loader = Option(getClass.getClassLoader)
    var entries = Vector.empty[String]
    while (loader.nonEmpty) {
      loader.foreach {
        case urls: URLClassLoader => entries ++= urls.getURLs.toVector.flatMap(_file_)
        case _ => ()
      }
      loader = loader.flatMap(value => Option(value.getParent))
    }
    val sources = Vector(ManagedCarCacheHolder.getClass, classOf[ManagedArchiveCache]).flatMap { cls =>
      Option(cls.getProtectionDomain).flatMap(domain => Option(domain.getCodeSource)).toVector
        .flatMap(source => _file_(source.getLocation))
    }
    require(sources.size == 2, "test and production code sources required for fixture JVM")
    val classpath = (sources ++ entries ++ properties).distinct
    require(classpath.forall(value => Files.exists(Path.of(value))), "actual file runtime classpath required")
    classpath.mkString(java.io.File.pathSeparator)
  }

  /** Own only the fixture child. A line protocol uses real startup/close events,
    * with a bounded response wait; no detached child or fixed readiness sleep.
    */
  final class Holder(repository: Path, cache: Path, stderr: Path) extends AutoCloseable {
    private val _process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString,
      "-Dtextus.test=true", "-cp", runtimeClasspath(),
      "org.goldenport.cncf.component.cache.ManagedCarCacheHolder", repository.toString, cache.toString)
      .redirectError(stderr.toFile).start()
    private val _reader = new BufferedReader(new InputStreamReader(_process.getInputStream, UTF_8))
    private val _writer = new PrintWriter(_process.getOutputStream, true, UTF_8)
    private val _reads = Executors.newSingleThreadExecutor()
    private var _closed = false
    private def _response(prefix: String): String = _reads.submit(new Callable[String] {
      def call(): String = {
        var line = _reader.readLine()
        while (line != null && !line.startsWith(prefix)) line = _reader.readLine()
        require(line != null, s"holder ended before $prefix; diagnostics: $stderr")
        line.substring(prefix.length)
      }
    }).get(45, TimeUnit.SECONDS)
    lazy val tree: Path = try Path.of(_response("READY ")) catch {
      case error: Throwable => close(); throw error
    }
    def load(): String = { _writer.println("load"); _response("VALUE ") }
    def hardDeath(): Unit = if (!_closed) {
      _closed = true
      try {
        _process.destroyForcibly()
        require(_process.waitFor(10, TimeUnit.SECONDS), "owned fixture JVM survived hard termination")
        require(_process.exitValue() != 0, "fixture hard termination unexpectedly ran normal close")
      } finally { _writer.close(); _reader.close(); _reads.shutdownNow() }
    }
    def close(): Unit = if (!_closed) {
      _closed = true
      try {
        _writer.println("close")
        if (!_process.waitFor(10, TimeUnit.SECONDS)) {
          _process.destroy()
          require(_process.waitFor(10, TimeUnit.SECONDS), "owned fixture JVM did not terminate")
        }
        require(_process.exitValue() == 0, s"holder failed; diagnostics: $stderr")
      } finally { _writer.close(); _reader.close(); _reads.shutdownNow() }
    }
  }
}

object ManagedCarCacheHolder {
  def main(args: Array[String]): Unit = {
    val runtime = ManagedCarCacheFixture.runtime(Path.of(args(0)), Path.of(args(1)))
    try {
      Console.out.println("READY " + runtime.tree)
      Console.out.flush()
      val input = new BufferedReader(new InputStreamReader(System.in, UTF_8))
      var command = input.readLine()
      while (command != null && command != "close") {
        require(command == "load", "unknown fixture command")
        Console.out.println("VALUE " + runtime.lazyResource("managed-cache/lazy.txt"))
        Console.out.flush()
        command = input.readLine()
      }
    } finally runtime.close()
  }
}
