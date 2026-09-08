package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiProviderComponent, SpiSelection}
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiMessage, AiRecordRequest, AiRecordResponse, AiRunner as AiRunnerSpi, AiRunnerSocket}
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  7, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentActivationLifecycleSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks {
  private val _e1 = afterWord("in spec:component-activation-lifecycle, example:E1, rules:R1,R2,R9, phase:70, slice:CA70-01B")
  private val _e2 = afterWord("in spec:component-activation-lifecycle, example:E2, rules:R3,R4,R6, phase:70, slice:CA70-01B")
  private val _e3 = afterWord("in spec:component-activation-lifecycle, example:E3, rules:R2,R3,R4, phase:70, slice:CA70-01B")
  private val _e4 = afterWord("in spec:component-activation-lifecycle, example:E4, rules:R4,R6, phase:70, slice:CA70-01B")
  private val _e5 = afterWord("in spec:component-activation-lifecycle, example:E5, rules:R4, phase:70, slice:CA70-01B")
  private val _e6 = afterWord("in spec:component-activation-lifecycle, example:E6, rules:R7, phase:70, slice:CA70-01B")
  private val _e7 = afterWord("in spec:component-activation-lifecycle, example:E7, rules:R4,R7, phase:70, slice:CA70-01B")
  private val _e8 = afterWord("in spec:component-activation-lifecycle, example:E8, rules:R7, phase:70, slice:CA70-01B")
  private val _e9 = afterWord("in spec:component-activation-lifecycle, example:E9, rules:R5, phase:70, slice:CA70-01B")
  private val _e10 = afterWord("in spec:component-activation-lifecycle, example:E10, rules:R7, phase:70, slice:CA70-01B")
  private val _e11 = afterWord("in spec:component-activation-lifecycle, example:E11, rules:R7, phase:70, slice:CA70-02A")
  private val _e12 = afterWord("in spec:component-activation-lifecycle, example:E12, rules:R4,R7, phase:70, slice:CA70-02A")

  "Component activation lifecycle" should {
    "preserve the public boundary and consumer neutrality" which {
      "E1 expose exactly the typed opt-in callback and R2 context primitives" must _e1 {
        "when the public callback and context are inspected through their typed and reflective surfaces" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R1,R2,R9; Example: E1")
          val activation = new ComponentActivation {
            override def activateC(context: ComponentActivationContext): Consequence[Unit] = Consequence.unit
          }

          When("the callback is assigned to its required function type and every context member is collected")
          val callback: ComponentActivationContext => Consequence[Unit] = activation.activateC
          val activationmethods = classOf[ComponentActivation].getDeclaredMethods.toVector
          val fields = classOf[ComponentActivationContext].getDeclaredFields.toVector
          val methods = classOf[ComponentActivationContext].getDeclaredMethods.toVector
          val members = fields.map(field => field.getName -> field.getType.getName) ++ methods.flatMap { method =>
            Vector(method.getName -> method.getReturnType.getName) ++
              method.getParameterTypes.toVector.map(parameter => method.getName -> parameter.getName)
          }
          val forbidden = Set("componentcreate", "componentinit", "factory", "repository", "classloader", "operation", "action", "bok")

          Then("the public callback is consequence-aware and the context has each admitted primitive exactly once")
          callback should not be null
          activationmethods.filter(_.getName == "activateC") should have size 1
          activationmethods.filter(_.getName == "activateC").foreach { method =>
            method.getParameterTypes.toVector shouldBe Vector(classOf[ComponentActivationContext])
            method.getReturnType shouldBe classOf[Consequence[?]]
          }
          fields.map(_.getType).groupMapReduce(value => value)(_ => 1)(_ + _) shouldBe Map(
            classOf[Subsystem] -> 1,
            classOf[org.goldenport.configuration.ResolvedConfiguration] -> 1,
            classOf[RunMode] -> 1,
            classOf[Instant] -> 1,
            classOf[ComponentActivationCancellation] -> 1
          )
          fields.map(_.getName).toSet shouldBe Set("subsystem", "configuration", "runMode", "deadline", "cancellation")

          Then("no construction, repository, class-loader, dispatch, action, or consumer-specific member type is exposed")
          members.exists { case (name, typename) =>
            val normalized = s"$name $typename".toLowerCase
            forbidden.exists(normalized.contains)
          } shouldBe false
        }
      }

      "E9 retain a capability-only activation surface without dispatch or implementation lookup" must _e9 {
        "when the public activation capability and context members are inspected reflectively" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R5; Example: E9")

          When("the declared activation and context members are collected without invoking a runtime route")
          val activationmethods = classOf[ComponentActivation].getDeclaredMethods.toVector
          val contextmembers =
            classOf[ComponentActivationContext].getDeclaredFields.toVector.map(field => field.getName -> field.getType.getName) ++
              classOf[ComponentActivationContext].getDeclaredMethods.toVector.flatMap { method =>
                Vector(method.getName -> method.getReturnType.getName) ++
                  method.getParameterTypes.toVector.map(parameter => method.getName -> parameter.getName)
              }
          val forbidden = Set("operation", "action", "dispatch", "execute", "factory", "repository", "componentcreate", "componentinit")

          Then("activation exposes only the capability callback and no dispatch or implementation lookup escape hatch")
          activationmethods
            .filter { method =>
              java.lang.reflect.Modifier.isPublic(method.getModifiers) &&
                !java.lang.reflect.Modifier.isStatic(method.getModifiers)
            }
            .map(_.getName).toSet shouldBe Set("activateC")
          contextmembers.exists { case (name, typename) =>
            val normalized = s"$name $typename".toLowerCase
            forbidden.exists(normalized.contains)
          } shouldBe false
        }
      }

      "E2 keep non-opting components activation-free across managed Server and non-Server construction" must _e2 {
        "when ordinary components are assembled in Server and each non-Server runtime mode" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R3,R4,R6; Example: E2")
          val ordinary = TestComponentFactory.emptySubsystem("activation-no-opt-in")
          val component = TestComponentFactory.create("ordinary", Protocol.empty, subsystem = ordinary)
          val modes = Table("mode", RunMode.Command, RunMode.Client, RunMode.Script, RunMode.ServerEmulator)
          val descriptor = _descriptor_path("controlled")
          var assembled = Vector.empty[(RunMode, Subsystem, Component, AtomicInteger)]
          var server: Option[Subsystem] = None
          var servercomponent: Option[Component] = None

          try {
            When("the ordinary component is admitted and the non-Server assemblies complete without activation")
            ordinary.add(component)
            assembled = modes.map { mode =>
              val callbackcount = new AtomicInteger(0)
              var created: Option[Component] = None
              val subsystem = CncfRuntime.buildSubsystem(
                extraComponents = current => {
                  val value = _activating_component(current, s"mode-${mode.name}", context => {
                    callbackcount.incrementAndGet()
                    Consequence.unit
                  })
                  created = Some(value)
                  Vector(value)
                },
                mode = Some(mode),
                args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
              )
              (mode, subsystem, created.get, callbackcount)
            }.toVector
            val serversubsystem = CncfRuntime.buildSubsystem(
              extraComponents = current => {
                val created = TestComponentFactory.create("server-ordinary", Protocol.empty, subsystem = current)
                servercomponent = Some(created)
                Vector(created)
              },
              mode = Some(RunMode.Server),
              args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
            )
            server = Some(serversubsystem)
            val serverresult = ComponentActivation.activateForServerRuntimeC(serversubsystem)

            Then("ordinary construction introduces no activation callback and every non-Server extra remains untouched")
            ordinary.findComponent(component.componentId) shouldBe Some(component)
            assembled.foreach { case (_, subsystem, created, callbackcount) =>
              subsystem.findComponent(created.componentId) shouldBe Some(created)
              callbackcount.get shouldBe 0
            }

            Then("a Server-managed ordinary component remains activation-free through the public production route")
            serverresult.toOption shouldBe Some(())
            servercomponent shouldBe defined
            servercomponent.foreach { value => serversubsystem.findComponent(value.componentId) shouldBe Some(value) }
            ComponentActivation.diagnosticFor(serversubsystem) shouldBe empty
            serversubsystem.systemNode.state shouldBe SystemNode.State.Running
          } finally {
            Subsystem.shutdownOwned(ordinary)
            assembled.foreach { case (_, subsystem, _, _) => Subsystem.shutdownOwned(subsystem) }
            server.foreach(Subsystem.shutdownOwned)
            _delete_descriptor(descriptor)
          }
        }
      }
    }

    "cover the completed runtime assembly and mode isolation" which {
      "E3 activate an opted-in component only after Server runtime assembly exposes its dependency and configuration" must _e3 {
        "when a Server subsystem is fully assembled with runtime extra components and startup SPI configuration" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R2,R3,R4; Example: E3")
          val observeddependency = new AtomicReference[Option[ComponentId]](None)
          val observedconfiguration = new AtomicReference[Option[String]](None)
          val observedcontext = new AtomicReference[Option[ComponentActivationContext]](None)
          val descriptor = _descriptor_path("assembled", executionprofile = "standard")
          val originaltestruntimeproperty = sys.props.get("textus.test")
          var activating: Option[Component] = None
          var socket: Option[Component & AiRunnerSocket] = None
          var assembled: Option[Subsystem] = None

          try {
            _without_test_runtime_flag {
              When("the public production activation route runs after complete Server assembly")
              val subsystem = CncfRuntime.buildSubsystem(
                extraComponents = current => {
                  val dependency = _initialized_component(
                    current,
                    "assembled-dependency",
                    new Component() with SpiProviderComponent {
                      def spiProviders: Vector[SpiProvider[?]] = Vector(AiRunnerProvider("activation-assembly"))
                    }
                  )
                  val consumer = _initialized_component(
                    current,
                    "assembled-consumer",
                    new Component() with ComponentActivation with AiRunnerSocket {
                      override def activateC(context: ComponentActivationContext): Consequence[Unit] = {
                        observedcontext.set(Some(context))
                        observeddependency.set(context.subsystem.findComponent(dependency.componentId).map(_.componentId))
                        observedconfiguration.set(RuntimeConfig.getString(context.configuration, "textus.activation.fixture"))
                        Consequence.unit
                      }
                    }
                  ).asInstanceOf[Component & AiRunnerSocket]
                  activating = Some(consumer)
                  socket = Some(consumer)
                  Vector(dependency, consumer)
                },
                mode = Some(RunMode.Server),
                args = Array(s"--textus.test.descriptor=$descriptor", "--no-default-components")
              )
              assembled = Some(subsystem)
              val result = ComponentActivation.activateForServerRuntimeC(subsystem)

              Then("the opted-in component sees the assembled public dependency and startup-imported typed configuration")
              result.toOption shouldBe Some(())
              activating shouldBe defined
              socket shouldBe defined
              subsystem.controlledTestExecutionEnabled shouldBe false
              activating.foreach(value => subsystem.findComponent(value.componentId) shouldBe Some(value))
              socket.foreach(_.isSpiInstalled shouldBe true)
              observeddependency.get shouldBe Some(TestComponentFactory.componentId("assembled-dependency"))
              observedconfiguration.get shouldBe Some("assembled")
              observedcontext.get shouldBe defined
              observedcontext.get.foreach { context =>
                context.subsystem shouldBe subsystem
                context.runMode shouldBe RunMode.Server
                context.deadline.isAfter(Instant.now.minusSeconds(30L)) shouldBe true
                context.cancellation.isCancelled shouldBe false
                context.configuration shouldBe subsystem.configuration
              }
              ComponentActivation.diagnosticFor(subsystem) shouldBe empty
              subsystem.systemNode.state shouldBe SystemNode.State.Running
            }

          } finally {
            assembled.foreach(Subsystem.shutdownOwned)
            _delete_descriptor(descriptor)
          }

          Then("the scoped production fixture restores the test-only runtime flag after assembly and activation")
          sys.props.get("textus.test") shouldBe originaltestruntimeproperty
        }
      }

      "E4 admit the package-private seam only for a controlled test subsystem" must _e4 {
        "when the seam is requested for ordinary and explicitly controlled subsystems" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R4,R6; Example: E4")
          val ordinary = new Subsystem(
            name = "activation-not-admitted",
            configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
            runMode = RunMode.Command
          )
          val controlled = TestComponentFactory.emptySubsystem("activation-admitted")
          val ordinarycount = new AtomicInteger(0)
          val controlledcount = new AtomicInteger(0)
          val controlledprobe = new ComponentActivationTestProbe
          ordinary.add(_activating_component(ordinary, "ordinary-seam", context => { ordinarycount.incrementAndGet(); Consequence.unit }))
          controlled.add(_activating_component(controlled, "controlled-seam", context => { controlledcount.incrementAndGet(); Consequence.unit }))

          try {
            When("the five-argument controlled-test activation seam is invoked")
            val ordinaryresult = _activate(ordinary, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, new ComponentActivationTestProbe)
            val controlledresult = _activate(controlled, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, controlledprobe)

            Then("only explicit controlled admission permits activation")
            ordinaryresult shouldBe a[Consequence.Failure[?]]
            ordinarycount.get shouldBe 0
            controlledresult.toOption shouldBe Some(())
            controlledcount.get shouldBe 1
            controlledprobe.coordinatorCleanupCount shouldBe 0
          } finally {
            Subsystem.shutdownOwned(ordinary)
            Subsystem.shutdownOwned(controlled)
          }
        }
      }
    }

    "make lifecycle execution deterministic" which {
      "E5 preserve final add/upsert order and reject post-activation duplicate callbacks" must _e5 {
        "when the coordinator is invoked on a bounded executor while the first callback is held" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R4; Example: E5")
          val subsystem = TestComponentFactory.emptySubsystem("activation-order")
          val firstentered = new CountDownLatch(1)
          val firstrelease = new CountDownLatch(1)
          val secondentered = new CountDownLatch(1)
          val order = new java.util.concurrent.ConcurrentLinkedQueue[String]()
          val firstreleased = new AtomicReference[Boolean](false)
          val first = _activating_component(subsystem, "order-first", context => {
              order.add("first")
              firstentered.countDown()
              firstreleased.set(firstrelease.await(5L, TimeUnit.SECONDS))
              Consequence.unit
            })
          val second = _activating_component(subsystem, "order-second", context => {
              order.add("second")
              secondentered.countDown()
              Consequence.unit
            })
          val third = _activating_component(subsystem, "order-third", context => {
              order.add("third")
              Consequence.unit
            })
          val postactivation = _activating_component(subsystem, "order-post-activation", context => {
              order.add("post-activation")
              Consequence.unit
            })
          subsystem.add(Vector(first, second))
          subsystem.upsert(Vector(first))
          subsystem.add(third)
          val executor = Executors.newSingleThreadExecutor()

          try {
            When("the first callback enters before its permit is released")
            val future = executor.submit(() => _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, new ComponentActivationTestProbe))
            val firstentryobserved = firstentered.await(5L, TimeUnit.SECONDS)

            Then("the second callback cannot enter before the first returns")
            firstentryobserved shouldBe true
            secondentered.getCount shouldBe 1L

            When("the first callback is released")
            firstrelease.countDown()
            val result = future.get(5L, TimeUnit.SECONDS)
            subsystem.add(postactivation)
            subsystem.upsert(Vector(first, third))
            val repeatedresult = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, new ComponentActivationTestProbe)
            executor.shutdown()
            val executorterminated = executor.awaitTermination(5L, TimeUnit.SECONDS)

            Then("the second and third callbacks preserve final add/upsert order, while post-activation add/upsert cannot duplicate activation")
            secondentered.await(5L, TimeUnit.SECONDS) shouldBe true
            result.toOption shouldBe Some(())
            repeatedresult.toOption shouldBe Some(())
            firstreleased.get shouldBe true
            executorterminated shouldBe true
            order.iterator().asScala.toVector shouldBe Vector("first", "second", "third")
          } finally {
            firstrelease.countDown()
            executor.shutdownNow()
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }

      "E6 bound a noncooperative callback at its deadline without later activation" must _e6 {
        "when callback entry is established before expiry and it ignores both cancellation and interruption until the caller has returned" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R7; Example: E6")
          val subsystem = TestComponentFactory.emptySubsystem("activation-noncooperative-deadline")
          val entered = new CountDownLatch(1)
          val release = new AtomicBoolean(false)
          val callbackfinished = new CountDownLatch(1)
          val cancellationobserved = new CountDownLatch(1)
          val interruptioncount = new AtomicInteger(0)
          val latercount = new AtomicInteger(0)
          val probe = new ComponentActivationTestProbe
          val deadline = Instant.now.plusMillis(250L)
          subsystem.add(Vector(
            _activating_component(subsystem, "noncooperative-first", context => {
              entered.countDown()
              while (!release.get) {
                if (context.cancellation.isCancelled)
                  cancellationobserved.countDown()
                try Thread.sleep(10L)
                catch { case _: InterruptedException => interruptioncount.incrementAndGet() }
              }
              if (context.cancellation.isCancelled)
                cancellationobserved.countDown()
              callbackfinished.countDown()
              Consequence.unit
            }),
            _activating_component(subsystem, "noncooperative-later", context => { latercount.incrementAndGet(); Consequence.unit })
          ))
          val executor = Executors.newSingleThreadExecutor()

          try {
            When("the bounded coordinator invokes the noncooperative callback before its deadline")
            val startedat = Instant.now
            val future = executor.submit(() => _activate(subsystem, RunMode.Server, deadline, new ComponentActivationCancellation, probe))
            val entryobserved = entered.await(5L, TimeUnit.SECONDS)
            val result = future.get(2L, TimeUnit.SECONDS)
            val returnedat = Instant.now
            val cancellationwasobserved = cancellationobserved.await(2L, TimeUnit.SECONDS)
            release.set(true)
            val callbackcompleted = callbackfinished.await(2L, TimeUnit.SECONDS)
            val failuretext = result match {
              case failure: Consequence.Failure[?] => failure.conclusion.show.toLowerCase
              case _ => ""
            }
            val diagnostic = ComponentActivation.diagnosticFor(subsystem)
            val diagnosticconclusions = _public_causal_conclusions(diagnostic.toVector.map(_.conclusion))
            executor.shutdown()
            val executorterminated = executor.awaitTermination(5L, TimeUnit.SECONDS)

            Then("the caller receives a terminal cancellation outcome by the bounded deadline even while the callback ignores interruption")
            entryobserved shouldBe true
            result shouldBe a[Consequence.Failure[?]]
            failuretext.exists(character => character.isLetter) shouldBe true
            Vector("deadline", "cancellation").exists(failuretext.contains) shouldBe true
            Duration.between(startedat, returnedat).toMillis should be < 1000L
            returnedat.isBefore(deadline.plusMillis(750L)) shouldBe true
            cancellationwasobserved shouldBe true
            interruptioncount.get should be >= 1
            callbackcompleted shouldBe true
            latercount.get shouldBe 0
            diagnostic shouldBe defined
            diagnosticconclusions should not be empty
            diagnosticconclusions.foreach(_.causes.foreach(_.getException shouldBe empty))
            probe.coordinatorCleanupCount shouldBe 1
            subsystem.systemNode.state shouldBe SystemNode.State.Stopped
            executorterminated shouldBe true
          } finally {
            release.set(true)
            executor.shutdownNow()
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }
    }

    "bound terminal failure and diagnostics" which {
      "E11 terminate an empty opt-in set before reporting success" must _e11 {
        "when a controlled activation request is already cancelled or expired on a subsystem without an opt-in component" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R7; Example: E11")
          val cases = Table("terminal", "cancelled", "expired", "released-after-deadline")

          val observations = cases.map { terminal =>
            val subsystem = TestComponentFactory.emptySubsystem(s"activation-empty-$terminal")
            val probe = new ComponentActivationTestProbe
            val cancellation = new ComponentActivationCancellation
            val deadline =
              if (terminal == "cancelled") {
                cancellation.cancel()
                Instant.now.plusSeconds(5L)
              } else
                Instant.now.minusMillis(1L)
            try {
              When(s"the already $terminal request reaches the coordinator before any callback traversal")
              val result = _activate(subsystem, RunMode.Server, deadline, cancellation, probe)
              val diagnostic = ComponentActivation.diagnosticFor(subsystem)

              (result, diagnostic, probe.coordinatorCleanupCount, subsystem.systemNode.state)
            } finally {
              Subsystem.shutdownOwned(subsystem)
            }
          }.toVector

          Then("each request returns the structured terminal failure, records owner-terminal evidence, and cannot publish readiness or success")
          observations.foreach { case (result, diagnostic, cleanupcount, state) =>
            result shouldBe a[Consequence.Failure[?]]
            result.toOption shouldBe empty
            diagnostic shouldBe defined
            cleanupcount shouldBe 1
            state shouldBe SystemNode.State.Stopped
          }
        }
      }

      "E12 bound a waiting controlled request without changing the active owner" must _e12 {
        "when an owner callback is held while a second request is already cancelled or expires" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R4,R7; Example: E12")
          val cases = Table("terminal", "cancelled", "expired", "released-after-deadline")

          cases.foreach { terminal =>
            val subsystem = TestComponentFactory.emptySubsystem(s"activation-waiter-$terminal")
            val entered = new CountDownLatch(1)
            val release = new CountDownLatch(1)
            val callbackcount = new AtomicInteger(0)
            val ownerprobe = new ComponentActivationTestProbe
            val waiterprobe = new ComponentActivationTestProbe
            val executor = Executors.newFixedThreadPool(2)
            subsystem.add(_activating_component(subsystem, s"waiter-owner-$terminal", context => {
              callbackcount.incrementAndGet()
              entered.countDown()
              release.await(5L, TimeUnit.SECONDS)
              Consequence.unit
            }))

            try {
              When(s"the first owner holds its callback and the second $terminal request waits for ownership")
              val owner = executor.submit(() => _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, ownerprobe))
              entered.await(5L, TimeUnit.SECONDS) shouldBe true
              val waitercancellation = new ComponentActivationCancellation
              val waiterdeadline =
                if (terminal == "cancelled") {
                  waitercancellation.cancel()
                  Instant.now.plusSeconds(5L)
                } else if (terminal == "released-after-deadline") {
                  Instant.now.plusMillis(75L)
                } else
                  Instant.now.minusMillis(1L)
              val startedat = Instant.now
              val waiter = executor.submit(() => _activate(subsystem, RunMode.Server, waiterdeadline, waitercancellation, waiterprobe))
              if (terminal == "released-after-deadline") {
                val remaining = Duration.between(Instant.now, waiterdeadline).toMillis
                if (remaining > 0L)
                  Thread.sleep(remaining + 1L)
                release.countDown()
              }
              val waiterresult = waiter.get(1L, TimeUnit.SECONDS)
              val returnedat = Instant.now
              release.countDown()
              val ownerresult = owner.get(5L, TimeUnit.SECONDS)
              val repeatedresult = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, new ComponentActivationTestProbe)

              Then("the waiting request remains a bounded local failure while the owner completes once and retains its successful terminal result")
              waiterresult shouldBe a[Consequence.Failure[?]]
              waiterresult.toOption shouldBe empty
              Duration.between(startedat, returnedat).toMillis should be < 1000L
              callbackcount.get shouldBe 1
              waiterprobe.coordinatorCleanupCount shouldBe 0
              waiterprobe.diagnostics shouldBe empty
              ownerresult.toOption shouldBe Some(())
              ownerprobe.coordinatorCleanupCount shouldBe 0
              repeatedresult.toOption shouldBe Some(())
              ComponentActivation.diagnosticFor(subsystem) shouldBe empty
              subsystem.systemNode.state shouldBe SystemNode.State.Running
            } finally {
              release.countDown()
              executor.shutdownNow()
              Subsystem.shutdownOwned(subsystem)
            }
          }
        }
      }

      "E7 retain a partial-success failure as the terminal no-retry result" must _e7 {
        "when one callback succeeds, the next fails, and a new callback is upserted before the repeat request" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R4,R7; Example: E7")
          val subsystem = TestComponentFactory.emptySubsystem("activation-partial-failure-once")
          val firstcount = new AtomicInteger(0)
          val failurecount = new AtomicInteger(0)
          val latercount = new AtomicInteger(0)
          val postterminalcount = new AtomicInteger(0)
          val firstprobe = new ComponentActivationTestProbe
          val secondprobe = new ComponentActivationTestProbe
          subsystem.add(Vector(
            _activating_component(subsystem, "partial-success", context => { firstcount.incrementAndGet(); Consequence.unit }),
            _activating_component(subsystem, "partial-failure", context => {
              failurecount.incrementAndGet()
              val failure: Consequence[Unit] = Consequence.serviceUnavailable("partial activation failure")
              failure
            }),
            _activating_component(subsystem, "partial-later", context => { latercount.incrementAndGet(); Consequence.unit })
          ))
          val postterminal = _activating_component(subsystem, "partial-postterminal", context => { postterminalcount.incrementAndGet(); Consequence.unit })

          try {
            When("the first request fails after partial success and a later component is upserted before a second request")
            val firstresult = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, firstprobe)
            subsystem.upsert(Vector(postterminal))
            val repeatedresult = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, secondprobe)

            Then("the first terminal failure is returned unchanged and no completed, later, or newly upserted callback is invoked again")
            firstresult shouldBe a[Consequence.Failure[?]]
            repeatedresult shouldBe a[Consequence.Failure[?]]
            repeatedresult.asInstanceOf[Consequence.Failure[?]].conclusion.show shouldBe
              firstresult.asInstanceOf[Consequence.Failure[?]].conclusion.show
            firstcount.get shouldBe 1
            failurecount.get shouldBe 1
            latercount.get shouldBe 0
            postterminalcount.get shouldBe 0
            firstprobe.coordinatorCleanupCount shouldBe 1
            secondprobe.coordinatorCleanupCount shouldBe 0
            ComponentActivation.diagnosticFor(subsystem) shouldBe defined
            subsystem.systemNode.state shouldBe SystemNode.State.Stopped
          } finally {
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }

      "E8 return one structured redacted diagnostic and coordinator cleanup for a callback exception" must _e8 {
        "when an opted-in callback throws a value containing secrets, a private path, a URL, and a BoK locator" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R7; Example: E8")
          val subsystem = TestComponentFactory.emptySubsystem("activation-redaction")
          val probe = new ComponentActivationTestProbe
          val secret = "activation-secret-value"
          val privatepath = "/private/activation/secret"
          val url = "https://activation.invalid/private"
          val boklocator = "textus://bok/component-activation-lifecycle"
          val latercount = new AtomicInteger(0)
          subsystem.add(Vector(
            _activating_component(subsystem, "diagnostic-failure", context => throw new IllegalStateException(s"secret=$secret path=$privatepath url=$url bok=$boklocator")),
            _activating_component(subsystem, "diagnostic-later", context => { latercount.incrementAndGet(); Consequence.unit })
          ))

          try {
            When("the controlled coordinator observes the callback exception")
            val result = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, probe)
            val failure = result match {
              case value: Consequence.Failure[?] => Some(value)
              case _ => None
            }
            val failuretext = failure.map(_.conclusion.show).getOrElse("")
            val diagnostics = probe.diagnostics
            val diagnostic = ComponentActivation.diagnosticFor(subsystem)
            val failurecauses = failure.toVector.flatMap(_.conclusion.causes)
            val diagnosticcauses = diagnostic.toVector.flatMap(_.conclusion.causes)
            val diagnostictext = diagnostic.toVector.flatMap(value => Vector(value.message, value.conclusion.show))
            val publicconclusions = failure.toVector.map(_.conclusion) ++ diagnostic.toVector.map(_.conclusion)
            val causalconclusions = _public_causal_conclusions(publicconclusions)

            Then("one bounded structured diagnostic records category, component, mode, and message without exception retention")
            result shouldBe a[Consequence.Failure[?]]
            failure shouldBe defined
            diagnostics should have size 1
            diagnostic shouldBe defined
            diagnostic.foreach { value =>
              value.category.toLowerCase should include("activation")
              value.mode shouldBe RunMode.Server
              value.message.length should be < 1024
              failure.foreach(_.conclusion.show shouldBe value.conclusion.show)
            }
            diagnosticcauses should not be empty
            diagnosticcauses.foreach(_.getException shouldBe empty)

            Then("the primary and every flattened public causal conclusion retain no exception accessor, rendered or structured secret, path, URL, or BoK locator, and later work is skipped")
            failurecauses should not be empty
            failurecauses.foreach(_.getException shouldBe empty)
            causalconclusions should not be empty
            causalconclusions.foreach { conclusion =>
              conclusion.causes.foreach(_.getException shouldBe empty)
            }
            (Vector(failuretext) ++ diagnostictext ++ causalconclusions.flatMap(_public_causal_forms)).foreach { text =>
              text should not include secret
              text should not include privatepath
              text should not include url
              text should not include boklocator
            }
            latercount.get shouldBe 0
            probe.coordinatorCleanupCount shouldBe 1
            subsystem.systemNode.state shouldBe SystemNode.State.Stopped
          } finally {
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }

      "E10 terminate a structured callback failure without retaining a raw Throwable" must _e10 {
        "when an opted-in callback returns a Consequence.Failure instead of throwing" in {
          Given("Spec: docs/spec/component-activation-lifecycle.md; Rules: R7; Example: E10")
          val subsystem = TestComponentFactory.emptySubsystem("activation-structured-failure")
          val probe = new ComponentActivationTestProbe
          val returned = new AtomicReference[Option[Consequence[Unit]]](None)
          val secret = "activation-structured-secret-7f3e"
          val privatepath = "/private/activation/structured-failure"
          val url = "https://activation.invalid/structured-failure"
          val boklocator = "textus://bok/activation-structured-failure"
          val latercount = new AtomicInteger(0)
          subsystem.add(Vector(
            _activating_component(subsystem, "structured-failure", context => {
              val failure: Consequence[Unit] = Consequence.serviceUnavailable(s"structured activation failure secret=$secret path=$privatepath url=$url bok=$boklocator")
              returned.set(Some(failure))
              failure
            }),
            _activating_component(subsystem, "structured-failure-later", context => {
              latercount.incrementAndGet()
              Consequence.unit
            })
          ))

          try {
            When("the controlled coordinator receives the returned structured failure")
            val result = _activate(subsystem, RunMode.Server, Instant.now.plusSeconds(5L), new ComponentActivationCancellation, probe)
            val failure = result match {
              case value: Consequence.Failure[?] => Some(value)
              case _ => None
            }
            val diagnostics = probe.diagnostics
            val diagnostic = ComponentActivation.diagnosticFor(subsystem)
            val failurecauses = failure.toVector.flatMap(_.conclusion.causes)
            val diagnosticcauses = diagnostic.toVector.flatMap(_.conclusion.causes)
            val failuretext = failure.toVector.map(_.conclusion.show)
            val diagnostictext = diagnostic.toVector.flatMap(value => Vector(value.message, value.conclusion.show))
            val publicconclusions = failure.toVector.map(_.conclusion) ++ diagnostic.toVector.map(_.conclusion)
            val causalconclusions = _public_causal_conclusions(publicconclusions)
            val publicrepresentations = failuretext ++ diagnostictext ++ causalconclusions.flatMap(_public_causal_forms)

            Then("the coordinator reports one terminal managed diagnostic and skips later activation")
            returned.get shouldBe defined
            returned.get.foreach(_ shouldBe a[Consequence.Failure[?]])
            result shouldBe a[Consequence.Failure[?]]
            diagnostics should have size 1
            diagnostic shouldBe defined
            diagnostic.foreach { value =>
              value.category.toLowerCase should include("activation")
              value.mode shouldBe RunMode.Server
              failure.foreach(_.conclusion.show shouldBe value.conclusion.show)
            }
            latercount.get shouldBe 0
            probe.coordinatorCleanupCount shouldBe 1
            subsystem.systemNode.state shouldBe SystemNode.State.Stopped

            Then("the primary and every flattened public causal conclusion retain no raw Throwable accessor, secret, path, URL, or BoK locator in rendered or structured form")
            failurecauses should not be empty
            diagnosticcauses should not be empty
            (failurecauses ++ diagnosticcauses).foreach(_.getException shouldBe empty)
            causalconclusions should not be empty
            causalconclusions.foreach { conclusion =>
              conclusion.causes.foreach(_.getException shouldBe empty)
            }
            publicrepresentations.foreach { text =>
              text should not include secret
              text should not include privatepath
              text should not include url
              text should not include boklocator
            }
          } finally {
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }
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
    ) ++ _public_structured_causal_forms(conclusion)

  private def _public_structured_causal_forms(conclusion: Conclusion): Vector[String] =
    Vector("toJsonString", "toRecord").flatMap { name =>
      conclusion.getClass.getMethods.iterator
        .find(method => method.getName == name && method.getParameterCount == 0)
        .flatMap { method =>
          try Option(method.invoke(conclusion)).map(_.toString)
          catch { case _: Throwable => None }
        }
    }

  private def _activate(
    subsystem: Subsystem,
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: ComponentActivationTestProbe
  ): Consequence[Unit] =
    ComponentActivation.activateForControlledTestC(subsystem, mode, deadline, cancellation, probe)

  private def _activating_component(
    subsystem: Subsystem,
    name: String,
    callback: ComponentActivationContext => Consequence[Unit]
  ): Component = {
    val componentid = TestComponentFactory.componentId(name)
    val component = new Component() with ComponentActivation {
      override def activateC(context: ComponentActivationContext): Consequence[Unit] = callback(context)
    }
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _initialized_component(
    subsystem: Subsystem,
    name: String,
    component: Component
  ): Component = {
    val componentid = TestComponentFactory.componentId(name)
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private final case class AiRunnerProvider(name: String) extends SpiProvider[AiRunnerSpi] {
    def supports(contract: SpiContract[AiRunnerSpi], selection: SpiSelection)(using ExecutionContext): Boolean =
      contract.name == "ai-runner" && contract.runtimeClass == classOf[AiRunnerSpi]

    def provide(contract: SpiContract[AiRunnerSpi], selection: SpiSelection)(using ExecutionContext): Consequence[AiRunnerSpi] =
      Consequence.success(AiRunner(name))
  }

  private final case class AiRunner(name: String) extends AiRunnerSpi {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.serviceUnavailable("generateRecord is not used by this spec")

    def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
      Consequence.success(AiChatResponse(AiMessage("assistant", name)))
  }

  private val _descriptor_directory: Path =
    Paths.get("target", "cncf-component-activation-lifecycle-spec")

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

  private def _descriptor_path(profile: String, executionprofile: String = "controlled"): Path = {
    Files.createDirectories(_descriptor_directory)
    val path = Files.createTempFile(_descriptor_directory, s"$profile-", ".yaml")
    val executionconfiguration =
      if (executionprofile == "standard")
        s"""execution:
           |  profile: $executionprofile
           |  key: component-activation-lifecycle-spec
           |  time:
           |    mode: system
           |  random:
           |    mode: system""".stripMargin
      else
        s"""execution:
           |  profile: $executionprofile
           |  key: component-activation-lifecycle-spec
           |  time:
           |    mode: manual
           |    start-at: 2026-09-07T00:00:00Z
           |  random:
           |    mode: seeded
           |    seed: component-activation-lifecycle-spec""".stripMargin
    Files.writeString(
      path,
      s"""kind: test-descriptor
         |config:
         |  textus.activation.fixture: $profile
         |assembly:
         |  spi:
         |    bindings:
         |      - socket:
         |          component: org.goldenport.cncf.test.AssembledConsumer
         |          contract: ai-runner
         |        provider:
         |          component: org.goldenport.cncf.test.AssembledDependency
         |$executionconfiguration
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private def _delete_descriptor(path: Path): Unit = {
    Files.deleteIfExists(path)
    ()
  }
}
