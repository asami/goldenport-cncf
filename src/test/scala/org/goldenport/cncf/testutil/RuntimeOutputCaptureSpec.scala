package org.goldenport.cncf.testutil

import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeOutputCaptureSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e1 = afterWord("in spec:test-runtime-output-isolation, example:E1, rules:R1, phase:standalone-incident")
  private val _e2 = afterWord("in spec:test-runtime-output-isolation, example:E2, rules:R2, phase:standalone-incident")
  private val _e3 = afterWord("in spec:test-runtime-output-isolation, example:E3, rules:R3, phase:standalone-incident")

  "Runtime output capture" should {
    "capture/lifetime behavior" should {
      "E1 capture Java and Scala output while retaining the result and original bindings" must _e1 {
        "when a runtime probe writes through Java, Scala, and the stdout backend routes" in {
          Given("Spec: docs/journal/2026/09/2026-09-28-test-runtime-output-isolation.md; Rules: R1; Example: E1; the current Java streams and global log backend identities")
          val originalout = System.out
          val originalerr = System.err
          val originalbackend = LogBackendHolder.backend

          When("the probe runs inside one RuntimeOutputCapture scope")
          val captured = RuntimeOutputCapture.capture {
            LogBackendHolder.install(LogBackend.StdoutBackend)
            print("scala-out")
            Console.err.print("scala-err")
            System.out.print("java-out")
            System.err.print("java-err")
            LogBackendHolder.backend.foreach(_.log("info", "backend-probe"))
            "capture-result"
          }

          Then("the result and UTF-8 Java and Scala output are returned exactly")
          captured.value shouldBe "capture-result"
          captured.stdout shouldBe "scala-outjava-outbackend-probe\n"
          captured.stderr shouldBe "scala-errjava-err"

          And("the original Java streams and logger backend identities are restored")
          System.out should be theSameInstanceAs originalout
          System.err should be theSameInstanceAs originalerr
          originalbackend match {
            case Some(backend) => LogBackendHolder.backend.get should be theSameInstanceAs backend
            case None => LogBackendHolder.backend shouldBe defined
          }
        }
      }

      "E2 retain joined child-thread output until the capture scope completes" must _e2 {
        "when a child thread writes to the process streams and is joined inside the scope" in {
          Given("Spec: docs/journal/2026/09/2026-09-28-test-runtime-output-isolation.md; Rules: R2; Example: E2; a child thread whose output is test-owned and joined before scope completion")
          val worker = new Thread(() => {
            System.out.print("child-out")
            System.err.print("child-err")
          })
          When("the child writes Java stdout and stderr inside RuntimeOutputCapture")
          val captured = RuntimeOutputCapture.capture {
            worker.start()
            worker.join()
            "joined"
          }

          Then("joined child output remains in the returned snapshots")
          captured.value shouldBe "joined"
          captured.stdout shouldBe "child-out"
          captured.stderr shouldBe "child-err"
        }
      }

      "E3 restore every binding when a captured body throws its original exception" must _e3 {
        "when the body replaces the backend, writes stderr, and throws intentionally" in {
          Given("Spec: docs/journal/2026/09/2026-09-28-test-runtime-output-isolation.md; Rules: R3; Example: E3; the current Java streams and global log backend identities")
          val originalout = System.out
          val originalerr = System.err
          val originalbackend = LogBackendHolder.backend
          val failure = new IllegalStateException("runtime-output-capture-failure")

          When("the failing body runs inside RuntimeOutputCapture")
          val thrown = intercept[IllegalStateException] {
            RuntimeOutputCapture.capture {
              LogBackendHolder.install(LogBackend.StdoutBackend)
              System.err.print("failure-stderr")
              throw failure
            }
          }

          Then("the identical throwable propagates and all original bindings are restored")
          thrown should be theSameInstanceAs failure
          System.out should be theSameInstanceAs originalout
          System.err should be theSameInstanceAs originalerr
          originalbackend match {
            case Some(backend) => LogBackendHolder.backend.get should be theSameInstanceAs backend
            case None => LogBackendHolder.backend shouldBe defined
          }
        }
      }
    }
  }
}
