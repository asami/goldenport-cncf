package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.{
  DataStore,
  EntityVersionedMutationCheckpoint,
  QueryDirective,
  SearchResult,
  SearchableDataStore,
  TotalCountCapability
}
import org.goldenport.cncf.datastore.sql.SqlDataStore
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
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Sep. 1, 2026
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
  private val _e6 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E6, rules:IC-04, phase:61.2, slice:IC-04C"
  )
  private val _e7 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E7, rules:IC-04, phase:61.2, slice:IC-04C"
  )
  private val _e8 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E8, rules:IC-04, phase:61.2, slice:IC-04C"
  )
  private val _e9 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E9, rules:IC-04, phase:61.2, slice:IC-04C"
  )
  private val _e10 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E10, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e11 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E11, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e12 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E12, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e13 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E13, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e14 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E14, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e15 = afterWord(
    "in spec:phase-61.2-information-space-entity-persistence, example:E15, rules:IC-04, phase:61.2, slice:IC-04D"
  )
  private val _e16 = afterWord(
    "in spec:phase-61.5-information-persistence-migration, example:E16, rules:IC-07, phase:61.5, slice:IC-07A.1"
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

    "E6 create an Information root with framework-managed revision and lifecycle defaults" must _e6 {
      "initialize revision and audit state without an application-supplied revision" in {
        Given("one Component owner, deterministic clock, and standard in-memory DataStore")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        val timestamp = Instant.parse("2026-08-31T00:00:00Z")
        given ExecutionContext = _context(
          namespace,
          "information-managed-create",
          DataStore.inMemorySearchable()
        )
        val component = _component("org.goldenport.cncf.information.ManagedCreateOwner")

        When("InformationSpace registers one root using only its domain and record input")
        val registered = _success(component.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Managed create"))
        )).head
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))

        Then("the EntityStore-owned create starts at revision one and persists default lifecycle audit values")
        registered.revision.value shouldBe 1L
        registered.lifecycleAttributes.createdAt shouldBe timestamp
        registered.lifecycleAttributes.updatedAt shouldBe timestamp
        registered.lifecycleAttributes.createdBy.value shouldBe "test_user_principal"
        registered.lifecycleAttributes.updatedBy.value shouldBe "test_user_principal"
        reloaded.revision shouldBe registered.revision
        reloaded.lifecycleAttributes shouldBe registered.lifecycleAttributes
      }
    }

    "E7 rehydrate a cache-empty InformationSpace and mutate its persisted root through local providers" must _e7 {
      "replay a persisted root through both in-memory and file-backed SQLite DataStores" in {
        Given("separate initial and restarted standard EntityStore contexts for both local providers")
        val memory = DataStore.inMemorySearchable()
        val sqlitepath = _database_path("rehydration")
        val providers: Vector[(String, DataStore, () => DataStore)] = Vector(
          ("in-memory", memory, () => memory),
          (
            "sqlite",
            SqlDataStore.sqlite(sqlitepath.toString),
            () => SqlDataStore.sqlite(sqlitepath.toString)
          )
        )

        When("a new cache-empty InformationSpace rehydrates and updates each persisted root")
        val evidence = providers.map { case (name, initialstore, restartedstore) =>
          _rehydration_evidence(name, initialstore, restartedstore)
        }

        Then("each restart mutation advances exactly once and publishes its authoritative root to the new cache")
        evidence.foreach { result =>
          result.cacheEmpty shouldBe true
          result.revision shouldBe 2L
          result.persistedTitle shouldBe Some(s"Rehydrated ${result.provider}")
          result.cacheSize shouldBe 1
        }
      }
    }

    "E8 admit one concurrent strict Information writer per local provider" must _e8 {
      "preserve one authoritative revision-two root when writers share one observed revision" in {
        Given("four cache-empty strict writers over in-memory and file-backed SQLite EntityStores")
        val memory = DataStore.inMemorySearchable()
        val sqlitepath = _database_path("strict-writers")
        val providers: Vector[(String, DataStore, () => DataStore)] = Vector(
          ("in-memory", memory, () => memory),
          (
            "sqlite",
            SqlDataStore.sqlite(sqlitepath.toString),
            () => SqlDataStore.sqlite(sqlitepath.toString)
          )
        )

        When("all writers submit a distinct update from the same observed revision")
        val evidence = providers.map { case (name, seedstore, callerstore) =>
          _strict_writer_evidence(name, seedstore, callerstore)
        }

        Then("one strict update wins, stale writers fail, and the persisted root matches the winner")
        evidence.foreach { result =>
          result.successfulWrites shouldBe 1
          result.failedWrites shouldBe 3
          result.revision shouldBe 2L
          result.persistedTitle shouldBe result.successfulTitles.headOption
        }
      }
    }

    "E9 retain cached and persisted nested Information state when a provider fails before publication" must _e9 {
      "reject a candidate and binding mutation without publishing partial nested state" in {
        Given("one Component-owned root backed by a local provider that rejects the before-publication checkpoint")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information")
        val store = new FailingBeforePublishDataStore
        given ExecutionContext = _context(namespace, "information-before-publication", store)
        val component = _component("org.goldenport.cncf.information.BeforePublicationOwner")
        val space = component.informationSpace
        val registered = _success(space.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Before publication"))
        )).head
        store.arm()

        When("the cached root attempts to add a nested resolution candidate and binding")
        val failed = space.addResolutionCandidate(
          registered.id,
          "title",
          "Rejected candidate",
          InformationIdentityBinding(authority = Some("openlibrary")),
          Some(0.85),
          Some("injected provider failure")
        )
        val cached = space.getInformation(registered.id)
          .getOrElse(fail(s"cached Information is missing: ${registered.id.print}"))
        val reloaded = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))

        Then("the failure leaves the root revision, cache, candidates, bindings, events, and publication projection unchanged")
        failed shouldBe a[Consequence.Failure[?]]
        cached shouldBe registered
        reloaded shouldBe registered
        reloaded.revision.value shouldBe 1L
        reloaded.resolutionCandidates shouldBe empty
        reloaded.identityBindings shouldBe empty
        reloaded.fieldEvents shouldBe empty
        reloaded.publicationStatuses shouldBe empty
      }
    }

    "E10 rehydrate authoritative Information reads and reset persisted lifecycle roots" must _e10 {
      "load search count and reset a cache-empty Component-owned InformationSpace" in {
        Given("one persisted Component-owned Information root and a fresh empty InformationSpace cache")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_authoritative_read_reset")
        given ExecutionContext = _context(namespace, "information-authoritative-read-reset")
        val owner = _component("org.goldenport.cncf.information.AuthoritativeReadResetOwner")
        val registered = _success(owner.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Authoritative restart read"))
        )).head
        val restarted = new InformationSpace(owner)
        val cacheempty = restarted.snapshot.information.isEmpty

        When("the fresh InformationSpace reads, searches, counts, and resets through its repository")
        val loaded = _success(restarted.getInformationC(registered.id))
        val searched = _success(restarted.searchInformationC(Some("paper")))
        val counts = _success(restarted.countsC)
        val snapshot = _success(restarted.snapshotC)
        _success(restarted.clear())
        val recreated = new InformationSpace(owner)
        val remaining = _success(recreated.snapshotC)

        Then("the authoritative reads refresh the cache and reset removes persisted roots for a recreated InformationSpace")
        cacheempty shouldBe true
        loaded.map(_.id) shouldBe Some(registered.id)
        searched.map(_.id) shouldBe Vector(registered.id)
        counts.informationCount shouldBe 1
        snapshot.information.map(_.id) shouldBe Vector(registered.id)
        restarted.snapshot shouldBe InformationSpaceSnapshot()
        remaining shouldBe InformationSpaceSnapshot()
      }
    }

    "E11 preserve the cache and persisted root when repository-backed reset fails" must _e11 {
      "reject reset before publishing a lifecycle deletion" in {
        Given("a cached Component-owned Information root and a provider armed to reject its lifecycle mutation")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_reset_failure")
        val store = new FailingBeforePublishDataStore
        given ExecutionContext = _context(namespace, "information-reset-failure", store)
        val owner = _component("org.goldenport.cncf.information.ResetFailureOwner")
        val space = owner.informationSpace
        val registered = _success(space.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Reset failure preservation"))
        )).head
        val cached = space.snapshot
        store.arm()

        When("the repository-backed reset reaches the provider failure before publication")
        val failed = space.clear()
        val persisted = _success(
          EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
        )

        Then("the failure publishes neither a cache invalidation nor a lifecycle deletion")
        failed shouldBe a[Consequence.Failure[?]]
        space.snapshot shouldBe cached
        persisted.map(_.id) shouldBe Some(registered.id)
      }
    }

    "E12 compensate an earlier Information lifecycle deletion when a later reset deletion fails" must _e12 {
      "restore the selected roots and refresh the cache from authoritative persistence" in {
        Given("two cached Component-owned Information roots and a provider that fails only its second reset lifecycle mutation")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_reset_compensation")
        val store = new FailingNthBeforePublishDataStore
        given ExecutionContext = _context(namespace, "information-reset-compensation", store)
        val owner = _component("org.goldenport.cncf.information.ResetCompensationOwner")
        val space = owner.informationSpace
        val registered = _success(space.registerInformation(
          "paper",
          Vector(
            Record.data("title" -> "First reset compensation root"),
            Record.data("title" -> "Second reset compensation root")
          )
        ))
        store.failOnNextMutation(2)

        When("reset deletes the first selected root and the later selected deletion fails")
        val failed = space.clear()
        val recreated = new InformationSpace(owner)
        val authoritative = _success(recreated.snapshotC)

        Then("the original failure is retained, the first logical deletion is compensated, and both caches match the authoritative roots")
        failed shouldBe a[Consequence.Failure[?]]
        authoritative.information.map(_.id) shouldBe registered.map(_.id).sortBy(_.print)
        space.snapshot shouldBe authoritative
        registered.foreach { information =>
          _success(
            EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](information.id)
          ).map(_.id) shouldBe Some(information.id)
        }
      }
    }

    "E13 evict exactly one stale cached Information root after an authoritative absence" must _e13 {
      "retain other cached roots when a direct lifecycle deletion makes one authoritative load absent" in {
        Given("two cached Component-owned Information roots and a direct deletion of only the first persisted root")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_authoritative_absence")
        given ExecutionContext = _context(namespace, "information-authoritative-absence")
        val owner = _component("org.goldenport.cncf.information.AuthoritativeAbsenceOwner")
        val space = owner.informationSpace
        val registered = _success(space.registerInformation(
          "paper",
          Vector(
            Record.data("title" -> "Stale cached Information root"),
            Record.data("title" -> "Retained cached Information root")
          )
        ))
        val deleted = registered.head
        val retained = registered(1)
        _success(EntityStore.standard().delete(deleted.id))

        When("the InformationSpace reads the directly deleted root from its authoritative repository")
        val loaded = _success(space.getInformationC(deleted.id))

        Then("the authoritative absence evicts only the stale cached id and preserves the other cached root")
        loaded shouldBe None
        space.snapshot.information.map(_.id) shouldBe Vector(retained.id)
        space.getInformation(deleted.id) shouldBe None
        space.getInformation(retained.id).map(_.id) shouldBe Some(retained.id)
      }
    }

    "E14 clear the local cache when a successful persistent reset cannot refresh it" must _e14 {
      "return the post-clear refresh failure without retaining deleted cached roots" in {
        Given("a cached persisted root and a searchable provider armed to fail only its post-clear refresh")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_clear_refresh_failure")
        val store = new FailingPostClearRefreshDataStore
        given ExecutionContext = _context(namespace, "information-clear-refresh-failure", store)
        val owner = _component("org.goldenport.cncf.information.ClearRefreshFailureOwner")
        val space = owner.informationSpace
        _success(space.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Clear refresh failure"))
        ))
        store.armPostClearRefresh()

        When("InformationSpace clears persistent roots and its immediate authoritative refresh fails")
        val failed = space.clear()
        val recreated = new InformationSpace(owner)
        val authoritative = _success(recreated.snapshotC)

        Then("the refresh failure is retained while local and recreated authoritative snapshots are empty")
        failed shouldBe a[Consequence.Failure[?]]
        space.snapshot shouldBe InformationSpaceSnapshot()
        authoritative shouldBe InformationSpaceSnapshot()
      }
    }

    "E15 sort authoritative Information restart reads by canonical id across local providers" must _e15 {
      "normalize multi-record provider output before publishing the restarted snapshot" in {
        Given("multi-record in-memory, reversed-emission, and SQLite Information stores")
        val memory = DataStore.inMemorySearchable()
        val reversed = new ReversedSearchableDataStore
        val sqlitepath = _database_path("restart-ordering")
        val providers: Vector[(String, DataStore, () => DataStore)] = Vector(
          ("in-memory", memory, () => memory),
          ("reversed-emission", reversed, () => reversed),
          (
            "sqlite",
            SqlDataStore.sqlite(sqlitepath.toString),
            () => SqlDataStore.sqlite(sqlitepath.toString)
          )
        )

        When("a fresh InformationSpace hydrates each repository through search")
        val evidence = providers.map { case (name, initialstore, restartedstore) =>
          _ordering_evidence(name, initialstore, restartedstore)
        }

        Then("every restarted snapshot uses ascending canonical ids instead of provider emission order")
        evidence.foreach { result =>
          result.snapshotids shouldBe result.expectedids
        }
        reversed.emittedReversed shouldBe true
      }
    }

    "E16 retain a generated canonical store record through the migration admission gate" must _e16 {
      "classifies the EntityStore representation as canonical without rewriting it" in {
        Given("one persisted Information root using the generated EntityStore record shape")
        val namespace = IdGenerationContext.IdNamespace("phase61", "information_canonical_admission")
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(
          namespace,
          "information-canonical-admission",
          store
        )
        val owner = _component("org.goldenport.cncf.information.CanonicalAdmissionOwner")
        val registered = _success(owner.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Canonical admission"))
        )).head
        val collection = DataStore.CollectionId.EntityStore(registered.id.collection)
        val entry = DataStore.EntryId(registered.id)
        val stored = _success(store.load(collection, entry)).getOrElse(
          fail(s"Information EntityStore raw record is missing: ${registered.id.print}")
        )

        When("the repository previews and decodes the actual EntityStore physical record")
        val preview = InformationPersistenceMigration.preview(stored)
        val decoded = _success(
          InformationEntityRepository.informationPersistent.fromStoreRecord(stored)
        )

        Then("the complete physical snake_case lifecycle representation remains canonical and unchanged")
        val keys = stored.fields.iterator.map(_.key).toSet
        Set(
          "created_at",
          "updated_at",
          "created_by",
          "updated_by",
          "post_status",
          "aliveness"
        ).subsetOf(keys) shouldBe true
        Set(
          "createdAt",
          "updatedAt",
          "createdBy",
          "updatedBy",
          "postStatus"
        ).intersect(keys) shouldBe empty
        preview shouldBe InformationPersistenceMigration.Preview.Canonical(stored)
        decoded shouldBe registered
      }
    }
  }

  private def _context(
    namespace: IdGenerationContext.IdNamespace,
    seed: String,
    datastore: DataStore = DataStore.inMemorySearchable()
  ): ExecutionContext = {
    val clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC)
    val idgeneration = IdGenerationContext.deterministic(namespace, clock, seed)
    val context = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      idgeneration
    )
    context.dataStoreSpace.useDataStore(datastore)
    context
  }

  private def _rehydration_evidence(
    provider: String,
    initialstore: DataStore,
    restartedstore: () => DataStore
  ): RehydrationEvidence = {
    val namespace = IdGenerationContext.IdNamespace(
      "phase61",
      s"information_rehydration_${provider.replace("-", "_")}"
    )
    val owner = _component(
      s"org.goldenport.cncf.information.Rehydration${provider.replace("-", "")}Owner"
    )
    val initialcontext = _context(
      namespace,
      s"information-rehydration-$provider-initial",
      initialstore
    )
    val registered = {
      given ExecutionContext = initialcontext
      _success(owner.informationSpace.registerInformation(
        "paper",
        Vector(Record.data("title" -> s"Initial $provider"))
      )).head
    }
    val restartedspace = new InformationSpace(owner)
    val cacheempty = restartedspace.snapshot.information.isEmpty
    val restartedcontext = _context(
      namespace,
      s"information-rehydration-$provider-restarted",
      restartedstore()
    )
    val updated = {
      given ExecutionContext = restartedcontext
      _success(restartedspace.updateInformation(
        registered.id,
        Record.data("title" -> s"Rehydrated $provider")
      ))
    }
    val persisted = {
      given ExecutionContext = restartedcontext
      _success(
        EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
      ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))
    }
    RehydrationEvidence(
      provider,
      cacheempty,
      updated.revision.value,
      persisted.workingData.getString("title"),
      restartedspace.snapshot.information.size
    )
  }

  private def _strict_writer_evidence(
    provider: String,
    seedstore: DataStore,
    callerstore: () => DataStore
  ): StrictWriterEvidence = {
    val namespace = IdGenerationContext.IdNamespace(
      "phase61",
      s"information_strict_${provider.replace("-", "_")}"
    )
    val owner = _component(
      s"org.goldenport.cncf.information.Strict${provider.replace("-", "")}Owner"
    )
    val seedcontext = _context(
      namespace,
      s"information-strict-$provider-seed",
      seedstore
    )
    val registered = {
      given ExecutionContext = seedcontext
      _success(owner.informationSpace.registerInformation(
        "paper",
        Vector(Record.data("title" -> s"Initial strict $provider"))
      )).head
    }
    val writercount = 4
    val ready = new CountDownLatch(writercount)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(writercount)
    val outcomes = new ConcurrentLinkedQueue[Consequence[Information]]()
    val writers = Vector.tabulate(writercount) { index =>
      new Thread(
        () => {
          val context = _context(
            namespace,
            s"information-strict-$provider-writer-$index",
            callerstore()
          )
          val space = new InformationSpace(owner)
          given ExecutionContext = context
          ready.countDown()
          try {
            if (start.await(5L, TimeUnit.SECONDS))
              outcomes.add(space.updateInformationObserved(
                registered.id,
                Record.data("title" -> s"Strict $provider writer $index"),
                registered.revision
              ))
          } finally {
            done.countDown()
          }
        },
        s"information-space-strict-$provider-$index"
      )
    }
    writers.foreach(_.start())
    ready.await(5L, TimeUnit.SECONDS) shouldBe true
    start.countDown()
    done.await(20L, TimeUnit.SECONDS) shouldBe true
    writers.foreach(_.join(5000L))
    val results = outcomes.toArray.toVector.map(
      _.asInstanceOf[Consequence[Information]]
    )
    val successful = results.count(_.toOption.nonEmpty)
    val successfultitles = results.flatMap(
      _.toOption.flatMap(_.workingData.getString("title"))
    )
    val verificationcontext = _context(
      namespace,
      s"information-strict-$provider-verification",
      callerstore()
    )
    val persisted = {
      given ExecutionContext = verificationcontext
      _success(
        EntityStore.standard().load[org.goldenport.cncf.information.entity.Information](registered.id)
      ).getOrElse(fail(s"Information EntityStore readback is missing: ${registered.id.print}"))
    }
    StrictWriterEvidence(
      provider,
      successful,
      results.size - successful,
      persisted.revision.value,
      persisted.workingData.getString("title"),
      successfultitles
    )
  }

  private def _ordering_evidence(
    provider: String,
    initialstore: DataStore,
    restartedstore: () => DataStore
  ): OrderingEvidence = {
    val namespace = IdGenerationContext.IdNamespace(
      "phase61",
      s"information_restart_ordering_${provider.replace("-", "_")}"
    )
    val owner = _component(
      s"org.goldenport.cncf.information.RestartOrdering${provider.replace("-", "")}Owner"
    )
    val seedcontext = _context(
      namespace,
      s"information-restart-ordering-$provider-seed",
      initialstore
    )
    val registered = {
      given ExecutionContext = seedcontext
      _success(owner.informationSpace.registerInformation(
        "paper",
        Vector(
          Record.data("title" -> s"First $provider"),
          Record.data("title" -> s"Second $provider"),
          Record.data("title" -> s"Third $provider")
        )
      ))
    }
    val restartedspace = new InformationSpace(owner)
    val restartedcontext = _context(
      namespace,
      s"information-restart-ordering-$provider-restarted",
      restartedstore()
    )
    val snapshot = {
      given ExecutionContext = restartedcontext
      _success(restartedspace.snapshotC)
    }
    OrderingEvidence(
      provider,
      registered.map(_.id).sortBy(_.print),
      snapshot.information.map(_.id)
    )
  }

  private def _database_path(name: String): Path =
    Files.createTempFile(s"information-space-$name-", ".db")

  private final case class RehydrationEvidence(
    provider: String,
    cacheEmpty: Boolean,
    revision: Long,
    persistedTitle: Option[String],
    cacheSize: Int
  )

  private final case class StrictWriterEvidence(
    provider: String,
    successfulWrites: Int,
    failedWrites: Int,
    revision: Long,
    persistedTitle: Option[String],
    successfulTitles: Vector[String]
  )

  private final case class OrderingEvidence(
    provider: String,
    expectedids: Vector[InformationId],
    snapshotids: Vector[InformationId]
  )

  private final class FailingBeforePublishDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    private var _armed = false

    def arm(): Unit =
      _armed = true

    override protected def versioned_mutation_checkpoint(
      checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      if (
        _armed &&
          checkpoint == EntityVersionedMutationCheckpoint.BeforePublish
      )
        Consequence.operationInvalid("injected-before-publication-failure")
      else
        Consequence.unit
  }

  private final class FailingNthBeforePublishDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with SearchableDataStore {
    private var _armed = false
    private var _mutation_count = 0
    private var _failure_mutation = Int.MaxValue

    def failOnNextMutation(index: Int): Unit = {
      _armed = true
      _mutation_count = 0
      _failure_mutation = index
    }

    override protected def versioned_mutation_checkpoint(
      checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      if (_armed && checkpoint == EntityVersionedMutationCheckpoint.BeforePublish) {
        _mutation_count += 1
        if (_mutation_count == _failure_mutation)
          Consequence.operationInvalid("injected-later-reset-deletion-failure")
        else
          Consequence.unit
      } else
        Consequence.unit

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      synchronized {
        ensure_collection(collection).flatMap(_.search(directive))
      }

    override def totalCountCapability(
      collection: DataStore.CollectionId
    ): TotalCountCapability =
      TotalCountCapability.Supported
  }

  private final class FailingPostClearRefreshDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with SearchableDataStore {
    private var _armed = false
    private var _search_count = 0

    def armPostClearRefresh(): Unit = {
      _armed = true
      _search_count = 0
    }

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      synchronized {
        if (_armed) {
          _search_count += 1
          if (_search_count == 2)
            Consequence.operationInvalid("injected-post-clear-refresh-failure")
          else
            ensure_collection(collection).flatMap(_.search(directive))
        } else
          ensure_collection(collection).flatMap(_.search(directive))
      }

    override def totalCountCapability(
      collection: DataStore.CollectionId
    ): TotalCountCapability =
      TotalCountCapability.Supported
  }

  private final class ReversedSearchableDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with SearchableDataStore {
    private var _emitted_reversed = false

    def emittedReversed: Boolean =
      _emitted_reversed

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      synchronized {
        ensure_collection(collection).flatMap(_.search(directive).map { searchresult =>
          if (searchresult.records.size > 1) {
            _emitted_reversed = true
            searchresult.copy(records = searchresult.records.reverse)
          } else
            searchresult
        })
      }

    override def totalCountCapability(
      collection: DataStore.CollectionId
    ): TotalCountCapability =
      TotalCountCapability.Supported
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
