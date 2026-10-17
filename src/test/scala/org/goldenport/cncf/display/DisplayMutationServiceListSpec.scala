package org.goldenport.cncf.display

import logicalui.DisplayMutationServiceDomain.BodyMode
import org.goldenport.cncf.directive.Query
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayMutationServiceListSpec extends AnyWordSpec with DisplayMutationServiceListFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceListFixture.*
  private val _governed = afterWord("under docs/spec/display-mutation-service.md C4C-01..08, docs/design/display-mutation-service.md C4C-D01, Phase96 P96-07C4C and RULE.md")

  "Canonical mutable resource List" should {
    "dynamic membership and complete lifecycle" which _governed {
      "LST-E1 search arbitrary owner witnesses and genuinely empty owner scopes through the selected provider" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations) {
          Given(s"a normal $backend $target/$representation with two separately typed witnesses outside the selected/unrelated pair")
          val session = openList(target, representation, backend)
          try {
            val before = session.prepare(false, 7L)
            val base = session.baseSession
            val selected = Entry(base.component.id, 7L, retainedValues("Before"))
            val entries = session.entries(Some(selected))
            val witness = session.witnesses.head
            Set(base.component.id, base.component.unrelatedId) should not contain witness.identity
            When("ordinary List queries as owner and a principal with no owned rows")
            val listed = base.execute(queryRequest(base))
            val empty = base.execute(queryRequest(base), base.execution(principal = "no-owned-rows"))
            Then("only independently declared owner rows appear with exact fields revisions and provenance and the empty scope has no items")
            checkSearch(listed, session, entries)
            checkSearch(empty, session, Vector.empty, "no-owned-rows")
            checkLifecycleState(base, before.state)
            checkWitnesses(session, before.witnesses)

            Given("a trusted semantic identity predicate naming the arbitrary third owner identity before query execution")
            val query = Query.plan(Record.empty, where = Query.Eq("id", witness.identity.value))
            val active = base.execution()
            val expectedvalues = retainedValues(witness.title)
            val expectedroot = expectedWitnessRoot(before.witnesses.head, representation)
            When("the registered contextual Browser interprets the semantic predicate through canonical durable search")
            val found = semanticQuery(base, query, active).TAKE
            Then("the actual witness is returned with its complete hydrated body and real managed carrier, without fixed two-ID enumeration")
            found.map(_.id) shouldBe Vector(witness.identity)
            found.head.record.fields shouldBe expectedroot.record.fields
            found.head.record.getString("content") shouldBe Some(body)
            found.head.record.getString("business") shouldBe Some("witness business")
            found.head.record.getString("audit") shouldBe Some("witness audit")
            found.head.record.getString("owner_id") shouldBe Some("owner")
            found.head.record.getAny(representation.storageFieldName).map(_.toString) shouldBe Some("17")
            val row = logicalui.DisplayMutationServiceDomain.row(root =>
              org.goldenport.Consequence.success(DisplayModel.Resource(root.id, revision(witness.version))))
            val projected = DisplayProjection.listC(definitions(base), listScreenId, "ess.reference", row, Vector.empty).TAKE
              .projectC(found, None).TAKE
            projected.model shouldBe expectedModel(base, Vector(Entry(witness.identity, 17L, expectedvalues)))
            active.lifecycleObservers.flatMap(_.searches) should have size 1
            active.lifecycleObservers.flatMap(_.updates) shouldBe Vector.empty
            active.lifecycleObservers.map(_.writes).sum shouldBe 0
            checkLifecycleState(base, before.state)
            checkWitnesses(session, before.witnesses)
          } finally session.close()
        }
      }

      "LST-E2 expose actual supplied and generated creates in a fresh exact List after one ready contextual Detail" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations;
          supplied <- Vector(false, true); mode <- BodyMode.values.toVector) {
          Given(s"a normally managed $backend $target/$representation with supplied=$supplied and independently selected body=$mode")
          val session = openList(target, representation, backend, supplied, mode)
          try {
            When("the complete create-to-Detail-to-explicit-List scenario is interpreted")
            val entry = createAndList(session, if (supplied) lifecycleFields else lifecycleFields.reverse)
            Then("the predeclared INITIAL identity and every typed value remain available without deriving expectations from query output")
            entry.version shouldBe 1L
            entry.identity shouldBe session.baseSession.component.id
          } finally session.close()
        }
      }

      "LST-E3 carry List selection through changed update current no-op stale equality and observed physical or logical deletion" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations; logical <- Vector(false, true)) {
          Given(s"a managed $backend $target/$representation, full independent witness baseline and logical=$logical deletion model")
          val session = openList(target, representation, backend)
          try {
            val selected = createAndList(session)
            val changes = Vector(field("title", DisplayValue.Text("Updated 日本 Ω")), field("description", DisplayValue.Text("\tλ\n")),
              field("number", DisplayValue.Number(BigDecimal("-9876.5000"))), field("flag", DisplayValue.Boolean(true)),
              field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2020-02-03T04:05:06.987654321Z"))),
              field("nullable", DisplayValue.Text("restored nullable")))
            When("the selected resource traverses the declared complete update/no-op/stale/delete sequence")
            updateDeleteSequence(session, selected, changes, logical)
            Then("the final explicit List excludes the selected root and preserves unrelated and witness fields exactly")
            queryAndCheck(session, session.entries(None))
          } finally session.close()
        }
      }

      "LST-E4 enforce foreign owner visibility and preserve full state at factory domain stale and duplicate rejection" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations) {
          Given(s"a normally authorized $backend $target/$representation with owner and foreign-owner witnesses")
          val session = openList(target, representation, backend)
          try {
            When("foreign owner search and every frozen rejection gate interpret ordinary requests")
            rejectionSequence(session)
            Then("only the separately seeded foreign witness is visible to its owner and all rejected requests preserve canonical state")
            queryAndCheck(session, session.entries(None, "foreign"), "foreign")
          } finally session.close()
        }
      }

      "LST-E5 retain committed create update and delete when later Detail or explicit List projection fails without replay" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations) {
          Given(s"the actual $backend $target/$representation provider and independently declared committed state with postcommit read faults")
          val session = openList(target, representation, backend)
          try {
            When("committed pending create/update and committed delete precede failing then successful explicit queries")
            pendingSequence(session)
            Then("the final List exposes the committed deletion without a second command or provider mutation")
            queryAndCheck(session, session.entries(None))
          } finally session.close()
        }
      }
    }
  }
}
