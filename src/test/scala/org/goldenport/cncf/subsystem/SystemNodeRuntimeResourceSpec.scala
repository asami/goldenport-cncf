package org.goldenport.cncf.subsystem

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.Files
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.workarea.WorkAreaSpace

/*
 * @since Oct.  7, 2026
 * @version Oct. 21, 2026
 */
final class SystemNodeRuntimeResourceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SystemNode runtime ownership" should {
    "reuse its assembly WorkArea and isolate a bare embedding's owned area" in {
      Given("one node with an assembly-owned WorkArea and a second bare embedding node")
      val first = SystemNode.create()
      val second = SystemNode.create()
      val assemblyarea = WorkAreaSpace.create(RuntimeConfig.default)
      first.ownRuntimeResourceC(assemblyarea).TAKE
      try {
        When("each node acquires its own WorkArea and the second owner shuts down")
        val firstarea = first.workAreaSpaceC.TAKE
        val secondarea = second.workAreaSpaceC.TAKE
        val firstfile = firstarea.createTempFile("first").TAKE
        val secondfile = secondarea.createTempFile("second").TAKE
        val secondclose = second.shutdownC()
        Then("assembly ownership is reused, only the second owner's files disappear, and late acquisition fails")
        (firstarea eq assemblyarea) shouldBe true
        (first.workAreaSpaceC.TAKE eq firstarea) shouldBe true
        secondclose.isSuccess shouldBe true
        Files.exists(firstfile) shouldBe true
        Files.exists(secondfile) shouldBe false
        second.workAreaSpaceC.isFaillure shouldBe true
        When("the first owner also shuts down")
        val firstclose = first.shutdownC()
        Then("its original assembly-owned files are removed")
        firstclose.isSuccess shouldBe true
        Files.exists(firstfile) shouldBe false
      } finally {
        first.shutdownC()
        second.shutdownC()
      }
    }

    "close each owned resource once in reverse registration order and refuse late admission" in {
      Given("a running SystemNode and two resources, including a duplicate registration")
      val node = SystemNode.create()
      var closed = Vector.empty[String]
      val first = new AutoCloseable { def close(): Unit = { closed = closed :+ "first" } }
      val second = new AutoCloseable { def close(): Unit = { closed = closed :+ "second" } }
      node.ownRuntimeResourceC(first).isSuccess shouldBe true
      node.ownRuntimeResourceC(second).isSuccess shouldBe true
      node.ownRuntimeResourceC(first).isSuccess shouldBe true
      When("the owning node shuts down twice and a caller attempts a later registration")
      val initial = node.shutdownC()
      val repeated = node.shutdownC()
      val late = node.ownRuntimeResourceC(new AutoCloseable { def close(): Unit = () })
      Then("resources close only once, the node is terminal and it accepts no new resource")
      initial.isSuccess shouldBe true
      repeated.isSuccess shouldBe true
      closed shouldBe Vector("second", "first")
      node.state shouldBe SystemNode.State.Stopped
      late.isSuccess shouldBe false
    }

    "retain a close failure while still closing the remaining resource and terminalizing" in {
      Given("a node with one ordinary resource and one failing close callback")
      val node = SystemNode.create()
      var firstclosed = false
      var failures = 0
      node.ownRuntimeResourceC(new AutoCloseable { def close(): Unit = { firstclosed = true } }).isSuccess shouldBe true
      node.ownRuntimeResourceC(new AutoCloseable { def close(): Unit = {
        failures += 1
        throw new IllegalStateException("test-only runtime close failure")
      } }).isSuccess shouldBe true
      When("the owner shuts down and a later caller observes its terminal result")
      val result = node.shutdownC()
      val repeated = node.shutdownC()
      Then("failure is retained without leaking the other resource or repeating close")
      result.isSuccess shouldBe false
      repeated.isSuccess shouldBe false
      firstclosed shouldBe true
      failures shouldBe 1
      node.state shouldBe SystemNode.State.Stopped
    }
  }
}
