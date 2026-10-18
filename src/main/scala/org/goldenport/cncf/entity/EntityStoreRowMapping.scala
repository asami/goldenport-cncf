package org.goldenport.cncf.entity

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.Multiplicity
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.directive.Update

/*
 * Pure declared logical-field / physical-row conversion. No provider activation.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityStoreRowMapping(
  fields: Vector[EntityStoreField],
  physicalFields: Set[String] = Set.empty
) {
  def validate(collection: EntityCollectionId): Consequence[Unit] = _validate(collection, "validate")

  def encode(collection: EntityCollectionId, record: Record): Consequence[Record] =
    _convert(collection, record, "encode")

  def encodePatch(collection: EntityCollectionId, record: Record): Consequence[Record] =
    _convert(collection, record, "encode-patch")

  def decode(collection: EntityCollectionId, record: Record): Consequence[Record] =
    _convert(collection, record, "decode")

  private[entity] def _encode_update(collection: EntityCollectionId, record: Record): Consequence[Record] = {
    _validate(collection, "encode-patch").flatMap { _ =>
      _validate_row(collection, record, "encode-patch", rawupdate = true).flatMap { _ =>
        val invalidvalues = record.fields.flatMap { input =>
          input.value.single match {
            case Update.SetValue(null) =>
              val declaration = fields.find(_.logicalName == input.key)
              Some(declaration match {
                case Some(field) => _failure[Unit](collection, Some(field), "encode-patch", "explicit-clear-required")
                case None => _decorate_names(_invalid[Unit]("explicit-clear-required", "encode-patch"),
                  collection, input.key, input.key, None, "encode-patch")
              })
            case _ => None
          }
        }
        Consequence.zipN(invalidvalues).flatMap { _ =>
          _decorate(Consequence(Update.toChangesRecord(record)), collection, None, "encode-patch")
            .flatMap(encodePatch(collection, _))
        }
      }
    }
  }

  private def _validate(collection: EntityCollectionId, stage: String): Consequence[Unit] = {
    if (collection == null || fields == null || physicalFields == null) {
      _failure(collection, None, stage, "missing-mapping")
    } else {
      val declarations = fields.map { field =>
        if (field == null) {
          _failure[Unit](collection, Some(field), stage, "missing-mapping")
        } else if (!_name_valid(field.logicalName) || !_name_valid(field.storageName)) {
          _failure[Unit](collection, Some(field), stage, "incompatible-mapping")
        } else if (field.domain == null || field.binding == null ||
          field.domain.multiplicity == null ||
          field.domain.constraints == null || field.domain.constraints.exists(_ == null)) {
          _failure[Unit](collection, Some(field), stage, "missing-mapping")
        } else {
          _decorate(field.binding._validate_binding, collection, Some(field), stage).flatMap { _ =>
            if (field.domain.datatype == null) {
              _failure[Unit](collection, Some(field), stage, "missing-mapping")
            } else if (field.domain.datatype != field.binding.datatype || !_multiplicity_valid(field)) {
              _failure[Unit](collection, Some(field), stage, "incompatible-mapping")
            } else {
              Consequence.success(())
            }
          }
        }
      }
      Consequence.zipN(declarations).flatMap { _ =>
        val logicalnames = fields.map(_.logicalName)
        val storagenames = fields.map(_.storageName)
        if (logicalnames.distinct.size != logicalnames.size ||
          storagenames.distinct.size != storagenames.size ||
          physicalFields.exists(x => !_name_valid(x)) ||
          physicalFields.exists(x => logicalnames.contains(x) || storagenames.contains(x))) {
          _failure(collection, None, stage, "incompatible-mapping")
        } else {
          Consequence.success(())
        }
      }
    }
  }

  private def _name_valid(name: String): Boolean =
    name != null && name.trim.nonEmpty && name == name.trim

  private def _range_valid(range: Multiplicity.Range): Boolean =
    range != null && range.from >= 0 && range.to >= range.from

  private def _multiplicity_valid(field: EntityStoreField): Boolean = {
    field.domain.multiplicity match {
      case Multiplicity.One | Multiplicity.ZeroOne => !field.binding._is_array
      case Multiplicity.ZeroMore | Multiplicity.OneMore => field.binding._is_array
      case range: Multiplicity.Range => field.binding._is_array && _range_valid(range)
      case Multiplicity.Ranges(ranges) =>
        field.binding._is_array && ranges != null && ranges.nonEmpty &&
          ranges.forall(x => x != null && x.toList.forall(_range_valid))
    }
  }

  private def _nullable(field: EntityStoreField): Boolean = {
    field.domain.multiplicity match {
      case Multiplicity.ZeroOne | Multiplicity.ZeroMore => true
      case Multiplicity.Range(from, _) => from == 0
      case Multiplicity.Ranges(ranges) => ranges.flatMap(_.toList).exists(_.from == 0)
      case _ => false
    }
  }

  private def _cardinality_valid(field: EntityStoreField, size: Int): Boolean = {
    field.domain.multiplicity match {
      case Multiplicity.ZeroMore => true
      case Multiplicity.OneMore => size > 0
      case Multiplicity.Range(from, to) => size >= from && size <= to
      case Multiplicity.Ranges(ranges) => ranges.flatMap(_.toList).exists(x => size >= x.from && size <= x.to)
      case _ => false
    }
  }

  private def _convert(collection: EntityCollectionId, record: Record, stage: String): Consequence[Record] = {
    _validate(collection, stage).flatMap { _ =>
      _validate_row(collection, record, stage).flatMap { _ =>
        val reading = stage == "decode"
        val declarations = fields.map(x => (if (reading) x.storageName else x.logicalName) -> x).toMap
        val missing = if (stage == "encode-patch") Vector.empty else fields.filter { field =>
          val key = if (reading) field.storageName else field.logicalName
          !record.fields.exists(_.key == key) && !_nullable(field)
        }.map(field => _failure[Option[Field]](collection, Some(field), stage, "missing-required-field"))
        val converted = record.fields.map { input =>
          declarations.get(input.key) match {
            case None => Consequence.success(Some(input))
            case Some(field) =>
              val outputname = if (reading) field.logicalName else field.storageName
              _field_value(collection, field, input.value.single, stage).map {
                _.map(value => Field(outputname, Field.Value.Single(value)))
              }
          }
        }
        Consequence.zipN(converted ++ missing).map(x => Record(x.toVector.flatten))
      }
    }
  }

  private def _validate_row(
    collection: EntityCollectionId,
    record: Record,
    stage: String,
    rawupdate: Boolean = false
  ): Consequence[Unit] = {
    if (record == null) {
      _failure(collection, None, stage, "invalid-row-shape")
    } else if (record.fields.exists(x => !_name_valid(x.key)) ||
      record.fields.map(_.key).distinct.size != record.fields.size) {
      _failure(collection, None, stage, "invalid-row-shape")
    } else {
      val reading = stage == "decode"
      val declarednames = fields.map(x => if (reading) x.storageName else x.logicalName).toSet
      val undeclared = record.fields.filterNot(x => declarednames.contains(x.key) || physicalFields.contains(x.key))
      Consequence.zipN(undeclared.map { input =>
        // An undeclared key is known declaration context, never a payload value.
        _decorate_names(_invalid[Unit]("missing-mapping", stage), collection,
          input.key, input.key, None, stage)
      }).flatMap { _ =>
        val invalidmarkers = if (rawupdate) Vector.empty else record.fields.flatMap { input =>
          fields.find(x => (if (reading) x.storageName else x.logicalName) == input.key).flatMap { field =>
            val value = input.value.single match {
              case Some(actual) if !reading && field.domain.multiplicity == Multiplicity.ZeroOne => actual
              case actual => actual
            }
            value match {
              case Update.SetNull if stage == "encode-patch" => None
              case _: Update[?] => Some(_failure[Unit](collection, Some(field), stage, "invalid-row-shape"))
              case _ => None
            }
          }
        }
        Consequence.zipN(invalidmarkers).map(_ => ())
      }
    }
  }

  private def _field_value(
    collection: EntityCollectionId,
    field: EntityStoreField,
    value: Any,
    stage: String
  ): Consequence[Option[Any]] = {
    if (stage != "decode" && field.domain.multiplicity == Multiplicity.ZeroOne) {
      value match {
        case None => Consequence.success(None)
        case Some(actual) => _present_value(collection, field, actual, stage)
        case _ => _present_value(collection, field, value, stage)
      }
    } else {
      _present_value(collection, field, value, stage)
    }
  }

  private def _present_value(
    collection: EntityCollectionId,
    field: EntityStoreField,
    value: Any,
    stage: String
  ): Consequence[Option[Any]] = {
    val nullable = _nullable(field)
    value match {
      case Update.SetNull if stage == "encode-patch" =>
        if (nullable) Consequence.success(Some(Update.SetNull))
        else _failure(collection, Some(field), stage, "null-not-allowed")
      case _: Update[?] => _failure(collection, Some(field), stage, "invalid-row-shape")
      case _ =>
        val converted = if (stage == "decode") {
          field.binding._decode(value).flatMap { decoded =>
            _check_cardinality(collection, field, decoded, stage).map(_ => decoded)
          }
        } else {
          val adapted = _adapt_array(value, field.binding)
          _check_cardinality(collection, field, adapted, stage).flatMap { _ => field.binding._encode(adapted) }
        }
        _decorate(converted, collection, Some(field), stage).map(x => Some(x))
    }
  }

  private def _adapt_array(value: Any, binding: EntityStoreFieldBinding): Any = {
    if (!binding._is_array) {
      value
    } else {
      val values = value match {
        case vector: NonEmptyVector[?] => Some(vector.toVector)
        case sequence: scala.collection.Seq[?] => Some(sequence.toVector)
        case _ => None
      }
      values match {
        case Some(sequence) => sequence.map(x => _adapt_array(x, binding.elementBinding.get))
        case None => value
      }
    }
  }

  private def _check_cardinality(
    collection: EntityCollectionId,
    field: EntityStoreField,
    value: Any,
    stage: String
  ): Consequence[Unit] = {
    value match {
      case sequence: scala.collection.Seq[?] if field.binding._is_array &&
        !_cardinality_valid(field, sequence.size) =>
        _invalid("cardinality-mismatch", stage)
      case _ => Consequence.success(())
    }
  }

  private def _failure[A](
    collection: EntityCollectionId,
    field: Option[EntityStoreField],
    stage: String,
    reason: String
  ): Consequence[A] = {
    _decorate(_invalid[A](reason, stage), collection, field, stage)
  }

  private def _invalid[A](reason: String, stage: String): Consequence[A] = {
    Consequence.valueInvalid[A](s"entity store $stage: $reason").leftMap { conclusion =>
      val observation = conclusion.observation
      conclusion.copy(observation = observation.copy(
        cause = observation.cause.addFacet(Descriptor.Facet.Reason(reason))))
    }
  }

  private def _decorate[A](
    result: Consequence[A],
    collection: EntityCollectionId,
    field: Option[EntityStoreField],
    stage: String
  ): Consequence[A] = {
    val declaration = field.flatMap(Option.apply)
    val logicalname = declaration.map(x => Option(x.logicalName).getOrElse("<missing-logical-name>"))
      .getOrElse(if (field.isDefined) "<missing-field>" else "<mapping>")
    val storagename = declaration.map(x => Option(x.storageName).getOrElse("<missing-storage-name>"))
      .getOrElse(if (field.isDefined) "<missing-field>" else "<mapping>")
    val algorithm = declaration.flatMap(x => Option(x.binding)).map(_.storageCodecIdentity)
    _decorate_names(result, collection, logicalname, storagename, algorithm, stage)
  }

  private def _decorate_names[A](
    result: Consequence[A],
    collection: EntityCollectionId,
    logicalname: String,
    storagename: String,
    algorithm: Option[String],
    stage: String
  ): Consequence[A] = {
    val facets = Vector[Descriptor.Facet](
      Descriptor.Facet.Policy("entity.store.logical-type"),
      Descriptor.Facet.Id(Option(collection).map(_.print).getOrElse("<missing-collection>")),
      Descriptor.Facet.FieldPath(logicalname), Descriptor.Facet.Key(storagename),
      Descriptor.Facet.State(stage)) ++ algorithm.map(Descriptor.Facet.Algorithm.apply)
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
