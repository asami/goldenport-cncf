package org.goldenport.cncf.context

import java.nio.file.Files
import java.time.{Duration, Instant}
import cats.~>
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig, RuntimeTestDescriptor}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.ComponentLogic
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionProfileSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _non_empty_token =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  "ExecutionProfileResolver" should {
    "resolve the production default as one standard capability bundle" in {
      Given("an ordinary runtime without execution-profile configuration")
      val configuration = _configuration(Map.empty)

      When("the runtime resolves its execution profile")
      val profile = ExecutionProfileResolver.resolve(configuration, OperationMode.Production).toOption.get

      Then("all dimensions come from the standard compatibility row")
      profile.identity.mode shouldBe ExecutionProfileMode.Standard
      profile.control.timeMode shouldBe ExecutionTimeMode.System
      profile.control.randomMode shouldBe ExecutionRandomMode.System
      profile.control.idMode shouldBe ExecutionIdMode.Production
      profile.control.schedulerMode shouldBe ExecutionSchedulerMode.Realtime
      profile.control.orderingMode shouldBe ExecutionOrderingMode.Concurrent
      profile.control.replayability.state shouldBe ReplayabilityState.Uncontrolled
      profile.runtimeClock.mode shouldBe RuntimeClockMode.System
    }

    "resolve generic environment assumptions once for arbitrary supported values" in {
      Given("generated locale, timezone, charset, line-separator, and math-context selections")
      val assumptions = for {
        locale <- Gen.oneOf("en-US", "ja-JP", "fr-FR")
        timezone <- Gen.oneOf("UTC", "Asia/Tokyo", "Europe/Paris")
        charset <- Gen.oneOf("UTF-8", "UTF-16", "US-ASCII")
        lineseparator <- Gen.oneOf("lf", "crlf", "cr")
        mathcontext <- Gen.oneOf("decimal32", "decimal64", "decimal128", "unlimited")
      } yield (locale, timezone, charset, lineseparator, mathcontext)

      When("each selection is resolved into a runtime binding")
      val property = Prop.forAll(assumptions) { case (locale, timezone, charset, lineseparator, mathcontext) =>
        val configuration = _configuration(Map(
          RuntimeConfig.EXECUTION_LOCALE_KEY -> locale,
          RuntimeConfig.EXECUTION_TIMEZONE_KEY -> timezone,
          RuntimeConfig.EXECUTION_CHARSET_KEY -> charset,
          RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY -> lineseparator,
          RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY -> mathcontext,
          RuntimeConfig.EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY -> "unicode-nfc",
          RuntimeConfig.EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY -> "case-sensitive",
          RuntimeConfig.EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY -> "iso-8601"
        ))
        val profile = ExecutionProfileResolver.resolve(configuration, OperationMode.Production).toOption.get
        val binding = profile.newRuntime(IdGenerationContext.DEFAULT_NAMESPACE).baseBinding
        val core = binding.environmentAssumptions.apply_to(ExecutionContext.create().core, binding.clock)

        core.locale.toLanguageTag == locale &&
          core.timezone.getId == timezone &&
          core.encoding.name == java.nio.charset.Charset.forName(charset).name &&
          core.vm.lineSeparator == binding.environmentAssumptions.lineSeparator &&
          core.vm.mathContext == binding.environmentAssumptions.mathContext &&
          core.i18n.textNormalizationPolicy == "unicode-nfc" &&
          core.i18n.textComparisonPolicy == "case-sensitive" &&
          core.i18n.dateTimeFormatPolicy == "iso-8601"
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("all supported assumptions are carried by the core execution context")
      checked.passed shouldBe true
    }

    "snapshot only allowlisted environment values without exposing their contents" in {
      Given("an allowlist, explicit and ambient values, and recognizable confidential material")
      val secret = "environment-secret-value-73519"
      val configuration = _configuration(Map(
        RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> "PUBLIC_SETTING,AMBIENT_SETTING",
        s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.PUBLIC_SETTING" -> secret
      ))
      val ambient = Map(
        "PUBLIC_SETTING" -> "explicit-value-must-win",
        "AMBIENT_SETTING" -> "ambient-visible",
        "UNDECLARED_SETTING" -> "ambient-hidden"
      )

      When("the profile is resolved and its diagnostics are rendered")
      val profile = ExecutionProfileResolver
        .resolve_with_environment(configuration, OperationMode.Production, ambient)
        .toOption
        .get
      val binding = profile.newRuntime(IdGenerationContext.DEFAULT_NAMESPACE).baseBinding
      val rendered = profile.toString + profile.environmentAssumptions.toRecord.toString + profile.control.toRecord.toString

      Then("the immutable snapshot contains only the declared name and diagnostics omit its value")
      binding.environmentAssumptions.environmentVariables shouldBe Map(
        "PUBLIC_SETTING" -> secret,
        "AMBIENT_SETTING" -> "ambient-visible"
      )
      binding.environmentAssumptions.environmentVariables should not contain key("UNDECLARED_SETTING")
      rendered should include ("PUBLIC_SETTING")
      rendered should not include secret
      rendered should not include "ambient-visible"
    }

    "reject configured environment values outside the allowlist" in {
      Given("an environment value whose name is not declared")
      val configuration = _configuration(Map(
        RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> "DECLARED_SETTING",
        s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.UNDECLARED_SETTING" -> "rejected"
      ))

      When("the execution profile is resolved")
      val result = ExecutionProfileResolver.resolve(configuration, OperationMode.Production)

      Then("bootstrap returns a deterministic structured configuration failure")
      result shouldBe a[Consequence.Failure[?]]
      _failure_message(result) should include (RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY)
      _failure_message(result) should include ("UNDECLARED_SETTING")
    }

    "accept format-neutral list and object configuration for environment assumptions" in {
      Given("nested configuration values produced by a YAML, JSON, or HOCON decoder")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "textus" -> ConfigurationValue.ObjectValue(Map(
            "execution" -> ConfigurationValue.ObjectValue(Map(
              "environment" -> ConfigurationValue.ObjectValue(Map(
                "allow" -> ConfigurationValue.ListValue(List(
                  ConfigurationValue.StringValue("FIRST_SETTING"),
                  ConfigurationValue.StringValue("SECOND_SETTING")
                )),
                "values" -> ConfigurationValue.ObjectValue(Map(
                  "FIRST_SETTING" -> ConfigurationValue.StringValue("first"),
                  "SECOND_SETTING" -> ConfigurationValue.StringValue("second")
                ))
              ))
            ))
          ))
        )),
        ConfigurationTrace.empty
      )

      When("the execution profile resolves the structural configuration")
      val assumptions = ExecutionProfileResolver
        .resolve_with_environment(configuration, OperationMode.Production, Map.empty)
        .toOption
        .get
        .environmentAssumptions

      Then("the same immutable allowlisted snapshot is produced without format-specific parsing")
      assumptions.environmentVariables shouldBe Map(
        "FIRST_SETTING" -> "first",
        "SECOND_SETTING" -> "second"
      )
    }

    "derive repeatable invocation streams for arbitrary seeded profile inputs" in {
      Given("generated seeds and run keys for two independent seeded runtimes")
      val inputs = for {
        seed <- _non_empty_token
        runkey <- _non_empty_token
      } yield (seed, runkey)

      When("the same explicit invocation and purpose stream are read")
      val property = Prop.forAll(inputs) { case (seed, runkey) =>
        val configuration = _configuration(Map(
          RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
          RuntimeConfig.EXECUTION_KEY -> runkey,
          RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> seed
        ))
        val leftprofile = ExecutionProfileResolver.resolve(configuration, OperationMode.Production).toOption.get
        val rightprofile = ExecutionProfileResolver.resolve(configuration, OperationMode.Production).toOption.get

        val namespace = IdGenerationContext.DEFAULT_NAMESPACE
        val left = leftprofile.newRuntime(namespace).nextBinding("catalog.price", Some("price-case"))
        val right = rightprofile.newRuntime(namespace).nextBinding("catalog.price", Some("price-case"))
        val leftvalues = Vector.fill(4)(left.random.stream("price-table").nextLong())
        val rightvalues = Vector.fill(4)(right.random.stream("price-table").nextLong())

        leftvalues == rightvalues &&
          left.control.invocation.map(_.key).contains("price-case")
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("all generated inputs reproduce and diagnostics remain redacted")
      checked.passed shouldBe true
    }

    "derive controlled ID sequences per invocation without consuming domain random streams" in {
      Given("two controlled runtimes with repeated explicit invocation keys")
      val config = _controlled_configuration("id-run", "domain-random-seed")
      val leftprofile = ExecutionProfileResolver.resolveForSpec(config).toOption.get
      val rightprofile = ExecutionProfileResolver.resolveForSpec(config).toOption.get
      val namespace = IdGenerationContext.DEFAULT_NAMESPACE
      val collection = org.simplemodeling.model.datatype.EntityCollectionId("sample", "catalog", "article")
      val leftruntime = leftprofile.newRuntime(namespace)
      val rightruntime = rightprofile.newRuntime(namespace)

      When("each runtime binds two invocation ordinals and one side generates IDs before domain random")
      val leftfirst = leftruntime.nextBinding("catalog.article.create", Some("same-explicit-key"))
      val leftfirstid = leftfirst.idGeneration.entityId(collection, "article.create")
      val leftrandom = leftfirst.random.stream("price-table").nextLong()
      val leftsecond = leftruntime.nextBinding("catalog.article.create", Some("same-explicit-key"))
      val leftsecondid = leftsecond.idGeneration.entityId(collection, "article.create")

      val rightfirst = rightruntime.nextBinding("catalog.article.create", Some("same-explicit-key"))
      val rightrandom = rightfirst.random.stream("price-table").nextLong()
      val rightfirstid = rightfirst.idGeneration.entityId(collection, "article.create")
      val rightsecond = rightruntime.nextBinding("catalog.article.create", Some("same-explicit-key"))
      val rightsecondid = rightsecond.idGeneration.entityId(collection, "article.create")

      Then("ordinal-bound IDs reproduce, remain unique, and use no domain-random values")
      leftfirstid shouldBe rightfirstid
      leftsecondid shouldBe rightsecondid
      leftfirstid should not be leftsecondid
      leftrandom shouldBe rightrandom
    }

    "redact seed and run-key material from profile diagnostics" in {
      Given("a seeded profile containing recognizable confidential material")
      val seed = "sensitive-seed-material-918273"
      val runkey = "sensitive-run-key-564738"
      val configuration = _configuration(Map(
        RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
        RuntimeConfig.EXECUTION_KEY -> runkey,
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> seed
      ))

      When("profile and control diagnostics are rendered")
      val profile = ExecutionProfileResolver.resolve(configuration, OperationMode.Production).toOption.get
      val rendered = profile.toString + profile.control.toRecord.toString

      Then("only the sanitized fingerprint is visible")
      rendered should not include seed
      rendered should not include runkey
      rendered should include (profile.identity.fingerprint)
    }

    "reject controlled mode outside an explicit test activation" in {
      Given("a complete controlled profile supplied to ordinary production startup")
      val configuration = _controlled_configuration("ordinary-run", "ordinary-seed")

      When("the profile is resolved without test provenance")
      val result = ExecutionProfileResolver.resolve(configuration, OperationMode.Production)

      Then("normal structured configuration failure is returned")
      result shouldBe a[Consequence.Failure[?]]
      _failure_message(result) should include ("explicit test descriptor")
    }

    "accept controlled mode through the in-process executable-spec builder" in {
      Given("a complete controlled profile")
      val configuration = _controlled_configuration("spec-run", "spec-seed")

      When("the explicit in-process spec resolver is used")
      val profile = ExecutionProfileResolver.resolveForSpec(configuration).toOption.get

      Then("manual time and deterministic CNCF controls form one replayable bundle")
      profile.identity.mode shouldBe ExecutionProfileMode.Controlled
      profile.runtimeClock.mode shouldBe RuntimeClockMode.Manual
      profile.runtimeClock.clock.instant() shouldBe Instant.parse("2026-07-28T09:00:00Z")
      profile.control.randomMode shouldBe ExecutionRandomMode.Seeded
      profile.control.idMode shouldBe ExecutionIdMode.Deterministic
      profile.control.schedulerMode shouldBe ExecutionSchedulerMode.Manual
      profile.control.orderingMode shouldBe ExecutionOrderingMode.Deterministic
      profile.control.replayability.state shouldBe ReplayabilityState.Replayable
    }

    "advance the runtime-owned manual clock for arbitrary positive durations" in {
      Given("a controlled runtime and generated positive durations")
      val durations = Gen.chooseNum(1L, 86400000L)

      When("the in-process test control advances operational time")
      val property = Prop.forAll(durations) { millis =>
        val profile = ExecutionProfileResolver
          .resolveForSpec(_controlled_configuration("manual-clock-run", "manual-clock-seed"))
          .toOption
          .get
        val runtime = profile.newRuntime(IdGenerationContext.DEFAULT_NAMESPACE)
        val binding = runtime.baseBinding
        val control = runtime.testControl.get
        val before = binding.clock.instant()

        control.advanceBy(Duration.ofMillis(millis))

        control.now == before.plusMillis(millis) &&
          binding.clock.instant() == control.now &&
          control.runUntilIdle() == 0
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("clock reads advance without host sleeping and no absent timer becomes due")
      checked.passed shouldBe true
    }

    "isolate manual time and ID timestamps between runtime instances" in {
      Given("two runtimes created from one controlled profile template")
      val profile = ExecutionProfileResolver
        .resolveForSpec(_controlled_configuration("isolated-clock-run", "isolated-clock-seed"))
        .toOption
        .get
      val left = profile.newRuntime(IdGenerationContext.DEFAULT_NAMESPACE)
      val right = profile.newRuntime(IdGenerationContext.DEFAULT_NAMESPACE)
      val start = profile.runtimeClock.clock.instant()
      val collection = org.simplemodeling.model.datatype.EntityCollectionId(
        "sample",
        "catalog",
        "article"
      )

      When("only the left runtime advances and creates an invocation-bound ID")
      left.testControl.get.advanceBy(Duration.ofMinutes(5L))
      val binding = left.nextBinding("catalog.article.create", Some("advanced-id"))
      val id = binding.idGeneration.entityId(collection, "article.create")

      Then("the left clock and ID advance while the template and right runtime stay unchanged")
      left.runtimeClock.clock.instant() shouldBe start.plus(Duration.ofMinutes(5L))
      id.timestamp shouldBe Some(left.runtimeClock.clock.instant())
      right.runtimeClock.clock.instant() shouldBe start
      profile.runtimeClock.clock.instant() shouldBe start
      left.runtimeClock.clock should not be theSameInstanceAs(right.runtimeClock.clock)
    }

    "reject a dimension that contradicts its selected profile" in {
      Given("a seeded profile that attempts to select deterministic identifiers")
      val configuration = _configuration(Map(
        RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> "profile-seed",
        RuntimeConfig.EXECUTION_IDS_MODE_KEY -> "deterministic"
      ))

      When("the profile is validated directly and through the runtime config factory")
      val result = ExecutionProfileResolver.resolve(configuration, OperationMode.Production)
      val runtimeconfig = RuntimeConfig.create(configuration)

      Then("the incompatibility remains the same structured configuration failure")
      result shouldBe a[Consequence.Failure[?]]
      runtimeconfig shouldBe a[Consequence.Failure[?]]
      _failure_message(result) should include ("requires id mode production")
      _failure_conclusion(runtimeconfig).observation.taxonomy shouldBe
        _failure_conclusion(result).observation.taxonomy
      _failure_conclusion(runtimeconfig).observation.cause.kind shouldBe
        _failure_conclusion(result).observation.cause.kind
    }

    "keep the offset-clock compatibility key coherent with unified time configuration" in {
      Given("different instants in the compatibility and unified clock keys")
      val configuration = _configuration(Map(
        RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY -> "2026-07-28T09:00:00Z",
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> "offset",
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> "2026-07-29T09:00:00Z"
      ))

      When("the profile is resolved")
      val result = ExecutionProfileResolver.resolve(configuration, OperationMode.Production)

      Then("the conflicting clock sources are rejected")
      result shouldBe a[Consequence.Failure[?]]
      _failure_message(result) should include ("conflicts")
    }
  }

  "RuntimeTestDescriptor" should {
    "normalize structured execution shorthand into canonical runtime keys" in {
      Given("an explicit test descriptor with a controlled execution block")
      val path = Files.createTempFile("cncf-execution-profile", ".yaml")
      Files.writeString(
        path,
        """kind: test-descriptor
          |execution:
          |  profile: controlled
          |  key: descriptor-run
          |  time:
          |    mode: manual
          |    start-at: 2026-07-28T09:00:00Z
          |  random:
          |    mode: seeded
          |    seed: descriptor-seed
          |  ids:
          |    mode: deterministic
          |  scheduler:
          |    mode: manual
          |  ordering:
          |    mode: deterministic
          |""".stripMargin
      )

      When("the descriptor is decoded")
      val descriptor = RuntimeTestDescriptor.load(path).toOption.get

      Then("every execution dimension uses its canonical configuration key")
      descriptor.config(RuntimeConfig.EXECUTION_PROFILE_KEY) shouldBe "controlled"
      descriptor.config(RuntimeConfig.EXECUTION_KEY) shouldBe "descriptor-run"
      descriptor.config(RuntimeConfig.EXECUTION_TIME_MODE_KEY) shouldBe "manual"
      descriptor.config(RuntimeConfig.EXECUTION_TIME_START_AT_KEY) shouldBe "2026-07-28T09:00:00Z"
      descriptor.config(RuntimeConfig.EXECUTION_RANDOM_MODE_KEY) shouldBe "seeded"
      descriptor.config(RuntimeConfig.EXECUTION_RANDOM_SEED_KEY) shouldBe "descriptor-seed"
      descriptor.config(RuntimeConfig.EXECUTION_IDS_MODE_KEY) shouldBe "deterministic"
      descriptor.config(RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY) shouldBe "manual"
      descriptor.config(RuntimeConfig.EXECUTION_ORDERING_MODE_KEY) shouldBe "deterministic"
    }

    "normalize structured environment assumptions without retaining provider-specific shape" in {
      Given("an explicit test descriptor with i18n and allowlisted environment assumptions")
      val path = Files.createTempFile("cncf-execution-environment", ".yaml")
      Files.writeString(
        path,
        """kind: test-descriptor
          |execution:
          |  assumptions:
          |    locale: ja-JP
          |    timezone: Asia/Tokyo
          |    charset: UTF-8
          |    line-separator: lf
          |    math-context: decimal128
          |    i18n:
          |      text-normalization-policy: unicode-nfc
          |      text-comparison-policy: case-sensitive
          |      date-time-format-policy: iso-8601
          |    environment:
          |      allow:
          |        - PUBLIC_SETTING
          |      values:
          |        PUBLIC_SETTING: visible-to-component
          |""".stripMargin
      )

      When("the descriptor is decoded")
      val descriptor = RuntimeTestDescriptor.load(path).toOption.get

      Then("the shorthand maps to the canonical runtime configuration keys")
      descriptor.config(RuntimeConfig.EXECUTION_LOCALE_KEY) shouldBe "ja-JP"
      descriptor.config(RuntimeConfig.EXECUTION_TIMEZONE_KEY) shouldBe "Asia/Tokyo"
      descriptor.config(RuntimeConfig.EXECUTION_CHARSET_KEY) shouldBe "UTF-8"
      descriptor.config(RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY) shouldBe "lf"
      descriptor.config(RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY) shouldBe "decimal128"
      descriptor.config(RuntimeConfig.EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY) shouldBe "unicode-nfc"
      descriptor.config(RuntimeConfig.EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY) shouldBe "case-sensitive"
      descriptor.config(RuntimeConfig.EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY) shouldBe "iso-8601"
      descriptor.config(RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY) shouldBe "PUBLIC_SETTING"
      descriptor.config(s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.PUBLIC_SETTING") shouldBe "visible-to-component"
    }

    "normalize JSON execution shorthand through the same canonical keys" in {
      Given("an explicit JSON test descriptor with a seeded execution block")
      val path = Files.createTempFile("cncf-execution-profile", ".json")
      Files.writeString(
        path,
        """{
          |  "kind": "test-descriptor",
          |  "execution": {
          |    "profile": "seeded",
          |    "key": "json-run",
          |    "time": {
          |      "mode": "offset",
          |      "start-at": "2026-07-28T09:00:00Z"
          |    },
          |    "random": {
          |      "mode": "seeded",
          |      "seed": "json-seed"
          |    }
          |  }
          |}
          |""".stripMargin
      )

      When("the JSON descriptor is decoded")
      val descriptor = RuntimeTestDescriptor.load(path).toOption.get

      Then("the format-independent execution keys have the same canonical shape")
      descriptor.config(RuntimeConfig.EXECUTION_PROFILE_KEY) shouldBe "seeded"
      descriptor.config(RuntimeConfig.EXECUTION_KEY) shouldBe "json-run"
      descriptor.config(RuntimeConfig.EXECUTION_TIME_MODE_KEY) shouldBe "offset"
      descriptor.config(RuntimeConfig.EXECUTION_TIME_START_AT_KEY) shouldBe "2026-07-28T09:00:00Z"
      descriptor.config(RuntimeConfig.EXECUTION_RANDOM_MODE_KEY) shouldBe "seeded"
      descriptor.config(RuntimeConfig.EXECUTION_RANDOM_SEED_KEY) shouldBe "json-seed"
    }
  }

  "ExecutionContext profile binding" should {
    "assign invocation identity and propagate one profile into the UnitOfWork context" in {
      Given("generated explicit keys and a controlled runtime context")
      When("each key is admitted at the ActionCall execution boundary")
      val property = Prop.forAll(_non_empty_token) { explicitkey =>
        val config = _runtime_config(_controlled_configuration("binding-run", "binding-seed"))
        val context = _runtime_context(config)
        val prepared = ExecutionContext.withExplicitExecutionInvocationKey(context, explicitkey)

        val bound = ExecutionContext.withExecutionInvocation(prepared, "catalog.price.calculate")

        bound.executionControl.profile == config.executionProfile.identity &&
          bound.executionControl.invocation.map(_.key).contains(explicitkey) &&
          bound.executionControl.invocation.map(_.ordinal).contains(1L) &&
          bound.executionControl.idMode == ExecutionIdMode.Deterministic &&
          (bound.clock eq prepared.clock) &&
          (bound.clock ne config.executionClock.clock) &&
          bound.idGeneration.namespace == config.idNamespace &&
          bound.runtime.unitOfWork.executionContext.executionControl == bound.executionControl &&
          (bound.runtime.unitOfWork.executionContext.random eq bound.random)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

      Then("core, CNCF, and UnitOfWork contexts carry the same invocation-bound profile")
      checked.passed shouldBe true
    }

    "replace all profile-dependent capabilities when moving to another runtime" in {
      Given("an invocation bound to one seeded runtime and a second runtime with another profile")
      val leftconfig = _runtime_config(_configuration(Map(
        RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> "left-seed",
        RuntimeConfig.EXECUTION_LOCALE_KEY -> "en-US",
        RuntimeConfig.EXECUTION_TIMEZONE_KEY -> "UTC",
        RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> "RUNTIME_SIDE",
        s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.RUNTIME_SIDE" -> "left"
      )))
      val rightconfig = _runtime_config(_configuration(_controlled_configuration_values("right-run", "right-seed") ++ Map(
        RuntimeConfig.EXECUTION_LOCALE_KEY -> "ja-JP",
        RuntimeConfig.EXECUTION_TIMEZONE_KEY -> "Asia/Tokyo",
        RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> "RUNTIME_SIDE",
        s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.RUNTIME_SIDE" -> "right"
      )))
      val left = ExecutionContext.withExecutionInvocation(_runtime_context(leftconfig), "catalog.price")
      val rightcontext = _runtime_context(rightconfig)
      val rightruntime = rightcontext.runtime

      When("the execution context is rebound beneath the second global runtime")
      val rebound = ExecutionContext.withRuntimeContext(left, rightruntime)

      Then("no invocation, random, id, clock, or profile metadata survives from the first runtime")
      rebound.executionControl.profile shouldBe rightconfig.executionProfile.identity
      rebound.executionControl.invocation shouldBe None
      rebound.executionControl.idMode shouldBe ExecutionIdMode.Deterministic
      rebound.clock should be theSameInstanceAs rightcontext.clock
      rebound.clock should not be theSameInstanceAs(rightconfig.executionClock.clock)
      rebound.random should not be theSameInstanceAs(left.random)
      rebound.idGeneration should not be theSameInstanceAs(left.idGeneration)
      rebound.locale shouldBe java.util.Locale.forLanguageTag("ja-JP")
      rebound.timezone shouldBe java.time.ZoneId.of("Asia/Tokyo")
      rebound.vm.environmentVariables shouldBe Map("RUNTIME_SIDE" -> "right")
    }

    "select the runtime-owned profile when scope and runtime originate from different trees" in {
      Given("a seeded scope and a controlled runtime from separate runtime trees")
      val scopeconfig = _runtime_config(_configuration(Map(
        RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> "scope-seed"
      )))
      val runtimeconfig = _runtime_config(_controlled_configuration("runtime-run", "runtime-seed"))
      val scopecontext = _runtime_context(scopeconfig)
      val runtimecontext = _runtime_context(runtimeconfig)

      When("an execution context is created with the foreign scope and runtime")
      val created = ExecutionContext.create(scopecontext.runtime, runtimecontext.runtime)

      Then("the runtime lifecycle owner supplies every profile-dependent capability")
      created.executionControl.profile shouldBe runtimeconfig.executionProfile.identity
      created.executionControl.invocation shouldBe None
      created.executionControl.idMode shouldBe ExecutionIdMode.Deterministic
      created.clock should be theSameInstanceAs runtimecontext.clock
      created.clock should not be theSameInstanceAs(runtimeconfig.executionClock.clock)
      created.idGeneration.namespace shouldBe runtimeconfig.idNamespace
    }

    "bind profile invocation identity when ComponentLogic creates an ActionCall" in {
      Given("a component action and an execution context under a seeded runtime")
      val config = _runtime_config(_configuration(Map(
        RuntimeConfig.EXECUTION_PROFILE_KEY -> "seeded",
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> "component-admission-seed"
      )))
      val context = _runtime_context(config)
      val component = TestComponentFactory.create("catalog", Protocol.empty)
      val action = ProfileQueryAction(Request.of(
        component = "catalog",
        service = "price",
        operation = "calculate"
      ))

      When("ComponentLogic admits the action and creates its ActionCall")
      val call = ComponentLogic(component).createActionCall(action, context)
      val nextcall = ComponentLogic(component).createActionCall(action, context)

      Then("the ActionCall has a monotonic invocation identity before execution")
      call.executionContext.executionControl.invocation.map(_.ordinal) shouldBe Some(1L)
      nextcall.executionContext.executionControl.invocation.map(_.ordinal) shouldBe Some(2L)
      nextcall.executionContext.executionControl.invocation.map(_.key) should not be
        call.executionContext.executionControl.invocation.map(_.key)
      call.executionContext.executionControl.invocation.map(_.operationSelector) shouldBe
        Some(s"${component.componentId.name}.price.calculate")
      call.executionContext.runtime.unitOfWork.executionContext.executionControl shouldBe
        call.executionContext.executionControl
    }

    "keep resolved environment assumptions stable across ActionCall admission" in {
      Given("a component action under a runtime with explicit generic assumptions")
      val config = _runtime_config(_configuration(Map(
        RuntimeConfig.EXECUTION_LOCALE_KEY -> "ja-JP",
        RuntimeConfig.EXECUTION_TIMEZONE_KEY -> "Asia/Tokyo",
        RuntimeConfig.EXECUTION_CHARSET_KEY -> "UTF-16",
        RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY -> "crlf",
        RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY -> "decimal128",
        RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> "CATALOG_MODE",
        s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.CATALOG_MODE" -> "spec"
      )))
      val context = _runtime_context(config)
      val component = TestComponentFactory.create("catalog", Protocol.empty)
      val action = ProfileQueryAction(Request.of(component = "catalog", service = "price", operation = "calculate"))

      When("ComponentLogic creates two invocation-scoped ActionCalls")
      val first = ComponentLogic(component).createActionCall(action, context).executionContext
      val second = ComponentLogic(component).createActionCall(action, context).executionContext

      Then("both ActionCalls and their UnitOfWork contexts retain the bootstrap snapshot")
      first.locale shouldBe java.util.Locale.forLanguageTag("ja-JP")
      first.timezone shouldBe java.time.ZoneId.of("Asia/Tokyo")
      first.encoding shouldBe java.nio.charset.Charset.forName("UTF-16")
      first.vm.lineSeparator shouldBe "\r\n"
      first.math shouldBe java.math.MathContext.DECIMAL128
      first.vm.environmentVariables shouldBe Map("CATALOG_MODE" -> "spec")
      second.vm.environmentVariables shouldBe first.vm.environmentVariables
      first.runtime.unitOfWork.executionContext.vm.environmentVariables shouldBe first.vm.environmentVariables
    }
  }

  private def _controlled_configuration(
    runkey: String,
    seed: String
  ): ResolvedConfiguration =
    _configuration(_controlled_configuration_values(runkey, seed))

  private def _controlled_configuration_values(
    runkey: String,
    seed: String
  ): Map[String, String] =
    Map(
      RuntimeConfig.operationModeKey -> "test",
      RuntimeConfig.EXECUTION_PROFILE_KEY -> "controlled",
      RuntimeConfig.EXECUTION_KEY -> runkey,
      RuntimeConfig.EXECUTION_TIME_MODE_KEY -> "manual",
      RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> "2026-07-28T09:00:00Z",
      RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> "seeded",
      RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> seed,
      RuntimeConfig.EXECUTION_IDS_MODE_KEY -> "deterministic",
      RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> "manual",
      RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> "deterministic"
    )

  private def _runtime_config(configuration: ResolvedConfiguration): RuntimeConfig =
    RuntimeConfig.from(configuration)

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(_) => ""
    }

  private def _failure_conclusion[A](result: Consequence[A]): org.goldenport.Conclusion =
    result match {
      case Consequence.Failure(conclusion) => conclusion
      case Consequence.Success(_) => fail("expected structured failure")
    }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue.apply).toMap),
      ConfigurationTrace.empty
    )

  private def _runtime_context(config: RuntimeConfig): ExecutionContext = {
    val base = ExecutionContext.create()
    val global = GlobalRuntimeContext.create(
      "execution-profile-spec",
      config,
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      base.observability,
      AliasResolver.empty
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "execution-profile-spec",
        parent = Some(global),
        observabilityContext = base.observability
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          Consequence.serviceUnavailable("not used by execution-profile spec")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "execution-profile-spec",
      operationMode = config.operationMode
    )
    context
  }
}

private final case class ProfileQueryAction(request: Request) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    ProfileQueryActionCall(core)
}

private final case class ProfileQueryActionCall(core: ActionCall.Core)
  extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("profile-bound"))
}
