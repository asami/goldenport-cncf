package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, RdfNodeName}
import org.goldenport.cncf.tag.{TagCreate, TagRepository}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationFieldEvent,
  InformationFieldState,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState
}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 21, 2026
 *  version Jun.  5, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationEditorProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()
  private val _e1 = afterWord(
    "in spec:phase-61.3-information-editor-projection-lifecycle, example:E1, rules:CB-61.3-RR-001, phase:61.3"
  )
  private val _ic06e1 = afterWord(
    "in spec:phase-61.4-information-projection-compatibility, example:E1, rules:IC-06, phase:61.4, slice:IC-06B"
  )
  private val _ic06e2 = afterWord(
    "in spec:phase-61.4-information-projection-compatibility, example:E2, rules:IC-06, phase:61.4, slice:IC-06B"
  )

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
        entityBindings = Vector.empty,
        knowledgeNodeId = None,
        authority = Some("dbpedia"),
        confidence = Some(0.85),
        status = InformationBindingStatus.candidate
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "dbpediaUri", "Domain-driven design", binding, Some(0.85), Some("title match")))
      _success(component.informationSpace.validateInformation(recordid))
      _success(component.informationSpace.appendFieldEvent(recordid, InformationFieldEvent(
        fieldPath = "title",
        state = InformationFieldState.imported,
        source = "dbpedia",
        operation = Some("resolveBook"),
        provider = Some("provider:dbpedia.book.lookup"),
        transformation = Some("label-normalized"),
        valueBefore = None,
        valueAfter = Some("Domain-Driven Design"),
        evidence = Some("title match"),
        note = Some("Imported from resolver."),
        occurredAt = summon[ExecutionContext].clock.instant(),
        actor = None
      )))
      _success(component.informationSpace.appendFieldEvent(recordid, InformationFieldEvent(
        fieldPath = "language",
        state = InformationFieldState.inferred,
        source = "domain-rule",
        operation = Some("seedBook"),
        provider = None,
        transformation = Some("isbn-language-inference"),
        valueBefore = None,
        valueAfter = Some("en"),
        evidence = Some("isbn13=9780134685991; isbnGroup=0; language=en"),
        note = Some("Language inferred from ISBN registration group."),
        occurredAt = summon[ExecutionContext].clock.instant(),
        actor = None
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
      record.state shouldBe InformationLifecycleState.needs_resolution
      record.actions.find(_.name == "resolve").map(_.enabled) shouldBe Some(true)
      title.value shouldBe Some("Domain-Driven Design")
      title.status.map(_.state) shouldBe Some(InformationFieldState.imported)
      title.events.map(_.source) shouldBe Vector("dbpedia")
      title.events.headOption.flatMap(_.transformation) shouldBe Some("label-normalized")
      language.status.map(_.state) shouldBe Some(InformationFieldState.inferred)
      language.events.headOption.flatMap(_.transformation) shouldBe Some("isbn-language-inference")
      dbpedia.resolutionCandidates.map(_.candidateLabel) shouldBe Vector("Domain-driven design")
    }

    "E1 project the canonical output and conditional-update boundaries" must _ic06e1 {
      "keep the generated revision outside application data while retaining working data for editing" in {
        Given("registered Information containing application fields and an unprojected provider payload")
        val component = _component()
        val providerpayload = "provider-secret-payload"
        val registered = _success(component.informationSpace.registerInformation("book", Vector(
          Record.data("title" -> "Canonical editor title", "providerPayload" -> providerpayload)
        ))).head

        When("the existing editor projection consumes the canonical descriptors")
        val projection = _success(InformationSpaceEditorProjection.component(component, "book"))
        val record = projection.information.headOption.getOrElse(fail("record projection missing"))

        Then("managed revision is output, observed revision is a separate precondition, and raw data is absent")
        record.revision shouldBe registered.revision
        projection.output shouldBe InformationProjectionContract.output
        projection.output.field("revision").exists(x => x.systemManaged && x.readOnly) shouldBe true
        projection.createApplicationInput.field("workingData").map(_.required) shouldBe Some(true)
        projection.createApplicationInput.excludedFields should contain allOf ("revision", "rawData")
        projection.conditionalUpdate.application.field("workingData").map(_.required) shouldBe Some(true)
        projection.conditionalUpdate.application.field("observedRevision") shouldBe empty
        projection.conditionalUpdate.observedRevision.transportPrecondition shouldBe true
        record.toString should not include providerpayload
      }
    }

    "E2 keep raw provider data outside canonical editor output" must _ic06e2 {
      "project only canonical output fields and profile-backed working data" in {
        Given("the editor projection output descriptor")
        val output = InformationProjectionContract.output

        When("the editor consumer metadata is inspected")
        val fields = output.fields.map(_.name).toSet

        Then("raw provider data has no editable or projected editor field")
        fields should contain allOf ("id", "domain", "workingData", "state", "conflicts", "revision")
        fields should not contain "rawData"
        output.excludedFields should contain ("rawData")
      }
    }

    "rehydrate persisted Information into an editor projection after a fresh component cache starts empty" in {
      Given("one persisted book Information root and a new Component instance with the same identity")
      val componentname = "BookEditorRestartProjectionComponent"
      val original = _component(componentname)
      val registered = _success(original.informationSpace.registerInformation(
        "book",
        Vector(Record.data("title" -> "Restarted editor projection"))
      )).head
      val restarted = _component(componentname)
      val cacheempty = restarted.informationSpace.snapshot.information.isEmpty

      When("the fresh component editor projection is requested under the supplied ExecutionContext")
      val projection = _success(InformationSpaceEditorProjection.component(restarted, "book"))

      Then("the persisted root is projected and the fresh cache is refreshed")
      cacheempty shouldBe true
      projection.information.map(_.informationId) shouldBe Vector(registered.id)
      projection.information.map(_.title) shouldBe Vector(Some("Restarted editor projection"))
      restarted.informationSpace.snapshot.information.map(_.id) shouldBe Vector(registered.id)
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
        entityBindings = Vector.empty,
        knowledgeNodeId = None,
        authority = Some("local"),
        confidence = Some(0.80),
        status = InformationBindingStatus.candidate
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "doi", "Knowledge Editing with InformationSpace", binding, Some(0.80), Some("local identifier")))
      _success(component.informationSpace.validateInformation(recordid))

      When("the paper editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "paper"))
      val record = projection.information.headOption.getOrElse(fail("paper record projection missing"))
      val doi = record.fields.find(_.descriptor.fieldPath == "doi").getOrElse(fail("doi field missing"))

      Then("the record and DOI field expose the candidate and lifecycle")
      projection.domain shouldBe "paper"
      record.state shouldBe InformationLifecycleState.needs_resolution
      record.title shouldBe Some("Knowledge Editing with InformationSpace")
      doi.resolutionCandidates.map(_.candidateLabel) shouldBe Vector("Knowledge Editing with InformationSpace")
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
        entityBindings = Vector.empty,
        knowledgeNodeId = None,
        authority = Some("local"),
        confidence = Some(0.80),
        status = InformationBindingStatus.candidate
      )
      _success(component.informationSpace.addResolutionCandidate(recordid, "url", "KnowledgeSpace Web Resource", binding, Some(0.80), Some("local URL")))
      _success(component.informationSpace.validateInformation(recordid))

      When("the web-resource editor state is projected")
      val projection = _success(InformationSpaceEditorProjection.component(component, "web-resource"))
      val record = projection.information.headOption.getOrElse(fail("web resource record projection missing"))
      val url = record.fields.find(_.descriptor.fieldPath == "url").getOrElse(fail("url field missing"))

      Then("the record and URL field expose the candidate and lifecycle")
      projection.domain shouldBe "web-resource"
      record.state shouldBe InformationLifecycleState.needs_resolution
      record.title shouldBe Some("KnowledgeSpace Web Resource")
      url.resolutionCandidates.map(_.candidateLabel) shouldBe Vector("KnowledgeSpace Web Resource")
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

    "E1 project only lifecycle-authorized action availability" must _e1 {
      "enable actions only when InformationSpace accepts their lifecycle state" in {
      Given("book Information in imported, invalid, ready, confirmed, published, and rejected lifecycle states")
      val component = _component()
      val imported = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Imported")))).head
      val invalid = _success(component.informationSpace.registerInformation("book", Vector(Record.data("isbn13" -> "9780134685991")))).head
      val ready = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Ready")))).head
      val confirmable = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Confirmed")))).head
      val publishable = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Published")))).head
      val rejected = _success(component.informationSpace.registerInformation("book", Vector(Record.data("title" -> "Rejected")))).head
      _success(component.informationSpace.validateInformation(invalid.id))
      _success(component.informationSpace.validateInformation(ready.id))
      _success(component.informationSpace.validateInformation(confirmable.id))
      val confirmed = _success(component.informationSpace.confirmInformation(confirmable.id))
      _success(component.informationSpace.validateInformation(publishable.id))
      val published = _success(component.informationSpace.confirmInformation(publishable.id))
      _success(component.informationSpace.publishInformation(published.id, "rdf-vector", Some("published")))
      _success(component.informationSpace.rejectInformation(rejected.id, "editor matrix"))

      When("the editor projects the lifecycle action matrix")
      val records = _success(InformationSpaceEditorProjection.component(component, "book"))
        .information
        .map(record => record.informationId -> record)
        .toMap
      def _enabled_(informationid: EntityId, action: String): Option[Boolean] =
        records.get(informationid).flatMap(_.actions.find(_.name == action)).map(_.enabled)

      Then("save and validate remain available only for accepted imported or update paths")
      _enabled_(imported.id, "save") shouldBe Some(true)
      _enabled_(imported.id, "validate") shouldBe Some(true)
      _enabled_(invalid.id, "save") shouldBe Some(true)
      _enabled_(invalid.id, "validate") shouldBe Some(false)
      _enabled_(ready.id, "save") shouldBe Some(true)
      _enabled_(ready.id, "validate") shouldBe Some(false)
      _enabled_(confirmed.id, "save") shouldBe Some(false)
      _enabled_(confirmed.id, "validate") shouldBe Some(false)
      _enabled_(published.id, "save") shouldBe Some(false)
      _enabled_(published.id, "validate") shouldBe Some(false)

      And("reject and reopen follow the generated lifecycle topology")
      _enabled_(imported.id, "reject") shouldBe Some(true)
      _enabled_(ready.id, "confirm") shouldBe Some(true)
      _enabled_(rejected.id, "reject") shouldBe Some(false)
      _enabled_(rejected.id, "reopen") shouldBe Some(true)
      _enabled_(confirmed.id, "reject") shouldBe Some(false)
      _enabled_(confirmed.id, "reopen") shouldBe Some(true)
      _enabled_(confirmed.id, "confirm") shouldBe Some(true)

      And("published records retain only deliberate publication and materialization repeats")
      _enabled_(published.id, "publish") shouldBe Some(true)
      _enabled_(published.id, "materialize") shouldBe Some(true)
      _enabled_(published.id, "reopen") shouldBe Some(false)
      _enabled_(published.id, "reject") shouldBe Some(false)
      records(published.id).publication.map(_.state) shouldBe Some(InformationPublicationState.published)
      }
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
      record.state shouldBe InformationLifecycleState.invalid
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
      record.state shouldBe InformationLifecycleState.invalid
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
      record.state shouldBe InformationLifecycleState.invalid
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

  private def _component(
    componentname: String = "BookEditorComponent"
  ): Component =
    TestComponentFactory.create(componentname, Protocol.empty)

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
