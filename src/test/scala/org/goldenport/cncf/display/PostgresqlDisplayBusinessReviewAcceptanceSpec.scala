package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.PostgresqlAcceptance
import org.goldenport.cncf.entity.EntityConcurrencyMetadata
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlDisplayBusinessReviewAcceptanceSpec extends AnyWordSpec
    with DisplayBusinessReviewFixture.Vocabulary with PostgresqlAcceptance {
  import DisplayBusinessReviewFixture.*
  import DisplayMutationServiceFixture.{Target, representations, conclusion, revision, denied}

  "BRPG1 Native ordinary Business and explicit Detail" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRPG1, rules:BR1,BR2,PG5,PG11,PG12, phase:96") {
    "commit every canonical target and representation through managed PostgreSQL with independent complete native evidence" in {
      with_postgresql { database =>
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"docs/spec/display-business-operation-descriptor.md BR1/BR2 and postgresql-phase-96-persistence.md PG5/PG11/PG12: native $target/$representation")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          val session = open(target, representation, Some(database))
          try {
            val before = baseline(session)
            checkNative(session, before)
            val wanted = expected(session, before, 2L)
            When("one ordinary Business command uses normal managed ComponentDataStore and canonical UserPermission mutation")
            val attempt = session.execute(request(session))
            Then("the real enclosing UoW commits revision two with exactly the predeclared stage/audit change and no implicit Browser read")
            val response = checkCommit(attempt, session, before, wanted, 2L)
            attempt.execution.space.observers should not be empty
            checkReload(session, before, response, 2L)
            checkState(session, wanted)
          } finally session.close()
          When("the owned Subsystem/SystemNode has shut down after the complete correspondence proof")
          val later = session.subsystem.systemNode.bind().acquireLeaseC()
          Then("a later lease is rejected by the real closed managed node")
          later.toOption shouldBe None
        }
      }
    }
  }

  "BRPG2 Native rejected Business preservation" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRPG2, rules:BR1,BR2,PG5,PG11,PG12, phase:96") {
    "preserve every complete native table, logical field and body on malformed, stale and denied requests" in {
      with_postgresql { database =>
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"docs/spec/display-business-operation-descriptor.md BR1/BR2 and postgresql-phase-96-persistence.md PG5/PG11/PG12: warmed real $target/$representation")
          val session = open(target, representation, Some(database))
          try {
            val cases = malformed(session).map(candidate => "malformed" -> candidate) ++ Vector(
              "stale" -> request(session, "2"), "anonymous" -> request(session),
              "foreign" -> request(session), "factory" -> request(session))
            cases.foreach { case (mode, candidate) =>
              Given(s"complete independently observed native columns/types/rows, ordered logical fields and warmed Views before $mode")
              session.log.denyFactory = false
              val before = baseline(session)
              checkNative(session, before)
              session.log.denyFactory = mode == "factory"
              val active = session.execution(principal = if (mode == "foreign") "foreign" else "owner",
                authenticated = mode != "anonymous")
              When("the normal ordinary Business Operation receives the invalid observation, input or subject")
              val attempt = session.execute(candidate, active)
              Then("structured canonical rejection leaves all durable data and Browser/build observations unchanged, without publication")
              attempt.result shouldBe a[Consequence.Failure[?]]
              if (mode == "stale") checkLifecycleFailure(conclusion(attempt.result),
                conclusion(EntityConcurrencyMetadata.staleMutation(revision(2L), revision(1L))))
              if (mode == "factory") (conclusion(attempt.result) eq denied.conclusion) shouldBe true
              if (mode != "stale") active.attempts shouldBe 0
              active.space.observers.flatMap(_.captured).flatMap(_.record) shouldBe empty
              checkPreservedBusiness(session, before, stale = mode == "stale")
              if (mode == "malformed") session.log.events.count(_.event == "business") shouldBe 0
            }
          } finally session.close()
        }
      }
    }
  }

  "BRPG3 Native production authenticated HTTP" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRPG3, rules:BO5,BR1,BR2,PG5,PG11,PG12, phase:96") {
    "prove real provider owner and foreign sessions on PostgreSQL for Entity and Aggregate across both representations" in {
      with_postgresql { database =>
        for (target <- Vector(Target.Entity, Target.Aggregate); representation <- representations) {
          Given(s"docs/spec/display-business-operation-descriptor.md BO5/BR1/BR2 and postgresql-phase-96-persistence.md PG5/PG11/PG12: production standard/system $target/$representation")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          val session = open(target, representation, Some(database), Setup(production = true))
          try {
            val before = baseline(session)
            checkNative(session, before)
            When("actual provider-backed Http4s ingress receives untrusted/foreign/malformed negatives and the legitimate owner positive control")
            checkProductionHttp(session)
            Then("actual native canonical commit and explicit fresh Detail establish production identity correspondence without a trusted direct Execution context")
            session.log.events.count(_.event == "business") shouldBe 1
            session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
          } finally session.close()
        }
      }
    }
  }
}
