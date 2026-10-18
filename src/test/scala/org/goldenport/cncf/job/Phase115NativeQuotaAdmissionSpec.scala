package org.goldenport.cncf.job

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityRevisionBinding,
  EntityRevisionRepresentation}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Staged admission, mapped creation and detached readback on actual managed native storage.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class Phase115NativeQuotaAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed native lifetime quota persistence" should {
    Vector("direct native", "measured payload native", "measured query native").foreach { route =>
      s"admit an empty store and preserve its mapped quota with detached revision one through $route" in {
      Given("a started native SQLite runtime and real managed lease with no published Job")
      DurableJobBackupFixtures.withFixture { fixture =>
        fixture.use {
          given ExecutionContext = fixture.context
          val writes: Option[AtomicInteger] = route match {
            case "direct native" => None
            case "measured payload native" =>
              val wrapper = new DurableJobOperationResponsePayloadFixtures.MeasuredSqlDataStore(fixture.sql.get)
              fixture.context.dataStoreSpace.useDataStore(wrapper)
              Some(wrapper.writes)
            case "measured query native" =>
              val wrapper = new DurableJobOperationResponseQueryFixtures.NativeSql(fixture.sql.get)
              fixture.context.dataStoreSpace.useDataStore(wrapper)
              Some(wrapper.writes)
          }
          val policy = DurableJobLifetimeQuotaPolicy.default
          val provider = new DurableJobLifetimeQuotaProvider(fixture.entity, policy)

          Then("the selected measured route starts with no writes")
          writes.foreach(_.get() shouldBe 0)

          When("the native quota provider admits its collections")
          val admission = provider.admit()
          Then("native quota admission succeeds")
          withClue(s"native quota admission: ${admission.toString}") {
            admission.isSuccess shouldBe true
          }
          writes.foreach(_.get() shouldBe 0)

          When("the admitted provider loads the as-yet absent quota")
          val absent = provider.load()
          Then("the load succeeds with no quota row")
          withClue(s"initial quota load: ${absent.toString}") {
            absent.isSuccess shouldBe true
            absent.toOption shouldBe Some(None)
          }
          writes.foreach(_.get() shouldBe 0)

          When("the provider proves that the Job collection is empty")
          val initialproof = provider.proveEmpty()
          Then("the bounded native empty-Job proof succeeds")
          withClue(s"initial empty-Job proof: ${initialproof.toString}") {
            initialproof.isSuccess shouldBe true
          }
          writes.foreach(_.get() shouldBe 0)

          Given("an empty quota record under the default finite policy")
          val quota = DurableJobLifetimeQuotaRecord(policy, Vector.empty)
          When("the actual quota codec encodes the record")
          val encoding = DurableJobLifetimeQuotaCodec.encode(quota)
          Then("encoding succeeds before its canonical text is extracted")
          withClue(s"quota codec encode: ${encoding.toString}") {
            encoding.isSuccess shouldBe true
          }
          val canonical = encoding.toOption.get

          When("the quota entity supplies its canonical identity")
          val identity = DurableJobLifetimeQuotaEntity.entityId
          Then("identity construction succeeds before the entity is constructed")
          withClue(s"quota entity identity: ${identity.toString}") {
            identity.isSuccess shouldBe true
          }
          val id = identity.toOption.get
          val entity = DurableJobLifetimeQuotaEntity(id, canonical)

          When("the actual create mapping produces the provider payload")
          val tc = summon[EntityPersistentCreate[DurableJobLifetimeQuotaEntity]]
          val mapping = EntityPersistentCreate._to_provider_record(tc, id.collection, entity)
          Then("the payload retains scalar identity and canonical JSON with exactly its declared keys")
          withClue(s"quota provider payload mapping: ${mapping.toString}") {
            mapping.isSuccess shouldBe true
          }
          val payload = mapping.toOption.get
          withClue(s"quota provider payload values: ${mapping.toString}") {
            payload.getString("id") shouldBe Some(id.value)
            payload.getString("canonicalJson") shouldBe Some(canonical)
            payload.fields.map(_.key).toSet shouldBe Set("id", "canonicalJson")
          }

          When("the EntityStore creates the quota through the managed native provider")
          val creation = fixture.entity.create(entity)
          Then("creation succeeds and returns its authoritative record")
          withClue(s"native quota create: ${creation.toString}") {
            creation.isSuccess shouldBe true
          }
          writes.foreach(_.get() shouldBe 1)
          val result = creation.toOption.get
          withClue(s"native quota create record: ${creation.toString}") {
            result.record.nonEmpty shouldBe true
          }

          When("the returned record is bound through the existing detached revision mapping")
          val binding = EntityRevisionBinding(EntityRevisionRepresentation.Detached)
            .detachedCarrier(result.record.get)(
              summon[EntityPersistent[DurableJobLifetimeQuotaEntity]].fromStoreRecord)
          Then("the detached carrier contains the created quota entity at revision one")
          withClue(s"created quota detached binding: ${binding.toString}") {
            binding.isSuccess shouldBe true
          }
          val created = binding.toOption.get
          withClue(s"created quota detached values: ${binding.toString}") {
            created.entity shouldBe entity
            created.revision.value shouldBe 1L
          }
          writes.foreach(_.get() shouldBe 1)

          When("the provider reads the created quota through the EntityStore mapping")
          val loading = provider.load()
          Then("the load succeeds with the original empty quota and detached entity at revision one")
          withClue(s"created quota load: ${loading.toString}") {
            loading.isSuccess shouldBe true
            loading.toOption.get.nonEmpty shouldBe true
          }
          val (loaded, restored) = loading.toOption.get.get
          withClue(s"created quota readback values: ${loading.toString}") {
            restored shouldBe quota
            loaded.entity shouldBe entity
            loaded.revision.value shouldBe 1L
          }
          writes.foreach(_.get() shouldBe 1)

          When("the provider proves Job emptiness after quota creation")
          val finalproof = provider.proveEmpty()
          Then("the empty-Job proof still succeeds because no Job was published")
          withClue(s"final empty-Job proof: ${finalproof.toString}") {
            finalproof.isSuccess shouldBe true
          }
          writes.foreach(_.get() shouldBe 1)
        }
      }
      }
    }
  }
}
