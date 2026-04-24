package org.goldenport.cncf.cli

import org.goldenport.cncf.config.RuntimeConfig
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 24, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeOptionsParserSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Runtime options parser" should {
    "map debug trace-job and calltree flags to framework properties" in {
      Given("a command line with debug trace-job and calltree flags")
      val (options, clean) = RuntimeOptionsParser.extract(Seq("--debug.trace-job", "--debug.calltree", "client", "get"))

      When("runtime properties are projected")
      val properties = RuntimeOptionsParser.properties(options)

      Then("debug trace-job and calltree are framework properties and do not remain as positional args")
      clean shouldBe Seq("client", "get")
      properties.find(_.name == RuntimeConfig.DebugTraceJobKey).map(_.value.toString) shouldBe Some("true")
      properties.find(_.name == RuntimeConfig.DebugCallTreeKey).map(_.value.toString) shouldBe Some("true")
    }

    "map debug trace-job key-value input to framework properties" in {
      Given("a command line with a debug trace-job property")
      val (options, clean) = RuntimeOptionsParser.extract(Seq("textus.debug.trace-job=true", "client", "get"))

      When("runtime properties are projected")
      val properties = RuntimeOptionsParser.properties(options)

      Then("debug trace-job is captured as a framework option and removed from positional args")
      clean shouldBe Seq("client", "get")
      properties.find(_.name == RuntimeConfig.DebugTraceJobKey).map(_.value.toString) shouldBe Some("true")
    }
  }
}
