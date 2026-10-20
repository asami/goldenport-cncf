package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct. 19, 2026
 */
final class ProcessArgumentTemplateSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _capability = ProcessCapabilityId.parseC("selected-tip").toOption.get
  private val _prefix = Vector("--no-optional-locks", "-C", "/runtime/selected")
  private val _tip = "a" * 40
  private val _template = ProcessArgumentTemplate(Vector(
    ProcessArgumentSlot.Literal("merge"),
    ProcessArgumentSlot.Literal("--no-commit"),
    ProcessArgumentSlot.Literal("--no-ff"),
    ProcessArgumentSlot.LowerHex(Set(40, 64))
  ))
  private val _argument_policy = ProcessArgumentPolicy(_prefix,
    admission = ProcessArgumentAdmission.Template, permittedArgumentTemplates = Set(_template))
  private val _limits = ProcessExecutionLimits(Some(100), Some(100), Some(100),
    Some(256), Some(256), Some(256), Some(8), Some(512), Some(1), Some(256), Some(512))

  "Runtime-declared argument templates" should {
    "preserve an exact host-fixed literal path containing colon, backslash, LF, CR and TAB" in {
      Given("a host-fixed path after a native OID slot and the literal path separator")
      val path = "selected:name\\part\nline\rreturn\ttab.txt"
      val template = ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("diff"),
        ProcessArgumentSlot.LowerHex(Set(40, 64)), ProcessArgumentSlot.Literal("--"),
        ProcessArgumentSlot.Literal(path)))
      val policy = _argument_policy.copy(permittedArgumentTemplates = Set(template))
      val selected = Vector("diff", _tip, "--", path)
      val crossed = Vector(selected.updated(3, path.replace('\n', ' ')),
        selected.updated(3, "other.txt"), selected.updated(3, "--force"),
        selected :+ "--force")
      When("the declaration is installed and its exact vector and crossed requests are admitted")
      val declared = _definition(policy)
      val admission = _admission(policy)
      val accepted = admission.admitC(ProcessExecutionRequest(_capability, selected))
      val refused = crossed.map(argv => admission.admitC(ProcessExecutionRequest(_capability, argv)))
      Then("the declared vector is preserved verbatim and different paths, options and extra arguments fail")
      declared.toOption.isDefined shouldBe true
      accepted.toOption.map(_.effectiveArguments) shouldBe Some(_prefix ++ selected)
      refused.forall(_.isFaillure) shouldBe true
    }

    "reject null literals and every ISO control other than LF, CR and TAB in declarations" in {
      Given("literal declarations containing NUL, ESC, DEL, the other forbidden controls or null")
      val controls = (0 to 0x9f).map(_.toChar).filter(c =>
        c.isControl && c != '\n' && c != '\r' && c != '\t')
      val literals = controls.map(c => "selected" + c + ".txt").toVector :+ null
      val policies = literals.map(value => _argument_policy.copy(permittedArgumentTemplates =
        Set(ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal(value))))))
      When("the trusted runtime constructs each literal declaration")
      val refused = policies.map(_definition)
      Then("every forbidden literal fails declaration without granting argument admission")
      refused.forall(_.isFaillure) shouldBe true
    }

    "keep LF, CR and TAB forbidden in lower-hex affixes while preserving native tokens" in {
      Given("lower-hex slots with each permitted literal control in a prefix or suffix")
      val slots = Vector("\n", "\r", "\t").flatMap(control => Vector(
        ProcessArgumentSlot.LowerHex(Set(40, 64), prefix = control),
        ProcessArgumentSlot.LowerHex(Set(40, 64), suffix = control)))
      val policies = slots.map(slot => _argument_policy.copy(permittedArgumentTemplates =
        Set(ProcessArgumentTemplate(Vector(slot)))))
      val selected = Vector("merge", "--no-commit", "--no-ff", _tip)
      When("the runtime constructs the affixed declarations and admits an unchanged native token")
      val refused = policies.map(_definition)
      val accepted = _admission(_argument_policy).admitC(ProcessExecutionRequest(_capability, selected))
      Then("all control-bearing affixes fail and the ordinary native-token vector remains exact")
      refused.forall(_.isFaillure) shouldBe true
      accepted.toOption.map(_.effectiveArguments) shouldBe Some(_prefix ++ selected)
    }

    "admit generated native-token forms only in their complete fixed positions" in {
      Given("a granted template capability and generated 40/64-character ASCII lower-hex tokens")
      val admission = _admission(_argument_policy)
      val tokens = Gen.oneOf(40, 64).flatMap(size =>
        Gen.listOfN(size, Gen.oneOf("0123456789abcdef".toVector)).map(_.mkString))
      val property = Prop.forAll(tokens) { token =>
        val argv = Vector("merge", "--no-commit", "--no-ff", token)
        admission.admitC(ProcessExecutionRequest(_capability, argv)).toOption
          .exists(_.effectiveArguments == _prefix ++ argv)
      }
      When("the installed admission resolves the generated logical requests")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      Then("each exact token is preserved after the unchanged runtime fixed prefix")
      checked.passed shouldBe true
    }

    "admit literal-affixed tokens without granting a different destination ref" in {
      Given("a runtime-declared push vector with one lower-hex token and fixed ref suffix")
      val template = ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("push"),
        ProcessArgumentSlot.Literal("--"), ProcessArgumentSlot.Literal("selected-endpoint"),
        ProcessArgumentSlot.LowerHex(Set(40, 64), suffix = ":refs/heads/main")))
      val admission = _admission(_argument_policy.copy(permittedArgumentTemplates = Set(template)))
      val selected = Vector("push", "--", "selected-endpoint", _tip + ":refs/heads/main")
      When("a selected refspec and crossed ref/endpoint/forced refspec are submitted")
      val accepted = admission.admitC(ProcessExecutionRequest(_capability, selected))
      val crossed = Vector(selected.updated(3, _tip + ":refs/heads/other"),
        selected.updated(2, "other-endpoint"), selected.updated(3, "+" + selected(3)))
        .map(argv => admission.admitC(ProcessExecutionRequest(_capability, argv)))
      Then("only the complete runtime-declared vector is admitted verbatim")
      accepted.toOption.map(_.effectiveArguments) shouldBe Some(_prefix ++ selected)
      crossed.forall(_.isFaillure) shouldBe true
    }

    "refuse crossed shape, count, case, ref and options before a deterministic driver starts" in {
      Given("one installed template and a recording deterministic driver")
      val admission = _admission(_argument_policy.copy(permittedArguments = Set("--force"),
        permittedArgumentVectors = Set(Vector("--force"))))
      val capture = ProcessExecutionCapture(Vector.empty, 0, truncated = false)
      val result = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), capture,
        capture, Vector.empty, 1, "selected-tip")
      val profile = ProcessExecutionTestProfile(Map(_capability -> result))
      val selected = Vector("merge", "--no-commit", "--no-ff", _tip)
      val invalid = Vector(selected.reverse, selected :+ "--force", selected.dropRight(1),
        Vector("--force"), selected.updated(3, "HEAD"), selected.updated(3, _tip.toUpperCase),
        selected.updated(3, _tip + "\n"), selected.updated(3, " " + _tip),
        selected.updated(3, "a" * 39), selected.updated(3, "g" * 40))
      When("each invalid request passes through admission before any driver start")
      val refused = invalid.map(argv => admission.admitC(ProcessExecutionRequest(_capability, argv))
        .flatMap(profile.driver.startC))
      Then("no registered-value fallback or process invocation occurs and diagnostics omit values")
      refused.forall(_.isFaillure) shouldBe true
      profile.driver.executions shouldBe empty
      refused.mkString should not include _tip
      refused.mkString should not include "--force"
    }

    "preserve missing-grant and finite argument limits under template admission" in {
      Given("a valid template with an absent or tighter capability grant")
      val definition = _definition(_argument_policy).toOption.get
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val absent = ProcessExecutionAdmission.createC(policy, Vector.empty).toOption.get
      val admission = _admission(_argument_policy)
      val request = ProcessExecutionRequest(_capability, Vector("merge", "--no-commit", "--no-ff", _tip))
      When("the request lacks a grant or exceeds a tightened count or byte limit")
      val refused = Vector(absent.admitC(request),
        admission.admitC(request.copy(requestedLimits = ProcessExecutionLimits(argumentCount = Some(3)))),
        admission.admitC(request.copy(requestedLimits = ProcessExecutionLimits(argumentBytes = Some(20)))))
      Then("shape matching never broadens grants or effective resource bounds")
      refused.forall(_.isFaillure) shouldBe true
    }

    "refuse malformed or unselected template declarations at runtime construction" in {
      Given("invalid lengths, controls, null slots and a template declared outside Template mode")
      val slots = Vector(ProcessArgumentSlot.LowerHex(Set.empty), ProcessArgumentSlot.LowerHex(Set(0)),
        ProcessArgumentSlot.LowerHex(Set(-1)), ProcessArgumentSlot.LowerHex(Set(40), prefix = "\n"),
        ProcessArgumentSlot.Literal("secret\u0000literal"), null)
      val policies = slots.map(slot => _argument_policy.copy(permittedArgumentTemplates =
        Set(ProcessArgumentTemplate(Vector(slot))))) ++ Vector(
        _argument_policy.copy(permittedArgumentTemplates = Set.empty),
        _argument_policy.copy(admission = ProcessArgumentAdmission.Registered),
        _argument_policy.copy(admission = ProcessArgumentAdmission.BoundedText),
        _argument_policy.copy(fixedPrefix = Vector("wrong-prefix")))
      When("the trusted runtime constructs those declarations")
      val refused = policies.map(_definition)
      Then("each declaration is rejected without reflecting configuration content")
      refused.forall(_.isFaillure) shouldBe true
      refused.mkString should not include "secret"
      refused.mkString should not include "wrong-prefix"
    }

    "preserve registered vectors and explicitly declared empty-template semantics" in {
      Given("the unchanged Registered policy and a Template policy admitting only an empty vector")
      val registered = _admission(ProcessArgumentPolicy(_prefix,
        permittedArgumentVectors = Set(Vector("--json"))))
      val emptytemplate = _admission(_argument_policy.copy(permittedArgumentTemplates =
        Set(ProcessArgumentTemplate(Vector.empty))))
      When("registered and empty-template requests are admitted under their selected modes")
      val registeredok = registered.admitC(ProcessExecutionRequest(_capability, Vector("--json")))
      val registeredbad = registered.admitC(ProcessExecutionRequest(_capability, Vector(_tip)))
      val emptyok = emptytemplate.admitC(ProcessExecutionRequest(_capability))
      val emptybad = emptytemplate.admitC(ProcessExecutionRequest(_capability, Vector(_tip)))
      Then("old vectors remain exact and an empty template adds no dynamic argument permission")
      registeredok.toOption.map(_.effectiveArguments) shouldBe Some(_prefix :+ "--json")
      registeredbad.isFaillure shouldBe true
      emptyok.toOption.map(_.effectiveArguments) shouldBe Some(_prefix)
      emptybad.isFaillure shouldBe true
    }
  }

  private def _definition(policy: ProcessArgumentPolicy): Consequence[ProcessProgramDefinition] =
    ProcessProgramDefinition.fromRuntimeC(_capability, "selected-tip", "/runtime/bin/selected-tool",
      _prefix, policy, _limits, Set.empty)

  private def _admission(policy: ProcessArgumentPolicy): ProcessExecutionAdmission = {
    val definition = _definition(policy).toOption.get
    val registry = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    ProcessExecutionAdmission.createC(registry, Vector(ProcessExecutionGrant(_capability))).toOption.get
  }
}
