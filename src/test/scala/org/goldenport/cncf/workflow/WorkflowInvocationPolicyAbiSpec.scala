package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import domain.statemachine.workflow.{StateMachineWorkflowAbi => Producer,
  InvocationControlledWorkflowStateMachineWorkflow1 => Generated,
  StateMachineWorkflowComponentFactoryBootstrap => Bootstrap}

/** Executable contract for actual generated metadata admission.
  * @since   Oct.  2, 2026
  * @version Oct.  2, 2026
  */
final class WorkflowInvocationPolicyAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import WorkflowInvocationPolicyAbi.*

  private val _raw = _resource("/workflow/invocation-policy-producer-abi.json")
  private val _legacy = _resource("/workflow/invocation-policy-legacy-producer-abi.json")
  private val _json = parse(_raw).toOption.get
  private val _label = "modeler/workflow-invocation-policy.cml"
  private val _workflow = Vector("workflows", "0")
  private val _policy = _workflow ++ Vector("invocationPolicies", "0")
  private val _action = _workflow ++ Vector("actions", "0")
  private val _spi = _workflow ++ Vector("requiredSpi", "0")

  "Generated invocation metadata" should {
    "retain actual producer facts" which {
      "admits the complete ordered v2 artifact and exact bytes" in {
        Given("the unmodified public-route producer JSON and caller correlation label")
        val raw = _raw
        When("the metadata is parsed and readmitted")
        val artifact = _take(parseC(raw, _label))
        val readmitted = admitC(artifact)
        Then("every workflow fact and declared policy survives without defaults")
        artifact shouldBe Artifact(schemaVersion, _label, raw, Vector(_expected),
          "bbbca0dad63e18ce4f12def1c42edd7d94ede03a3b7b2736b8028b2a6bb422d1")
        readmitted shouldBe Consequence.success(artifact)
        artifact.workflows.head.invocationPolicies(1).retry shouldBe None
        artifact.workflows.head.invocationPolicies(1).executionTimeoutMillis shouldBe Some(Long.MaxValue)
      }
      "equals compiled producer descriptor and bootstrap values" in {
        Given("the actual generated Scala descriptor and its direct bootstrap wrapper")
        val generated = Generated.workflow
        When("the matching actual JSON is admitted")
        val artifact = _take(parseC(_raw, _label))
        val workflow = artifact.workflows.head
        Then("typed values equal every compiled descriptor field")
        Producer.VERSION shouldBe artifact.schemaVersion
        generated.schemaVersion shouldBe artifact.schemaVersion
        workflow.identity shouldBe generated.identity
        workflow.version shouldBe generated.version
        workflow.source shouldBe WorkflowSourceCorrelation(_source(generated.source.root), _source(generated.source.definition))
        workflow.states shouldBe generated.states.map(state => State(state.name, _source(state.source)))
        workflow.actions shouldBe generated.actions.map(action => Action(action.identity,
          ActionKind.values.find(_.value == action.kind).get, _operation(action.operation),
          action.inputBinding, _source(action.source)))
        workflow.requiredSpi shouldBe generated.requiredOperations.map { spi =>
          val action = generated.actions.find(_.identity == spi.actionIdentity).get
          spi.actionSource shouldBe action.source
          spi.operation.service shouldBe action.operation.service
          spi.operation.operation shouldBe action.operation.name
          spi.inputType.map(_.value) shouldBe action.operation.inputType
          spi.resultType.map(_.value) shouldBe action.operation.resultType
          RequiredSpi(spi.identity.capability, spi.actionIdentity, _operation(action.operation), _source(spi.capabilitySource))
        }
        workflow.invocationPolicies shouldBe generated.invocationPolicies.map { policy =>
          val target = policy.target match {
            case Producer.InvocationTarget.Action(identity) => InvocationTarget.Action(identity)
            case Producer.InvocationTarget.Participant(identity) => InvocationTarget.Participant(identity)
          }
          InvocationPolicy(policy.identity, target, policy.actionIdentity,
            policy.retry.map(retry => RetryPolicy(retry.maximumAttempts, retry.fixedRetryDelayMillis)),
            policy.executionTimeoutMillis, _source(policy.source))
        }
        Bootstrap.schemaVersion shouldBe "cozy.cml.statemachine-workflow-bootstrap.v2"
        Bootstrap.componentFactoryMetadata shouldBe Vector(Generated.componentFactoryMetadata)
        Bootstrap.componentFactoryMetadata.head.workflow shouldBe generated
      }
      "retains independent legacy v1 facts and pinned producer bytes" in {
        Given("the actual independently generated legacy route artifact")
        val raw = _legacy
        When("legacy metadata is admitted")
        val artifact = _take(parseC(raw, "legacy.cml"))
        val readmitted = admitC(artifact)
        Then("prior facts remain unchanged and policies are empty")
        artifact.sha256 shouldBe "c1ec6d672e76f060b1047880e50db38627ea54f80b8833e1f7a590c06ea79626"
        artifact.rawJson shouldBe raw
        artifact.schemaVersion shouldBe legacySchemaVersion
        artifact.workflows shouldBe Vector(_expected.copy(identity = "OrderProgress", version = "workflow-v1",
          actions = _expected.actions.take(1), requiredSpi = Vector(_expected.requiredSpi.head.copy(source = SourceIdentity(Some(69)))),
          invocationPolicies = Vector.empty))
        readmitted shouldBe Consequence.success(artifact)
      }
    }
    "interpret versioned collections" which {
      "accepts empty v1 and rejects policy fields even when empty" in {
        Given("legacy producer metadata with bounded collection and policy-field mutations")
        val legacy = parse(_legacy).toOption.get
        val empty = _change(legacy, Vector.empty)(_.mapObject(_.add("workflows", Json.arr())))
        val added = _change(legacy, _workflow)(_.mapObject(_.add("invocationPolicies", Json.arr())))
        When("the versioned artifacts are parsed")
        val admitted = parseC(empty.noSpaces, _label)
        val rejected = parseC(added.noSpaces, _label)
        Then("empty v1 remains legal and the closed v1 record rejects the new field")
        _take(admitted).workflows shouldBe Vector.empty
        _diagnostic(rejected).code shouldBe DiagnosticCode.InvalidShape
      }
      "preserves mixed members and order but requires some v2 metadata" in {
        Given("actual v2 metadata and a metadata-free copy, including repeated workflow identities")
        val member = _at(_json, _workflow)
        val free = member.mapObject(_.add("invocationPolicies", Json.arr())
          .add("actions", Json.arr()).add("requiredSpi", Json.arr()))
        val mixed = _set(_json, Vector("workflows"), Json.arr(free, member, free))
        val empty = _set(_json, Vector("workflows"), Json.arr())
        val allfree = _set(_json, Vector("workflows"), Json.arr(free))
        When("these collection variants are parsed")
        val result = _take(parseC(mixed.noSpaces, _label))
        val failures = Vector(empty, allfree).map(json => parseC(json.noSpaces, _label))
        Then("declaration order is retained without a workflow-identity or count restriction")
        result.workflows.map(_.invocationPolicies) shouldBe Vector(Vector.empty, _expected.invocationPolicies, Vector.empty)
        result.workflows.head.actions shouldBe Vector.empty
        result.workflows.head.requiredSpi shouldBe Vector.empty
        failures.map(_diagnostic(_).code) shouldBe Vector.fill(2)(DiagnosticCode.InvalidValue)
      }
      "rejects missing v2 policy vectors and unknown schema versions" in {
        Given("a missing policy vector and an unsupported schema")
        val missing = _change(_json, _workflow)(_.mapObject(_.remove("invocationPolicies")))
        val unknown = _set(_json, Vector("schemaVersion"), Json.fromString("future"))
        When("the changed artifacts are parsed")
        val missingresult = parseC(missing.noSpaces, _label)
        val unknownresult = parseC(unknown.noSpaces, _label)
        Then("shape and schema diagnostics preserve stable context")
        _diagnostic(missingresult).code shouldBe DiagnosticCode.InvalidShape
        _diagnostic(unknownresult).code shouldBe DiagnosticCode.UnsupportedSchema
        _diagnostic(unknownresult).data("schema") shouldBe "future"
      }
    }
    "interpret canonical integers and absence" which {
      "admits exact numeric endpoints and each single-control policy" in {
        Given("actual fixture mutations at all admitted endpoints")
        val variants = Vector((1, 0L, 1L), (Int.MaxValue, Long.MaxValue, Long.MaxValue))
        val raws = variants.map { case (attempts, delay, timeout) => _numbers(attempts, delay, timeout) }
        val sourcemax = _set(_json, _policy ++ Vector("source", "line"), Json.fromInt(Int.MaxValue))
        val retryonly = _set(_json, _policy :+ "executionTimeoutMillis", Json.Null)
        val timeoutonly = _set(_json, _policy :+ "retry", Json.Null)
        When("the endpoint and absence variants are admitted")
        val results = raws.map(raw => _take(parseC(raw, _label)).workflows.head.invocationPolicies.head)
        val singles = Vector(retryonly, timeoutonly).map(json => _take(parseC(json.noSpaces, _label)))
        val source = _take(parseC(sourcemax.noSpaces, _label)).workflows.head.invocationPolicies.head.source
        Then("integer values remain exact and missing controls acquire no default")
        results.map(policy => (policy.retry.get.maximumAttempts, policy.retry.get.fixedRetryDelayMillis,
          policy.executionTimeoutMillis.get)) shouldBe variants
        singles.head.workflows.head.invocationPolicies.head.executionTimeoutMillis shouldBe None
        singles(1).workflows.head.invocationPolicies.head.retry shouldBe None
        source.line shouldBe Some(Int.MaxValue)
      }
      "round trips the valid Int and Long value spaces exactly" in {
        Given("at least fifty generated valid integer triples applied to actual producer metadata")
        val values = for { attempts <- Gen.choose(1, Int.MaxValue); delay <- Gen.choose(0L, Long.MaxValue)
          timeout <- Gen.choose(1L, Long.MaxValue) } yield (attempts, delay, timeout)
        When("each mutated artifact is parsed and readmitted")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), Prop.forAll(values) {
          case (attempts, delay, timeout) =>
            val artifact = _take(parseC(_numbers(attempts, delay, timeout), _label))
            val policy = artifact.workflows.head.invocationPolicies.head
            policy.retry == Some(RetryPolicy(attempts, delay)) && policy.executionTimeoutMillis == Some(timeout) &&
              admitC(artifact) == Consequence.success(artifact)
        })
        Then("all successful cases preserve exact values without floating conversion")
        result.passed shouldBe true
        result.succeeded should be >= 50
      }
      "rejects out-of-range and noncanonical tokens in every integer position" in {
        Given("low/high boundaries, negatives, fractions, integral decimals, exponents, strings and booleans")
        val positions = Vector((_policy ++ Vector("retry", "maximumAttempts"), "0", "2147483648"),
          (_policy ++ Vector("retry", "fixedRetryDelayMillis"), "-1", "9223372036854775808"),
          (_policy :+ "executionTimeoutMillis", "0", "9223372036854775808"),
          (_policy ++ Vector("source", "line"), "0", "2147483648"))
        val cases = positions.flatMap { case (path, low, high) =>
          Vector(low, high, "-2", "0.5", "1.0", "1e0", "\"1\"", "true").map(token => _token(path, token))
        }
        When("each token is received through parseC")
        val results = cases.map(raw => parseC(raw, _label))
        Then("all categories fail as typed invalid values")
        results.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.InvalidValue)
      }
      "retains explicit null source and optional string fields" in {
        Given("all source positions and optional strings changed to explicit null")
        def _null_fields_(json: Json): Json = json.arrayOrObject(json,
          values => Json.fromValues(values.map(_null_fields_)), obj => Json.fromJsonObject(obj.mapValues(_null_fields_)
            .toVector.foldLeft(io.circe.JsonObject.empty) { case (acc, (key, value)) =>
              acc.add(key, if (Set("line", "inputType", "resultType", "inputBinding")(key)) Json.Null else value)
            }))
        val changed = _null_fields_(_json)
        When("the explicit absences are admitted")
        val workflow = _take(parseC(changed.noSpaces, _label)).workflows.head
        Then("None is preserved in every correlated source and optional string")
        workflow.source shouldBe WorkflowSourceCorrelation(SourceIdentity(None), SourceIdentity(None))
        workflow.states.map(_.source.line) shouldBe Vector(None, None)
        workflow.actions.foreach { action =>
          action.source.line shouldBe None
          action.operation.source.line shouldBe None
          action.operation.inputType shouldBe None
          action.operation.resultType shouldBe None
          action.inputBinding shouldBe None
        }
        workflow.requiredSpi.map(_.source.line) shouldBe Vector(None, None)
        workflow.invocationPolicies.map(_.source.line) shouldBe Vector(None, None)
      }
      "rejects incomplete retry records, wrong retry type and absent controls" in {
        Given("retry records missing either field, a scalar retry and a policy with neither control")
        val retry = _policy :+ "retry"
        val variants = Vector("maximumAttempts", "fixedRetryDelayMillis").map(key =>
          _change(_json, retry)(_.mapObject(_.remove(key)))) ++ Vector(_set(_json, retry, Json.fromBoolean(true)),
          _set(_set(_json, retry, Json.Null), _policy :+ "executionTimeoutMillis", Json.Null))
        When("the incomplete controls are parsed")
        val results = variants.map(json => parseC(json.noSpaces, _label))
        Then("missing or scalar retry is invalid shape and total absence is invalid value")
        results.map(_diagnostic(_).code) shouldBe Vector(DiagnosticCode.InvalidShape, DiagnosticCode.InvalidShape,
          DiagnosticCode.InvalidShape, DiagnosticCode.InvalidValue)
      }
    }
    "enforce the closed wire shape" which {
      "rejects missing and extra fields on every object level" in {
        Given("one missing or extra key on root, workflow, policy, target, retry, source, Action, Operation, SPI and state")
        val objects = Vector(Vector.empty[String], _workflow, _policy, _policy :+ "target", _policy :+ "retry",
          _policy :+ "source", _action, _action :+ "operation", _spi, _workflow ++ Vector("states", "0"),
          _workflow :+ "source")
        val variants = objects.flatMap(path => Vector(_change(_json, path)(json =>
          json.mapObject(obj => obj.remove(obj.keys.toVector.sorted.head))),
          _change(_json, path)(_.mapObject(_.add("extra", Json.Null)))))
        When("the bounded shape mutations are parsed")
        val results = variants.map(json => parseC(json.noSpaces, _label))
        Then("every object has a closed key set, including nullable keys")
        results.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.InvalidShape)
      }
      "rejects malformed JSON, duplicate keys and non-object or non-array shapes" in {
        Given("malformed and duplicate-key raw tokens plus structural type mutations")
        val malformed = Vector("", "  ", "{", _raw.replace("\"schemaVersion\":", "\"schemaVersion\":\"x\",\"schemaVersion\":"),
          _raw.replace("\"identity\":\"capture-control\"", "\"identity\":\"capture-control\",\"identity\":\"duplicate\""))
        val shapes = Vector("null", "[]", "true") ++ Vector(Vector("workflows"), _workflow, _policy,
          _policy :+ "target", _policy :+ "source", _action, _action :+ "operation", _spi,
          _workflow ++ Vector("states", "0")).map(path => _set(_json, path, Json.fromInt(1)).noSpaces)
        When("the raw and structural variants are parsed")
        val invalidjson = malformed.map(raw => parseC(raw, _label))
        val invalidshape = shapes.map(raw => parseC(raw, _label))
        Then("syntax/duplicate-key failures differ from shape failures without leaking parser text")
        invalidjson.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.InvalidJson)
        invalidshape.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.InvalidShape)
      }
      "rejects blank, null and typed string violations and unknown kinds" in {
        Given("required and optional strings with blank/null/type mutations and unknown kind tokens")
        val required = _workflow :+ "identity"
        val optional = _action :+ "inputBinding"
        val variants = Vector(required -> Json.Null, required -> Json.fromInt(1), required -> Json.fromString(" "),
          optional -> Json.fromBoolean(false), optional -> Json.fromString(" "),
          (_action :+ "kind") -> Json.fromString("UNKNOWN"), (_policy ++ Vector("target", "kind")) -> Json.fromString("PROVIDER"))
        When("each string violation is parsed")
        val results = variants.map { case (path, value) => parseC(_set(_json, path, value).noSpaces, _label) }
        Then("non-string values are invalid shape and blank or unknown values are invalid value")
        results.map(_diagnostic(_).code) shouldBe Vector(DiagnosticCode.InvalidShape, DiagnosticCode.InvalidShape,
          DiagnosticCode.InvalidValue, DiagnosticCode.InvalidShape, DiagnosticCode.InvalidValue,
          DiagnosticCode.InvalidValue, DiagnosticCode.InvalidValue)
      }
    }
    "bind declared targets and producer correlations" which {
      "preserves Action and Participant aliases and other nonblank strings" in {
        Given("declared aliases with distinct spelling and preserved caller strings")
        val alias = _set(_set(_json, _policy ++ Vector("target", "identity"), Json.fromString("CAPTURE_PAYMENT")),
          _workflow ++ Vector("invocationPolicies", "1", "target", "identity"), Json.fromString("Check Payment Capability"))
        val changed = _set(alias, _workflow :+ "version", Json.fromString(" opaque version "))
        When("the aliases are resolved")
        val workflow = _take(parseC(changed.noSpaces, " label ")).workflows.head
        Then("declared spelling remains intact and canonical Action bindings remain exact")
        workflow.invocationPolicies.map(_.target.identity) shouldBe Vector("CAPTURE_PAYMENT", "Check Payment Capability")
        workflow.invocationPolicies.map(_.actionIdentity) shouldBe Vector("capture-payment", "check-payment")
        workflow.version shouldBe " opaque version "
      }
      "rejects unresolved or mismatched Action, SPI and Participant bindings" in {
        Given("unknown/provider-like targets, wrong canonical identities, operations and source correlations")
        val changes = Vector((_policy ++ Vector("target", "identity")) -> Json.fromString("provider-order"),
          (_policy :+ "actionIdentity") -> Json.fromString("CAPTURE_PAYMENT"),
          (_policy ++ Vector("target", "identity")) -> Json.fromString("check-payment"),
          (_workflow ++ Vector("invocationPolicies", "1", "target", "identity")) -> Json.fromString("provider-order"),
          (_workflow ++ Vector("invocationPolicies", "1", "actionIdentity")) -> Json.fromString("capture-payment"),
          (_spi :+ "actionIdentity") -> Json.fromString("unknown"),
          (_spi ++ Vector("operation", "name")) -> Json.fromString("other"),
          (_spi ++ Vector("operation", "source", "line")) -> Json.fromInt(62),
          (_action ++ Vector("operation", "source", "line")) -> Json.fromInt(62))
        When("the mismatched correlations are parsed")
        val results = changes.map { case (path, value) => parseC(_set(_json, path, value).noSpaces, _label) }
        Then("all failures are stable target mismatches with workflow context")
        results.foreach { result =>
          _diagnostic(result).code shouldBe DiagnosticCode.TargetMismatch
          _diagnostic(result).data("workflow") shouldBe _expected.identity
        }
      }
      "rejects alias duplicates and multiple policies for one resolved Action" in {
        Given("duplicate state, Action, capability, policy identities and duplicate resolved Actions")
        val duplicates = Vector((_workflow ++ Vector("states", "1", "name")) -> Json.fromString("PENDING"),
          (_workflow ++ Vector("actions", "1", "identity")) -> Json.fromString("CAPTURE_PAYMENT"),
          (_workflow ++ Vector("requiredSpi", "1", "capability")) -> Json.fromString("Capture Payment Capability"),
          (_workflow ++ Vector("invocationPolicies", "1", "identity")) -> Json.fromString("CAPTURE_CONTROL"))
        val second = _workflow ++ Vector("invocationPolicies", "1")
        val repeated = _set(_set(_json, second ++ Vector("target", "identity"), Json.fromString("capture-payment-capability")),
          second :+ "actionIdentity", Json.fromString("capture-payment"))
        When("the repeated identities and resolved bindings are parsed")
        val results = duplicates.map { case (path, value) => parseC(_set(_json, path, value).noSpaces, _label) } :+
          parseC(repeated.noSpaces, _label)
        Then("all duplicates fail under the same lower-case/alphanumeric comparison")
        results.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.DuplicateIdentity)
      }
      "admits JUDGMENT and retains non-targeted ADMISSION while rejecting targeted ADMISSION" in {
        Given("Judgment, targeted Admission and a mixed untargeted Admission Action")
        val judgment = _set(_json, _action :+ "kind", Json.fromString("JUDGMENT"))
        val admission = _set(_json, _action :+ "kind", Json.fromString("ADMISSION"))
        val extra = _at(_json, _action).mapObject(_.add("identity", Json.fromString("local-admission"))
          .add("kind", Json.fromString("ADMISSION")))
        val mixed = _change(_json, _workflow :+ "actions")(json => Json.fromValues(json.asArray.get :+ extra))
        When("these Action kinds are received")
        val judged = parseC(judgment.noSpaces, _label)
        val rejected = parseC(admission.noSpaces, _label)
        val retained = parseC(mixed.noSpaces, _label)
        Then("only eligible targeted Actions acquire invocation metadata")
        _take(judged).workflows.head.actions.head.kind shouldBe ActionKind.Judgment
        _diagnostic(rejected).code shouldBe DiagnosticCode.IneligibleAction
        _take(retained).workflows.head.actions.last.kind shouldBe ActionKind.Admission
        _take(retained).workflows.head.invocationPolicies shouldBe _expected.invocationPolicies
      }
    }
    "readmit immutable facts and transport diagnostics" which {
      "rejects divergent copied facts and malformed changed raw JSON" in {
        Given("an admitted artifact copied with divergent schema, hash, Workflow and policy values")
        val original = _take(parseC(_raw, _label))
        val changedpolicy = _expected.invocationPolicies.head.copy(executionTimeoutMillis = Some(1L))
        val variants = Vector(original.copy(schemaVersion = legacySchemaVersion), original.copy(sha256 = "changed"),
          original.copy(workflows = Vector(_expected.copy(version = "changed"))),
          original.copy(workflows = Vector(_expected.copy(invocationPolicies = changedpolicy +: _expected.invocationPolicies.tail))))
        When("copied values are readmitted using their original bytes")
        val results = variants.map(admitC)
        val malformed = admitC(original.copy(rawJson = "{"))
        Then("facts diverging from reparse fail and malformed bytes retain their parse diagnostic")
        results.foreach { result =>
          _diagnostic(result) shouldBe Diagnostic(DiagnosticCode.DefinitionMismatch,
            Map("path" -> "artifact", "reason" -> "facts-diverge"))
        }
        _diagnostic(malformed).code shouldBe DiagnosticCode.InvalidJson
      }
      "preserves exact whitespace hashes and explicit caller labels across repeated admission" in {
        Given("original bytes, harmless trailing whitespace and a caller-changed label")
        val original = _take(parseC(_raw, _label))
        val raw = _raw + " \n"
        When("the bytes are parsed and equal artifacts repeatedly readmitted")
        val spaced = _take(parseC(raw, _label))
        val relabeled = admitC(original.copy(sourceResource = " arbitrary label "))
        val repeated = Vector.fill(3)(admitC(original))
        val divergent = admitC(original.copy(rawJson = raw))
        Then("raw bytes determine the hash while a label is preserved without authentication")
        spaced.rawJson shouldBe raw
        spaced.sha256 should not be original.sha256
        spaced.workflows shouldBe original.workflows
        divergent shouldBe a[Consequence.Failure[?]]
        _take(relabeled).sourceResource shouldBe " arbitrary label "
        repeated shouldBe Vector.fill(3)(Consequence.success(original))
      }
      "fails null inputs safely and exposes stable typed deterministic diagnostics" in {
        Given("null artifact/JSON/resource and blank resource labels")
        val resources = Vector(null, "", " ")
        When("invalid inputs cross the public entry points")
        val failures = Vector(admitC(null), parseC(null, _label)) ++ resources.map(label => parseC(_raw, label))
        val blank = parseC(" ", _label)
        val repeated = parseC(" ", _label)
        Then("each failure propagates through Consequence with stable code and sorted context")
        failures.foreach(result => _diagnostic(result).code shouldBe DiagnosticCode.InvalidValue)
        val diagnostic = _diagnostic(blank)
        diagnostic shouldBe Diagnostic(DiagnosticCode.InvalidJson, Map("path" -> "root", "reason" -> "invalid-json"))
        diagnostic.render shouldBe "CWF-INVOCATION-POLICY-ABI-001 path=root reason=invalid-json"
        _diagnostic(repeated).render shouldBe diagnostic.render
        Diagnostic(DiagnosticCode.TargetMismatch, Map("z" -> "last", "a" -> "first")).render shouldBe
          "CWF-INVOCATION-POLICY-ABI-006 a=first z=last"
      }
    }
  }

  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def _take[A](result: Consequence[A]): A = {
    result match {
      case Consequence.Success(value) => value
      case failure => fail(s"expected admission: $failure")
    }
  }
  private def _diagnostic(result: Consequence[Artifact]): Diagnostic = {
    result match {
      case Consequence.Failure(conclusion) => conclusion.getException match {
        case Some(exception: WorkflowInvocationPolicyAbiException) => exception.diagnostic
        case other => fail(s"expected typed diagnostic: $other")
      }
      case other => fail(s"expected rejection: $other")
    }
  }
  private def _at(json: Json, path: Vector[String]): Json = {
    path.foldLeft(json)((value, key) => if (value.isArray) value.asArray.get(key.toInt) else value.asObject.get(key).get)
  }
  private def _change(json: Json, path: Vector[String])(change: Json => Json): Json = {
    if (path.isEmpty) change(json)
    else if (json.isArray) Json.fromValues(json.asArray.get.zipWithIndex.map { case (value, index) =>
      if (index == path.head.toInt) _change(value, path.tail)(change) else value
    })
    else json.mapObject(obj => obj.add(path.head, _change(obj(path.head).get, path.tail)(change)))
  }
  private def _set(json: Json, path: Vector[String], value: Json): Json = {
    _change(json, path)(_ => value)
  }
  private def _token(path: Vector[String], token: String): String = {
    _set(_json, path, Json.fromString("WIRE_INTEGER")).noSpaces.replace("\"WIRE_INTEGER\"", token)
  }
  private def _numbers(attempts: Int, delay: Long, timeout: Long): String = {
    _set(_set(_set(_json, _policy ++ Vector("retry", "maximumAttempts"), Json.fromInt(attempts)),
      _policy ++ Vector("retry", "fixedRetryDelayMillis"), Json.fromLong(delay)),
      _policy :+ "executionTimeoutMillis", Json.fromLong(timeout)).noSpaces
  }
  private def _source(source: Producer.SourceIdentity): SourceIdentity = { SourceIdentity(source.line) }
  private def _operation(operation: Producer.Operation): Operation = {
    Operation(operation.service, operation.name, operation.inputType, operation.resultType, _source(operation.source))
  }
  private def _expected: Workflow = {
    val first = Operation("OrderService", "capturePayment", Some("PaymentCommand"), Some("PaymentResult"), SourceIdentity(Some(61)))
    val second = first.copy(source = SourceIdentity(Some(67)))
    Workflow("InvocationControlledWorkflow", "workflow-invocation-policy-v2",
      WorkflowSourceCorrelation(SourceIdentity(Some(25)), SourceIdentity(Some(27))),
      Vector(State("Pending", SourceIdentity(Some(43))), State("Complete", SourceIdentity(Some(45)))),
      Vector(Action("capture-payment", ActionKind.Operation, first, Some("payment.subject"), first.source),
        Action("check-payment", ActionKind.Operation, second, Some("payment.subject"), second.source)),
      Vector(RequiredSpi("capture-payment-capability", "capture-payment", first, SourceIdentity(Some(75))),
        RequiredSpi("check-payment-capability", "check-payment", second, SourceIdentity(Some(79)))),
      Vector(InvocationPolicy("capture-control", InvocationTarget.Action("capture-payment"), "capture-payment",
        Some(RetryPolicy(3, 250L)), Some(30000L), SourceIdentity(Some(85))),
        InvocationPolicy("check-control", InvocationTarget.Participant("check-payment-capability"), "check-payment",
          None, Some(Long.MaxValue), SourceIdentity(Some(92)))))
  }
}
