package org.goldenport.cncf.http

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.Schema

/*
 * Resolves CML/domain schema and WebDescriptor presentation overrides into a
 * Web-facing schema contract.
 *
 * @since   Apr. 16, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object WebSchemaResolver {
  enum Surface {
    case Entity
    case Data
    case View
    case Aggregate
    case Operation

    def name: String =
      productPrefix.toLowerCase(java.util.Locale.ROOT)
  }

  enum Source {
    case Schema
    case Descriptor
    case WebDescriptor
    case Sampling
    case Empty
  }

  enum FieldOrderStrategy {
    case SchemaOrder
    case IdFirst
  }

  final case class ResolvedWebSchema(
    selector: String,
    surface: Surface,
    fields: Vector[ResolvedWebField],
    source: Source,
    warnings: Vector[String] = Vector.empty
  ) {
    def fieldNames: Vector[String] =
      fields.map(_.name)

    def controls: Map[String, WebDescriptor.FormControl] =
      fields.map(x => x.name -> x.asControl).toMap
  }

  final case class ResolvedWebField(
    name: String,
    label: Option[String] = None,
    dataType: Option[String] = None,
    multiplicity: Option[String] = None,
    control: WebDescriptor.FormControl = WebDescriptor.FormControl(),
    hidden: Boolean = false,
    system: Boolean = false,
    values: Vector[String] = Vector.empty,
    multiple: Boolean = false,
    readonly: Boolean = false,
    placeholder: Option[String] = None,
    help: Option[String] = None,
    source: Source = Source.Empty
  ) {
    def controlType: String =
      control.controlType.getOrElse(defaultControlType)

    def required: Boolean =
      control.required.getOrElse(defaultRequired)

    def defaultControlType: String =
      dataType.map(_.toLowerCase(java.util.Locale.ROOT)) match {
        case Some(x) if x.contains("bool") => "checkbox"
        case Some(x) if _is_multiline(name, x) => "textarea"
        case _ if _is_secret(name) => "password"
        case Some(x) if x.contains("int") || x.contains("long") || x.contains("decimal") || x.contains("number") => "number"
        case Some(x) if x.contains("datetime") || x.contains("timestamp") => "datetime-local"
        case Some(x) if x.contains("date") => "date"
        case _ => "text"
      }

    def defaultRequired: Boolean =
      !multiplicity.exists(x => x.toLowerCase(java.util.Locale.ROOT).contains("zero"))

    def asControl: WebDescriptor.FormControl =
      control.copy(
        controlType = Some(controlType),
        hidden = hidden,
        system = system,
        values = values,
        multiple = multiple,
        required = Some(required),
        readonly = readonly,
        placeholder = placeholder,
        help = help
      )
  }

  def resolveEntity(
    component: Component,
    componentPath: String,
    entityName: String,
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String] = Vector.empty,
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    resolveEntityLike(component, componentPath, Surface.Entity, "entity", entityName, entityName, webDescriptor, fallbackFields, fieldOrderStrategy)

  def resolveView(
    component: Component,
    componentPath: String,
    viewName: String,
    entityName: Option[String],
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String] = Vector.empty,
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    resolveEntityLike(component, componentPath, Surface.View, "view", viewName, entityName.getOrElse(viewName), webDescriptor, fallbackFields, fieldOrderStrategy)

  def resolveAggregate(
    component: Component,
    componentPath: String,
    aggregateName: String,
    entityName: Option[String],
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String] = Vector.empty,
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    resolveEntityLike(component, componentPath, Surface.Aggregate, "aggregate", aggregateName, entityName.getOrElse(aggregateName), webDescriptor, fallbackFields, fieldOrderStrategy)

  def resolveData(
    componentPath: String,
    dataName: String,
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String],
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    _merge(
      selector = s"${componentPath}.data.${dataName}",
      surface = Surface.Data,
      base = _order_fields(
        fallbackFields.map(name => ResolvedWebField(name = name, source = Source.Sampling)),
        Source.Sampling,
        fieldOrderStrategy
      ),
      baseSource = if (fallbackFields.nonEmpty) Source.Sampling else Source.Empty,
      adminFields = webDescriptor.adminFields(componentPath, "data", dataName)
    )

  def resolveOperation(
    componentPath: String,
    surfaceName: String,
    collectionName: String,
    operationName: String,
    parameters: Vector[ParameterDefinition],
    webDescriptor: WebDescriptor
  ): ResolvedWebSchema = {
    val adminFields = webDescriptor.adminOperationFields(componentPath, surfaceName, collectionName, operationName)
    _merge(
      selector = s"${componentPath}.${surfaceName}.${collectionName}.${operationName}",
      surface = Surface.Operation,
      base = parameters.map(_operation_field),
      baseSource = if (parameters.nonEmpty) Source.Schema else Source.Empty,
      adminFields = adminFields
    )
  }

  def resolveOperationControls(
    selector: String,
    parameters: Vector[ParameterDefinition],
    controls: Map[String, WebDescriptor.FormControl]
  ): ResolvedWebSchema = {
    val base = parameters.map(_operation_field)
    val baseNames = base.map(_.name).toSet
    val merged = base.map { field =>
      controls.get(field.name).map(control =>
        _with_control(field, control, Source.WebDescriptor)
      ).getOrElse(field)
    } ++ controls.toVector.sortBy(_._1).collect {
      case (name, control) if !baseNames.contains(name) =>
        _with_control(ResolvedWebField(name = name), control, Source.WebDescriptor)
    }
    ResolvedWebSchema(
      selector = selector,
      surface = Surface.Operation,
      fields = merged,
      source = if (controls.nonEmpty) Source.WebDescriptor else if (base.nonEmpty) Source.Schema else Source.Empty
    )
  }

  def fromSchema(
    schema: Schema
  ): Vector[ResolvedWebField] =
    schema.columns.map { column =>
      ResolvedWebField(
        name = column.name.value,
        label = column.label.map(_.value.displayMessage),
        dataType = Some(column.domain.datatype.name),
        multiplicity = Some(column.domain.multiplicity.toString),
        control = WebDescriptor.FormControl(
          controlType = column.web.controlType,
          hidden = column.web.hidden,
          system = column.web.system,
          values = column.web.values,
          multiple = column.web.multiple,
          required = column.web.required,
          readonly = column.web.readonly,
          placeholder = column.web.placeholder,
          help = column.web.help
        ),
        hidden = column.web.hidden,
        system = column.web.system,
        values = column.web.values,
        multiple = column.web.multiple,
        readonly = column.web.readonly,
        placeholder = column.web.placeholder,
        help = column.web.help,
        source = Source.Schema
      )
    }

  private def resolveEntityLike(
    component: Component,
    componentPath: String,
    surface: Surface,
    surfaceName: String,
    collectionName: String,
    entityName: String,
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String],
    fieldOrderStrategy: FieldOrderStrategy
  ): ResolvedWebSchema = {
    val runtimeDescriptor = component.entityRuntimeDescriptor(entityName)
    val schemaFields = runtimeDescriptor.flatMap(_.schema).map(fromSchema).getOrElse(Vector.empty)
    val fallback = if (schemaFields.nonEmpty) Vector.empty else fallbackFields
    val base =
      if (schemaFields.nonEmpty)
        _order_fields(schemaFields, Source.Schema, fieldOrderStrategy)
      else
        _order_fields(
          fallback.map(name => ResolvedWebField(name = name, source = Source.Sampling)),
          Source.Sampling,
          fieldOrderStrategy
        )
    val source =
      if (schemaFields.nonEmpty) Source.Schema
      else if (fallback.nonEmpty) Source.Sampling
      else Source.Empty
    _merge(
      selector = s"${componentPath}.${surfaceName}.${collectionName}",
      surface = surface,
      base = base,
      baseSource = source,
      adminFields = webDescriptor.adminFields(componentPath, surfaceName, collectionName)
    )
  }

  private def _merge(
    selector: String,
    surface: Surface,
    base: Vector[ResolvedWebField],
    baseSource: Source,
    adminFields: Vector[WebDescriptor.AdminField]
  ): ResolvedWebSchema = {
    val byName = base.map(x => x.name -> x).toMap
    val fields =
      if (adminFields.nonEmpty)
        adminFields.map { field =>
          byName.get(field.name).map { baseField =>
            _with_control(baseField, field.control, Source.WebDescriptor)
          }.getOrElse {
            _with_control(ResolvedWebField(name = field.name), field.control, Source.WebDescriptor)
          }
        }
      else
        base
    ResolvedWebSchema(selector, surface, fields, if (adminFields.nonEmpty) Source.WebDescriptor else baseSource)
  }

  private def _operation_field(
    parameter: ParameterDefinition
  ): ResolvedWebField =
    ResolvedWebField(
      name = parameter.name,
      label = parameter.label.map(_.displayMessage),
      dataType = Option(parameter.domain.datatype).map(_.toString),
      multiplicity = Option(parameter.domain.multiplicity).map(_.toString),
      control = WebDescriptor.FormControl(
        controlType = parameter.web.controlType,
        hidden = parameter.web.hidden,
        system = parameter.web.system,
        values = parameter.web.values,
        multiple = parameter.web.multiple,
        required = parameter.web.required,
        readonly = parameter.web.readonly,
        placeholder = parameter.web.placeholder,
        help = parameter.web.help
      ),
      readonly = parameter.web.readonly,
      placeholder = parameter.web.placeholder,
      help = parameter.web.help,
      source = Source.Schema
    )

  private def _with_control(
    field: ResolvedWebField,
    control: WebDescriptor.FormControl,
    source: Source
  ): ResolvedWebField = {
    val mergedControl = _merge_control(field.control, control)
    field.copy(
      control = mergedControl,
      hidden = mergedControl.hidden,
      system = mergedControl.system,
      values = mergedControl.values,
      multiple = mergedControl.multiple,
      readonly = mergedControl.readonly,
      placeholder = control.placeholder.orElse(field.placeholder),
      help = control.help.orElse(field.help),
      source = source
    )
  }

  private def _merge_control(
    base: WebDescriptor.FormControl,
    overrideControl: WebDescriptor.FormControl
  ): WebDescriptor.FormControl =
    WebDescriptor.FormControl(
      controlType = overrideControl.controlType.orElse(base.controlType),
      hidden = base.hidden || overrideControl.hidden,
      system = base.system || overrideControl.system,
      values = if (overrideControl.values.nonEmpty) overrideControl.values else base.values,
      multiple = base.multiple || overrideControl.multiple,
      required = overrideControl.required.orElse(base.required),
      readonly = base.readonly || overrideControl.readonly,
      placeholder = overrideControl.placeholder.orElse(base.placeholder),
      help = overrideControl.help.orElse(base.help)
    )

  private def _order_fields(
    fields: Vector[ResolvedWebField],
    source: Source,
    strategy: FieldOrderStrategy
  ): Vector[ResolvedWebField] =
    strategy match {
      case FieldOrderStrategy.SchemaOrder =>
        fields
      case FieldOrderStrategy.IdFirst =>
        fields.find(_.name == "id") match {
          case Some(id) => id +: fields.filterNot(_.name == "id")
          case None => ResolvedWebField(name = "id", source = source) +: fields
        }
    }

  private def _is_secret(name: String): Boolean = {
    val n = NamingConventions.toNormalizedSegment(name)
    n.contains("password") || n.contains("secret") || n.contains("token")
  }

  private def _is_multiline(name: String, datatype: String): Boolean = {
    val n = NamingConventions.toNormalizedSegment(name)
    datatype.contains("text") ||
      datatype.contains("memo") ||
      datatype.contains("document") ||
      n.contains("body") ||
      n.contains("content") ||
      n.contains("description") ||
      n.contains("comment") ||
      n.contains("message")
  }
}
