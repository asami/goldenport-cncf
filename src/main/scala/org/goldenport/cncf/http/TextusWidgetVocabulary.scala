package org.goldenport.cncf.http

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */

sealed trait TextusWidgetCategory {
  def name: String
}

object TextusWidgetCategory {
  case object Display extends TextusWidgetCategory {
    val name = "display"
  }
  case object Layout extends TextusWidgetCategory {
    val name = "layout"
  }
  case object Action extends TextusWidgetCategory {
    val name = "action"
  }
  case object Feedback extends TextusWidgetCategory {
    val name = "feedback"
  }
  case object FormEdit extends TextusWidgetCategory {
    val name = "form-edit"
  }
  case object RuntimeHelper extends TextusWidgetCategory {
    val name = "runtime-helper"
  }

  val values: Vector[TextusWidgetCategory] = Vector(
    Display,
    Layout,
    Action,
    Feedback,
    FormEdit,
    RuntimeHelper
  )

  def fromName(name: String): Option[TextusWidgetCategory] =
    values.find(_.name == name)
}

final case class TextusWidgetDefinition(
  canonicalName: String,
  category: TextusWidgetCategory,
  acceptedAliases: Vector[String] = Vector.empty,
  requiredAttributes: Vector[String] = Vector.empty,
  optionalAttributes: Vector[String] = Vector.empty,
  sourceBinding: Boolean = false,
  viewBinding: Boolean = false,
  columnsBinding: Boolean = false,
  actionBinding: Boolean = false,
  navigationBinding: Boolean = false,
  implemented: Boolean = true
) {
  def names: Vector[String] = canonicalName +: acceptedAliases

  def accepts(name: String): Boolean =
    names.exists(_ == TextusWidgetName.key(name))

  def project(
    name: String,
    attributes: Map[String, String] = Map.empty,
    metadatasources: Vector[String] = TextusWidgetVocabulary.DEFAULT_METADATA_SOURCES
  ): TextusWidgetProjection = TextusWidgetProjection(
    requestedName = name,
    canonicalName = canonicalName,
    category = category.name,
    implemented = implemented,
    acceptedAliases = acceptedAliases,
    requiredAttributes = requiredAttributes,
    optionalAttributes = optionalAttributes,
    attributes = attributes,
    metadataSources = metadatasources,
    sourceBinding = sourceBinding,
    viewBinding = viewBinding,
    columnsBinding = columnsBinding,
    actionBinding = actionBinding,
    navigationBinding = navigationBinding
  )
}

final case class TextusWidgetProjection(
  requestedName: String,
  canonicalName: String,
  category: String,
  implemented: Boolean,
  acceptedAliases: Vector[String],
  requiredAttributes: Vector[String],
  optionalAttributes: Vector[String],
  attributes: Map[String, String],
  metadataSources: Vector[String],
  sourceBinding: Boolean,
  viewBinding: Boolean,
  columnsBinding: Boolean,
  actionBinding: Boolean,
  navigationBinding: Boolean
)

object TextusWidgetName {
  def key(name: String): String = name.trim

  def dashAlias(canonicalname: String): String =
    canonicalname.replace("textus:", "textus-")
}

object TextusWidgetVocabulary {
  val DEFAULT_METADATA_SOURCES: Vector[String] = Vector(
    "cml-schema-view",
    "web-descriptor",
    "form-descriptor",
    "operation-result",
    "page-context",
    "widget-attributes"
  )

  val definitions: Vector[TextusWidgetDefinition] = Vector(
    _display("textus:result-view", aliases = Some(Vector("textus-result-view")), source = true),
    _display("textus:table", aliases = Some(Vector.empty), source = true, view = true, columns = true, navigation = true),
    _display("textus:record-card", source = true, view = true, columns = true, navigation = true),
    _display("textus:card-list", source = true, view = true, columns = true, navigation = true),
    _display("textus:line-list", source = true, view = true, columns = true, navigation = true),
    _display("textus:field-list", source = true, view = true),
    _display("textus:description-list", source = true, view = true, columns = true),
    _display("textus:html-field", source = true),
    _display("textus:property-list", aliases = Some(Vector("textus-property-list")), source = true),
    _layout("textus:card"),
    _layout("textus:summary-card"),
    _layout("textus:pagination", source = true),
    _layout("textus:nav-list", source = true, navigation = true),
    _action("textus:action-link", source = true),
    _action("textus:action-form", source = true),
    _action("textus:action-group", source = true),
    _action("textus:action-card", source = true),
    _action("textus:confirm-action", source = true),
    _runtime("textus:hidden-context", source = true),
    _runtime("textus:operation-panel", source = true),
    _runtime("textus:job-panel", source = true),
    _runtime("textus:job-ticket", source = true),
    _runtime("textus:job-actions", source = true),
    _runtime("textus:candidate-list", source = true),
    _runtime("textus:knowledge-summary", source = true),
    _feedback("textus:error-panel", aliases = Some(Vector("textus-error-panel")), source = true),
    _feedback("textus:alert"),
    _feedback("textus:empty-state"),
    _feedback("textus:status-badge"),
    _feedback("textus:capability-message"),
    TextusWidgetDefinition(
      canonicalName = "textus:editable-line-list",
      category = TextusWidgetCategory.FormEdit,
      requiredAttributes = Vector("name"),
      optionalAttributes = Vector("source", "view", "columns", "row-template", "key", "add", "add-label", "new-rows", "empty", "colspan"),
      sourceBinding = true,
      viewBinding = true,
      columnsBinding = true,
      implemented = true
    )
  )

  private val _by_name: Map[String, TextusWidgetDefinition] =
    definitions.flatMap(x => x.names.map(TextusWidgetName.key(_) -> x)).toMap

  def lookup(name: String): Option[TextusWidgetDefinition] =
    _by_name.get(TextusWidgetName.key(name))

  def project(
    name: String,
    attributes: Map[String, String] = Map.empty,
    metadatasources: Vector[String] = DEFAULT_METADATA_SOURCES
  ): Either[String, TextusWidgetProjection] =
    lookup(name).map(_.project(name, attributes, metadatasources)).
      toRight(s"Unknown Textus widget: $name")

  private def _display(
    name: String,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false,
    view: Boolean = false,
    columns: Boolean = false,
    navigation: Boolean = false
  ): TextusWidgetDefinition = _definition(
    name,
    TextusWidgetCategory.Display,
    aliases = aliases,
    source = source,
    view = view,
    columns = columns,
    navigation = navigation
  )

  private def _layout(
    name: String,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false,
    navigation: Boolean = false
  ): TextusWidgetDefinition = _definition(
    name,
    TextusWidgetCategory.Layout,
    aliases = aliases,
    source = source,
    navigation = navigation
  )

  private def _action(
    name: String,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false
  ): TextusWidgetDefinition = _definition(
    name,
    TextusWidgetCategory.Action,
    aliases = aliases,
    source = source,
    action = true,
    navigation = true
  )

  private def _feedback(
    name: String,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false
  ): TextusWidgetDefinition = _definition(
    name,
    TextusWidgetCategory.Feedback,
    aliases = aliases,
    source = source
  )

  private def _runtime(
    name: String,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false
  ): TextusWidgetDefinition = _definition(
    name,
    TextusWidgetCategory.RuntimeHelper,
    aliases = aliases,
    source = source
  )

  private def _definition(
    name: String,
    category: TextusWidgetCategory,
    aliases: Option[Vector[String]] = None,
    source: Boolean = false,
    view: Boolean = false,
    columns: Boolean = false,
    action: Boolean = false,
    navigation: Boolean = false
  ): TextusWidgetDefinition = TextusWidgetDefinition(
    canonicalName = name,
    category = category,
    acceptedAliases = aliases.getOrElse(Vector(TextusWidgetName.dashAlias(name))),
    optionalAttributes = _optional_attributes(source, view, columns, action, navigation),
    sourceBinding = source,
    viewBinding = view,
    columnsBinding = columns,
    actionBinding = action,
    navigationBinding = navigation
  )

  private def _optional_attributes(
    source: Boolean,
    view: Boolean,
    columns: Boolean,
    action: Boolean,
    navigation: Boolean
  ): Vector[String] = Vector(
    if (source) Some("source") else None,
    if (view) Some("view") else None,
    if (columns) Some("columns") else None,
    if (action) Some("action") else None,
    if (navigation) Some("href") else None
  ).flatten
}
