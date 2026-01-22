package org.goldenport.cncf.log

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import org.goldenport.cncf.observability.global.GlobalObservability
import org.goldenport.test.TestTags
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Tag
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 23, 2026
 * @version Jan. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class LogBackendBootstrapReplaySpec extends AnyWordSpec
    with Matchers with BeforeAndAfterEach {
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    GlobalObservability.gate.foreach(_.blockAll())
    LogBackendHolder.reset()
  }

  override protected def afterEach(): Unit = {
    GlobalObservability.gate.foreach(_.allowAll())
    super.afterEach()
  }


  private final class MemoryBackend extends LogBackend {
    private val _lines = ListBuffer.empty[String]

    def lines: Vector[String] = _lines.synchronized {
      _lines.toVector
    }

    override def supportsBootstrapReplay: Boolean = true

    override def writeLine(line: String): Unit = _lines.synchronized {
      _lines += line
    }
  }

  private final class CountingBackend extends LogBackend {
    private val _count = new AtomicInteger(0)

    def count: Int = _count.get()

    override def writeLine(line: String): Unit =
      _count.incrementAndGet()
  }

  "LogBackendHolder" should {
    "replay bootstrap buffer to a replayable backend" taggedAs(
      Tag(TestTags.MANUAL_SPEC),
      Tag(TestTags.FORK_SPEC)
    ) in {
      LogBackendHolder.reset()
      LogBackendHolder.backend.foreach(_.log("info", "boot"))
      val memory = new MemoryBackend
      LogBackendHolder.install(memory)
      memory.lines.count(_ == "boot") shouldBe 1
    }

    "skip replay when backend does not support replay" taggedAs(
      Tag(TestTags.MANUAL_SPEC),
      Tag(TestTags.FORK_SPEC)
    ) in {
      LogBackendHolder.reset()
      LogBackendHolder.backend.foreach(_.log("info", "boot"))
      val counter = new CountingBackend
      LogBackendHolder.install(counter)
      counter.count shouldBe 0
    }
  }
}
