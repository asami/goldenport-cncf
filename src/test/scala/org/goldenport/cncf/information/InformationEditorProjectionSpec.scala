package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, RdfNodeName}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 21, 2026
 * @version May. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationEditorProjectionSpec
  extends AnyWordSpec
  with Matchers {

  "InformationSpaceEditorProjection" should {
    "provide book field descriptors and knowledge mapping metadata" in {
      val profile = InformationSpaceEditorProjection.profileOption("book").getOrElse(fail("book profile missing"))

      val title = _field(profile, "title")
      val isbn = _field(profile, "isbn13")
      val authors = _field(profile, "authors")
      val subjects = _field(profile, "subjects")

      title.label shouldBe "Title"
      title.requiredness shouldBe "required"
      title.mappings.map(_.targetPath) should contain allOf ("presentation.labels", "information.title")
      isbn.resolverAssisted shouldBe true
      isbn.mappings.map(_.targetPath) should contain ("identity.externalIdentifiers")
      authors.mappings.map(_.targetKind) should contain ("relationship")
      subjects.mappings.map(_.profileLayer).toSet should contain ("common-neighborhood")
      profile.fields.flatMap(_.mappings.map(_.targetKind)).toSet should contain allOf (
        "knowledge-node-section",
        "relationship",
        "fact",
        "evidence",
        "frame"
      )
      profile.fields.flatMap(_.mappings.map(_.profileLayer)).toSet should contain allOf (
        "common-neighborhood",
        "book-profile-extension"
      )
    }

    "project records with field validation issues and resolution candidates" in {
      val component = _component()
      val batch = _success(component.informationSpace.registerImportBatch("book", Vector(
        Record.data(
          "isbn13" -> "9780134685991",
          "title" -> "Domain-Driven Design",
          "authors" -> "Eric Evans"
        )
      )))
      val recordid = batch.recordIds.head
      val binding = InformationIdentityBinding(
        InformationIdentityBindingId("pending"),
        rdfSubject = Some(RdfNodeName("http://dbpedia.org/resource/Domain-driven_design")),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "http://dbpedia.org/resource/Domain-driven_design", Some("book"))),
        authority = Some("dbpedia"),
        confidence = Some(0.85)
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "dbpediaUri", "Domain-driven design", binding, Some(0.85), Some("title match")))

      val projection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val record = projection.records.headOption.getOrElse(fail("record projection missing"))
      val dbpedia = record.fields.find(_.descriptor.fieldPath == "dbpediaUri").getOrElse(fail("dbpedia field missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))

      projection.componentName shouldBe component.name
      projection.domain shouldBe "book"
      record.state shouldBe InformationLifecycleState.NeedsResolution
      record.actions.find(_.name == "resolve").map(_.enabled) shouldBe Some(true)
      title.value shouldBe Some("Domain-Driven Design")
      dbpedia.resolutionCandidates.map(_.label) shouldBe Vector("Domain-driven design")
    }

    "project action availability across lifecycle states" in {
      val component = _component()
      val batch = _success(component.informationSpace.registerImportBatch("book", Vector(Record.data("title" -> "Ready"))))
      val recordid = batch.recordIds.head
      _success(component.informationSpace.validateInformationRecord(recordid))
      val readyprojection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val readyrecord = readyprojection.records.headOption.getOrElse(fail("ready record missing"))

      val item = _success(component.informationSpace.confirmInformationRecord(recordid))
      _success(component.informationSpace.publishInformationItem(item.id, "rdf-vector", Some("published")))
      val publishedprojection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val publisheditem = publishedprojection.items.headOption.getOrElse(fail("published item missing"))

      readyrecord.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(true)
      publisheditem.actions.find(_.name == "publish").map(_.enabled) shouldBe Some(true)
      publisheditem.actions.find(_.name == "materialize").map(_.enabled) shouldBe Some(true)
      publisheditem.publication.map(_.state) shouldBe Some(InformationPublicationState.Published)
    }

    "disable confirmation when required book fields are missing" in {
      val component = _component()
      val batch = _success(component.informationSpace.registerImportBatch("book", Vector(Record.data("isbn13" -> "9780134685991"))))
      val recordid = batch.recordIds.head
      _success(component.informationSpace.validateInformationRecord(recordid))

      val projection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val record = projection.records.headOption.getOrElse(fail("record projection missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))

      record.state shouldBe InformationLifecycleState.Invalid
      title.validationIssues.map(_.fieldPath) shouldBe Vector("title")
      record.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(false)
      component.informationSpace.confirmInformationRecord(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "reject unknown editor profiles deterministically" in {
      val component = _component()

      InformationSpaceEditorProjection.component(component, "unknown") shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _field(
    profile: InformationEditorProfile,
    fieldpath: String
  ): InformationFieldDescriptor =
    profile.fields.find(_.fieldPath == fieldpath).getOrElse(fail(s"field missing: $fieldpath"))

  private def _component(): Component =
    TestComponentFactory.create("BookEditorComponent", Protocol.empty)

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
