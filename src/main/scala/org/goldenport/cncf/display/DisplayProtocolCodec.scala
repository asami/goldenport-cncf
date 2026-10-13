package org.goldenport.cncf.display

import java.time.Instant
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import scala.util.control.NonFatal

/*
 * @since   Oct.  4, 2026
 * @version Oct.  5, 2026
 */
/** Pure strict read codec; trusted definitions remain the sole semantic admission source. */
object DisplayProtocolCodec {
  import DisplayModel.*
  import GeneratedLogicalUiAbi.{AdmittedDefinitions, Definition, PresentationRole}
  import DisplayProtocol.{Source, Diagnostic, CodecException}

  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _decimal_pattern = "(-?)([0-9]+)(?:\\.([0-9]+))?(?:E([+-]?[0-9]+))?".r

  def encodeC(source: Source, display: Display): Consequence[String] =
    encodeJsonC(source, display).map(_render)

  def encodeJsonC(source: Source, display: Display): Consequence[Json] = {
    if (source == null) _failure("INPUT_INVALID", "source")
    else if (display == null) _failure("INPUT_INVALID", "display")
    else Consequence.success(_payload(source, display))
  }

  def decodeC(expectedSource: Source, definitions: AdmittedDefinitions, json: String): Consequence[Display] = {
    if (expectedSource == null) _failure("INPUT_INVALID", "expectedSource")
    else if (definitions == null) _failure("INPUT_INVALID", "definitions")
    else if (json == null) _failure("INPUT_INVALID", "json")
    else {
      val parsed = try {
        _parser.parse(json).fold(_ => _failure[Json]("JSON_INVALID", "$"), Consequence.success)
      } catch { case NonFatal(_) => _failure[Json]("JSON_INVALID", "$") }
      parsed.flatMap { value =>
        if (!value.isObject) _failure("JSON_INVALID", "$")
        else _decode(expectedSource, definitions, value)
      }
    }
  }

  private def _failure[A](code: String, path: String): Consequence[A] = {
    val reason = code match {
      case "INPUT_INVALID" => "expected nonnull valid input"
      case "JSON_INVALID" => "expected strict JSON object without duplicate members"
      case "MEMBER_INVALID" => "expected exact members and nonnull declared primitive"
      case "PROTOCOL_INCOMPATIBLE" => "expected supported protocol descriptor"
      case "SOURCE_MISMATCH" => "expected exact trusted source"
      case "ABI_INCOMPATIBLE" => "expected exact trusted ABI descriptor"
      case "CONTRACT_INCOMPATIBLE" => "expected exact trusted semantic descriptor"
      case "DEFINITION_MISMATCH" => "expected exact trusted definition metadata"
      case "MODEL_KIND_INVALID" => "expected closed model kind"
      case "VALUE_KIND_INVALID" => "expected closed value kind"
      case "ROLE_INVALID" => "expected closed presentation role"
      case "AVAILABILITY_INVALID" => "expected closed inert availability"
      case "RESOURCE_INVALID" => "expected canonical typed resource scalar"
      case "VALUE_INVALID" => "expected canonical typed value scalar"
    }
    Consequence.fail(Taxonomy.argumentInvalid,
      new CodecException(Diagnostic("DSP96_WIRE_" + code, path, reason)))
  }

  private def _object(json: Json, path: String, required: Vector[String], optional: Vector[String] = Vector.empty): Consequence[JsonObject] = {
    json.asObject match {
      case Some(obj) if required.forall(obj.contains) && obj.keys.forall(key => required.contains(key) || optional.contains(key)) =>
        Consequence.success(obj)
      case _ => _failure("MEMBER_INVALID", path)
    }
  }
  private def _member(obj: JsonObject, name: String): Json = obj(name).getOrElse(Json.Null)
  private def _string(obj: JsonObject, name: String, path: String): Consequence[String] =
    _member(obj, name).asString.map(Consequence.success).getOrElse(_failure("MEMBER_INVALID", path + "." + name))
  private def _expected_string(obj: JsonObject, name: String, expected: String, path: String, code: String): Consequence[Unit] = {
    _string(obj, name, path).flatMap(value =>
      if (value == expected) Consequence.success(()) else _failure(code, path + "." + name))
  }
  private def _version(obj: JsonObject, expected: Int, path: String, code: String): Consequence[Unit] = {
    try {
      _member(obj, "version").asNumber.flatMap(_.toBigDecimal).flatMap(_.toBigIntExact) match {
        case Some(value) if value == expected => Consequence.success(())
        case Some(_) => _failure(code, path + ".version")
        case None => _failure("MEMBER_INVALID", path + ".version")
      }
    } catch { case NonFatal(_) => _failure("MEMBER_INVALID", path + ".version") }
  }
  private def _array[A](obj: JsonObject, name: String, path: String)(decode: (Json, String) => Consequence[A]): Consequence[Vector[A]] = {
    val arraypath = path + "." + name
    _member(obj, name).asArray match {
      case None => _failure("MEMBER_INVALID", arraypath)
      case Some(values) => values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[A])) { case (acc, (value, index)) =>
        acc.flatMap { result =>
          val entrypath = s"$arraypath[$index]"
          if (value.isNull) _failure("MEMBER_INVALID", entrypath)
          else decode(value, entrypath).map(result :+ _)
        }
      }
    }
  }
  private def _optional[A](obj: JsonObject, name: String, path: String)(decode: (Json, String) => Consequence[A]): Consequence[Option[A]] = {
    obj(name) match {
      case None => Consequence.success(None)
      case Some(value) if value.isNull => _failure("MEMBER_INVALID", path + "." + name)
      case Some(value) => decode(value, path + "." + name).map(Some(_))
    }
  }

  private def _decode(source: Source, definitions: AdmittedDefinitions, json: Json): Consequence[Display] = {
    for {
      root <- _object(json, "$", Vector("schema", "version", "componentId", "resourceType", "abi", "semanticContract", "provenance", "definition", "model"))
      _ <- _expected_string(root, "schema", DisplayProtocol.schema, "$", "PROTOCOL_INCOMPATIBLE")
      _ <- _version(root, DisplayProtocol.version, "$", "PROTOCOL_INCOMPATIBLE")
      _ <- _expected_string(root, "componentId", source.componentId.name, "$", "SOURCE_MISMATCH")
      _ <- _expected_string(root, "resourceType", source.resourceType, "$", "SOURCE_MISMATCH")
      abi <- _object(_member(root, "abi"), "$.abi", Vector("identity", "version"))
      _ <- _expected_string(abi, "identity", GeneratedLogicalUiAbi.identity, "$.abi", "ABI_INCOMPATIBLE")
      _ <- _version(abi, GeneratedLogicalUiAbi.version, "$.abi", "ABI_INCOMPATIBLE")
      _ <- _contract(_member(root, "semanticContract"))
      metadata <- _object(_member(root, "definition"), "$.definition", Vector("screenId", "revision"))
      screenid <- _string(metadata, "screenId", "$.definition")
      definition <- definitions.definitions.find(_.screenId == screenid)
        .map(Consequence.success).getOrElse(_failure("DEFINITION_MISMATCH", "$.definition.screenId"))
      _ <- _expected_string(metadata, "revision", definition.definitionRevision, "$.definition", "DEFINITION_MISMATCH")
      _ <- _provenance(_member(root, "provenance"), definition)
      model <- _model(_member(root, "model"), "$.model")
      _ <- if (model.screenId == screenid) Consequence.success(()) else _failure("DEFINITION_MISMATCH", "$.model.screenId")
      display <- Display.createC(definitions, model)
    } yield display
  }
  private def _contract(json: Json): Consequence[Unit] = {
    val expected = GeneratedLogicalUiAbi.semanticContract
    val path = "$.semanticContract"
    for {
      obj <- _object(json, path, Vector("schema", "version", "semanticAuthoritySchema", "assurance", "driver"))
      _ <- _expected_string(obj, "schema", expected.schema, path, "CONTRACT_INCOMPATIBLE")
      _ <- _version(obj, expected.version, path, "CONTRACT_INCOMPATIBLE")
      _ <- _expected_string(obj, "semanticAuthoritySchema", expected.semanticAuthoritySchema, path, "CONTRACT_INCOMPATIBLE")
      _ <- _expected_string(obj, "assurance", expected.assurance, path, "CONTRACT_INCOMPATIBLE")
      driver <- _object(_member(obj, "driver"), path + ".driver", Vector("repository", "revision", "path", "scenarioId"))
      _ <- _expected_string(driver, "repository", expected.driver.repository, path + ".driver", "CONTRACT_INCOMPATIBLE")
      _ <- _expected_string(driver, "revision", expected.driver.revision, path + ".driver", "CONTRACT_INCOMPATIBLE")
      _ <- _expected_string(driver, "path", expected.driver.path, path + ".driver", "CONTRACT_INCOMPATIBLE")
      _ <- _expected_string(driver, "scenarioId", expected.driver.scenarioId, path + ".driver", "CONTRACT_INCOMPATIBLE")
    } yield ()
  }
  private def _provenance(json: Json, definition: Definition): Consequence[Unit] = {
    val path = "$.provenance"
    for {
      obj <- _object(json, path, Vector("producerId", "sourceId", "revision"))
      _ <- _expected_string(obj, "producerId", definition.provenance.producerId, path, "DEFINITION_MISMATCH")
      _ <- _expected_string(obj, "sourceId", definition.provenance.sourceId, path, "DEFINITION_MISMATCH")
      _ <- _expected_string(obj, "revision", definition.provenance.revision, path, "DEFINITION_MISMATCH")
    } yield ()
  }
  private def _model(json: Json, path: String): Consequence[DisplayModel] = {
    json.asObject match {
      case None => _failure("MEMBER_INVALID", path)
      case Some(obj) if !obj.contains("type") => _failure("MEMBER_INVALID", path)
      case Some(obj) => _string(obj, "type", path).flatMap {
        case "list" => for {
          obj <- _object(json, path, Vector("type", "screenId", "title", "items", "detailTarget", "actions"), Vector("snapshot"))
          screenid <- _string(obj, "screenId", path)
          title <- _string(obj, "title", path)
          items <- _array(obj, "items", path)(_item)
          target <- _object(_member(obj, "detailTarget"), path + ".detailTarget", Vector("screenId"))
          targetid <- _string(target, "screenId", path + ".detailTarget")
          actions <- _array(obj, "actions", path)(_action)
          snapshot <- _optional(obj, "snapshot", path) { (value, entrypath) =>
            _object(value, entrypath, Vector("reference")).flatMap(_string(_, "reference", entrypath)).map(QuerySnapshot.apply)
          }
        } yield ListModel(screenid, title, items, GeneratedLogicalUiAbi.DetailTarget(targetid), actions, snapshot)
        case "detail" => for {
          obj <- _object(json, path, Vector("type", "screenId", "title", "resource", "sections", "actions"))
          screenid <- _string(obj, "screenId", path)
          title <- _string(obj, "title", path)
          resource <- _resource(_member(obj, "resource"), path + ".resource")
          sections <- _array(obj, "sections", path)(_section)
          actions <- _array(obj, "actions", path)(_action)
        } yield DetailModel(screenid, title, resource, sections, actions)
        case _ => _failure("MODEL_KIND_INVALID", path + ".type")
      }
    }
  }
  private def _resource(json: Json, path: String): Consequence[Resource] = {
    for {
      obj <- _object(json, path, Vector("entityId", "revision"))
      identitytext <- _string(obj, "entityId", path)
      identity <- _entity_id(identitytext, path + ".entityId")
      revisiontext <- _string(obj, "revision", path)
      revision <- _revision(revisiontext, path + ".revision")
    } yield Resource(identity, revision)
  }
  private def _entity_id(text: String, path: String): Consequence[EntityId] = {
    try {
      EntityId.parse(text).toOption match {
        case Some(value) if value.value == text => Consequence.success(value)
        case _ => _failure("RESOURCE_INVALID", path)
      }
    } catch { case NonFatal(_) => _failure("RESOURCE_INVALID", path) }
  }
  private def _revision(text: String, path: String): Consequence[EntityRevision] = {
    try {
      EntityRevision.parseC(text).toOption match {
        case Some(value) if value.value.toString == text => Consequence.success(value)
        case _ => _failure("RESOURCE_INVALID", path)
      }
    } catch { case NonFatal(_) => _failure("RESOURCE_INVALID", path) }
  }
  private def _item(json: Json, path: String): Consequence[ListItem] = {
    for {
      obj <- _object(json, path, Vector("resource", "fields", "actions"))
      resource <- _resource(_member(obj, "resource"), path + ".resource")
      fields <- _array(obj, "fields", path)(_field)
      actions <- _array(obj, "actions", path)(_action)
    } yield ListItem(resource, fields, actions)
  }
  private def _section(json: Json, path: String): Consequence[Section] = {
    for {
      obj <- _object(json, path, Vector("id", "fields"), Vector("title"))
      id <- _string(obj, "id", path)
      title <- _optional(obj, "title", path) { (value, entrypath) =>
        value.asString.map(Consequence.success).getOrElse(_failure("MEMBER_INVALID", entrypath))
      }
      fields <- _array(obj, "fields", path)(_field)
    } yield Section(id, title, fields)
  }
  private def _field(json: Json, path: String): Consequence[Field] = {
    for {
      obj <- _object(json, path, Vector("id", "label", "role", "value"))
      id <- _string(obj, "id", path)
      label <- _string(obj, "label", path)
      tag <- _string(obj, "role", path)
      role <- PresentationRole.values.find(_.tag == tag).map(Consequence.success)
        .getOrElse(_failure("ROLE_INVALID", path + ".role"))
      value <- _value(_member(obj, "value"), path + ".value")
    } yield Field(id, label, role, value)
  }
  private def _action(json: Json, path: String): Consequence[Action] = {
    for {
      obj <- _object(json, path, Vector("id", "label", "bindingId", "availability"))
      id <- _string(obj, "id", path)
      label <- _string(obj, "label", path)
      bindingid <- _string(obj, "bindingId", path)
      tag <- _string(obj, "availability", path)
      availability <- tag match {
        case "hidden" => Consequence.success(ActionAvailability.Hidden)
        case "disabled" => Consequence.success(ActionAvailability.Disabled)
        case "enabled" => Consequence.success(ActionAvailability.Enabled)
        case _ => _failure("AVAILABILITY_INVALID", path + ".availability")
      }
    } yield Action(id, label, bindingid, availability)
  }
  private def _value(json: Json, path: String): Consequence[DisplayValue] = {
    json.asObject match {
      case None => _failure("MEMBER_INVALID", path)
      case Some(obj) if !obj.contains("type") => _failure("MEMBER_INVALID", path)
      case Some(obj) => _string(obj, "type", path).flatMap {
        case "text" => _object(json, path, Vector("type", "text"))
          .flatMap(_string(_, "text", path)).map(DisplayValue.Text.apply)
        case "number" => _object(json, path, Vector("type", "number"))
          .flatMap(_string(_, "number", path)).flatMap(_decimal(_, path + ".number")).map(DisplayValue.Number.apply)
        case "boolean" => _object(json, path, Vector("type", "boolean")).flatMap { declared =>
          _member(declared, "boolean").asBoolean.map(value => Consequence.success(DisplayValue.Boolean(value)))
            .getOrElse(_failure("MEMBER_INVALID", path + ".boolean"))
        }
        case "timestamp" => _object(json, path, Vector("type", "instant"))
          .flatMap(_string(_, "instant", path)).flatMap(_instant(_, path + ".instant")).map(DisplayValue.Timestamp.apply)
        case "missing" => _object(json, path, Vector("type")).map(_ => DisplayValue.Missing)
        case _ => _failure("VALUE_KIND_INVALID", path + ".type")
      }
    }
  }
  private def _decimal(text: String, path: String): Consequence[BigDecimal] = {
    try {
      text match {
        case _decimal_pattern(sign, whole, fraction, exponent) =>
          val digits = Option(fraction).getOrElse("")
          val scale = BigInt(digits.length) - Option(exponent).map(BigInt.apply).getOrElse(BigInt(0))
          if (!scale.isValidInt) _failure("VALUE_INVALID", path)
          else {
            val decimal = new java.math.BigDecimal(new java.math.BigInteger(sign + whole + digits), scale.toInt)
            if (decimal.toString != text) _failure("VALUE_INVALID", path)
            else Consequence.success(BigDecimal(decimal))
          }
        case _ => _failure("VALUE_INVALID", path)
      }
    } catch { case NonFatal(_) => _failure("VALUE_INVALID", path) }
  }
  private def _instant(text: String, path: String): Consequence[Instant] = {
    try {
      val instant = Instant.parse(text)
      if (instant.toString == text) Consequence.success(instant) else _failure("VALUE_INVALID", path)
    } catch { case NonFatal(_) => _failure("VALUE_INVALID", path) }
  }

  private def _obj(members: (String, Json)*): Json = Json.fromFields(members)
  private def _text(text: String): Json = Json.fromString(text)
  private def _vector[A](values: Vector[A])(encode: A => Json): Json = Json.fromValues(values.map(encode))
  private def _payload(source: Source, display: Display): Json = {
    val definition = display.definition
    val contract = definition.semanticContract
    val driver = contract.driver
    val provenance = definition.provenance
    _obj("schema" -> _text(DisplayProtocol.schema), "version" -> Json.fromInt(DisplayProtocol.version),
      "componentId" -> _text(source.componentId.name), "resourceType" -> _text(source.resourceType),
      "abi" -> _obj("identity" -> _text(definition.abiIdentity), "version" -> Json.fromInt(definition.abiVersion)),
      "semanticContract" -> _obj("schema" -> _text(contract.schema), "version" -> Json.fromInt(contract.version),
        "semanticAuthoritySchema" -> _text(contract.semanticAuthoritySchema), "assurance" -> _text(contract.assurance),
        "driver" -> _obj("repository" -> _text(driver.repository), "revision" -> _text(driver.revision),
          "path" -> _text(driver.path), "scenarioId" -> _text(driver.scenarioId))),
      "provenance" -> _obj("producerId" -> _text(provenance.producerId), "sourceId" -> _text(provenance.sourceId),
        "revision" -> _text(provenance.revision)),
      "definition" -> _obj("screenId" -> _text(definition.screenId), "revision" -> _text(definition.definitionRevision)),
      "model" -> _model_json(display.model))
  }
  private def _model_json(model: DisplayModel): Json = model match {
    case value: ListModel =>
      val members = Vector("type" -> _text("list"), "screenId" -> _text(value.screenId), "title" -> _text(value.title),
        "items" -> _vector(value.items)(_item_json), "detailTarget" -> _obj("screenId" -> _text(value.detailTarget.screenId)),
        "actions" -> _vector(value.actions)(_action_json)) ++
        value.snapshot.toVector.map(snapshot => "snapshot" -> _obj("reference" -> _text(snapshot.reference)))
      Json.fromFields(members)
    case value: DetailModel => _obj("type" -> _text("detail"), "screenId" -> _text(value.screenId), "title" -> _text(value.title),
      "resource" -> _resource_json(value.resource), "sections" -> _vector(value.sections)(_section_json),
      "actions" -> _vector(value.actions)(_action_json))
  }
  private def _resource_json(value: Resource): Json =
    _obj("entityId" -> _text(value.entityId.value), "revision" -> _text(value.revision.value.toString))
  private def _item_json(value: ListItem): Json = _obj("resource" -> _resource_json(value.resource),
    "fields" -> _vector(value.fields)(_field_json), "actions" -> _vector(value.actions)(_action_json))
  private def _section_json(value: Section): Json = Json.fromFields(
    Vector("id" -> _text(value.id)) ++ value.title.toVector.map(title => "title" -> _text(title)) ++
      Vector("fields" -> _vector(value.fields)(_field_json)))
  private def _field_json(value: Field): Json = _obj("id" -> _text(value.id), "label" -> _text(value.label),
    "role" -> _text(value.role.tag), "value" -> _value_json(value.value))
  private def _action_json(value: Action): Json = {
    val tag = value.availability match {
      case ActionAvailability.Hidden => "hidden"
      case ActionAvailability.Disabled => "disabled"
      case ActionAvailability.Enabled => "enabled"
    }
    _obj("id" -> _text(value.id), "label" -> _text(value.label), "bindingId" -> _text(value.bindingId), "availability" -> _text(tag))
  }
  private def _value_json(value: DisplayValue): Json = value match {
    case DisplayValue.Text(text) => _obj("type" -> _text("text"), "text" -> _text(text))
    case DisplayValue.Number(number) => _obj("type" -> _text("number"), "number" -> _text(number.bigDecimal.toString))
    case DisplayValue.Boolean(boolean) => _obj("type" -> _text("boolean"), "boolean" -> Json.fromBoolean(boolean))
    case DisplayValue.Timestamp(instant) => _obj("type" -> _text("timestamp"), "instant" -> _text(instant.toString))
    case DisplayValue.Missing => _obj("type" -> _text("missing"))
  }
  private def _render(json: Json): String = json.fold(
    "null", value => if (value) "true" else "false", _.toString, _quote,
    values => values.map(_render).mkString("[", ",", "]"),
    obj => obj.toVector.map { case (key, value) => _quote(key) + ":" + _render(value) }.mkString("{", ",", "}"))
  private def _quote(text: String): String = {
    val builder = new java.lang.StringBuilder().append('"')
    text.foreach { char =>
      char match {
        case '"' => builder.append("\\\"")
        case '\\' => builder.append("\\\\")
        case '\b' => builder.append("\\b")
        case '\f' => builder.append("\\f")
        case '\n' => builder.append("\\n")
        case '\r' => builder.append("\\r")
        case '\t' => builder.append("\\t")
        case value if value < ' ' || value > '~' =>
          val hex = Integer.toHexString(value.toInt)
          builder.append("\\u").append("0" * (4 - hex.length)).append(hex)
        case value => builder.append(value)
      }
    }
    builder.append('"').toString
  }
}
