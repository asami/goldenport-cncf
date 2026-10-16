package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.convert.StringEncodable
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.record.RecordKeyNaming
import org.goldenport.text.Presentable
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final case class EntityCreatePlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  record: Record,
  sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty
)

final case class EntityCreateResult(record: Record)

trait EntityCreateDataStore { self: DataStore =>
  def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities.empty

  def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] =
    EntityNativeMutationSupport.unsupported("entity-create", EntityMutationProviderFeature.AtomicCreate)
}

enum EntityCreateCheckpoint {
  case BeforeSideWrite(effect: EntityVersionedSideEffect)
  case BeforeRootWrite
  case BeforePublish
}

private[datastore] object EntityCreateSupport {
  def validate(plan: EntityCreatePlan): Consequence[Unit] =
    if (plan == null) Consequence.argumentMissing("plan")
    else if (plan.sideEffects == null) Consequence.argumentMissing("sideEffects")
    else for {
      _ <- _validate_collection("collection", plan.collection)
      _ <- _validate_entry("entryId", plan.entryId)
      _ <- validateRecord("record", plan.record, plan.entryId)
      _ <- plan.sideEffects.foldLeft(Consequence.unit) { (result, effect) =>
        result.flatMap { _ =>
          if (effect == null) Consequence.argumentMissing("sideEffects.effect")
          else for {
            _ <- _validate_collection("sideEffects.collection", effect.collection)
            _ <- _validate_entry("sideEffects.entryId", effect.entryId)
            _ <- effect match {
              case EntityVersionedSideEffect.Save(_, entry, record) => validateRecord("sideEffects.record", record, entry)
              case _: EntityVersionedSideEffect.Delete => Consequence.unit
            }
          } yield ()
        }
      }
    } yield ()

  def requireCapabilities(capabilities: EntityMutationProviderCapabilities, hasSideEffects: Boolean): Consequence[Unit] = {
    val required = Vector(EntityMutationProviderFeature.AtomicCreate, EntityMutationProviderFeature.AuthoritativeRecordResult) ++
      (if (hasSideEffects) Vector(EntityMutationProviderFeature.AtomicSideEffects) else Vector.empty)
    required.foldLeft(Consequence.unit) { (result, feature) =>
      result.flatMap { _ =>
        if (capabilities != null && capabilities.features != null && capabilities.supports(feature)) Consequence.unit
        else EntityNativeMutationSupport.unsupported("entity-create", feature)
      }
    }
  }

  def validateTargets(plan: EntityCreatePlan, collectionKey: DataStore.CollectionId => String): Consequence[Unit] = {
    val root = collectionKey(plan.collection) -> plan.entryId.print
    val sides = plan.sideEffects.map(effect => collectionKey(effect.collection) -> effect.entryId.print)
    if (sides.contains(root)) aliasedTarget
    else if (sides.distinct.size != sides.size) repeatedTarget
    else Consequence.unit
  }

  def aliasedTarget: Consequence[Unit] =
    Consequence.argumentPolicyViolation("sideEffects", "entity-create.root-isolation", "effects excluding the root", "root target")

  def repeatedTarget: Consequence[Unit] =
    Consequence.argumentInvalid("sideEffects", "unique physical targets", "duplicate targets")

  def withIdentity(record: Record, entryId: DataStore.EntryId): Record =
    if (record.fields.exists(_.key == "id")) record else record.upsertSingle("id", entryId.print)

  def validateResult(
    plan: EntityCreatePlan,
    result: EntityCreateResult,
    fieldName: String => String = identity,
    storageScalar: Any => String = scalar
  ): Consequence[EntityCreateResult] = {
    if (result == null || result.record == null) invalidResult
    else validateRecord("result.record", result.record, plan.entryId).flatMap { _ =>
      val record = result.record
      val id = _record_value(record, "id", fieldName)
      val revisions = Vector("revision", "cncf_revision").filter(plan.record.getAny(_).isDefined)
      if (!id.exists(value => scalar(value) == plan.entryId.print)) invalidResult
      else if (revisions.exists { name =>
        !_record_value(record, name, fieldName).exists(value => storageScalar(value) == storageScalar(plan.record.getAny(name).get))
      }) invalidResult
      else Consequence.success(result)
    }.recoverWith(_ => invalidResult)
  }

  def invalidResult[A]: Consequence[A] =
    EntityVersionedMutationFailure.transactionIndeterminate("Entity create returned an untrusted authoritative result")

  private def _record_value(record: Record, name: String, fieldname: String => String): Option[Any] = {
    val names = Set(name, fieldname(name), RecordKeyNaming.toSnakeColumnName(name), RecordKeyNaming.toCanonicalCamelName(name))
    record.fields.find(field => names.exists(_.equalsIgnoreCase(field.key))).map(_.value.single)
  }

  def scalar(value: Any): String = value match {
    case null => ""
    case Some(content) => scalar(content)
    case id: UniversalId => id.print
    case nominal: NominalScalar => scalar(nominal.value)
    case encoded: StringEncodable =>
      given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
      encoded.encode
    case other => Presentable.print(other)
  }

  def validateRecord(path: String, record: Record, entryId: DataStore.EntryId): Consequence[Unit] =
    if (record == null || record.fields == null) Consequence.argumentMissing(path)
    else if (record.fields.exists(field => field == null || field.value == null)) Consequence.argumentMissing(s"$path.field")
    else {
      val keys = record.fields.map(_.key)
      if (keys.distinct.size != keys.size) Consequence.argumentInvalid(path, "unique record keys", "duplicate keys")
      else keys.foldLeft(Consequence.unit)((result, key) => result.flatMap(_ => _validate_name(s"$path.key", key))).flatMap { _ =>
        if (keys.contains("id") && !record.getAny("id").exists(value => scalar(value) == entryId.print))
          Consequence.argumentInvalid(s"$path.id", entryId.print, "mismatched identity")
        else Consequence.unit
      }
    }

  private def _validate_collection(path: String, collection: DataStore.CollectionId): Consequence[Unit] = collection match {
    case null => Consequence.argumentMissing(path)
    case DataStore.CollectionId.Instance(name) => _validate_name(path, name)
    case DataStore.CollectionId.EntityStore(null) => Consequence.argumentMissing(path)
    case DataStore.CollectionId.EntityStore(id) => for {
      _ <- _validate_name(path, id.major)
      _ <- _validate_name(path, id.minor)
      _ <- _validate_name(path, id.name)
    } yield ()
  }

  private def _validate_entry(path: String, entry: DataStore.EntryId): Consequence[Unit] = entry match {
    case null => Consequence.argumentMissing(path)
    case DataStore.StringEntryId(id) => _validate_name(path, id)
    case DataStore.UniversalEntryId(null) => Consequence.argumentMissing(path)
    case other => _validate_name(path, other.print)
  }

  private def _validate_name(path: String, value: String): Consequence[Unit] =
    if (value == null || value.trim.isEmpty) Consequence.argumentMissing(path)
    else if (value.exists(_.isControl)) Consequence.argumentInvalid(path, "name without control characters", "invalid name")
    else Consequence.unit
}
