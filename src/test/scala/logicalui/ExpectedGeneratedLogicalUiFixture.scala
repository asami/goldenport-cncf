package logicalui

import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
import org.goldenport.cncf.display.GeneratedLogicalUiMetadataProvider

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Hand-written expected-generated ESS2-01 source; this is not Cozy generator output. */
object ExpectedGeneratedLogicalUiFixture extends GeneratedLogicalUiMetadataProvider {
  /** Compile-time probes are lexically external to the consumer production package. */
  def verifyAdmissionTypeBoundary(): Unit = {
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
      new AdmittedDefinitions(Vector.empty[Definition])
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
      AdmittedDefinitions(Vector.empty[Definition])
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
      val admitted: AdmittedDefinitions = null
      admitted.copy(definitions = Vector.empty[Definition])
    """)
  }

  def verifyClosedVocabulary(): Unit = {
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
      val role: PresentationRole = PresentationRole.Future
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.GeneratedLogicalUiAbi.*
      val kind: ValueKind = ValueKind.Date
    """)
  }

  override val generatedLogicalUiDefinitions: Vector[Definition] = Vector(
    Definition(
      "cncf.logical-ui-generated.v1", 1,
      SemanticContract("cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario",
        DriverReference("KnowledgeHubProject/nict-editing-studio-app",
          "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail")),
      SourceProvenance("cncf.expected-generated-fixture", "ess2-01", "fixture-1"),
      "collecting-resources", "ess-definition-1", "ess.reference",
      ListDefinition("Collecting Resources", Vector(
        FieldDefinition("title", "Title", PresentationRole.Title, ValueDefinition("ess.title", Vector(ValueKind.Text))),
        FieldDefinition("stage", "Capture stage", PresentationRole.Status, ValueDefinition("ess.stage", Vector(ValueKind.Text)))
      ), DetailTarget("capture-detail"), Vector.empty, Vector.empty)
    ),
    Definition(
      "cncf.logical-ui-generated.v1", 1,
      SemanticContract("cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario",
        DriverReference("KnowledgeHubProject/nict-editing-studio-app",
          "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail")),
      SourceProvenance("cncf.expected-generated-fixture", "ess2-01", "fixture-1"),
      "capture-detail", "ess-definition-1", "ess.reference",
      DetailDefinition("Capture Detail", Vector(SectionDefinition("main", None, Vector(
        FieldDefinition("title", "Title", PresentationRole.Title, ValueDefinition("ess.title", Vector(ValueKind.Text))),
        FieldDefinition("stage", "Capture stage", PresentationRole.Status, ValueDefinition("ess.stage", Vector(ValueKind.Text))),
        FieldDefinition("description", "Description", PresentationRole.Description,
          ValueDefinition("ess.description", Vector(ValueKind.Text)))
      ))), Vector(ActionDefinition("request-review", "Request Review", "ess.request-review")))
    )
  )
}
