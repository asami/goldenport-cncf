package org.goldenport.cncf.component.cache

import java.io.{BufferedReader, FilterOutputStream, InputStreamReader, OutputStream, PrintWriter}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{AssemblyApiClassLoader, AssemblyApiMetadata, ComponentDescriptorLoader}
import org.goldenport.cncf.component.repository.{CanonicalArchiveFixtureComponent, ComponentRepository}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/** Ordinary I/O, cheap reuse and explicitly bounded managed collection.
  * Quota-only examples are superseded; prior native receipts retain their old scope.
  * @since   Oct. 22, 2026
  * @version Oct. 22, 2026
  */
class ManagedCacheRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ManagedCarCacheFixture.{withCacheFixture, archive, Holder, fileIdentity}
  import ManagedCacheRecoveryFixture.*

  "Managed cache recovery and operations" should {
    "eager repository metadata source protection" which {
      Vector(ArchiveKind.Car -> "A", ArchiveKind.Sar -> "B").foreach { case (kind, suffix) =>
        Vector(false -> "1", true -> "2").foreach { case (generation, location) =>
          Vector("success" -> "1", "failure" -> "2", "throw" -> "3").foreach { case (outcome, completion) =>
            val example = s"E17$suffix$location$completion"
            s"$example protect ${kind.directory} ${if (generation) "generation" else "coordinate"} metadata until $outcome completes" must
              afterWord(s"in spec:car-sar-cache-management, example:$example, rules:C3,C5,C6, phase:116") {
              "keep the actual eager reader usable and release its protection on every completion" in withCacheFixture { root =>
                Given(s"Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5,C6; Example: $example; a managed ${kind.directory} and an eager reader paused before ZIP open")
                val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L, evictReusableSources = true))
                val coordinate = cache.root.resolve(kind.directory).resolve("metadata" + kind.suffix)
                Files.createDirectories(coordinate.getParent)
                metadataArchive(coordinate, kind, root)
                val selected = if (generation) {
                  val source = cache.acquireSourceC(coordinate, kind).TAKE
                  try source.path finally source.close()
                } else coordinate
                val original = Files.readAllBytes(selected).toVector
                val entered = new CountDownLatch(1)
                val release = new CountDownLatch(1)
                val loaded = new AtomicReference[AssemblyApiMetadata]()
                val executor = Executors.newSingleThreadExecutor()
                val expected = Consequence.resourceInvalid[AssemblyApiMetadata]("metadata callback failure")
                try {
                  When("collection and coordinate publication encounter the paused metadata reader")
                  val reader = executor.submit(new Callable[Consequence[AssemblyApiMetadata]] {
                    def call(): Consequence[AssemblyApiMetadata] = ComponentRepository.withArchiveSourceReadC(selected, Some(cache.root)) { path =>
                      entered.countDown()
                      require(release.await(30L, TimeUnit.SECONDS), "metadata reader release timed out")
                      readMetadata(path, kind).flatMap { metadata =>
                        loaded.set(metadata)
                        outcome match {
                          case "failure" => expected
                          case "throw" => throw new IllegalStateException("metadata callback exception")
                          case _ => Consequence.success(metadata)
                        }
                      }
                    }
                  })
                  entered.await(30L, TimeUnit.SECONDS) shouldBe true
                  val active = cache.collectC().TAKE
                  Then("the selected source remains in use and its original bytes survive")
                  active.operations.exists(entry => entry.pool == (if (generation) "source" else "coordinate") &&
                    entry.disposition == CacheCollectionDisposition.InUse) shouldBe true
                  Files.readAllBytes(selected).toVector shouldBe original
                  if (!generation) {
                    val refused = cache.publishSourceC(coordinate, kind)(path => { Files.writeString(path, "replacement"); () })
                    refused.isSuccess shouldBe false
                    refused.toString should include("publish-coordinate")
                    Files.readAllBytes(selected).toVector shouldBe original
                  }
                  When("the callback opens the real ZIP and completes")
                  release.countDown()
                  val result = reader.get(30L, TimeUnit.SECONDS)
                  Then("actual metadata is eager and the pin is released for success, failure or exception")
                  withClue(s"metadata reader result: $result; ") {
                    loaded.get() should not be null
                    loaded.get().artifacts should not be empty
                  }
                  loaded.get().artifacts.head.contract.apiClass shouldBe classOf[CanonicalArchiveFixtureComponent].getName
                  loaded.get().artifacts.head.classes(classOf[CanonicalArchiveFixtureComponent].getName).nonEmpty shouldBe true
                  outcome match {
                    case "failure" => result shouldBe expected
                    case "throw" => result.isSuccess shouldBe false; result.toString should include("metadata callback exception")
                    case _ => result.TAKE.artifacts shouldBe loaded.get().artifacts
                  }
                  if (!generation) {
                    cache.publishSourceC(coordinate, kind)(path => { Files.write(path, original.toArray); () }).isSuccess shouldBe true
                  }
                  val reclaimed = Vector.fill(4)(cache.collectC().TAKE)
                  reclaimed.flatMap(_.operations).exists(_.disposition == CacheCollectionDisposition.Reclaimed) shouldBe true
                  Files.exists(selected) shouldBe false
                  loaded.get().artifacts.head.classes(classOf[CanonicalArchiveFixtureComponent].getName).nonEmpty shouldBe true
                } finally {
                  release.countDown()
                  executor.shutdownNow()
                  executor.awaitTermination(10L, TimeUnit.SECONDS)
                  cache.close()
                }
              }
            }
          }
        }
      }

      Vector(ArchiveKind.Car -> "E18A", ArchiveKind.Sar -> "E18B").foreach { case (kind, example) =>
        s"$example protect a metadata read in another JVM through release and termination" must
          afterWord(s"in spec:car-sar-cache-management, example:$example, rules:C3,C5,C6, phase:116") {
          "exclude collection while the actual repository reader owns its source pin" in withCacheFixture { root =>
            Given(s"Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5,C6; Example: $example; a test-owned metadata-reader JVM and a separate collecting JVM")
            val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L, evictReusableSources = true))
            val coordinate = cache.root.resolve(kind.directory).resolve("metadata" + kind.suffix)
            Files.createDirectories(coordinate.getParent)
            metadataArchive(coordinate, kind, root)
            val child = new MetadataProcess(cache.root, coordinate, kind, root.resolve("metadata-reader.log"))
            try {
              When("the parent collects while the other JVM is paused before ZIP open")
              child.ready()
              val active = cache.collectC().TAKE
              Then("cross-process shared ownership keeps the coordinate readable")
              active.operations.exists(entry => entry.pool == "coordinate" && entry.disposition == CacheCollectionDisposition.InUse) shouldBe true
              When("the child reads the archive and releases its callback")
              child.readAndRelease() shouldBe classOf[CanonicalArchiveFixtureComponent].getName
              Then("the reader terminates normally and collection can reclaim")
              cache.collectC().TAKE.operations.exists(entry => entry.pool == "coordinate" && entry.disposition == CacheCollectionDisposition.Reclaimed) shouldBe true
              Files.exists(coordinate) shouldBe false
            } finally { child.close(); cache.close() }
          }
        }
      }
    }

    "E19 eager metadata protection through directory aliases" which {
      Vector(ArchiveKind.Car -> "A", ArchiveKind.Sar -> "B").foreach { case (kind, suffix) =>
        Vector(false -> "1", true -> "2").foreach { case (generation, location) =>
          Vector("alias-root-alias-path" -> "1", "real-root-alias-path" -> "2",
            "alias-root-real-path" -> "3", "initialized-alias-path" -> "4").foreach { case (route, variant) =>
            val example = s"E19$suffix$location$variant"
            s"$example protect ${kind.directory} ${if (generation) "generation" else "coordinate"} metadata through $route" must
              afterWord(s"in spec:car-sar-cache-management, example:$example, rules:C3,C5,C6, phase:116") {
              "preserve original reader provenance while collection uses the real root" in withCacheFixture { root =>
                Given(s"Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5,C6; Example: $example; a local cache directory alias and an actual eager reader using $route")
                val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L, evictReusableSources = true))
                try {
                  Files.createDirectories(cache.root.resolve("locks/generations"))
                  val alias = Files.createSymbolicLink(root.resolve("cache-alias"), cache.root)
                  val coordinate = cache.root.resolve(kind.directory).resolve("metadata" + kind.suffix)
                  Files.createDirectories(coordinate.getParent)
                  metadataArchive(coordinate, kind, root)
                  val actual = if (generation) {
                    val source = cache.acquireSourceC(coordinate, kind).TAKE
                    try source.path finally source.close()
                  } else coordinate
                  val selected = if (route == "alias-root-real-path") actual else alias.resolve(cache.root.relativize(actual))
                  val selectedroot = route match {
                    case "real-root-alias-path" => Some(cache.root)
                    case "initialized-alias-path" => None
                    case _ => Some(alias)
                  }
                  val original = Files.readAllBytes(actual).toVector
                  val entered = new CountDownLatch(1)
                  val release = new CountDownLatch(1)
                  val observed = new AtomicReference[Path]()
                  val provenance = new AtomicReference[Path]()
                  val executor = Executors.newSingleThreadExecutor()
                  try {
                    When("the alias-selected reader pauses after protection and a collector uses the real root")
                    val reader = executor.submit(new Callable[Consequence[AssemblyApiMetadata]] {
                      def call(): Consequence[AssemblyApiMetadata] = ComponentRepository.withArchiveSourceReadC(selected, selectedroot) { path =>
                        observed.set(path)
                        entered.countDown()
                        require(release.await(30L, TimeUnit.SECONDS), "alias metadata reader release timed out")
                        val descriptor = if (kind == ArchiveKind.Car) GenericSubsystemDescriptor.loadComponentArchive(path)
                          else GenericSubsystemDescriptor.load(path)
                        descriptor.flatMap { value =>
                          provenance.set(value.path)
                          readMetadata(path, kind)
                        }
                      }
                    })
                    entered.await(30L, TimeUnit.SECONDS) shouldBe true
                    val active = cache.collectC().TAKE
                    Then("the selected coordinate or generation is in use and the callback retains its original argument")
                    observed.get() shouldBe selected
                    active.operations.exists(entry => entry.pool == (if (generation) "source" else "coordinate") &&
                      entry.disposition == CacheCollectionDisposition.InUse) shouldBe true
                    Files.readAllBytes(actual).toVector shouldBe original
                    When("the callback reads the actual metadata ZIP and releases its protection")
                    release.countDown()
                    val metadata = reader.get(30L, TimeUnit.SECONDS).TAKE
                    Then("eager metadata and descriptor provenance preserve the selected path")
                    provenance.get() shouldBe selected
                    metadata.artifacts.head.contract.apiClass shouldBe classOf[CanonicalArchiveFixtureComponent].getName
                    metadata.artifacts.head.classes(classOf[CanonicalArchiveFixtureComponent].getName).nonEmpty shouldBe true
                    When("ordinary collection resumes against the same real root")
                    val reclaimed = Vector.fill(4)(cache.collectC().TAKE)
                    Then("released source bytes are reclaimed while eager class bytes remain usable")
                    reclaimed.flatMap(_.operations).exists(_.disposition == CacheCollectionDisposition.Reclaimed) shouldBe true
                    Files.exists(actual) shouldBe false
                    metadata.artifacts.head.classes(classOf[CanonicalArchiveFixtureComponent].getName).nonEmpty shouldBe true
                  } finally {
                    release.countDown()
                    executor.shutdownNow()
                    executor.awaitTermination(10L, TimeUnit.SECONDS)
                  }
                } finally cache.close()
              }
            }
          }
        }
      }
    }

    "actual native protection" which {
      "E1 preserve a live JVM after another holder dies, then collect after the final death" must afterWord("in spec:car-sar-cache-management, example:E1, rules:C3,C5, phase:116") {
        "preserve a live JVM after another holder dies, then collect after the final death" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5; Example: E1; two test-owned JVMs using the same repository CAR")
          val repository = Files.createDirectory(root.resolve("repository"))
          archive(repository.resolve("fixture.car"), root)
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L))
          val first = new Holder(repository, cache.root, root.resolve("first.log"))
          val second = new Holder(repository, cache.root, root.resolve("second.log"))
          try {
            first.tree shouldBe second.tree
            When("one JVM dies without runtime cleanup")
            first.hardDeath()
            Then("the survivor's lease protects lazy loading and collection skips its tree")
            cache.collectC().TAKE.inUseCount should be > 0
            second.load() shouldBe "lazy payload"
            When("the final holder dies")
            second.hardDeath()
            Then("native exclusion permits collection and stable lock files remain")
            cache.collectC().TAKE.reclaimedCount shouldBe 1
            Files.exists(second.tree) shouldBe false
            regularFiles(cache.root.resolve("locks/generations")).nonEmpty shouldBe true
          } finally { first.close(); second.close(); cache.close() }
        }
      }

      "E2 recover an interrupted ordinary publisher after its test-owned JVM dies" must afterWord("in spec:car-sar-cache-management, example:E2, rules:C2,C3,C5, phase:116") {
        "recover an interrupted ordinary publisher after its test-owned JVM dies" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3,C5; Example: E2; an original coordinate and another JVM paused during an ordinary staged write")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val coordinate = cache.root.resolve("car/current.car")
          cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "original"); () })
          val child = new StageProcess(cache.root, root.resolve("publisher.log"))
          try {
            val directory = child.directory
            When("collection encounters the live writer")
            val live = cache.collectC().TAKE
            Then("native ownership protects incomplete output without exposing it as current")
            live.operations.exists(entry => entry.pool == "staging" && entry.disposition == CacheCollectionDisposition.InUse) shouldBe true
            Files.readString(coordinate) shouldBe "original"
            When("the owned writer JVM dies without executing its cleanup callback")
            child.close()
            val recovered = cache.collectC().TAKE
            Then("ordinary collection acquires the released pin and removes only the abandoned stage")
            recovered.operations.exists(entry => entry.pool == "staging" && entry.disposition == CacheCollectionDisposition.Reclaimed) shouldBe true
            Files.exists(directory) shouldBe false
            Files.readString(coordinate) shouldBe "original"
          } finally { child.close(); cache.close() }
        }
      }

      "E3 recover abandoned staging only after its owner releases the stable pin" must afterWord("in spec:car-sar-cache-management, example:E3, rules:C2,C3,C5, phase:116") {
        "recover abandoned staging only after its owner releases the stable pin" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C3,C5; Example: E3; staging protected before metadata or payload creation")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val stage = new InterruptedStage(cache.root, cache.policy, 37)
          try {
            When("explicit collection encounters a live producer")
            val live = cache.collectC().TAKE
            Then("the stage remains protected")
            live.inUseCount shouldBe 1
            live.operations.head.bytesKnown shouldBe false
            cache.inspectC().TAKE.bytesKnown shouldBe false
            Files.exists(stage.directory) shouldBe true
            When("the producer loses its pin and dry-run inspects the same work")
            stage.interrupt()
            val before = snapshot(cache.root)
            val dry = cache.collectC(true).TAKE
            Then("dry-run leaves payload and metadata unchanged")
            dry.operations.exists(_.disposition == CacheCollectionDisposition.WouldReclaim) shouldBe true
            dry.operations.forall(_.bytesKnown) shouldBe true
            snapshot(cache.root) shouldBe before
            When("ordinary collection follows")
            val collected = cache.collectC().TAKE
            Then("only the abandoned work is deleted")
            collected.operations.exists(_.reason == "abandoned staging") shouldBe true
            Files.exists(stage.directory) shouldBe false
          } finally { stage.close(); cache.close() }
        }
      }
    }

    "ordinary I/O and recoverable failures" which {
      "E4 preserve current bytes when a write fails and retain cleanup failures as suppressed" must afterWord("in spec:car-sar-cache-management, example:E4, rules:C2,C5, phase:116") {
        "preserve current bytes when a write fails and retain cleanup failures as suppressed" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C5; Example: E4; an existing coordinate, a failing write and partial stage cleanup")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
          val coordinate = cache.root.resolve("car/current.car")
          cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "original"); () })
          val original = fileIdentity(coordinate)
          val failure = new java.io.IOException("injected disk full")
          files.failCleanup = true
          When("a replacement write fails and cleanup cannot finish")
          val caught = intercept[CacheFailure] {
            cache.publishSource(coordinate, ArchiveKind.Car) { path =>
              Files.writeString(path, "partial")
              throw failure
            }
          }
          Then("the original cause and cleanup failure survive, while current bytes remain usable")
          caught.reason shouldBe CacheFailure.Reason.IoFailure
          caught.getCause shouldBe failure
          caught.getSuppressed.nonEmpty shouldBe true
          fileIdentity(coordinate) shouldBe original
          Files.readString(coordinate) shouldBe "original"
          cache.inspectC().TAKE.stagingBytes should be > 0L
          When("cleanup becomes available")
          files.failCleanup = false
          val result = cache.collectC().TAKE
          Then("ordinary recovery removes the partial stage without affecting the coordinate")
          result.failedCount shouldBe 0
          cache.inspectC().TAKE.stagingBytes shouldBe 0L
          Files.readString(coordinate) shouldBe "original"
          cache.close()
        }
      }

      "E5 keep identical current bytes after failure before rename, without blocking another producer" must afterWord("in spec:car-sar-cache-management, example:E5, rules:C2,C5, phase:116") {
        "keep identical current bytes after failure before rename, without blocking another producer" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C5; Example: E5; the old E22 publication/partial-cleanup scenario without a strict quota")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), sourceMaxBytes = 1L), files)
          val coordinate = cache.root.resolve("car/current.car")
          cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "same"); () })
          val original = fileIdentity(coordinate)
          files.failBeforeRename = true
          files.failCleanup = true
          When("identical replacement fails before atomic rename")
          intercept[CacheFailure] {
            cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "same"); () })
          }.reason shouldBe CacheFailure.Reason.PublicationFailed
          Then("the previous publication remains and staging is explicitly recoverable")
          fileIdentity(coordinate) shouldBe original
          cache.inspectC().TAKE.stagingBytes should be > 0L
          files.failCleanup = false
          When("another producer writes ordinary bytes despite the soft target")
          cache.publishSource(cache.root.resolve("car/other.car"), ArchiveKind.Car)(path => { Files.writeString(path, "other"); () })
          Then("no capacity reservation or policy binding refuses that write")
          Files.readString(cache.root.resolve("car/other.car")) shouldBe "other"
          cache.collectC().TAKE.failedCount shouldBe 0
          cache.close()
        }
      }

      "E6 preserve successful rename even when publication reports failure afterward" must afterWord("in spec:car-sar-cache-management, example:E6, rules:C2,C5, phase:116") {
        "preserve successful rename even when publication reports failure afterward" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C5; Example: E6; the old E21 post-rename interruption with retained current bytes")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
          val coordinate = cache.root.resolve("car/current.car")
          cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "old"); () })
          files.failAfterRename = true
          When("rename succeeds before the publisher reports an interruption")
          intercept[CacheFailure] {
            cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.writeString(path, "new"); () })
          }
          Then("cleanup cannot delete the successful destination and repeated recovery keeps it")
          Files.readString(coordinate) shouldBe "new"
          Vector.fill(3)(cache.collectC().TAKE).foreach(_.failedCount shouldBe 0)
          Files.readString(coordinate) shouldBe "new"
          cache.close()
        }
      }

      "E7 not publish an incomplete capture when its actual output stream fails" must afterWord("in spec:car-sar-cache-management, example:E7, rules:C2,C5, phase:116") {
        "not publish an incomplete capture when its actual output stream fails" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C5; Example: E7; a real CAR and an injected ordinary output-stream failure")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
          val car = archive(root.resolve("fixture.car"), root)
          files.failOutput = true
          When("capture writes its first bytes")
          val result = cache.acquireCarC(car)
          Then("the cause is retained and no complete source or extraction is returned")
          result.isSuccess shouldBe false
          result.toString should include("injected write failure")
          cache.inspectC().TAKE.extractedBytes shouldBe 0L
          files.failOutput = false
          When("the same ordinary acquisition is tried after storage works")
          val lease = cache.acquireCarC(car).TAKE
          Then("a complete usable tree is published")
          Files.isDirectory(lease.value.root) shouldBe true
          lease.close()
          cache.close()
        }
      }

      "E8 reject failed extraction without exposing a partial hit, then accept a complete replacement" must afterWord("in spec:car-sar-cache-management, example:E8, rules:C2,C5,C6, phase:116") {
        "reject failed extraction without exposing a partial hit, then accept a complete replacement" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C5,C6; Example: E8; an archive rejected by the existing extractor's path-safety checks")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val car = root.resolve("fixture.car")
          Using.resource(new ZipOutputStream(Files.newOutputStream(car))) { zip =>
            zip.putNextEntry(new ZipEntry("../../escaped.txt"))
            zip.write(Array[Byte](1))
            zip.closeEntry()
          }
          When("ordinary acquisition attempts extraction")
          val rejected = cache.acquireCarC(car)
          Then("no completed extraction or escaped output is visible")
          rejected.isSuccess shouldBe false
          cache.inspectC().TAKE.extractedBytes shouldBe 0L
          regularFiles(cache.root.resolve("extracted")).exists(_.getFileName.toString == "entry.json") shouldBe false
          regularFiles(root).exists(_.getFileName.toString == "escaped.txt") shouldBe false
          When("the caller replaces its archive with a valid CAR")
          archive(car, root)
          val lease = cache.acquireCarC(car).TAKE
          Then("controlled ingestion and the same extractor publish a usable replacement")
          Files.isDirectory(lease.value.root) shouldBe true
          lease.close()
          cache.close()
        }
      }

      "E9 make partial payload deletion visible and retry it under the same generation exclusion" must afterWord("in spec:car-sar-cache-management, example:E9, rules:C3,C5, phase:116") {
        "make partial payload deletion visible and retry it under the same generation exclusion" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C5; Example: E9; an unused extraction and a selected deletion that fails")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L), files)
          val car = archive(root.resolve("fixture.car"), root)
          val lease = cache.acquireCarC(car).TAKE
          val tree = lease.value.root
          lease.close()
          files.failAt = Some(tree.getParent)
          When("explicit collection fails to remove the selected generation")
          val failed = cache.collectC().TAKE
          Then("the failure is visible and the entry has been unpublished")
          failed.failedCount shouldBe 1
          Files.exists(tree.getParent.resolve("entry.json")) shouldBe false
          When("the next collection retries the leftover")
          val recovered = cache.collectC().TAKE
          Then("the same payload is reclaimed without deleting stable locks")
          recovered.failedCount shouldBe 0
          Files.exists(tree) shouldBe false
          regularFiles(cache.root.resolve("locks/generations")).nonEmpty shouldBe true
          cache.close()
        }
      }
    }

    "cheap completed generations and bounded diagnostics" which {
      "E10 reuse protected immutable bytes without copying, hashing, walking or identical metadata writes" must afterWord("in spec:car-sar-cache-management, example:E10, rules:C2,C6, phase:116") {
        "reuse protected immutable bytes without copying, hashing, walking or identical metadata writes" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C2,C6; Example: E10; an immutable managed source and its completed extraction")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
          val car = archive(root.resolve("fixture.car"), root)
          val source = cache.acquireSourceC(car).TAKE
          val prepared = cache.acquireCarC(source.path).TAKE
          val tree = prepared.value.root
          prepared.close()
          files.reset()
          When("the same managed generation is acquired again")
          val reused = cache.acquireCarC(source.path).TAKE
          Then("targeted protection and metadata validation reuse its identity without bulk work")
          reused.value.root shouldBe tree
          files.inputReads shouldBe 0
          files.outputWrites shouldBe 0
          files.metadataWrites shouldBe 0
          files.visits shouldBe 0
          reused.close()
          source.close()
          cache.close()
        }
      }

      "E11 repair missing references without rewriting already satisfied reference metadata" must afterWord("in spec:car-sar-cache-management, example:E11, rules:C3,C6, phase:116") {
        "repair missing references without rewriting already satisfied reference metadata" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C3,C6; Example: E11; a completed managed generation whose own coordinate reference is missing")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")), files)
          val car = archive(root.resolve("fixture.car"), root)
          val source = cache.acquireSourceC(car).TAKE
          val initial = cache.acquireCarC(source.path).TAKE
          initial.close()
          val selection = new ManagedGenerationSelection(cache.root, cache.policy)
          val coordinate = selection.coordinate(source.path, ArchiveKind.Car)
          val reference = cache.root.resolve("metadata/generations").resolve(source.generation.key).resolve("references").resolve(coordinate)
          Files.delete(reference)
          files.reset()
          When("the existing generation is acquired")
          val repaired = cache.acquireCarC(source.path).TAKE
          Then("only the required missing relationship is repaired")
          Files.isRegularFile(reference) shouldBe true
          files.metadataWrites shouldBe 1
          repaired.close()
          source.close()
          cache.close()
        }
      }

      "E12 bound discovery before materialization and carry an unfinished scan to later collection" must afterWord("in spec:car-sar-cache-management, example:E12, rules:C5, phase:116") {
        "bound discovery before materialization and carry an unfinished scan to later collection" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C5; Example: E12; hundreds of unrelated coordinates, a small exploration limit and an existing completed hit")
          val files = new ObservedFiles
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), collectionMaxEntries = 16), files)
          val car = archive(root.resolve("fixture.car"), root)
          val source = cache.acquireSourceC(car).TAKE
          val initial = cache.acquireCarC(source.path).TAKE
          initial.close()
          Files.createDirectories(cache.root.resolve("car"))
          (0 until 200).foreach(index => Files.writeString(cache.root.resolve("car").resolve(index.toString + ".car"), "body"))
          files.reset()
          When("the completed generation is acquired beside that unrelated cache")
          val hit = cache.acquireCarC(source.path).TAKE
          Then("hit work performs zero inventory visits")
          files.visits shouldBe 0
          hit.close()
          When("explicit diagnostics and repeated collection explore bounded pages")
          val inspected = cache.inspectC().TAKE
          val pages = Vector.fill(30)(cache.collectC().TAKE)
          Then("each call bounds actual visited paths, reports partial totals and eventually reaches every coordinate")
          inspected.truncated shouldBe true
          pages.forall(_.visitedPaths <= 16) shouldBe true
          pages.exists(_.truncated) shouldBe true
          pages.flatMap(_.operations.filter(_.pool == "coordinate").map(_.entry)).distinct.size shouldBe 200
          source.close()
          cache.close()
        }
      }

      "E13 carry already discovered candidates when the reclamation byte target ends a page" must afterWord("in spec:car-sar-cache-management, example:E13, rules:C5, phase:116") {
        "carry already discovered candidates when the reclamation byte target ends a page" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C5; Example: E13; two unused generations and a collection byte target smaller than either tree")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), idleMillis = 0L,
            collectionMaxBytes = 1L))
          val first = cache.acquireCarC(archive(root.resolve("first.car"), root, "first")).TAKE
          val second = cache.acquireCarC(archive(root.resolve("second.car"), root, "second")).TAKE
          val trees = Set(first.value.root, second.value.root)
          first.close()
          second.close()
          When("one page reclaims its first tree and the next collection resumes")
          val page = cache.collectC().TAKE
          val continued = cache.collectC().TAKE
          Then("the undispatched candidate is reclaimed without restarting discovery")
          page.reclaimedCount shouldBe 1
          page.truncated shouldBe true
          continued.reclaimedCount shouldBe 1
          continued.visitedPaths shouldBe 0
          trees.foreach(path => Files.exists(path) shouldBe false)
          cache.close()
        }
      }

      "E14 accept changed soft targets and ignore old reservation policy metadata" must afterWord("in spec:car-sar-cache-management, example:E14, rules:C5, phase:116") {
        "accept changed soft targets and ignore old reservation policy metadata" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C5; Example: E14; healthy completed bytes and a leftover old policy file")
          val car = archive(root.resolve("fixture.car"), root)
          val first = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val held = first.acquireCarC(car).TAKE
          val tree = held.value.root
          Files.createDirectories(first.root.resolve("metadata"))
          val oldpolicy = first.root.resolve("metadata/policy.json")
          Files.writeString(oldpolicy, "old policy must not be interpreted")
          When("another owner uses a smaller soft target")
          val second = new ManagedArchiveCache(first.policy.copy(sourceMaxBytes = 1L, extractedMaxBytes = 1L))
          val reused = second.acquireCarC(car).TAKE
          Then("healthy reuse succeeds and old metadata is not removed")
          reused.value.root shouldBe tree
          Files.readString(oldpolicy) shouldBe "old policy must not be interpreted"
          reused.close()
          held.close()
          first.close()
          second.close()
        }
      }

      "E15 preserve mutable external source ingestion across changed bodies" must afterWord("in spec:car-sar-cache-management, example:E15, rules:C1,C2, phase:116") {
        "preserve mutable external source ingestion across changed bodies" in withCacheFixture { root =>
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C1,C2; Example: E15; an external mutable archive")
          val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache")))
          val body = root.resolve("external.car")
          Files.writeString(body, "first")
          val first = cache.acquireSourceC(body).TAKE
          When("external bytes change")
          Files.writeString(body, "second")
          val second = cache.acquireSourceC(body).TAKE
          Then("a new immutable generation is captured while the old reader remains usable")
          second.generation should not be first.generation
          Files.readString(first.path) shouldBe "first"
          Files.readString(second.path) shouldBe "second"
          first.close()
          second.close()
          cache.close()
        }
      }

      "E16 use soft targets for reclamation rather than write admission under generated payload sizes" must afterWord("in spec:car-sar-cache-management, example:E16, rules:C5, phase:116") {
        "use soft targets for reclamation rather than write admission under generated payload sizes" in {
          Given("Spec: docs/spec/car-sar-cache-management.md; Rules: C5; Example: E16; generated payloads larger than a collection target")
          val property = Prop.forAllNoShrink(Gen.choose(2, 96)) { size => withCacheFixture { root =>
            val cache = new ManagedArchiveCache(ManagedCachePolicy(root.resolve("cache"), sourceMaxBytes = 1L))
            try {
              When("ordinary publication writes more bytes than the target")
              val coordinate = cache.root.resolve("car/fixture.car")
              cache.publishSource(coordinate, ArchiveKind.Car)(path => { Files.write(path, Array.fill[Byte](size)(7)); () })
              Then("all bytes are published, with no capacity-only metadata")
              Files.size(coordinate) shouldBe size.toLong
              Files.exists(cache.root.resolve("metadata/reservations")) shouldBe false
              Files.exists(cache.root.resolve("locks/capacity")) shouldBe false
              true
            } finally cache.close()
          } }
          Test.check(Test.Parameters.default.withMinSuccessfulTests(8).withWorkers(1), property).passed shouldBe true
        }
      }
    }
  }
}

private[cache] object ManagedCacheRecoveryFixture {
  def metadataArchive(target: Path, kind: ArchiveKind, assets: Path): Path = {
    if (kind == ArchiveKind.Sar) {
      val car = Files.createTempFile(assets, "metadata-child-", ".car")
      metadataArchive(car, ArchiveKind.Car, assets)
      Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
        zip.putNextEntry(new ZipEntry("subsystem-descriptor.json"))
        zip.write("""{"name":"metadata","version":"1.0.0","subsystem":"metadata","components":[{"namespace":"org.goldenport.fixture","id":"CanonicalArchiveFixture","version":"0.1.0"}]}""".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(new ZipEntry("component/metadata.car"))
        Files.copy(car, zip)
        zip.closeEntry()
      }
    } else {
      val jar = Files.createTempFile(assets, "metadata-api-", ".jar")
      val classname = classOf[CanonicalArchiveFixtureComponent].getName
      val entry = classname.replace('.', '/') + ".class"
      Using.resource(new ZipOutputStream(Files.newOutputStream(jar))) { zip =>
        zip.putNextEntry(new ZipEntry(entry))
        Using.resource(classOf[CanonicalArchiveFixtureComponent].getClassLoader.getResourceAsStream(entry))(_.transferTo(zip))
        zip.closeEntry()
      }
      val descriptor = Files.createTempFile(assets, "metadata-component-", ".json")
      Files.writeString(descriptor, """{"schemaVersion":3,"component":{"namespace":"org.goldenport.fixture","id":"CanonicalArchiveFixture","version":"0.1.0"},"subsystem":"metadata"}""")
      val api = Files.createTempFile(assets, "metadata-api-", ".json")
      Files.writeString(api, s"""{"schemaVersion":"cncf.component-api.v1","component":{"name":"org.goldenport.fixture.CanonicalArchiveFixture","version":"0.1.0"},"provided":[{"apiClass":"$classname","packages":["org.goldenport.cncf.component.repository"],"abiHash":"sha256:metadata","artifactPath":"spi/metadata-api.jar"}],"required":[]}""")
      org.goldenport.cncf.component.testutil.CarArchiveFixture.write(target, Vector(
        "component-descriptor.json" -> descriptor, "component-api-descriptor.json" -> api,
        "spi/metadata-api.jar" -> jar, "component/main.jar" -> jar))
    }
    target
  }

  def readMetadata(path: Path, kind: ArchiveKind): Consequence[AssemblyApiMetadata] =
    if (kind == ArchiveKind.Car)
      ComponentDescriptorLoader.loadArchive(path).flatMap { _ =>
        GenericSubsystemDescriptor.loadComponentArchive(path).flatMap(_ => AssemblyApiClassLoader.loadCar(path))
      }
    else GenericSubsystemDescriptor.load(path).flatMap(_ => AssemblyApiClassLoader.loadSar(path))

  final class MetadataProcess(root: Path, archive: Path, kind: ArchiveKind, stderr: Path) extends AutoCloseable {
    private val _process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString,
      "-cp", ManagedCarCacheFixture.runtimeClasspath(), "org.goldenport.cncf.component.cache.ManagedCacheMetadataHolder",
      root.toString, archive.toString, kind.directory).redirectError(stderr.toFile).start()
    private val _reader = new BufferedReader(new InputStreamReader(_process.getInputStream, java.nio.charset.StandardCharsets.UTF_8))
    private val _writer = new PrintWriter(_process.getOutputStream, true)
    private val _reads = Executors.newSingleThreadExecutor()
    private var _closed = false

    private def _line(prefix: String): String = try _reads.submit(new Callable[String] {
      def call(): String = {
        var line = _reader.readLine()
        while (line != null && !line.startsWith(prefix)) line = _reader.readLine()
        require(line != null, "metadata child ended before " + prefix + "; diagnostics: " + stderr)
        line.substring(prefix.length)
      }
    }).get(45L, TimeUnit.SECONDS) catch { case error: Throwable => close(); throw error }

    def ready(): Unit = { _line("READY "); () }
    def readAndRelease(): String = {
      _writer.println("READ")
      val result = _line("READ ")
      require(_process.waitFor(10L, TimeUnit.SECONDS), "metadata child did not terminate after release")
      require(_process.exitValue() == 0, "metadata child failed; diagnostics: " + stderr)
      result
    }
    def close(): Unit = if (!_closed) {
      _closed = true
      try {
        if (_process.isAlive) _process.destroyForcibly()
        require(_process.waitFor(10L, TimeUnit.SECONDS), "owned metadata JVM did not terminate")
      } finally { _writer.close(); _reader.close(); _reads.shutdownNow() }
    }
  }

  def regularFiles(root: Path): Vector[Path] = if (!Files.exists(root)) Vector.empty else
    Using.resource(Files.walk(root))(_.iterator().asScala.filter(Files.isRegularFile(_)).toVector)

  def snapshot(root: Path): Map[String, (Vector[Byte], Long)] = regularFiles(root).map(path =>
    root.relativize(path).toString -> (Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path).toMillis)).toMap

  final class StageProcess(root: Path, stderr: Path) extends AutoCloseable {
    private val _process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString,
      "-cp", ManagedCarCacheFixture.runtimeClasspath(), "org.goldenport.cncf.component.cache.ManagedCacheStageHolder",
      root.toString).redirectError(stderr.toFile).start()
    private val _reader = new BufferedReader(new InputStreamReader(_process.getInputStream, java.nio.charset.StandardCharsets.UTF_8))
    private val _reads = Executors.newSingleThreadExecutor()
    private var _closed = false
    lazy val directory: Path = try _reads.submit(new Callable[Path] {
      def call(): Path = {
        var line = _reader.readLine()
        while (line != null && !line.startsWith("READY ")) line = _reader.readLine()
        require(line != null, "publisher child ended before readiness; diagnostics: " + stderr)
        Path.of(line.substring(6))
      }
    }).get(45L, TimeUnit.SECONDS) catch { case error: Throwable => close(); throw error }
    def close(): Unit = if (!_closed) {
      _closed = true
      try {
        _process.destroyForcibly()
        require(_process.waitFor(10L, TimeUnit.SECONDS), "owned publisher JVM did not terminate")
        require(_process.exitValue() != 0, "hard-death publisher fixture exited normally")
      } finally { _reader.close(); _reads.shutdownNow() }
    }
  }

  final class InterruptedStage(root: Path, policy: ManagedCachePolicy, bytes: Int) extends AutoCloseable {
    val id: String = UUID.randomUUID().toString
    private val _pin = ManagedCacheLocks.acquire(root.resolve("locks/staging").resolve(id + ".lock"), false, policy)
    val directory: Path = Files.createDirectories(root.resolve("staging/2026/10").resolve(id))
    Files.createDirectory(directory.resolve("payload"))
    Files.write(directory.resolve("payload/body"), Array.fill[Byte](bytes)(1))
    def interrupt(): Unit = _pin.close()
    def close(): Unit = { _pin.close(); ManagedCacheFiles.deleteTree(directory) }
  }

  final class ObservedFiles extends ManagedCacheFiles {
    var inputReads = 0
    var outputWrites = 0
    var metadataWrites = 0
    var visits = 0
    var failCleanup = false
    var failBeforeRename = false
    var failAfterRename = false
    var failOutput = false
    var failAt: Option[Path] = None

    def reset(): Unit = { inputReads = 0; outputWrites = 0; metadataWrites = 0; visits = 0 }
    override def visited(path: Path): Unit = { visits += 1 }
    override def openInput(path: Path): java.io.InputStream = { inputReads += 1; super.openInput(path) }
    override def openOutput(path: Path): OutputStream = {
      outputWrites += 1
      val output = super.openOutput(path)
      new FilterOutputStream(output) {
        override def write(bytes: Array[Byte], offset: Int, length: Int): Unit =
          if (failOutput) throw new java.io.IOException("injected write failure") else out.write(bytes, offset, length)
      }
    }
    override def writeMetadata(path: Path, text: String): Unit = { metadataWrites += 1; super.writeMetadata(path, text) }
    def deleteTree(path: Path): Unit = {
      if (failCleanup && path.toString.contains("/staging/")) throw new java.io.IOException("injected cleanup failure")
      if (failAt.contains(path)) { failAt = None; throw new java.io.IOException("injected partial deletion") }
      ManagedCacheFiles.deleteTree(path)
    }
    override def publish(prepared: Path, destination: Path): Unit = {
      if (failBeforeRename) { failBeforeRename = false; throw new java.io.IOException("injected before rename") }
      super.publish(prepared, destination)
      if (failAfterRename) { failAfterRename = false; throw new java.io.IOException("injected after rename") }
    }
  }
}

object ManagedCacheStageHolder {
  def main(args: Array[String]): Unit = {
    val cache = new ManagedArchiveCache(ManagedCachePolicy(Path.of(args(0))))
    try cache.publishSource(cache.root.resolve("car/current.car"), ArchiveKind.Car) { prepared =>
      Files.writeString(prepared, "incomplete replacement")
      Console.out.println("READY " + prepared.getParent.getParent)
      Console.out.flush()
      System.in.read()
      ()
    } finally cache.close()
  }
}

object ManagedCacheMetadataHolder {
  def main(args: Array[String]): Unit = {
    val kind = ArchiveKind.values.find(_.directory == args(2)).get
    val result = ComponentRepository.withArchiveSourceReadC(Path.of(args(1)), Some(Path.of(args(0)))) { path =>
      Console.out.println("READY " + path)
      Console.out.flush()
      require(new BufferedReader(new InputStreamReader(System.in)).readLine() == "READ", "metadata read command missing")
      ManagedCacheRecoveryFixture.readMetadata(path, kind)
    }.TAKE
    Console.out.println("READ " + result.artifacts.head.contract.apiClass)
    Console.out.flush()
  }
}
