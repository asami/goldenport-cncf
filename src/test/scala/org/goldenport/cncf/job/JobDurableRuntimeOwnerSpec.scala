package org.goldenport.cncf.job

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.record.Record
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, JobDurableRuntimeConfiguration}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobDurableRuntimeFixtures.*

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableRuntimeOwnerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-runtime-owner, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1B")

  "DO1 The unconfigured original engine" must _scenario("DO1", "R1,R3,R4") {
    "retain its native identity without factory source or storage effects" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DO1: a Subsystem with no durable binding and a registered unused provider")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        val original = fixture.subsystem.jobEngine
        success(fixture.subsystem._register_durable_job_provider_c(fixture.registered))
        When("unconfigured initialization and repeated health reads occur")
        success(fixture.subsystem._initialize_durable_job_runtime_c())
        val observations = Vector.fill(8)(fixture.engine.durableOperationalHealth.get)
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        success(fixture.engine.submit(List(task), fixture.context, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Async)))
        fixture.engine.drainAll() shouldBe 1
        Then("the same engine stays Unconfigured with no provider, source, or datastore acquisition")
        fixture.subsystem.jobEngine should be theSameInstanceAs original
        observations.map(_.state).distinct shouldBe Vector(JobDurableOperationalState.Unconfigured)
        observations.forall(value => !value.configured && !value.executionAvailable) shouldBe true
        fixture.builds.get() shouldBe 0
        fixture.startup.calls.get() shouldBe 0
        fixture.node.managedDataStoreCount shouldBe 0
        task.runs.get() shouldBe 1
        val catalog = CncfConfigurationParameterCatalog.closed.definitions
        catalog.size shouldBe 55
        val keys = catalog.map(_.canonicalSpelling)
        keys.distinct.size shouldBe 55
        keys should contain allOf(JobDurableRuntimeConfiguration.providerKey, JobDurableRuntimeConfiguration.periodKey,
          JobDurableRuntimeConfiguration.candidatesKey, "textus.subsystem.user-mode")
      }
    }
  }
  "DO2 Exact per-Subsystem provider admission" must _scenario("DO2", "R3,R4") {
    "select once and refuse invalid duplicate foreign missing late and concurrent attempts" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DO2: two independent bindings and a blocked actual provider startup")
      JobDurableRuntimeFixtures.withFixture { first => JobDurableRuntimeFixtures.withFixture { second =>
        first.admit(); second.admit()
        first.subsystem._register_durable_job_provider_c(null).isSuccess shouldBe false
        first.subsystem._register_durable_job_provider_c(new ConfiguredDurableJobRuntimeProvider("bad.id", _ => throw new AssertionError)).isSuccess shouldBe false
        val foreign = new AtomicInteger
        success(first.subsystem._register_durable_job_provider_c(new ConfiguredDurableJobRuntimeProvider("foreign", _ => {
          foreign.incrementAndGet(); DurableJobRuntimeOwner.refused
        })))
        success(first.subsystem._register_durable_job_provider_c(first.registered))
        first.subsystem._register_durable_job_provider_c(first.registered).isSuccess shouldBe false
        success(second.subsystem._register_durable_job_provider_c(second.registered))
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        first.startup.before = () => { entered.countDown(); await(release) }
        val running = actor(first.subsystem._initialize_durable_job_runtime_c())
        try {
          await(entered)
          When("the second Subsystem starts and the first receives overlapping then repeated initialization")
          success(second.subsystem._initialize_durable_job_runtime_c())
          first.subsystem._initialize_durable_job_runtime_c().isSuccess shouldBe false
          first.subsystem._register_durable_job_provider_c(new ConfiguredDurableJobRuntimeProvider("late", _ => throw new AssertionError)).isSuccess shouldBe false
        } finally { release.countDown(); join(running._1) }
        Then("each exact provider was called once and the foreign factory was never selected")
        running._2.get().toOption.get.isSuccess shouldBe true
        success(first.subsystem._initialize_durable_job_runtime_c())
        first.builds.get() shouldBe 1; second.builds.get() shouldBe 1; foreign.get() shouldBe 0
        first.owner should not be theSameInstanceAs(second.owner)
        first.subsystem._register_durable_job_provider_c(first.registered).isSuccess shouldBe false
      }}
      JobDurableRuntimeFixtures.withFixture { missing =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO2: a configured identity without an exact registered provider")
        missing.admit("absent")
        When("final initialization selects the admitted exact identity")
        val result = missing.subsystem._initialize_durable_job_runtime_c()
        Then("selection refuses without factory or discovery effects")
        result.isSuccess shouldBe false
        missing.builds.get() shouldBe 0
        missing.startup.calls.get() shouldBe 0
      }
    }
  }
  "DO3 Native policy and freshness admission" must _scenario("DO3", "R5,R6") {
    "reject mismatches and previous native work without starting replacement resources" in {
      Vector(0, 4097).foreach { limit => JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: an actual native factory with an invalid or raised storage ceiling")
        fixture.storagePolicy = fixture.storagePolicy.copy(maxstoredjobs = limit)
        When("configured startup validates the supplied native storage policy")
        val result = fixture.start()
        Then("the original source has not run and no Task publication occurred")
        result.isSuccess shouldBe false
        fixture.startup.calls.get() shouldBe 0
        fixture.engine.resourceSnapshot.retained shouldBe 0
      }}
      Vector(0, 4097).foreach { limit => JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: an actual native factory with an invalid or raised owned Blob ceiling")
        fixture.blobPolicy = fixture.blobPolicy.copy(maxownedblobs = limit)
        When("configured startup validates the supplied native Blob policy")
        val result = fixture.start()
        Then("the original source has not run and no Task publication occurred")
        result.isSuccess shouldBe false
        fixture.startup.calls.get() shouldBe 0
        fixture.engine.resourceSnapshot.retained shouldBe 0
      }}
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: a supplied lifetime differing from the original State policy")
        fixture.lifetimePolicy = fixture.lifetimePolicy.copy(lifetime = fixture.lifetimePolicy.lifetime.copy(maxchargedtasks = 1L))
        When("configured startup compares the original attached lifetime policy")
        val result = fixture.start()
        Then("the source has not run and State policy has not changed")
        result.isSuccess shouldBe false
        fixture.startup.calls.get() shouldBe 0
        fixture.engine.runtimeState._lifetime_budget.policy shouldBe JobLifetimePolicy.default
      }
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: pending work on the original unconfigured engine")
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        success(fixture.engine.submit(List(task), fixture.context, JobSubmitOption(runMode = JobRunMode.Async)))
        When("configured startup checks original freshness")
        val result = fixture.start()
        Then("factory and Task body remain untouched")
        result.isSuccess shouldBe false
        fixture.builds.get() shouldBe 0
        task.runs.get() shouldBe 0
      }
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: the original worker startup CAS has already been committed")
        fixture.engine._ensure_scheduler_workers()
        When("configured startup checks original worker ownership")
        val result = fixture.start()
        Then("the provider factory is not invoked")
        result.isSuccess shouldBe false
        fixture.builds.get() shouldBe 0
      }
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO3: the original engine already has a lifecycle bridge")
        fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(new DurableJobStore(fixture.entity), fixture.evidence))
        When("configured startup checks original bridge ownership")
        val result = fixture.start()
        Then("the provider factory is not invoked")
        result.isSuccess shouldBe false
        fixture.builds.get() shouldBe 0
      }
    }
  }
  "DO4 Transparent effect delegation" must _scenario("DO4", "R9,R10") {
    "delegate all 31 Entity and five Blob effects once under the real nested binding lease" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DO4: the real SystemNode lease and delegates retaining one Failure instance")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        given ExecutionContext = fixture.context
        val lease = DurableJobRuntimeLease.subsystem(fixture.subsystem)
        val failure = Consequence.stateInvalid[Nothing]("PRIVATE delegate failure")
        val faults = new AtomicInteger
        val spy = new EntityProbe(fixture, failure)
        val entity = new DurableJobLeasedEntityStore(spy, lease, () => { faults.incrementAndGet(); () })
        val id = success(DurableJobLifetimeQuotaEntity.entityId)
        val value = DurableJobLifetimeQuotaEntity(id, "{}")
        given EntityPersistentCreate[DurableJobLifetimeQuotaEntity] = EntityPersistentCreate.fromPersistent(summon[EntityPersistent[DurableJobLifetimeQuotaEntity]])
        given EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
          def collection(e: Record): EntityCollectionId = id.collection
          def toRecord(e: Record): Record = e
          def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
        }
        val revision = success(EntityRevision.createC(7L))
        val options = EntityCreateOptions.default; val policy = EntityMutationExecutionPolicy.default
        val patch = Record.data("marker" -> "patch")
        val authorize: Option[Record] => Consequence[Unit] = _ => Consequence.unit
        val saved: CreateResult[DurableJobLifetimeQuotaEntity] => Consequence[Unit] = _ => Consequence.unit
        val query = EntityQuery[DurableJobLifetimeQuotaEntity](id.collection, Query.plan(Record.empty))
        val command = null.asInstanceOf[EntityConditionalTransitionCommand[Record, Record, Record]]
        val calls: Vector[() => Consequence[?]] = Vector(
          () => entity.create(value, options), () => entity.claimOrLoad[DurableJobLifetimeQuotaEntity, DurableJobLifetimeQuotaEntity](value, options),
          () => entity.upsert(value, id, options)(authorize), () => entity.upsert(value, id, options)(authorize, saved),
          () => entity.upsertVersioned(value, id, options, EntityUpsertPolicy.default)(authorize),
          () => entity.load[DurableJobLifetimeQuotaEntity](id), () => entity.loadSnapshot[DurableJobLifetimeQuotaEntity](id),
          () => entity.loadDetached[DurableJobLifetimeQuotaEntity](id), () => entity.save(value), () => entity.saveManaged(value, policy),
          () => entity.save(value, revision), () => entity.save(value, Some(revision), policy), () => entity.saveDetached(value, Some(revision), policy),
          () => entity.update(value), () => entity.update(value, revision), () => entity.update(value, Some(revision), policy),
          () => entity.updateDetached(value, Some(revision), policy), () => entity.updateById(id, patch, revision),
          () => entity.updateById(id, patch, Some(revision), policy), () => entity.updateByIdManaged(id, patch, policy),
          () => entity.updateByIdManagedAuthoritative(id, patch, policy, EntityStore.ManagedMutationBase.Resolved(None)),
          () => entity.updateByIdDetached(id, patch, Some(revision), policy), () => entity.updateByIdUnversioned(id, patch),
          () => entity.conditionalTransition(command), () => entity.delete(id), () => entity.restore(id), () => entity.deleteHard(id),
          () => entity.search(query), () => entity.searchInternal(query),
          () => entity.uniqueValueExists[DurableJobLifetimeQuotaEntity](id.collection, "marker", "patch", Some(id), EntityIdentityScope.Global, true),
          () => entity.resolveIdentity[DurableJobLifetimeQuotaEntity](id.collection, "patch", Vector("marker"), true, EntityIdentityScope.Global))
        val blobprobe = new BlobProbe(fixture, failure)
        val blob = new DurableJobLeasedBlobStore(blobprobe, lease, () => { faults.incrementAndGet(); () })
        val ref = BlobStorageRef("blob", "default", "private-key")
        val request = BlobPutRequest(id, BlobKind.Binary, Some("private-name"), ContentType.APPLICATION_OCTET_STREAM)
        val payload = Bag.binary(Array[Byte](1, 2))
        val blobcalls: Vector[() => Consequence[?]] = Vector(() => blob.put(request, payload), () => blob.get(ref),
          () => blob.delete(ref), () => blob.accessUrl(ref), () => blob.status())
        When("every distinct overload runs and the outer gate closes during an already admitted nested call")
        calls.foreach(call => (call().asInstanceOf[AnyRef] eq failure.asInstanceOf[AnyRef]) shouldBe true)
        blobcalls.foreach(call => (call().asInstanceOf[AnyRef] eq failure.asInstanceOf[AnyRef]) shouldBe true)
        entity.name shouldBe "entity-probe"; entity.isAccept(id.collection) shouldBe true; blob.name shouldBe "blob-probe"
        val throwable = new IllegalStateException("PRIVATE thrown delegate")
        spy.throwable = Some(throwable)
        intercept[IllegalStateException](entity.delete(id)) should be theSameInstanceAs throwable
        spy.throwable = None
        success(lease.use { lease.close(); entity.delete(id) match {
          case result if result.asInstanceOf[AnyRef] eq failure.asInstanceOf[AnyRef] => Consequence.unit
          case _ => Consequence.stateInvalid("nested delegate changed")
        }})
        val before = spy.calls.size
        entity.delete(id).isSuccess shouldBe false
        Then("all effects retain identity, argument witnesses and one real outer lease, while new entries are refused")
        spy.calls.take(31).map(_._1) shouldBe Vector.tabulate(31)(index => s"entity-$index")
        spy.calls.forall(_._3 == 1) shouldBe true
        spy.calls.forall(_._4 eq fixture.context) shouldBe true
        spy.calls(3)._2 shouldBe Vector(value, id, options, authorize, saved)
        spy.calls(20)._2 shouldBe Vector(id, patch, policy, EntityStore.ManagedMutationBase.Resolved(None))
        blobprobe.calls.map(_._1) shouldBe Vector("put", "get", "delete", "accessUrl", "status")
        blobprobe.calls.forall(_._3 == 1) shouldBe true
        blobprobe.calls.head._2 shouldBe Vector(request, payload)
        spy.calls.size shouldBe before
        fixture.node.activeLeaseCount shouldBe 0
        faults.get() shouldBe 39
      }
    }
  }
  "DO5 Actual operational health" must _scenario("DO5", "R14,R15,R16") {
    "publish Ready only after native probes and retain adverse faults through pure reads" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DO5: a configured native owner and its actual registered maintenance")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        fixture.owner.health shouldBe JobDurableOperationalHealth(JobDurableOperationalState.Ready, true, true, true, true, true, true)
        val before = (fixture.builds.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.entity.loads.get(), fixture.clock.calls.get(), fixture.ids.calls.get())
        When("pure health reads precede a real missing retention candidate and then a healthy empty sweep")
        Vector.fill(12)(fixture.engine.durableOperationalHealth.get).forall(_.state == JobDurableOperationalState.Ready) shouldBe true
        (fixture.builds.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.entity.loads.get(), fixture.clock.calls.get(), fixture.ids.calls.get()) shouldBe before
        fixture.retention.entries = Vector(DurableJobRetentionCandidate(0L, JobInputBoundsFixtures.id("missing-health").value, access))
        fixture.advance()
        fixture.owner.health.state shouldBe JobDurableOperationalState.Degraded
        fixture.retention.entries = Vector.empty
        fixture.advance()
        Then("storage/startup stay available, degradation is sticky, reads expose only the enum and booleans, and closing disables every facet")
        val health = fixture.owner.health
        health.state shouldBe JobDurableOperationalState.Degraded
        health.storageReady shouldBe true; health.startupReady shouldBe true; health.maintenanceHealthy shouldBe false
        health.productIterator.drop(1).forall(_.isInstanceOf[Boolean]) shouldBe true
        success(fixture.subsystem.shutdownC())
        fixture.owner.health shouldBe JobDurableOperationalHealth(JobDurableOperationalState.Closed, true, false, false, false, false, false)
      }
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO5: an actual native factory whose Blob readiness probe refuses")
        fixture.selectedBlob = new BlobProbe(fixture, Consequence.stateInvalid("PRIVATE unavailable probe"))
        When("configured startup probes the supplied native backend")
        val result = fixture.start()
        Then("health is degraded with no candidate discovery or execution")
        result.isSuccess shouldBe false
        fixture.startup.calls.get() shouldBe 0
        fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Degraded
      }
      JobDurableRuntimeFixtures.withFixture { fixture =>
        Given("docs/spec/durable-job-runtime-owner-contract.md DO5: an actual available native owner whose leased Blob delegate will fail")
        success(fixture.start())
        When("the leased persistent Blob backend receives a genuinely missing native key")
        fixture.owner._blob_store.get(BlobStorageRef(fixture.blob.name, "default", "PRIVATE missing")).isSuccess shouldBe false
        Then("the provider fault degrades the owner permanently through subsequent healthy maintenance")
        fixture.owner.health.state shouldBe JobDurableOperationalState.Degraded
        fixture.advance()
        fixture.owner.health.state shouldBe JobDurableOperationalState.Degraded
      }
      Vector("notdue", "held", "shared").foreach { mode => JobDurableRuntimeFixtures.withFixture { fixture =>
        Given(s"docs/spec/durable-job-runtime-owner-contract.md DO5: genuine native terminal evidence and a real persistent shared asset for $mode")
        val supplied = DurableJobKeyedEvidenceFixtures.recipe()
        val id = fixture.context.idGeneration.entityId(BlobRepository.CollectionId, "shared-asset")
        val stored = success(fixture.blob.put(BlobPutRequest(id, BlobKind.Binary, Some("shared.bin"),
          ContentType.APPLICATION_OCTET_STREAM), Bag.binary(Array[Byte](1, 2, 3))))
        val inputs = if (mode == "shared") Vector(DurableInputReference("shared", DurableValue.External(
          DurableExternalReference(id.value, "shared", Some(stored.contentType.header), stored.byteSize, stored.digest)))) else Vector.empty
        val retention = supplied.projection.retention.copy(expiresAt = Some(instant.plusSeconds(if (mode == "notdue") 3600 else 1)),
          retainUntil = if (mode == "held") Some(java.time.Instant.MAX) else None)
        fixture.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(_ => supplied.copy(projection = supplied.projection.copy(
          inputs = inputs, retention = retention)))
        success(fixture.start())
        val jobid = fixture.submit(new JobDurableLifetimeRecoveryFixtures.Task)
        fixture.engine.drainAll() shouldBe 1
        fixture.retention.entries = Vector(DurableJobRetentionCandidate(0L, jobid.value,
          access.copy(scopes = access.scopes + DurableJobRetentionPolicy.manageScope)))
        When("the registered original manual timer runs the complete native sweep")
        fixture.advance()
        Then("normal not-due, held, or shared acknowledgement stays healthy and the actual shared bytes remain")
        fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
        val report = fixture.owner._scheduler.status.lastResult.get.toOption.get.entries.head
        if (mode == "notdue") report.retention shouldBe Vector(DurableJobRetentionFacet.NotDue)
        if (mode == "held") report.retention shouldBe Vector(DurableJobRetentionFacet.Held)
        if (mode == "shared") report.cleanup shouldBe Some(DurableJobCleanupFacet.Tombstoned)
        fixture.blob.get(stored.storageRef).isSuccess shouldBe true
      }}
    }
  }
  "DO6 Shrinking owner operation sequences" must _scenario("DO6", "R9,R12,R14,R19") {
    "retain real lease and registration invariants for seed 69709702 and at least 24 successes" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DO6: ordinary shrinking query/fault/close sequences on fresh native bindings")
      val property = Prop.forAll(Gen.choose(1, 8).flatMap(size => Gen.listOfN(size, Gen.choose(0, 3)))) { operations => JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        var closed = false; var degraded = false
        val observedleases = scala.collection.mutable.ArrayBuffer.empty[Int]
        fixture.retention.before = () => { observedleases += fixture.node.activeLeaseCount; () }
        operations.forall { operation =>
          operation match {
            case 0 => fixture.engine.durableOperationalHealth
            case 1 if !closed => fixture.retention.fault = true; fixture.advance(); degraded = true
            case 2 => success(fixture.subsystem.shutdownC()); closed = true
            case _ => ()
          }
          val health = fixture.engine.durableOperationalHealth.get
          fixture.node.activeLeaseCount == 0 && observedleases.forall(_ == 1) && health.configured &&
            (if (closed) health.state == JobDurableOperationalState.Closed && !health.maintenanceRegistered && !health.executionAvailable
             else health.maintenanceRegistered && fixture.owner._scheduler.status.nextDueAt.nonEmpty &&
               !fixture.owner._scheduler.status.inFlight && health.state == (if (degraded) JobDurableOperationalState.Degraded else JobDurableOperationalState.Ready))
        }
      }}
      When("ScalaCheck runs the seeded shrinking property")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709702L)), property)
      Then("every generated operation preserves the native lease and monotone health contract")
      result.passed shouldBe true
      result.succeeded should be >= 24
    }
  }

  private final class EntityProbe(fixture: Fixture, failure: Consequence[Nothing]) extends EntityStore {
    val calls = scala.collection.mutable.ArrayBuffer.empty[(String, Vector[Any], Int, ExecutionContext)]
    var throwable = Option.empty[IllegalStateException]
    def name: String = "entity-probe"
    private def _hit[A](name: String, arguments: Vector[Any], ctx: ExecutionContext): Consequence[A] = {
      calls += ((name, arguments, fixture.node.activeLeaseCount, ctx))
      throwable.foreach(exception => throw exception)
      failure
    }
    override def create[T](entity: T, options: EntityCreateOptions = EntityCreateOptions.default)
      (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
      _hit("entity-0", Vector(entity, options), ctx)
    override def claimOrLoad[C, P](entity: C, options: EntityCreateOptions = EntityCreateOptions.default)
      (using createTc: EntityPersistentCreate[C], persisted: EntityPersistent[P], ctx: ExecutionContext): Consequence[EntityStore.EntityClaimResult[C, P]] =
      _hit("entity-1", Vector(entity, options), ctx)
    override private[cncf] def upsert[T](entity: T, id: EntityId, options: EntityCreateOptions = EntityCreateOptions.default)
      (authorize: Option[Record] => Consequence[Unit])(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
      _hit("entity-2", Vector(entity, id, options, authorize), ctx)
    override private[cncf] def upsert[T](entity: T, id: EntityId, options: EntityCreateOptions)
      (authorize: Option[Record] => Consequence[Unit], onsaved: CreateResult[T] => Consequence[Unit])
      (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
      _hit("entity-3", Vector(entity, id, options, authorize, onsaved), ctx)
    override private[cncf] def upsertVersioned[T](entity: T, id: EntityId, options: EntityCreateOptions, policy: EntityUpsertPolicy)
      (authorize: Option[Record] => Consequence[Unit])(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
      _hit("entity-4", Vector(entity, id, options, policy, authorize), ctx)
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] =
      _hit("entity-5", Vector(id), ctx)
    override def loadSnapshot[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntitySnapshot[T]]] =
      _hit("entity-6", Vector(id), ctx)
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] =
      _hit("entity-7", Vector(id), ctx)
    override private[cncf] def save[T](entity: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-8", Vector(entity), ctx)
    override private[cncf] def saveManaged[T](entity: T, executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-9", Vector(entity, executionPolicy), ctx)
    override def save[T](entity: T, expectedRevision: EntityRevision)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
      _hit("entity-10", Vector(entity, expectedRevision), ctx)
    override def save[T](entity: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
      _hit("entity-11", Vector(entity, expectedRevision, executionPolicy), ctx)
    override def saveDetached[T](entity: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] =
      _hit("entity-12", Vector(entity, expectedRevision, executionPolicy), ctx)
    override private[cncf] def update[T](changes: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-13", Vector(changes), ctx)
    override def update[T](changes: T, expectedRevision: EntityRevision)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
      _hit("entity-14", Vector(changes, expectedRevision), ctx)
    override def update[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
      _hit("entity-15", Vector(changes, expectedRevision, executionPolicy), ctx)
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] =
      _hit("entity-16", Vector(changes, expectedRevision, executionPolicy), ctx)
    override def updateById[P](id: EntityId, patch: P, expectedRevision: EntityRevision)
      (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
      _hit("entity-17", Vector(id, patch, expectedRevision), ctx)
    override def updateById[P](id: EntityId, patch: P, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
      _hit("entity-18", Vector(id, patch, expectedRevision, executionPolicy), ctx)
    override private[cncf] def updateByIdManaged[P](id: EntityId, patch: P, executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[Record] =
      _hit("entity-19", Vector(id, patch, executionPolicy), ctx)
    override private[cncf] def updateByIdManagedAuthoritative[P](id: EntityId, patch: P, executionPolicy: EntityMutationExecutionPolicy,
      managedMutationBase: EntityStore.ManagedMutationBase)(using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityStore.ManagedRecordMutationResult] =
      _hit("entity-20", Vector(id, patch, executionPolicy, managedMutationBase), ctx)
    override def updateByIdDetached[P](id: EntityId, patch: P, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[Record]] =
      _hit("entity-21", Vector(id, patch, expectedRevision, executionPolicy), ctx)
    override private[cncf] def updateByIdUnversioned[P](id: EntityId, patch: P)
      (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-22", Vector(id, patch), ctx)
    override private[cncf] def conditionalTransition[R, P, S](command: EntityConditionalTransitionCommand[R, P, S])
      (using ctx: ExecutionContext): Consequence[EntityConditionalTransitionExecutionResult[R, S]] =
      _hit("entity-23", Vector(command), ctx)
    override def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-24", Vector(id), ctx)
    override def restore(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-25", Vector(id), ctx)
    override def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] =
      _hit("entity-26", Vector(id), ctx)
    override def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
      _hit("entity-27", Vector(query), ctx)
    override def searchInternal[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
      _hit("entity-28", Vector(query), ctx)
    override def uniqueValueExists[T](collection: EntityCollectionId, fieldname: String, value: String, excludeid: Option[EntityId],
      scope: EntityIdentityScope, includeentityidentropy: Boolean)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean] =
      _hit("entity-29", Vector(collection, fieldname, value, excludeid, scope, includeentityidentropy), ctx)
    override def resolveIdentity[T](collection: EntityCollectionId, value: String, fieldnames: Vector[String],
      includeentityidentropy: Boolean, scope: EntityIdentityScope)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]] =
      _hit("entity-30", Vector(collection, value, fieldnames, includeentityidentropy, scope), ctx)
  }
  private final class BlobProbe(fixture: Fixture, failure: Consequence[Nothing]) extends BlobStore {
    val calls = scala.collection.mutable.ArrayBuffer.empty[(String, Vector[Any], Int)]
    def name: String = "blob-probe"
    private def _hit[A](name: String, arguments: Vector[Any]): Consequence[A] = {
      calls += ((name, arguments, fixture.node.activeLeaseCount)); failure
    }
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = _hit("put", Vector(request, payload))
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = _hit("get", Vector(ref))
    def delete(ref: BlobStorageRef): Consequence[Unit] = _hit("delete", Vector(ref))
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = _hit("accessUrl", Vector(ref))
    def status(): Consequence[BlobStoreStatus] = _hit("status", Vector.empty)
  }
}
