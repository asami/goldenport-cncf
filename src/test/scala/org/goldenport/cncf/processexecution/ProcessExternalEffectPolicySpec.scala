package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Paths}
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.effect.{ExternalEffectClassification, ExternalEffectKind, ExternalEffectRoute}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.workspace.WorkspaceBinding
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExternalEffectPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _capability = ProcessCapabilityId.parseC("selected-tool").toOption.get
  private val _owner = ComponentId("org.example.EffectOwner")
  private val _limits = ProcessExecutionLimits(Some(100), Some(100), Some(100), Some(64), Some(64),
    Some(64), Some(4), Some(512), Some(1), Some(64), Some(128))
  private val _secret = "confidential-effect-sentinel"

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Runtime definition rejected: $failure")
  }

  private def _definition(executable: String): ProcessProgramDefinition = _take(
    ProcessProgramDefinition.fromRuntimeC(_capability, "selected-tool", executable, Vector.empty,
      ProcessArgumentPolicy(Vector.empty, admission = ProcessArgumentAdmission.Template,
        permittedArgumentTemplates = Set(ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("inspect"))))),
      _limits, Set.empty, environment = Map("FIXED_SECRET" -> _secret)))

  private def _policy[A](value: Consequence[A]): Option[String] = value match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => None
  }

  "Bounded command effect classification" should {
    "identify shell, interpreter and wrapper effects as Direct Process" in {
      Given("representative shell, numeric-version Python, interpreter and wrapper command names")
      val names = Vector("sh", "bash", "pwsh", "cmd", "python3.12", "python2.7.18", "java", "node", "env", "xargs", "sudo")
      When("the public classifier describes these ordinary command names")
      val classifications = names.map(ProcessExternalEffectPolicy.classifyCommandName)
      Then("each describes direct process authority without suggesting a grant")
      classifications.foreach(_ shouldBe ExternalEffectClassification(Set(ExternalEffectKind.Process), ExternalEffectRoute.Direct))
    }

    "describe recognized file, HTTP, database and untagged tool laundering" in {
      Given("bounded names with known effects that require their owning managed routes")
      val cases = Vector(
        "cat" -> Set(ExternalEffectKind.FileRead),
        "cp" -> Set(ExternalEffectKind.FileRead, ExternalEffectKind.FileWrite),
        "rm" -> Set(ExternalEffectKind.FileWrite),
        "curl" -> Set(ExternalEffectKind.Http),
        "psql" -> Set(ExternalEffectKind.Persistence),
        "git" -> Set(ExternalEffectKind.GitRead, ExternalEffectKind.GitWrite),
        "sbt" -> Set(ExternalEffectKind.Build),
        "sbt.cmd" -> Set(ExternalEffectKind.Build),
        "sbt.bat" -> Set(ExternalEffectKind.Build))
      When("the classifier describes each basename")
      val classifications = cases.map { case (name, kinds) =>
        ProcessExternalEffectPolicy.classifyCommandName(name) -> kinds
      }
      Then("the effect kinds identify capability laundering independently of program identity")
      classifications.foreach { case (classification, kinds) =>
        classification shouldBe ExternalEffectClassification(kinds, ExternalEffectRoute.CapabilityLaundering)
      }
    }

    "preserve classification and redacted refusal for generated basename and case variants" in {
      Given("known names with generated confidential directory segments, separator, case and optional executable suffix")
      val names = Gen.oneOf("bash", "python3.12", "env", "cat", "curl", "git", "sbt.cmd")
      val variants = for {
        name <- names
        separator <- Gen.oneOf("/", "\\")
        upper <- Gen.oneOf(true, false)
        suffix <- Gen.oneOf("", ".exe")
        depth <- Gen.choose(1, 6)
      } yield (name, Vector.fill(depth)(_secret).mkString(separator) + separator +
        (if (upper) name.toUpperCase(Locale.ROOT) else name) + suffix)
      When("the public classifier and registered-definition gate receive each generated path spelling")
      val property = Prop.forAll(variants) { case (name, variant) =>
        val expected = ProcessExternalEffectPolicy.classifyCommandName(name)
        val definition = _definition("/" + variant)
        val rejected = ProcessExternalEffectPolicy.validateGenericDefinitionC(definition)
        ProcessExternalEffectPolicy.classifyCommandName(variant) == expected &&
          ProcessExternalEffectPolicy.classifyDefinition(definition) == expected &&
          _policy(rejected).contains(expected.route.diagnosticCode) &&
          !rejected.toString.contains(_secret) && !rejected.toString.contains(variant)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(80), property)
      Then("normalization cannot turn known effects into generic opt-in or disclose confidential host values")
      checked.passed shouldBe true
    }

    "leave unknown binaries and nonnumeric Python lookalikes Unclassified" in {
      Given("an explicitly selected tool and names outside the bounded interpreter grammar")
      val names = Vector("selected-tool", "python3.12-helper", "python3..12", "python3.x")
      When("the classifier describes these names without inspecting files or guessing their behavior")
      val classifications = names.map(ProcessExternalEffectPolicy.classifyCommandName)
      Then("Unclassified records the inference limit and never claims safety")
      classifications.foreach(_ shouldBe ExternalEffectClassification(Set(ExternalEffectKind.Process), ExternalEffectRoute.Unclassified))
    }
  }

  "Generic registered-definition admission" should {
    "refuse known direct and laundering definitions despite exact program registration and a component grant" in {
      Given("complete finite host definitions and independent grants for each recognized effect")
      val names = Vector("bash", "python3.12", "env", "cat", "cp", "rm", "curl", "sqlite3", "git", "sbt.bat")
      val definitions = names.map(name => _definition(s"/runtime/$_secret/$name"))
      val grants = Map(_owner -> Vector(ProcessExecutionGrant(_capability)))
      When("the shared gate and natural host plan assembly receive each definition")
      val outcomes = definitions.map { definition =>
        (ProcessExternalEffectPolicy.classifyDefinition(definition),
          ProcessExternalEffectPolicy.validateGenericDefinitionC(definition),
          RuntimeProcessExecutionPlan.createC(Vector(definition), grants))
      }
      Then("the route-specific structured refusal wins and neither path reflects confidential runtime data")
      outcomes.foreach { case (classification, validation, plan) =>
        validation.isFaillure shouldBe true
        plan.isFaillure shouldBe true
        _policy(validation) shouldBe Some(classification.route.diagnosticCode)
        _policy(plan) shouldBe Some(classification.route.diagnosticCode)
        validation.toString should not include _secret
        plan.toString should not include _secret
      }
    }

    "keep an Unclassified exact tool eligible while preserving independent grants and finite policy" in {
      Given("a complete registered selected-tool definition and one independently declared component capability grant")
      val definition = _definition("/runtime/bin/selected-tool")
      val grants = Map(_owner -> Vector(ProcessExecutionGrant(_capability)))
      When("the generic gate and host assembly admit the definition with and without that grant")
      val eligibility = ProcessExternalEffectPolicy.validateGenericDefinitionC(definition)
      val selected = _take(RuntimeProcessExecutionPlan.createC(Vector(definition), grants))
      val ungranted = _take(RuntimeProcessExecutionPlan.createC(Vector(definition), Map.empty))
      Then("eligibility retains the exact host policy and cannot manufacture a component grant")
      eligibility.isSuccess shouldBe true
      selected.programs shouldBe Vector(definition)
      selected.programs.head.maximumLimits shouldBe _limits
      selected.grants shouldBe grants
      ungranted.grants shouldBe empty
    }

    "classify CNCF-tagged Build and Git permissions as Managed and refuse generic reuse" in {
      Given("a shared workspace binding with CNCF-created Build, read Git and write Git definitions")
      val root = Files.createTempDirectory("process-effect-policy-")
      try {
        val binding = _take(WorkspaceBinding.createC(GitExecutionSpecSupport.workspaceId, root))
        val build = _take(SbtProgramDefinition.createC(binding, SbtOperation.Test, _capability,
          "selected-build", Paths.get("/usr/bin/true"), _limits, Map.empty))
        val read = _take(GitExecutionSpecSupport.definition(binding,
          GitExecutionSpecSupport.declaration(GitOperationKind.Head, root.resolve("remote.git").toString)))
        val write = _take(GitExecutionSpecSupport.definition(binding,
          GitExecutionSpecSupport.declaration(GitOperationKind.Fetch, root.resolve("remote.git").toString)))
        val definitions = Vector(build, read, write)
        When("the shared classifier and generic gate receive the actual tagged definitions")
        val classifications = definitions.map(ProcessExternalEffectPolicy.classifyDefinition)
        val rejected = definitions.map(ProcessExternalEffectPolicy.validateGenericDefinitionC)
        Then("private tool provenance supplies Managed classification while generic reuse remains forbidden")
        classifications shouldBe Vector(ExternalEffectKind.Build, ExternalEffectKind.GitRead, ExternalEffectKind.GitWrite)
          .map(kind => ExternalEffectClassification(Set(kind), ExternalEffectRoute.Managed))
        rejected.foreach { result =>
          result.isFaillure shouldBe true
          _policy(result) shouldBe Some("external.effect.managed")
          result.toString should not include root.toString
        }
      } finally Files.deleteIfExists(root)
    }
  }
}
