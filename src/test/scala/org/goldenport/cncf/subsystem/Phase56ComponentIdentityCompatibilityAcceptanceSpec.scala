package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import java.util.Comparator

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.goldenport.Consequence
import org.goldenport.cncf.component.{CarExtractor, ComponentId}
import org.goldenport.cncf.component.testutil.LegacyDeferredReleaseCarFixture
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.workarea.WorkAreaSpace

/*
 * @since   Aug.  8, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityCompatibilityAcceptanceSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private val _work_root = Path.of(
    "target",
    "cncf-test",
    "work",
    "phase56-component-identity-compatibility-acceptance"
  ).toAbsolutePath.normalize

  override protected def beforeAll(): Unit = {
    Files.createDirectories(_work_root)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    try _delete_recursively(_work_root)
    finally super.afterAll()
  }

  "Phase 56 Component identity compatibility retirement" should {
    "preserve strict archive-boundary ordering" which {
    "reject an exact former deferred Corpus CAR before caller component use" in {
      _with_work_directory { root =>
        Given("an exact schema-2 Corpus 0.1.0 CAR with ABI document v1 and no packaged runtime manifest")
        val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(
          root.resolve("expanded-textus-corpus-0.1.0")
        )
        val packed = LegacyDeferredReleaseCarFixture.pack(
          expanded,
          root.resolve("textus-corpus-0.1.0.car")
        )
        val before = Files.readAllBytes(packed).toVector
        var callerused = false

        When("the packed CAR reaches framework activation")
        val rejected = CarExtractor.withExtracted(
          packed,
          WorkAreaSpace.create(RuntimeConfig.default)
        ) { _ =>
          callerused = true
          Consequence.success(())
        }

        Then("activation rejects the legacy CAR before ClassLoader, component, routing, provenance, warning, or archive mutation")
        rejected.toOption shouldBe empty
        callerused shouldBe false
        ComponentId.parseC("Corpus").toOption shouldBe empty
        Files.readAllBytes(packed).toVector shouldBe before
      }
    }

    "reject the expanded former deferred CAR before any caller or lifecycle action" in {
      _with_work_directory { root =>
        Given("the expanded schema-2 Corpus fixture and a caller that would start runtime work")
        val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(
          root.resolve("expanded-textus-corpus-0.1.0")
        )
        var callerused = false

        When("the expanded archive is submitted for extraction")
        val rejected = CarExtractor.withExtracted(
          expanded,
          WorkAreaSpace.create(RuntimeConfig.default)
        ) { _ =>
          callerused = true
          Consequence.success(())
        }

        Then("canonical admission fails before component, ClassLoader, or shutdown lifecycle ownership exists")
        rejected.toOption shouldBe empty
        callerused shouldBe false
        ComponentId.parseC(LegacyDeferredReleaseCarFixture.legacyLocalId).toOption shouldBe empty
      }
    }

    "keep ComponentId parsing strict across a caller thread boundary" in {
      Given("the retired bare Corpus local ID")
      var threaded: Option[ComponentId] = Some(ComponentId("org.example.Placeholder"))
      val thread = new Thread(() => {
        threaded = ComponentId.parseC(LegacyDeferredReleaseCarFixture.legacyLocalId).toOption
      })

      When("the same strict parser is evaluated by a worker thread")
      val caller = ComponentId.parseC(LegacyDeferredReleaseCarFixture.legacyLocalId).toOption
      thread.start()
      thread.join()

      Then("neither caller nor worker receives ambient deferred-release identity adaptation")
      caller shouldBe empty
      threaded shouldBe empty
    }
  }

  }

  private def _with_work_directory[A](body: Path => A): A = {
    val root = Files.createTempDirectory(_work_root, "acceptance-")
    try body(root)
    finally _delete_recursively(root)
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
}
