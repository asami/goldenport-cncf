package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentLocalFirstClassLoader, ComponentOrigin}
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.config.{CncfConfigurationTarget, RuntimeConfig}
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.subsystem.fixture.Phase56NamespaceIsolatedRuntimeFixture
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56NamespaceIsolatedRuntimeIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-56-namespace-isolated-runtime-integration, example:E1, rules:CID05D-R1,R2,R3,R4, phase:56, slice:CID-05D"
  )

  override def beforeAll(): Unit =
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))

  "Phase 56 namespace-isolated runtime integration" should {
    "E1 preserve same-local-id components through real descriptor, CAR, ClassLoader, and public routing" must _e1 {
      "when two schema-3 CAR components share Shared only as presentation" in {
        Given("a real assembly descriptor and two release 0.6.0 component CARs")
        _with_work_directory { root =>
          val repository = Files.createDirectories(root.resolve("component-repository"))
          val alphaid = ComponentId("org.alpha.textus.Shared")
          val betaid = ComponentId("org.beta.textus.Shared")
          val assembly = _write_assembly_descriptor(root, alphaid, betaid)
          val testdescriptor = Files.writeString(
            root.resolve("test.yaml"),
            "kind: test-descriptor\n",
            StandardCharsets.UTF_8
          )
          _write_component_car(
            repository.resolve("alpha-shared.car"),
            root.resolve("alpha-assets"),
            alphaid,
            Vector(
              classOf[fixture.phase56alpha.ComponentFactory],
              classOf[fixture.phase56alpha.SharedComponent]
            )
          )
          _write_component_car(
            repository.resolve("beta-shared.car"),
            root.resolve("beta-assets"),
            betaid,
            Vector(
              classOf[fixture.phase56beta.ComponentFactory],
              classOf[fixture.phase56beta.SharedComponent]
            )
          )
          val descriptor = GenericSubsystemDescriptor.load(assembly).toOption
            .getOrElse(fail("assembly descriptor did not load"))
          val configuration = ResolvedConfiguration(
            Configuration(Map(
              RuntimeConfig.repositoryDirKey ->
                ConfigurationValue.StringValue(s"component-dir:$repository"),
              RuntimeConfig.TEST_DESCRIPTOR_KEY ->
                ConfigurationValue.StringValue(testdescriptor.toString)
            )),
            ConfigurationTrace.empty
          )
          var subsystem: Option[Subsystem] = None
          var shutdowncomplete = false

          try {
            When("GenericSubsystemFactory admits and materializes both CARs")
            val runtime = GenericSubsystemFactory.default(descriptor, configuration = configuration)
            subsystem = Some(runtime)
            runtime.admitRuntimeConfigurationBindingsC(
              ConfigurationBindingCollection.empty[CncfConfigurationTarget]
            ).getOrElse(fail("runtime configuration bindings were not admitted"))
            val bindings = runtime.descriptor.toVector.flatMap(_.componentBindings)
            val components = runtime.components.filter(component =>
              Set(alphaid, betaid).contains(component.componentId)
            )
            val byid = components.map(component => component.componentId -> component).toMap
            val loaders = runtime.componentClassLoaderSnapshot match {
              case Vector(first, second) => Vector(first, second).map {
                case loader: ComponentLocalFirstClassLoader => loader
                case loader => fail(s"registered component loader is not component-local: $loader")
              }
              case values => fail(s"expected two registered component loaders but found ${values.size}")
            }

            Then("descriptor admission promotes only the exact qualified declarations in order")
            bindings.map(_.componentId) shouldBe Vector(Some(alphaid), Some(betaid))

            And("Core, default instance, artifact, repository origin, and display retain distinct roles")
            components should have size 2
            components.map(_.componentId).toSet shouldBe Set(alphaid, betaid)
            components.foreach { component =>
              component.name shouldBe component.componentId.name
              component.instanceId shouldBe ComponentInstanceId.default(component.componentId)
              component.displayName shouldBe "Shared"
              component.artifactMetadata.flatMap(_.componentId) shouldBe Some(component.componentId)
              component.artifactMetadata.map(_.version) shouldBe Some("0.6.0")
              component.origin shouldBe a[ComponentOrigin.Repository]
            }
            byid(alphaid).artifactMetadata.flatMap(_.archivePath)
              .map(Path.of(_).getFileName.toString) shouldBe Some("alpha-shared.car")
            byid(betaid).artifactMetadata.flatMap(_.archivePath)
              .map(Path.of(_).getFileName.toString) shouldBe Some("beta-shared.car")
            loaders.foreach(_.closeInvocationCount shouldBe 0)

            When("public Request routing selects each qualified component")
            val alpharesponse = runtime.executeOperationResponse(
              Request.of(
                component = alphaid.name,
                service = Phase56NamespaceIsolatedRuntimeFixture.serviceName,
                operation = Phase56NamespaceIsolatedRuntimeFixture.operationName
              )
            )
            val betaresponse = runtime.executeOperationResponse(
              Request.of(
                component = betaid.name,
                service = Phase56NamespaceIsolatedRuntimeFixture.serviceName,
                operation = Phase56NamespaceIsolatedRuntimeFixture.operationName
              )
            )

            Then("each namespace reaches its own successful scalar operation")
            alpharesponse.getOrElse(fail(alpharesponse.display)) shouldBe OperationResponse.Scalar("alpha")
            betaresponse.getOrElse(fail(betaresponse.display)) shouldBe OperationResponse.Scalar("beta")

            And("bare presentation is not selected by ComponentSpace or resolver")
            runtime.findComponent("Shared") shouldBe None
            runtime.resolver.resolve(
              s"Shared.${Phase56NamespaceIsolatedRuntimeFixture.serviceName}.${Phase56NamespaceIsolatedRuntimeFixture.operationName}"
            ) shouldBe ResolutionResult.Ambiguous(
              "Shared",
              Vector(
                s"${alphaid.name}.${Phase56NamespaceIsolatedRuntimeFixture.serviceName}.${Phase56NamespaceIsolatedRuntimeFixture.operationName}",
                s"${betaid.name}.${Phase56NamespaceIsolatedRuntimeFixture.serviceName}.${Phase56NamespaceIsolatedRuntimeFixture.operationName}"
              )
            )

            When("the owning subsystem is shut down repeatedly after routing")
            val firstshutdown = runtime.shutdownC()
            val secondshutdown = runtime.shutdownC()
            shutdowncomplete = true

            Then("each successful CAR loader is closed exactly once by the owning subsystem")
            firstshutdown.isSuccess shouldBe true
            secondshutdown.isSuccess shouldBe true
            loaders.foreach(_.closeInvocationCount shouldBe 1)
          } finally {
            if (!shutdowncomplete)
              subsystem.foreach(Subsystem.shutdownOwned)
          }
        }
      }
    }
  }

  private def _write_assembly_descriptor(
    root: Path,
    alphaid: ComponentId,
    betaid: ComponentId
  ): Path = {
    val target = root.resolve("assembly-descriptor.yaml")
    Files.writeString(
      target,
      s"""subsystem: phase56-namespace-isolated-runtime
         |components:
         |  - name: ${alphaid.name}
         |    version: 0.6.0
         |  - name: ${betaid.name}
         |    version: 0.6.0
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    target
  }

  private def _write_component_car(
    target: Path,
    assets: Path,
    componentid: ComponentId,
    classes: Vector[Class[?]]
  ): Unit = {
    val jar = _create_component_jar(assets.resolve("component-main.jar"), classes)
    val descriptor = assets.resolve("component-descriptor.json")
    Files.createDirectories(assets)
    Files.writeString(descriptor, _canonical_descriptor_json(componentid), StandardCharsets.UTF_8)
    CarArchiveFixture.write(
      target,
      Seq(
        "component/main.jar" -> jar,
        "component-descriptor.json" -> descriptor
      )
    )
  }

  private def _create_component_jar(
    target: Path,
    classes: Vector[Class[?]]
  ): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      classes.foreach { cls =>
        val entry = s"${cls.getName.replace('.', '/')}.class"
        val resource = Option(getClass.getClassLoader.getResource(entry))
          .getOrElse(fail(s"missing test fixture class resource: $entry"))
        zip.putNextEntry(new ZipEntry(entry))
        Using.resource(resource.openStream())(_.transferTo(zip))
        zip.closeEntry()
      }
    }
    target
  }

  private def _canonical_descriptor_json(componentid: ComponentId): String = {
    val segments = componentid.name.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"0.6.0"}}"""
  }

  private def _with_work_directory[A](body: Path => A): A = {
    val workroot = Files.createDirectories(
      Path.of("target", "cncf-test", "work", "phase56-namespace-isolated-runtime-integration-spec")
        .toAbsolutePath
        .normalize
    )
    val work = Files.createTempDirectory(workroot, "runtime-")
    try body(work)
    finally {
      if (Files.exists(work)) {
        Using.resource(Files.walk(work)) { stream =>
          stream
            .sorted(Comparator.reverseOrder())
            .iterator()
            .asScala
            .foreach(Files.deleteIfExists)
        }
      }
      Files.exists(work) shouldBe false
    }
  }
}
