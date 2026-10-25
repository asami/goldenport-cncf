package org.goldenport.cncf.processexecution

import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.resource.{ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 23, 2026
 * @version Oct. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class HostSourceReadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import HostSourceReadSpecSupport.*

  "E1 Host-source target validation" must afterWord("in spec:host-source-read, example:E1, rules:R1, phase:6.4") {
    "retain exact bounded lower-hyphen identities and refuse normalization or path syntax" in {
      Given("docs/spec/host-source-read.md R1 E1, safe logical identifiers and malformed alternatives")
      val invalid = Vector(null, "", " source", "source ", "Source", "source_1", "../source", "source/one",
        "-source", "source-", "source--one", "a" * 48, "source\n")
      val names = for {
        count <- Gen.choose(1, 6)
        parts <- Gen.listOfN(count, Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.take(6).mkString))
      } yield parts.mkString("-")
      When("the public parser receives exact logical source names")
      val property = Prop.forAll(names)(name => HostSourceTarget.parseC(name).toOption.exists(_.value == name))
      val rejected = invalid.map(HostSourceTarget.parseC)
      Then("safe names retain their identity and unsafe or overlong names cannot become capabilities")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property).passed shouldBe true
      HostSourceTarget.parseC("a" * 47).isSuccess shouldBe true
      rejected.foreach(_.isFaillure shouldBe true)
    }
  }

  "E2 Host-source payload validation" must afterWord("in spec:host-source-read, example:E2, rules:R2, phase:6.4") {
    "bound actual UTF-8 bytes and refuse unpaired surrogates without replacement" in {
      Given("docs/spec/host-source-read.md R2 E2, exact byte boundaries and malformed Unicode")
      val text = Gen.listOf(Gen.oneOf("a", "あ", "😀", "\n")).map(_.mkString)
      When("typed payloads encode complete text before admission")
      val property = Prop.forAll(text)(value => HostSourcePayload.fromUtf8C(value).toOption
        .exists(_.bytes == value.getBytes("UTF-8").toVector))
      val accepted = Vector("", "a" * 65536, "あ" * 21845).map(HostSourcePayload.fromUtf8C)
      val rejected = Vector(null, "a" * 65537, "あ" * 21846, "\uD800", "\uDC00").map(HostSourcePayload.fromUtf8C)
      Then("valid content is exact, the 64 KiB boundary is inclusive and malformed or excessive content fails")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property).passed shouldBe true
      accepted.foreach(_.isSuccess shouldBe true)
      rejected.foreach(_.isFaillure shouldBe true)
    }
  }

  "E3 Host-source fixed-program admission" must afterWord("in spec:host-source-read, example:E3, rules:R3, phase:6.4") {
    "derive only the fixed capability and admit the actual component with an empty caller argument vector" in {
      Given("docs/spec/host-source-read.md R3 E3, the registered reader with fixed runtime arguments and its component grant")
      val context = contextFor(definition(), result())
      When("the dedicated internal builder resolves a validated source request")
      val execution = HostSourceRead.resolveC(context.cncfCore.scope, target, payload).toOption.get
      Then("only payload bytes and the derived capability are caller-controlled, with all other request fields defaulted")
      execution.request.capability shouldBe capability
      execution.request.arguments shouldBe empty
      execution.request.input shouldBe ProcessExecutionInput.Bytes(payload.bytes)
      execution.request.workingDirectory shouldBe None
      execution.request.outputs shouldBe empty
      execution.request.inputFiles shouldBe empty
      execution.request.resourceTrees shouldBe empty
      execution.request.requestedLimits shouldBe ProcessExecutionLimits.empty
      execution.effectiveArguments shouldBe Vector("--fixed-source", "selected")
      execution.definition.safeProgramIdentity shouldBe capability.print
    }
  }

  "E4 Host-source authority refusal" must afterWord("in spec:host-source-read, example:E4, rules:R3, phase:6.4") {
    "refuse wrong identities, wider templates, file permissions and missing or impersonated grants" in {
      Given("docs/spec/host-source-read.md R3 E4, otherwise admissible declarations that widen the fixed reader contract")
      val artifact = ProcessArtifactName.parseC("extra").toOption.get
      val definitions = Vector(
        definition(identity = "different-reader"),
        definition(policy = ProcessArgumentPolicy(Vector("--fixed-source", "selected"))),
        definition(policy = templatePolicy(Set(ProcessArgumentTemplate(Vector.empty),
          ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("extra")))))),
        definition(policy = templatePolicy(Set(ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("extra")))))),
        definition(artifacts = Set(artifact)), definition(workingDirectory = true),
        definition(inputFiles = Set(artifact), inputPaths = Map(artifact -> WorkAreaRelativePath.parseC("extra.txt").toOption.get)),
        definition(resourceTrees = Map(ResourceTreeReference.parseC("extra-tree").toOption.get -> ResourceTreeLimits())))
      When("ordinary admission and the dedicated definition check resolve each widened or unauthorized source")
      val results = definitions.map(d => HostSourceRead.resolveC(contextFor(d, result()).cncfCore.scope, target, payload)) ++
        Vector(contextFor(definition(), result(), granted = false),
          contextFor(definition(), result(), actualOwner = ComponentId("org.example.Other")),
          contextFor(definition(), result(), installed = false)).map(c => HostSourceRead.resolveC(c.cncfCore.scope, target, payload))
      Then("no refused declaration can yield a resolved source execution")
      results.foreach(_.isFaillure shouldBe true)
    }
  }

  "E5 Host-source terminal mapping" must afterWord("in spec:host-source-read, example:E5, rules:R4, phase:6.4") {
    "preserve every unsuccessful terminal receipt and reserve Unconfirmed for uncertain termination" in {
      Given("docs/spec/host-source-read.md R4 E5, actual receipts for every failed or uncertain termination")
      val receipts = failedTerminations.map(t => result().copy(termination = t))
      val uncertain = result().copy(termination = ProcessExecutionTermination.TerminationUnconfirmed)
      When("the typed decoder interprets terminal results")
      val failed = receipts.map(HostSourceRead.decodeC)
      val unconfirmed = HostSourceRead.decodeC(uncertain)
      Then("failures retain the exact receipt and no failure or uncertainty becomes Complete")
      failed.map(_.toOption.get) shouldBe receipts.map(HostSourceReadResult.Failed.apply)
      unconfirmed.toOption shouldBe Some(HostSourceReadResult.Unconfirmed(uncertain))
    }
  }

  "E6 Host-source complete capture" must afterWord("in spec:host-source-read, example:E6, rules:R4, phase:6.4") {
    "complete only exit zero with consistent complete captures and strict UTF-8 stdout" in {
      Given("docs/spec/host-source-read.md R4 E6, successful output, partial captures and malformed UTF-8")
      val receipt = result()
      val incomplete = Vector(receipt.copy(stdout = receipt.stdout.copy(truncated = true)),
        receipt.copy(stderr = receipt.stderr.copy(truncated = true)),
        receipt.copy(stdout = receipt.stdout.copy(byteCount = 999)),
        receipt.copy(stderr = receipt.stderr.copy(byteCount = -1)))
      val malformed = receipt.copy(stdout = ProcessExecutionCapture(Vector(0xc3.toByte, 0x28.toByte), 2, false))
      When("the decoder receives each terminal capture")
      val complete = HostSourceRead.decodeC(receipt)
      val failed = incomplete.map(HostSourceRead.decodeC)
      val invalid = HostSourceRead.decodeC(malformed)
      Then("only exact complete bytes succeed, incomplete receipts remain Failed and malformed complete text is a Consequence failure")
      complete.toOption shouldBe Some(HostSourceReadResult.Complete(receipt.stdout.content))
      failed.map(_.toOption.get) shouldBe incomplete.map(HostSourceReadResult.Failed.apply)
      invalid.isFaillure shouldBe true
    }
  }
}

private[cncf] object HostSourceReadSpecSupport {
  val owner: ComponentId = ComponentId("org.example.HostSourceOwner")
  val target: HostSourceTarget = HostSourceTarget.parseC("sm-workflow-codex-terminal").toOption.get
  val payload: HostSourcePayload = HostSourcePayload.fromUtf8C("{\"locator\":\"selected\"}").toOption.get
  val capability: ProcessCapabilityId = ProcessCapabilityId.parseC("host-source-sm-workflow-codex-terminal-read").toOption.get
  val limits: ProcessExecutionLimits = ProcessExecutionLimits(Some(1000), Some(1000), Some(100), Some(65536),
    Some(65536), Some(65536), Some(1), Some(1), Some(1), Some(1), Some(65536))
  val failedTerminations: Vector[ProcessExecutionTermination] = Vector(ProcessExecutionTermination.Exited(1),
    ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.TimedOut,
    ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
    ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr), ProcessExecutionTermination.ArtifactLimitExceeded)

  def templatePolicy(templates: Set[ProcessArgumentTemplate] = Set(ProcessArgumentTemplate(Vector.empty))): ProcessArgumentPolicy =
    ProcessArgumentPolicy(Vector("--fixed-source", "selected"), admission = ProcessArgumentAdmission.Template,
      permittedArgumentTemplates = templates)

  def definition(
    identity: String = capability.print,
    policy: ProcessArgumentPolicy = templatePolicy(),
    artifacts: Set[ProcessArtifactName] = Set.empty,
    workingDirectory: Boolean = false,
    inputFiles: Set[ProcessArtifactName] = Set.empty,
    inputPaths: Map[ProcessArtifactName, WorkAreaRelativePath] = Map.empty,
    resourceTrees: Map[ResourceTreeReference, ResourceTreeLimits] = Map.empty
  ): ProcessProgramDefinition = ProcessProgramDefinition.fromRuntimeC(capability, identity, "/runtime/native-host-source",
    Vector("--fixed-source", "selected"), policy, limits, artifacts, allowsworkingdirectory = workingDirectory,
    allowedinputfiles = inputFiles, allowedinputfilepaths = inputPaths, allowedresourcetrees = resourceTrees).toOption.get

  def result(): ProcessExecutionResult = {
    val bytes = "{\"source\":\"実際\"}".getBytes("UTF-8").toVector
    ProcessExecutionResult(ProcessExecutionTermination.Exited(0), ProcessExecutionCapture(bytes, bytes.size.toLong, false),
      ProcessExecutionCapture(Vector.empty, 0, false), Vector.empty, 1, capability.print)
  }

  def contextFor(
    definition: ProcessProgramDefinition,
    result: ProcessExecutionResult,
    granted: Boolean = true,
    actualOwner: ComponentId = owner,
    installed: Boolean = true,
    driver: Option[ProcessExecutionDriver] = None
  ): ExecutionContext = {
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val execution = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), true)
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    val grants = if (granted) Vector(ProcessExecutionGrant(capability)) else Vector.empty
    val admission = ProcessExecutionAdmission.forComponentsC(policy, Map(owner -> grants)).toOption.get
    val runtime = ScopeContext(ScopeKind.Runtime, "host-source-spec", None, execution.observability,
      processexecutiondriveroption = driver.orElse(Some(ProcessExecutionTestProfile(Map(capability -> result)).driver)),
      processexecutionadmissionoption = if (installed) Some(admission) else None)
    val component = Component.create(actualOwner.name, actualOwner, ComponentInstanceId.default(actualOwner), Protocol.empty)
    execution.withScope(Component.Context(actualOwner.name, runtime, component, ComponentOrigin.Unknown)
      .createChildScope(ScopeKind.Service, "source").createChildScope(ScopeKind.Action, "read"))
  }
}
