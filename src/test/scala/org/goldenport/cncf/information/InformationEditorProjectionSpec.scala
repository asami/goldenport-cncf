package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, RdfNodeName}
import org.goldenport.cncf.tag.{TagCreate, TagRepository}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 21, 2026
 *  version Jun.  5, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationEditorProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()

  "InformationSpaceEditorProjection" should {
    "provide book field descriptors and knowledge mapping metadata" in {
      Given("the built-in book editor profile")

      When("its field descriptors are projected")
      val profile = InformationSpaceEditorProjection.profileOption("book").getOrElse(fail("book profile missing"))

      val title = _field(profile, "title")
      val originaltitle = _field(profile, "originalTitle")
      val volume = _field(profile, "volume")
      val series = _field(profile, "series")
      val isbn = _field(profile, "isbn13")
      val authors = _field(profile, "authors")
      val subjects = _field(profile, "subjects")
      val description = _field(profile, "description")
      val sourceurl = _field(profile, "sourceUrl")

      Then("book fields expose authoring, resolution, and Knowledge mapping semantics")
      title.label shouldBe "Title"
      title.requiredness shouldBe "required"
      title.mappings.map(_.targetPath) should contain allOf ("presentation.labels", "information.title")
      originaltitle.label shouldBe "Original title"
      originaltitle.resolverAssisted shouldBe true
      originaltitle.mappings.map(_.targetPath) should contain ("information.originalTitle")
      volume.label shouldBe "Volume"
      volume.resolverAssisted shouldBe true
      volume.mappings.map(_.targetPath) should contain ("publication.volume")
      series.label shouldBe "Series"
      series.mappings.map(_.targetKind) should contain allOf ("relationship", "frame")
      isbn.resolverAssisted shouldBe true
      isbn.mappings.map(_.targetPath) should contain ("identity.externalIdentifiers")
      authors.mappings.map(_.targetKind) should contain ("relationship")
      subjects.mappings.map(_.profileLayer).toSet should contain ("common-neighborhood")
      description.resolverAssisted shouldBe true
      description.mappings.map(_.targetKind) should contain ("evidence")
      sourceurl.resolverAssisted shouldBe true
      sourceurl.mappings.map(_.targetKind) should contain allOf ("evidence", "provenance")
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
      Given("book Information with resolver candidates and field history")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("book", Vector(
        Record.data(
          "isbn13" -> "9780134685991",
          "title" -> "Domain-Driven Design",
          "authors" -> "Eric Evans"
        )
      )))
      val recordid = batch.head.id
      val binding = InformationIdentityBinding(
        rdfSubject = Some(RdfNodeName("http://dbpedia.org/resource/Domain-driven_design")),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "http://dbpedia.org/resource/Domain-driven_design", Some("book"))),
        authority = Some("dbpedia"),
        confidence = Some(0.85)
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "dbpediaUri", "Domain-driven design", binding, Some(0.85), Some("title match")))
      _success(component.informationSpace.appendFieldEvent(recordid, InformationFieldEvent(
        fieldPath = "title",
        state = InformationFieldState.Imported,
        source = "dbpedia",
        operation = Some("resolveBook"),
        provider = Some("provider:dbpedia.book.lookup"),
        transformation = Some("label-normalized"),
        valueAfter = Some("Domain-Driven Design"),
        evidence = Some("title match"),
        note = Some("Imported from resolver."),
        occurredAt = summon[ExecutionContext].clock.instant()
      )))
      _success(component.informationSpace.appendFieldEvent(recordid, InformationFieldEvent(
        fieldPath = "language",
        state = InformationFieldState.Inferred,
        source = "domain-rule",
        operation = Some("seedBook"),
        transformation = Some("isbn-language-inference"),
        valueAfter = Some("en"),
        evidence = Some("isbn13=9780134685991; isbnGroup=0; language=en"),
        note = Some("Language inferred from ISBN registration group."),
        occurredAt = summon[ExecutionContext].clock.instant()
      )))

      When("the component editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val record = projection.information.headOption.getOrElse(fail("record projection missing"))
      val dbpedia = record.fields.find(_.descriptor.fieldPath == "dbpediaUri").getOrElse(fail("dbpedia field missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))
      val language = record.fields.find(_.descriptor.fieldPath == "language").getOrElse(fail("language field missing"))

      Then("the record exposes lifecycle, field history, and resolution state")
      projection.componentName shouldBe component.name
      projection.domain shouldBe "book"
      record.informationIdString shouldBe recordid.print
      record.state shouldBe InformationLifecycleState.NeedsResolution
      record.actions.find(_.name == "resolve").map(_.enabled) shouldBe Some(true)
      title.value shouldBe Some("Domain-Driven Design")
      title.status.map(_.state) shouldBe Some(InformationFieldState.Imported)
      title.events.map(_.source) shouldBe Vector("dbpedia")
      title.events.headOption.flatMap(_.transformation) shouldBe Some("label-normalized")
      language.status.map(_.state) shouldBe Some(InformationFieldState.Inferred)
      language.events.headOption.flatMap(_.transformation) shouldBe Some("isbn-language-inference")
      dbpedia.resolutionCandidates.map(_.label) shouldBe Vector("Domain-driven design")
    }

    "provide person and organization field descriptors" in {
      Given("the built-in authority editor profiles")

      When("person and organization descriptors are projected")
      val person = InformationSpaceEditorProjection.profileOption("person").getOrElse(fail("person profile missing"))
      val organization = InformationSpaceEditorProjection.profileOption("organization").getOrElse(fail("organization profile missing"))

      val personname = _field(person, "name")
      val orcid = _field(person, "orcidId")
      val organizationname = _field(organization, "name")
      val ror = _field(organization, "rorId")

      Then("identity fields map to their authority-specific Knowledge layers")
      personname.requiredness shouldBe "required"
      personname.mappings.map(_.targetPath) should contain ("presentation.labels")
      orcid.mappings.map(_.targetPath) should contain ("identity.externalIdentifiers")
      organizationname.requiredness shouldBe "required"
      organizationname.mappings.map(_.profileLayer) should contain ("organization-profile-extension")
      ror.resolverAssisted shouldBe true
    }

    "provide shared RDF anchor fields for cultural resource and authority profiles" in {
      Given("the built-in cultural-resource and authority profiles")
      val domains = Vector("book", "person", "organization", "textual-work", "textual-edition", "textual-volume")
      val anchorfields = Vector(
        "primaryRdfUri",
        "linkedRdfNodes",
        "sameAsUris",
        "exactMatchUris",
        "closeMatchUris",
        "rdfTypes",
        "rdfNote"
      )

      When("their common RDF anchor fields are projected")
      val projected = domains.map { domain =>
        val profile = InformationSpaceEditorProjection.profileOption(domain).getOrElse(fail(s"$domain profile missing"))
        domain -> profile
      }

      Then("each profile carries the same optional RDF authoring contract")
      projected.foreach { case (_, profile) =>
        anchorfields.foreach(fieldpath => _field(profile, fieldpath).requiredness shouldBe "optional")
        _field(profile, "primaryRdfUri").mappings.map(_.targetPath) should contain ("identity.rdfAnchor.primary")
        _field(profile, "linkedRdfNodes").validationHint.getOrElse("") should include ("graph traversal and import are deferred")
        _field(profile, "rdfNote").resolverAssisted shouldBe false
      }
    }

    "provide paper field descriptors and knowledge mapping metadata" in {
      Given("the built-in paper editor profile")

      When("its field descriptors are projected")
      val profile = InformationSpaceEditorProjection.profileOption("paper").getOrElse(fail("paper profile missing"))

      val title = _field(profile, "title")
      val doi = _field(profile, "doi")
      val dbpedia = _field(profile, "dbpediaUri")
      val citations = _field(profile, "citations")

      Then("paper identity and citation fields map to Knowledge semantics")
      title.label shouldBe "Title"
      title.requiredness shouldBe "required"
      doi.resolverAssisted shouldBe true
      doi.mappings.map(_.targetPath) should contain ("identity.externalIdentifiers")
      dbpedia.mappings.map(_.targetKind) should contain ("evidence")
      citations.mappings.map(_.targetKind) should contain ("relationship")
      profile.fields.flatMap(_.mappings.map(_.profileLayer)).toSet should contain allOf (
        "common-neighborhood",
        "paper-profile-extension"
      )
    }

    "project paper records with field validation issues and resolution candidates" in {
      Given("paper Information with a DOI resolution candidate")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("paper", Vector(
        Record.data(
          "title" -> "Knowledge Editing with InformationSpace",
          "doi" -> "10.1000/paper"
        )
      )))
      val recordid = batch.head.id
      val binding = InformationIdentityBinding(
        rdfSubject = Some(RdfNodeName("http://dbpedia.org/resource/Knowledge_graph")),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("doi", "10.1000/paper", Some("paper"))),
        authority = Some("local"),
        confidence = Some(0.80)
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "doi", "Knowledge Editing with InformationSpace", binding, Some(0.80), Some("local identifier")))

      When("the paper editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "paper"))
      val record = projection.information.headOption.getOrElse(fail("paper record projection missing"))
      val doi = record.fields.find(_.descriptor.fieldPath == "doi").getOrElse(fail("doi field missing"))

      Then("the record and DOI field expose the candidate and lifecycle")
      projection.domain shouldBe "paper"
      record.state shouldBe InformationLifecycleState.NeedsResolution
      record.title shouldBe Some("Knowledge Editing with InformationSpace")
      doi.resolutionCandidates.map(_.label) shouldBe Vector("Knowledge Editing with InformationSpace")
    }

    "provide web resource field descriptors and knowledge mapping metadata" in {
      Given("the built-in web-resource editor profile")

      When("its field descriptors are projected")
      val profile = InformationSpaceEditorProjection.profileOption("web-resource").getOrElse(fail("web resource profile missing"))

      val url = _field(profile, "url")
      val canonicalurl = _field(profile, "canonicalUrl")
      val finalurl = _field(profile, "finalUrl")
      val title = _field(profile, "title")
      val links = _field(profile, "links")

      Then("URL, provenance, and link fields expose their Knowledge mappings")
      title.label shouldBe "Title"
      title.requiredness shouldBe "required"
      url.requiredness shouldBe "required-one-of"
      canonicalurl.requiredness shouldBe "required-one-of"
      finalurl.requiredness shouldBe "optional"
      url.mappings.map(_.targetPath) should contain ("identity.externalIdentifiers")
      finalurl.mappings.map(_.targetKind) should contain ("provenance")
      links.mappings.map(_.targetKind) should contain ("relationship")
      profile.fields.flatMap(_.mappings.map(_.profileLayer)).toSet should contain allOf (
        "common-neighborhood",
        "web-resource-profile-extension"
      )
    }

    "project web resource records with resolver candidates" in {
      Given("web-resource Information with a URL resolution candidate")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("web-resource", Vector(
        Record.data(
          "title" -> "KnowledgeSpace Web Resource",
          "url" -> "https://example.org/knowledge"
        )
      )))
      val recordid = batch.head.id
      val binding = InformationIdentityBinding(
        rdfSubject = Some(RdfNodeName("https://dbpedia.org/resource/Knowledge_graph")),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("url", "https://example.org/knowledge", Some("web-resource"))),
        authority = Some("local"),
        confidence = Some(0.80)
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "url", "KnowledgeSpace Web Resource", binding, Some(0.80), Some("local URL")))

      When("the web-resource editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "web-resource"))
      val record = projection.information.headOption.getOrElse(fail("web resource record projection missing"))
      val url = record.fields.find(_.descriptor.fieldPath == "url").getOrElse(fail("url field missing"))

      Then("the record and URL field expose the candidate and lifecycle")
      projection.domain shouldBe "web-resource"
      record.state shouldBe InformationLifecycleState.NeedsResolution
      record.title shouldBe Some("KnowledgeSpace Web Resource")
      url.resolutionCandidates.map(_.label) shouldBe Vector("KnowledgeSpace Web Resource")
    }

    "project Information tags from the dedicated information tag space" in {
      Given("book Information tagged in the dedicated Information tag space")
      val component = _component()
      val tag = _success(TagRepository.entityStore().create(TagCreate(
        None,
        "projection-tag",
        None,
        tagSpace = InformationSpaceEditorProjection.InformationTagSpace,
        title = Some("Projection Tag")
      )))
      val information = _success(component.informationSpace.registerInformation("book", Vector(
        Record.data("title" -> "Tagged Book")
      ))).head
      _success(InformationTagging.workflow().sync(
        information.id.print,
        Vector(tag.path),
        InformationTagging.Role
      ))

      When("the editor state is projected with tags")
      val projection = _success(InformationSpaceEditorProjection.componentWithTags(component, "book"))
      val record = projection.information.headOption.getOrElse(fail("tagged Information projection missing"))

      Then("the tag path and reverse source binding are visible")
      record.tags.map(_.tagSpace) shouldBe Vector(InformationSpaceEditorProjection.InformationTagSpace)
      record.tags.map(_.path) shouldBe Vector(tag.path)
      _success(InformationSpaceEditorProjection.informationTagSourceIds("projection-tag")) should contain (information.id.print)
    }

    "project action availability across lifecycle states" in {
      Given("book Information observed before and after confirmation and publication")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Ready"))))
      val recordid = batch.head.id
      _success(component.informationSpace.validateInformation(recordid))
      When("editor actions are projected for ready and published states")
      val readyprojection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val readyrecord = readyprojection.information.headOption.getOrElse(fail("ready record missing"))

      val item = _success(component.informationSpace.confirmInformation(recordid))
      _success(component.informationSpace.publishInformation(item.id, "rdf-vector", Some("published")))
      val publishedprojection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val publisheditem = publishedprojection.information.headOption.getOrElse(fail("published item missing"))

      Then("each lifecycle exposes only its valid next actions")
      readyrecord.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(true)
      publisheditem.actions.find(_.name == "publish").map(_.enabled) shouldBe Some(true)
      publisheditem.actions.find(_.name == "materialize").map(_.enabled) shouldBe Some(true)
      publisheditem.publication.map(_.state) shouldBe Some(InformationPublicationState.Published)
    }

    "disable confirmation when required book fields are missing" in {
      Given("book Information without a title")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("book", Vector(Record.data("isbn13" -> "9780134685991"))))
      val recordid = batch.head.id
      _success(component.informationSpace.validateInformation(recordid))

      When("the invalid editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "book"))
      val record = projection.information.headOption.getOrElse(fail("record projection missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))

      Then("the title issue disables confirmation and the lifecycle gate rejects it")
      record.state shouldBe InformationLifecycleState.Invalid
      title.validationIssues.map(_.fieldPath) shouldBe Vector("title")
      record.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(false)
      component.informationSpace.confirmInformation(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "disable confirmation when required paper title is missing" in {
      Given("paper Information with a DOI but no title")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("paper", Vector(Record.data("doi" -> "10.1000/paper"))))
      val recordid = batch.head.id
      _success(component.informationSpace.validateInformation(recordid))

      When("the invalid editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "paper"))
      val record = projection.information.headOption.getOrElse(fail("record projection missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))

      Then("the title issue disables confirmation and the lifecycle gate rejects it")
      record.state shouldBe InformationLifecycleState.Invalid
      title.validationIssues.map(_.fieldPath) shouldBe Vector("title")
      record.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(false)
      component.informationSpace.confirmInformation(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "disable confirmation when required web resource fields are missing" in {
      Given("web-resource Information with a URL but no title")
      val component = _component()
      val batch = _success(component.informationSpace.registerInformation("web-resource", Vector(Record.data("url" -> "https://example.org/only-url"))))
      val recordid = batch.head.id
      _success(component.informationSpace.validateInformation(recordid))

      When("the invalid editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "web-resource"))
      val record = projection.information.headOption.getOrElse(fail("record projection missing"))
      val title = record.fields.find(_.descriptor.fieldPath == "title").getOrElse(fail("title field missing"))

      Then("the title issue disables confirmation and the lifecycle gate rejects it")
      record.state shouldBe InformationLifecycleState.Invalid
      title.validationIssues.map(_.fieldPath) shouldBe Vector("title")
      record.actions.find(_.name == "confirm").map(_.enabled) shouldBe Some(false)
      component.informationSpace.confirmInformation(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "reject unknown editor profiles deterministically" in {
      Given("a component and an unsupported editor profile name")
      val component = _component()

      When("the editor projection is requested")
      val result = InformationSpaceEditorProjection.component(component, "unknown")

      Then("the unsupported profile is rejected structurally")
      result shouldBe a[Consequence.Failure[_]]
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
