package org.goldenport.cncf.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.{InetAddress, ServerSocket}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.collection.mutable

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.{Component, ComponentActivation, ComponentActivationContext, ComponentActivationDiagnostic, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.http.Http4sHttpServer
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServerOperationActivationSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:component-activation-lifecycle, example:E1, rules:R3,R7, phase:70, slice:CA70-02A")
  private val _e2 = afterWord("in spec:component-activation-lifecycle, example:E2, rules:R3,R7,R8, phase:70, slice:CA70-02A")
  private val _e3 = afterWord("in spec:component-activation-lifecycle, example:E3, rules:R3,R7, phase:70, slice:CA70-02A")
  private val _e4 = afterWord("in spec:component-activation-lifecycle, example:E4, rules:R3,R7,R8, phase:70, slice:CA70-02A")

  "Server operation activation" should {
    "E1 gate canonical Server dispatch after a completed assembly and before HTTP publication" must _e1 {
      "when an initialized extra activation component fails through the canonical _run gate" in {
        Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R3,R7; Example: E1")
        val callbackcount = new AtomicInteger(0)
        val secret = "CA70_ACTIVATION_SECRET_8d3f4a"
        val privatelocator = "private-locator://CA70/activation/8d3f4a"
        val descriptor = _descriptor_path("canonical")
        val originaltestruntimeproperty = sys.props.get("textus.test")
        val originalboundproperties = _bound_properties
        var assembly: Option[ServerAssembly] = None

        try {
          _without_test_runtime_flag {
            When("the canonical CncfRuntime _run Server branch receives the completed managed Server assembly")
            _clear_bound_properties()
            val built = _build_failing_assembly("canonical", callbackcount, secret, privatelocator, descriptor)
            assembly = Some(built)
            built.subsystem.controlledTestExecutionEnabled shouldBe false
            built.subsystem.findComponent(built.component.componentId) shouldBe Some(built.component)
            val runtime = new CncfRuntime()
            val request = _server_request(runtime)
            val (exitcode, renderederror) = _capture_error(runtime._run(built.subsystem, request))
            val diagnostic = ComponentActivation.diagnosticFor(built.subsystem)

            Then("the completed extra component is admitted, the canonical gate invokes it once, and ordinary failure rendering is safe")
            callbackcount.get shouldBe 1
            exitcode shouldBe 1
            renderederror should include("Component activation failed.")
            renderederror should not include secret
            renderederror should not include privatelocator

            Then("failure publishes one sanitized public diagnostic, leaves HTTP properties unpublished, and performs managed cleanup")
            diagnostic shouldBe defined
            diagnostic.foreach(_assert_safe_diagnostic(_, secret, privatelocator))
            _bound_properties.values.foreach(_ shouldBe empty)
            built.subsystem.systemNode.state shouldBe SystemNode.State.Stopped
          }
        } finally {
          assembly.foreach(value => Subsystem.shutdownOwned(value.subsystem))
          _restore_bound_properties(originalboundproperties)
          _delete_descriptor(descriptor)
        }

        Then("the public HTTP bound and readiness property set and test-only runtime flag are restored for other runtime owners")
        _bound_properties shouldBe originalboundproperties
        sys.props.get("textus.test") shouldBe originaltestruntimeproperty
      }
    }

    "E2 retain the direct legacy startServer compatibility gate on a separate completed assembly" must _e2 {
      "when an initialized extra activation component fails through the legacy startServer route" in {
        Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R3,R7,R8; Example: E2")
        val callbackcount = new AtomicInteger(0)
        val secret = "CA70_LEGACY_SECRET_8d3f4a"
        val privatelocator = "private-locator://CA70/legacy/8d3f4a"
        val descriptor = _descriptor_path("legacy")
        val originaltestruntimeproperty = sys.props.get("textus.test")
        val originalboundproperties = _bound_properties
        var assembly: Option[ServerAssembly] = None

        try {
          _without_test_runtime_flag {
            When("the direct compatibility startServer route receives its own completed managed Server assembly")
            _clear_bound_properties()
            val built = _build_failing_assembly("legacy", callbackcount, secret, privatelocator, descriptor)
            assembly = Some(built)
            built.subsystem.controlledTestExecutionEnabled shouldBe false
            built.subsystem.findComponent(built.component.componentId) shouldBe Some(built.component)
            val runtime = new CncfRuntime()
            val (exitcode, renderederror) = _capture_error(runtime.startServer(built.subsystem, _server_request(runtime).request))
            val diagnostic = ComponentActivation.diagnosticFor(built.subsystem)

            Then("the completed extra component is admitted and the legacy compatibility gate invokes it once without starting HTTP")
            callbackcount.get shouldBe 1
            exitcode shouldBe 1
            renderederror should include("Component activation failed.")
            renderederror should not include secret
            renderederror should not include privatelocator

            Then("legacy failure publishes one sanitized public diagnostic, prevents property publication, and performs managed cleanup")
            diagnostic shouldBe defined
            diagnostic.foreach(_assert_safe_diagnostic(_, secret, privatelocator))
            _bound_properties.values.foreach(_ shouldBe empty)
            built.subsystem.systemNode.state shouldBe SystemNode.State.Stopped
          }
        } finally {
          assembly.foreach(value => Subsystem.shutdownOwned(value.subsystem))
          _restore_bound_properties(originalboundproperties)
          _delete_descriptor(descriptor)
        }

        Then("the public HTTP bound and readiness property set and test-only runtime flag are restored for other runtime owners")
        _bound_properties shouldBe originalboundproperties
        sys.props.get("textus.test") shouldBe originaltestruntimeproperty
      }
    }

    "E3 start the actual HTTP server only after successful canonical activation" must _e3 {
      "when the completed assembly is dispatched through the canonical _run Server branch under the thread-scoped test adapter" in {
        Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R3,R7; Example: E3")
        val callbackcount = new AtomicInteger(0)
        val callbackboundproperties = new AtomicReference[Map[String, Option[String]]](Map.empty)
        val descriptor = _descriptor_path("canonical-success")
        val originaltestruntimeproperty = sys.props.get("textus.test")
        val originalportproperty = sys.props.get(Http4sHttpServer.PORT_PROPERTY_KEY)
        val originalboundproperties = _bound_properties
        val adapter = new Http4sHttpServer.RuntimeStartTestAdapter
        var assembly: Option[ServerAssembly] = None

        try {
          _with_selected_loopback_port { port =>
            _without_test_runtime_flag {
              When("canonical dispatch activates its opted-in component before the adapter starts the real Ember program")
              _clear_bound_properties()
              val built = _build_successful_assembly("canonical-success", callbackcount, callbackboundproperties, descriptor)
              assembly = Some(built)
              val runtime = new CncfRuntime()
              val exitcode = Http4sHttpServer.withRuntimeStartTestAdapter(adapter) {
                runtime._run(built.subsystem, _server_request(runtime))
              }
              val readyproperties = _await_bound_properties()
              adapter.cancelAndAwait()
              val clearedproperties = _bound_properties

              Then("the canonical route returns success after one activation, then publishes real bound readiness at the selected loopback port and clears it on cancellation")
              exitcode shouldBe 0
              callbackcount.get shouldBe 1
              callbackboundproperties.get.values.foreach(_ shouldBe empty)
              ComponentActivation.diagnosticFor(built.subsystem) shouldBe empty
              readyproperties(Http4sHttpServer.BOUND_SNAPSHOT_PROPERTY_KEY) shouldBe defined
              readyproperties(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY) shouldBe defined
              readyproperties(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY).foreach(_ should include(s":$port"))
              clearedproperties.values.foreach(_ shouldBe empty)
            }
          }
        } finally {
          adapter.cancelAndAwait()
          assembly.foreach(value => Subsystem.shutdownOwned(value.subsystem))
          _restore_bound_properties(originalboundproperties)
          _delete_descriptor(descriptor)
        }

        Then("the canonical test restores the HTTP properties, descriptor, runtime flag, and thread-scoped adapter ownership")
        _bound_properties shouldBe originalboundproperties
        sys.props.get("textus.test") shouldBe originaltestruntimeproperty
        sys.props.get(Http4sHttpServer.PORT_PROPERTY_KEY) shouldBe originalportproperty
        Files.exists(descriptor) shouldBe false
        assembly.foreach(_.subsystem.systemNode.state shouldBe SystemNode.State.Stopped)
      }
    }

    "E4 start the actual HTTP server only after successful legacy activation" must _e4 {
      "when the completed assembly uses the direct legacy startServer route under the thread-scoped test adapter" in {
        Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R3,R7,R8; Example: E4")
        val callbackcount = new AtomicInteger(0)
        val callbackboundproperties = new AtomicReference[Map[String, Option[String]]](Map.empty)
        val descriptor = _descriptor_path("legacy-success")
        val originaltestruntimeproperty = sys.props.get("textus.test")
        val originalportproperty = sys.props.get(Http4sHttpServer.PORT_PROPERTY_KEY)
        val originalboundproperties = _bound_properties
        val adapter = new Http4sHttpServer.RuntimeStartTestAdapter
        var assembly: Option[ServerAssembly] = None

        try {
          _with_selected_loopback_port { port =>
            _without_test_runtime_flag {
              When("legacy startServer activates its opted-in component before the adapter starts the real Ember program")
              _clear_bound_properties()
              val built = _build_successful_assembly("legacy-success", callbackcount, callbackboundproperties, descriptor)
              assembly = Some(built)
              val runtime = new CncfRuntime()
              val exitcode = Http4sHttpServer.withRuntimeStartTestAdapter(adapter) {
                runtime.startServer(built.subsystem, _server_request(runtime).request)
              }
              val readyproperties = _await_bound_properties()
              adapter.cancelAndAwait()
              val clearedproperties = _bound_properties

              Then("the compatibility route returns success after one activation, then publishes real bound readiness at the selected loopback port and clears it on cancellation")
              exitcode shouldBe 0
              callbackcount.get shouldBe 1
              callbackboundproperties.get.values.foreach(_ shouldBe empty)
              ComponentActivation.diagnosticFor(built.subsystem) shouldBe empty
              readyproperties(Http4sHttpServer.BOUND_SNAPSHOT_PROPERTY_KEY) shouldBe defined
              readyproperties(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY) shouldBe defined
              readyproperties(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY).foreach(_ should include(s":$port"))
              clearedproperties.values.foreach(_ shouldBe empty)
            }
          }
        } finally {
          adapter.cancelAndAwait()
          assembly.foreach(value => Subsystem.shutdownOwned(value.subsystem))
          _restore_bound_properties(originalboundproperties)
          _delete_descriptor(descriptor)
        }

        Then("the legacy test restores the HTTP properties, descriptor, runtime flag, and thread-scoped adapter ownership")
        _bound_properties shouldBe originalboundproperties
        sys.props.get("textus.test") shouldBe originaltestruntimeproperty
        sys.props.get(Http4sHttpServer.PORT_PROPERTY_KEY) shouldBe originalportproperty
        Files.exists(descriptor) shouldBe false
        assembly.foreach(_.subsystem.systemNode.state shouldBe SystemNode.State.Stopped)
      }
    }
  }

  private final case class ServerAssembly(subsystem: Subsystem, component: Component)

  private val _bound_property_keys = Vector(
    Http4sHttpServer.BOUND_SNAPSHOT_PROPERTY_KEY,
    Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY,
    Http4sHttpServer.BOUND_APPLICATION_PATH_PROPERTY_KEY,
    Http4sHttpServer.BOUND_APPLICATION_URL_PROPERTY_KEY
  )

  private val _descriptor_directory: Path =
    Paths.get("target", "cncf-server-operation-activation-spec")

  private def _build_failing_assembly(
    name: String,
    callbackcount: AtomicInteger,
    secret: String,
    privatelocator: String,
    descriptor: Path
  ): ServerAssembly = {
    val componentref = new AtomicReference[Option[Component]](None)
    val subsystem = CncfRuntime.buildSubsystem(
      extraComponents = current => {
        val componentid = ComponentId(s"org.goldenport.cncf.test.serveroperationactivation.${name.head.toUpper}${name.tail}")
        val component = new Component() with ComponentActivation {
          override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
            callbackcount.incrementAndGet()
            throw new IllegalStateException(s"$secret $privatelocator")
          }
        }
        componentref.set(Some(_initialize_component(current, componentid, component)))
        Vector(component)
      },
      mode = Some(RunMode.Server),
      args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
    )
    ServerAssembly(subsystem, componentref.get.get)
  }

  private def _build_successful_assembly(
    name: String,
    callbackcount: AtomicInteger,
    callbackboundproperties: AtomicReference[Map[String, Option[String]]],
    descriptor: Path
  ): ServerAssembly = {
    val componentref = new AtomicReference[Option[Component]](None)
    val subsystem = CncfRuntime.buildSubsystem(
      extraComponents = current => {
        val localname = name.split("-").map(value => s"${value.head.toUpper}${value.tail}").mkString
        val componentid = ComponentId(s"org.goldenport.cncf.test.serveroperationactivation.$localname")
        val component = new Component() with ComponentActivation {
          override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
            callbackcount.incrementAndGet()
            callbackboundproperties.set(_bound_properties)
            Consequence.unit
          }
        }
        componentref.set(Some(_initialize_component(current, componentid, component)))
        Vector(component)
      },
      mode = Some(RunMode.Server),
      args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
    )
    ServerAssembly(subsystem, componentref.get.get)
  }

  private def _initialize_component(
    subsystem: Subsystem,
    componentid: ComponentId,
    component: Component
  ): Component = {
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _server_request(runtime: CncfRuntime): org.goldenport.protocol.operation.OperationRequest =
    runtime._runtime_protocol_engine.makeOperationRequest(Array(RunMode.Server.name)) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _assert_safe_diagnostic(
    diagnostic: ComponentActivationDiagnostic,
    secret: String,
    privatelocator: String
  ): Unit = {
    diagnostic.category should include("activation")
    diagnostic.conclusion.causes should not be empty
    _public_causal_conclusions(Vector(diagnostic.conclusion)).foreach(_.causes.foreach(_.getException shouldBe empty))
    _public_causal_forms(diagnostic.conclusion).foreach { text =>
      text should not include secret
      text should not include privatelocator
    }
  }

  private def _public_causal_conclusions(roots: Vector[Conclusion]): Vector[Conclusion] = {
    val seen = new java.util.IdentityHashMap[Conclusion, java.lang.Boolean]()
    val results = mutable.ArrayBuffer.empty[Conclusion]

    def collect(value: Any): Unit = value match {
      case null => ()
      case conclusion: Conclusion =>
        if (!seen.containsKey(conclusion)) {
          seen.put(conclusion, java.lang.Boolean.TRUE)
          results += conclusion
          conclusion.causes.foreach(collect)
        }
      case option: Option[?] => option.foreach(collect)
      case optional: java.util.Optional[?] if optional.isPresent => collect(optional.get)
      case values: Iterable[?] => values.foreach(collect)
      case values: Array[?] => values.foreach(collect)
      case reference: AnyRef =>
        reference.getClass.getMethods.iterator
          .filter(method => method.getParameterCount == 0 && method.getName.toLowerCase.contains("conclusion"))
          .foreach { method =>
            try collect(method.invoke(reference))
            catch { case _: Throwable => () }
          }
      case _ => ()
    }

    roots.foreach(collect)
    results.toVector
  }

  private def _public_causal_forms(conclusion: Conclusion): Vector[String] =
    Vector(
      conclusion.show,
      conclusion.toString,
      conclusion.causes.map(_.show).mkString("\n"),
      conclusion.causes.map(_.toString).mkString("\n")
    )

  private def _bound_properties: Map[String, Option[String]] =
    _bound_property_keys.map(key => key -> Option(System.getProperty(key))).toMap

  private def _clear_bound_properties(): Unit =
    _bound_property_keys.foreach(System.clearProperty)

  private def _restore_bound_properties(properties: Map[String, Option[String]]): Unit =
    properties.foreach {
      case (key, Some(value)) => System.setProperty(key, value)
      case (key, None) => System.clearProperty(key)
    }

  private def _await_bound_properties(): Map[String, Option[String]] = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5L)
    var properties = _bound_properties
    while (properties(Http4sHttpServer.BOUND_SNAPSHOT_PROPERTY_KEY).isEmpty && System.nanoTime() < deadline) {
      Thread.sleep(10L)
      properties = _bound_properties
    }
    withClue("real HTTP server readiness was not published before the bounded wait") {
      properties(Http4sHttpServer.BOUND_SNAPSHOT_PROPERTY_KEY) shouldBe defined
      properties(Http4sHttpServer.BOUND_BASE_URL_PROPERTY_KEY) shouldBe defined
    }
    properties
  }

  private def _with_selected_loopback_port[A](body: Int => A): A = {
    val previous = sys.props.get(Http4sHttpServer.PORT_PROPERTY_KEY)
    val socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress)
    val port = try socket.getLocalPort finally socket.close()
    System.setProperty(Http4sHttpServer.PORT_PROPERTY_KEY, port.toString)
    try body(port)
    finally {
      previous match {
        case Some(value) => System.setProperty(Http4sHttpServer.PORT_PROPERTY_KEY, value)
        case None => System.clearProperty(Http4sHttpServer.PORT_PROPERTY_KEY)
      }
    }
  }

  private def _descriptor_path(profile: String): Path = {
    Files.createDirectories(_descriptor_directory)
    val path = Files.createTempFile(_descriptor_directory, s"$profile-", ".yaml")
    Files.writeString(
      path,
      s"""kind: test-descriptor
         |config:
         |  textus.activation.fixture: $profile
         |execution:
         |  profile: standard
         |  key: server-operation-activation-spec
         |  time:
         |    mode: system
         |  random:
         |    mode: system
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private def _delete_descriptor(path: Path): Unit = {
    Files.deleteIfExists(path)
    ()
  }

  private def _without_test_runtime_flag[A](body: => A): A = {
    val previous = sys.props.get("textus.test")
    System.clearProperty("textus.test")
    try body
    finally {
      previous match {
        case Some(value) => System.setProperty("textus.test", value)
        case None => System.clearProperty("textus.test")
      }
    }
  }

  private def _capture_error(body: => Int): (Int, String) = {
    val error = new ByteArrayOutputStream()
    val stream = new PrintStream(error)
    try {
      val exitcode = Console.withErr(stream) {
        body
      }
      stream.flush()
      (exitcode, error.toString("UTF-8"))
    } finally {
      stream.close()
    }
  }
}
