package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeNodeId, KnowledgeRelationshipKind, KnowledgeTagBinding, KnowledgeWorkingSet, RdfNodeName}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationToKnowledgeProjectionSpec
  extends AnyWordSpec
  with Matchers {

  "InformationToKnowledgeProjection" should {
    "materialize confirmed paper information into KnowledgeSpace without id collapse" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Projection", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      val snapshot = InformationSpace.materializeInformation(item)
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val nodeid = KnowledgeNodeId(s"information-${item.id.print}")
      val node = workingset.nodeOption(nodeid).getOrElse(fail("missing node"))

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
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Tagged Projection"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val information = _success(space.confirmInformation(recordid))

      val snapshot = InformationToKnowledgeProjection.materialize(
        information,
        Vector(KnowledgeTagBinding("information", "knowledge/book"))
      )
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val node = workingset.nodeOption(KnowledgeNodeId(s"information-${information.id.print}")).getOrElse(fail("missing materialized node"))

      node.bindings.tagBindings should contain (KnowledgeTagBinding("information", "knowledge/book"))
    }

    "materialize selected book person and organization candidates as surrounding nodes" in {
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
          authority = Some("dbpedia"),
          confidence = Some(0.82)
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
          authority = Some("dbpedia"),
          confidence = Some(0.80)
        ),
        Some(0.80),
        Some("publisher name match")
      ))
      _success(space.selectResolutionCandidate(informationid, author.candidateKey))
      _success(space.selectResolutionCandidate(informationid, publisher.candidateKey))
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

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

    "materialize an ordinary single-volume book as publication and textual work" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Effective Java"))))
      val informationid = batch.head.id
      _success(space.validateInformation(informationid))
      val information = _success(space.confirmInformation(informationid))

      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet

      nodecategories should contain allOf ("publication", "textual-work")
      nodecategories should not contain "volume"
      nodecategories should not contain "edition"
      relationshipkinds should contain ("publication-of")
      relationshipkinds should not contain "volume-of"
      relationshipkinds should not contain "edition-of"
    }

    "materialize Genji Iwanami volume into publication volume edition textual work layers" in {
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

      val snapshot = InformationSpace.materializeInformation(information)
      val nodecategories = snapshot.nodes.map(_.category.print).toSet
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet
      val labels = snapshot.nodes.flatMap(_.presentation.defaultLabel).toSet

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

    "materialize book classification entries as concept nodes and relationships" in {
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

      val snapshot = InformationSpace.materializeInformation(information)
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val booknodeid = KnowledgeNodeId(s"information-${information.id.print}")
      val booknode = workingset.nodeOption(booknodeid).getOrElse(fail("missing book node"))
      val relationshipkinds = snapshot.relationships.map(_.kind.print).toSet
      val conceptlabels = snapshot.nodes.filter(_.category.print == "concept").flatMap(_.presentation.defaultLabel).toSet

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
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Namespace"))))
      val information = _success(space.confirmInformation(_success(space.validateInformation(batch.head.id)).id))
      val shortid = InformationRdfNodeNaming.default.shortInformationId(information.id)
      val smnaming = InformationRdfNodeNaming(prefix = "sm", namespaces = InformationRdfNodeNaming.BUILT_IN_NAMESPACES)
      val customnaming = InformationRdfNodeNaming(
        prefix = "acme",
        namespaces = InformationRdfNodeNaming.BUILT_IN_NAMESPACES :+ InformationRdfNamespace("acme", "https://example.com/acme")
      )

      smnaming.rdfNodeName(information).print shouldBe s"sm:book/$shortid"
      smnaming.publishedRdfNodeUri("book", shortid) shouldBe Some(s"https://www.simplemodeling.org/book/$shortid")
      customnaming.rdfNodeName(information).print shouldBe s"acme:book/$shortid"
      customnaming.publishedRdfNodeUri("book", shortid) shouldBe Some(s"https://example.com/acme/book/$shortid")
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
