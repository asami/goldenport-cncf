package org.goldenport.cncf.display

import io.circe.Json
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityRevisionTransport
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.handler.ingress.ArgsIngress
import org.http4s.Method
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object DisplayMutationServiceListIngressSpec {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceListFixture.*
  import DisplayMutation.Operation

  trait AdapterVocabulary extends DisplayMutationServiceListFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkHttp(response: logicalui.DisplayServiceReadFixture.HttpResult): Unit = {
      withClue(response.text) { response.status shouldBe 200 }
      response.contentType shouldBe Some("application/json")
      response.json.hcursor.downField("execution").get[String]("interface-shape") shouldBe Right("json")
      new String(response.bytes.toArray, StandardCharsets.UTF_8) shouldBe response.text
      response.data.isObject shouldBe true
    }
    def httpList(session: ListSession, entries: Vector[Entry]): Json = {
      Given("the independently fixed complete typed List and actual managed HTTP owner/context")
      val base = session.baseSession
      val before = session.baseline()
      val wanted = expectedReadObservation(before.state)
      When("actual Http4s GET dispatches ordinary display.query with capture-list")
      val response = base.http(Array.emptyByteArray, Vector.empty, Method.GET, "query", selectedScreenId = listScreenId)
      Then("the UTF8 JSON envelope carries exact typed values order identities revisions and source/provenance without mutation")
      checkHttp(response)
      checkListJson(response.data, base, entries)
      val context = base.log.queries.last._2
      context.security.principal.id.value shouldBe "owner"
      context.clock.instant() shouldBe fixedInstant
      context.observability shouldBe observability
      val authorization = base.log.authorizations.filter(pair => pair._2 eq context.unitOfWork).map(_._1)
      authorization.map(_.accessKind) shouldBe Vector("search/list")
      authorization.map(_.targetComponentName) shouldBe Vector(Some(base.component.name))
      base.lifecycleObservers.flatMap(_.searches).last._1 shouldBe
        org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(base.component.collectionId)
      checkLifecycleState(base, wanted)
      checkWitnesses(session, before.witnesses)
      response.data
    }
    def httpSequence(session: ListSession): Unit = {
      val base = session.baseSession
      Given("the server-supplied identity and full pre-action create/root/body/native/resident/View and witness oracle")
      base.component.lifecycleSetup.get.supplied shouldBe true
      val before = session.prepare(true)
      val wanted = expectedLifecycleCreate(base, before.state, lifecycleFields)
      val created = createdEntry(base.component, lifecycleFields)
      val createdentries = session.entries(Some(created))
      httpList(session, session.entries(None))
      base.log.reset()
      base.lifecycleObservers.foreach(_.reset())
      When("actual Http4s POST carries strict raw create JSON without If-Match to the ordinary installed operation")
      val response = base.http(base.lifecyclePayload(Operation.Create).getBytes(StandardCharsets.UTF_8), Vector.empty,
        Method.POST, "create")
      Then("INITIAL Created has one contextual Detail and exactly one actual managed provider create")
      checkHttp(response)
      checkLifecycleJson(response.data, base, true, 1L)
      checkDetailJson(response.data.hcursor.downField("projection").downField("display").focus.get, base, created)
      base.log.idCalls shouldBe Vector.empty
      base.log.events.count(_.event == "find") shouldBe 1
      base.lifecycleObservers.flatMap(_.creates) should have size 1
      base.lifecycleObservers.map(_.writes).sum shouldBe 0
      checkLifecycleState(base, wanted)
      checkWitnesses(session, before.witnesses)
      httpList(session, createdentries)

      Given("the predeclared created selected identity and managed revision exposed by List")
      val changes = Vector(field("title", DisplayValue.Text("HTTP updated 日本 Ω")),
        field("description", DisplayValue.Text("\tHTTP λ\r\n")), field("number", DisplayValue.Number(BigDecimal("-9.00100"))),
        field("flag", DisplayValue.Boolean(true)), field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2001-02-03T04:05:06.987654321Z"))),
        field("nullable", DisplayValue.Text("HTTP present")))
      val updatebefore = session.baseline()
      val updatewanted = expectedUpdate(base, updatebefore.state, changes, true, 2L)
      val updatedentry = updatedEntry(created, changes, 2L)
      When("actual Http4s GET requests that selected Detail")
      val detail = base.http(Array.emptyByteArray, Vector.empty, Method.GET, "read",
        extra = Vector("resourceId" -> created.identity.value))
      Then("the selected Detail retains every typed field and observed managed revision")
      checkHttp(detail)
      checkDetailJson(detail.data, base, created)
      base.log.reset()
      base.lifecycleObservers.foreach(_.reset())
      When("actual Http4s PUT carries the changed fields and a single strong If-Match observation")
      val updated = base.http(base.payload(changes).getBytes(StandardCharsets.UTF_8),
        Vector("If-Match" -> EntityRevisionTransport.entityTag(revision(1L))), Method.PUT, "update")
      Then("Updated retains revision two and one contextual Detail with independently expected full state")
      checkHttp(updated)
      updated.data.hcursor.get[String]("outcome") shouldBe Right("updated")
      updated.data.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
      updated.data.hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
      updated.data.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
      checkDetailJson(updated.data.hcursor.downField("projection").downField("display").focus.get, base, updatedentry)
      base.lifecycleObservers.flatMap(_.updates) should have size 1
      checkLifecycleState(base, updatewanted)
      checkWitnesses(session, updatebefore.witnesses)
      httpList(session, session.entries(Some(updatedentry)))

      Given("the current revision-two selected root and independent full post-delete and List oracles")
      val deletebefore = session.baseline()
      val deletewanted = expectedLifecycleDelete(base, deletebefore.state, 2L, true)
      val entries = session.entries(None)
      base.log.reset()
      base.lifecycleObservers.foreach(_.reset())
      When("actual Http4s DELETE carries the matched strong If-Match observation")
      val deleted = base.http(base.lifecyclePayload(Operation.Delete).getBytes(StandardCharsets.UTF_8),
        Vector("If-Match" -> EntityRevisionTransport.entityTag(revision(2L))), Method.DELETE, "delete")
      Then("Deleted is committed once with an inert reload-list and no automatic query or deleted Detail")
      checkHttp(deleted)
      checkLifecycleJson(deleted.data, base, false, 2L)
      base.log.queries shouldBe Vector.empty
      base.log.events.count(_.event == "find") shouldBe 0
      base.lifecycleObservers.flatMap(_.deletes) should have size 1
      base.lifecycleObservers.map(_.writes).sum shouldBe 0
      checkLifecycleState(base, deletewanted)
      checkWitnesses(session, deletebefore.witnesses)
      httpList(session, entries)
    }

    def cliAndAdmission(session: ListSession): Unit = {
      Given("a canonical mutation already committed and an independent exact List model for HTTP CLI and semantic consumers")
      val base = session.baseSession
      val created = createAndList(session)
      val expected = session.entries(Some(created))
      val args = Vector("display", "query", "--resourceType=" + resourceType, "--screenId=" + listScreenId)
      val request = ArgsIngress().encode(base.component.protocol.services, args.toArray).TAKE.copy(component = Some(base.component.name))
      When("actual ArgsIngress interprets the unchanged ordinary query selectors")
      val result = base.execute(request)
      Then("the ordinary CLI query matches the complete independently declared typed List")
      checkSearch(result, session, expected)
      val http = httpList(session, expected)
      json(result.result) shouldBe http

      Given("a trusted semantic consumer and the same query predicate with no public filter or paging controls")
      val active = base.execution()
      val query = org.goldenport.cncf.directive.Query.fromRecord(org.goldenport.record.Record.empty)
      When("the semantic client calls the registered Browser with its normal active owner context")
      val semantic = semanticQuery(base, query, active).TAKE
      Then("original domain Roots carry the exact List identities titles and real revisions independently of wire decoding")
      semantic.map(_.id) shouldBe expected.map(_.identity)
      semantic.map(_.record.getString("title").get) shouldBe expected.map(_.values.head.asInstanceOf[DisplayValue.Text].value)
      semantic.map(_.record.getAny(base.component.representation.storageFieldName).get.toString) shouldBe expected.map(_.version.toString)

      val wrong = Vector(
        request.copy(properties = request.properties.map(property => if (property.name == "resourceType") property.copy(value = "foreign") else property)),
        request.copy(properties = request.properties.map(property => if (property.name == "screenId") property.copy(value = "undeclared") else property)),
        request.copy(properties = request.properties.map(property => if (property.name == "screenId") property.copy(value = screenId) else property)),
        request.copy(operation = "read")) ++ Vector("where", "sort", "limit", "offset", "includeTotal", "sourceId", "producerId", "target", "callback")
          .map(name => request.copy(properties = request.properties :+ Property(name, "injected", None)))
      wrong.foreach { rejectedrequest =>
        Given("the exact full baseline and no Browser/provider events before an inadmissible ordinary query")
        val before = session.baseline()
        base.log.reset()
        When("ordinary query admission receives a wrong resource screen branch or extra/source-control selector")
        val rejected = base.execute(rejectedrequest)
        Then("structured rejection occurs before Browser provider search mutation or derived-state effects")
        rejected.result shouldBe a[Consequence.Failure[?]]
        base.log.queries shouldBe Vector.empty
        rejected.execution.lifecycleObservers.flatMap(_.searches) shouldBe Vector.empty
        rejected.execution.lifecycleObservers.flatMap(_.creates) shouldBe Vector.empty
        rejected.execution.lifecycleObservers.flatMap(_.updates) shouldBe Vector.empty
        rejected.execution.lifecycleObservers.flatMap(_.deletes) shouldBe Vector.empty
        checkLifecycleState(base, before.state)
        checkWitnesses(session, before.witnesses)
      }
      for ((screen, extra) <- Vector("undeclared" -> Vector.empty[(String, String)], screenId -> Vector.empty[(String, String)],
        listScreenId -> Vector("sourceId" -> "foreign"), listScreenId -> Vector("resourceType" -> "foreign"))) {
        Given("the unchanged complete state before actual HTTP query selector rejection")
        val before = session.baseline()
        base.log.reset()
        When("actual Http4s GET carries a wrong query screen or injected source selector")
        val response = base.http(Array.emptyByteArray, Vector.empty, Method.GET, "query", extra = extra, selectedScreenId = screen)
        Then("normal HTTP rejection reaches no registered Browser or provider search")
        response.status should be >= 400
        base.log.queries shouldBe Vector.empty
        checkLifecycleState(base, before.state)
        checkWitnesses(session, before.witnesses)
      }
    }
  }
}

final class DisplayMutationServiceListIngressSpec extends AnyWordSpec with DisplayMutationServiceListIngressSpec.AdapterVocabulary {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceListFixture.*
  private val _governed = afterWord("under docs/spec/display-mutation-service.md C4C-06..08, docs/design/display-mutation-service.md C4C-D01, Phase96 P96-07C4C and RULE.md")
  "Canonical List lifecycle ingress" should {
    "real HTTP and ordinary CLI" which _governed {
      "LST-H1 carry GET List POST create selected Detail PUT update and DELETE through actual Http4s on SQLite" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"a normally managed SQLite $target/$representation with a server-supplied identity and real owner/context")
          val session = openList(target, representation, "sqlite")
          try {
            When("the actual ordinary HTTP lifecycle and explicit List queries execute")
            httpSequence(session)
            Then("the final HTTP List has the complete unchanged unrelated and witness records")
            httpList(session, session.entries(None))
          } finally session.close()
        }
      }
      "LST-H2 agree across actual ArgsIngress HTTP and semantic List and reject unsupported selectors before effects" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"the ordinary SQLite $target/$representation routes and immutable resource/screen declarations")
          val session = openList(target, representation, "sqlite")
          try {
            When("CLI semantic and HTTP consumers use the same post-mutation source and admission boundaries")
            cliAndAdmission(session)
            Then("an explicit canonical query still returns its independently declared created resource and witnesses")
            queryAndCheck(session, session.entries(Some(createdEntry(session.baseSession.component, lifecycleFields))))
          } finally session.close()
        }
      }
    }
  }
}
