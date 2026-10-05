package logicalui

import java.time.Instant
import io.circe.Json
import io.circe.jawn.JawnParser
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.display.*
import org.goldenport.schema.{DataConfidentiality, WebColumn, WebValidationHints}
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure supplied wire candidates and lexical external type probes, not lifecycle evidence. */
object DisplayMutationCodecFixture {
  import DisplayMutation.*
  import DisplayMutationBinding.*
  import DisplayMutationResult.*
  import GeneratedLogicalUiAbi.*
  import DisplayMutationContractFixture.{success, source, resource, positiveCapabilities, Patch, NativePatch, Trace}
  final case class ResultCase(request: Request, observedRevision: Option[EntityRevision], result: Result)
  final case class NativeCase(binding: Binding[NativePatch], request: Request.Create, display: Display)
  private val _parser = JawnParser(allowDuplicateKeys = false)

  def wire(text: String): Json = _parser.parse(text).fold(_ => throw new IllegalStateException("valid wire fixture"), value => value)
  def conclusion[A](result: Consequence[A]): Conclusion = result match {
    case Consequence.Failure(value) => value
    case _ => throw new IllegalStateException("expected structured fixture rejection")
  }
  def problem[A](result: Consequence[A]): DisplayMutation.Diagnostic = conclusion(result).observation.cause.getException.collect {
    case carrier: MutationException => carrier.diagnostic
  }.getOrElse(throw new IllegalStateException("expected mutation diagnostic"))
  def set(json: Json, path: Vector[String], value: Json): Json = {
    if (path.isEmpty) value
    else json.asObject match {
      case Some(obj) => Json.fromJsonObject(obj.add(path.head, set(obj(path.head).getOrElse(Json.Null), path.tail, value)))
      case None => Json.fromValues(json.asArray.get.zipWithIndex.map { case (entry, index) =>
        if (index.toString == path.head) set(entry, path.tail, value) else entry
      })
    }
  }
  def at(json: Json, path: Vector[String]): Json = path.foldLeft(json) { (value, name) =>
    value.asObject.flatMap(_(name)).getOrElse(value.asArray.get(name.toInt))
  }
  def remove(json: Json, path: Vector[String], name: String): Json =
    set(json, path, Json.fromJsonObject(at(json, path).asObject.get.remove(name)))
  def duplicates(json: Json): Vector[String] = _objects(json, Vector.empty).filter(_._2.toVector.nonEmpty)
    .map { case (path, _) => _duplicate(json, Vector.empty, path) }
  def shapes(json: Json): Vector[String] = _objects(json, Vector.empty).flatMap { case (path, obj) =>
    val extra = set(json, path, Json.fromJsonObject(obj.add("attacker-secret", Json.fromString("attacker-secret")))).noSpaces
    Vector(extra) ++ obj.toVector.flatMap { case (name, value) =>
      val wrong = if (value.isString || value.isNumber || value.isBoolean) Json.arr() else Json.fromString("wrong")
      Vector(remove(json, path, name).noSpaces, set(json, path :+ name, Json.Null).noSpaces,
        set(json, path :+ name, wrong).noSpaces)
    }
  }
  private def _objects(json: Json, path: Vector[String]): Vector[(Vector[String], io.circe.JsonObject)] = json.asObject match {
    case Some(obj) => Vector(path -> obj) ++ obj.toVector.flatMap { case (name, value) => _objects(value, path :+ name) }
    case None => json.asArray.toVector.flatMap(_.zipWithIndex.flatMap { case (value, index) => _objects(value, path :+ index.toString) })
  }
  private def _duplicate(json: Json, path: Vector[String], target: Vector[String]): String = json.asObject match {
    case Some(obj) =>
      val members = obj.toVector.map { case (name, value) => Json.fromString(name).noSpaces + ":" + _duplicate(value, path :+ name, target) }
      (members ++ (if (path == target && members.nonEmpty) Vector(members.head) else Vector.empty)).mkString("{", ",", "}")
    case None => json.asArray match {
      case Some(values) => values.zipWithIndex.map { case (value, index) => _duplicate(value, path :+ index.toString, target) }.mkString("[", ",", "]")
      case None => json.noSpaces
    }
  }
  def metadataBinding(trace: Trace, kinds: Vector[ValueKind] = ValueKind.values.toVector): Binding[Patch] = {
    val original = DisplayMutationContractFixture.definitions.definitions(1)
    val detail = original.screen match {
      case value: DetailDefinition => value
      case _ => throw new IllegalStateException("declared Detail fixture")
    }
    val candidate = original.copy(screen = detail.copy(sections = detail.sections.map(section => section.copy(fields =
      section.fields.map(field => if (field.id == "title") field.copy(role = PresentationRole.Content, value = field.value.copy(allowedKinds = kinds)) else field)))))
    val definitions = success(GeneratedLogicalUiAbi.admitC(Vector(candidate)))
    val web = WebColumn.empty.copy(hidden = true, multiple = true, controlType = Some("select"), required = Some(false),
      confidentiality = DataConfidentiality.Secret,
      placeholder = Some("  placeholder Ω  "), help = Some("help\n"), values = Vector("z", "a", "a", "Ω"),
      validation = WebValidationHints(min = Some(BigDecimal("1.00")), max = Some(BigDecimal("9.00")),
        step = Some(BigDecimal("0.10")), minLength = Some(0), maxLength = Some(Int.MaxValue), pattern = Some("^.*$")))
    val fields = DisplayMutationContractFixture.fields(Some(trace)).map(field =>
      if (field.location == DisplayMutationContractFixture.title) field.copy(acceptedKinds = kinds, web = web) else field)
    success(DisplayMutationBinding.admitC(source, definitions, candidate.screenId, "metadata-1",
      DisplayMutationContractFixture.capabilities, fields, Set.empty, input => DisplayMutationContractFixture.validateC(input, Some(trace))))
  }
  def nativeCase(text: String, number: BigDecimal, flag: Boolean, instant: Instant,
    order: Vector[ValueKind] = ValueKind.values.toVector, index: Int = 1, revision: Long = 1): NativeCase = {
    val definitions = DisplayMutationContractFixture.nativeDefinitions(order)
    val binding = success(DisplayMutationBinding.admitC(source, definitions, definitions.definitions.head.screenId,
      "native-wire-1", positiveCapabilities, DisplayMutationContractFixture.nativeFields(order), Set.empty,
      (input: NativePatch) => Consequence.success(input)))
    val fields = order.map { kind =>
      val value = kind match {
        case ValueKind.Text => DisplayValue.Text(text)
        case ValueKind.Number => DisplayValue.Number(number)
        case ValueKind.Boolean => DisplayValue.Boolean(flag)
        case ValueKind.Timestamp => DisplayValue.Timestamp(instant)
        case ValueKind.Missing => DisplayValue.Missing
      }
      FieldInput(FieldLocator("native", kind.tag), value)
    }
    val model = DisplayModel.DetailModel(binding.selection.definition.screenId, "Native contract", resource(index, revision),
      Vector(DisplayModel.Section("native", None, fields.map(field => DisplayModel.Field(field.location.fieldId,
        field.location.fieldId, PresentationRole.Content, field.value)))), Vector.empty)
    NativeCase(binding, Request.Create(binding.selection, fields), success(Display.createC(definitions, model)))
  }
  def resultCase(kind: OutcomeKind, index: Int = 1, revision: Long = 7, ready: Boolean = true,
    reason: PendingReason = PendingReason.ViewNotCurrent): ResultCase = {
    val binding = success(DisplayMutationContractFixture.bindingC(positive = true))
    val current = resource(index, revision)
    val request: Request = kind match {
      case OutcomeKind.Created => Request.Create(binding.selection, Vector.empty)
      case OutcomeKind.Updated | OutcomeKind.Unchanged => Request.Update(binding.selection, current.entityId,
        Vector(FieldInput(DisplayMutationContractFixture.title, DisplayValue.Text("new"))))
      case OutcomeKind.Deleted => Request.Delete(binding.selection, current.entityId)
    }
    val observed = if (kind == OutcomeKind.Created) None else Some(current.revision)
    val committed = kind match {
      case OutcomeKind.Created => resource(index, 1)
      case OutcomeKind.Updated => DisplayModel.Resource(current.entityId, success(current.revision.nextC))
      case _ => current
    }
    val receipt = success(receiptC(binding.selection, request, kind, committed, observed))
    val projection = if (kind == OutcomeKind.Deleted) None else if (ready)
      Some(ProjectionState.Ready(DisplayMutationContractFixture.reload(committed.revision.value, index)))
      else Some(ProjectionState.Pending(reason))
    ResultCase(request, observed, success(createC(receipt, projection)))
  }
  def verifyTypeBoundary(): Unit = {
    org.scalatest.Assertions.assertCompiles("""
      import org.goldenport.cncf.display.*
      import org.goldenport.Consequence
      import org.simplemodeling.model.datatype.EntityRevision
      val request: DisplayMutation.Request = null
      val selection: DisplayMutation.Selection = null
      val binding: DisplayMutationBinding.Binding[String] = null
      val descriptor: DisplayMutationBinding.Descriptor = null
      val result: DisplayMutationResult.Result = null
      val definitions: GeneratedLogicalUiAbi.AdmittedDefinitions = null
      val encoded: Consequence[String] = DisplayMutationCodec.encodeRequestC(request = request)
      val decoded: Consequence[DisplayMutation.Request] = DisplayMutationCodec.decodeRequestC(
        expectedSelection = selection, operation = DisplayMutation.Operation.Create, json = "")
      DisplayMutationCodec.encodeDescriptorC(binding = binding, descriptor = descriptor)
      DisplayMutationCodec.decodeDescriptorC(binding = binding, json = "")
      DisplayMutationCodec.encodeResultC(result = result)
      DisplayMutationCodec.decodeResultC(expectedSelection = selection, definitions = definitions,
        request = request, observedRevision = None, json = "")
    """)
    org.scalatest.Assertions.assertTypeError("org.goldenport.cncf.display.DisplayMutationJson._parse(\"{}\")")
    org.scalatest.Assertions.assertTypeError("org.goldenport.cncf.display.DisplayMutationRequestCodec._encode(null)")
    org.scalatest.Assertions.assertTypeError("org.goldenport.cncf.display.DisplayMutationDescriptorCodec._encode[String](null, null)")
    org.scalatest.Assertions.assertTypeError("org.goldenport.cncf.display.DisplayMutationResultCodec._encode(null)")
    org.scalatest.Assertions.assertTypeError("new org.goldenport.cncf.display.DisplayMutationBinding.Descriptor(null, null, null, Vector.empty)")
    org.scalatest.Assertions.assertTypeError("new org.goldenport.cncf.display.DisplayMutationResult.Result(null, None)")
    org.scalatest.Assertions.assertTypeError("org.goldenport.cncf.display.DisplayMutationCodec.encodeRequestC(\"untyped\")")
  }
}
