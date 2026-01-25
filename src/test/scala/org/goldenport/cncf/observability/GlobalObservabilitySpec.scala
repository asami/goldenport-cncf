package org.goldenport.cncf.observability

import scala.collection.mutable.ListBuffer

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.goldenport.cncf.observability.global.{GlobalObservability, GlobalObservabilityGate, ObservabilityRoot, ObservabilityScopeDefaults}
import org.goldenport.cncf.log.LogBackendHolder

/*
 * @since   Jan. 23, 2026
 * @version Jan. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class GlobalObservabilitySpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    LogBackendHolder.reset()
    GlobalObservability.resetForTests()
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = LogLevel.Trace))
  }

  override protected def afterEach(): Unit = {
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = LogLevel.Info))
    super.afterEach()
  }

  "GlobalObservability" should {
    "replay buffered trace after initialize" in {
      GlobalObservability.observeTrace(
        ObservabilityScopeDefaults.Subsystem,
        "pre-init replay",
        classOf[GlobalObservability.type]
      )
      val backend = new MemoryBackend
      GlobalObservability.initialize(
        ObservabilityRoot(
          engine = ObservabilityEngine,
          gate = GlobalObservabilityGate.allowAll,
          backend = backend
        )
      )
      backend.lines.exists(_.contains("pre-init replay")) shouldBe true
    }

    "not replay the same event twice" in {
      GlobalObservability.observeTrace(
        ObservabilityScopeDefaults.Subsystem,
        "pre-init replay once",
        classOf[GlobalObservability.type]
      )
      val backend = new MemoryBackend
      GlobalObservability.initialize(
        ObservabilityRoot(
          engine = ObservabilityEngine,
          gate = GlobalObservabilityGate.allowAll,
          backend = backend
        )
      )
      val initialSize = backend.lines.size
      GlobalObservability.observeTrace(
        ObservabilityScopeDefaults.Subsystem,
        "post-init emit",
        classOf[GlobalObservability.type]
      )
      backend.lines.size shouldBe initialSize + 1
    }

    "drop oldest buffered events when overflowing" in {
      val limit = GlobalObservability.BufferLimit
      (0 to limit).foreach { i =>
        GlobalObservability.observeTrace(
          ObservabilityScopeDefaults.Subsystem,
          s"buffered trace $i",
          classOf[GlobalObservability.type]
        )
      }
      val backend = new MemoryBackend
      GlobalObservability.initialize(
        ObservabilityRoot(
          engine = ObservabilityEngine,
          gate = GlobalObservabilityGate.allowAll,
          backend = backend
        )
      )
      backend.lines.exists(_.contains("buffered trace 0")) shouldBe false
      backend.lines.exists(_.contains(s"buffered trace $limit")) shouldBe true
    }
  }

  private final class MemoryBackend extends LogBackend {
    private val _lines = ListBuffer.empty[String]

    def lines: Vector[String] = _lines.synchronized {
      _lines.toVector
    }

    override def writeLine(line: String): Unit = _lines.synchronized {
      _lines += line
    }
  }
}
