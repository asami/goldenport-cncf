package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayPresentationContextScenarioSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PresentationContextRuntimeFixture.{Provider, authenticated, emptyWire, flow, instant, property, success, wire, withCandidate}
  import DisplayMutationServiceFixture.{Session, Execution, LifecycleSetup, Reload, json, field}

  private def _execute(session: Session, provider: Provider, request: Request): (Json, Execution) = {
    val active = session.execution()
    val ctx = authenticated(active.context).withScope(ScopeContext.withPresentationContextResolver(active.context.scope, provider))
    json(session.subsystem.executeOperationResponseInContext(request, ctx)) -> active
  }
  private def _context(json: Json): PresentationContext =
    success(PresentationContextCodec.decodeJsonC(json.hcursor.downField("presentationContext").focus.get))
  private def _continuation(json: Json): String = success(PresentationContextCodec.encodeC(_context(json)))
  private def _check_context(context: PresentationContext): Unit = {
    context.locale shouldBe Some("en-US")
    context.variant shouldBe Some("server-variant")
    context.flow.get.displayInstance shouldBe "server-display"
    context.flow.get.correlation shouldBe "server-correlation"
    context.flow.get.expiresAt shouldBe instant.plusSeconds(600)
  }
  private def _check_callbacks(session: Session): Unit = {
    val callbacks = session.log.events.filter(event => event.event == "find" || event.event == "query")
    callbacks should not be empty
    callbacks.foreach { event =>
      event.context.presentationContext should not be empty
      _check_context(PresentationContextRuntime.project(event.context).get)
    }
  }

  "Opt-in Display editing flow" should afterWord(
    "in spec:display-presentation-context, example:BS1..BS6, rules:R5,R6,R7,R8, phase:96") {
    "ordinary read mutation and acknowledgement continuity" which {
      "BS1 carry accepted context through registered Detail List Updated and Unchanged callbacks without replay" in {
        Given("R5,R8: a normally bootstrapped lifecycle component with registered Detail and List Browsers")
        val session = DisplayMutationServiceFixture.open(lifecycle = Some(LifecycleSetup(withList = true)))
        val provider = new Provider
        try {
          session.log.reset()
          When("ordinary read negotiates support and List explicitly continues the returned opaque references")
          val read = _execute(session, provider, withCandidate(session.readRequest, emptyWire))._1
          val list = _execute(session, provider,
            withCandidate(DisplayMutationServiceListFixture.queryRequest(session), _continuation(read)))._1
          Then("both real registered View callbacks see accepted state and both Displays carry safe server projection")
          _check_context(_context(read))
          _check_context(_context(list))
          _check_callbacks(session)
          provider.requests should have size 2

          Given("the returned List context and one title change at authoritative revision one")
          val changes = Vector(field("title", DisplayValue.Text("Continued title 日本")))
          session.log.reset()
          When("ordinary Display update commits and performs its one contextual reload")
          val (updated, execution) = _execute(session, provider,
            withCandidate(session.request(changes), _continuation(list)))
          Then("the committed receipt revision two and Ready Display share safe flow with one write and one reload")
          updated.hcursor.get[String]("outcome") shouldBe Right("updated")
          updated.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          updated.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
          updated.hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
          _check_context(_context(updated))
          _check_context(_context(updated.hcursor.downField("projection").downField("display").focus.get))
          execution.uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
          session.log.events.count(_.event == "find") shouldBe 1
          execution.lifecycleObservers.map(_.updates.size).sum shouldBe 1
          _check_callbacks(session)

          Given("the same committed title and revision two with explicit continuation from the acknowledgement")
          session.log.reset()
          When("the ordinary update applies an unchanged semantic patch")
          val unchanged = _execute(session, provider, withCandidate(session.request(changes, observed = 2L), _continuation(updated)))._1
          Then("Unchanged retains revision two and the same safe flow without a second logical mutation")
          unchanged.hcursor.get[String]("outcome") shouldBe Right("unchanged")
          unchanged.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          _check_context(_context(unchanged))
          provider.requests should have size 4
        } finally session.close()
      }
      "BS2 retain pending and delete acknowledgement context after commit with no command replay" in {
        Given("R8: a committed update whose contextual projection will fail")
        val session = DisplayMutationServiceFixture.open(lifecycle = Some(LifecycleSetup()))
        val provider = new Provider
        try {
          session.log.reset()
          session.log.reload = Reload.Failure
          When("the ordinary update commits once and its single reload fails")
          val (pending, execution) = _execute(session, provider, withCandidate(session.request(
            Vector(field("title", DisplayValue.Text("Committed pending")))), emptyWire))
          Then("Pending still acknowledges safe root context and authoritative revision without retry or replay")
          pending.hcursor.downField("projection").get[String]("type") shouldBe Right("pending")
          pending.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          pending.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
          pending.hcursor.downField("projection").downField("display").focus shouldBe None
          _check_context(_context(pending))
          execution.uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed)
          execution.lifecycleObservers.map(_.updates.size).sum shouldBe 1
          provider.requests should have size 1

          Given("the committed revision two and safe acknowledgement context for an ordinary delete")
          session.log.reset()
          When("the normal delete commits at the observed prior revision")
          val (deleted, deletion) = _execute(session, provider, withCandidate(
            session.lifecycleRequest(DisplayMutation.Operation.Delete, observed = 2L), _continuation(pending)))
          Then("Deleted retains the same safe root flow with no Display or implicit Detail reload")
          deleted.hcursor.get[String]("outcome") shouldBe Right("deleted")
          deleted.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          deleted.hcursor.downField("projection").get[String]("type") shouldBe Right("reload-list")
          deleted.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
          _check_context(_context(deleted))
          session.log.events.count(_.event == "find") shouldBe 0
          deletion.lifecycleObservers.map(_.deletes.size).sum shouldBe 1
          provider.requests should have size 2
        } finally session.close()
      }
      "BS3 attach creation acknowledgement and its one Ready reload to the same safe flow" in {
        Given("R8: a normal lifecycle component with the selected root durably absent")
        val session = DisplayMutationServiceFixture.open(lifecycle = Some(LifecycleSetup()))
        val provider = new Provider
        try {
          session.prepareLifecycle(true)
          session.log.reset()
          When("ordinary Display create receives explicit support negotiation and commits")
          val (created, execution) = _execute(session, provider,
            withCandidate(session.lifecycleRequest(DisplayMutation.Operation.Create), emptyWire))
          Then("Created retains INITIAL revision one and matching root and nested safe context after one native creation")
          created.hcursor.get[String]("outcome") shouldBe Right("created")
          created.hcursor.downField("resource").get[Long]("revision") shouldBe Right(1L)
          created.hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
          _check_context(_context(created))
          _check_context(_context(created.hcursor.downField("projection").downField("display").focus.get))
          execution.lifecycleObservers.map(_.creates.size).sum shouldBe 1
          session.log.events.count(_.event == "find") shouldBe 1
          provider.requests should have size 1
          _check_callbacks(session)
        } finally session.close()
      }
    }
    "direct Business and optional-state continuity" which {
      "BS4 keep the original direct Business response and continue the flow only through explicit Display reload" in {
        Given("R5,R7,R8: an ordinary Business workflow with independently registered Display read")
        val session = DisplayMutationServiceFixture.open(businessSetup = Some(DisplayBusinessReviewFixture.Setup()))
        val provider = new Provider
        try {
          val read = _execute(session, provider, withCandidate(session.readRequest))._1
          session.log.reset()
          When("the direct business.requestReview Operation executes with explicit returned continuation")
          val (business, execution) = _execute(session, provider,
            withCandidate(DisplayBusinessReviewFixture.request(session), _continuation(read)))
          Then("the direct response retains its own three business members and performs no Display reload or proxy call")
          business.asObject.get.keys.toSet shouldBe Set("outcome", "resourceId", "revision")
          business.hcursor.get[String]("outcome") shouldBe Right("review-requested")
          business.hcursor.get[Long]("revision") shouldBe Right(2L)
          session.log.events.count(_.event == "business") shouldBe 1
          session.log.events.count(_.event == "find") shouldBe 0
          session.log.events.find(_.event == "business").get.context.presentationContext should not be empty
          execution.attempts shouldBe 1

          Given("the direct Business result and the retained client-safe context from the original Display")
          When("the client explicitly reloads Display through its ordinary read Operation")
          val reloaded = _execute(session, provider, withCandidate(session.readRequest, _continuation(read)))._1
          Then("the refreshed Detail has revision two and the same opaque server flow")
          _check_context(_context(reloaded))
          reloaded.hcursor.downField("model").downField("resource").get[Long]("revision") shouldBe Right(2L)
          provider.requests should have size 3
          _check_callbacks(session)
        } finally session.close()
      }
      "BS5 execute ordinary read and Business paths when flow state is unknown expired or absent after restart" in {
        Given("R5,R8: ordinary routes and optional server state unavailable expired or missing")
        val fixture = PresentationContextRuntimeFixture.open(declared = false)
        val session = DisplayMutationServiceFixture.open()
        val provider = new Provider
        try {
          fixture.provider.answer = (_, _) => Consequence.success(PresentationContextResolver.Result.Unavailable)
          provider.answer = (request, _) => Consequence.success(PresentationContextResolver.Result.Supported(success(
            PresentationContextRuntime.Resolution.createC(request.binding, flow = Some(flow(expiresAt = request.now))))))
          When("unknown ordinary Business expired Display and provider-free restart Display calls execute normally")
          val ordinary = fixture.subsystem.executeOperationResponseInContext(withCandidate(fixture.request, wire()), fixture.context)
          val expired = _execute(session, provider, withCandidate(session.readRequest, wire()))._1
          val active = session.execution()
          val restarted = json(session.subsystem.executeOperationResponseInContext(withCandidate(session.readRequest, wire()), authenticated(active.context)))
          Then("optional context is omitted while every ordinary path still completes")
          ordinary shouldBe Consequence.success(OperationResponse.Scalar("ordinary"))
          fixture.evidence.business.head.presentationContext shouldBe None
          expired.hcursor.downField("presentationContext").focus shouldBe None
          restarted.hcursor.downField("presentationContext").focus shouldBe None
          provider.requests should have size 1

          Given("a freshly accepted flow retained by its real registered View callback and a rebound server session")
          val acceptedprovider = new Provider
          val accepted = _execute(session, acceptedprovider, withCandidate(session.readRequest))._1
          val callbackcontext = session.log.events.filter(_.event == "find").last.context
          val rebound = authenticated(callbackcontext, sessionId = "different-session")
          When("the real registered Display registry projects under that rebound session without a new provider lookup")
          val isolated = success(session.subsystem._with_managed_datastore_lease_c { _ =>
            session.component.displayRuntime.get.readC(DisplayMutationServiceFixture.screenId, session.component.id)(using rebound)
          })
          Then("ordinary View reading remains available but the inherited flow cannot project into the other session")
          _check_context(_context(accepted))
          isolated.presentationContext shouldBe None
          acceptedprovider.requests should have size 1
        } finally { fixture.close(); session.close() }
      }
    }
    "pure acknowledgement compatibility" which {
      "BS6 roundtrip optional root context enforce Ready correspondence and preserve old absent bytes and exact trusted objects" in {
        Given("R4,R8: a supplied canonical receipt and independently admitted exact Ready Display")
        val fixture = logicalui.DisplayMutationContractFixture
        val binding = success(fixture.bindingC())
        val request = fixture.update(binding, Vector(DisplayMutation.FieldInput(fixture.title, DisplayValue.Text("changed"))))
        val receipt = success(DisplayMutationResult.receiptC(binding.selection, request, DisplayMutationResult.OutcomeKind.Updated,
          fixture.resource(revision = 8), Some(fixture.resource().revision)))
        val snapshot = fixture.reload()
        val original = success(DisplayMutationResult.createC(receipt, Some(DisplayMutationResult.ProjectionState.Ready(snapshot))))
        val before = success(DisplayMutationCodec.encodeResultC(original))
        val context = success(PresentationContext.createC(Some("en-US"), Some("server-variant"), Some(flow())))
        When("attachment copies the inert acknowledgement and the strict result codec roundtrips it")
        val attached = success(DisplayMutationResult.withPresentationContextC(original, Some(context)))
        val encoded = success(DisplayMutationCodec.encodeResultC(attached))
        val decoded = success(DisplayMutationCodec.decodeResultC(binding.selection, fixture.definitions, request,
          Some(fixture.resource().revision), encoded))
        val removed = success(DisplayMutationResult.withPresentationContextC(attached, None))
        Then("root and nested values match semantically and receipt definitions model and old absent encoding are preserved")
        _check_context(decoded.presentationContext.get)
        (attached.receipt eq receipt) shouldBe true
        attached.projection.get match {
          case DisplayMutationResult.ProjectionState.Ready(value) =>
            (value.display.definition eq snapshot.display.definition) shouldBe true
            (value.display.model eq snapshot.display.model) shouldBe true
            value.display.presentationContext.get shouldBe context
          case _ => fail("Ready required")
        }
        success(DisplayMutationCodec.encodeResultC(removed)) shouldBe before
        success(DisplayMutationCodec.encodeResultC(original)) shouldBe before
        encoded.indexOf("\"retryCommand\":false,\"presentationContext\"") should be >= 0
        decoded.retryCommand shouldBe false
        decoded.reloadRequired shouldBe false

        Given("a root context differing from the nested Ready context and nullable API containers")
        val different = success(PresentationContext.createC(Some("ja-JP"), Some("server-variant"), Some(flow())))
        val jsonvalue = io.circe.parser.parse(encoded).toOption.get
        val mismatch = jsonvalue.mapObject(_.add("presentationContext", success(PresentationContextCodec.encodeJsonC(different))))
        When("pure result construction and wire decode encounter mismatch or null context")
        val invalid = Vector(DisplayMutationResult.createC(receipt, original.projection, Some(context)),
          DisplayMutationResult.createC(receipt, original.projection, null),
          DisplayMutationResult.createC(receipt, original.projection, Some(null)),
          DisplayMutationCodec.decodeResultC(binding.selection, fixture.definitions, request, Some(fixture.resource().revision), mismatch.noSpaces),
          DisplayMutationCodec.decodeResultC(binding.selection, fixture.definitions, request, Some(fixture.resource().revision),
            jsonvalue.mapObject(_.add("presentationContext", Json.Null)).noSpaces))
        Then("every malformed correspondence is structured failure without any reload or command execution")
        invalid.foreach(_.isSuccess shouldBe false)
        Given("the earlier Slice A supplied nested value with no root context")
        When("the pure factory admits that older inert supplied-value representation")
        val inert = DisplayMutationResult.createC(receipt, attached.projection)
        Then("root absence retains the earlier nested-value compatibility contract")
        success(inert).presentationContext shouldBe None
      }
    }
  }
}
