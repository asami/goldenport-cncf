package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.cncf.workflow.WorkflowTerminalClosureContinuation.{Intent, Target}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/** Managed runtime examples use the real canonical SQLite host and guard owner.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  */
final class WorkflowTerminalClosureContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private val _e1 = afterWord("in spec:workflow-terminal-closure-continuation, example:E1, rules:R1,R2, phase:terminal-closure")
  private val _e2 = afterWord("in spec:workflow-terminal-closure-continuation, example:E2, rules:R2,R3,R4, phase:terminal-closure")
  private val _e3 = afterWord("in spec:workflow-terminal-closure-continuation, example:E3, rules:R1,R3,R4, phase:terminal-closure")
  private val _e4 = afterWord("in spec:workflow-terminal-closure-continuation, example:E4, rules:R3,R4, phase:terminal-closure")
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"terminal closure fixture rejected: $failure")
  }
  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val _producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
  private val _definition = _take(bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
    _resource("/workflow/candidate-admission-workflow-abi.json").trim, _producer,
    "modeler/candidate-admission-workflow.cml"))))
  private val _catalog = _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_definition)))
  private val _component = ComponentId("org.goldenport.cncf.test.TerminalClosure")
  private val _operation = StateMachineOperationIdentity("workflow", "continueClosure")
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "terminalclosure", name))
  private val _instances = _collection("instances")
  private val _checkpoints = _collection("checkpoints")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("terminal-closure")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("terminal-closure")))
  private val _codec = new PayloadCodec[String] {
    val typeIdentity = "closure.request.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("expected closure text")
  }
  private val _policy = _take(EventPersistencePolicy.createC("terminal-closure-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private val _declaration = _take(EventStorePersistenceDeclaration.createC("events",
    _collection("cursor"), _collection("events"), _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(_component, "workflow", _instances, _checkpoints))))
  private def _path(): Path = Files.createTempDirectory(
    Files.createDirectories(Path.of("target/workflow-terminal-closure-spec").toAbsolutePath), "episode-").resolve("host.sqlite")
  private def _host(path: Path) = _take(SubsystemEventStoreBootstrap.createC(_policy, _declaration,
    ScopeContext(ScopeKind.Subsystem, "terminal-closure", None, context.observability),
    ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)))
  private def _bind(host: SubsystemEventStoreBootstrap, codec: PayloadCodec[String] = _codec) =
    _take(WorkflowManagedTerminalClosureContinuation.bindC(host.subsystem, _component, _config, _operation,
      Vector(_definition), _instances, _checkpoints, codec))
  private def _root(host: SubsystemEventStoreBootstrap) = _take(WorkflowInstancePersistenceBinding.bindC(
    _component, host.subsystem, ComponentDataStore.Environment(context.runtime.resolvedParameters,
      Some(host.subsystem.configuration)),
    WorkflowInstancePersistenceBinding.Declaration(_config, Vector(_definition), _instances)))
  private def _initial(suffix: String = "one") = InstanceRecord(InstanceIdentity(s"instance-$suffix"), _definition,
    initialRevision, Lifecycle.NotStarted, None, CorrelationReference(s"session-$suffix"), CausationReference("start"),
    Some(DerivedCompositeOccurrenceReference("original-composite")),
    Some(CommittedEntityTransitionReference("original-predecessor")), None, Vector.empty)
  private def _entry(current: InstanceRecord, lifecycle: Lifecycle, cause: String) = HistoryEntry(
    HistorySequence(current.history.size.toLong + 1L), current.revision.next, lifecycle,
    if (lifecycle == Lifecycle.Active) Some(ProgressionReference("Pending")) else None,
    current.correlation, CausationReference(cause), current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def _closed(suffix: String = "one"): InstanceRecord = {
    val initial = _initial(suffix)
    val active = _take(initial.appendC(initial.revision, _entry(initial, Lifecycle.Active, "active")))
    _take(active.appendC(active.revision, _entry(active, Lifecycle.Completed, "original-terminal")))
  }
  private def _seed(host: SubsystemEventStoreBootstrap): InstanceRecord = {
    _take(host.initializeCursorC())
    _seed_instance(host, "one")
  }
  private def _seed_instance(host: SubsystemEventStoreBootstrap, suffix: String): InstanceRecord = {
    val persistence = _root(host)
    val initial = _initial(suffix)
    _take(persistence.createC(initial))
    val active = _take(persistence.appendC(initial.identity, initial.revision, _entry(initial, Lifecycle.Active, "active")))
    _take(persistence.appendC(active.identity, active.revision, _entry(active, Lifecycle.Completed, "original-terminal")))
  }
  private def _intent(target: Target, identity: String = "closure-one") = Intent(target, Reference(identity, "1"),
    ProgressionReference("Pending"), CausationReference("closure-admission"), TypedValue(_codec.typeIdentity, "selected closure purpose"))
  private def _released(host: SubsystemEventStoreBootstrap): Unit = {
    host.subsystem.systemNode.managedDataStoreCount shouldBe 1
    host.subsystem.systemNode.activeLeaseCount shouldBe 0
  }

  "Dedicated terminal closure continuation" should {
    "E1 retain old JSON and immutable terminal provenance without weakening ordinary append" must _e1 {
      "for generated native identities and exact terminal facts" in {
        Given("admitted definition references and generated completed histories without terminal continuation fields")
        val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { suffix =>
          val source = _closed(suffix)
          val oldbytes = _take(WorkflowInstanceRecordJsonV1.encodeC(source))
          val target = _take(WorkflowTerminalClosureContinuation.targetC(_component, source))
          val receipt = _take(WorkflowTerminalClosureContinuation.admitC(_component, _operation, source, _intent(target)))
          val marker = receipt.next.history.last.terminalClosure.get
          val bytes = _take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next))
          !oldbytes.contains("terminalClosure") &&
            WorkflowInstanceRecordJsonV1.decodeC(oldbytes, _catalog).toOption.contains(source) &&
            receipt.next.history.init == source.history && marker.terminalCausation == source.causation &&
            marker.terminalRevision == source.revision && marker.terminalSequence == source.history.last.sequence &&
            receipt.next.correlation == source.correlation &&
            receipt.next.committedPredecessor == source.committedPredecessor &&
            receipt.next.derivedCompositeOccurrence == source.derivedCompositeOccurrence &&
            WorkflowInstanceRecordJsonV1.decodeC(bytes, _catalog).toOption.contains(receipt.next) &&
            !source.appendC(source.revision, receipt.next.history.last).isSuccess &&
            !source.appendC(source.revision, receipt.next.history.last.copy(terminalClosure = None)).isSuccess &&
            !WorkflowMechanicalTransition.admitC(_component, _operation, WorkflowMechanicalTransition.Intent(
              _config, receipt.reference, source, source.revision, receipt.next.history.last, receipt.payload)).isSuccess
        }
        When("the dedicated admission, legacy decoding and ordinary transition gates consume those histories")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)
        Then("only dedicated admission can append the typed original terminal, and every original entry survives")
        checked.passed shouldBe true
      }
      "when a copied marker or current projection changes the original terminal" in {
        Given("a dedicated successor and its complete source history")
        val source = _closed()
        val receipt = _take(WorkflowTerminalClosureContinuation.admitC(_component, _operation, source,
          _intent(_take(WorkflowTerminalClosureContinuation.targetC(_component, source)))))
        val marker = receipt.next.history.last.terminalClosure.get
        val changed = receipt.next.copy(history = receipt.next.history.updated(source.history.size,
          receipt.next.history.last.copy(terminalClosure = Some(marker.copy(terminalCausation = CausationReference("invented"))))))
        val bytes = _take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next))
        val json = _take(parse(bytes).fold(e => Consequence.stateConflict(e.message), Consequence.success))
        val changedcurrent = json.mapObject(_.add("current", json.hcursor.downField("current").focus.get
          .mapObject(_.remove("terminalClosure")))).noSpaces
        When("passive history and current projection are validated")
        val invalidhistory = changed.validateC
        val invalidprojection = WorkflowInstanceRecordJsonV1.decodeC(changedcurrent, _catalog)
        Then("neither mismatched provenance nor a dropped current marker is admitted")
        invalidhistory shouldBe a[Consequence.Failure[?]]
        invalidprojection shouldBe a[Consequence.Failure[?]]
      }
    }

    "E2 reopen one managed continuation and collect a lost result after later ordinary progress" must _e2 {
      "through the captured SQLite host and an independently reopened reader" in {
        Given("an actual completed native instance and one managed same-store closure binding")
        val path = _path()
        val first = _host(path)
        val source = try _seed(first) catch { case e: Throwable => _take(first.shutdownC()); throw e }
        val writer = _bind(first)
        val intent = _intent(_take(writer.inspectC(source.identity)))
        val receipt = try {
          When("the exact target is continued and an identical request is repeated")
          val completed = _take(writer.executeC(intent))
          val repeated = _take(writer.executeC(intent))
          Then("one native append and one retained checkpoint preserve the original terminal")
          completed.receipt.next.history.init shouldBe source.history
          completed.receipt.next.lifecycle shouldBe Lifecycle.Active
          repeated.receipt shouldBe completed.receipt
          _take(_root(first).loadC(source.identity)).get.history should have size 3
          writer.executeC(intent.copy(payload = TypedValue(_codec.typeIdentity, "different purpose"))) shouldBe a[Consequence.Failure[?]]
          _released(first)
          completed.receipt
        } finally _take(first.shutdownC())
        writer.inspectC(source.identity) shouldBe a[Consequence.Failure[?]]
        val reopened = _host(path)
        try {
          Given("the caller lost its delivery result and the native instance subsequently progressed normally")
          val reader = _bind(reopened)
          val persistence = _root(reopened)
          val progressed = _take(persistence.appendC(source.identity, receipt.next.revision,
            _entry(receipt.next, Lifecycle.Active, "later-progress")))
          When("the ordinary public retained query and exact duplicate request collect the original receipt")
          val retained = _take(reader.loadRetainedC(intent.reference, source.identity)).get
          val duplicate = _take(reader.executeC(intent))
          Then("the actual current frontier is returned without replacing history or replaying continuation")
          retained.receipt shouldBe receipt
          retained.current shouldBe progressed
          duplicate.current shouldBe progressed
          _take(persistence.loadC(source.identity)).get.history should have size 4
          reader.executeC(intent.copy(reference = Reference("new-after-progress", "1"))) shouldBe a[Consequence.Failure[?]]
          reader.loadRetainedC(intent.reference, InstanceIdentity("foreign-instance")) shouldBe a[Consequence.Failure[?]]
          _released(reopened)
        } finally _take(reopened.shutdownC())
      }
    }

    "E3 refuse foreign, stale and nonterminal targets without native or checkpoint changes" must _e3 {
      "when any native frontier or captured authority differs" in {
        Given("a real completed instance, its exact target and the existing host grant")
        val host = _host(_path())
        try {
          val source = _seed(host)
          val writer = _bind(host)
          val target = _take(writer.inspectC(source.identity))
          val intent = _intent(target)
          val targets = Vector(
            target.copy(component = ComponentId("org.goldenport.cncf.test.Foreign")),
            target.copy(instance = InstanceIdentity("foreign-instance")),
            target.copy(definitionIdentity = WorkflowDefinitionIdentity("foreign-definition")),
            target.copy(definitionRevision = WorkflowDefinitionRevision("foreign-revision")),
            target.copy(correlation = CorrelationReference("foreign-session")),
            target.copy(terminalSequence = HistorySequence(1L)),
            target.copy(terminalRevision = InstanceRevision(1L)),
            target.copy(terminalCausation = CausationReference("invented-terminal")),
            target.copy(entityRevision = _take(EntityRevision.createC(1L))))
          When("foreign binding, changed native targets and an unknown execution progression are offered")
          WorkflowManagedTerminalClosureContinuation.bindC(host.subsystem, ComponentId("org.goldenport.cncf.test.Foreign"),
            _config, _operation, Vector(_definition), _instances, _checkpoints, _codec) shouldBe a[Consequence.Failure[?]]
          targets.foreach(changed => writer.executeC(intent.copy(target = changed)) shouldBe a[Consequence.Failure[?]])
          writer.executeC(intent.copy(progression = ProgressionReference("not-in-definition"))) shouldBe a[Consequence.Failure[?]]
          val initial = _initial("not-started")
          _take(_root(host).createC(initial))
          writer.inspectC(initial.identity) shouldBe a[Consequence.Failure[?]]
          val active = _take(_root(host).appendC(initial.identity, initial.revision, _entry(initial, Lifecycle.Active, "active")))
          writer.inspectC(active.identity) shouldBe a[Consequence.Failure[?]]
          Then("the original native frontier and absent checkpoint are unchanged and leases are released")
          _take(_root(host).loadC(source.identity)) shouldBe Some(source)
          _take(writer.loadRetainedC(intent.reference, source.identity)) shouldBe None
          _released(host)
        } finally _take(host.shutdownC())
      }
      "when a competing native instance consumes the unique checkpoint before publication" in {
        Given("independent managed hosts, two terminal instances and a shared exact checkpoint reference")
        val path = _path()
        val first = _host(path)
        val second = _host(path)
        try {
          val source = _seed(first)
          // Both hosts share the first-installed cursor; adding another native
          // instance must not try to initialize or reset that physical cursor.
          val other = _seed_instance(second, "other")
          val competing = _bind(second)
          val competingintent = _intent(_take(competing.inspectC(other.identity)))
          var competed = false
          val racedcodec = new PayloadCodec[String] {
            val typeIdentity = _codec.typeIdentity
            def encode(value: String): Json = {
              if (!competed) {
                competed = true
                _take(competing.executeC(competingintent))
              }
              _codec.encode(value)
            }
            def decode(value: Json): Either[String, String] = _codec.decode(value)
          }
          val writer = _bind(first, racedcodec)
          val intent = _intent(_take(writer.inspectC(source.identity)))
          When("normal competing publication consumes Create after the first native root has been read")
          val refused = writer.executeC(intent)
          Then("checkpoint Create refusal rolls back the otherwise eligible first root CAS")
          competed shouldBe true
          refused shouldBe a[Consequence.Failure[?]]
          _take(_root(first).loadC(source.identity)) shouldBe Some(source)
          _take(_root(second).loadC(other.identity)).get.history should have size 3
          _take(competing.loadRetainedC(competingintent.reference, other.identity)).get.receipt.current shouldBe other
          _bind(first).loadRetainedC(intent.reference, source.identity) shouldBe a[Consequence.Failure[?]]
          _released(first)
          _released(second)
        } finally {
          _take(first.shutdownC())
          _take(second.shutdownC())
        }
      }
      "when two independent managed hosts race different references against one terminal revision" in {
        Given("two normal hosts of the same physical SQLite store and targets inspected before either commit")
        val path = _path()
        val first = _host(path)
        val second = _host(path)
        val pool = Executors.newFixedThreadPool(2)
        try {
          val source = _seed(first)
          val ready = new CountDownLatch(2)
          val encodes = new AtomicInteger(0)
          val racedcodec = new PayloadCodec[String] {
            val typeIdentity = _codec.typeIdentity
            def encode(value: String): Json = {
              if (encodes.incrementAndGet() <= 2) {
                ready.countDown()
                if (!ready.await(20L, TimeUnit.SECONDS)) throw new IllegalStateException("competing managed preparation did not arrive")
              }
              _codec.encode(value)
            }
            def decode(value: Json): Either[String, String] = _codec.decode(value)
          }
          val left = _bind(first, racedcodec)
          val right = _bind(second, racedcodec)
          val leftintent = _intent(_take(left.inspectC(source.identity)), "race-left")
          val rightintent = _intent(_take(right.inspectC(source.identity)), "race-right")
          When("both actual managed preparations reach serialization before native guarded publication")
          val leftfuture = pool.submit(new Callable[Boolean] { def call(): Boolean = left.executeC(leftintent).isSuccess })
          val rightfuture = pool.submit(new Callable[Boolean] { def call(): Boolean = right.executeC(rightintent).isSuccess })
          val outcomes = Vector(leftfuture.get(30L, TimeUnit.SECONDS), rightfuture.get(30L, TimeUnit.SECONDS))
          Then("exactly one root CAS and its matching checkpoint Create survive the actual writer race")
          outcomes.count(identity) shouldBe 1
          _take(_root(first).loadC(source.identity)).get.history should have size 3
          val retained = Vector(_take(_bind(first).loadRetainedC(leftintent.reference, source.identity)),
            _take(_bind(first).loadRetainedC(rightintent.reference, source.identity)))
          retained.count(_.nonEmpty) shouldBe 1
          _released(first)
          _released(second)
        } finally {
          pool.shutdownNow()
          _take(first.shutdownC())
          _take(second.shutdownC())
        }
      }
    }

    "E4 separate preparation refusal from postcommit UNKNOWN and recover through retained inspection" must _e4 {
      "when receipt encoding fails before the managed transaction" in {
        Given("a completed native root and a payload codec whose encoder fails")
        val host = _host(_path())
        try {
          val source = _seed(host)
          val broken = new PayloadCodec[String] {
            val typeIdentity = _codec.typeIdentity
            def encode(value: String): Json = throw new IllegalStateException("preparation encoding failed")
            def decode(value: Json): Either[String, String] = _codec.decode(value)
          }
          val writer = _bind(host, broken)
          val intent = _intent(_take(writer.inspectC(source.identity)))
          When("the ordinary managed entry prepares the complete retained receipt")
          val refused = writer.executeC(intent)
          Then("no native append or checkpoint is published")
          refused shouldBe a[Consequence.Failure[?]]
          _take(_root(host).loadC(source.identity)) shouldBe Some(source)
          _take(_bind(host).loadRetainedC(intent.reference, source.identity)) shouldBe None
          _released(host)
        } finally _take(host.shutdownC())
      }
      "when authoritative receipt decoding fails after native guarded publication" in {
        Given("a normal encoder and a failing readback decoder on the actual managed path")
        val path = _path()
        val host = _host(path)
        val source = try _seed(host) catch { case e: Throwable => _take(host.shutdownC()); throw e }
        val broken = new PayloadCodec[String] {
          val typeIdentity = _codec.typeIdentity
          def encode(value: String): Json = _codec.encode(value)
          def decode(value: Json): Either[String, String] = throw new IllegalStateException("readback lost")
        }
        val writer = _bind(host, broken)
        val intent = _intent(_take(writer.inspectC(source.identity)))
        try {
          When("the same-store root and checkpoint commit but response projection fails")
          val unknown = writer.executeC(intent)
          Then("the result stays a failure with no invented successful response or rollback assertion")
          unknown match {
            case Consequence.Failure(conclusion) =>
              conclusion.observation.cause.descriptor.facets.collect {
                case org.goldenport.observation.Descriptor.Facet.Reason(value) => value
              } should contain("transaction-indeterminate")
            case _ => fail("lost authoritative readback must remain transaction-indeterminate")
          }
          _take(_root(host).loadC(source.identity)).get.history should have size 3
          _released(host)
        } finally _take(host.shutdownC())
        Given("an independent normal reader after physical host reopen")
        val reopened = _host(path)
        try {
          When("the exact instance and checkpoint reference are collected through public retained inspection")
          val reader = _bind(reopened)
          val retained = _take(reader.loadRetainedC(intent.reference, source.identity)).get
          val repeated = _take(reader.executeC(intent))
          Then("authoritative retained data settles the native outcome without an additional continuation effect")
          retained.receipt.current shouldBe source
          retained.receipt.next.history.init shouldBe source.history
          repeated.receipt shouldBe retained.receipt
          _take(_root(reopened).loadC(source.identity)).get.history should have size 3
          _released(reopened)
        } finally _take(reopened.shutdownC())
      }
    }
  }
}
