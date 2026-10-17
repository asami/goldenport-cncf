package logicalui

import java.time.Instant
import domain.logicalui.DomainLogicalUiDefinitions
import org.goldenport.Consequence
import org.goldenport.cncf.display.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.record.Record
import org.goldenport.schema.WebColumn
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
/** Hand-written development extension of the unchanged generated definitions. */
object DisplayMutationServiceDomain {
  import GeneratedLogicalUiAbi.*
  import DisplayMutation.*
  import DisplayMutationBinding.*

  final case class Root(id: EntityId, record: Record) extends EntityPersistable {
    def toRecord(): Record = record
  }
  final case class PatchShape(title: Update[String], description: Update[String],
    number: Update[BigDecimal], flag: Update[Boolean], instant: Update[Instant], nullable: Update[String])
  enum BodyMode { case Absent, Inline, Overflow, SetNull }
  final case class CreateShape(identity: Option[EntityId], title: String, description: String,
    number: BigDecimal, flag: Boolean, instant: Instant, nullable: Option[String],
    stage: String, business: String, audit: String, bodyMode: BodyMode, body: String)
  val lifecycleCapabilities: Capabilities = Capabilities(Support.Supported, Support.Unsupported,
    Support.Supported, Support.Supported, Support.Supported)
  def createSeed(identity: Option[EntityId], bodyMode: BodyMode = BodyMode.Overflow,
    body: String = "Server body 日本 Ω\n" * 400): CreateShape =
    CreateShape(identity, "", "server description", BigDecimal("42.1250"), false,
      instant, None, "draft", "server business", "server audit", bodyMode, body)

  def createPersistent(collectionId: EntityCollectionId): EntityPersistentCreate[CreateShape] = new EntityPersistentCreate[CreateShape] {
    def id(entity: CreateShape): Option[EntityId] = entity.identity
    def collection(entity: CreateShape): EntityCollectionId = collectionId
    def toRecord(entity: CreateShape): Record = {
      val values = Vector[(String, Any)]("title" -> entity.title, "description" -> entity.description,
        "number" -> entity.number, "flag" -> entity.flag, "instant" -> entity.instant,
        "stage" -> entity.stage, "business" -> entity.business, "audit" -> entity.audit) ++
        entity.nullable.toVector.map(value => "nullable" -> value) ++ (entity.bodyMode match {
          case BodyMode.Absent => Vector.empty
          case BodyMode.SetNull => Vector("content" -> Update.SetNull)
          case BodyMode.Inline => Vector("content" -> entity.body.take(24))
          case BodyMode.Overflow => Vector("content" -> entity.body)
        })
      Record.dataAuto(values*)
    }
    override def toStoreRecord(entity: CreateShape): Record = storageRecord(toRecord(entity))
  }
  val seed: PatchShape = PatchShape(Update.noop[String], Update.noop[String], Update.noop[BigDecimal],
    Update.noop[Boolean], Update.noop[Instant], Update.noop[String])
  val fieldIds: Vector[String] = Vector("title", "description", "number", "flag", "instant", "nullable")
  val instant: Instant = Instant.parse("2026-10-15T03:04:05.123456789Z")
  val domainFailure: Consequence.Failure[Nothing] = Consequence.argumentInvalid("display-domain-title-rejected")

  val definitions: Vector[Definition] = {
    val fields = Vector(
      FieldDefinition("title", "Title", PresentationRole.Title, ValueDefinition("ess.title", Vector(ValueKind.Text))),
      FieldDefinition("description", "Description", PresentationRole.Content, ValueDefinition("ess.description", Vector(ValueKind.Text))),
      FieldDefinition("number", "Number", PresentationRole.Content, ValueDefinition("native.number", Vector(ValueKind.Number))),
      FieldDefinition("flag", "Flag", PresentationRole.Content, ValueDefinition("native.flag", Vector(ValueKind.Boolean))),
      FieldDefinition("instant", "Instant", PresentationRole.Content, ValueDefinition("native.instant", Vector(ValueKind.Timestamp))),
      FieldDefinition("nullable", "Nullable", PresentationRole.Content, ValueDefinition("native.nullable", Vector(ValueKind.Text, ValueKind.Missing))),
      FieldDefinition("stage", "Stage", PresentationRole.Content, ValueDefinition("native.stage", Vector(ValueKind.Text))))
    val detail = DomainLogicalUiDefinitions.definitions(1)
    Vector(detail.copy(provenance = SourceProvenance("cncf.development.display-update", "typed-normal-component", "fixture-1"),
      screen = DetailDefinition("Normal update", Vector(SectionDefinition("main", None, fields)), Vector.empty)))
  }
  val admitted: AdmittedDefinitions = GeneratedLogicalUiAbi.admitC(definitions).TAKE

  def persistent: EntityPersistent[Root] = new EntityPersistent[Root] {
    def id(entity: Root): EntityId = entity.id
    def toRecord(entity: Root): Record = entity.record
    override def toStoreRecord(entity: Root): Record = storageRecord(entity.record).upsertSingle("id", entity.id.value)
    def fromRecord(record: Record): Consequence[Root] = {
      val identity = record.getAny("id") match {
        case Some(id: EntityId) => Consequence.success(id)
        case Some(value: String) => EntityId.parse(value)
        case _ => Consequence.argumentMissing("id")
      }
      identity.map { id =>
        val decoded = Record(record.fields.filterNot(_.key == "cncf_revision").map { field => (field.key, field.value.single) match {
          case ("title" | "description" | "nullable", text: String) => field.copy(value = org.goldenport.record.Field.Value.Single(_text_value(text)))
          case _ => field
        } })
        Root(id, decoded.upsertSingle("id", id))
      }
    }
  }
  def patchPersistent(collectionId: EntityCollectionId): EntityPersistentUpdate[PatchShape] = new EntityPersistentUpdate[PatchShape] {
    def collection(patch: PatchShape): EntityCollectionId = collectionId
    def toRecord(patch: PatchShape): Record = Record.dataAuto("title" -> patch.title, "description" -> patch.description,
      "number" -> patch.number, "flag" -> patch.flag, "instant" -> patch.instant, "nullable" -> patch.nullable)
    override def toStoreRecord(patch: PatchShape): Record = Record.dataAuto("title" -> _stored_update(patch.title)(_text_storage),
      "description" -> _stored_update(patch.description)(_text_storage), "number" -> _stored_update(patch.number)(_.bigDecimal.toString),
      "flag" -> _stored_update(patch.flag)(value => if (value) "true" else "false"),
      "instant" -> _stored_update(patch.instant)(_.toString), "nullable" -> _stored_update(patch.nullable)(_text_storage))
    def fromRecord(record: Record): Consequence[PatchShape] = Consequence.operationInvalid("patch-is-write-only")
  }
  private def _stored_update[A](value: Update[A])(encode: A => String): Update[String] =
    value.fold(Update.noop[String], actual => Update.set(encode(actual)), Update.setNull[String])
  def storageRecord(record: Record): Record = Record(record.fields.map { field =>
    val value = (field.key, field.value.single) match {
      case ("title" | "description" | "nullable", actual: String) => _text_storage(actual)
      case ("number", actual: BigDecimal) => actual.bigDecimal.toString
      case ("instant", actual: Instant) => actual.toString
      case ("flag", actual: Boolean) => if (actual) "true" else "false"
      case (_, actual) => actual
    }
    field.copy(value = org.goldenport.record.Field.Value.Single(value))
  })
  private def _text_storage(text: String): String = "utf16:" + text.iterator.map(character => f"${character.toInt}%04x").mkString
  private def _text_value(text: String): String = {
    if (text.startsWith("utf16:")) text.drop(6).grouped(4).map(value => Integer.parseInt(value, 16).toChar).mkString
    else text
  }
  def binding(source: DisplayProtocol.Source, ownerId: String, aggregate: Boolean,
    conversion: String => Unit = _ => (), validation: () => Unit = () => (),
    definitionSet: AdmittedDefinitions = admitted, screenId: String = "capture-detail",
    target: UpdateTarget => UpdateTarget = (value: UpdateTarget) => value, aliasDescription: Boolean = false,
    capabilities: Capabilities = Capabilities(Support.Supported, Support.Unsupported, Support.Unsupported, Support.Supported, Support.Unsupported)): Binding[PatchShape] = {
    def _field_(name: String, kinds: Vector[ValueKind], convert: (PatchShape, DisplayValue) => Consequence[PatchShape]) =
      FieldBinding[PatchShape](FieldLocator("main", name), "update." + name,
        target(if (aggregate) UpdateTarget.Aggregate(ownerId, if (aliasDescription && name == "description") "title" else name)
          else UpdateTarget.Entity(ownerId, if (aliasDescription && name == "description") "title" else name)), kinds,
        WebColumn.empty, (patch, value) => {
          conversion(name)
          if (aliasDescription && name == "description") value match {
            case DisplayValue.Text(text) => Consequence.success(patch.copy(title = Update.set(text)))
            case _ => inputInvalidC("VALUE_KIND_INVALID", "description")
          } else convert(patch, value)
        })
    val fields = Vector(
      _field_("title", Vector(ValueKind.Text), (patch, value) => value match {
        case DisplayValue.Text(text) => Consequence.success(patch.copy(title = Update.set(text)))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "title")
      }),
      _field_("description", Vector(ValueKind.Text), (patch, value) => value match {
        case DisplayValue.Text(text) => Consequence.success(patch.copy(description = Update.set(text)))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "description")
      }),
      _field_("number", Vector(ValueKind.Number), (patch, value) => value match {
        case DisplayValue.Number(number) => Consequence.success(patch.copy(number = Update.set(number)))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "number")
      }),
      _field_("flag", Vector(ValueKind.Boolean), (patch, value) => value match {
        case DisplayValue.Boolean(flag) => Consequence.success(patch.copy(flag = Update.set(flag)))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "flag")
      }),
      _field_("instant", Vector(ValueKind.Timestamp), (patch, value) => value match {
        case DisplayValue.Timestamp(instant) => Consequence.success(patch.copy(instant = Update.set(instant)))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "instant")
      }),
      _field_("nullable", Vector(ValueKind.Text, ValueKind.Missing), (patch, value) => value match {
        case DisplayValue.Text(text) => Consequence.success(patch.copy(nullable = Update.set(text)))
        case DisplayValue.Missing => Consequence.success(patch.copy(nullable = Update.setNull[String]))
        case _ => inputInvalidC("VALUE_KIND_INVALID", "nullable")
      }))
    DisplayMutationBinding.admitC(source, definitionSet, screenId, "normal-update-1",
      capabilities,
      fields, Set.empty, patch => {
        validation()
        if (Update.toChangesRecord(patchPersistent(EntityCollectionId("fixture", "unused", "patch")).toRecord(patch))
            .getString("title").exists(_.trim.isEmpty)) domainFailure
        else Consequence.success(patch)
      }).TAKE
  }
  def createBinding(source: DisplayProtocol.Source, ownerId: String, aggregate: Boolean,
    conversion: String => Unit = _ => (), validation: () => Unit = () => (),
    definitionSet: AdmittedDefinitions = admitted, screenId: String = "capture-detail",
    capabilities: Capabilities = lifecycleCapabilities): Binding[CreateShape] = {
    val metadata = binding(source, ownerId, aggregate, definitionSet = definitionSet,
      screenId = screenId, capabilities = capabilities)
    val fields = metadata.descriptorC(Availability.enabled).TAKE.fields.filter(_.bindingId.nonEmpty).map { registration =>
      val target = if (aggregate) UpdateTarget.Aggregate(ownerId, registration.location.fieldId)
        else UpdateTarget.Entity(ownerId, registration.location.fieldId)
      FieldBinding[CreateShape](registration.location, registration.bindingId.get, target,
        registration.acceptedKinds, registration.web, (input, value) => {
          conversion(registration.location.fieldId)
          (registration.location.fieldId, value) match {
            case ("title", DisplayValue.Text(text)) => Consequence.success(input.copy(title = text))
            case ("description", DisplayValue.Text(text)) => Consequence.success(input.copy(description = text))
            case ("number", DisplayValue.Number(number)) => Consequence.success(input.copy(number = number))
            case ("flag", DisplayValue.Boolean(flag)) => Consequence.success(input.copy(flag = flag))
            case ("instant", DisplayValue.Timestamp(instant)) => Consequence.success(input.copy(instant = instant))
            case ("nullable", DisplayValue.Text(text)) => Consequence.success(input.copy(nullable = Some(text)))
            case ("nullable", DisplayValue.Missing) => Consequence.success(input.copy(nullable = None))
            case _ => inputInvalidC("VALUE_KIND_INVALID", "create.fields")
          }
        })
    }
    DisplayMutationBinding.admitC(source, definitionSet, screenId, "normal-update-1", capabilities, fields, Set.empty,
      input => { validation(); if (input.title.trim.isEmpty) domainFailure else Consequence.success(input) }).TAKE
  }
  def command(root: Root, patch: PatchShape): Consequence[Root] = {
    val changes = Update.toChangesRecord(patchPersistent(root.id.collection).toRecord(patch))
    val merged = changes.fields.foldLeft(root.record) { (record, field) =>
      record.upsertSingle(field.key, field.value.single)
    }
    if (merged.getString("title").exists(_.trim.nonEmpty)) Consequence.success(root.copy(record = merged)) else domainFailure
  }
  def row(resource: Root => Consequence[DisplayModel.Resource]): DisplayProjection.RowBinding[Root] =
    DisplayProjection.RowBinding(resource, Vector(
      DisplayProjection.ValueBinding(Vector("ess.title"), root => Consequence.success(Vector(DisplayValue.Text(root.record.getString("title").get)))),
      DisplayProjection.ValueBinding(Vector("ess.description"), root => Consequence.success(Vector(DisplayValue.Text(root.record.getString("description").get)))),
      DisplayProjection.ValueBinding(Vector("native.number"), root => _number_c(root.record.getAny("number")).map(value => Vector(DisplayValue.Number(value)))),
      DisplayProjection.ValueBinding(Vector("native.flag"), root => _flag_c(root.record.getAny("flag")).map(value => Vector(DisplayValue.Boolean(value)))),
      DisplayProjection.ValueBinding(Vector("native.instant"), root => _instant_c(root.record.getAny("instant")).map(value => Vector(DisplayValue.Timestamp(value)))),
      DisplayProjection.ValueBinding(Vector("native.nullable"), root => root.record.getAny("nullable") match {
        case None | Some(Update.SetNull) => Consequence.success(Vector(DisplayValue.Missing))
        case Some(value: String) => Consequence.success(Vector(DisplayValue.Text(value)))
        case _ => inputInvalidC("VALUE_INVALID", "nullable")
      }),
      DisplayProjection.ValueBinding(Vector("native.stage"), root => Consequence.success(Vector(DisplayValue.Text(root.record.getString("stage").get))))
    ), Vector.empty)

  private def _number_c(value: Option[Any]): Consequence[BigDecimal] = value match {
    case Some(number: BigDecimal) => Consequence.success(number)
    case Some(text: String) => Consequence(BigDecimal(text))
    case _ => inputInvalidC("VALUE_INVALID", "number")
  }
  private def _flag_c(value: Option[Any]): Consequence[Boolean] = value match {
    case Some(flag: Boolean) => Consequence.success(flag)
    case Some("true") => Consequence.success(true)
    case Some("false") => Consequence.success(false)
    case _ => inputInvalidC("VALUE_INVALID", "flag")
  }
  private def _instant_c(value: Option[Any]): Consequence[Instant] = value match {
    case Some(instant: Instant) => Consequence.success(instant)
    case Some(text: String) => Consequence(Instant.parse(text))
    case _ => inputInvalidC("VALUE_INVALID", "instant")
  }

  val privateConstructionDenied: Boolean = !scala.compiletime.testing.typeChecks("""
    new org.goldenport.cncf.display.DisplayMutationRuntime.Binding() {}
  """)
  val erasedWriterDenied: Boolean = !scala.compiletime.testing.typeChecks("""
    org.goldenport.cncf.display.DisplayMutationRuntime.write(org.goldenport.record.Record.empty)
  """)
  val captureConstructionDenied: Boolean = !scala.compiletime.testing.typeChecks("""
    new org.goldenport.cncf.entity.EntityVersionedMutationCaptureOutcome(null, null)
  """)
  val lifecycleCaptureConstructionDenied: Boolean = !scala.compiletime.testing.typeChecks("""
    new org.goldenport.cncf.display.DisplayMutationRuntimeEntry.Captured.Created(null)
  """)
  def entityRegistration(binding: Binding[PatchShape], ownerId: String, collection: EntityCollection[Root],
    seed: PatchShape, persistent: EntityPersistentUpdate[PatchShape], availability: ExecutionContext => Consequence[Availability]): Consequence[DisplayMutationRuntime.Binding] =
    DisplayMutationRuntime.entityUpdateC[Root, PatchShape](binding, ownerId, collection, fieldIds, seed, persistent, availability)
  def aggregateRegistration(binding: Binding[PatchShape], aggregateName: String, seed: PatchShape,
    persistent: EntityPersistentUpdate[PatchShape], command: (Root, PatchShape) => Consequence[Root],
    availability: ExecutionContext => Consequence[Availability]): Consequence[DisplayMutationRuntime.Binding] =
    DisplayMutationRuntime.aggregateUpdateC[Root, PatchShape](binding, aggregateName, "changeFields", fieldIds, seed, persistent, command, availability)
  def entityLifecycleRegistration(updateBinding: Binding[PatchShape], ownerId: String, collection: EntityCollection[Root],
    createBinding: Binding[CreateShape], createSeed: CreateShape, validateDelete: Root => Consequence[Unit],
    availability: ExecutionContext => Consequence[Availability]): Consequence[DisplayMutationRuntime.Binding] =
    DisplayMutationRuntime.entityLifecycleC(updateBinding, ownerId, collection, fieldIds, seed,
      patchPersistent(collection.descriptor.collectionId), createBinding, createSeed,
      createPersistent(collection.descriptor.collectionId), validateDelete, availability)
  def aggregateLifecycleRegistration(updateBinding: Binding[PatchShape], aggregateName: String, collectionId: EntityCollectionId,
    createBinding: Binding[CreateShape], createSeed: CreateShape, updateCommand: (Root, PatchShape) => Consequence[Root],
    createCommand: CreateShape => Consequence[CreateShape], validateDelete: Root => Consequence[Unit],
    availability: ExecutionContext => Consequence[Availability]): Consequence[DisplayMutationRuntime.Binding] =
    DisplayMutationRuntime.aggregateLifecycleC[Root, CreateShape, PatchShape](updateBinding, aggregateName, "changeFields",
      fieldIds, seed, patchPersistent(collectionId), updateCommand, createBinding, createSeed, createPersistent(collectionId),
      "createRoot", createCommand, validateDelete, availability)
}
