package org.goldenport.cncf.workflow

import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Initial allocation association, not public Start/advance/result acceptance.
  * @since   Oct. 10, 2026
  * @version Oct. 10, 2026
  */
final class WorkflowInstanceSessionPersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = _context(None)
  private val _collection = DataStore.CollectionId("workflow_session_instances")
  private val _configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("session-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("session-store")))

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }

  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }

  private def _definition: DefinitionBinding = {
    val producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
    _take(bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
      _resource("/workflow/candidate-admission-workflow-abi.json"), producer,
      "modeler/candidate-admission-workflow.cml"))))
  }

  private def _initial(identity: String, definition: DefinitionBinding): InstanceRecord =
    InstanceRecord(InstanceIdentity(identity), definition, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("business-correlation"), CausationReference("start"),
      None, None, None, Vector.empty)

  private def _active: HistoryEntry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("review.Awaiting")), CorrelationReference("business-correlation"),
    CausationReference("start"), None, None, None)

  private def _context(session: Option[String]): ExecutionContext = {
    val base = ExecutionContext.create()
    session.fold(base)(value => _take(ExecutionContext.withRoutingSessionIdentity(base, value)))
  }

  private def _provider(store: DataStore, definition: DefinitionBinding, session: Option[String]):
    WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _configuration, Vector(definition), _collection)(
      using _context(session)))

  "Workflow session association" should {
    "attach explicit routing inside an existing invocation without replacing its live UnitOfWork" in {
      Given("actual execution contexts with their live UnitOfWork and explicit specification-owned provider, local or unadmitted origins")
      for (origin <- Vector("provider", "local", "unadmitted")) {
      val context = ExecutionContext.create(SecurityContext.Privilege.User)
      origin match {
        case "provider" => SecurityAuthenticationProvenance.providerAuthenticated(context.security)
        case "local" => SecurityAuthenticationProvenance.trustedLocalAuthenticated(context.security)
        case _ => ()
      }
      val originalsubject = SecuritySubject.from(context.security)
      val activework = context.runtime.unitOfWork
      When("the public context API carries only an opaque invocation session")
      val routed = _take(ExecutionContext.withRoutingSessionIdentity(context, "opaque-session"))
      Then("effects stay in the same live UnitOfWork and actual actor, capability and origin survive without minting authentication")
      routed.runtime.unitOfWork should be theSameInstanceAs activework
      routed.security.principal shouldBe context.security.principal
      routed.security.capabilities shouldBe context.security.capabilities
      val routedsubject = SecuritySubject.from(routed.security)
      routedsubject.isProviderAuthenticated shouldBe originalsubject.isProviderAuthenticated
      routedsubject.isTrustedLocalAuthenticated shouldBe originalsubject.isTrustedLocalAuthenticated
      routed.security.session.flatMap(_.sessionId) shouldBe Some("opaque-session")
      context.security.session.flatMap(_.sessionId) shouldBe None
      }
    }

    "retain exact routing in the initial row without changing correlation or legacy layout" in {
      Given("three allocations under two actual sessions and one legacy invocation")
      val store = DataStore.inMemorySearchable()
      val definition = _definition
      val session = " session/日本語\n "
      val record = _initial("associated", definition)
      _take(_provider(store, definition, Some(session)).create(_configuration, record))
      _take(_provider(store, definition, Some("other")).create(_configuration, _initial("other", definition)))
      _take(_provider(store, definition, None).create(_configuration, _initial("legacy", definition)))
      When("a fresh invocation resolves the exact opaque session, not its trimmed form")
      val reader = _provider(store, definition, None)
      val selected = _take(reader.findBySessionC(session, definition.workflowIdentity))
      Then("only its allocation is returned, still NotStarted and not presented as successful Start")
      selected.map(_.identity) shouldBe Vector(record.identity)
      selected.head.lifecycle shouldBe Lifecycle.NotStarted
      selected.head.correlation shouldBe record.correlation
      _take(reader.findBySessionC(session.trim, definition.workflowIdentity)) shouldBe Vector.empty
      _take(reader.findBySessionC(session, WorkflowDefinitionIdentity("unrelated"))) shouldBe Vector.empty
      val legacy = _take(store.load(_collection, DataStore.StringEntryId("legacy"))).get
      legacy.getString(WorkflowInstanceDataStorePersistence._session_field) shouldBe None
    }

    "return all candidates without latest selection and leave association unchanged after conflicts" in {
      Given("two instances allocated to one session and an attempted duplicate from another")
      val store = DataStore.inMemorySearchable()
      val definition = _definition
      val writer = _provider(store, definition, Some("same-session"))
      val first = _initial("first", definition)
      _take(writer.create(_configuration, first))
      _take(writer.create(_configuration, _initial("second", definition)))
      When("activation consumes revision zero and both duplicate allocation and stale append fail")
      _take(writer.append(_configuration, first.identity, initialRevision, _active))
      writer.append(_configuration, first.identity, initialRevision, _active).toOption shouldBe None
      _provider(store, definition, Some("foreign-session")).create(_configuration, first).toOption shouldBe None
      Then("both real candidates remain and no failed command can rebind the first instance")
      val reader = _provider(store, definition, None)
      val records = _take(reader.findBySessionC("same-session", definition.workflowIdentity))
      records.map(_.identity.value).toSet shouldBe Set("first", "second")
      records.find(_.identity == first.identity).get.history shouldBe Vector(_active)
      _take(reader.findBySessionC("foreign-session", definition.workflowIdentity)) shouldBe Vector.empty
    }

    "reopen actual SQLite session associations with current state after closing the physical provider" in {
      Given("a file-backed native allocation and activation under actual invocation session context")
      val root = Files.createDirectories(Paths.get("target/workflow-session-persistence"))
      val path = Files.createTempDirectory(root, "reopen-").resolve("workflow.sqlite")
      val definition = _definition
      val record = _initial("durable", definition)
      val first = SqlDataStore.sqlite(path.toString)
      try {
        val writer = _provider(first, definition, Some("durable-session"))
        _take(writer.create(_configuration, record))
        _take(writer.append(_configuration, record.identity, initialRevision, _active))
      } finally _take(first.closeC())
      When("a different invocation and physical provider reopen the database")
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val records = _take(_provider(reopened, _definition, None).findBySessionC("durable-session", definition.workflowIdentity))
        Then("the association resolves the actual active history without caller revision or content proof")
        records.map(_.identity) shouldBe Vector(record.identity)
        records.head.history shouldBe Vector(_active)
        records.head.revision shouldBe InstanceRevision(1L)
      } finally _take(reopened.closeC())
    }
  }
}
