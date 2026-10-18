package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.{EntityMutationExecutionPolicy, EntityPersistent, EntityRevisionCarrier, EntityStore, StandardEntityStore}
import org.simplemodeling.model.datatype.EntityRevision
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class Phase100DurableStoreMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Declared durable quota text" should {
    "retain exact canonical String bytes through native create and detached load" in {
      Given("a test-owned SQLite provider and the existing detached quota collection declaration")
      val root = Files.createDirectories(Path.of("target/phase100/durable-mapping"))
      val path = Files.createTempDirectory(root, "sql-").toAbsolutePath.resolve("quota.db")
      val provider = SqlDataStore.sqlite(path.toString)
      val context = ExecutionContext.test()
      context.dataStoreSpace.useDataStore(provider)
      given ExecutionContext = context
      var diagnostic = ""
      val store = new StandardEntityStore {
        override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
            (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
          val result = super.updateDetached(changes, expectedRevision, executionPolicy)
          result match {
            case Consequence.Failure(conclusion) => diagnostic = conclusion.causes.map(cause =>
              cause.display + " " + cause.observation.cause.descriptor.facets.mkString(",")).mkString("\n")
            case _ => ()
          }
          result
        }
      }
      val quota = new DurableJobStorageQuota(store, DurableJobStorageResourcePolicy.default)
      quota.resourceSnapshot() // Registers the existing collection; an absent ledger has no snapshot.
      val id = _take(DurableJobStorageQuotaEntity.entityId)
      val canonical = "  " + _take(DurableJobStorageQuotaCodec.encode(
        DurableJobStorageQuotaRecord(DurableJobStorageResourcePolicy.default, Vector.empty))) + "\n "
      val entity = DurableJobStorageQuotaEntity(id, canonical)
      try {
        When("the native create acknowledgement is read through the declared mapping in a detached carrier")
        _take(store.create(entity))
        val raw = _take(provider.load(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))).get
        val loaded = _take(store.loadDetached[DurableJobStorageQuotaEntity](id)).get

        Then("the physical and logical canonical values are the original String and the provider revision is retained")
        raw.getString("canonicalJson") shouldBe Some(canonical)
        loaded.entity shouldBe entity
        loaded.revision.value shouldBe 1L

        When("the original quota protocol reserves native capacity through the same mapped CAS route")
        _take(new DurableJobStore(store).load("probe", DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))))
        val reservation = quota.reserve("probe", "{}", None)

        Then("the operational ledger remains decodable after native update")
        withClue(diagnostic) { reservation shouldBe a[Consequence.Success[_]] }
        _take(quota.resourceSnapshot()).pendingreservations shouldBe 1

        When("the reserved Job becomes a physical row and native census reconciles its exact bytes")
        val jobid = _take(DurableJobStoreEntity.entityId("probe"))
        val job = DurableJobStoreEntity(jobid, "probe", "{}")
        _take(store.create(job))
        val jobrow = _take(provider.load(DataStore.CollectionId.EntityStore(jobid.collection), DataStore.EntryId(jobid))).get
        val binding = org.goldenport.cncf.entity.EntityRevisionBinding(
          org.goldenport.cncf.entity.EntityRevisionRepresentation.Detached)
        val observed = _take(binding.revision(jobrow))
        val decoded = _take(binding.decodeEntity(jobrow)(summon[EntityPersistent[DurableJobStoreEntity]].fromStoreRecord))
        val inventory = DurableJobStorageQuotaInventory.scan(DurableJobStorageResourcePolicy.default)

        Then("the direct physical inventory retains the quota row's identity and revision")
        decoded shouldBe job
        _take(inventory).map(_.jobid) shouldBe Vector("probe")

        When("the reserved lease is confirmed with the observed physical metadata")
        val lease = _take(reservation)
        val metadata = _take(DurableJobStorageQuotaInventory.metadata("probe", "{}", observed.value))
        _take(quota.confirm(lease, metadata))
        val confirmed = _take(quota.resourceSnapshot())

        Then("the native quota protocol consumes the pending reservation")
        confirmed.pendingreservations shouldBe 0
      } finally _take(provider.closeC())
    }
  }

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) =>
      fail((conclusion.show +: conclusion.causes.map(cause =>
        cause.display + " " + cause.observation.cause.descriptor.facets.mkString(","))).mkString("\n"))
  }
}
