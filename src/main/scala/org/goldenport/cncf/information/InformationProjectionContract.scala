package org.goldenport.cncf.information

/*
 * Canonical public projection metadata for generated Information.
 *
 * This declares reusable consumer descriptors only.  It neither defines a
 * protocol operation nor registers a transport route.
 *
 * @since   Sep.  1, 2026
 * @version Sep.  1, 2026
 * @author  ASAMI, Tomoharu
 */
enum InformationProjectionSurface(val name: String) {
  case Help extends InformationProjectionSurface("help")
  case Http extends InformationProjectionSurface("http")
  case Form extends InformationProjectionSurface("form")
  case Json extends InformationProjectionSurface("json")
  case Yaml extends InformationProjectionSurface("yaml")
  case Xml extends InformationProjectionSurface("xml")
  case Schema extends InformationProjectionSurface("schema")
  case OpenApi extends InformationProjectionSurface("openapi")
  case Mcp extends InformationProjectionSurface("mcp")
}

enum InformationProjectionValueCategory(val name: String) {
  case Scalar extends InformationProjectionValueCategory("scalar")
  case StructuredConflict extends InformationProjectionValueCategory("structured-conflict")
}

final case class InformationProjectionField(
  name: String,
  datatype: String,
  required: Boolean = false,
  systemManaged: Boolean = false,
  readOnly: Boolean = false,
  transportPrecondition: Boolean = false,
  valueCategory: InformationProjectionValueCategory = InformationProjectionValueCategory.Scalar
)

final case class InformationProjectionDescriptor(
  name: String,
  fields: Vector[InformationProjectionField],
  excludedFields: Set[String],
  surfaces: Vector[InformationProjectionSurface]
) {
  def field(name: String): Option[InformationProjectionField] =
    fields.find(_.name == name)
}

final case class InformationConditionalUpdateProjection(
  application: InformationProjectionDescriptor,
  observedRevision: InformationProjectionField,
  conflictValues: Vector[InformationProjectionField],
  surfaces: Vector[InformationProjectionSurface]
)

/**
 * Consumer-neutral metadata for projecting the generated [[Information]]
 * entity.  Revision is system output metadata; observedRevision is a
 * conditional transport precondition and never an Information domain field.
 */
object InformationProjectionContract {
  val surfaces: Vector[InformationProjectionSurface] =
    InformationProjectionSurface.values.toVector

  val output: InformationProjectionDescriptor =
    InformationProjectionDescriptor(
      name = "information-output",
      fields = Vector(
        InformationProjectionField("id", "EntityId", required = true, readOnly = true),
        InformationProjectionField("domain", "String", required = true, readOnly = true),
        InformationProjectionField("workingData", "Record", required = true, readOnly = true),
        InformationProjectionField("state", "InformationLifecycleState", required = true, readOnly = true),
        InformationProjectionField(
          "conflicts",
          "InformationConflict[]",
          readOnly = true,
          valueCategory = InformationProjectionValueCategory.StructuredConflict
        ),
        InformationProjectionField(
          "revision",
          "EntityRevision",
          required = true,
          systemManaged = true,
          readOnly = true
        )
      ),
      excludedFields = Set("rawData"),
      surfaces = surfaces
    )

  val createApplicationInput: InformationProjectionDescriptor =
    InformationProjectionDescriptor(
      name = "information-create-application-input",
      fields = Vector(
        InformationProjectionField("domain", "String", required = true),
        InformationProjectionField("workingData", "Record", required = true)
      ),
      excludedFields = Set("revision", "rawData"),
      surfaces = surfaces
    )

  val conditionalUpdate: InformationConditionalUpdateProjection =
    InformationConditionalUpdateProjection(
      application = InformationProjectionDescriptor(
        name = "information-update-application-input",
        fields = Vector(InformationProjectionField("workingData", "Record", required = true)),
        excludedFields = Set("revision", "observedRevision", "rawData"),
        surfaces = surfaces
      ),
      observedRevision = InformationProjectionField(
        "observedRevision",
        "EntityRevision",
        required = true,
        transportPrecondition = true
      ),
      conflictValues = Vector(
        InformationProjectionField(
          "staleConflict",
          "InformationConflict",
          readOnly = true,
          valueCategory = InformationProjectionValueCategory.StructuredConflict
        )
      ),
      surfaces = surfaces
    )

  val descriptors: Vector[InformationProjectionDescriptor] =
    Vector(output, createApplicationInput, conditionalUpdate.application)

  val canonicalEntityClass: Class[?] = classOf[entity.Information]
}
