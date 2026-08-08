package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.record.Record
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.*
import org.goldenport.cncf.component.testutil.LegacyDeferredReleaseCarFixture
import org.goldenport.cncf.config.{CncfConfigurationTarget, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.projection.{DescribeProjection, HelpProjection}
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.corpus.CorpusComponentFactory
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityCompatibilityAcceptanceSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private val _work_root = Path.of(
    "target",
    "cncf-test",
    "work",
    "phase56-component-identity-compatibility-acceptance"
  ).toAbsolutePath.normalize
  private val _e1 = afterWord(
    "in spec:phase-56-component-identity-compatibility-acceptance, example:E1, rules:CID06-R1,R2,R3,R4,R5,R6,R7,R8,R9,R10, phase:56, slice:CID-06E"
  )

  override def beforeAll(): Unit = {
    Files.createDirectories(_work_root)
    val filearea = new FileWorkArea(
      WorkAreaId("phase56_cid06e"),
      WorkArea.Limit(None, None, None),
      _work_root
    )
    GlobalContext.set(GlobalContext(WorkAreaSpace(WorkAreaGroup(filearea))))
  }

  override def afterAll(): Unit = {
    try _delete_recursively(_work_root)
    finally GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
  }

  "Phase 56 Component identity compatibility acceptance" should {
    "real assembly and runtime compatibility" which {
      "E1 keep an exact deferred Corpus release usable while canonical identity remains authoritative" must _e1 {
        "when one legacy request crosses assembly, repository, ClassLoader, routing, projection, observability, and shutdown" in {
          Given("Spec: docs/notes/phase-56-cid06e-compatibility-step-acceptance-plan.md; Rules: CID06-R1 through CID06-R10; Example: E1; one exact schema-2 textus-corpus:0.1.0 CAR and a real assembly binding named Corpus")
          _with_work_directory { root =>
            val canonicalid = ComponentId("org.simplemodeling.textus.Corpus")
            val legacyid = "Corpus"
            val service = CorpusComponentFactory.serviceName
            val operation = CorpusComponentFactory.operationName
            val canonicalselector = s"${canonicalid.name}.$service.$operation"
            val legacyselector = s"$legacyid.$service.$operation"
            val entry = ComponentIdentityDeferredReleaseRegistry.default.entries
              .find(_.componentid == canonicalid)
              .getOrElse(fail("Corpus deferred-release entry is missing"))
            val expanded = LegacyDeferredReleaseCarFixture.writeDirectory(
              root.resolve("expanded-textus-corpus-0.1.0"),
              entry
            )
            val packed = LegacyDeferredReleaseCarFixture.pack(
              expanded,
              root.resolve("textus-corpus-0.1.0.car")
            )
            val assembly = _write_assembly_descriptor(root, legacyid)
            val testdescriptor = Files.writeString(
              root.resolve("test.yaml"),
              "kind: test-descriptor\n",
              StandardCharsets.UTF_8
            )
            val descriptor = GenericSubsystemDescriptor.load(assembly).toOption
              .getOrElse(fail("CID-06E assembly descriptor did not load"))
            val configuration = ResolvedConfiguration(
              Configuration(Map(
                RuntimeConfig.repositoryDirKey ->
                  ConfigurationValue.StringValue(s"component-file:$packed"),
                RuntimeConfig.TEST_DESCRIPTOR_KEY ->
                  ConfigurationValue.StringValue(testdescriptor.toString)
              )),
              ConfigurationTrace.empty
            )
            val owner = GlobalRuntimeContext.create(
              "phase56-cid06e",
              RuntimeConfig.default,
              configuration,
              ExecutionContext.create().observability,
              AliasResolver.empty
            )
            val previousruntime = GlobalRuntimeContext.current
            var subsystem: Option[Subsystem] = None
            var shutdowncomplete = false

            try {
              GlobalRuntimeContext.current = Some(owner)

              When("the exact packed release is extracted and then admitted through GenericSubsystemFactory using the configured component repository as the only candidate authority")
              val extraction = CarExtractor.withExtracted(
                packed,
                GlobalContext.globalContext.workAreaSpace
              ) { extracted =>
                Consequence.success((
                  extracted.rawDescriptor,
                  extracted.descriptor,
                  extracted.deferredRelease
                ))
              }.toOption.getOrElse(fail("exact deferred packed CAR did not extract"))
              val runtime = GenericSubsystemFactory.defaultWithScope(
                descriptor = descriptor,
                context = owner,
                mode = Some(RunMode.Server),
                configuration = configuration,
                aliasResolver = AliasResolver.empty
              )
              subsystem = Some(runtime)
              runtime.admitRuntimeConfigurationBindingsC(
                ConfigurationBindingCollection.empty[CncfConfigurationTarget]
              ).getOrElse(fail("runtime configuration bindings were not admitted"))
              val admitted = runtime.descriptor.getOrElse(fail("admitted descriptor is missing"))
              val binding = admitted.componentBindings match {
                case Vector(value) => value
                case values => fail(s"expected one admitted binding but found ${values.size}")
              }
              val component = runtime.findComponent(canonicalid)
                .getOrElse(fail("canonical Corpus component is missing"))
              val loader = runtime.componentClassLoaderSnapshot match {
                case Vector(value: ComponentLocalFirstClassLoader) => value
                case values => fail(s"expected one retained component-local loader but found ${values.size}")
              }

              Then("raw schema-2 identity and ABI evidence remain inspectable while the effective descriptor, assembly binding, Component/Core, instance, artifact, cache, and resolver slot are canonical")
              extraction._1.flatMap(_.schemaVersion) shouldBe Some(2)
              extraction._1.flatMap(_.name) shouldBe Some("textus-corpus")
              extraction._1.flatMap(_.componentName) shouldBe Some(legacyid)
              extraction._2.schemaVersion shouldBe Some(2)
              extraction._2.componentId shouldBe Some(canonicalid)
              extraction._2.name shouldBe Some(canonicalid.name)
              extraction._2.componentName shouldBe Some(canonicalid.name)
              extraction._3 shouldBe Some(entry)
              binding.componentId shouldBe Some(canonicalid)
              binding.runtimeComponentName shouldBe canonicalid.name
              component.componentId shouldBe canonicalid
              component.core.componentId shouldBe canonicalid
              component.name shouldBe canonicalid.name
              component.instanceId shouldBe ComponentInstanceId.default(canonicalid)
              component.componentDescriptors.map(_.componentId).distinct shouldBe Vector(Some(canonicalid))
              component.deferredReleaseProvenance shouldBe Some(entry)
              component.artifactMetadata.flatMap(_.componentId) shouldBe Some(canonicalid)
              component.artifactMetadata.map(_.name) shouldBe Some(canonicalid.name)
              component.artifactMetadata.map(_.version) shouldBe Some("0.1.0")
              runtime.findComponent(canonicalid) shouldBe Some(component)
              runtime.resolver.resolve(canonicalselector) shouldBe ResolutionResult.Resolved(
                canonicalselector,
                canonicalid.name,
                service,
                operation
              )
              loader.closeInvocationCount shouldBe 0
              ComponentId.parseC(legacyid).toOption shouldBe empty

              When("canonical public routing and the unique typed legacy selector execute the unchanged generated-Core operation")
              val canonicalresponse = runtime.executeOperationResponse(Request.of(
                component = canonicalid.name,
                service = service,
                operation = operation
              ))
              val legacyresponse = runtime.executeOperationResponse(Request.of(
                component = legacyid,
                service = service,
                operation = operation
              ))

              Then("both routes return the same scalar result and the resolver exposes only the canonical slot")
              canonicalresponse.getOrElse(fail(canonicalresponse.display)) shouldBe
                OperationResponse.Scalar(CorpusComponentFactory.responseBody)
              legacyresponse.getOrElse(fail(legacyresponse.display)) shouldBe
                OperationResponse.Scalar(CorpusComponentFactory.responseBody)
              runtime.resolver.resolve(legacyselector) shouldBe ResolutionResult.Resolved(
                canonicalselector,
                canonicalid.name,
                service,
                operation
              )

              When("Help and Meta are requested through the accepted legacy presentation alias")
              val help = HelpProjection.projectModel(component, Some(legacyselector))
              val meta = DescribeProjection.project(component, Some(legacyid))

              Then("both projections retain canonical authority and Help advertises Corpus only as one accepted compatibility alias")
              help.`type` shouldBe "operation"
              help.componentId shouldBe Some(canonicalid.name)
              help.selector.map(_.canonical) shouldBe Some(canonicalselector)
              help.selector.map(_.accepted) shouldBe Some(Vector(canonicalselector, legacyselector))
              meta.getString("type") shouldBe Some("component")
              meta.getString("name") shouldBe Some(canonicalid.name)

              When("the owning Admin assembly report is read after assembly, runtime, Help, and Meta compatibility observations")
              val reportresult = runtime.executeOperationResponse(Request.of(
                component = "org.goldenport.cncf.Admin",
                service = "assembly",
                operation = "report"
              ))
              val report = reportresult.toOption.collect {
                case OperationResponse.RecordResponse(record) => record
              }.getOrElse(fail(s"Admin assembly report was not a RecordResponse: $reportresult"))
              val warningsrecord = report.getRecord("warnings")
                .getOrElse(fail("Admin warning projection is missing"))
              val warningrecords = _warning_records(warningsrecord)
              val deferredwarnings = owner.assemblyReport.warnings
                .filter(_.reason.contains("exact-deferred-release"))
              val aliaswarnings = owner.assemblyReport.warnings
                .filter(_.kind == "component-identity-compatibility")

              Then("the owner exposes bounded alias decisions and one deduplicated exact-release warning with complete removal evidence")
              deferredwarnings should have size 1
              deferredwarnings.head.componentName shouldBe canonicalid.name
              deferredwarnings.head.message should include("release=0.1.0")
              deferredwarnings.head.message should include("legacy-artifact=textus-corpus")
              deferredwarnings.head.message should include("migration-owner=textus-corpus")
              deferredwarnings.head.message should include("reason=exact-deferred-release")
              aliaswarnings should not be empty
              aliaswarnings.foreach(_.componentName shouldBe canonicalid.name)
              aliaswarnings.flatMap(_.reason).mkString("\n") should include("surface=assembly-binding")
              aliaswarnings.flatMap(_.reason).mkString("\n") should include("surface=runtime-selector")
              warningsrecord.getString("status") shouldBe Some("warning")
              warningsrecord.getInt("warningCount") shouldBe Some(owner.assemblyReport.warnings.size)
              warningrecords.count(_.getString("kind").contains("component-identity-deferred-release")) shouldBe 1
              warningrecords.find(_.getString("kind").contains("component-identity-deferred-release"))
                .flatMap(_.getString("message")) shouldBe Some(deferredwarnings.head.message)

              When("an unknown qualified selector, a malformed selector, and a neighboring legacy spelling are routed")
              val warningcount = owner.assemblyReport.warnings.size
              val unknown = runtime.executeOperationResponse(Request.of(
                component = "org.unknown.Corpus",
                service = service,
                operation = operation
              ))
              val malformed = runtime.executeOperationResponse(Request.of(
                component = "Corpus..compatibility",
                service = service,
                operation = operation
              ))
              val neighboring = runtime.executeOperationResponse(Request.of(
                component = "CorpusNeighbor",
                service = service,
                operation = operation
              ))

              Then("all three fail closed without compatibility fallback, new warnings, or deferred identity scope leakage")
              unknown.isSuccess shouldBe false
              malformed.isSuccess shouldBe false
              neighboring.isSuccess shouldBe false
              owner.assemblyReport.warnings should have size warningcount
              ComponentId.parseC(legacyid).toOption shouldBe empty

              When("the owning subsystem is shut down repeatedly")
              val firstshutdown = runtime.shutdownC()
              val secondshutdown = runtime.shutdownC()

              Then("the retained component ClassLoader closes exactly once and strict ComponentId behavior remains restored")
              firstshutdown.isSuccess shouldBe true
              secondshutdown.isSuccess shouldBe true
              loader.closeInvocationCount shouldBe 1
              ComponentId.parseC(legacyid).toOption shouldBe empty
              shutdowncomplete = true
            } finally {
              if (!shutdowncomplete)
                subsystem.foreach(Subsystem.shutdownOwned)
              GlobalRuntimeContext.current = previousruntime
            }
          }
        }
      }
    }
  }

  private def _write_assembly_descriptor(
    root: Path,
    legacyid: String
  ): Path =
    Files.writeString(
      root.resolve("assembly-descriptor.yaml"),
      s"""subsystem: phase56-component-identity-compatibility-acceptance
         |subsystemCapabilities:
         |  providers:
         |    - name: runtime-facilities
         |      component: org.simplemodeling.textus.Corpus
         |      provides:
         |        - datastore.optimistic-concurrency@1
         |        - datastore.persistent@1
         |        - datastore.transactional@1
         |        - user-context.current@1
         |components:
         |  - name: $legacyid
         |    version: 0.1.0
         |""".stripMargin,
      StandardCharsets.UTF_8
    )

  private def _warning_records(record: Record): Vector[Record] =
    record.getAny("warnings").collect {
      case values: Seq[?] => values.collect { case value: Record => value }.toVector
    }.getOrElse(Vector.empty)

  private def _with_work_directory[A](body: Path => A): A = {
    val work = Files.createTempDirectory(_work_root, "acceptance-")
    try body(work)
    finally _delete_recursively(work)
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
}
