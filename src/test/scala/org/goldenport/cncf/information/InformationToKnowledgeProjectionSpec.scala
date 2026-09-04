package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeNodeId, KnowledgeRelationshipKind, KnowledgeTagBinding, KnowledgeWorkingSet, RdfNodeName}
import org.goldenport.cncf.tag.{TagCreate, TagRepository}
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationFieldEvent,
  InformationFieldState,
  InformationIdentityBinding
}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version May. 31, 2026
 *  version Jun. 18, 2026
 *  version Jul. 30, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationToKnowledgeProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()

  "InformationToKnowledgeProjection" should {
    "materialize confirmed paper information into KnowledgeSpace without id collapse" in {
      Given("confirmed paper Information")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Projection", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      When("the Information is materialized and loaded into KnowledgeSpace")
      val snapshot = InformationSpace.materializeInformation(item)
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val nodeid = KnowledgeNodeId(s"information-${item.id.print}")
      val node = workingset.nodeOption(nodeid).getOrElse(fail("missing node"))

      Then("Information, Knowledge, and RDF identities remain distinct")
      node.id shouldBe nodeid
      node.id.print should not be item.id.print
      val rdfnode = node.identity.rdfNode.map(_.print).getOrElse(fail("missing RDF node"))
      rdfnode should startWith ("test:paper/")
      rdfnode should not include item.id.print
      node.presentation.defaultLabel shouldBe Some("Projection")
      node.identity.externalIdentifiers.map(_.system) should contain ("cncf.information")
      workingset.counts.frameCount shouldBe 1
      workingset.counts.factCount shouldBe 1
    }

    "materialize Information tag bindings into KnowledgeNode bindings" in {
      Given("confirmed book Information and an Information-space tag binding")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Tagged Projection"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val information = _success(space.confirmInformation(recordid))

      When("the tagged Information is materialized")
      val snapshot = InformationToKnowledgeProjection.materialize(
        information,
        Vector(KnowledgeTagBinding("information", "knowledge/book"))
      )
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val node = workingset.nodeOption(KnowledgeNodeId(s"information-${information.id.print}")).getOrElse(fail("missing materialized node"))

      Then("the tag binding is attached to the KnowledgeNode")
      node.bindings.tagBindings should contain (KnowledgeTagBinding("information", "knowledge/book"))
    }

    "materialize dedicated Information TagSpace bindings through InformationSpace" in {
      Given("a confirmed Information item bound to a Tag in the dedicated Information TagSpace")
      val tag = _success(TagRepository.entityStore().create(TagCreate(
        None,
        "materialization-tag",
        None,
        tagSpace = InformationTagging.TagSpace,
        title = Some("Materialization Tag")
      )))
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Tagged Materialization"))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))
      _success(InformationTagging.workflow().sync(
        information.id.print,
        Vector(tag.path),
        InformationTagging.Role
      ))

      When("the tagged Information is materialized through InformationSpace")
      val snapshot = _success(InformationSpace.materializeInformationWithTags(information))
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val nodeid = KnowledgeNodeId(s"information-${information.id.print}")
      val node = workingset.nodeOption(nodeid).getOrElse(fail("missing materialized Information node"))

      Then("the focal KnowledgeNode has exactly the dedicated Information TagSpace binding")
      node.bindings.tagBindings shouldBe Vector(KnowledgeTagBinding(InformationTagging.TagSpace, tag.id.value))
    }

    "materialize selected book person and organization candidates as surrounding nodes" in {
      Given("confirmed book Information with selected author and publisher candidates")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "The Tale of Genji",
        "workTitle" -> "The Tale of Genji",
        "editionTitle" -> "Iwanami edition The Tale of Genji",
        "volume" -> "3",
        "authors" -> "Murasaki Shikibu",
        "publisher" -> "Iwanami Shoten"
      ))))
      val informationid = batch.head.id
      val author = _success(space.addResolutionCandidate(
        informationid,
        "authors",
        "Murasaki Shikibu",
        InformationIdentityBinding(
          rdfSubject = Some(RdfNodeName("http://dbpedia.org/resource/Murasaki_Shikibu")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "http://dbpedia.org/resource/Murasaki_Shikibu", Some("person"))),
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("dbpedia"),
          confidence = Some(0.82),
          status = InformationBindingStatus.candidate
        ),
        Some(0.82),
        Some("author name match")
      ))
      val publisher = _success(space.addResolutionCandidate(
        informationid,
        "publisher",
        "Iwanami Shoten",
        InformationIdentityBinding(
          rdfSubject = Some(RdfNodeName("http://dbpedia.org/resource/Iwanami_Shoten")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "http://dbpedia.org/resource/Iwanami_Shoten", Some("organization"))),
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("dbpedia"),
          confidence = Some(0.80),
          status = InformationBindingStatus.candidate
        ),
        Some(0.80),
        Some("publisher name match")
      ))
      _success(space.selectResolutionCandidate(informationid, author.candidateKey))
      _success(space.selectResolutionCandidate(informationid, publisher.candidateKey))
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the book neighborhood is materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

      Then("the cultural-resource layers and authority relationships are present")
      nodecategories should contain allOf ("publication", "textual-work", "edition", "volume", "person", "organization")
      relationshipkinds should contain allOf ("publication-of", "volume-of", "edition-of", "authored-by", "published-by")
      snapshot.nodes
        .filter(node => Set("publication", "textual-work", "edition", "volume").contains(node.category.print))
        .foreach { node =>
          node.attributes.values.get("resource_family") should contain ("cultural-resource")
          node.attributes.values.get("cultural_resource_kind") should contain (node.category.print)
          node.attributes.values.get("domain_profile") should contain ("book")
        }
      snapshot.relationships.find(_.kind.print == "authored-by").map(_.sourceNodeId.print) should contain ("textual-work-the-tale-of-genji")
      snapshot.relationships.find(_.kind.print == "published-by").map(_.sourceNodeId.print) should contain (s"information-${information.id.print}")
      snapshot.frames.headOption.map(_.nodeIds.size) shouldBe Some(6)
      snapshot.frames.headOption.map(_.relationshipIds.size) shouldBe Some(5)
    }

    "exclude rejected and superseded book authority candidates from materialization" in {
      Given("book authority candidates in selected, superseded, and rejected states")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "Authority Decisions",
        "workTitle" -> "Authority Decisions",
        "authors" -> "Alice Example, Alice E.",
        "publisher" -> "Example Press"
      ))))
      val informationid = batch.head.id
      val author = _success(space.addResolutionCandidate(
        informationid,
        "authors",
        "Alice Example",
        InformationIdentityBinding(
          rdfSubject = None,
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("local", "alice-example", Some("person"))),
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("local"),
          confidence = None,
          status = InformationBindingStatus.candidate
        ),
        Some(0.90),
        Some("selected author evidence")
      ))
      val supersededauthor = _success(space.addResolutionCandidate(
        informationid,
        "authors",
        "Alice E.",
        InformationIdentityBinding(
          rdfSubject = None,
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("local", "alice-e", Some("person"))),
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("local"),
          confidence = None,
          status = InformationBindingStatus.candidate
        ),
        Some(0.70),
        Some("superseded author evidence")
      ))
      val rejectedpublisher = _success(space.addResolutionCandidate(
        informationid,
        "publisher",
        "Example Press",
        InformationIdentityBinding(
          rdfSubject = None,
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("local", "example-press", Some("organization"))),
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("local"),
          confidence = None,
          status = InformationBindingStatus.candidate
        ),
        Some(0.70),
        Some("rejected publisher evidence")
      ))
      _success(space.selectResolutionCandidate(informationid, author.candidateKey))
      _success(space.updateResolutionCandidateStatus(informationid, supersededauthor.candidateKey, InformationBindingStatus.superseded, Some(false)))
      _success(space.updateResolutionCandidateStatus(informationid, rejectedpublisher.candidateKey, InformationBindingStatus.rejected, Some(false)))
      val information = space.getInformation(informationid).getOrElse(fail("missing book information"))

      When("the effective book neighborhood is materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val labels = snapshot.nodes.flatMap(_.presentation.defaultLabel).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

      Then("only the selected candidate contributes nodes and relationships")
      labels should contain ("Alice Example")
      labels should not contain "Alice E."
      labels should not contain "Example Press"
      relationshipkinds should contain ("authored-by")
      relationshipkinds should not contain "published-by"
    }

    "materialize an ordinary single-volume book as publication and textual work" in {
      Given("confirmed ordinary book Information without edition or volume metadata")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Effective Java"))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the book is materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

      Then("only publication and textual-work layers are created")
      nodecategories should contain allOf ("publication", "textual-work")
      nodecategories should not contain "volume"
      nodecategories should not contain "edition"
      relationshipkinds should contain ("publication-of")
      relationshipkinds should not contain "volume-of"
      relationshipkinds should not contain "edition-of"
    }

    "materialize reviewed book information link qualifiers" in {
      Given("confirmed book Information with one reviewed and one invalid link event")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "The Tale of Genji",
        "workTitle" -> "The Tale of Genji",
        "authors" -> "Murasaki Shikibu"
      ))))
      val informationid = batch.head.id
      val author = _success(space.addResolutionCandidate(
        informationid,
        "authors",
        "Murasaki Shikibu",
        InformationIdentityBinding(
          rdfSubject = None,
          externalIdentifiers = Vector.empty,
          entityBindings = Vector.empty,
          knowledgeNodeId = None,
          authority = Some("openlibrary"),
          confidence = Some(0.75),
          status = InformationBindingStatus.candidate
        ),
        Some(0.75),
        Some("author source evidence")
      ))
      _success(space.selectResolutionCandidate(informationid, author.candidateKey))
      _success(space.appendFieldEvent(informationid, InformationFieldEvent(
        fieldPath = "informationLinks",
        state = InformationFieldState.stable,
        source = "manual",
        operation = Some("saveBook"),
        provider = None,
        transformation = Some("information-link-review"),
        valueBefore = None,
        valueAfter = Some("authored-by"),
        evidence = Some(s"linkKey=association:${author.candidateKey}; kind=authored-by; rdfPredicate=schema:author; order=1; role=author; confidence=0.95; source=openlibrary; evidenceSummary=reviewed%20author%3B%20source%20fragment"),
        note = None,
        occurredAt = summon[ExecutionContext].clock.instant(),
        actor = None
      )))
      _success(space.appendFieldEvent(informationid, InformationFieldEvent(
        fieldPath = "informationLinks",
        state = InformationFieldState.stable,
        source = "manual",
        operation = Some("saveBook"),
        provider = None,
        transformation = Some("information-link-review"),
        valueBefore = None,
        valueAfter = Some("not-a-relationship-kind"),
        evidence = Some(s"linkKey=association:${author.candidateKey}; kind=not-a-relationship-kind; order=99"),
        note = None,
        occurredAt = summon[ExecutionContext].clock.instant(),
        actor = None
      )))
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the reviewed links are materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val relationship = snapshot.relationships.find(_.kind.print == "authored-by").getOrElse(fail("authored-by relationship missing"))

      Then("the valid link carries its reviewed qualifiers and RDF predicate")
      relationship.qualifiers.values should contain ("order" -> "1")
      relationship.qualifiers.values should contain ("role" -> "author")
      relationship.qualifiers.values should contain ("confidence" -> "0.95")
      relationship.qualifiers.values should contain ("evidenceSummary" -> "reviewed author; source fragment")
      relationship.rdfPredicate.map(_.print) should contain ("schema:author")
      relationship.attributes.values.get("information_link_review_state") should contain ("stable")
    }

    "materialize Genji Iwanami volume into publication volume edition textual work layers" in {
      Given("confirmed multi-layer Genji volume Information")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "源氏物語",
        "workTitle" -> "源氏物語",
        "editionTitle" -> "岩波版 源氏物語",
        "volume" -> "3",
        "isbn13" -> "9784003510179",
        "isbn10" -> "4003510178",
        "publicationYear" -> "2018",
        "publicationMonth" -> "03"
      ))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the volume is materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet
      val labels = snapshot.nodes.flatMap(_.presentation.defaultLabel).toSet

      Then("all cultural-resource layers and relationships are created")
      nodecategories should contain allOf ("publication", "textual-work", "edition", "volume")
      relationshipkinds should contain allOf ("publication-of", "volume-of", "edition-of")
      labels should contain allOf ("源氏物語", "岩波版 源氏物語", "源氏物語 3")
      snapshot.nodes
        .filter(node => Set("publication", "textual-work", "edition", "volume").contains(node.category.print))
        .foreach { node =>
          node.attributes.values.get("resource_family") should contain ("cultural-resource")
          node.attributes.values.get("cultural_resource_kind") should contain (node.category.print)
          node.attributes.values.get("domain_profile") should contain ("book")
        }
    }

    "prefer linked Textual Work Edition and Volume Information during book materialization" in {
      Given("confirmed book Information linked to curated work, edition, and volume Information")
      val space = new InformationSpace
      val work = _success(space.registerInformation("textual-work", Vector(Record.data(
        "title" -> "源氏物語"
      )))).head
      val edition = _success(space.registerInformation("textual-edition", Vector(Record.data(
        "title" -> "岩波文庫 源氏物語",
        "textualWorkInformationId" -> work.id.print
      )))).head
      val volume = _success(space.registerInformation("textual-volume", Vector(Record.data(
        "title" -> "源氏物語 三",
        "volume" -> "3",
        "textualWorkInformationId" -> work.id.print,
        "textualEditionInformationId" -> edition.id.print
      )))).head
      val book = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "Provider raw title",
        "workTitle" -> "Wrong fallback work",
        "editionTitle" -> "Wrong fallback edition",
        "volume" -> "3",
        "textualWorkInformationId" -> work.id.print,
        "textualEditionInformationId" -> edition.id.print,
        "textualVolumeInformationId" -> volume.id.print
      )))).head
      _success(space.validateInformation(work.id))
      val confirmedwork = _success(space.confirmInformation(work.id))
      _success(space.validateInformation(edition.id))
      val confirmededition = _success(space.confirmInformation(edition.id))
      _success(space.validateInformation(volume.id))
      val confirmedvolume = _success(space.confirmInformation(volume.id))
      _success(space.validateInformation(book.id))
      val confirmedbook = _success(space.confirmInformation(book.id))

      When("the linked book neighborhood is materialized")
      val snapshot = _success(space.materializeInformation(confirmedbook.id))
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val worknode = workingset.nodeOption(KnowledgeNodeId(s"information-${confirmedwork.id.print}")).getOrElse(fail("missing linked work node"))
      val editionnode = workingset.nodeOption(KnowledgeNodeId(s"information-${confirmededition.id.print}")).getOrElse(fail("missing linked edition node"))
      val volumenode = workingset.nodeOption(KnowledgeNodeId(s"information-${confirmedvolume.id.print}")).getOrElse(fail("missing linked volume node"))
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

      Then("the linked Information supplies identity, labels, and hierarchy")
      worknode.presentation.defaultLabel shouldBe Some("源氏物語")
      editionnode.presentation.defaultLabel shouldBe Some("岩波文庫 源氏物語")
      volumenode.presentation.defaultLabel shouldBe Some("源氏物語 三")
      worknode.identity.rdfNode should contain (InformationRdfNodeNaming.default.rdfNodeName(confirmedwork))
      editionnode.identity.rdfNode should contain (InformationRdfNodeNaming.default.rdfNodeName(confirmededition))
      volumenode.identity.rdfNode should contain (InformationRdfNodeNaming.default.rdfNodeName(confirmedvolume))
      relationshipkinds should contain allOf ("publication-of", "volume-of", "edition-of")
      snapshot.relationships.find(_.kind.print == "publication-of").map(_.targetNodeId) should contain (volumenode.id)
      snapshot.relationships.find(_.kind.print == "volume-of").map(_.targetNodeId) should contain (editionnode.id)
      snapshot.relationships.find(_.kind.print == "edition-of").map(_.targetNodeId) should contain (worknode.id)
      Vector(worknode, editionnode, volumenode).foreach { node =>
        node.attributes.values.get("resource_family") should contain ("cultural-resource")
        node.attributes.values.get("domain_profile") should contain ("book")
      }
      worknode.attributes.values.get("information_domain") should contain ("textual-work")
      editionnode.attributes.values.get("information_domain") should contain ("textual-edition")
      volumenode.attributes.values.get("information_domain") should contain ("textual-volume")
      worknode.attributes.values.get("cultural_resource_kind") should contain ("textual-work")
      editionnode.attributes.values.get("cultural_resource_kind") should contain ("edition")
      volumenode.attributes.values.get("cultural_resource_kind") should contain ("volume")
    }

    "fall back to title-based book layers when linked Information is absent" in {
      Given("confirmed book Information whose linked Information ids cannot be resolved")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "Provider raw title",
        "workTitle" -> "Fallback Work",
        "editionTitle" -> "Fallback Edition",
        "volume" -> "1",
        "textualWorkInformationId" -> "missing-work",
        "textualEditionInformationId" -> "missing-edition",
        "textualVolumeInformationId" -> "missing-volume"
      ))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the book is materialized")
      val snapshot = _success(space.materializeInformation(information.id))
      val nodeids = snapshot.nodes.map(_.id).toSet
      val labels = snapshot.nodes.flatMap(_.presentation.defaultLabel).toSet

      Then("title-based layers are used without dangling relationship endpoints")
      labels should contain allOf ("Fallback Work", "Fallback Edition", "Fallback Work 1")
      nodeids.map(_.print) should not contain "information-missing-work"
      nodeids.map(_.print) should not contain "information-missing-edition"
      nodeids.map(_.print) should not contain "information-missing-volume"
      snapshot.relationships.foreach { relationship =>
        nodeids should contain (relationship.sourceNodeId)
        nodeids should contain (relationship.targetNodeId)
      }
    }

    "materialize book classification entries as concept nodes and relationships" in {
      Given("confirmed book Information with stable and editing classification entries")
      val space = new InformationSpace
      val entries = Vector(
        "entryKey=ndc-913-36; kind=library; system=ndc; code=913.36; label=NDC 913.36; source=manual; evidence=reviewed NDC%3B source%3Dmanual; state=stable; primary=true",
        "entryKey=subject-genji; kind=subject; system=openlibrary; label=Genji; source=subjects; evidence=subjects=Genji; state=stable",
        "entryKey=genre-classic; kind=genre; system=local; label=Classic; source=manual; state=editing",
        "entryKey=domain-japanese-literature; kind=knowledge-domain; system=wikidata; rdfUri=https://www.wikidata.org/entity/Q8274; label=Japanese literature; state=stable"
      ).mkString("\n")
      val batch = _success(space.registerInformation("book", Vector(Record.data(
        "title" -> "The Tale of Genji",
        "classificationEntries" -> entries
      ))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      When("the classifications are materialized")
      val snapshot = InformationSpace.materializeInformation(information)
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val booknodeid = KnowledgeNodeId(s"information-${information.id.print}")
      val booknode = workingset.nodeOption(booknodeid).getOrElse(fail("missing book node"))
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet
      val conceptlabels = snapshot.nodes.filter(_.category.print == "concept").flatMap(_.presentation.defaultLabel).toSet

      Then("stable classifications become typed concept relationships and editing entries are excluded")
      relationshipkinds should contain allOf (
        KnowledgeRelationshipKind.ClassifiedBy.print,
        "has-subject",
        "has-knowledge-domain"
      )
      relationshipkinds should not contain "has-genre"
      conceptlabels should contain allOf ("NDC 913.36", "Genji", "Japanese literature")
      val ndcnode = snapshot.nodes.find(_.presentation.defaultLabel.contains("NDC 913.36")).getOrElse(fail("missing NDC concept node"))
      ndcnode.identity.rdfNode shouldBe None
      ndcnode.identity.externalIdentifiers should contain (ExternalKnowledgeIdentifier("ndc", "913.36", Some("library")))
      val wikidatanode = snapshot.nodes.find(_.presentation.defaultLabel.contains("Japanese literature")).getOrElse(fail("missing Wikidata concept node"))
      wikidatanode.identity.rdfNode.map(_.print) should contain ("https://www.wikidata.org/entity/Q8274")
      booknode.structure.classifications.primary.map(_.print) should contain ("classification-ndc-913-36")
      booknode.structure.classifications.additional.map(_.print) should contain ("classification-ndc-913-36")
    }

    "use built-in and configured RDF namespace prefixes" in {
      Given("confirmed book Information and built-in plus custom namespace mappings")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Namespace"))))
      val information = _success(space.confirmInformation(_success(space.validateInformation(batch.head.id)).id))
      val shortid = InformationRdfNodeNaming.default.shortInformationId(information.id)
      val smnaming = InformationRdfNodeNaming(prefix = "sm", namespaces = InformationRdfNodeNaming.BUILT_IN_NAMESPACES)
      val customnaming = InformationRdfNodeNaming(
        prefix = "acme",
        namespaces = InformationRdfNodeNaming.BUILT_IN_NAMESPACES :+ InformationRdfNamespace("acme", "https://example.com/acme")
      )

      When("RDF names and published URIs are derived")
      val smname = smnaming.rdfNodeName(information).print
      val smuri = smnaming.publishedRdfNodeUri("book", shortid)
      val customname = customnaming.rdfNodeName(information).print
      val customuri = customnaming.publishedRdfNodeUri("book", shortid)

      Then("each prefix resolves through its configured namespace")
      smname shouldBe s"sm:book/$shortid"
      smuri shouldBe Some(s"https://www.simplemodeling.org/book/$shortid")
      customname shouldBe s"acme:book/$shortid"
      customuri shouldBe Some(s"https://example.com/acme/book/$shortid")
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
