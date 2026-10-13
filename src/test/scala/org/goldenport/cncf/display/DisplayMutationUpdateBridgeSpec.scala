package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.entity.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class DisplayMutationUpdateBridgeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutationUpdateBridgeFixture.*
  import DisplayMutationUpdateBridge.entityC

  "Display mutation update program in spec:display-mutation-bridge, phase:96" should {
    "canonical effects" which {
      "UB-E1 construct without effects and execute one selected change through the canonical interpreter" in {
        Given("a real registered embedded-revision Entity, resident value and primed View")
        val state = fixture()
        val bound = binding()
        val bridge = registration(bound)
        val prepared = success(prepareC(state, bound, Vector(title -> "After")))
        val grant = authorization(state.id)
        success(state.view.resolve(state.id)).getString("item_title") shouldBe Some("Before")
        state.provider.resetObservation()

        When("the bridge constructs the typed program")
        val program = bridge.programC(prepared, grant)

        Then("construction invokes neither provider nor canonical authorization or transition")
        program shouldBe a[Consequence.Success[?]]
        state.provider.attempts shouldBe 0
        state.provider.mutations shouldBe 0
        state.provider.loads shouldBe 0
        state.authorizationCalls shouldBe 0
        state.transitionCalls shouldBe 0

        When("the ordinary fixture owner evaluates and commits the program in the canonical UnitOfWork")
        val result = state.executeC(success(program))

        Then("one authoritative changed snapshot reaches revision two and preserves description")
        result.map(snapshot => (snapshot.record.getString("item_title"), snapshot.record.getString("item_description"), snapshot.revision.value)) shouldBe Consequence.success((Some("After"), Some("Description"), 2L))
        state.provider.attempts shouldBe 1
        state.provider.mutations shouldBe 1
        state.lastAuthorization shouldBe Some(grant)
        state.authorizationCalls shouldBe 1
        state.transitionCalls shouldBe 1
        val raw = success(state.rawC)
        raw.getString("item_title") shouldBe Some("After")
        raw.getString("item_description") shouldBe Some("Description")
        raw.getAny("revision") shouldBe Some(2L)
        state.collection.resolve(state.id).map(entity => (entity.title, entity.description, entity.revision.value)) shouldBe Consequence.success(("After", "Description", 2L))
        success(state.view.resolve(state.id)).getString("item_title") shouldBe Some("After")
        state.viewBuilds shouldBe 2
      }

      "UB-E2 preserve revision updatedAt and audit metadata for a current equal-value patch" in {
        Given("a current revision-one Entity with authoritative lifecycle and audit metadata")
        val state = fixture()
        val before = success(state.rawC)
        before.getAny("updated_at").isDefined shouldBe true
        before.getAny("updated_by").isDefined shouldBe true
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "Before")))
        val program = success(registration(bound).programC(prepared, authorization(state.id)))

        When("the real provider evaluates the strict current-revision equal-value patch")
        val result = state.executeC(program)

        Then("the authoritative result is a no-op and the entire persisted state is unchanged")
        result.map(_.revision.value) shouldBe Consequence.success(1L)
        result.map(_.record.asMap) shouldBe Consequence.success(before.asMap)
        success(state.rawC).asMap shouldBe before.asMap
        state.provider.attempts shouldBe 1
        state.provider.mutations shouldBe 0
      }

      "UB-E3 reject stale equality after a canonical intervening write" in {
        Given("a program prepared on revision one and an intervening canonical write of that same business value")
        val state = fixture()
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "Concurrent")))
        val program = success(registration(bound).programC(prepared, authorization(state.id)))
        success(state.canonicalUpdateC(BridgePatch(Update.set("Concurrent"), Update.noop[String]))).revision.value shouldBe 2L
        val before = success(state.rawC)
        state.provider.resetObservation()

        When("the earlier equal-value program reaches the actual provider with stale revision one")
        val result = state.executeC(program)

        Then("atomic stale comparison wins over equality without metadata or state changes")
        result match {
          case Consequence.Failure(conclusion) =>
            val expected = EntityConcurrencyMetadata.staleMutation[EntityRecordSnapshot](
              org.simplemodeling.model.datatype.EntityRevision.INITIAL,
              success(org.simplemodeling.model.datatype.EntityRevision.createC(2))).conclusion
            conclusion.isMatch(expected) shouldBe true
          case _ => fail("expected structured stale revision conflict")
        }
        success(state.rawC).asMap shouldBe before.asMap
        state.provider.attempts shouldBe 1
        state.provider.mutations shouldBe 0
        state.collection.resolve(state.id) shouldBe a[Consequence.Failure[?]]
      }
    }

    "canonical rejection" which {
      "UB-E4 preserve the genuine canonical authorizer Conclusion and resource" in {
        Given("an admitted program and a Component UnitOfWork authorizer that independently denies update")
        val state = fixture(denyAuthorization = true)
        val before = success(state.rawC)
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "Denied")))
        val grant = authorization(state.id)
        val program = success(registration(bound).programC(prepared, grant))

        When("the canonical interpreter evaluates authorization")
        val result = state.executeC(program)

        Then("the exact structured denial survives without transition or provider mutation")
        result shouldBe authorizationFailure
        state.lastAuthorization shouldBe Some(grant)
        state.authorizationCalls shouldBe 1
        state.transitionCalls shouldBe 0
        state.provider.attempts shouldBe 0
        success(state.rawC).asMap shouldBe before.asMap
      }

      "UB-E5 preserve independent canonical transition rejection of a valid prepared patch" in {
        Given("a domain-valid preparation and a rejecting canonical transition hook")
        val state = fixture(rejectTransition = true)
        val before = success(state.rawC)
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "Valid")))
        val program = success(registration(bound).programC(prepared, authorization(state.id)))

        When("the program reaches canonical transition validation")
        val result = state.executeC(program)

        Then("the original transition Conclusion survives and the provider is never invoked")
        result shouldBe transitionFailure
        state.transitionCalls shouldBe 1
        state.provider.attempts shouldBe 0
        success(state.rawC).asMap shouldBe before.asMap
      }

      "UB-E5 preserve preparation and canonical domain rejection of blank title" in {
        Given("the same blank title with ordinary preparation validation and an explicit pass-through preparation adapter")
        val state = fixture()
        val before = success(state.rawC)
        val ordinary = binding()
        val passthrough = binding(validatePreparation = false)
        val prepared = success(prepareC(state, passthrough, Vector(title -> " ")))
        val program = success(registration(passthrough).programC(prepared, authorization(state.id)))

        When("preparation checks the domain and the canonical hook independently checks the admitted patch")
        val rejectedpreparation = prepareC(state, ordinary, Vector(title -> " "))
        val rejectedexecution = state.executeC(program)

        Then("both preserve the domain Conclusion and canonical validation prevents a write")
        rejectedpreparation shouldBe domainFailure
        rejectedexecution shouldBe domainFailure
        state.transitionCalls shouldBe 1
        state.provider.attempts shouldBe 0
        success(state.rawC).asMap shouldBe before.asMap
      }
    }

    "program admission UB-E6" which {
      "reject foreign logical Source owner and unregistered Entity field before invocation" in {
        Given("one retained registration and independently admitted foreign Source owner and field bindings")
        val state = fixture()
        val bound = binding()
        val bridge = registration(bound)
        val foreignsource = success(org.goldenport.cncf.component.ComponentId.parseC("org.goldenport.fixture.Foreign")
          .flatMap(componentid => DisplayProtocol.Source.createC(componentid, "BridgeItem")))
        val otherresource = success(DisplayProtocol.Source.createC(source.componentId, "ForeignItem"))
        val alternatives = Vector(binding(sourceValue = foreignsource), binding(sourceValue = otherresource),
          binding(ownerId = "ForeignOwner"), binding(titleField = "foreignField"))
        val preparations = alternatives.map(value => success(prepareC(state, value, Vector(title -> "After"))))
        state.provider.resetObservation()

        When("each foreign preparation is offered to the retained registration")
        val results = preparations.map(bridge.programC(_, authorization(state.id)))

        Then("every mismatch is rejected before canonical authorization reads or provider writes")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        state.authorizationCalls shouldBe 0
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
      }

      "require exact resource and persistent collection without interpreting EntityId" in {
        Given("a foreign canonical resource collection and a mismatching typed persistent collection")
        val state = fixture()
        val bound = binding()
        val foreigncollection = EntityCollectionId("test", "phase96", "foreign_item")
        val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "foreign_item", foreigncollection, entropy = "foreign_item")
        val foreignprepared = success(prepareC(state, bound, Vector(title -> "After"), resourceId = Some(foreignid)))
        val prepared = success(prepareC(state, bound, Vector(title -> "After")))
        val bridge = registration(bound)
        val othercodec = registration(bound, patchPersistent(collection = foreigncollection))
        state.provider.resetObservation()

        When("program admission compares both canonical collection values")
        val wrongresource = bridge.programC(foreignprepared, authorization(foreignid))
        val wrongpersistent = othercodec.programC(prepared, authorization(state.id))

        Then("both collection mismatches reject without any provider invocation")
        wrongresource shouldBe a[Consequence.Failure[?]]
        wrongpersistent shouldBe a[Consequence.Failure[?]]
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
      }

      "reject unexpected seed fields and managed storage overrides" in {
        Given("title-only preparation with an unselected seeded description and codecs emitting managed fields")
        val state = fixture()
        val bound = binding()
        val unexpected = success(prepareC(state, bound, Vector(title -> "After"),
          seedValue = BridgePatch(Update.noop[String], Update.set("Unexpected"))))
        val prepared = success(prepareC(state, bound, Vector(title -> "After")))
        val managednames = Vector("updated_at", "owner_id", "permission", "aliveness", "cncf_revision")
        val managedbridges = managednames.map(name => registration(bound, patchPersistent(storeRecord = patch =>
          Record.dataAuto("item_title" -> patch.title, name -> Update.set("override")))))
        val duplicatebridge = registration(bound, patchPersistent(storeRecord = patch => {
          val record = Record.dataAuto("item_title" -> patch.title)
          Record(record.fields ++ record.fields)
        }))
        state.provider.resetObservation()

        When("internal canonical patch inspection checks selected keys uniqueness and managed policy")
        val results = Vector(registration(bound).programC(unexpected, authorization(state.id)),
          duplicatebridge.programC(prepared, authorization(state.id))) ++
          managedbridges.map(_.programC(prepared, authorization(state.id)))

        Then("unexpected duplicate security lifecycle and managed fields all reject before effects")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
        state.authorizationCalls shouldBe 0
      }

      "require existing exact authorization scope while retaining ordinary authorization evidence" in {
        Given("a valid preparation and authorizations with one mismatching mandatory scope member")
        val state = fixture()
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "After")))
        val grant = authorization(state.id)
        val alternatives = Vector(grant.copy(resourceFamily = "aggregate"), grant.copy(accessKind = "read"),
          grant.copy(targetId = None), grant.copy(collectionName = None), grant.copy(sourceComponentName = None),
          grant.copy(targetComponentName = Some("foreign")), null)
        state.provider.resetObservation()

        When("program construction checks mandatory authorization correspondence")
        val results = alternatives.map(registration(bound).programC(prepared, _))

        Then("no absent or foreign scope is promoted into authority")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        state.authorizationCalls shouldBe 0
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
      }

      "reject malformed and aliased registration with redacted callback failures" in {
        Given("malformed selections owners fields and persistent mappings")
        val state = fixture()
        val bound = binding()
        val mappings = Vector(patchPersistent(fieldName = _ => "item_title"),
          patchPersistent(fieldName = _ => "owner_id"), patchPersistent(fieldName = _ => ""),
          patchPersistent(fieldName = _ => null),
          patchPersistent(fieldName = _ => throw new IllegalStateException("private callback payload")))
        state.provider.resetObservation()

        When("registration validates shape and canonical storage mappings")
        val results = Vector(entityC(null, "BridgeItem", collectionId, Vector("title"), patchCodec),
          entityC(bound.selection, " ", collectionId, Vector("title"), patchCodec),
          entityC(bound.selection, "BridgeItem", null, Vector("title"), patchCodec),
          entityC(bound.selection, "BridgeItem", collectionId, Vector.empty, patchCodec),
          entityC(bound.selection, "BridgeItem", collectionId, Vector("title", "title"), patchCodec),
          entityC(bound.selection, "BridgeItem", collectionId, Vector("title"), null: EntityPersistentUpdate[BridgePatch])) ++
          mappings.map(entityC(bound.selection, "BridgeItem", collectionId, Vector("title", "description"), _))

        Then("all malformed mappings reject with fixed diagnostics and no invocation")
        results.foreach { result =>
          result shouldBe a[Consequence.Failure[?]]
          result match {
            case Consequence.Failure(conclusion) => conclusion.show should not include "private callback payload"
            case _ => fail("expected registration rejection")
          }
        }
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
      }

      "contain null and throwing persistent outputs before producing a program" in {
        Given("valid typed preparation with null or throwing collection and serialization callbacks")
        val state = fixture()
        val bound = binding()
        val prepared = success(prepareC(state, bound, Vector(title -> "After")))
        val codecs = Vector(patchPersistent(collection = null), patchPersistent(storeRecord = _ => null),
          patchPersistent(storeRecord = _ => throw new IllegalStateException("private callback payload")))
        state.provider.resetObservation()

        When("program construction inspects the persistent boundary")
        val results = codecs.map(codec => registration(bound, codec).programC(prepared, authorization(state.id))) :+
          registration(bound).programC(null, authorization(state.id))

        Then("fixed redacted callback or shape failures stop before interpreter execution")
        results.foreach { result =>
          result shouldBe a[Consequence.Failure[?]]
          result match {
            case Consequence.Failure(conclusion) => conclusion.show should not include "private callback payload"
            case _ => fail("expected boundary rejection")
          }
        }
        state.provider.loads shouldBe 0
        state.provider.attempts shouldBe 0
      }
    }

    "Entity-only target ownership" which {
      "UB-E7 reject an Aggregate target without erasing or downgrading it to Entity" in {
        Given("a genuinely prepared Aggregate target and an explicitly registered Entity bridge")
        val state = fixture()
        val bound = binding()
        val aggregate = binding(aggregate = true)
        val prepared = success(prepareC(state, aggregate, Vector(title -> "Aggregate value")))
        prepared.targets shouldBe Vector(DisplayMutationBinding.UpdateTarget.Aggregate("BridgeItem", "title"))
        state.provider.resetObservation()

        When("the Entity bridge receives the Aggregate preparation")
        val result = registration(bound).programC(prepared, authorization(state.id))

        Then("it rejects before provider invocation and preserves the resource")
        result shouldBe a[Consequence.Failure[?]]
        state.provider.attempts shouldBe 0
        state.provider.loads shouldBe 0
        state.authorizationCalls shouldBe 0
        success(state.rawC).getString("item_title") shouldBe Some("Before")
      }
    }
  }
}
