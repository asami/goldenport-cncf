package org.goldenport.cncf.subsystem

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since Oct.  7, 2026
 * @version Oct.  7, 2026
 */
final class SystemNodeRuntimeResourceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SystemNode runtime ownership" should {
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
