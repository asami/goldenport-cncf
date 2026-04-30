package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.operation.{CmlOperationAssociationBinding, CmlOperationChildEntityBinding}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Operation-facing helper for creating child Entity records after a parent
 * operation returns or supplies the parent Entity id.
 *
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ChildEntityBindingSummary(
  sourceEntityId: String,
  entityName: String,
  createdIds: Vector[String]
) {
  def toRecord: Record =
    Record.dataAuto(
      "sourceEntityId" -> sourceEntityId,
      "entityName" -> entityName,
      "createdIds" -> createdIds,
      "createdCount" -> createdIds.size
    )
}

final class ChildEntityBindingWorkflow(
  component: Component
) {
  def createChildren(
    binding: CmlOperationChildEntityBinding,
    request: Request,
    response: OperationResponse
  )(using ExecutionContext): Consequence[ChildEntityBindingSummary] =
    ChildEntityBindingWorkflow.extract(binding, request).flatMap {
      case Vector() =>
        Consequence.success(ChildEntityBindingSummary("", binding.entityName, Vector.empty))
      case rows =>
        for {
          sourceid <- ChildEntityBindingWorkflow.resolveSourceEntityId(binding, request, response)
          collection <- _entity_collection(binding.entityName)
          created <- _create_children(collection, binding, sourceid, rows)
        } yield ChildEntityBindingSummary(sourceid, binding.entityName, created.map(_.value))
    }.recoverWith { conclusion =>
      ChildEntityBindingWorkflow.resolveSourceEntityId(binding, request, response) match {
        case Consequence.Success(sourceid) =>
          _compensate_parent(binding, sourceid)
            .flatMap(_ => Consequence.Failure[ChildEntityBindingSummary](conclusion))
        case Consequence.Failure(_) =>
          Consequence.Failure[ChildEntityBindingSummary](conclusion)
      }
    }

  def compensate(
    binding: CmlOperationChildEntityBinding,
    summary: ChildEntityBindingSummary
  )(using ExecutionContext): Consequence[Unit] =
    if (summary.sourceEntityId.isEmpty && summary.createdIds.isEmpty)
      Consequence.unit
    else
      for {
        collection <- _entity_collection(summary.entityName)
        ids <- summary.createdIds.foldLeft(Consequence.success(Vector.empty[EntityId])) { (z, value) =>
          z.flatMap(xs => EntityId.parse(value).map(xs :+ _))
        }
        _ <- _cleanup_children(collection, ids)
        _ <-
          if (summary.sourceEntityId.nonEmpty)
            _compensate_parent(binding, summary.sourceEntityId)
          else
            Consequence.unit
      } yield ()

  private def _create_children(
    collection: EntityCollection[?],
    binding: CmlOperationChildEntityBinding,
    sourceEntityId: String,
    rows: Vector[Record]
  )(using ExecutionContext): Consequence[Vector[EntityId]] =
    rows.zipWithIndex.foldLeft(Consequence.success(Vector.empty[EntityId])) {
      case (z, (row, index)) =>
        z.flatMap { created =>
          _prepare_child_record(collection, binding, sourceEntityId, row, index).flatMap { case (record, id) =>
            _ensure_child_absent(collection, id).flatMap { _ =>
              collection.putRecordSynced(record).map(_ => created :+ id).recoverWith { conclusion =>
                _cleanup_children(collection, created)
                  .flatMap(_ => Consequence.Failure[Vector[EntityId]](conclusion))
              }
            }
          }.recoverWith { conclusion =>
            _cleanup_children(collection, created)
              .flatMap(_ => Consequence.Failure[Vector[EntityId]](conclusion))
          }
        }
    }

  private def _ensure_child_absent(
    collection: EntityCollection[?],
    id: EntityId
  )(using ExecutionContext): Consequence[Unit] =
    collection.resolve(id) match {
      case Consequence.Success(_) =>
        Consequence.stateConflict(s"child entity already exists: ${id.value}")
      case Consequence.Failure(_) =>
        Consequence.unit
    }

  private def _prepare_child_record(
    collection: EntityCollection[?],
    binding: CmlOperationChildEntityBinding,
    sourceEntityId: String,
    row: Record,
    index: Int
  ): Consequence[(Record, EntityId)] =
    _validate_parent_field(binding, sourceEntityId, row).flatMap { _ =>
      val withParent = row ++ Record.dataAuto(binding.parentIdField -> sourceEntityId)
      val withSort = binding.sortOrderField match {
        case Some(field) if withParent.getAny(field).isEmpty =>
          withParent ++ Record.dataAuto(field -> index)
        case _ =>
          withParent
      }
      _child_id(collection, binding, withSort).map { case (record, id) =>
        record -> id
      }
    }

  private def _validate_parent_field(
    binding: CmlOperationChildEntityBinding,
    sourceEntityId: String,
    row: Record
  ): Consequence[Unit] =
    row.getAny(binding.parentIdField).map(_.toString.trim).filter(_.nonEmpty) match {
      case Some(value) if value != sourceEntityId =>
        Consequence.argumentInvalid(s"${binding.inputParameter}.${binding.parentIdField} must match parent Entity id")
      case _ =>
        Consequence.unit
    }

  private def _child_id(
    collection: EntityCollection[?],
    binding: CmlOperationChildEntityBinding,
    record: Record
  ): Consequence[(Record, EntityId)] = {
    val field = binding.childIdField.getOrElse("id")
    record.getAny(field).map(_.toString.trim).filter(_.nonEmpty) match {
      case Some(value) =>
        EntityId.parse(value).map(id => record -> id)
      case None if binding.childIdField.isDefined =>
        val id = EntityId(
          collection.descriptor.collectionId.major,
          collection.descriptor.collectionId.minor,
          collection.descriptor.collectionId
        )
        Consequence.success((record ++ Record.dataAuto(field -> id.value)) -> id)
      case None =>
        Consequence.argumentMissing(field)
    }
  }

  private def _entity_collection(
    entityName: String
  ): Consequence[EntityCollection[?]] =
    component.entitySpace.entityOption[Any](entityName).orElse {
      component.componentDescriptors
        .flatMap(_.entityRuntimeDescriptors)
        .find(x =>
          NamingConventions.equivalentByNormalized(x.entityName, entityName) ||
            NamingConventions.equivalentByNormalized(x.collectionId.name, entityName)
        )
        .flatMap(x => component.entitySpace.entityOption(x.collectionId))
    } match {
      case Some(collection) => Consequence.success(collection)
      case None => Consequence.operationNotFound(s"child entity collection:${entityName}")
    }

  private def _cleanup_children(
    collection: EntityCollection[?],
    ids: Vector[EntityId]
  )(using ExecutionContext): Consequence[Unit] =
    ids.foldLeft(Consequence.unit) { (z, id) =>
      z.flatMap { _ =>
        summon[ExecutionContext].entityStoreSpace.delete(UnitOfWorkOp.EntityStoreDelete(id))
          .map(_ => collection.evict(id))
      }
    }

  private def _compensate_parent(
    binding: CmlOperationChildEntityBinding,
    sourceEntityId: String
  )(using ExecutionContext): Consequence[Unit] =
    if (_should_compensate_parent(binding))
      EntityId.parse(sourceEntityId).flatMap { id =>
        summon[ExecutionContext].entityStoreSpace.delete(UnitOfWorkOp.EntityStoreDelete(id))
          .map { _ =>
            component.entitySpace.entityOption(id.collection).foreach(_.evict(id))
            component.entitySpace.entityNames.foreach { name =>
              component.entitySpace.entityOption[Any](name).foreach(_.evict(id))
            }
          }
      }
    else
      Consequence.unit

  private def _should_compensate_parent(
    binding: CmlOperationChildEntityBinding
  ): Boolean =
    binding.sourceEntityIdMode == CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult &&
      binding.failurePolicy == CmlOperationChildEntityBinding.FailurePolicyCompensateParentOnCreate
}

object ChildEntityBindingWorkflow {
  def extract(
    binding: CmlOperationChildEntityBinding,
    request: Request
  ): Consequence[Vector[Record]] = {
    val values = _values(request)
    _string_value(values, binding.inputParameter) match {
      case Some("") | None =>
        _records_value(values, binding.inputParameter)
      case Some(_) =>
        _records_value(values, binding.inputParameter)
    }
  }

  def resolveSourceEntityId(
    binding: CmlOperationChildEntityBinding,
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
        binding.sourceEntityIdParameters.iterator.flatMap(name => _string_value(values, name)).nextOption() match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.argumentMissing(binding.sourceEntityIdParameters.headOption.getOrElse("sourceEntityId"))
        }
      case _ =>
        Consequence.argumentInvalid("child entity binding sourceEntityIdMode is not automatic")
    }

  private def _response_record(response: OperationResponse): Consequence[Record] =
    response match {
      case OperationResponse.RecordResponse(record) => Consequence.success(record)
      case _ => Consequence.argumentInvalid("child entity binding requires RecordResponse")
    }

  private def _records_value(
    values: Vector[(String, Any)],
    name: String
  ): Consequence[Vector[Record]] =
    values.collectFirst { case (key, value) if key == name => value } match {
      case None =>
        Consequence.success(Vector.empty)
      case Some(xs: Vector[?]) =>
        _records(xs)
      case Some(xs: Seq[?]) =>
        _records(xs.toVector)
      case Some(record: Record) =>
        Consequence.success(Vector(record))
      case Some(other) =>
        Consequence.argumentInvalid(s"${name} must be a record or record sequence: ${other}")
    }

  private def _records(values: Vector[?]): Consequence[Vector[Record]] =
    values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Record])) {
      case (z, (record: Record, _)) =>
        z.map(_ :+ record)
      case (z, (other, index)) =>
        z.flatMap(_ => Consequence.argumentInvalid(s"child entity row[${index}] must be Record: ${other}"))
    }

  private def _values(request: Request): Vector[(String, Any)] =
    (request.arguments.map(x => x.name -> x.value) ++ request.properties.map(x => x.name -> x.value)).toVector

  private def _first_string(record: Record, names: Vector[String]): Option[String] =
    names.iterator.flatMap(name => record.getAny(name)).map(_.toString.trim).find(_.nonEmpty)

  private def _string_value(values: Vector[(String, Any)], name: String): Option[String] =
    values.collectFirst { case (key, value) if key == name => value.toString.trim }.filter(_.nonEmpty)
}
