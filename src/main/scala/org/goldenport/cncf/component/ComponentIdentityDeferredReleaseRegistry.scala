package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.jawn.JawnParser

import org.goldenport.Consequence
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate

/*
 * Executable authority for the four exact released CAR identity deferrals.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentIdentityDeferredReleaseEntry(
  componentid: ComponentId,
  release: String,
  legacyartifact: String,
  legacylocalid: String,
  migrationowner: String
)

private[cncf] final case class ComponentIdentityDeferredReleaseRegistry(
  entries: Vector[ComponentIdentityDeferredReleaseEntry]
) {
  import ComponentIdentityDeferredReleaseRegistry.*

  def admitDescriptorC(
    descriptor: ComponentDescriptor
  ): Consequence[DescriptorAdmission] =
    classifyC(descriptor).flatMap {
      case exact @ ExactDeferred(entry, effective) =>
        Consequence.success(DescriptorAdmission(descriptor, effective, Some(entry), exact))
      case Strict =>
        descriptor.requireCanonicalIdentityC.map(_ =>
          DescriptorAdmission(descriptor, descriptor, None, Strict)
        )
      case migration: MigrationRequired =>
        descriptor.requireCanonicalIdentityC.map(_ =>
          DescriptorAdmission(descriptor, descriptor, None, migration)
        )
      case InventoryError(diagnostic) =>
        Consequence.resourceInvalid(diagnostic)
    }

  def classifyC(
    descriptor: ComponentDescriptor
  ): Consequence[Classification] =
    if (descriptor == null)
      Consequence.componentInvalid(
        "component.identity.deferred-release.inventory-error reason=descriptor-required"
      )
    else
      descriptor.schemaVersion match {
        case Some(3) => Consequence.success(Strict)
        case Some(2) => _classify_legacy_c(descriptor)
        case actual =>
          Consequence.success(
            InventoryError(
              s"component.identity.deferred-release.inventory-error reason=descriptor-schema-version; expected=2-or-3; actual=${actual.map(_.toString).getOrElse("missing")}"
            )
          )
      }

  private def _classify_legacy_c(
    descriptor: ComponentDescriptor
  ): Consequence[Classification] = {
      val artifact = descriptor.name.map(_.trim).filter(_.nonEmpty)
      val localid = descriptor.componentName.map(_.trim).filter(_.nonEmpty)
      val release = descriptor.version.map(_.trim).filter(_.nonEmpty)
      val artifactentry = artifact.flatMap(value => entries.find(_.legacyartifact == value))
      artifactentry match {
        case Some(entry) =>
          release match {
            case Some(value) if value == entry.release && localid.contains(entry.legacylocalid) =>
              ComponentIdentityCompatibilityAdapter
                .projectDescriptorC(
                  descriptor.copy(
                    name = Some(entry.componentid.name),
                    componentName = Some(entry.componentid.name)
                  ),
                  entry.componentid
                )
                .map(projection => ExactDeferred(entry, projection.descriptor))
            case Some(value) if value == entry.release =>
              Consequence.success(
                InventoryError(
                  s"component.identity.deferred-release.inventory-error reason=local-id-mismatch; artifact=${entry.legacyartifact}; release=$value; expected=${entry.legacylocalid}; actual=${localid.getOrElse("missing")}"
                )
              )
            case Some(value) if _is_snapshot(value) =>
              Consequence.success(MigrationRequired(entry, value, "snapshot-release"))
            case Some(value) =>
              (_numeric_release(entry.release), _numeric_release(value)) match {
                case (Some(expected), Some(actual)) if _compare(actual, expected) > 0 =>
                  Consequence.success(MigrationRequired(entry, value, "greater-stable-release"))
                case (Some(expected), Some(actual)) if _compare(actual, expected) < 0 =>
                  Consequence.success(
                    InventoryError(
                      s"component.identity.deferred-release.inventory-error reason=lower-release; artifact=${entry.legacyartifact}; expected=${entry.release}; actual=$value"
                    )
                  )
                case _ =>
                  val reason =
                    if (_qualified_release_pattern.matches(value)) "incomparable-release"
                    else "malformed-release"
                  Consequence.success(
                    InventoryError(
                      s"component.identity.deferred-release.inventory-error reason=$reason; artifact=${entry.legacyartifact}; expected=${entry.release}; actual=$value"
                    )
                  )
              }
            case None =>
              Consequence.success(
                InventoryError(
                  s"component.identity.deferred-release.inventory-error reason=release-missing; artifact=${entry.legacyartifact}; expected=${entry.release}"
                )
              )
          }
        case None =>
          val partial = entries.filter { entry =>
            localid.contains(entry.legacylocalid) ||
              descriptor.componentId.contains(entry.componentid)
          }
          if (partial.nonEmpty)
            Consequence.success(
              InventoryError(
                s"component.identity.deferred-release.inventory-error reason=partial-match; artifact=${artifact.getOrElse("missing")}; local-id=${localid.getOrElse("missing")}; candidates=${partial.map(_.componentid.name).sorted.mkString(",")}"
              )
            )
          else
            Consequence.success(Strict)
      }
  }
}

private[cncf] object ComponentIdentityDeferredReleaseRegistry {
  val RESOURCE_PATH =
    "META-INF/cncf/component-identity-deferred-release-registry.json"
  val SCHEMA_VERSION =
    "cncf.component-identity-deferred-release-registry.v1"

  sealed trait Classification
  final case class DescriptorAdmission(
    raw: ComponentDescriptor,
    effective: ComponentDescriptor,
    entry: Option[ComponentIdentityDeferredReleaseEntry],
    classification: Classification
  )
  final case class ExactDeferred(
    entry: ComponentIdentityDeferredReleaseEntry,
    descriptor: ComponentDescriptor
  ) extends Classification
  case object Strict extends Classification
  final case class MigrationRequired(
    entry: ComponentIdentityDeferredReleaseEntry,
    actualrelease: String,
    reason: String
  ) extends Classification
  final case class InventoryError(diagnostic: String) extends Classification

  lazy val default: ComponentIdentityDeferredReleaseRegistry =
    loadC() match {
      case Consequence.Success(registry) => registry
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
    }

  def loadC(
    loader: ClassLoader = getClass.getClassLoader
  ): Consequence[ComponentIdentityDeferredReleaseRegistry] =
    try {
      Option(loader.getResourceAsStream(RESOURCE_PATH)) match {
        case Some(stream) =>
          try parseC(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
          finally stream.close()
        case None =>
          Consequence.resourceInvalid(
            s"component.identity.deferred-release.registry.invalid reason=resource-missing; path=$RESOURCE_PATH"
          )
      }
    } catch {
      case NonFatal(error) =>
        Consequence.resourceInvalid(
          s"component.identity.deferred-release.registry.invalid reason=resource-read-failed; path=$RESOURCE_PATH; cause=${Option(error.getMessage).getOrElse(error.getClass.getName)}"
        )
    }

  private[component] def parseC(
    text: String
  ): Consequence[ComponentIdentityDeferredReleaseRegistry] =
    _parse(text) match {
      case Right(registry) => Consequence.success(registry)
      case Left(message) =>
        Consequence.resourceInvalid(
          s"component.identity.deferred-release.registry.invalid $message"
        )
    }

  private def _parse(
    text: String
  ): Either[String, ComponentIdentityDeferredReleaseRegistry] =
    for {
      json <- _strict_json_parser.parse(text).left.map(error => s"reason=json-invalid; detail=${error.getMessage}")
      cursor = json.hcursor
      _ <- _only(cursor, Set("schemaVersion", "entries"), "registry")
      schema <- _required[String](cursor, "schemaVersion", "registry")
      _ <- Either.cond(
        schema == SCHEMA_VERSION,
        (),
        s"reason=schema-version; expected=$SCHEMA_VERSION; actual=$schema"
      )
      values <- _required[Vector[Json]](cursor, "entries", "registry")
      _ <- Either.cond(values.size == 4, (), s"reason=entry-count; expected=4; actual=${values.size}")
      entries <- _sequence(values.zipWithIndex.map { case (value, index) =>
        _entry(value.hcursor, index)
      })
      _ <- _unique(entries.map(_.componentid.name), "canonical-component-id")
      _ <- _unique(entries.map(_.legacyartifact), "legacy-artifact")
    } yield ComponentIdentityDeferredReleaseRegistry(entries.sortBy(_.componentid.name))

  private def _entry(
    cursor: HCursor,
    index: Int
  ): Either[String, ComponentIdentityDeferredReleaseEntry] = {
    val context = s"entry[$index]"
    for {
      _ <- _only(
        cursor,
        Set("canonicalComponentId", "release", "legacyArtifact", "legacyLocalId", "migrationOwner"),
        context
      )
      canonical <- _non_empty(cursor, "canonicalComponentId", context)
      componentid <- ComponentId.parseC(canonical) match {
        case Consequence.Success(value) => Right(value)
        case Consequence.Failure(conclusion) =>
          Left(s"reason=canonical-component-id; context=$context; detail=${conclusion.display}")
      }
      release <- _non_empty(cursor, "release", context)
      _ <- _validate_coordinate(componentid, release, context)
      artifact <- _non_empty(cursor, "legacyArtifact", context)
      localid <- _non_empty(cursor, "legacyLocalId", context)
      owner <- _non_empty(cursor, "migrationOwner", context)
      _ <- Either.cond(
        componentid.localId.value() == localid,
        (),
        s"reason=local-id-disagreement; context=$context; expected=${componentid.localId.value()}; actual=$localid"
      )
    } yield ComponentIdentityDeferredReleaseEntry(
      componentid,
      release,
      artifact,
      localid,
      owner
    )
  }

  private def _validate_coordinate(
    componentid: ComponentId,
    release: String,
    context: String
  ): Either[String, Unit] = {
    val result = ComponentReleaseCoordinate.create(componentid.sharedIdentity, release)
    if (result.isSuccess()) Right(())
    else {
      val error = result.error().get()
      Left(s"reason=release-coordinate; context=$context; detail=${error.code()}: ${error.message()}")
    }
  }

  private def _non_empty(
    cursor: HCursor,
    field: String,
    context: String
  ): Either[String, String] =
    _required[String](cursor, field, context).flatMap { value =>
      Either.cond(
        value.nonEmpty && value == value.trim,
        value,
        s"reason=non-empty-field; context=$context; field=$field"
      )
    }

  private def _required[A: Decoder](
    cursor: ACursor,
    field: String,
    context: String
  ): Either[String, A] =
    cursor.get[A](field).left.map(error =>
      s"reason=required-field; context=$context; field=$field; detail=${error.message}"
    )

  private def _only(
    cursor: HCursor,
    allowed: Set[String],
    context: String
  ): Either[String, Unit] = {
    val unknown = cursor.keys.toVector.flatten.filterNot(allowed).sorted
    Either.cond(
      unknown.isEmpty,
      (),
      s"reason=unknown-field; context=$context; fields=${unknown.mkString(",")}"
    )
  }

  private def _unique(
    values: Vector[String],
    field: String
  ): Either[String, Unit] = {
    val duplicates = values.groupBy(x => x).collect {
      case (value, xs) if xs.size > 1 => value
    }.toVector.sorted
    Either.cond(
      duplicates.isEmpty,
      (),
      s"reason=duplicate-entry; field=$field; values=${duplicates.mkString(",")}"
    )
  }

  private def _sequence[A](
    values: Vector[Either[String, A]]
  ): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }

  private def _is_snapshot(value: String): Boolean =
    value.endsWith("-SNAPSHOT")

  private def _numeric_release(value: String): Option[(Int, Int, Int)] =
    value match {
      case _numeric_release_pattern(major, minor, patch) =>
        try Some((major.toInt, minor.toInt, patch.toInt))
        catch {
          case _: NumberFormatException => None
        }
      case _ => None
    }

  private def _compare(
    lhs: (Int, Int, Int),
    rhs: (Int, Int, Int)
  ): Int = {
    val left = Vector(lhs._1, lhs._2, lhs._3)
    val right = Vector(rhs._1, rhs._2, rhs._3)
    left.zip(right).collectFirst {
      case (l, r) if l != r => java.lang.Integer.compare(l, r)
    }.getOrElse(0)
  }

  private val _numeric_release_pattern = "([0-9]+)\\.([0-9]+)\\.([0-9]+)".r
  private val _qualified_release_pattern = "[0-9]+\\.[0-9]+\\.[0-9]+-.+".r
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
}
