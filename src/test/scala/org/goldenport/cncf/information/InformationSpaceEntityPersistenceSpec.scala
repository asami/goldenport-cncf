package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.entity.{EntityRevisionRepresentation, EntityStore}
import org.goldenport.cncf.entity.runtime.EntityMemoryPolicy
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeEntityBinding,
  KnowledgeNodeId,
  RdfNodeName
}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpaceEntityPersistenceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E1, rules:IC-04, phase:61.2, slice:IC-04A"
  )
  private val _e2 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E2, rules:IC-04, phase:61.2, slice:IC-04A"
  )
  private val _e3 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E3, rules:IC-04, phase:61.2, slice:IC-04A"
  )
  private val _e4 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E4, rules:IC-04, phase:61.2, slice:IC-04A"
  )
  private val _e5 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E5, rules:IC-04, phase:61.2, slice:IC-04B"
  )

  "InformationSpace Entity persistence" should {
    "E1 register the exact Component-owned collection and read a generated Information root back from EntityStore" must _e1 {
      "registers and reads back a generated Information root" in {
        Given("one canonical Component owner and a deterministic runtime namespace")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        given ExecutionContext = _context(namespace, "information-persistence")
        val component = _component("org.goldenport.cncf.information.PersistenceOwner")

        When("the owner InformationSpace registers one generated Information root")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "EntityStore persistence"))
        )).head
        val collection = registered.id.collection
        val runtimecollection = summon[ExecutionContext].entitySpace.entityOption(collection)
          .getOrElse(fail(s"Information EntityCollection is not registered: ${collection.print}"))
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))

        Then("the collection is exact, StoreOnly, Embedded-revision-backed, and persistent readback preserves the generated root")
        collection.major shouldBe namespace.major
        collection.minor shouldBe s"${namespace.minor}_${component.componentId.name.replace(".", "_")}"
        collection.name shouldBe "information"
        runtimecollection.descriptor.plan.memoryPolicy shouldBe EntityMemoryPolicy.StoreOnly
        runtimecollection.descriptor.revisionBinding.map(_.representation) shouldBe
          Some(EntityRevisionRepresentation.Embedded)
        reloaded.id shouldBe registered.id
        reloaded.revision shouldBe registered.revision
        reloaded.domain shouldBe "paper"
        reloaded.workingData.getString("title") shouldBe Some("EntityStore persistence")
      }
    }

    "E2 isolate same-logical-name Information collections for two Component owners" must _e2 {
      "isolates both Component-owned collections and rejects a foreign mutation" in {
        Given("two canonical Component owners sharing one deterministic runtime namespace")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        given ExecutionContext = _context(namespace, "information-isolation")
        val first = _component("org.goldenport.cncf.information.FirstOwner")
        val second = _component("org.goldenport.cncf.information.SecondOwner")

        When("both owner InformationSpaces register the same logical Information collection name")
        val firstregistered = _success(first.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "First owner"))
        )).head
        val secondregistered = _success(second.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Second owner"))
        )).head
        val foreignmutation = first.informationSpace.updateInformation(
          secondregistered.id,
          Record.data("title" -> "Foreign mutation")
        )

        Then("both exact collections remain registered and a foreign Component identity cannot select or mutate the other owner record")
        firstregistered.id.collection.name shouldBe "information"
        secondregistered.id.collection.name shouldBe "information"
        firstregistered.id.collection.minor shouldBe
          s"${namespace.minor}_${first.componentId.name.replace(".", "_")}"
        secondregistered.id.collection.minor shouldBe
          s"${namespace.minor}_${second.componentId.name.replace(".", "_")}"
        firstregistered.id.collection should not equal secondregistered.id.collection
        summon[ExecutionContext].entitySpace.entityCollectionIds should contain(
          firstregistered.id.collection
        )
        summon[ExecutionContext].entitySpace.entityCollectionIds should contain(
          secondregistered.id.collection
        )
        first.informationSpace.getInformation(secondregistered.id) shouldBe None
        foreignmutation shouldBe a[Consequence.Failure[?]]
        _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](firstregistered.id)
        ).map(_.workingData.getString("title")) shouldBe Some(Some("First owner"))
        _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](secondregistered.id)
        ).map(_.workingData.getString("title")) shouldBe Some(Some("Second owner"))
      }
    }

    "E3 rehydrate a Component-owned Information resolution candidate and canonical identity binding through EntityStore" must _e3 {
      "persist and rehydrate nested candidate and binding semantics" in {
        Given("one canonical Component owner and deterministic runtime context")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        given ExecutionContext = _context(namespace, "information-candidate-rehydration")
        val component = _component("org.goldenport.cncf.information.CandidateOwner")

        When("the owner registers an Information root and adds a candidate with one canonical identity binding")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Persistent candidate"))
        )).head
        val binding = InformationIdentityBinding(
          authority = Some("openlibrary"),
          confidence = Some(0.91)
        )
        val candidate = _success(component.informationSpace.addResolutionCandidate(
          registered.id,
          "title",
          "Persistent candidate",
          binding,
          Some(0.91),
          Some("canonical title match")
        ))
        val updated = component.informationSpace.getInformation(registered.id)
          .getOrElse(fail(s"Information mutation is missing: ${registered.id.print}"))
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))

        Then("the generated root advances revision and preserves the candidate and its canonical binding after rehydration")
        registered.revision.value shouldBe 1L
        updated.revision.value shouldBe 2L
        reloaded.revision shouldBe updated.revision
        reloaded.resolutionCandidates.map(_.candidateKey) shouldBe Vector(candidate.candidateKey)
        reloaded.resolutionCandidates.map(_.fieldPath) shouldBe Vector("title")
        reloaded.resolutionCandidates.map(_.label) shouldBe Vector("Persistent candidate")
        reloaded.resolutionCandidates.map(_.binding.authority) shouldBe Vector(Some("openlibrary"))
        reloaded.resolutionCandidates.map(_.binding.confidence) shouldBe Vector(Some(0.91))
        reloaded.identityBindings.map(_.authority) shouldBe Vector(Some("openlibrary"))
        reloaded.identityBindings.map(_.confidence) shouldBe Vector(Some(0.91))
        reloaded.identityBindings.map(_.status) shouldBe Vector(InformationBindingStatus.candidate)
      }
    }

    "E4 rehydrate structural identity-binding values from a Component-owned Information candidate through EntityStore" must _e4 {
      "preserves canonical RDF, external identifier, entity-binding, and knowledge-node values" in {
        Given("one canonical Component owner and deterministic runtime context")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        given ExecutionContext = _context(namespace, "information-structural-binding")
        val component = _component("org.goldenport.cncf.information.StructuralBindingOwner")
        val externalidentifier = ExternalKnowledgeIdentifier(
          "openlibrary",
          "OL45883W",
          Some("work")
        )
        val entitybinding = KnowledgeEntityBinding(
          "Book",
          "OL45883W",
          Some("2026.08"),
          Some("library-catalog")
        )
        val binding = InformationIdentityBinding(
          rdfSubject = Some(RdfNodeName("https://openlibrary.org/works/OL45883W")),
          externalIdentifiers = Vector(externalidentifier),
          entityBindings = Vector(entitybinding),
          knowledgeNodeId = Some(KnowledgeNodeId("knowledge:book:OL45883W")),
          authority = Some("openlibrary"),
          confidence = Some(0.98),
          status = InformationBindingStatus.candidate
        )

        When("the owner registers an Information root and adds a candidate with structural binding values")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Structural binding"))
        )).head
        _success(component.informationSpace.addResolutionCandidate(
          registered.id,
          "title",
          "Structural binding",
          binding,
          Some(0.98),
          Some("canonical structural identity")
        ))
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))
        val rehydrated = reloaded.resolutionCandidates.headOption
          .getOrElse(fail("Information candidate is missing after EntityStore rehydration"))

        Then("the persisted candidate rehydrates every structural identity value at revision two")
        registered.revision.value shouldBe 1L
        reloaded.revision.value shouldBe 2L
        rehydrated.binding.rdfSubject shouldBe binding.rdfSubject
        rehydrated.binding.externalIdentifiers shouldBe Vector(externalidentifier)
        rehydrated.binding.entityBindings shouldBe Vector(entitybinding)
        rehydrated.binding.knowledgeNodeId shouldBe binding.knowledgeNodeId
        rehydrated.binding.authority shouldBe binding.authority
        rehydrated.binding.confidence shouldBe binding.confidence
        rehydrated.binding.status shouldBe binding.status
      }
    }

    "E5 accept the current observed revision for a strict Information edit and reject a stale retry without mutation" must _e5 {
      "keep observed revision as adapter metadata and persist only the current edit" in {
        Given("one Component-owned Information root and its initial managed revision")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        given ExecutionContext = _context(namespace, "information-observed-update")
        val component = _component("org.goldenport.cncf.information.ObservedUpdateOwner")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Initial title"))
        )).head

        When("a strict adapter submits the current revision and later retries with that now-stale revision")
        val accepted = _success(component.informationSpace.updateInformationObserved(
          registered.id,
          Record.data("title" -> "Current title"),
          registered.revision
        ))
        val stale = component.informationSpace.updateInformationObserved(
          registered.id,
          Record.data("title" -> "Stale title"),
          registered.revision
        )
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))

        Then("the strict update advances its managed revision, while the stale retry fails without changing the stored or cached root")
        accepted.revision.value shouldBe 2L
        stale shouldBe a[Consequence.Failure[?]]
        reloaded.revision shouldBe accepted.revision
        reloaded.workingData.getString("title") shouldBe Some("Current title")
        component.informationSpace.getInformation(registered.id).map(_.workingData.getString("title")) shouldBe
          Some(Some("Current title"))
      }
    }
  }

  private def _context(
    namespace: IdGenerationContext.IdNamespace,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC)
    val idgeneration = IdGenerationContext.deterministic(namespace, clock, seed)
    ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      idgeneration
    )
  }

  private def _component(
    componentid: String
  ): Component = {
    val identity = ComponentId(componentid)
    Component.create(
      identity.name,
      identity,
      ComponentInstanceId.default(identity),
      Protocol.empty
    )
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        val causes = conclusion.causes.zipWithIndex.map { case (cause, index) =>
          s"cause[$index]: ${cause.display}"
        }
        val diagnostic = (conclusion.show +: causes).mkString("\n")
        throw new AssertionError(diagnostic)
    }
}
