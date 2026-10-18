package org.goldenport.cncf.entity

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.{Field, Record}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Stateless declared row conversion and logical application-hook stages.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EntityStorePersistenceStage {
  def _validate(collection: EntityCollectionId, mapping: Option[EntityStoreRowMapping]): Consequence[Unit] =
    _mapping(collection, mapping, "validate").map(_ => ())

  def _validate_provider(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping]
  )(using ctx: ExecutionContext): Consequence[Unit] = mapping match {
    case None => Consequence.unit
    case _ => _provider_mapping(collection, mapping).map(_ => ())
  }

  def _provider_row(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    record: Record
  )(using ctx: ExecutionContext): Consequence[Record] = mapping match {
    case None => Consequence.success(record)
    case _ => _provider_mapping(collection, mapping).flatMap { case (declared, provider) =>
      if (record == null)
        _failure(collection, "<row>", "provider-key", "invalid-row-shape")
      else {
        Consequence {
          val names = declared.fields.map(field => provider.recordKey(field.storageName) -> field.storageName).toMap
          Record(record.fields.map(field => Field(names.getOrElse(field.key, field.key), field.value)))
        }
      }
    }
  }

  private def _provider_mapping(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping]
  )(using ctx: ExecutionContext): Consequence[(EntityStoreRowMapping, DataStore)] =
    _mapping(collection, mapping, "provider-key").flatMap { declared =>
      ctx.dataStoreSpace.dataStore(DataStore.CollectionId.EntityStore(collection)).flatMap { provider =>
        Consequence {
          declared.copy(
            fields = declared.fields.map(field => field.copy(storageName = provider.recordKey(field.storageName))),
            physicalFields = declared.physicalFields.map(provider.recordKey))
        }.flatMap(_.validate(collection)).map(_ => (declared, provider))
      }
    }

  def _encode(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    source: => Consequence[Record]
  ): Consequence[Record] = {
    _mapping(collection, mapping, "encode").flatMap { declared =>
      _hook(collection, "logical-write", source).flatMap(declared.encode(collection, _))
    }
  }

  def _encode_changes(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    source: => Consequence[Record]
  ): Consequence[Record] = {
    _mapping(collection, mapping, "encode-patch").flatMap { declared =>
      _hook(collection, "logical-write", source).flatMap(declared._encode_update(collection, _))
    }
  }

  def _admit(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    record: Record
  )(admit: Record => Consequence[Record]): Consequence[Record] = {
    _mapping(collection, mapping, "decode").flatMap { declared =>
      declared.decode(collection, record).flatMap { logical =>
        _hook(collection, "logical-admit", admit(logical))
      }
    }
  }

  def _decode_admitted[A](
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    record: Record
  )(decode: Record => Consequence[A]): Consequence[A] = {
    _mapping(collection, mapping, "decode-admitted").flatMap { _ =>
      _hook(collection, "logical-decode", decode(record))
    }
  }

  private def _mapping(
    collection: EntityCollectionId,
    mapping: Option[EntityStoreRowMapping],
    stage: String
  ): Consequence[EntityStoreRowMapping] = {
    if (collection == null || mapping == null) {
      _failure(collection, "<mapping>", stage, "missing-mapping")
    } else {
      mapping match {
        case Some(declared) if declared != null => declared.validate(collection).map(_ => declared)
        case _ => _failure(collection, "<mapping>", stage, "missing-mapping")
      }
    }
  }

  private def _hook[A](
    collection: EntityCollectionId,
    stage: String,
    source: => Consequence[A]
  ): Consequence[A] = {
    try {
      source match {
        case null | Consequence.Success(null) =>
          _failure[A](collection, "<hook>", stage, "application-hook-failure")
        case outcome => _decorate(outcome, collection, "<hook>", stage)
      }
    } catch {
      case NonFatal(_) => _failure[A](collection, "<hook>", stage, "application-hook-failure")
    }
  }

  private def _failure[A](
    collection: EntityCollectionId,
    field: String,
    stage: String,
    reason: String
  ): Consequence[A] = {
    val result = Consequence.valueInvalid[A](s"entity store $stage: $reason").leftMap { conclusion =>
      val observation = conclusion.observation
      conclusion.copy(observation = observation.copy(
        cause = observation.cause.addFacet(Descriptor.Facet.Reason(reason))))
    }
    _decorate(result, collection, field, stage)
  }

  private def _decorate[A](
    result: Consequence[A],
    collection: EntityCollectionId,
    field: String,
    stage: String
  ): Consequence[A] = {
    val facets = Vector[Descriptor.Facet](
      Descriptor.Facet.Policy("entity.store.logical-type"),
      Descriptor.Facet.Id(Option(collection).map(_.print).getOrElse("<missing-collection>")),
      Descriptor.Facet.FieldPath(field), Descriptor.Facet.Key(field), Descriptor.Facet.State(stage))
    result.leftMap { conclusion =>
      conclusion.causes.map { cause =>
        val observation = cause.observation
        val descriptor = observation.cause.descriptor
        cause.copy(observation = observation.copy(cause = observation.cause.copy(
          descriptor = descriptor.copy(facets = descriptor.facets ++ facets))))
      }.reduce(_ ++ _)
    }
  }
}
