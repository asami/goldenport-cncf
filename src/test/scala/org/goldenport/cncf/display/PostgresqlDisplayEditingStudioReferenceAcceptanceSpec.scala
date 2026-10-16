package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.PostgresqlAcceptance
import org.goldenport.protocol.Property
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class PostgresqlDisplayEditingStudioReferenceAcceptanceSpec extends AnyWordSpec
    with DisplayEditingStudioReferenceFixture.Vocabulary with PostgresqlAcceptance {
  import DisplayEditingStudioReferenceFixture.*
  import DisplayMutationServiceFixture.{Target, representations, conclusion}

  "REF-PG1 Native same-resource correspondence" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-PG1, rules:R1,R2,R3,R6,R8, phase:96") {
    "retain independent native catalog, rows, overflow body and physical NULL through the connected chain" in {
      Given("docs/spec/display-editing-studio-reference.md R1/R2/R3/R6/R8 REF-PG1: mandatory managed PostgreSQL 17.11 with durable settings")
      with_postgresql { database =>
        database.scalar("SHOW server_version_num") shouldBe "170011"
        database.scalar("SHOW fsync") shouldBe "on"
        database.scalar("SHOW synchronous_commit") shouldBe "on"
        for (target <- Target.values.toVector; representation <- representations; profile <- Profile.values.toVector) {
          val session = openReference(target, representation, profile, Some(database))
          try {
            val before = session.lifecycleSnapshot()
            checkNative(session, before)
            val sample = Sample("\n\u0000", witnessText, 16L, target, representation, profile)
            When("normal managed Operations connect List/select/update-to-Missing/no-op/stale/direct Business and explicit reloads")
            val result = connected(session, sample, nullableMissing = true)
            Then("full native rows/types/body/security/audit equal pre-action oracles while physical nullable remains NULL and its logical attribute is absent")
            checkNative(session, result.state)
            val rootname = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName
            val root = session.physical().find(_._1._2 == rootname).get._2.rows
              .find(_.get("id").contains(session.component.id.value)).get
            root.contains("nullable") shouldBe true
            Option(root("nullable")) shouldBe None
            session.rows().selected.get.getAny("nullable") shouldBe None
            result.entry.values(5) shouldBe DisplayValue.Missing
            result.entry.version shouldBe 18L
          } finally session.close()
          When("the owned Subsystem/SystemNode has closed after its native sequence")
          val later = session.subsystem.systemNode.bind().acquireLeaseC()
          Then("the real closed managed runtime rejects another lease")
          later.toOption shouldBe None
        }
      }
    }
  }

  "REF-PG2 Native explicit capability and Crud lifecycle" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-PG2, rules:R1,R2,R6,R7,R8, phase:96") {
    "prove real supplied create/Detail/List/Business/reload/delete/List and unsupported UpdateOnly controls" in {
      Given("docs/spec/display-editing-studio-reference.md R1/R2/R6/R7/R8 REF-PG2: mandatory native provider across all targets and representations")
      with_postgresql { database =>
        for (target <- Target.values.toVector; representation <- representations; profile <- Profile.values.toVector) {
          val session = openReference(target, representation, profile, Some(database))
          try {
            val declaration = prepare(session)
            checkNative(session, session.lifecycleSnapshot())
            When("the exact profile receives ordinary canonical CUD and explicit read requests")
            if (profile == Profile.Crud) crud(session) else unsupported(session, declaration)
            Then("actual provider effects or exact unsupported failures retain every independently expected native state and unrelated root")
            session.rows().unrelated shouldBe defined
            session.component.inputBinding.capabilities shouldBe capabilities(profile)
          } finally session.close()
          When("the owned native runtime has completed finally cleanup")
          val later = session.subsystem.systemNode.bind().acquireLeaseC()
          Then("no closed pool owner can acquire a later managed lease")
          later.toOption shouldBe None
        }
      }
    }
  }

  "REF-PG3 Native failures and independent read recovery" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-PG3, rules:R4,R5,R6,R8, phase:96") {
    "retain complete state on malformed/readonly/stale/auth/factory failures and never replay a committed Business writer" in {
      Given("docs/spec/display-editing-studio-reference.md R4/R5/R6/R8 REF-PG3: real PostgreSQL, ordinary authorization and full-state native readers")
      with_postgresql { database =>
        database.scalar("SHOW server_version_num") shouldBe "170011"
        database.scalar("SHOW fsync") shouldBe "on"
        database.scalar("SHOW synchronous_commit") shouldBe "on"
        for (target <- Target.values.toVector; representation <- representations) {
          val session = openReference(target, representation, database = Some(database))
          try {
            When("the canonical request gates receive the independent malformed/readonly/stale/auth/factory matrix")
            rejections(session)
            denials(session)
            Then("all negative branches have retained the real native baseline rather than substituting a mock or skipped proof")
            checkNative(session, session.lifecycleSnapshot())
            Given("an independently declared same-resource List selection and malformed ordinary Business revision Property")
            val declaration = prepare(session)
            val selected = select(session, declaration)
            val before = session.lifecycleSnapshot()
            val malformed = businessRequest(session, selected.resource).copy(properties = List(
              Property("resourceId", selected.resource.entityId.value, None), Property("revision", "01", None)))
            resetTrace(session)
            When("the ordinary Business Operation rejects noncanonical positive decimal spelling")
            val rejected = session.execute(malformed)
            Then("its exact structured input failure precedes every Business effect and preserves the entire native state")
            checkLifecycleFailure(conclusion(rejected.result), conclusion(Consequence.argumentInvalid(
              "revision must be a canonical positive decimal Long")))
            checkNoWrites(rejected)
            checkLifecycleState(session, before)
            When("one authorized ordinary Business commit is followed by a failed explicit Detail read and explicit read recovery")
            val result = business(session, selected, declaration.initial, recoverRead = true)
            Then("only explicit Detail/List expose the real durable revision two with exactly one Business provider mutation")
            query(session, Vector(result.entry, declaration.other))
            result.entry.version shouldBe 2L
            checkNative(session, result.state)
          } finally session.close()
        }
      }
    }
  }
}
