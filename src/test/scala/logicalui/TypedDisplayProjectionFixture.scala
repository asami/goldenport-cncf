package logicalui

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.display.{DisplayModel, DisplayProjection, DisplayValue, GeneratedLogicalUiAbi}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Development-only typed application fixture, not Entity admission or Cozy output. */
object TypedDisplayProjectionFixture {
  import DisplayProjection.*
  import DisplayModel.*
  import GeneratedLogicalUiAbi.*

  sealed abstract class CaptureStatus
  object CaptureStatus {
    case object Draft extends CaptureStatus
    case object Ready extends CaptureStatus
    case object Held extends CaptureStatus
  }
  final case class Capture(resource: Resource, title: String, description: String,
    status: CaptureStatus, number: BigDecimal, flag: Boolean, instant: Instant,
    availability: ActionAvailability)

  val definitions: Vector[Definition] = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions

  def resourceC(index: Int, revision: Long): Consequence[Resource] = {
    for {
      identity <- EntityId.restore("fixture", "display", EntityCollectionId("fixture", "display", "Capture"),
        Instant.ofEpochMilli(1800000000000L), "projection_resource_" + index)
      managed <- EntityRevision.createC(revision)
    } yield Resource(identity, managed)
  }
  def captureC(index: Int, revision: Long): Consequence[Capture] = {
    resourceC(index, revision).map(resource => Capture(resource, "  捕捉 Ω e\u0301  ", "\t説明\n  ",
      CaptureStatus.Draft, BigDecimal("123.45000"), true, Instant.parse("2026-10-03T00:00:00.123456789Z"),
      ActionAvailability.Enabled))
  }
  def stage(status: CaptureStatus): String = {
    status match {
      case CaptureStatus.Draft => "draft"
      case CaptureStatus.Ready => "review-ready"
      case CaptureStatus.Held => "held"
    }
  }
  def semanticSummary(source: Capture): (String, CaptureStatus, BigDecimal) = {
    (source.title, source.status, source.number)
  }
  def semanticTitles(source: Vector[Capture]): Vector[String] = {
    source.map(_.title)
  }
  def titleBinding: ValueBinding[Capture] = {
    ValueBinding(Vector("ess.title"), source => Consequence.success(Vector(DisplayValue.Text(source.title))))
  }
  def stageBinding: ValueBinding[Capture] = {
    ValueBinding(Vector("ess.stage"), source => Consequence.success(Vector(DisplayValue.Text(stage(source.status)))))
  }
  def descriptionBinding: ValueBinding[Capture] = {
    ValueBinding(Vector("ess.description"), source => Consequence.success(Vector(DisplayValue.Text(source.description))))
  }
  def reviewBinding: ActionBinding[Capture] = {
    ActionBinding("ess.request-review", source => Consequence.success(source.availability))
  }
  def detailRow: RowBinding[Capture] = {
    RowBinding(source => Consequence.success(source.resource),
      Vector(descriptionBinding, stageBinding, titleBinding), Vector(reviewBinding))
  }
  def listRow: RowBinding[Capture] = {
    RowBinding(source => Consequence.success(source.resource), Vector(stageBinding, titleBinding), Vector.empty)
  }
  def detailC(admitted: AdmittedDefinitions): Consequence[Detail[Capture]] = {
    DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", detailRow)
  }
  def listC(admitted: AdmittedDefinitions): Consequence[List[Capture]] = {
    DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", listRow, Vector.empty)
  }

  /** The real E11 scenario invokes these external compile-time negative probes. */
  def verifyTypeBoundary(): Unit = {
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      import logicalui.TypedDisplayProjectionFixture.Capture
      val detail: Detail[Capture] = null
      detail.projectC("wrong source")
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      import logicalui.TypedDisplayProjectionFixture.Capture
      val list: List[Capture] = null
      list.projectC(Vector("wrong source"), None)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.Consequence
      import org.goldenport.cncf.display.DisplayProjection.*
      ValueBinding[String](Vector("key"), source => Consequence.success(Vector(source)))
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.Consequence
      import org.goldenport.cncf.display.DisplayProjection.*
      ActionBinding[String]("key", source => Consequence.success(source))
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.Consequence
      import org.goldenport.cncf.display.DisplayProjection.*
      RowBinding[String](source => Consequence.success(source), Vector.empty, Vector.empty)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      new Detail[String](null, null, null, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      new List[String](null, null, null, null, Vector.empty)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      Detail[String](null, null, null, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      List[String](null, null, null, null, Vector.empty)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      val detail: Detail[String] = null
      detail.copy()
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayProjection.*
      val list: List[String] = null
      list.copy()
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayValue
      DisplayValue.Date(java.time.LocalDate.of(2026, 10, 3))
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayValue
      new DisplayValue { def kind = null }
    """)
  }
}
