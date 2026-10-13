package org.goldenport.cncf.display

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.schema.WebColumn

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Inert metadata admission against the supplied immutable registration. */
private[display] object DisplayMutationDescriptorCodec {
  import DisplayMutation.*
  import DisplayMutationBinding.{Binding, Descriptor}
  import DisplayMutationJson.*
  import GeneratedLogicalUiAbi.ValueKind
  private val _operations = Vector("read", "query", "create", "update", "delete")

  def _encode[P](binding: Binding[P], descriptor: Descriptor): Consequence[Json] = {
    if (binding == null || descriptor == null) inputInvalidC("ELEMENT_INVALID", "binding/descriptor")
    else binding.descriptorC(descriptor.availability).flatMap { canonical =>
      if (!_same_selection(canonical.selection, descriptor.selection) || canonical.capabilities != descriptor.capabilities ||
        canonical.fields != descriptor.fields || _payload(canonical) != _payload(descriptor)) inputInvalidC("DEFINITION_MISMATCH", "descriptor")
      else Consequence.success(_payload(canonical))
    }
  }
  def _decode[P](binding: Binding[P], json: String): Consequence[Descriptor] = {
    if (binding == null) inputInvalidC("ELEMENT_INVALID", "binding")
    else for {
      parsed <- _parse(json)
      root <- _object(parsed, "$", Vector("schema", "version", "componentId", "resourceType", "definition",
        "provenance", "capabilities", "availability", "fields"))
      _ <- _protocol(root, DisplayMutationProtocol.descriptorSchema)
      _ <- _selection(root, binding.selection, true)
      _ <- _capabilities(_member(root, "capabilities"))
      states <- _availability(_member(root, "availability"))
      fields <- _array(root, "fields", "$")(_field)
      _ <- if (fields.map(_._1).distinct.size == fields.size) Consequence.success(())
        else inputInvalidC("FIELD_DUPLICATE", "$.fields")
      base <- binding.descriptorC(Availability.enabled)
      bound = base.fields.filter(_.bindingId.nonEmpty).map(_.location).toSet
      readonly = fields.collect { case (location, FieldEditability.ReadOnly) if bound.contains(location) => location }.toSet
      canonical <- binding.descriptorC(Availability(states(0), states(1), states(2), states(3), states(4), readonly))
      _ <- if (_payload(canonical) == parsed) Consequence.success(())
        else inputInvalidC("DEFINITION_MISMATCH", "descriptor")
    } yield canonical
  }
  private def _capabilities(json: Json): Consequence[Unit] = for {
    obj <- _object(json, "$.capabilities", _operations)
    _ <- _operations.foldLeft(Consequence.success(())) { (acc, name) => acc.flatMap { _ =>
      _string(obj, name, "$.capabilities").flatMap {
        case "supported" | "unsupported" => Consequence.success(())
        case _ => inputInvalidC("VALUE_INVALID", "$.capabilities." + name)
      }
    } }
  } yield ()
  private def _availability(json: Json): Consequence[Vector[AvailabilityState]] = for {
    obj <- _object(json, "$.availability", _operations)
    states <- _operations.foldLeft(Consequence.success(Vector.empty[AvailabilityState])) { (acc, name) =>
      acc.flatMap { states => _string(obj, name, "$.availability").flatMap {
        case "enabled" => Consequence.success(states :+ AvailabilityState.Enabled)
        case "disabled" => Consequence.success(states :+ AvailabilityState.Disabled)
        case "hidden" => Consequence.success(states :+ AvailabilityState.Hidden)
        case _ => inputInvalidC("VALUE_INVALID", "$.availability." + name)
      } }
    }
  } yield states
  private def _field(json: Json, path: String): Consequence[(FieldLocator, FieldEditability)] = for {
    obj <- _object(json, path, Vector("sectionId", "fieldId", "editability", "acceptedKinds", "web"), Vector("bindingId"))
    sectionid <- _string(obj, "sectionId", path)
    fieldid <- _string(obj, "fieldId", path)
    location = FieldLocator(sectionid, fieldid)
    _ <- if (_location_valid(location)) Consequence.success(()) else inputInvalidC("ID_INVALID", path)
    tag <- _string(obj, "editability", path)
    editability <- tag match {
      case "editable" => Consequence.success(FieldEditability.Editable)
      case "read-only" => Consequence.success(FieldEditability.ReadOnly)
      case _ => inputInvalidC("VALUE_INVALID", path + ".editability")
    }
    _ <- _optional(obj, "bindingId", path) { (value, entrypath) =>
      _string_value(value, entrypath).flatMap(text => if (_id_valid(text)) Consequence.success(text)
        else inputInvalidC("ID_INVALID", entrypath))
    }
    kinds <- _array(obj, "acceptedKinds", path) { (value, entrypath) =>
      _string_value(value, entrypath).flatMap(tag => ValueKind.values.find(_.tag == tag)
        .map(Consequence.success).getOrElse(inputInvalidC("VALUE_INVALID", entrypath)))
    }
    _ <- if (kinds.nonEmpty && kinds.distinct.size == kinds.size) Consequence.success(())
      else inputInvalidC("VALUE_INVALID", path + ".acceptedKinds")
    _ <- _web(_member(obj, "web"), path + ".web")
  } yield (location, editability)
  private def _web(json: Json, path: String): Consequence[Unit] = for {
    obj <- _object(json, path, Vector("hidden", "system", "readonly", "multiple", "values", "validation"),
      Vector("controlType", "required", "placeholder", "help"))
    _ <- Vector("hidden", "system", "readonly", "multiple").foldLeft(Consequence.success(())) { (acc, name) =>
      acc.flatMap(_ => _boolean_value(_member(obj, name), path + "." + name).map(_ => ()))
    }
    _ <- Vector("controlType", "placeholder", "help").foldLeft(Consequence.success(())) { (acc, name) =>
      acc.flatMap(_ => _optional(obj, name, path)(_string_value).map(_ => ()))
    }
    _ <- _optional(obj, "required", path)(_boolean_value)
    _ <- _array(obj, "values", path)(_string_value)
    _ <- _validation(_member(obj, "validation"), path + ".validation")
  } yield ()
  private def _validation(json: Json, path: String): Consequence[Unit] = for {
    obj <- _object(json, path, Vector.empty, Vector("min", "max", "step", "minLength", "maxLength", "pattern"))
    _ <- Vector("min", "max", "step").foldLeft(Consequence.success(())) { (acc, name) => acc.flatMap { _ =>
      _optional(obj, name, path) { (value, entrypath) => _string_value(value, entrypath).flatMap(_decimal(_, entrypath)) }
        .map(_ => ())
    } }
    _ <- Vector("minLength", "maxLength").foldLeft(Consequence.success(())) { (acc, name) => acc.flatMap { _ =>
      _optional(obj, name, path) { (value, entrypath) => _integer(value, entrypath, BigInt(0), BigInt(Int.MaxValue)) }
        .map(_ => ())
    } }
    _ <- _optional(obj, "pattern", path)(_string_value)
  } yield ()
  private def _payload(descriptor: Descriptor): Json = Json.fromFields(
    _prefix(DisplayMutationProtocol.descriptorSchema) ++ _selection_json(descriptor.selection, true) ++
      Vector("capabilities" -> _capabilities_json(descriptor.capabilities),
        "availability" -> _availability_json(descriptor.availability),
        "fields" -> Json.fromValues(descriptor.fields.map(_field_json))))
  private def _capabilities_json(capabilities: Capabilities): Json = Json.fromFields(
    _operations.zip(Vector(capabilities.read, capabilities.query, capabilities.create, capabilities.update, capabilities.delete))
      .map { case (name, support) => name -> _text(support match {
        case Support.Supported => "supported"
        case Support.Unsupported => "unsupported"
      }) })
  private def _availability_json(availability: Availability): Json = Json.fromFields(
    _operations.zip(Vector(availability.read, availability.query, availability.create, availability.update, availability.delete))
      .map { case (name, state) => name -> _text(state match {
        case AvailabilityState.Enabled => "enabled"
        case AvailabilityState.Disabled => "disabled"
        case AvailabilityState.Hidden => "hidden"
      }) })
  private def _field_json(field: FieldDescriptor): Json = Json.fromFields(
    Vector("sectionId" -> _text(field.location.sectionId), "fieldId" -> _text(field.location.fieldId),
      "editability" -> _text(if (field.editability == FieldEditability.Editable) "editable" else "read-only")) ++
      field.bindingId.toVector.map(id => "bindingId" -> _text(id)) ++
      Vector("acceptedKinds" -> Json.fromValues(field.acceptedKinds.map(kind => _text(kind.tag))), "web" -> _web_json(field.web)))
  private def _web_json(web: WebColumn): Json = {
    val hints = web.validation
    val validation = Json.fromFields(
      hints.min.toVector.map(value => "min" -> _text(value.bigDecimal.toString)) ++
      hints.max.toVector.map(value => "max" -> _text(value.bigDecimal.toString)) ++
      hints.step.toVector.map(value => "step" -> _text(value.bigDecimal.toString)) ++
      hints.minLength.toVector.map(value => "minLength" -> Json.fromInt(value)) ++
      hints.maxLength.toVector.map(value => "maxLength" -> Json.fromInt(value)) ++
      hints.pattern.toVector.map(value => "pattern" -> _text(value)))
    Json.fromFields(Vector("hidden" -> Json.fromBoolean(web.hidden), "system" -> Json.fromBoolean(web.system),
      "readonly" -> Json.fromBoolean(web.readonly), "multiple" -> Json.fromBoolean(web.multiple),
      "values" -> Json.fromValues(web.values.map(_text)), "validation" -> validation) ++
      web.controlType.toVector.map(value => "controlType" -> _text(value)) ++
      web.required.toVector.map(value => "required" -> Json.fromBoolean(value)) ++
      web.placeholder.toVector.map(value => "placeholder" -> _text(value)) ++
      web.help.toVector.map(value => "help" -> _text(value)))
  }
}
