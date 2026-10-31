package org.goldenport.cncf.display

import logicalui.DisplayMutationServiceDomain.BodyMode
import org.goldenport.cncf.datastore.{DataStore, PostgresqlAcceptance}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlDisplayMutationServiceListAcceptanceSpec extends AnyWordSpec
    with DisplayMutationServiceListPropertySpec.Laws
    with DisplayMutationServiceListIngressSpec.AdapterVocabulary with PostgresqlAcceptance {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceListFixture.*
  private val _governed = afterWord("under docs/spec/display-mutation-service.md C4C-01..08, docs/spec/postgresql-phase-96-persistence.md PG-E12, docs/design/display-mutation-service.md C4C-D01, Phase96 P96-07C4C and RULE.md")

  "Native canonical List lifecycle" should {
    "required PostgreSQL correspondence" which _governed {
      "LST-PG1 execute complete managed Entity Aggregate root and alias lifecycles and actual HTTP with independent native observations" in {
        with_postgresql { database =>
          Given("the mandatory real PostgreSQL 17.11 runtime with native independent catalog readers and durable server settings")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          for (target <- Target.values.toVector; representation <- representations; supplied <- Vector(false, true);
            mode <- BodyMode.values.toVector; logical <- Vector(false, true)) {
            Given(s"normal ComponentFactory/SystemNode-managed PostgreSQL $target/$representation supplied=$supplied body=$mode logical=$logical")
            val session = openList(target, representation, "postgresql", supplied, mode, Some(database))
            try {
              val before = session.prepare(true)
              val base = session.baseSession
              val rootname = DataStore.CollectionId.EntityStore(base.component.collectionId).collectionName
              val table = before.state.physical.toVector.find(_._1._2 == rootname).get._2
              table.columns.map(_._1) shouldBe lifecycleRootColumns(representation)
              table.columns.find(_._1 == representation.storageFieldName).map(_._2) shouldBe Some("int8")
              table.rows.map(_("id")).toSet shouldBe (session.witnesses.map(_.identity.value).toSet + base.component.unrelatedId.value)
              before.state.rows.selected shouldBe None
              before.state.rows.body shouldBe None
              val changes = Vector(field("title", DisplayValue.Text("Native updated 日本 Ω")),
                field("description", DisplayValue.Text("\tNative λ\r\n")), field("number", DisplayValue.Number(BigDecimal("-777.00100"))),
                field("flag", DisplayValue.Boolean(true)), field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2000-01-02T03:04:05.987654321Z"))),
                field("nullable", DisplayValue.Missing))
              When("the ordinary native create Detail List update no-op stale and observed delete sequence executes")
              val entry = createAndList(session)
              updateDeleteSequence(session, entry, changes, logical)
              Then("every full independent catalog row NULL body unrelated witness resident and View oracle holds and fresh List hides the deletion")
              queryAndCheck(session, session.entries(None))
            } finally session.close()
          }
          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"the actual native $target/$representation HTTP routes with a server-supplied identity")
            val session = openList(target, representation, "postgresql", database = Some(database))
            try {
              When("actual Http4s GET POST GET selected Detail PUT GET DELETE GET crosses ordinary native operations")
              httpSequence(session)
              Then("the final native HTTP List retains exact surviving fields and owner/provenance correspondence")
              httpList(session, session.entries(None))
            } finally session.close()
          }
        }
      }

      "LST-PG2 preserve native owner factory domain stale duplicate and committed pending/read-failure state without replay" in {
        with_postgresql { database =>
          Given("mandatory native PostgreSQL version 170011 with fsync and synchronous_commit enabled")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"the normally managed native $target/$representation with separate owner/foreign witnesses and complete independent state")
            val session = openList(target, representation, "postgresql", database = Some(database))
            try {
              When("ordinary native requests traverse each frozen rejection gate then committed pending and failing-read boundaries")
              rejectionSequence(session)
              pendingSequence(session)
              Then("the independent full native logical/body/resident/View oracles and final List correspond without replay")
              queryAndCheck(session, session.entries(None))
            } finally session.close()
          }
        }
      }

      "LST-PG-P1 require one hundred total shrinking complete native List lifecycle sequences and close every exact owned witness" in {
        with_postgresql { database =>
          Given("the mandatory PostgreSQL 17.11 database and a finite owned session pool with at most four live managed runtimes")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          val pool = new Pool("postgresql", Some(database))
          When("the separate native complete sequence law runs every total generated mode with meaningful value and mode shrinking")
          val result = try lifecycleLaw(pool, "LST-PG-P1") finally pool.close()
          Then("the actual native property result has at least one hundred successes one worker zero discards and no live owned session")
          withClue(result.status.toString) { result.passed shouldBe true }
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          pool.liveSessionCount shouldBe 0
        }
      }
    }
  }
}
