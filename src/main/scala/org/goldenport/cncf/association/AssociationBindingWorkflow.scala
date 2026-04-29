package org.goldenport.cncf.association

import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.CmlOperationAssociationBinding
import org.goldenport.id.UniversalId
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Operation-facing helper for creating Association records from existing
 * target Entity ids.
 *
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AssociationBindingPart(
  role: String,
  targetEntityId: EntityId,
  sortOrder: Option[Int]
)

final case class AssociationBindingSummary(
  sourceEntityId: String,
  associations: Vector[Record]
) {
  def toRecord: Record =
    Record.dataAuto(
      "sourceEntityId" -> sourceEntityId,
      "associations" -> associations,
      "associationCount" -> associations.size
    )
}

final case class AssociationBindingAttachResult(
  association: Association,
  created: Boolean
)

trait AssociationTargetValidator {
  def validate(
    targetKind: Option[String],
    id: EntityId
  )(using ExecutionContext): Consequence[Unit]
}

object AssociationTargetValidator {
  val entityStoreRecordExists: AssociationTargetValidator =
    new AssociationTargetValidator {
      def validate(
        targetKind: Option[String],
        id: EntityId
      )(using ctx: ExecutionContext): Consequence[Unit] =
        for {
          cid <- ctx.entityStoreSpace.dataStoreCollection(id)
          dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
          ds <- ctx.dataStoreSpace.dataStore(cid)
          record <- ds.load(cid, dsid)
          _ <- record match {
            case Some(_) => Consequence.unit
            case None => Consequence.entityNotFound(s"association target:${id.value}")
          }
        } yield ()
    }

  val unchecked: AssociationTargetValidator =
    new AssociationTargetValidator {
      def validate(
        targetKind: Option[String],
        id: EntityId
      )(using ExecutionContext): Consequence[Unit] =
        Consequence.unit
    }
}

final class AssociationBindingWorkflow(
  repository: AssociationRepository,
  storagePolicy: AssociationStoragePolicy = AssociationStoragePolicy.blobAttachmentDefault,
  targetValidator: AssociationTargetValidator = AssociationTargetValidator.entityStoreRecordExists
) {
  def attachExistingTargets(
    sourceEntityId: String,
    binding: CmlOperationAssociationBinding,
    request: Request
  )(using ExecutionContext): Consequence[AssociationBindingSummary] =
    for {
      parts <- AssociationBindingWorkflow.extract(binding, request)
      associations <- _attach_existing_targets(sourceEntityId, binding, parts)
    } yield AssociationBindingSummary(
      sourceEntityId = sourceEntityId,
      associations = associations.map(AssociationRecordCodec.toRecord)
    )

  def attachExistingTarget(
    sourceEntityId: String,
    domain: AssociationDomain,
    targetKind: Option[String],
    targetEntityId: EntityId,
    role: String,
    sortOrder: Option[Int],
    attributes: Map[String, String] = Map.empty
  )(using ExecutionContext): Consequence[Association] =
    attachExistingTargetResult(
      sourceEntityId = sourceEntityId,
      domain = domain,
      targetKind = targetKind,
      targetEntityId = targetEntityId,
      role = role,
      sortOrder = sortOrder,
      attributes = attributes
    ).map(_.association)

  def attachExistingTargetResult(
    sourceEntityId: String,
    domain: AssociationDomain,
    targetKind: Option[String],
    targetEntityId: EntityId,
    role: String,
    sortOrder: Option[Int],
    attributes: Map[String, String] = Map.empty
  )(using ExecutionContext): Consequence[AssociationBindingAttachResult] = {
    val collection = storagePolicy.collection(domain)
    for {
      _ <- targetValidator.validate(targetKind, targetEntityId)
      result <- repository.list(AssociationFilter(
        domain = domain,
        sourceEntityId = Some(sourceEntityId),
        targetEntityId = Some(targetEntityId.value),
        targetKind = targetKind,
        role = Some(role)
      )).flatMap {
        case existing +: _ =>
          Consequence.success(AssociationBindingAttachResult(existing, created = false))
        case _ =>
          repository.create(AssociationCreate(
            id = Some(_association_entity_id(collection)),
            associationId = UUID.randomUUID().toString,
            sourceEntityId = sourceEntityId,
            targetEntityId = targetEntityId.value,
            targetKind = targetKind,
            role = role,
            associationDomain = domain,
            sortOrder = sortOrder,
            attributes = attributes,
            collectionId = collection
          )).map(AssociationBindingAttachResult(_, created = true))
      }
    } yield result
  }

  private def _attach_existing_targets(
    sourceEntityId: String,
    binding: CmlOperationAssociationBinding,
    parts: Vector[AssociationBindingPart]
  )(using ExecutionContext): Consequence[Vector[Association]] = {
    val domain = AssociationDomain(binding.domain)
    val targetKind = Option(binding.targetKind).filter(_.nonEmpty)
    parts.foldLeft(Consequence.success(Vector.empty[AssociationBindingAttachResult])) { (z, part) =>
      z.flatMap { created =>
        attachExistingTargetResult(
          sourceEntityId = sourceEntityId,
          domain = domain,
          targetKind = targetKind,
          targetEntityId = part.targetEntityId,
          role = part.role,
          sortOrder = part.sortOrder
        ).map(created :+ _).recoverWith { conclusion =>
          _cleanup(created.filter(_.created).map(_.association))
            .flatMap(_ => Consequence.Failure[Vector[AssociationBindingAttachResult]](conclusion))
        }
      }
    }.map(_.map(_.association))
  }

  private def _cleanup(
    values: Vector[Association]
  )(using ExecutionContext): Consequence[Unit] =
    values.foldLeft(Consequence.unit) { (z, association) =>
      z.flatMap(_ => repository.delete(association).recover(_ => ()))
    }

  private def _association_entity_id(collection: EntityCollectionId): EntityId =
    EntityId(
      collection.major,
      collection.minor,
      collection,
      Some(UniversalId.StableTimestamp),
      Some(s"association_${UUID.randomUUID().toString.replace("-", "_")}")
    )
}

object AssociationBindingWorkflow {
  def extract(
    binding: CmlOperationAssociationBinding,
    request: Request
  ): Consequence[Vector[AssociationBindingPart]] = {
    val values = _values(request)
    binding.targetIdParameters.zipWithIndex.foldLeft(Consequence.success(Vector.empty[AssociationBindingPart])) {
      case (z, (name, index)) =>
        z.flatMap { acc =>
          _string(values, name) match {
            case Some(value) =>
              EntityId.parse(value).map { id =>
                acc :+ AssociationBindingPart(
                  role = _role(binding, name, index),
                  targetEntityId = id,
                  sortOrder = _sort_order(binding, values, name, index)
                )
              }
            case None =>
              Consequence.success(acc)
          }
        }
    }
  }

  def resolveSourceEntityId(
    binding: CmlOperationAssociationBinding,
    request: Request,
    response: OperationResponse
  ): Consequence[String] =
    binding.sourceEntityIdMode match {
      case CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult |
          CmlOperationAssociationBinding.SourceEntityIdModeResultField =>
        _response_record(response).flatMap { record =>
          _first_string(record, binding.sourceEntityIdResultFields)
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing(binding.sourceEntityIdResultFields.headOption.getOrElse("entity_id")))
        }
      case CmlOperationAssociationBinding.SourceEntityIdModeParameter =>
        val values = _values(request)
        binding.sourceEntityIdParameters.iterator.flatMap(name => _string(values, name)).nextOption() match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.argumentMissing(binding.sourceEntityIdParameters.headOption.getOrElse("sourceEntityId"))
        }
      case _ =>
        Consequence.argumentInvalid("association binding sourceEntityIdMode is not automatic")
    }

  private def _response_record(response: OperationResponse): Consequence[Record] =
    response match {
      case OperationResponse.RecordResponse(record) => Consequence.success(record)
      case _ => Consequence.argumentInvalid("association binding requires RecordResponse")
    }

  private def _role(
    binding: CmlOperationAssociationBinding,
    name: String,
    index: Int
  ): String =
    binding.roles match {
      case Vector(role) => role
      case roles if roles.isDefinedAt(index) => roles(index)
      case _ => name
    }

  private def _sort_order(
    binding: CmlOperationAssociationBinding,
    values: Vector[(String, Any)],
    name: String,
    index: Int
  ): Option[Int] =
    _int(values, s"${name}.sortOrder", s"${name}.sort_order").orElse {
      binding.sortOrderParameters.lift(index).flatMap(param => _int(values, param))
    }

  private def _values(request: Request): Vector[(String, Any)] =
    (request.arguments.map(x => x.name -> x.value) ++ request.properties.map(x => x.name -> x.value)).toVector

  private def _first_string(record: Record, names: Vector[String]): Option[String] =
    names.iterator.flatMap(name => record.getAny(name)).map(_.toString.trim).find(_.nonEmpty)

  private def _string(values: Vector[(String, Any)], name: String): Option[String] =
    values.collectFirst { case (key, value) if key == name => value.toString.trim }.filter(_.nonEmpty)

  private def _int(values: Vector[(String, Any)], names: String*): Option[Int] =
    names.iterator.flatMap(name => _string(values, name)).flatMap(_.toIntOption).nextOption()
}
