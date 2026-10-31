package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, PostgresqlAcceptance}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlDisplayMutationServiceUpdateAcceptanceSpec extends AnyWordSpec
    with DisplayMutationServiceFixture.Vocabulary with PostgresqlAcceptance {
  import DisplayMutationServiceFixture.*
  private val _changes = Vector(field("title", DisplayValue.Text("Native committed 日本 Ω\ud800")), field("description", DisplayValue.Text("")),
    field("number", DisplayValue.Number(BigDecimal("12345678901234567890.0012300"))), field("flag", DisplayValue.Boolean(false)),
    field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2026-10-15T02:03:04.987654321Z"))), field("nullable", DisplayValue.Missing))

  private def _check_native_baseline(session: Session, tables: Map[(String, String), StoredTable]): Unit = {
    When("the independent native snapshot identifies the selected root collection and overflow body table")
    val roots = tables.toVector.filter(_._1._2 == DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName)
    val bodies = tables.toVector.filter(_._1._2 == "cncf_content_body_overflow")
    val rootids = Vector(DataStore.EntryId(session.component.id).print, DataStore.EntryId(session.component.unrelatedId).print).sorted
    val bodyids = Vector(session.component.id.value + ":content", session.component.unrelatedId.value + ":content").sorted
    Then("exactly one native root table contains both ordered fixture rows and exactly one overflow table preserves both complete bodies")
    roots should have size 1
    roots.head._2.rows.map(_("id")) shouldBe rootids
    bodies should have size 1
    val selectedbodies = bodies.head._2.rows.filter(row => bodyids.contains(row("id")))
    selectedbodies.map(_("id")) shouldBe bodyids
    selectedbodies.foreach { row =>
      row("content") shouldBe body
      row("field") shouldBe "content"
    }
  }

  "Native ordinary Display update" should {
    "managed PostgreSQL correspondence" which {
      "C3-PG1 execute registered Entity, Aggregate and alias on both representations with actual commit and contextual Detail" in {
        with_postgresql { database =>
          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"PostgreSQL 17.11 with ordinary managed ComponentDataStore, $target/$representation and independent native reader")
            database.scalar("SHOW server_version_num") shouldBe "170011"
            database.scalar("SHOW fsync") shouldBe "on"
            database.scalar("SHOW synchronous_commit") shouldBe "on"
            val session = open(target, representation, Some(database))
            try {
              val before = session.rows()
              val tables = database.snapshotAll()
              _check_native_baseline(session, tables)
              val wanted = session.expected(before, tables, _changes, true, 2L)
              When("Detail then descriptor precede the ordinary selected update and real enclosing UnitOfWork commit")
              val detail = session.read()
              val descriptor = session.descriptor()
              session.log.reset()
              val attempt = session.execute(session.request(_changes))
              val after = session.rows()
              val physical = database.snapshotAll()
              Then("the native complete logical and physical tables match the independent oracle and the actual registered Browser returns Ready")
              json(detail.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
              json(descriptor.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-mutation-descriptor.v1")
              checkResult(attempt, session, "updated", 2L)
              checkRows(after, wanted.rows)
              physical shouldBe wanted.physical
              checkCompleteState(session, wanted)
              session.log.events.count(_.event == "find") shouldBe 1
              session.resident.unrelated shouldBe defined
              session.resident.memoryunrelated shouldBe session.resident.unrelated
              attempt.execution.space.observers should not be empty
            } finally session.close()
            When("a later managed lease is requested after ordinary owned Subsystem/SystemNode shutdown")
            val later = session.subsystem.systemNode.bind().acquireLeaseC()
            Then("the closed node rejects the later lease")
            later.toOption shouldBe None
          }
        }
      }

      "C3-PG2 preserve complete native physical, logical, resident and View state for no-op, stale and denied updates" in {
        with_postgresql { database =>
          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"a canonical native $target/$representation with a current title and all root/body/audit/security/default state")
            val session = open(target, representation, Some(database))
            try {
              val before = session.rows(); val tables = database.snapshotAll()
              _check_native_baseline(session, tables)
              val equal = Vector(field("title", DisplayValue.Text("Before")))
              val wanted = session.expected(before, tables, equal, false, 1L)
              When("the ordinary current equal command commits without a changed publication")
              val noop = session.execute(session.request(equal))
              val unchanged = session.rows(); val unchangedtables = database.snapshotAll()
              Then("NoOp retains the authoritative revision/audit and all complete native tables")
              checkResult(noop, session, "unchanged", 1L)
              checkRows(unchanged, before)
              unchangedtables shouldBe tables
              checkCompleteState(session, wanted)
              val resident = session.resident
              for (mode <- Vector("stale-equal", "stale-changed", "factory-denied", "domain-denied")) {
                Given(s"the same complete independent native baseline and current $mode boundary")
                session.log.reset()
                session.log.denyFactory = mode == "factory-denied"
                val selected = if (mode == "stale-equal") equal else if (mode == "domain-denied") Vector(field("title", DisplayValue.Text(" "))) else _changes
                When("ordinary operation execution applies authoritative validation or authorization before native publication")
                val attempt = session.execute(session.request(selected, if (mode.startsWith("stale")) 2L else 1L))
                val after = session.rows(); val aftertables = database.snapshotAll()
                Then("the original structured failure has no writer replay or contextual reload and all complete state is preserved")
                attempt.result shouldBe a[Consequence.Failure[?]]
                if (mode == "factory-denied") (conclusion(attempt.result) eq denied.conclusion) shouldBe true
                if (mode == "domain-denied") (conclusion(attempt.result) eq domainFailure.conclusion) shouldBe true
                attempt.execution.attempts shouldBe 0
                checkRows(after, before)
                aftertables shouldBe tables
                session.resident shouldBe resident
                session.log.events.count(_.event == "find") shouldBe 0
                if (mode != "domain-denied") session.log.conversions shouldBe Vector.empty
              }
            } finally session.close()
          }
        }
      }

      "C3-PG3 retain committed lag/failure and actual capture faults, physical SQL NULL and logical omission without replay" in {
        with_postgresql { database =>
          for (target <- Vector(Target.Entity, Target.Aggregate); representation <- representations;
            mode <- Vector("lag", "reload", "projection", "coherence", "clear")) {
            Given(s"a normal managed native $target/$representation with selected $mode postcommit behavior and independent complete tables")
            val session = open(target, representation, Some(database))
            try {
              val changes = if (mode == "clear") Vector(field("nullable", DisplayValue.Missing)) else _changes
              val before = session.rows(); val tables = database.snapshotAll()
              _check_native_baseline(session, tables)
              val wanted = session.expected(before, tables, changes, true, 2L,
                captureFault = if (mode == "projection") CaptureFault.Projection else if (mode == "coherence") CaptureFault.Coherence else CaptureFault.None)
              if (mode == "lag") session.log.reload = Reload.Older
              if (mode == "reload") session.log.reload = Reload.Failure
              if (mode == "projection") session.log.captureFault = CaptureFault.Projection
              if (mode == "coherence") session.log.captureFault = CaptureFault.Coherence
              When("one normal command captures the real PostgreSQL acknowledgement and the actual current enclosing commit completes")
              val attempt = session.execute(session.request(changes))
              val after = session.rows(); val physical = database.snapshotAll()
              Then("the exact committed receipt, selected read attempt and complete physical/logical state correspond without a second writer")
              checkResult(attempt, session, "updated", 2L, if (mode == "clear") "ready" else "pending",
                if (mode == "clear") None else Some(if (mode == "lag") "view-not-current" else "projection-unavailable"))
              checkRows(after, wanted.rows)
              physical shouldBe wanted.physical
              checkCompleteState(session, wanted)
              session.log.events.count(_.event == "find") shouldBe (if (mode == "projection" || mode == "coherence") 0 else 1)
              session.log.events.count(_.event == "capture") shouldBe 1
              if (mode == "clear") {
                after.selected.get.getAny("nullable") shouldBe None
                val table = physical.values.find(_.rows.exists(_.get("id").contains(DataStore.EntryId(session.component.id).print))).get
                table.columns.map(_._1) should contain("nullable")
                Option(table.rows.find(_.get("id").contains(DataStore.EntryId(session.component.id).print)).get("nullable")) shouldBe None
                Given("the physically NULL attribute remains logically absent at current revision two")
                session.log.reset()
                When("the same clear command repeats through ordinary admission and native WriteIfChanged")
                val noop = session.execute(session.request(changes, 2L))
                val repeated = session.rows(); val repeatedphysical = database.snapshotAll()
                Then("the repeated clear is an authoritative NoOp without revision, audit, body or physical changes")
                checkResult(noop, session, "unchanged", 2L)
                checkRows(repeated, after)
                repeatedphysical shouldBe physical
              }
            } finally session.close()
          }
        }
      }
    }
  }
}
