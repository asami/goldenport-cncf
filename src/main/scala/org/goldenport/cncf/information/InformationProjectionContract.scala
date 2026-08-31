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
  case SanitizedApplicationData extends InformationProjectionValueCategory("sanitized-application-data")
}

enum InformationProjectionSurfaceStatus(val name: String) {
  case Applicable extends InformationProjectionSurfaceStatus("applicable")
  case Inapplicable extends InformationProjectionSurfaceStatus("inapplicable")
}

final case class InformationProjectionSurfaceMetadata(
  surface: InformationProjectionSurface,
  status: InformationProjectionSurfaceStatus,
  reason: Option[String] = None
) {
  def applicable: Boolean =
    status == InformationProjectionSurfaceStatus.Applicable
}

final case class InformationProjectionField(
  name: String,
  datatype: String,
  required: Boolean = false,
  systemManaged: Boolean = false,
  readOnly: Boolean = false,
  transportPrecondition: Boolean = false,
  valueCategory: InformationProjectionValueCategory = InformationProjectionValueCategory.Scalar,
  projectedFieldPaths: Vector[String] = Vector.empty,
  excludedFieldPaths: Set[String] = Set.empty
)

final case class InformationProjectionDescriptor(
  name: String,
  fields: Vector[InformationProjectionField],
  excludedFields: Set[String],
  surfaces: Vector[InformationProjectionSurfaceMetadata]
) {
  def field(name: String): Option[InformationProjectionField] =
    fields.find(_.name == name)
}

final case class InformationConditionalUpdateFailureProjection(
  reason: String,
  policy: String,
  expectedRevision: InformationProjectionField,
  actualRevision: InformationProjectionField
)

final case class InformationConditionalUpdateProjection(
  application: InformationProjectionDescriptor,
  observedRevision: InformationProjectionField,
  staleFailure: InformationConditionalUpdateFailureProjection,
  surfaces: Vector[InformationProjectionSurfaceMetadata]
)

/**
 * Consumer-neutral metadata for projecting the generated [[Information]]
 * entity.  Revision is system output metadata; observedRevision is a
 * conditional transport precondition and never an Information domain field.
 */
object InformationProjectionContract {
  private val _surfaces: Vector[InformationProjectionSurface] =
    InformationProjectionSurface.values.toVector

  private val _no_existing_information_access_seam =
    "No existing Information access seam: only the System Admin Information Web/HTTP read projection exists."

  private val _output_surfaces: Vector[InformationProjectionSurfaceMetadata] =
    _surface_metadata(Set(InformationProjectionSurface.Http))

  private val _input_surfaces: Vector[InformationProjectionSurfaceMetadata] =
    _surface_metadata(Set.empty)

  val output: InformationProjectionDescriptor =
    InformationProjectionDescriptor(
      name = "information-output",
      fields = Vector(
        InformationProjectionField("id", "EntityId", required = true, readOnly = true),
        InformationProjectionField("domain", "String", required = true, readOnly = true),
        InformationProjectionField(
          "workingData",
          "InformationProfileOutput",
          required = true,
          readOnly = true,
          valueCategory = InformationProjectionValueCategory.SanitizedApplicationData,
          projectedFieldPaths = Vector("title"),
          excludedFieldPaths = Set("providerPayload")
        ),
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
      excludedFields = Set("rawData", "providerPayload"),
      surfaces = _output_surfaces
    )

  val createApplicationInput: InformationProjectionDescriptor =
    InformationProjectionDescriptor(
      name = "information-create-application-input",
      fields = Vector(
        InformationProjectionField("domain", "String", required = true),
        InformationProjectionField("workingData", "Record", required = true)
      ),
      excludedFields = Set("revision", "rawData"),
      surfaces = _input_surfaces
    )

  val conditionalUpdate: InformationConditionalUpdateProjection =
    InformationConditionalUpdateProjection(
      application = InformationProjectionDescriptor(
        name = "information-update-application-input",
        fields = Vector(InformationProjectionField("workingData", "Record", required = true)),
        excludedFields = Set("revision", "observedRevision", "rawData"),
        surfaces = _input_surfaces
      ),
      observedRevision = InformationProjectionField(
        "observedRevision",
        "EntityRevision",
        required = true,
        transportPrecondition = true
      ),
      staleFailure = InformationConditionalUpdateFailureProjection(
        reason = "stale-entity-revision",
        policy = "entity.optimistic-concurrency",
        expectedRevision = InformationProjectionField(
          "expectedRevision",
          "EntityRevision",
          required = true,
          readOnly = true
        ),
        actualRevision = InformationProjectionField(
          "actualRevision",
          "EntityRevision",
          readOnly = true,
          systemManaged = true
        )
      ),
      surfaces = _input_surfaces
    )

  val descriptors: Vector[InformationProjectionDescriptor] =
    Vector(output, createApplicationInput, conditionalUpdate.application)

  val canonicalEntityClass: Class[?] = classOf[entity.Information]

  def projectOutputApplicationData(workingData: org.goldenport.record.Record): org.goldenport.record.Record = {
    val paths = output.field("workingData").map(_.projectedFieldPaths.toSet).getOrElse(Set.empty)
    org.goldenport.record.Record(workingData.fields.filter(field => paths.contains(field.key)))
  }

  private def _surface_metadata(
    applicable: Set[InformationProjectionSurface]
  ): Vector[InformationProjectionSurfaceMetadata] =
    _surfaces.map { surface =>
      if (applicable.contains(surface))
        InformationProjectionSurfaceMetadata(
          surface,
          InformationProjectionSurfaceStatus.Applicable
        )
      else
        InformationProjectionSurfaceMetadata(
          surface,
          InformationProjectionSurfaceStatus.Inapplicable,
          Some(_no_existing_information_access_seam)
        )
    }
}
