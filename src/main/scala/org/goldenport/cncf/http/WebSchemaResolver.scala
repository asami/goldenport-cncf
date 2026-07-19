package org.goldenport.cncf.http

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{DataConfidentiality, Schema, WebValidationHints}
import scala.util.Try

/*
 * Resolves CML/domain schema and WebDescriptor presentation overrides into a
 * Web-facing schema contract.
 *
 * @since   Apr. 16, 2026
 *  version Apr. 17, 2026
 *  version Apr. 27, 2026
 *  version May.  8, 2026
 * @version Jul. 19, 2026
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
    validation: WebValidationHints = WebValidationHints.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Public,
    source: Source = Source.Empty,
    updateCommands: Vector[String] = Vector.empty,
    updateValueCarriers: Vector[String] = Vector.empty
  ) {
    def controlType: String =
      control.controlType.getOrElse(defaultControlType)

    def required: Boolean =
      control.required.getOrElse(defaultRequired)

    def defaultControlType: String =
      dataType.map(_.toLowerCase(java.util.Locale.ROOT)) match {
        case Some(x) if x.contains("bool") => "checkbox"
        case Some(x) if _is_multiline(name, x) => "textarea"
        case _ if confidentiality == DataConfidentiality.Secret || _is_secret(name) => "password"
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
      label = label,
      placeholder = placeholder,
      help = help,
      validation = validation
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
    _resolve_entity_like(component, componentPath, Surface.Entity, "entity", entityName, entityName, webDescriptor, fallbackFields, None, fieldOrderStrategy)

  def resolveView(
    component: Component,
    componentPath: String,
    viewName: String,
    entityName: Option[String],
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String] = Vector.empty,
    viewFields: Option[Vector[String]] = None,
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    _resolve_entity_like(component, componentPath, Surface.View, "view", viewName, entityName.getOrElse(viewName), webDescriptor, fallbackFields, viewFields, fieldOrderStrategy)

  def resolveAggregate(
    component: Component,
    componentPath: String,
    aggregateName: String,
    entityName: Option[String],
    webDescriptor: WebDescriptor,
    fallbackFields: => Vector[String] = Vector.empty,
    viewFields: Option[Vector[String]] = None,
    fieldOrderStrategy: FieldOrderStrategy = FieldOrderStrategy.IdFirst
  ): ResolvedWebSchema =
    _resolve_entity_like(component, componentPath, Surface.Aggregate, "aggregate", aggregateName, entityName.getOrElse(aggregateName), webDescriptor, fallbackFields, viewFields, fieldOrderStrategy)

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
      basesource = if (fallbackFields.nonEmpty) Source.Sampling else Source.Empty,
      adminfields = webDescriptor.adminFields(componentPath, "data", dataName)
    )

  def resolveOperation(
    componentPath: String,
    surfaceName: String,
    collectionName: String,
    operationName: String,
    parameters: Vector[ParameterDefinition],
    webDescriptor: WebDescriptor
  ): ResolvedWebSchema = {
    val adminfields = webDescriptor.adminOperationFields(componentPath, surfaceName, collectionName, operationName)
    _merge(
      selector = s"${componentPath}.${surfaceName}.${collectionName}.${operationName}",
      surface = Surface.Operation,
      base = parameters.map(_operation_field),
      basesource = if (parameters.nonEmpty) Source.Schema else Source.Empty,
      adminfields = adminfields
    )
  }

  def resolveOperationControls(
    selector: String,
    parameters: Vector[ParameterDefinition],
    controls: Map[String, WebDescriptor.FormControl]
  ): ResolvedWebSchema = {
    val base = parameters.map(_operation_field)
    val basenames = base.map(_.name).toSet
    val merged = base.map { field =>
      controls.get(field.name).map(control =>
        _with_control(field, control, Source.WebDescriptor)
      ).getOrElse(field)
    } ++ controls.toVector.sortBy(_._1).collect {
      case (name, control) if !basenames.contains(name) =>
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
        label = column.label.orElse(column.baseContent.nameAttributes.label).map(_.value.displayMessage),
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
          help = column.web.help,
          validation = column.web.validation
        ),
        hidden = column.web.hidden,
        system = column.web.system,
        values = column.web.values,
        multiple = column.web.multiple,
        readonly = column.web.readonly,
        placeholder = column.web.placeholder,
        help = column.web.help,
        validation = column.web.validation,
        confidentiality = _effective_confidentiality(column.confidentiality, column.web.confidentiality),
        source = Source.Schema
      )
    }

  private def _resolve_entity_like(
    component: Component,
    componentpath: String,
    surface: Surface,
    surfacename: String,
    collectionname: String,
    entityname: String,
    webdescriptor: WebDescriptor,
    fallbackfields: => Vector[String],
    viewfields: Option[Vector[String]],
    fieldorderstrategy: FieldOrderStrategy
  ): ResolvedWebSchema = {
    val runtimedescriptor = _entity_runtime_descriptor(component, entityname, collectionname, surfacename)
    val effectiveschema = runtimedescriptor.flatMap(_.schema).orElse(_generated_entity_schema(component, entityname, collectionname, surfacename))
    val schemafields = _select_fields(effectiveschema.map(fromSchema).getOrElse(Vector.empty), viewfields)
    val fallback = if (schemafields.nonEmpty) Vector.empty else fallbackfields
    val base =
      if (schemafields.nonEmpty)
        _order_fields(schemafields, Source.Schema, fieldorderstrategy)
      else
        _order_fields(
          fallback.map(name => ResolvedWebField(name = name, source = Source.Sampling)),
          Source.Sampling,
          fieldorderstrategy
        )
    val source =
      if (schemafields.nonEmpty) Source.Schema
      else if (fallback.nonEmpty) Source.Sampling
      else Source.Empty
    _merge(
      selector = s"${componentpath}.${surfacename}.${collectionname}",
      surface = surface,
      base = base,
      basesource = source,
      adminfields = webdescriptor.adminFields(componentpath, surfacename, collectionname)
    )
  }

  private def _select_fields(
    fields: Vector[ResolvedWebField],
    names: Option[Vector[String]]
  ): Vector[ResolvedWebField] =
    names.filter(_.nonEmpty).map { xs =>
      xs.flatMap(name => fields.find(field => NamingConventions.equivalentByNormalized(field.name, name)))
    }.filter(_.nonEmpty).getOrElse(fields)

  private def _entity_runtime_descriptor(
    component: Component,
    entityname: String,
    collectionname: String,
    surfacename: String
  ): Option[org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor] =
    _entity_name_candidates(entityname, collectionname, surfacename)
      .iterator
      .flatMap(component.entityRuntimeDescriptor)
      .toSeq
      .headOption

  private def _generated_entity_schema(
    component: Component,
    entityname: String,
    collectionname: String,
    surfacename: String
  ): Option[Schema] =
    _entity_name_candidates(entityname, collectionname, surfacename)
      .iterator
      .flatMap(_generated_entity_module(component, _).flatMap(_extract_schema))
      .toSeq
      .headOption

  private def _entity_name_candidates(
    entityname: String,
    collectionname: String,
    surfacename: String
  ): Vector[String] = {
    val suffix = s"-${NamingConventions.toNormalizedSegment(surfacename)}"
    Vector(entityname, collectionname) ++
      Vector(entityname, collectionname).flatMap(_strip_suffix(_, suffix))
  }.map(_.trim).filter(_.nonEmpty).distinct

  private def _strip_suffix(
    value: String,
    suffix: String
  ): Option[String] = {
    val normalized = NamingConventions.toNormalizedSegment(value)
    if (normalized.endsWith(suffix))
      Some(normalized.dropRight(suffix.length))
    else
      None
  }

  private def _merge(
    selector: String,
    surface: Surface,
    base: Vector[ResolvedWebField],
    basesource: Source,
    adminfields: Vector[WebDescriptor.AdminField]
  ): ResolvedWebSchema = {
    val byname = base.map(x => x.name -> x).toMap
    val fields =
      if (adminfields.nonEmpty)
        adminfields.map { field =>
          byname.get(field.name).map { basefield =>
            _with_control(basefield, field.control, Source.WebDescriptor)
          }.getOrElse {
            _with_control(ResolvedWebField(name = field.name), field.control, Source.WebDescriptor)
          }
        }
      else
        base
    ResolvedWebSchema(selector, surface, fields, if (adminfields.nonEmpty) Source.WebDescriptor else basesource)
  }

  private def _operation_field(
    parameter: ParameterDefinition
  ): ResolvedWebField =
    ResolvedWebField(
      name = parameter.name,
      label = parameter.label.map(_.displayMessage),
      dataType = Option(parameter.domain.datatype).map(_.name),
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
        help = parameter.web.help,
        validation = parameter.web.validation
      ),
      readonly = parameter.web.readonly,
      placeholder = parameter.web.placeholder,
      help = parameter.web.help,
      validation = parameter.web.validation,
      confidentiality = _effective_confidentiality(parameter.confidentiality, parameter.web.confidentiality),
      source = Source.Schema
    )

  private def _effective_confidentiality(
    primary: DataConfidentiality,
    secondary: DataConfidentiality
  ): DataConfidentiality =
    if (primary != DataConfidentiality.Public) primary else secondary

  private def _generated_entity_module(
    component: Component,
    entityname: String
  ): Option[AnyRef] = {
    val packagenames = _generated_module_package_names(component)
    val classname = _entity_class_name(entityname)
    val candidates = packagenames.flatMap { pkg =>
      Vector(
        s"${pkg}.entity.${classname}$$",
        s"${pkg}.entity.aggregate.${classname}$$",
        s"${pkg}.entity.operation.${classname}$$",
        s"${pkg}.entity.read.${classname}$$",
        s"${pkg}.entity.view.${classname}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private def _extract_schema(
    module: AnyRef
  ): Option[Schema] =
    Try(module.getClass.getMethod("schema").invoke(module)).toOption.collect {
      case schema: Schema => schema
    }

  private def _entity_class_name(
    entityname: String
  ): String =
    NamingConventions.toNormalizedSegment(entityname)
      .split("-")
      .toVector
      .filter(_.nonEmpty)
      .map(s => s"${s.head.toUpper}${s.drop(1)}")
      .mkString

  private def _generated_module_package_names(
    component: Component
  ): Vector[String] =
    Vector(
      Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty),
      Some("org.goldenport.cncf.component")
    ).flatten.distinct

  private def _load_scala_module(
    loader: ClassLoader,
    classname: String
  ): Option[AnyRef] = {
    val loaders = Vector(
      Option(loader),
      Option(Thread.currentThread.getContextClassLoader)
    ).flatten.distinct
    loaders.iterator.flatMap { cl =>
      try {
        val cls = Class.forName(classname, true, cl)
        val field = cls.getField("MODULE$")
        Option(field.get(null).asInstanceOf[AnyRef])
      } catch {
        case _: Throwable => None
      }
    }.toSeq.headOption
  }

  private def _with_control(
    field: ResolvedWebField,
    control: WebDescriptor.FormControl,
    source: Source
  ): ResolvedWebField = {
    val mergedcontrol = _merge_control(field.control, control)
    field.copy(
      control = mergedcontrol,
      hidden = mergedcontrol.hidden,
      system = mergedcontrol.system,
      values = mergedcontrol.values,
      multiple = mergedcontrol.multiple,
      readonly = mergedcontrol.readonly,
      placeholder = control.placeholder.orElse(field.placeholder),
      help = control.help.orElse(field.help),
      validation = mergedcontrol.validation,
      source = source
    )
  }

  private def _merge_control(
    base: WebDescriptor.FormControl,
    overridecontrol: WebDescriptor.FormControl
  ): WebDescriptor.FormControl =
    WebDescriptor.FormControl(
      controlType = overridecontrol.controlType.orElse(base.controlType),
      hidden = base.hidden || overridecontrol.hidden,
      system = base.system || overridecontrol.system,
      values = if (overridecontrol.values.nonEmpty) overridecontrol.values else base.values,
      multiple = base.multiple || overridecontrol.multiple,
      required = overridecontrol.required.orElse(base.required),
      readonly = base.readonly || overridecontrol.readonly,
      label = overridecontrol.label.orElse(base.label),
      placeholder = overridecontrol.placeholder.orElse(base.placeholder),
      help = overridecontrol.help.orElse(base.help),
      defaultValue = overridecontrol.defaultValue.orElse(base.defaultValue),
      validation = _merge_validation(base.validation, overridecontrol.validation)
    )

  private def _merge_validation(
    base: WebValidationHints,
    overridehints: WebValidationHints
  ): WebValidationHints =
    WebValidationHints(
      min = _max_decimal(base.min, overridehints.min),
      max = _min_decimal(base.max, overridehints.max),
      step = overridehints.step.orElse(base.step),
      minLength = _max_int(base.minLength, overridehints.minLength),
      maxLength = _min_int(base.maxLength, overridehints.maxLength),
      pattern = overridehints.pattern.orElse(base.pattern)
    )

  private def _max_decimal(
    lhs: Option[BigDecimal],
    rhs: Option[BigDecimal]
  ): Option[BigDecimal] =
    (lhs, rhs) match {
      case (Some(a), Some(b)) => Some(a.max(b))
      case _ => lhs.orElse(rhs)
    }

  private def _min_decimal(
    lhs: Option[BigDecimal],
    rhs: Option[BigDecimal]
  ): Option[BigDecimal] =
    (lhs, rhs) match {
      case (Some(a), Some(b)) => Some(a.min(b))
      case _ => lhs.orElse(rhs)
    }

  private def _max_int(
    lhs: Option[Int],
    rhs: Option[Int]
  ): Option[Int] =
    (lhs, rhs) match {
      case (Some(a), Some(b)) => Some(a.max(b))
      case _ => lhs.orElse(rhs)
    }

  private def _min_int(
    lhs: Option[Int],
    rhs: Option[Int]
  ): Option[Int] =
    (lhs, rhs) match {
      case (Some(a), Some(b)) => Some(a.min(b))
      case _ => lhs.orElse(rhs)
    }

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
