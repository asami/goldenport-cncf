package org.goldenport.cncf.processexecution

import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.validation.ValidationExecutionModeV1 as M
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Preliminary legacy JVM guards using synthetic receipts; no command or old CAR is executed.
 * @since   Nov.  7, 2026
 * @version Nov. 12, 2026
 */
final class SbtExecutionBinaryCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _workspace = WorkspaceId.parseC("abi-project").toOption.get
  private val _other_workspace = WorkspaceId.parseC("abi-other-project").toOption.get
  private val _scenario = SbtScenarioSelection("project.spec.SelectedSpec", "selected behavior")
  private val _estimate = SbtValidationEstimate("focused compatibility", "source-1",
    Vector(SbtValidationEstimate.Leaf(_scenario.suiteClassName, _scenario.testName, 1000000L, false)),
    SbtValidationEstimate.Preparation(0L, 0L, 0L, 0L), BigDecimal(1))
  private val _selected = SbtValidationMode.Case(_scenario, "develop", Some("fixture"))
  private val _child_observation = M.Observation(_scenario.suiteClassName, _scenario.testName, "develop", "fixture")
  private val _observation = SbtTestObservation(Vector(_scenario.suiteClassName), SbtSuiteNameConfirmation.Complete,
    Some(1L), Some(1L), Some(0L), Some(0L), Some(1L), Some(0L), Some(0L), Some(0L),
    Vector("synthetic failed-test detail"), true)
  private val _mode_target = SbtTarget(_workspace, Some(_scenario), Some(_estimate),
    validationMode = Some("develop"), validationProvider = Some("fixture"))
  private val _discovery_target = SbtTarget(_workspace, validationDiscovery = true)
  private val _batch_target = SbtTarget(_workspace, validationBatch = Vector(_selected))
  private val _scoped = SbtScopedInput(org.goldenport.cncf.component.ComponentId("org.example.Owner"),
    _workspace, _workspace, "scope", "selection",
    org.goldenport.cncf.workspace.WorkspaceRelativePath.parseC("input.json").toOption.get)
  private val _plain_target = SbtTarget(_workspace)
  private val _target_parameters: Array[Class[?]] =
    Array(classOf[String], classOf[Option[?]], classOf[Option[?]])
  private val _result_parameters: Array[Class[?]] =
    Array(classOf[SbtTarget], classOf[SbtOperation], classOf[ProcessExecutionResult], classOf[Option[?]])

  private def _process(termination: ProcessExecutionTermination): ProcessExecutionResult =
    ProcessExecutionResult(termination, ProcessExecutionCapture(Vector.empty, 0L, false),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 13L, "synthetic-abi-receipt")

  // Independent nonempty projections make erased Option slot confusion observable.
  private def _current_result: SbtExecutionResult = SbtExecutionResult(
    target = _mode_target, operation = SbtOperation.TestOnly,
    processResult = _process(ProcessExecutionTermination.Exited(1)),
    testSelection = Some(SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.FailedTests)),
    testObservation = Some(_observation),
    validationMode = Some(SbtValidationMode.Result.Observed(_child_observation)),
    discovery = Some(SbtValidationDiscovery.Result.Unavailable("synthetic unavailable build")),
    batch = Some(Vector(SbtValidationMode.CaseResult(_selected, "failed", Some(_child_observation))))
  )

  "Legacy typed SBT JVM entries" should {
    "the three-field target prefix" which {
      "constructs and applies through the exact erased String and Option signature" in {
        Given("typed nonempty scenario and estimate data used by the old operational target")
        val arguments = Array[AnyRef](_workspace.asInstanceOf[AnyRef], Some(_scenario), Some(_estimate))
        val constructor = classOf[SbtTarget].getConstructor(_target_parameters*)
        val applymethod = SbtTarget.getClass.getMethod("apply", _target_parameters*)
        When("the old constructor and companion apply are actually invoked")
        val constructed = constructor.newInstance(arguments*)
        val applied = applymethod.invoke(SbtTarget, arguments*).asInstanceOf[SbtTarget]
        Then("the prefix survives and all appended target fields have ordinary absent defaults")
        Vector(constructed, applied).foreach { target =>
          target.workspaceId shouldBe _workspace
          target.scenario shouldBe Some(_scenario)
          target.validationEstimate shouldBe Some(_estimate)
          target.validationMode shouldBe None
          target.validationProvider shouldBe None
          target.validationDiscovery shouldBe false
          target.validationBatch shouldBe empty
        }
        classOf[SbtTarget].getMethod("workspaceId").getReturnType shouldBe classOf[String]
        classOf[SbtTarget].getMethod("scenario").invoke(applied) shouldBe Some(_scenario)
        classOf[SbtTarget].getMethod("validationEstimate").invoke(applied) shouldBe Some(_estimate)
      }

      "copies the legacy prefix while retaining each current selection form" in {
        Given("separate exact-mode, discovery and exact-batch targets with their current fields")
        val targets = Vector(_mode_target, _discovery_target, _batch_target, _plain_target.copy(scopedInput = Some(_scoped)))
        val copymethod = classOf[SbtTarget].getMethod("copy", _target_parameters*)
        When("an old caller changes only its three-field prefix")
        val copies = targets.map(target => copymethod.invoke(target,
          _other_workspace.asInstanceOf[AnyRef], None, None).asInstanceOf[SbtTarget])
        Then("the requested prefix changes without dropping mode, provider, discovery or batch")
        targets.zip(copies).foreach { (original, copied) =>
          copied.workspaceId shouldBe _other_workspace
          copied.scenario shouldBe None
          copied.validationEstimate shouldBe None
          copied.validationMode shouldBe original.validationMode
          copied.validationProvider shouldBe original.validationProvider
          copied.validationDiscovery shouldBe original.validationDiscovery
          copied.validationBatch shouldBe original.validationBatch
          copied.scopedInput shouldBe original.scopedInput
        }
      }

      "retains constructor defaults and full current construction and copy" in {
        Given("the current seven-field target and the defaults referenced by compiled callers")
        val parameters = _target_parameters ++ Array[Class[?]](classOf[Option[?]], classOf[Option[?]],
          java.lang.Boolean.TYPE, classOf[Vector[?]])
        When("default getters, the full constructor and the full generated apply and copy are invoked")
        val defaults = (2 to 7).map(index => SbtTarget.getClass
          .getMethod("$lessinit$greater$default$" + index).invoke(SbtTarget)).toVector
        val arguments = Array[AnyRef](_workspace.asInstanceOf[AnyRef], Some(_scenario), Some(_estimate),
          Some("develop"), Some("fixture"), java.lang.Boolean.FALSE, Vector.empty[SbtValidationMode.Case])
        val constructed = classOf[SbtTarget].getConstructor(parameters*).newInstance(arguments*)
        val applied = SbtTarget.getClass.getMethod("apply", parameters*).invoke(SbtTarget, arguments*)
        val copied = classOf[SbtTarget].getMethod("copy", parameters*).invoke(_plain_target, arguments*)
        val scopedcopy = classOf[SbtTarget].getMethod("copy", parameters*)
          .invoke(_plain_target.copy(scopedInput = Some(_scoped)), arguments*).asInstanceOf[SbtTarget]
        val copydefaults = (1 to 7).map(index => classOf[SbtTarget]
          .getMethod("copy$default$" + index).invoke(_mode_target)).toVector
        Then("full entries retain their current meaning and legacy defaults remain absent")
        defaults shouldBe Vector(None, None, None, None, java.lang.Boolean.FALSE, Vector.empty)
        Vector(constructed, applied, copied).foreach(_ shouldBe _mode_target)
        copydefaults shouldBe arguments.toVector
        scopedcopy.scopedInput shouldBe Some(_scoped)
        _mode_target.copy(validationProvider = Some("live")).validationProvider shouldBe Some("live")
        SbtTarget(_workspace).scenario shouldBe None
      }
    }

    "the four- and five-field result prefixes" which {
      "construct and apply without reinterpreting a legacy observation as mode evidence" in {
        Given("a synthetic failed receipt and a nonempty literal reporter observation")
        val current = _current_result
        val prefix = Array[AnyRef](current.target, current.operation, current.processResult, current.testSelection)
        When("both exact erased constructor and companion apply signatures are invoked")
        val values = Vector(4, 5).flatMap { arity =>
          val parameters = if (arity == 4) _result_parameters else _result_parameters :+ classOf[Option[?]]
          val arguments = if (arity == 4) prefix else prefix :+ current.testObservation
          Vector(arity -> classOf[SbtExecutionResult].getConstructor(parameters*).newInstance(arguments*),
            arity -> SbtExecutionResult.getClass.getMethod("apply", parameters*)
              .invoke(SbtExecutionResult, arguments*).asInstanceOf[SbtExecutionResult])
        }
        Then("legacy observation stays in slot five and appended feature metadata remains absent")
        values.foreach { (arity, result) =>
          result.target shouldBe current.target
          result.operation shouldBe current.operation
          result.processResult shouldBe current.processResult
          result.testSelection shouldBe current.testSelection
          result.testObservation shouldBe (if (arity == 4) None else Some(_observation))
          result.validationMode shouldBe None
          result.discovery shouldBe None
          result.batch shouldBe None
          result.outcome shouldBe SbtExecutionOutcome.Failed(1)
        }
      }

      "copies old fields while preserving nonempty observation and independent current metadata" in {
        Given("a current result with distinct nonempty legacy and feature observations")
        val current = _current_result
        val changed = _process(ProcessExecutionTermination.NotStarted)
        val prefix = Array[AnyRef](_plain_target, SbtOperation.Compile, changed, None)
        val replacement = Some(_observation.copy(nameConfirmation = SbtSuiteNameConfirmation.Partial))
        When("the old four- and five-argument copy entries update the prefix")
        val copiedfour = classOf[SbtExecutionResult].getMethod("copy", _result_parameters*)
          .invoke(current, prefix*).asInstanceOf[SbtExecutionResult]
        val copiedfive = classOf[SbtExecutionResult].getMethod("copy", (_result_parameters :+ classOf[Option[?]])*)
          .invoke(current, (prefix :+ replacement)*).asInstanceOf[SbtExecutionResult]
        Then("four-field copy retains observation, five-field copy replaces it, and both retain appended data")
        copiedfour.testObservation shouldBe current.testObservation
        copiedfive.testObservation shouldBe replacement
        Vector(copiedfour, copiedfive).foreach { result =>
          result.target shouldBe _plain_target
          result.operation shouldBe SbtOperation.Compile
          result.processResult shouldBe changed
          result.testSelection shouldBe None
          result.validationMode shouldBe current.validationMode
          result.discovery shouldBe current.discovery
          result.batch shouldBe current.batch
          result.outcome shouldBe SbtExecutionOutcome.NotExecuted
        }
      }

      "keeps default getter five tied to observation and retains full current source APIs" in {
        Given("the current eight-field result with a nonempty legacy observation and all appended metadata")
        val current = _current_result
        val parameters = _result_parameters ++ Array.fill[Class[?]](4)(classOf[Option[?]])
        val arguments = Array[AnyRef](current.target, current.operation, current.processResult, current.testSelection,
          current.testObservation, current.validationMode, current.discovery, current.batch)
        When("the generated full entries, constructor defaults and copy defaults are invoked")
        val constructed = classOf[SbtExecutionResult].getConstructor(parameters*).newInstance(arguments*)
        val applied = SbtExecutionResult.getClass.getMethod("apply", parameters*).invoke(SbtExecutionResult, arguments*)
        val copied = classOf[SbtExecutionResult].getMethod("copy", parameters*).invoke(current, arguments*)
        val defaults = (4 to 8).map(index => SbtExecutionResult.getClass
          .getMethod("$lessinit$greater$default$" + index).invoke(SbtExecutionResult)).toVector
        val copydefaults = (1 to 8).map(index => classOf[SbtExecutionResult]
          .getMethod("copy$default$" + index).invoke(current)).toVector
        val legacycopy = classOf[SbtExecutionResult].getMethod("copy", (_result_parameters :+ classOf[Option[?]])*)
          .invoke(current, copydefaults.take(5)*).asInstanceOf[SbtExecutionResult]
        val namedcopy = current.copy(validationMode = None)
        Then("observation, mode, discovery and batch retain separate slots and ordinary defaults")
        Vector(constructed, applied, copied, legacycopy).foreach(_ shouldBe current)
        defaults shouldBe Vector.fill(5)(None)
        copydefaults shouldBe arguments.toVector
        copydefaults(4) shouldBe Some(_observation)
        namedcopy.testObservation shouldBe Some(_observation)
        namedcopy.validationMode shouldBe None
        namedcopy.discovery shouldBe current.discovery
        namedcopy.batch shouldBe current.batch
        Vector("target", "operation", "processResult", "testSelection", "testObservation", "outcome").foreach { name =>
          classOf[SbtExecutionResult].getMethod(name).invoke(current) should not be null
        }
        SbtExecutionResult(_plain_target, SbtOperation.Compile, current.processResult).testObservation shouldBe None
      }

      "preserves separated observations and feature projections across generated legacy updates" in {
        Given("generated nonzero synthetic exits and canonical mode observations")
        val copymethod = classOf[SbtExecutionResult].getMethod("copy", (_result_parameters :+ classOf[Option[?]])*)
        When("old five-field updates clear observation on current results")
        val property = Prop.forAll(Gen.choose(1, 255), Gen.oneOf("develop", "test", "demo", "production")) { (exit, mode) =>
          val current = _current_result.copy(processResult = _process(ProcessExecutionTermination.Exited(exit)),
            validationMode = Some(SbtValidationMode.Result.Observed(_child_observation.copy(mode = mode))))
          val copied = copymethod.invoke(current, current.target, current.operation, current.processResult,
            current.testSelection, None).asInstanceOf[SbtExecutionResult]
          copied.testObservation.isEmpty && copied.validationMode == current.validationMode &&
            copied.discovery == current.discovery && copied.batch == current.batch &&
            copied.outcome == SbtExecutionOutcome.Failed(exit) && copied.processResult == current.processResult
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
        Then("the legacy Option never erases current mode, discovery, batch or failure data")
        checked.passed shouldBe true
      }
    }
  }

  "Compatibility without a new execution policy" should {
    "retain the missing operational enum class and companion identities" in {
      Given("the exact public enum class, constructor declaration and companion referenced by the old compiled caller")
      val name = "org.goldenport.cncf.processexecution.SbtExecutionOutcome$TestValidationFailed"
      val reason = SbtTestSelectionFailure.FailedTests
      val variantclass = Class.forName(name)
      val constructor = variantclass.getConstructor(classOf[SbtTestSelectionFailure])
      val companion = SbtExecutionOutcome.TestValidationFailed
      When("the restored enum companion apply constructs the variant directly and reflectively")
      val variant = companion(reason)
      val applied = Class.forName(name + "$").getMethod("apply", classOf[SbtTestSelectionFailure])
        .invoke(companion, reason)
      val extracted = variant match {
        case SbtExecutionOutcome.TestValidationFailed(value) => value
      }
      Then("the constructor signature and exact enum identities remain available with unchanged reason access and extraction")
      constructor.getDeclaringClass shouldBe variantclass
      constructor.getParameterTypes.toVector shouldBe Vector(classOf[SbtTestSelectionFailure])
      companion.getClass.getName shouldBe name + "$"
      variant.getClass shouldBe variantclass
      applied.getClass shouldBe variantclass
      variant shouldBe SbtExecutionOutcome.TestValidationFailed(reason)
      applied shouldBe variant
      variant.asInstanceOf[SbtExecutionOutcome.TestValidationFailed].reason shouldBe reason
      variantclass.getMethod("reason").invoke(applied) shouldBe reason
      extracted shouldBe reason
    }

    "preserve failure, incomplete and guarded successful outcomes independently of legacy observation" in {
      Given("explicit synthetic terminal facts, absent qualifications and a nonempty failed-test observation")
      val zero = _process(ProcessExecutionTermination.Exited(0))
      val verified = Some(SbtTestSelectionResult.Verified(Vector(_scenario.suiteClassName), 1, 1L))
      val rejected = Some(SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.FailedTests))
      When("current outcome rules interpret those data without executing any command")
      val observedfailure = SbtExecutionResult(_plain_target, SbtOperation.TestOnly, zero, rejected, Some(_observation))
      val missing = SbtExecutionResult(_plain_target, SbtOperation.TestOnly, zero, None, Some(_observation))
      val success = SbtExecutionResult(_plain_target, SbtOperation.TestOnly, zero, verified, Some(_observation))
      Then("the restored enum is available without changing current TestOnly or feature guards")
      observedfailure.outcome shouldBe SbtExecutionOutcome.TestValidationFailed(SbtTestSelectionFailure.FailedTests)
      missing.outcome shouldBe SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.MissingSelection)
      success.outcome shouldBe SbtExecutionOutcome.Succeeded
      success.effect shouldBe ExternalEffect(ExternalEffectKind.Build, _workspace)
      SbtExecutionResult(_mode_target, SbtOperation.TestOnly, zero, verified).outcome shouldBe
        SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.ModeObservationMismatch)
      SbtExecutionResult(_discovery_target, SbtOperation.TestCompile, zero).outcome shouldBe
        SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.DiscoveryUnavailable)
      SbtExecutionResult(_batch_target, SbtOperation.TestOnly, zero).outcome shouldBe
        SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.BatchSelectionMismatch)
      val modequalified = success.copy(target = _mode_target,
        validationMode = Some(SbtValidationMode.Result.Observed(_child_observation)))
      modequalified.outcome shouldBe SbtExecutionOutcome.Succeeded
      modequalified.copy(validationMode = Some(SbtValidationMode.Result.Observed(_child_observation.copy(provider = "live"))))
        .outcome shouldBe SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.ModeObservationMismatch)
      success.copy(target = _batch_target, batch = Some(Vector(
        SbtValidationMode.CaseResult(_selected, "passed", Some(_child_observation))))).outcome shouldBe SbtExecutionOutcome.Succeeded
      success.copy(target = _discovery_target, operation = SbtOperation.TestCompile,
        discovery = Some(SbtValidationDiscovery.Result.Available(SbtValidationDiscovery.Build(_workspace,
          SbtValidationDiscovery.Binding("project", "project-tests", "1", "project.spec"),
          Vector(java.nio.file.Path.of("/synthetic/classes")), Vector(java.nio.file.Path.of("/synthetic/classes"))))))
        .outcome shouldBe SbtExecutionOutcome.Succeeded
      Vector(ProcessExecutionTermination.NotStarted -> SbtExecutionOutcome.NotExecuted,
        ProcessExecutionTermination.Exited(2) -> SbtExecutionOutcome.Failed(2),
        ProcessExecutionTermination.LaunchFailed -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.LaunchFailed),
        ProcessExecutionTermination.TimedOut -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut)).foreach { (termination, outcome) =>
        success.copy(processResult = _process(termination)).outcome shouldBe outcome
      }
    }

    "retain fixed empty-request admission without deriving authority from compatibility data" in {
      Given("an existing typed capability with an empty request and caller override variants")
      val capability = ProcessCapabilityId.parseC("abi-program").toOption.get
      val empty = ProcessExecutionRequest(capability)
      val overrides = Vector(empty.copy(arguments = Vector("test")),
        empty.copy(input = ProcessExecutionInput.Bytes(Vector(1.toByte))),
        empty.copy(requestedLimits = ProcessExecutionLimits(executionTimeoutMillis = Some(10L))))
      When("the unchanged typed request and intent admission policies inspect these values")
      val accepted = SbtExecutionAdmission.validateRequestC(empty)
      val rejected = overrides.map(SbtExecutionAdmission.validateRequestC)
      val invalidintent = SbtExecutionAdmission.resolveC(null, null, SbtOperation.Compile)
      Then("only the empty shape passes and missing intent cannot gain authority")
      accepted.isSuccess shouldBe true
      rejected.foreach(_.isFaillure shouldBe true)
      invalidintent.isFaillure shouldBe true
    }
  }
}
