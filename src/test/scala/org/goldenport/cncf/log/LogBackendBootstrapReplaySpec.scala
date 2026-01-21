package org.goldenport.cncf.log

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class LogBackendBootstrapReplaySpec extends AnyWordSpec with Matchers {

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
    "replay bootstrap buffer to a replayable backend" in {
      LogBackendHolder.reset()
      LogBackendHolder.backend.foreach(_.log("info", "boot"))
      val memory = new MemoryBackend
      LogBackendHolder.install(memory)
      memory.lines shouldBe Vector("boot")
    }

    "skip replay when backend does not support replay" in {
      LogBackendHolder.reset()
      LogBackendHolder.backend.foreach(_.log("info", "boot"))
      val counter = new CountingBackend
      LogBackendHolder.install(counter)
      counter.count shouldBe 0
    }
  }
}
