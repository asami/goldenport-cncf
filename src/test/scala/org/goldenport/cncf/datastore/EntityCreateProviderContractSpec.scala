package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityCreateFailureSafetyFixture, EntityObservedDeleteCanonicalFixture}
import org.goldenport.record.{Field, Record}
import org.goldenport.observation.Descriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 */
final class EntityCreateProviderContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateProviderFixture.*
  import EntityObservedDeleteCanonicalFixture.{reason, success}

  "CA-PORT strict create admission" should {
    "retain Save/Delete-only scope when the shared effect algebra grows" which {
      "reject Create before invoking the public space provider" in {
        Given("docs/spec/entity-create-atomic-provider.md CA2/CA7: a seeded memory delegate and qualified port receiving a well-shaped shared Create effect")
        val setup = new DirectSession("closed_effect_space", DataStore.inMemory())
        setup.seed()
        val provider = new QualifiedStore(setup.store)
        val before = setup.snapshot()
        val plan = _create_effect_plan(setup)

        When("the public DataStoreSpace strict-create route admits the plan")
        val result = setup.createC(plan, provider)

        Then("structured policy rejection precedes provider invocation and every real row is retained")
        _check_create_rejection(result)
        provider.createAttempts shouldBe 0
        provider.genericWrites shouldBe 0
        setup.snapshot() shouldBe before
      }

      Vector(false, true).foreach { searchable =>
        s"reject Create on the direct memory port with searchable=$searchable" in {
          Given("docs/spec/entity-create-atomic-provider.md CA2/CA7: an owned memory provider and complete selected/unrelated state")
          val actual = if (searchable) DataStore.inMemorySearchable() else DataStore.inMemory()
          val setup = new DirectSession(s"closed_effect_memory_$searchable", actual)
          setup.seed()
          val before = setup.snapshot()
          val provider = actual match {
            case value: EntityCreateDataStore => value
            case _ => fail("The owned memory store must expose strict create")
          }
          val plan = _create_effect_plan(setup)

          When("the direct owned port receives the unsupported shared effect")
          val result = provider.createEntity(plan)(using setup.context)

          Then("it returns the same structured policy failure instead of throwing or publishing")
          _check_create_rejection(result)
          setup.snapshot() shouldBe before
        }
      }

      "reject Create on the direct SQLite port before native publication" in {
        Given("docs/spec/entity-create-atomic-provider.md CA2/CA7: a task-owned native SQLite provider and independent complete state reader")
        EntityCreateFailureSafetyFixture.withSqlite { (database, _) =>
          val provider = sqliteFaultStore(database)
          try {
            val setup = new DirectSession("closed_effect_sqlite", provider)
            setup.seed()
            val reader = database.open()
            val before = setup.snapshot(reader)
            val plan = _create_effect_plan(setup)

            When("the direct SQL strict-create port receives the unsupported shared effect")
            val result = provider.createEntity(plan)(using setup.context)

            Then("the declared policy rejects structurally and every native row remains unchanged")
            _check_create_rejection(result)
            setup.snapshot(reader) shouldBe before
          } finally success(provider.closeC())
        }
      }
    }

    "retain real storage before any unsupported-provider invocation" which {
      Vector("legacy", "default-port", "missing-create", "missing-readback", "missing-side", "null-capabilities").foreach { mode =>
        s"reject $mode without a generic-write fallback" in {
          Given("a seeded real memory delegate and one explicitly unqualified wrapper")
          val actual = DataStore.inMemory()
          val setup = new DirectSession(s"port_${mode.replace('-', '_')}", actual)
          setup.seed()
          val before = setup.snapshot()
          val provider: LegacyStore = mode match {
            case "legacy" => new LegacyStore(actual)
            case "default-port" => new DefaultPortStore(actual)
            case "missing-create" => new QualifiedStore(actual, missingFeature = Some(EntityMutationProviderFeature.AtomicCreate))
            case "missing-readback" => new QualifiedStore(actual, missingFeature = Some(EntityMutationProviderFeature.AuthoritativeRecordResult))
            case "missing-side" => new QualifiedStore(actual, missingFeature = Some(EntityMutationProviderFeature.AtomicSideEffects))
            case _ => new QualifiedStore(actual, nullCapabilities = true)
          }
          When("DataStoreSpace attempts strict creation through the selected wrapper")
          val result = setup.createC(setup.plan(), provider)
          Then("unsupported capability retains the complete underlying snapshot and performs no fallback writes")
          result shouldBe a[Consequence.Failure[?]]
          reason(result) shouldBe Some("unsupported-capability")
          provider.genericWrites shouldBe 0
          setup.snapshot() shouldBe before
        }
      }
    }

    "admit well-shaped plans before provider invocation" which {
      Vector("null-plan", "null-collection", "null-entry", "null-record", "null-effects", "null-effect",
        "null-save-record", "duplicate-keys", "blank-key", "control-key", "null-field", "null-value-wrapper", "wrong-record-id",
        "blank-entry", "null-entry-value", "control-entry", "null-side-collection", "null-side-entry", "wrong-side-id").foreach { mode =>
        s"reject $mode with complete real-state retention" in {
          Given("a qualified real delegate and an independently malformed create plan")
          val actual = DataStore.inMemory()
          val setup = new DirectSession(s"invalid_${mode.replace('-', '_')}", actual)
          setup.seed()
          val provider = new QualifiedStore(actual)
          val before = setup.snapshot()
          val original = setup.plan()
          val malformed = mode match {
            case "null-plan" => null
            case "null-collection" => original.copy(collection = null)
            case "null-entry" => original.copy(entryId = null)
            case "null-record" => original.copy(record = null)
            case "null-effects" => original.copy(sideEffects = null)
            case "null-effect" => original.copy(sideEffects = Vector(null))
            case "null-save-record" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Save(setup.sideCollection, setup.sideEntry, null)))
            case "duplicate-keys" => original.copy(record = Record(original.record.fields ++ original.record.fields))
            case "blank-key" => original.copy(record = original.record ++ Record.dataAuto(" " -> "value"))
            case "control-key" => original.copy(record = original.record ++ Record.dataAuto("bad\nkey" -> "value"))
            case "null-field" => original.copy(record = Record(Vector(null)))
            case "null-value-wrapper" => original.copy(record = Record(Vector(Field("business", null))))
            case "blank-entry" => original.copy(entryId = DataStore.StringEntryId(" "))
            case "null-entry-value" => original.copy(entryId = DataStore.StringEntryId(null))
            case "control-entry" => original.copy(entryId = DataStore.StringEntryId("fresh\n"))
            case "null-side-collection" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(null, setup.sideEntry)))
            case "null-side-entry" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, null)))
            case "wrong-side-id" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Save(setup.sideCollection, setup.sideEntry,
              Record.dataAuto("id" -> "other"))))
            case _ => original.copy(record = original.record.upsertSingle("id", "other"))
          }
          When("the malformed plan reaches DataStoreSpace's boundary")
          val result = setup.createC(malformed, provider)
          Then("the provider is never called and no selected or unrelated record changes")
          result shouldBe a[Consequence.Failure[?]]
          provider.createAttempts shouldBe 0
          provider.genericWrites shouldBe 0
          setup.snapshot() shouldBe before
        }
      }
    }

    "keep one actual provider and isolated physical targets" which {
      "reject a side mapping to another real delegate before either receives create" in {
        Given("different actual root and side stores selected by their collection names")
        val actual = DataStore.inMemory()
        val side = DataStore.inMemory()
        val setup = new DirectSession("cross_provider", actual)
        setup.seed()
        val before = setup.snapshot()
        val rootprovider = new QualifiedStore(actual, acceptedName = Some(setup.rootCollection.collectionName))
        val sideprovider = new QualifiedStore(side, acceptedName = Some(setup.sideCollection.collectionName))
        val space = new DataStoreSpace().addDataStore(rootprovider).addDataStore(sideprovider)
        When("the selected root plan requires the different side provider")
        val result = space.createEntity(setup.plan())(using setup.context)
        Then("same-provider admission rejects before calls and both real stores retain their full state")
        reason(result) shouldBe Some("provider-domain-mismatch")
        rootprovider.createAttempts shouldBe 0
        sideprovider.createAttempts shouldBe 0
        setup.snapshot() shouldBe before
        setup.snapshot(side).side shouldBe None
        setup.snapshot(side).oldside shouldBe None
      }

      Vector("root-alias", "repeated-side").foreach { mode =>
        s"reject $mode before publication in actual memory" in {
          Given("a seeded memory plan whose side targets alias one physical entry")
          val setup = new DirectSession(s"target_${mode.replace('-', '_')}", DataStore.inMemory())
          setup.seed()
          val before = setup.snapshot()
          val plan = if (mode == "root-alias") setup.plan().copy(sideEffects =
            Vector(EntityVersionedSideEffect.Delete(setup.rootCollection, DataStore.StringEntryId(setup.rootEntry.print))))
          else setup.plan().copy(sideEffects = setup.plan().sideEffects ++ setup.plan().sideEffects)
          When("the actual provider admits the physical targets")
          val result = setup.createC(plan)
          Then("the invalid target plan cannot publish and the complete snapshot remains")
          result shouldBe a[Consequence.Failure[?]]
          setup.snapshot() shouldBe before
        }
      }

      "reject SQL normalized id-column aliases before writing rows" in {
        Given("a task-owned SQLite provider configured to normalize column names")
        EntityCreateFailureSafetyFixture.withSqlite { (database, _) =>
          val provider = sqliteFaultStore(database, normalizeColumns = true)
          try {
            val setup = new DirectSession("port_sql_column", provider)
            setup.seed()
            val before = setup.snapshot(database.open())
            val plan = setup.plan().copy(record = setup.plan().record ++ Record.dataAuto("Id" -> "foreign"))
            When("strict create receives a noncanonical field normalized to the physical id column")
            val result = setup.createC(plan)
            Then("structured argument rejection preserves all independently read rows")
            result shouldBe a[Consequence.Failure[?]]
            setup.snapshot(database.open()) shouldBe before
          } finally success(provider.closeC())
        }
      }
    }

    "treat foreign publication and result trust as indeterminate" which {
      ReturnedMode.values.filterNot(_ == ReturnedMode.Delegate).foreach { mode =>
        s"never acknowledge $mode as successful or automatically replay it" in {
          Given("a real seeded delegate behind a foreign port returning the selected result condition")
          val actual = DataStore.inMemory()
          val setup = new DirectSession(s"foreign_$mode", actual)
          setup.seed()
          val before = setup.snapshot()
          val provider = new QualifiedStore(actual, returned = mode)
          val plan = setup.plan()
          When("DataStoreSpace invokes the foreign port once")
          val result = setup.createC(plan, provider)
          val after = setup.snapshot()
          Then("the caller receives indeterminate failure and the actual provider has exactly its observed state")
          result shouldBe a[Consequence.Failure[?]]
          reason(result) shouldBe Some("transaction-indeterminate")
          provider.createAttempts shouldBe 1
          provider.genericWrites shouldBe 0
          after.oldroot shouldBe before.oldroot
          after.oldside shouldBe before.oldside
          if (mode == ReturnedMode.CommitThenThrow || mode == ReturnedMode.CommitThenIndeterminate) {
            after.root shouldBe Some(plan.record.asMap)
            after.side shouldBe Some(plan.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
          } else after shouldBe before
        }
      }
    }

    "retain positive qualified compatibility" which {
      "return the actually stored record through an explicitly delegating port-only wrapper" in {
        Given("a real provider with seeded unrelated rows behind a qualified wrapper")
        val actual = DataStore.inMemorySearchable()
        val setup = new DirectSession("qualified", actual)
        setup.seed()
        val before = setup.snapshot()
        val provider = new QualifiedStore(actual)
        When("DataStoreSpace looks up port-only capabilities and delegates strict create")
        val capabilities = success(new DataStoreSpace().useDataStore(provider).entityMutationProviderCapabilities(setup.rootCollection))
        val result = setup.createC(setup.plan(), provider)
        val after = setup.snapshot()
        Then("the authoritative result exactly matches storage without generic writes or unrelated changes")
        capabilities.supports(EntityMutationProviderFeature.AtomicCreate) shouldBe true
        success(result).record.asMap shouldBe after.root.get
        after.side shouldBe Some(setup.plan().sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
        after.oldroot shouldBe before.oldroot
        after.oldside shouldBe before.oldside
        provider.createAttempts shouldBe 1
        provider.genericWrites shouldBe 0
      }

      "preserve unmanaged domain revision text and nullable business data" in {
        Given("an unversioned canonical collection with a qualified real provider and domain-owned revision text")
        val actual = DataStore.inMemory()
        val provider = new QualifiedStore(actual)
        val base = EntityObservedDeleteCanonicalFixture.fixture(token = "unmanaged_create", provider = provider, managed = false)
        val setup = new EntityCreateFailureSafetyFixture.Session(base)
        val input = setup.input(EntityCreateFailureSafetyFixture.BodyMode.Inline, Some(setup.freshId))
          .copy(record = Record.dataAuto("title" -> "Domain", "revision" -> "draft-A", "business" -> null))
        When("canonical strict create prepares and publishes the unversioned domain record")
        val result = setup.createC(input)
        Then("domain revision remains ordinary text and no detached revision is fabricated")
        success(result).record.get.getString("revision") shouldBe Some("draft-A")
        success(result).record.get.getAny("cncf_revision") shouldBe None
        success(result).record.map(_.asMap) shouldBe setup.snapshot(setup.freshId).root
        provider.createAttempts shouldBe 1
      }
    }
  }

  private def _create_effect_plan(session: DirectSession): EntityCreatePlan =
    session.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Create(
      session.sideCollection, session.sideEntry, Record.dataAuto("content" -> "unsupported strict-create effect"))))

  private def _check_create_rejection(result: Consequence[EntityCreateResult]): Unit = result match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.taxonomy shouldBe Consequence.argumentPolicyViolation(
        "sideEffects", "entity-create.side-effect-kinds", "Save or Delete", "Create").conclusion.observation.taxonomy
      conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Policy("entity-create.side-effect-kinds"))
    case _ => fail("Strict create must reject the unsupported shared Create effect")
  }
}
