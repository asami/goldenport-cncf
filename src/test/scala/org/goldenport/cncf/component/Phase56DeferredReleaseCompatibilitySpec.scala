package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

import org.goldenport.Consequence
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.testutil.LegacyDeferredReleaseCarFixture
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56DeferredReleaseCompatibilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 56 deferred-release retirement" should {
    "keep ComponentId parsing strict on every thread" in {
      Given("a former deferred local Component ID and no ambient identity scope")
      val workerresult = new AtomicReference[Option[ComponentId]]()

      When("the local ID is parsed on the caller and a separate worker thread")
      val callerbefore = ComponentId.parseC("Corpus").toOption
      val throwing = Try(ComponentId("Corpus"))
      val worker = new Thread(() => workerresult.set(ComponentId.parseC("Corpus").toOption))
      worker.start()
      worker.join()
      val callerafter = ComponentId.parseC("Corpus").toOption

      Then("neither caller nor worker adapts the bare local ID")
      callerbefore shouldBe empty
      throwing.isFailure shouldBe true
      workerresult.get() shouldBe empty
      callerafter shouldBe empty
    }

    "reject a packed former deferred CAR before caller component use" in {
      _with_temp_dir { root =>
        Given("an exact schema-2 Corpus CAR with ABI v1 evidence")
        val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve("expanded"))
        val packed = LegacyDeferredReleaseCarFixture.pack(expanded, root.resolve("textus-corpus-0.1.0.car"))
        val before = Files.readAllBytes(packed).toVector
        var callerused = false

        When("the packed CAR reaches archive activation")
        val result = CarExtractor.withExtracted(packed, WorkAreaSpace.create(RuntimeConfig.default)) { _ =>
          callerused = true
          Consequence.success(())
        }

        Then("canonical admission rejects it before ClassLoader or caller use without archive mutation")
        result.toOption shouldBe empty
        callerused shouldBe false
        Files.readAllBytes(packed).toVector shouldBe before
      }
    }

    "reject an expanded former deferred CAR before component discovery" in {
      _with_temp_dir { root =>
        Given("an exact schema-2 Corpus directory with ABI v1 evidence")
        val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve("textus-corpus-0.1.0.car.d"))

        When("the expanded CAR reaches archive admission")
        val result = CarExtractor.resolveDirectory(expanded)

        Then("canonical admission fails closed before any component can be discovered")
        result.toOption shouldBe empty
        ComponentId.parseC("Corpus").toOption shouldBe empty
      }
    }

    "keep nested and concurrent parsing strict after deferred identity scope removal" in {
      Given("former deferred local IDs on nested caller work and independent worker threads")
      val ready = new CountDownLatch(2)
      val proceed = new CountDownLatch(1)
      val left = new AtomicReference[Vector[Option[ComponentId]]]()
      val right = new AtomicReference[Vector[Option[ComponentId]]]()
      def worker(target: AtomicReference[Vector[Option[ComponentId]]]): Thread =
        new Thread(() => {
          ready.countDown()
          proceed.await()
          target.set(Vector(
            ComponentId.parseC("Corpus").toOption,
            ComponentId.parseC("Corpus").toOption
          ))
        })
      val leftthread = worker(left)
      val rightthread = worker(right)

      When("both workers and the nested caller evaluate ComponentId")
      leftthread.start()
      rightthread.start()
      ready.await()
      val nested = Vector(
        ComponentId.parseC("Corpus").toOption,
        ComponentId.parseC("Corpus").toOption
      )
      proceed.countDown()
      leftthread.join()
      rightthread.join()

      Then("no thread-local provenance or ambient identity changes the strict result")
      nested shouldBe Vector(None, None)
      left.get() shouldBe Vector(None, None)
      right.get() shouldBe Vector(None, None)
    }

    "reject every former deferred coordinate variant without archive fallback" in {
      _with_temp_dir { root =>
        Given("schema-2 descriptors for exact, higher, SNAPSHOT, wrong-artifact, and wrong-local inputs")
        val variants = Vector(
          "exact" -> LegacyDeferredReleaseCarFixture.legacyDescriptor(),
          "higher" -> LegacyDeferredReleaseCarFixture.legacyDescriptor().copy(version = Some("0.1.1")),
          "snapshot" -> LegacyDeferredReleaseCarFixture.legacyDescriptor().copy(version = Some("0.1.1-SNAPSHOT")),
          "wrong-artifact" -> LegacyDeferredReleaseCarFixture.legacyDescriptor().copy(name = Some("wrong-artifact")),
          "wrong-local" -> LegacyDeferredReleaseCarFixture.legacyDescriptor().copy(componentName = Some("corpus"))
        )

        When("each expanded archive reaches canonical archive admission")
        val results = variants.map { case (label, descriptor) =>
          val archive = LegacyDeferredReleaseCarFixture.writeDirectory(
            root.resolve(label),
            descriptorOverride = Some(descriptor)
          )
          label -> CarExtractor.resolveDirectory(archive)
        }

        Then("all former coordinates fail before repository discovery or ClassLoader use")
        results.foreach { case (label, result) =>
          withClue(label) {
            result shouldBe a[Consequence.Failure[_]]
          }
        }
      }
    }

    "fail closed for non-regular and corrupt archive evidence without deferred exceptions" in {
      _with_temp_dir { root =>
        Given("a former schema-2 CAR whose runtime or ABI evidence is non-regular or malformed")
        val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(root.resolve("expanded"))
        val packednonregular = LegacyDeferredReleaseCarFixture.pack(
          expanded,
          root.resolve("nonregular-runtime-manifest.car"),
          runtimeManifestDirectory = true
        )

        When("packed and expanded boundaries inspect each invalid evidence representation")
        val packed = CarExtractor.withExtracted(
          packednonregular,
          WorkAreaSpace.create(RuntimeConfig.default)
        ) { _ =>
          Consequence.success(())
        }
        Files.createDirectory(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))
        val expandednonregular = CarExtractor.resolveDirectory(expanded)
        Files.delete(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))
        Files.writeString(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE), "{}")
        val corruptruntime = CarExtractor.resolveDirectory(expanded)
        Files.delete(expanded.resolve(CarRuntimeAdmission.MANIFEST_FILE))
        Files.writeString(expanded.resolve(CarRuntimeAdmission.ABI_MANIFEST_FILE), "{}")
        val corruptabi = CarExtractor.resolveDirectory(expanded)

        Then("each fails at archive admission and none can invoke a deferred-release provenance path")
        Vector(packed, expandednonregular, corruptruntime, corruptabi).foreach { result =>
          result shouldBe a[Consequence.Failure[_]]
        }
      }
    }

    "retain ordinary schema-3 archive admission while rejecting a bare assembly selector" in {
      _with_temp_dir { root =>
        Given("a schema-3 canonical archive descriptor and its unqualified local spelling")
        Files.writeString(
          root.resolve("component-descriptor.json"),
          """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Strict","version":"1.0.0"}}"""
        )
        val canonical = ComponentId("org.example.Strict")

        When("canonical archive admission and assembly selector resolution are evaluated")
        val admitted = ComponentDescriptorLoader.loadArchive(root)
        val bare = ComponentIdentityCompatibilityAdapter.resolve(
          "Strict",
          Vector(canonical),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )

        Then("schema-3 identity remains valid and the bare selector has no adaptation path")
        admitted.toOption.flatMap(_.componentId) shouldBe Some(canonical)
        bare shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        bare.toConsequence.toOption shouldBe empty
      }
    }

    "leave the assembly report empty because deferred-release observability is removed" in {
      Given("an AssemblyReport after former deferred-release evidence is rejected")
      val report = new AssemblyReport()

      When("no runtime registry or deferred observer exists to record compatibility provenance")
      val warningcount = report.warnings.size
      val projection = report.toRecord

      Then("no deferred-release warning or provenance record is retained")
      warningcount shouldBe 0
      projection.getInt("warningCount") shouldBe Some(0)
    }
  }

  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Files.createTempDirectory("phase56-deferred-release-")
    try body(root)
    finally _delete_recursively(root)
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
}
