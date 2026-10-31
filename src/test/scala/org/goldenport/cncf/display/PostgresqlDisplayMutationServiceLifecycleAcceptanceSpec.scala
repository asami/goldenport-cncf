package org.goldenport.cncf.display

import logicalui.DisplayMutationServiceDomain.BodyMode
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityMutationProviderFeature, PostgresqlAcceptance}
import org.goldenport.observation.Taxonomy
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlDisplayMutationServiceLifecycleAcceptanceSpec extends AnyWordSpec
    with DisplayMutationServiceLifecyclePropertySpec.Laws
    with DisplayMutationServiceLifecycleIngressSpec.AdapterVocabulary with PostgresqlAcceptance {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceLifecyclePropertySpec.Pool
  import DisplayMutation.Operation
  private val _governed = afterWord("under docs/spec/display-mutation-service.md DLC-01..13, docs/spec/postgresql-phase-96-persistence.md, docs/design/display-mutation-service.md DLC-D01 and RULE.md")

  "Native ordinary Display lifecycle" should {
    "mandatory managed PostgreSQL correspondence" which _governed {
      "DLC-PG1 retain native positive create logical and physical delete plus precise rejected-target and current-commit state" in {
        with_postgresql { database =>
          Given("the required real PostgreSQL 17.11 runtime with fsync/synchronous_commit and independent complete catalog reader")
          database.scalar("SHOW server_version_num") shouldBe "170011"
          database.scalar("SHOW fsync") shouldBe "on"
          database.scalar("SHOW synchronous_commit") shouldBe "on"
          for (target <- Target.values.toVector; representation <- representations; supplied <- Vector(false, true);
            bodymode <- BodyMode.values.toVector) {
            Given(s"normally bootstrapped managed $target/$representation with supplied=$supplied and server body=$bodymode")
            val session = open(target, representation, Some(database), lifecycle = Some(LifecycleSetup(supplied, bodymode)))
            try {
              session.prepareLifecycle(true)
              val fields = if (supplied) lifecycleFields else lifecycleFields.filterNot(_.location.fieldId == "nullable").reverse
              val before = session.lifecycleSnapshot()
              val wanted = expectedLifecycleCreate(session, before, fields)
              val rootname = DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName
              val roottable = before.physical.toVector.find(_._1._2 == rootname).get._2
              roottable.columns.map(_._1) shouldBe lifecycleRootColumns(representation)
              roottable.rows.map(_("id")) shouldBe Vector(session.component.unrelatedId.value)
              before.rows.selected shouldBe None
              before.rows.body shouldBe None
              When("one ordinary typed create is captured by the actual selected PostgreSQL provider and committed by its enclosing ActionEngine")
              val created = session.execute(session.lifecycleRequest(Operation.Create, fields))
              Then("actual INITIAL Created and contextual Detail match every predeclared logical/native/default/NULL/body/resident/View value")
              checkLifecycleResult(created, session, true, 1L)
              session.log.idCalls shouldBe (if (supplied) Vector.empty else Vector(session.component.collectionId -> "entity-create"))
              checkLifecycleState(session, wanted)
              for (logical <- Vector(true, false)) {
                Given("an independently reset native revision-seven root for the selected logical/physical delete plan")
                session.prepareLifecycle(false, 7L, logical)
                val deletebefore = session.lifecycleSnapshot()
                val deletewanted = expectedLifecycleDelete(session, deletebefore, 7L, logical)
                When("ordinary observed deletion authorizes the actual Entity or full Aggregate family and captures one native delete")
                val deleted = session.execute(session.lifecycleRequest(Operation.Delete, observed = 7L))
                Then("matched PRIOR Deleted and reload-list preserve full native correspondence without deleted Detail or replay")
                checkLifecycleResult(deleted, session, false, 7L)
                session.log.events.count(_.event == "find") shouldBe 0
                checkLifecycleState(session, deletewanted)
                if (logical) {
                  session.rows().body.map(_.fields) shouldBe deletebefore.rows.body.map(_.fields)
                  When("a later explicit normal Detail reads the native tombstone")
                  val missing = session.read()
                  Then("normal visibility hides the deleted root with the canonical entity-not-found classification")
                  conclusion(missing.result).observation.taxonomy shouldBe missingConclusion(session.component.id).observation.taxonomy
                }
              }
            } finally session.close()
          }

          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"the ordinary managed $target/$representation native failure/control route")
            val session = open(target, representation, Some(database), lifecycle = Some(LifecycleSetup()))
            try {
              for (boundary <- DeleteRejectionBoundary.values.toVector) checkLifecycleDeadTarget(session, boundary)
              for (create <- Vector(true, false); mode <- Vector("factory", "unauthenticated", "default", "domain", "stale", "duplicate")) {
                if (mode == "default" && target == Target.Entity) checkLifecycleForeignPrincipal(session, create)
                else {
                Given(s"a complete reset native baseline before the independently selected $mode gate with create=$create")
                session.prepareLifecycle(create && mode != "duplicate", 7L)
                val before = session.lifecycleSnapshot()
                val wanted = expectedLifecycleDeleteRejection(before, DeleteRejectionBoundary.BeforeCapture)
                session.log.denyFactory = mode == "factory"
                session.log.rejectDelete = mode == "domain"
                val fields = if (mode == "domain") Vector(field("title", DisplayValue.Text(" "))) else lifecycleFields
                val active = session.execution(principal = if (mode == "default" && target == Target.Entity) "foreign" else "owner",
                  authenticated = mode != "unauthenticated" && !(mode == "default" && target != Target.Entity))
                val operation = if (create) Operation.Create else Operation.Delete
                // Create has no observed-revision gate, and delete has no duplicate-create gate.
                val reject = !(create && mode == "stale") && !(!create && mode == "duplicate")
                if (reject) {
                  val expectedcommands = if (create && target != Target.Entity && mode == "duplicate") 1 else 0
                  When("the normal native lifecycle request reaches its responsible authorization domain revision or duplicate gate")
                  val attempt = session.execute(session.lifecycleRequest(operation, fields, if (mode == "stale") 6L else 7L), active)
                  Then("the precise responsible structured failure retains every complete native/body/unrelated/resident/View dimension")
                  if (mode == "duplicate") {
                    attempt.result shouldBe a[Consequence.Failure[?]]
                    active.lifecycleObservers.flatMap(_.creates) should have size 1
                    active.lifecycleObservers.flatMap(_.created) shouldBe Vector.empty
                    active.lifecycleObservers.map(_.writes).sum shouldBe 0
                    checkLifecycleState(session, wanted)
                  } else {
                    checkLifecyclePreserved(session, wanted, attempt, converted = create && mode == "domain")
                    if (mode == "factory") (conclusion(attempt.result) eq denied.conclusion) shouldBe true
                    if (mode == "domain") (conclusion(attempt.result) eq domainFailure.conclusion) shouldBe true
                    if (mode == "stale") {
                      conclusion(attempt.result).observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
                      reasons(attempt.result) should contain("stale-entity-revision")
                    }
                  }
                  session.log.events.count(_.event == "find") shouldBe 0
                  session.log.commands shouldBe expectedcommands
                }
                }
              }
              for (create <- Vector(true, false); fault <- (if (create) Vector(CaptureFault.Projection, CaptureFault.Coherence) else Vector(CaptureFault.Coherence))) {
                Given("a reset native root/absence and full post-ACK optional-coherence oracle fixed before the request")
                session.prepareLifecycle(create)
                val before = session.lifecycleSnapshot()
                val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields, fault)
                  else expectedLifecycleDelete(session, before, 1L, true, fault)
                session.log.captureFault = fault
                When("the actual native provider acknowledges one ordinary command before the optional coherence boundary")
                val attempt = session.execute(session.lifecycleRequest(if (create) Operation.Create else Operation.Delete))
                Then("Created pending or Deleted reload-list retains the native durable result and prescribed caches without command replay")
                checkLifecycleResult(attempt, session, create, 1L, if (create) "pending" else "ready")
                session.log.events.count(_.event == "find") shouldBe 0
                checkLifecycleState(session, wanted)
              }
              for (create <- Vector(true, false); mode <- Commit.values.toVector; historical <- Vector(false, true)) {
                Given(s"reset complete native state with current commit=$mode historical=$historical and independently declared durable command effects")
                session.prepareLifecycle(create)
                val before = session.lifecycleSnapshot()
                val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields)
                  else expectedLifecycleDelete(session, before, 1L, true)
                val active = session.execution(mode, historical = historical)
                When("one actual native capture reaches the enclosing current-operation commit guard")
                val attempt = session.execute(session.lifecycleRequest(if (create) Operation.Create else Operation.Delete), active)
                Then("only a current successful witness returns success and no ACK or historical result authorizes replay or invented rollback")
                if (mode == Commit.Normal) checkLifecycleResult(attempt, session, create, 1L)
                else {
                  attempt.result shouldBe a[Consequence.Failure[?]]
                  session.log.events.count(_.event == "find") shouldBe 0
                  active.lifecycleObservers.map(_.writes).sum shouldBe 0
                }
                session.log.events.count(_.event == "capture") shouldBe 1
                session.log.events.count(_.event == "commit") shouldBe 1
                checkLifecycleState(session, wanted)
              }
            } finally session.close()
            for (removed <- Vector(Set(EntityMutationProviderFeature.OptimisticCompareAndDelete),
              Set(EntityMutationProviderFeature.AuthoritativeDeleteResult), Set(EntityMutationProviderFeature.AtomicSideEffects))) {
              Given("the actual managed native provider with only one frozen required feature removed from its observed capability")
              val faulty = open(target, representation, Some(database), lifecycle = Some(LifecycleSetup(logical = false, removed = removed)))
              try {
                faulty.prepareLifecycle(false, logical = false)
                val before = faulty.lifecycleSnapshot()
                When("normal physical observed delete checks the selected provider and its actual body side-effect plan")
                val attempt = faulty.execute(faulty.lifecycleRequest(Operation.Delete))
                Then("capability rejection preserves the entire native resource and cannot fall back to a generic writer")
                checkLifecyclePreserved(faulty, before, attempt)
              } finally faulty.close()
            }
          }
        }
      }

      "DLC-PG2 preserve actual native HTTP ArgsIngress MCP and SPI create-delete correspondence" in {
        with_postgresql { database =>
          for (target <- Target.values.toVector; representation <- representations) {
            Given(s"a normal managed native $target/$representation with explicit supplied server identity and authenticated owner/full trace")
            val session = open(target, representation, Some(database), lifecycle = Some(LifecycleSetup()))
            try {
              for (adapter <- Vector("http", "cli", "mcp", "spi"); create <- Vector(true, false)) {
                Given(s"the complete independent native $adapter oracle before create=$create")
                session.prepareLifecycle(create, 7L)
                val before = session.lifecycleSnapshot()
                val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields)
                  else expectedLifecycleDelete(session, before, 7L, true)
                val operation = if (create) Operation.Create else Operation.Delete
                When("the actual ordinary adapter carries strict raw payload and operation-specific observation to the normal native command")
                val result = lifecycleAdapter(session, operation, session.lifecyclePayload(operation), adapter, 7L)
                Then("full business/native/default/context and current committed receipt correspond without a synthetic producer or second writer")
                checkLifecycleAdapter(session, result, create, if (create) 1L else 7L, wanted)
                Given("the same reset native state with the exact owner explicitly unauthenticated")
                session.prepareLifecycle(create, 7L)
                val deniedbefore = session.lifecycleSnapshot()
                When("the same actual adapter carries false authentication")
                val rejected = lifecycleAdapter(session, operation, session.lifecyclePayload(operation), adapter, 7L, authenticated = false)
                Then("authorization denies without any native or derived-state effects")
                rejected.status should be >= 400
                rejected.value shouldBe None
                session.log.authorizations shouldBe Vector.empty
                checkLifecycleIngressPreserved(session, deniedbefore, rejected.execution)
              }
            } finally session.close()
          }
        }
      }

      "DLC-PG-P1 require one hundred total shrinking native positive create successes with zero discards" in {
        with_postgresql { database =>
          Given("the suite-owned required PostgreSQL runtime and normal managed create pool retaining at most four live sessions with exact-ID reset/cleanup")
          val pool = new Pool("postgresql", Some(database))
          When("the separate native positive-create law interprets one hundred total shrinking ordinary commands")
          val result = try createLaw(pool, "DLC-PG-P1") finally pool.close()
          Then("mandatory native creation has one hundred successful samples and zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }

      "DLC-PG-P2 require one hundred total shrinking native observed-delete successes with zero discards" in {
        with_postgresql { database =>
          Given("the suite-owned required PostgreSQL runtime and normal managed logical/physical current/stale delete pool retaining at most four live sessions")
          val pool = new Pool("postgresql", Some(database))
          When("the separate native observed-delete law interprets one hundred total shrinking current/stale requests")
          val result = try deleteLaw(pool, "DLC-PG-P2") finally pool.close()
          Then("mandatory native observed deletion has one hundred successful samples and zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
}
