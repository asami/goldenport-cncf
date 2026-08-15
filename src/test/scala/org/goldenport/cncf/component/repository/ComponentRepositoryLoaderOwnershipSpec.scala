package org.goldenport.cncf.component.repository

import java.nio.file.Paths

import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentLocalFirstClassLoader, ComponentOrigin}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentRepositoryLoaderOwnershipSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:component-repository-loader-ownership, example:$exampleid, rules:P74-D2, phase:7.4, slice:P74-D2")

  "ComponentRepositoryLoaderOwnership" should {
    "close a development loader after a materialization failure without registering it" must _metadata("E1") {
      "when discovery returns a Consequence failure" in {
        Given("an empty component-local loader, a subsystem, and a recording bootstrap log")
        val subsystem = TestComponentFactory.emptySubsystem("loader-ownership-failure")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("loader-ownership"))
        val loader = new ComponentLocalFirstClassLoader(Array.empty[java.net.URL], getClass.getClassLoader)
        val log = new RecordingBootstrapLog
        val failuremessage = "materialization failure supplied by the development repository"

        try {
          When("development discovery receives the supplied Consequence failure")
          val discovered = ComponentRepositoryLoaderOwnership.discoverDevelopment(
            loader,
            params,
            log,
            Paths.get("component-dev-dir-label")
          ) {
            Consequence.Failure[Vector[Component]](Conclusion.simple(failuremessage))
          }

          Then("discovery returns no components, records the failure, and does not transfer loader ownership")
          discovered shouldBe Vector.empty
          log.warnings.exists(_.contains(failuremessage)) shouldBe true
          loader.closeInvocationCount shouldBe 1
          subsystem.componentClassLoaderSnapshot shouldBe Vector.empty

          When("the subsystem is shut down after failed discovery")
          val shutdownresult = subsystem.shutdownC()

          Then("shutdown succeeds without closing the already released loader again")
          shutdownresult shouldBe a[Consequence.Success[_]]
          loader.closeInvocationCount shouldBe 1
        } finally {
          subsystem.shutdownC()
        }
      }
    }

    "close a development loader and rethrow a materialization exception without registering it" must _metadata("E2") {
      "when discovery throws from materialization" in {
        Given("an empty component-local loader, a subsystem, and the exact materialization exception")
        val subsystem = TestComponentFactory.emptySubsystem("loader-ownership-exception")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("loader-ownership"))
        val loader = new ComponentLocalFirstClassLoader(Array.empty[java.net.URL], getClass.getClassLoader)
        val log = new RecordingBootstrapLog
        val materializationexception = new IllegalStateException("materialization exception supplied by the development repository")

        try {
          When("development discovery materialization throws the supplied exception")
          val thrown = intercept[IllegalStateException] {
            ComponentRepositoryLoaderOwnership.discoverDevelopment(
              loader,
              params,
              log,
              Paths.get("component-dev-dir-label")
            ) {
              throw materializationexception
            }
          }

          Then("the same exception is rethrown after one local cleanup and no subsystem registration")
          thrown shouldBe materializationexception
          thrown.getMessage shouldBe materializationexception.getMessage
          loader.closeInvocationCount shouldBe 1
          log.warnings shouldBe Vector.empty
          subsystem.componentClassLoaderSnapshot shouldBe Vector.empty

          When("the subsystem is shut down after exceptional discovery")
          val shutdownresult = subsystem.shutdownC()

          Then("shutdown succeeds without closing the already released loader again")
          shutdownresult shouldBe a[Consequence.Success[_]]
          loader.closeInvocationCount shouldBe 1
        } finally {
          subsystem.shutdownC()
        }
      }
    }
  }

  private final class RecordingBootstrapLog extends BootstrapLog {
    private var _warnings: Vector[String] = Vector.empty

    def warnings: Vector[String] = synchronized(_warnings)

    override def info(msg: String): Unit = ()

    override def warn(msg: String): Unit = synchronized {
      _warnings = _warnings :+ msg
    }

    override def error(msg: String): Unit = ()
  }
}
