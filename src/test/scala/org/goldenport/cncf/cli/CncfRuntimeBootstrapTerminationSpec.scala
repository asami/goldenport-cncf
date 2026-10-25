package org.goldenport.cncf.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Paths}
import io.circe.parser.parse
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeBootstrapTerminationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  override protected def withFixture(test: NoArgTest): org.scalatest.Outcome = {
    val previous = GlobalRuntimeContext.current
    val previouslegacy = CncfRuntime._global_runtime_context
    try super.withFixture(test)
    finally {
      GlobalRuntimeContext.current = previous
      CncfRuntime._global_runtime_context = previouslegacy
    }
  }
  "Companion bootstrap command termination" should {
    Vector(false, true).foreach { extra =>
      s"install the invocation hook around the actual companion command with extra components set to $extra" in {
        Given("an isolated controlled descriptor and the public companion command path used by the installed entry")
        val directory = Files.createTempDirectory(Files.createDirectories(
          Paths.get("target/cncf-test/work/bootstrap-termination").toAbsolutePath.normalize), "command-")
        val descriptor = directory.resolve("test.yaml")
        Files.writeString(descriptor,
          """kind: test-descriptor
            |execution:
            |  profile: controlled
            |  key: bootstrap-termination-spec
            |  time:
            |    mode: manual
            |    start-at: '2026-10-20T00:00:00Z'
            |  random:
            |    mode: seeded
            |    seed: bootstrap-termination-spec
            |""".stripMargin)
        val propertynames = Vector("org.slf4j.simpleLogger.logFile", "org.slf4j.simpleLogger.defaultLogLevel",
          "org.slf4j.simpleLogger.showDateTime", "org.slf4j.simpleLogger.dateTimeFormat",
          "org.slf4j.simpleLogger.log.com.zaxxer.hikari", "org.slf4j.simpleLogger.log.org.sqlite")
        val previous = propertynames.map(name => name -> Option(System.getProperty(name))).toMap
        val out = new ByteArrayOutputStream()
        val err = new ByteArrayOutputStream()
        val output = new PrintStream(out, true, "UTF-8")
        val errors = new PrintStream(err, true, "UTF-8")
        val originalerr = System.err
        var subsystem = Option.empty[Subsystem]
        var closes = 0
        var commandcompletedbeforeclose = false
        var hookregisteredbeforeclose = false
        try {
          System.setErr(errors)
          val args = Array(s"--textus.test.descriptor=$descriptor", "command", "help")
          When("the companion bootstrap dispatches Command through its real executeCommand and owned shutdown")
          val code = Console.withOut(output) {
            if (extra)
              CncfRuntime.runWithExtraComponents(args, value => {
                subsystem = Some(value)
                value.systemNode.ownRuntimeResourceC(new AutoCloseable {
                  def close(): Unit = {
                    closes += 1
                    commandcompletedbeforeclose = out.toString("UTF-8").contains("CNCF Command Help")
                    hookregisteredbeforeclose = err.toString("UTF-8").contains("[cncf-runtime-event] ")
                  }
                }).isSuccess shouldBe true
                Vector.empty
              })
            else
              CncfRuntime.run(args)
          }
          val prefix = "[cncf-runtime-event] "
          val events = err.toString("UTF-8").linesIterator.filter(_.startsWith(prefix)).map { line =>
            parse(line.stripPrefix(prefix)).fold(error => fail(error.message), identity)
          }.toVector
          Then("the actual command succeeds with exactly one runtime-started event naming the current JVM")
          code shouldBe 0
          out.toString("UTF-8") should include ("CNCF Command Help")
          events should have size 1
          events.head.asObject.get.keys.toSet shouldBe Set("state", "pid")
          events.head.hcursor.get[String]("state").toOption shouldBe Some("runtime-started")
          events.head.hcursor.get[Long]("pid").toOption shouldBe Some(ProcessHandle.current().pid())
          if (extra) {
            And("the original owner closes only after command execution and hook registration")
            closes shouldBe 1
            commandcompletedbeforeclose shouldBe true
            hookregisteredbeforeclose shouldBe true
            subsystem.get.systemNode.state shouldBe SystemNode.State.Stopped
          }
        } finally {
          System.setErr(originalerr)
          subsystem.foreach(Subsystem.shutdownOwned)
          output.close()
          errors.close()
          previous.foreach {
            case (name, Some(value)) => System.setProperty(name, value)
            case (name, None) => System.clearProperty(name)
          }
          Files.deleteIfExists(descriptor)
          Files.deleteIfExists(directory)
        }
      }
    }
  }
}
