package domain.logicalui

import org.goldenport.cncf.display.GeneratedLogicalUiAbi

object DomainLogicalUiDefinitions {
  val definitions: Vector[GeneratedLogicalUiAbi.Definition] = Vector(
    GeneratedLogicalUiAbi.Definition(
      "cncf.logical-ui-generated.v1", 1,
      GeneratedLogicalUiAbi.SemanticContract("cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario", GeneratedLogicalUiAbi.DriverReference("KnowledgeHubProject/nict-editing-studio-app", "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail")),
      GeneratedLogicalUiAbi.SourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1"),
      "collecting-resources", "ess-definition-1", "ess.reference",
      GeneratedLogicalUiAbi.ListDefinition("Collecting Resources", Vector(GeneratedLogicalUiAbi.FieldDefinition("title", "Title", GeneratedLogicalUiAbi.PresentationRole.Title, GeneratedLogicalUiAbi.ValueDefinition("ess.title", Vector(GeneratedLogicalUiAbi.ValueKind.Text))), GeneratedLogicalUiAbi.FieldDefinition("stage", "Capture stage", GeneratedLogicalUiAbi.PresentationRole.Status, GeneratedLogicalUiAbi.ValueDefinition("ess.stage", Vector(GeneratedLogicalUiAbi.ValueKind.Text)))), GeneratedLogicalUiAbi.DetailTarget("capture-detail"), Vector(), Vector())
    ),
    GeneratedLogicalUiAbi.Definition(
      "cncf.logical-ui-generated.v1", 1,
      GeneratedLogicalUiAbi.SemanticContract("cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario", GeneratedLogicalUiAbi.DriverReference("KnowledgeHubProject/nict-editing-studio-app", "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail")),
      GeneratedLogicalUiAbi.SourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1"),
      "capture-detail", "ess-definition-1", "ess.reference",
      GeneratedLogicalUiAbi.DetailDefinition("Capture Detail", Vector(GeneratedLogicalUiAbi.SectionDefinition("main", None, Vector(GeneratedLogicalUiAbi.FieldDefinition("title", "Title", GeneratedLogicalUiAbi.PresentationRole.Title, GeneratedLogicalUiAbi.ValueDefinition("ess.title", Vector(GeneratedLogicalUiAbi.ValueKind.Text))), GeneratedLogicalUiAbi.FieldDefinition("stage", "Capture stage", GeneratedLogicalUiAbi.PresentationRole.Status, GeneratedLogicalUiAbi.ValueDefinition("ess.stage", Vector(GeneratedLogicalUiAbi.ValueKind.Text))), GeneratedLogicalUiAbi.FieldDefinition("description", "Description", GeneratedLogicalUiAbi.PresentationRole.Description, GeneratedLogicalUiAbi.ValueDefinition("ess.description", Vector(GeneratedLogicalUiAbi.ValueKind.Text)))))), Vector(GeneratedLogicalUiAbi.ActionDefinition("request-review", "Request Review", "ess.request-review")))
    )
  )
}
