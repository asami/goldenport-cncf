package org.goldenport.cncf.display

import logicalui.DisplayMutationServiceDomain
import logicalui.DisplayMutationServiceDomain.BodyMode
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.datastore.{DataStore, EntityMutationProviderFeature}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Property
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 19, 2026
 */
final class DisplayMutationServiceLifecycleSpec extends AnyWordSpec with DisplayMutationServiceFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  import DisplayMutation.Operation
  private val _governed = afterWord("under docs/spec/display-mutation-service.md DLC-01..13, docs/design/display-mutation-service.md DLC-D01 and RULE.md")

  "Ordinary Display lifecycle" should {
    "canonical typed commands" which _governed {
      "DLC-E1 create typed Entity, Aggregate and alias roots with generated or supplied server identity and exact defaults/body/state" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations;
          supplied <- Vector(false, true); mode <- BodyMode.values.toVector) {
          Given(s"a normal $backend $target/$representation with $mode server defaults and supplied=$supplied; the selected root is durably absent behind warmed real Views")
          val session = open(target, representation, backend = backend, lifecycle = Some(LifecycleSetup(supplied, mode)))
          try {
            session.prepareLifecycle(true)
            if (mode == BodyMode.Absent && supplied) {
              Given("the selected resident is absent while the already-warm semantic Views and unrelated residents retain their normal baseline")
              session.component.realm.remove(session.component.id)
              session.component.memory.remove(session.component.id)
            }
            val fields = if (supplied) lifecycleFields.reverse else lifecycleFields.filterNot(_.location.fieldId == "nullable")
            val before = session.lifecycleSnapshot()
            before.rows.selected shouldBe None
            before.rows.body shouldBe None
            if (mode == BodyMode.Absent && supplied) {
              before.resident.selected shouldBe None
              before.resident.memoryselected shouldBe None
            }
            val wanted = expectedLifecycleCreate(session, before, fields)
            When("the normal resolver and ActionEngine interpret one authorized typed create on the current UnitOfWork and commit it")
            val attempt = session.execute(session.lifecycleRequest(Operation.Create, fields))
            Then("the actual provider acknowledgement produces INITIAL Created, one contextual Detail and no generic write or replay")
            checkLifecycleResult(attempt, session, true, 1L)
            session.log.idCalls shouldBe (if (supplied) Vector.empty else Vector(session.component.collectionId -> "entity-create"))
            session.log.conversions shouldBe DisplayMutationServiceDomain.fieldIds.filter(name => fields.exists(_.location.fieldId == name))
            session.log.validations shouldBe 1
            session.log.commands shouldBe (if (target == Target.Entity) 0 else 1)
            session.log.events.count(_.event == "find") shouldBe 1
            session.log.events.count(_.event == "capture") shouldBe 1
            checkLifecycleState(session, wanted)
          } finally session.close()
        }
      }

      "DLC-E2 delete independently seeded logical and physical roots at the matched prior revision without automatic Detail" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations; logical <- Vector(false, true)) {
          Given(s"a normal $backend $target/$representation with independently seeded revision seven, logical=$logical and complete warmed resident/Views")
          val session = open(target, representation, backend = backend, lifecycle = Some(LifecycleSetup(logical = logical)))
          try {
            session.prepareLifecycle(false, 7L, logical)
            val before = session.lifecycleSnapshot()
            val wanted = expectedLifecycleDelete(session, before, 7L, logical)
            When("normal deletion authorizes the instance, validates the authoritative root and performs one observed-delete capture and current commit")
            val attempt = session.execute(session.lifecycleRequest(Operation.Delete, observed = 7L))
            Then("Deleted retains the actual matched revision, requests reload-list and never invokes a deleted Detail or pure create command")
            checkLifecycleResult(attempt, session, false, 7L)
            session.log.validations shouldBe 1
            session.log.commands shouldBe 0
            session.log.conversions shouldBe Vector.empty
            session.log.events.count(_.event == "find") shouldBe 0
            checkLifecycleState(session, wanted)
            if (logical) {
              session.rows().body.map(_.fields) shouldBe before.rows.body.map(_.fields)
              When("a later explicit ordinary read asks for the tombstoned Detail")
              val read = session.read()
              Then("normal visibility returns the known entity-not-found failure while the raw tombstone remains durable")
              conclusion(read.result).observation.taxonomy shouldBe missingConclusion(session.component.id).observation.taxonomy
            }
          } finally session.close()
        }
      }

      "DLC-E3 preserve full state at authorization, selection, availability, field, revision and pure domain rejection gates" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations; create <- Vector(false, true)) {
          Given(s"an actual $backend $target/$representation normal lifecycle route and independently reset create=$create preconditions")
          val session = open(target, representation, backend = backend, lifecycle = Some(LifecycleSetup()))
          try {
            val modes = if (create) Vector("factory", "unauthenticated", "selection", "unavailable", "readonly", "duplicate", "duplicate-field", "managed", "kind", "read-only-field", "unknown-field", "missing", "domain")
              else Vector("factory", "unauthenticated", "selection", "collection", "wrong-id", "unavailable", "readonly", "stale", "maximum", "missing", "domain")
            modes.foreach { mode =>
              Given(s"the complete pre-action durable/native/resident/View oracle for $mode rejection")
              session.prepareLifecycle(create || mode == "missing", if (mode == "maximum") Long.MaxValue else 7L)
              val operation = if (create) Operation.Create else Operation.Delete
              var fields = lifecycleFields
              if (mode == "managed") fields = Vector(field("id", DisplayValue.Text(session.component.id.value)))
              if (mode == "kind") fields = Vector(field("title", DisplayValue.Number(BigDecimal(1))))
              if (mode == "read-only-field") fields = Vector(field("stage", DisplayValue.Text("forged")))
              if (mode == "unknown-field") fields = Vector(field("unknown", DisplayValue.Text("forged")))
              if (mode == "missing" && create) fields = lifecycleFields.filterNot(_.location.fieldId == "title")
              if (mode == "domain") fields = Vector(field("title", DisplayValue.Text(" ")))
              val raw = session.lifecyclePayload(operation, fields)
              val foreigncollection = org.simplemodeling.model.datatype.EntityCollectionId("sys", "sys", "foreign")
              val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "selected",
                if (mode == "collection") foreigncollection else session.component.collectionId, entropy = "absent_target")
              val payload = if (mode == "selection") raw.replace("normal-update-reference", "foreign-resource")
                else if (mode == "duplicate") raw.replace("\"version\":1", "\"version\":1,\"version\":1")
                else if (mode == "duplicate-field") {
                  val item = io.circe.parser.parse(raw).toOption.get.hcursor.downField("fields").downN(0).focus.get.noSpaces
                  raw.replace("\"fields\":[", "\"fields\":[" + item + ",")
                } else if (mode == "collection" || mode == "wrong-id") raw.replace(session.component.id.value, foreignid.value)
                else raw
              session.log.denyFactory = mode == "factory"
              session.log.rejectDelete = mode == "domain"
              if (mode == "unavailable") session.log.availability = Consequence.success(DisplayMutation.Availability.enabled.copy(
                create = DisplayMutation.AvailabilityState.Disabled, delete = DisplayMutation.AvailabilityState.Disabled))
              if (mode == "readonly") session.log.availability = Consequence.success(DisplayMutation.Availability.enabled.copy(
                delete = DisplayMutation.AvailabilityState.Hidden,
                readonlyFields = Set(DisplayMutation.FieldLocator("main", "title"))))
              val before = session.lifecycleSnapshot()
              val wanted = expectedLifecycleDeleteRejection(before, DeleteRejectionBoundary.BeforeCapture)
              val expectedmissing = authorizationMissingConclusion(if (mode == "wrong-id") foreignid else session.component.id)
              val expectedmaximum = conclusion(revision(Long.MaxValue).nextC).observation.taxonomy
              val expectedreason = if (mode == "unavailable" || (!create && mode == "readonly")) Some("operation-unavailable")
                else if (mode == "stale") Some("stale-entity-revision") else None
              val active = session.execution(authenticated = mode != "unauthenticated")
              val request = session.lifecycleRawRequest(operation, payload, if (mode == "stale") 6L else if (mode == "maximum") Long.MaxValue else 7L)
              When("normal command admission reaches the responsible gate before a provider attempt or contextual read")
              val attempt = session.execute(request, active)
              Then("the structured rejection preserves every predeclared state field and does not invoke a canonical writer")
              checkLifecyclePreserved(session, wanted, attempt, converted = create && (mode == "domain" || mode == "missing"))
              if (mode == "factory") (conclusion(attempt.result) eq denied.conclusion) shouldBe true
              if (mode == "domain" || (create && mode == "missing"))
                (conclusion(attempt.result) eq domainFailure.conclusion) shouldBe true
              expectedreason.foreach(reason => reasons(attempt.result) should contain(reason))
              if (!create && (mode == "missing" || mode == "wrong-id")) {
                checkLifecycleFailure(conclusion(attempt.result), expectedmissing)
                reasons(attempt.result) should not contain "entity-mutation-target-not-found"
              }
              if (mode == "stale") conclusion(attempt.result).observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
              if (mode == "maximum") conclusion(attempt.result).observation.taxonomy shouldBe expectedmaximum
              if (Set("selection", "collection", "duplicate", "duplicate-field", "managed", "kind", "read-only-field", "unknown-field").contains(mode))
                conclusion(attempt.result).observation.taxonomy shouldBe Taxonomy.argumentInvalid
              session.log.validations shouldBe (if (mode == "domain" || (create && mode == "missing")) 1 else 0)
              session.log.idCalls shouldBe Vector.empty
              session.log.commands shouldBe 0
            }
            if (target == Target.Entity) checkLifecycleForeignPrincipal(session, create)
            if (!create) DeleteRejectionBoundary.values.toVector.foreach(boundary => checkLifecycleDeadTarget(session, boundary))
          } finally session.close()
          Given("a static lifecycle registration explicitly unsupported for the selected create/delete command")
          val capabilities = DisplayMutationServiceDomain.lifecycleCapabilities.copy(
            create = DisplayMutation.Support.Unsupported, delete = DisplayMutation.Support.Unsupported)
          val unsupported = open(target, representation, backend = backend, lifecycle = Some(LifecycleSetup(capabilities = capabilities)))
          try {
            unsupported.prepareLifecycle(create)
            val before = unsupported.lifecycleSnapshot()
            val expectedreason = "operation-unsupported"
            When("the normal lifecycle Entry selects its unchanged declared support gate")
            val attempt = unsupported.execute(unsupported.lifecycleRequest(if (create) Operation.Create else Operation.Delete))
            Then("the exact unsupported reason preserves every state dimension without conversion domain command or provider fallback")
            reasons(attempt.result) should contain(expectedreason)
            checkLifecyclePreserved(unsupported, before, attempt)
            unsupported.log.validations shouldBe 0
          } finally unsupported.close()
        }
      }

      "DLC-E4 reject duplicate create, missing actual provider features and invalid closed lifecycle registration without fallback" in {
        for (backend <- Vector("memory", "sqlite"); target <- Target.values.toVector; representation <- representations) {
          Given(s"a real $backend $target/$representation whose independently seeded ID already exists")
          val session = open(target, representation, backend = backend, lifecycle = Some(LifecycleSetup()))
          try {
            val before = session.lifecycleSnapshot()
            When("ordinary create reaches the real provider unique-ID arbitration")
            val duplicate = session.execute(session.lifecycleRequest(Operation.Create))
            Then("the original root/body/native/resident/View state survives exactly and no fallback writer or Detail is invoked")
            duplicate.result shouldBe a[Consequence.Failure[?]]
            duplicate.execution.lifecycleObservers.map(_.creates.size).sum shouldBe 1
            duplicate.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
            session.log.events.count(_.event == "find") shouldBe 0
            checkLifecycleState(session, before)
            Given("the existing admitted typed bindings and exact registered collection")
            val input = session.component.inputBinding
            val create = session.component.createBinding
            When("server admission supplies a null validator or mismatched create descriptor")
            val nullvalidator = DisplayMutationRuntime.entityLifecycleC(input, session.component.ownerName, session.component.collection,
              DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
              DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), create, session.component.createSeed,
              DisplayMutationServiceDomain.createPersistent(session.component.collectionId), null, session.log.available)
            val mismatch = DisplayMutationServiceDomain.createBinding(create.selection.source, session.component.ownerName,
              target != Target.Entity, capabilities = input.capabilities.copy(create = DisplayMutation.Support.Unsupported))
            val wrongdescriptor = DisplayMutationRuntime.entityLifecycleC(input, session.component.ownerName, session.component.collection,
              DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
              DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), mismatch, session.component.createSeed,
              DisplayMutationServiceDomain.createPersistent(session.component.collectionId), (_: DisplayMutationServiceDomain.Root) => Consequence.unit,
              session.log.available)
            Then("registration rejects before publication and the closed constructor/writer/capture boundaries remain inaccessible")
            nullvalidator.toOption shouldBe None
            wrongdescriptor.toOption shouldBe None
            DisplayMutationServiceDomain.privateConstructionDenied shouldBe true
            DisplayMutationServiceDomain.erasedWriterDenied shouldBe true
            DisplayMutationServiceDomain.captureConstructionDenied shouldBe true
            DisplayMutationServiceDomain.lifecycleCaptureConstructionDenied shouldBe true
            Given("the same closed typed registration with independently chosen invalid callbacks identity collection target and storage mappings")
            val canonical = DisplayMutationServiceDomain.createPersistent(session.component.collectionId)
            def _persistent_(name: String): EntityPersistentCreate[DisplayMutationServiceDomain.CreateShape] = new EntityPersistentCreate[DisplayMutationServiceDomain.CreateShape] {
              def id(entity: DisplayMutationServiceDomain.CreateShape): Option[org.simplemodeling.model.datatype.EntityId] = canonical.id(entity)
              def collection(entity: DisplayMutationServiceDomain.CreateShape): org.simplemodeling.model.datatype.EntityCollectionId = canonical.collection(entity)
              def toRecord(entity: DisplayMutationServiceDomain.CreateShape): org.goldenport.record.Record = canonical.toRecord(entity)
              override def toStoreRecord(entity: DisplayMutationServiceDomain.CreateShape): org.goldenport.record.Record = canonical.toStoreRecord(entity)
              override def storeFieldName(logicalName: String): String = name
            }
            def _register_(createbinding: DisplayMutationBinding.Binding[DisplayMutationServiceDomain.CreateShape] = create,
              seed: DisplayMutationServiceDomain.CreateShape = session.component.createSeed,
              persistent: EntityPersistentCreate[DisplayMutationServiceDomain.CreateShape] = canonical,
              availability: org.goldenport.cncf.context.ExecutionContext => Consequence[DisplayMutation.Availability] = session.log.available)
                : Consequence[DisplayMutationRuntime.Binding] =
              DisplayMutationRuntime.entityLifecycleC(input, session.component.ownerName, session.component.collection,
                DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
                DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), createbinding, seed, persistent,
                (_: DisplayMutationServiceDomain.Root) => Consequence.unit, availability)
            val foreigncollection = org.simplemodeling.model.datatype.EntityCollectionId("sys", "sys", "foreign")
            val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "selected", foreigncollection, entropy = "foreign_seed")
            val foreigntarget = DisplayMutationServiceDomain.createBinding(create.selection.source, "foreign-owner", false)
            val foreignfamily = DisplayMutationServiceDomain.createBinding(create.selection.source, session.component.ownerName, target == Target.Entity)
            type CreateFields = Vector[DisplayMutationBinding.FieldBinding[DisplayMutationServiceDomain.CreateShape]]
            def _metadata_(change: CreateFields => CreateFields): DisplayMutationBinding.Binding[DisplayMutationServiceDomain.CreateShape] =
              DisplayMutationBinding.admitC(create.selection.source,
                GeneratedLogicalUiAbi.admitC(session.component.generatedLogicalUiDefinitions).TAKE, screenId, "normal-update-1",
                create.capabilities, change(create._field_registrations), Set.empty,
                (value: DisplayMutationServiceDomain.CreateShape) => Consequence.success(value)).TAKE
            val wrongbinding = _metadata_(values => values.map(value => if (value.location.fieldId == "title") value.copy(bindingId = "different.title") else value))
            val wrongkind = _metadata_(values => values.map(value => if (value.location.fieldId == "nullable")
              value.copy(acceptedKinds = Vector(GeneratedLogicalUiAbi.ValueKind.Text)) else value))
            val wrongweb = _metadata_(values => values.map(value => if (value.location.fieldId == "title")
              value.copy(web = value.web.copy(placeholder = Some("different server hint"))) else value))
            val missingfield = _metadata_(_.filterNot(_.location.fieldId == "description"))
            When("the typed lifecycle factories admit each frozen malformed server registration before publishing an operation")
            val invalid = Vector(_register_(createbinding = null), _register_(seed = null), _register_(persistent = null),
              _register_(availability = null), _register_(seed = session.component.createSeed.copy(identity = Some(foreignid))),
              _register_(persistent = DisplayMutationServiceDomain.createPersistent(foreigncollection)),
              _register_(persistent = _persistent_("title")), _register_(persistent = _persistent_("updated_at")),
              _register_(createbinding = foreigntarget), _register_(createbinding = foreignfamily),
              _register_(createbinding = wrongbinding), _register_(createbinding = wrongkind),
              _register_(createbinding = wrongweb), _register_(createbinding = missingfield),
              DisplayMutationRuntime.entityLifecycleC(input, session.component.ownerName, null,
                DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
                DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), create, session.component.createSeed,
                canonical, (_: DisplayMutationServiceDomain.Root) => Consequence.unit, session.log.available))
            Then("every invalid registration is a structured argument admission failure and has no publication or writer fallback")
            invalid.foreach(value => conclusion(value).observation.taxonomy shouldBe Taxonomy.argumentInvalid)
            When("the Aggregate factory receives either a null fixed create command or a null update command")
            val aggregateinvalid = Vector(
              DisplayMutationRuntime.aggregateLifecycleC[DisplayMutationServiceDomain.Root, DisplayMutationServiceDomain.CreateShape, DisplayMutationServiceDomain.PatchShape](
                input, session.component.ownerName, "changeFields", DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
                DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), DisplayMutationServiceDomain.command,
                create, session.component.createSeed, canonical, "createRoot", null, (_: DisplayMutationServiceDomain.Root) => Consequence.unit, session.log.available),
              DisplayMutationRuntime.aggregateLifecycleC[DisplayMutationServiceDomain.Root, DisplayMutationServiceDomain.CreateShape, DisplayMutationServiceDomain.PatchShape](
                input, session.component.ownerName, "changeFields", DisplayMutationServiceDomain.fieldIds, DisplayMutationServiceDomain.seed,
                DisplayMutationServiceDomain.patchPersistent(session.component.collectionId), null,
                create, session.component.createSeed, canonical, "createRoot", Consequence.success,
                (_: DisplayMutationServiceDomain.Root) => Consequence.unit, session.log.available))
            Then("both null application command boundaries fail before domain or provider use")
            aggregateinvalid.foreach(value => conclusion(value).observation.taxonomy shouldBe Taxonomy.argumentInvalid)
          } finally session.close()
          for (removed <- Vector(Set(EntityMutationProviderFeature.OptimisticCompareAndDelete),
            Set(EntityMutationProviderFeature.AuthoritativeDeleteResult), Set(EntityMutationProviderFeature.AtomicSideEffects))) {
            Given(s"the selected real provider with only $removed removed and a physical-delete root retaining its owned body")
            val faulty = open(target, representation, backend = backend,
              lifecycle = Some(LifecycleSetup(logical = false, removed = removed)))
            try {
              faulty.prepareLifecycle(false, logical = false)
              val before = faulty.lifecycleSnapshot()
              When("the normal observed delete checks actual root capabilities and the canonical physical side-effect plan")
              val attempt = faulty.execute(faulty.lifecycleRequest(Operation.Delete))
              Then("unsupported capability preserves full state and never calls a generic writer or substitutes a provider")
              checkLifecyclePreserved(faulty, before, attempt)
            } finally faulty.close()
          }
        }
        for (registration <- Vector(Registration.NullProvider, Registration.ThrowProvider, Registration.Duplicate,
          Registration.ForeignComponent, Registration.ForeignResource, Registration.WrongScreen, Registration.DefinitionRevision,
          Registration.Producer, Registration.Source, Registration.ProvenanceRevision, Registration.ListBranch, Registration.UnregisteredBrowser,
          Registration.WrongOwner, Registration.WrongFamily, Registration.WrongCollection, Registration.Unmanaged,
          Registration.DuplicateStorage, Registration.ManagedStorage, Registration.AliasedTarget)) {
          Given(s"normal prepublication bootstrap with lifecycle registration mismatch $registration")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("lifecycle-static-admission")
          val component = new NormalComponent("invalidlifecycle", Target.Entity, representations.head, new Log,
            registration, Some(LifecycleSetup()))
          initialize(component, subsystem)
          try {
            When("the normal ComponentFactory admits the whole registered canonical read and mutation boundary")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the malformed registration fails closed before service publication and any converter domain or canonical writer")
            result shouldBe a[Consequence.Failure[?]]
            component.log.conversions shouldBe Vector.empty
            component.log.validations shouldBe 0
            component.log.commands shouldBe 0
          } finally Subsystem.shutdownOwned(subsystem)
        }
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"an actually bootstrapped mixed lifecycle/legacy registry for $target/$representation")
          val session = open(target, representation, backend = "sqlite", lifecycle = Some(LifecycleSetup(mixed = true)))
          try {
            for (operation <- Vector(Operation.Create, Operation.Delete)) {
              Given("the original complete root/body/native/resident/View baseline and a legacy selected screen")
              session.prepareLifecycle(false)
              val before = session.lifecycleSnapshot()
              val selection = session.component.legacyBinding.selection
              val request = if (operation == Operation.Create) DisplayMutation.Request.Create(selection, lifecycleFields)
                else DisplayMutation.Request.Delete(selection, session.component.id)
              val payload = DisplayMutationCodec.encodeRequestC(request).TAKE
              val raw = session.lifecycleRawRequest(operation, payload).copy(properties = session.lifecycleRawRequest(operation, payload).properties.map { property =>
                if (property.name == "screenId") property.copy(value = "legacy-detail") else property
              })
              When("the ordinary published lifecycle operation selects the legacy Entry in the mixed registry")
              val attempt = session.execute(raw)
              Then("legacy create/delete remains explicitly unsupported with complete state preservation")
              reasons(attempt.result) should contain("operation-unsupported")
              checkLifecyclePreserved(session, before, attempt)
            }
            Given("the same actual mixed registry and an ordinary update using the original legacy binding")
            session.prepareLifecycle(false)
            val before = session.rows()
            val changes = Vector(field("title", DisplayValue.Text("Legacy update remains compatible")))
            val wanted = session.expected(before, session.physical(), changes, true, 2L)
            val payload = DisplayMutationCodec.encodeRequestC(DisplayMutation.Request.Update(session.component.legacyBinding.selection, session.component.id, changes)).TAKE
            val raw = session.rawRequest(payload).copy(properties = session.rawRequest(payload).properties.map { property =>
              if (property.name == "screenId") property.copy(value = "legacy-detail") else property
            })
            When("the legacy update Entry delegates the unchanged typed update capture and completion")
            val attempt = session.execute(raw)
            Then("the old update result and refreshed legacy Detail remain Ready with exact complete state and no lifecycle operation substitution")
            json(attempt.result).hcursor.get[String]("outcome") shouldBe Right("updated")
            json(attempt.result).hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
            checkCompleteState(session, wanted)
          } finally session.close()
        }
      }

      "DLC-E5 retain Created pending and Deleted reload-list through real coherence or contextual projection failure without replay" in {
        for (target <- Target.values.toVector; representation <- representations;
          mode <- Vector("decode", "coherence", "failure", "wrong-id", "older", "newer", "row", "row-null", "row-null-success", "row-throw", "null", "null-success", "throw")) {
          Given(s"a normally assembled SQLite $target/$representation and a predeclared $mode optional-create failure oracle")
          val session = open(target, representation, backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            session.prepareLifecycle(true)
            val before = session.lifecycleSnapshot()
            val fault = if (mode == "decode") CaptureFault.Projection else if (mode == "coherence") CaptureFault.Coherence else CaptureFault.None
            val wanted = expectedLifecycleCreate(session, before, lifecycleFields, fault)
            session.log.captureFault = fault
            session.log.reload = mode match {
              case "failure" => Reload.Failure
              case "wrong-id" => Reload.WrongId
              case "older" => Reload.Older
              case "newer" => Reload.Newer
              case "row" => Reload.RowFailure
              case "row-null" => Reload.RowNull
              case "row-null-success" => Reload.RowNullSuccess
              case "row-throw" => Reload.RowThrow
              case "null" => Reload.NullConsequence
              case "null-success" => Reload.NullSuccess
              case "throw" => Reload.Throw
              case _ => Reload.Current
            }
            val expectedprojection = if (mode == "older") "ready" else "pending"
            val expectedreason = if (mode == "newer") "view-not-current" else "projection-unavailable"
            When("one normal create obtains the actual provider acknowledgement and the enclosing current commit succeeds")
            val attempt = session.execute(session.lifecycleRequest(Operation.Create))
            Then("Created and the durable acknowledgement survive with the existing pending reason, exact resident/View state and retryCommand false")
            // INITIAL cannot be represented by an older positive revision.
            checkLifecycleResult(attempt, session, true, 1L, expectedprojection)
            if (expectedprojection == "pending") json(attempt.result).hcursor.downField("projection").get[String]("reason") shouldBe Right(expectedreason)
            session.log.events.count(_.event == "find") shouldBe (if (fault != CaptureFault.None) 0 else 1)
            checkLifecycleState(session, wanted)
            Given("a freshly reset independently seeded root and an actual post-ACK eviction throw")
            session.prepareLifecycle(false)
            val deletebefore = session.lifecycleSnapshot()
            val deletewanted = expectedLifecycleDelete(session, deletebefore, 1L, true, CaptureFault.Coherence)
            session.log.captureFault = CaptureFault.Coherence
            When("normal observed deletion captures the real result then encounters the optional coherence fault")
            val deleted = session.execute(session.lifecycleRequest(Operation.Delete))
            Then("Deleted still requests reload-list, performs no Detail or replay and leaves the prescribed stale resident behind invalidated Views")
            checkLifecycleResult(deleted, session, false, 1L)
            session.log.events.count(_.event == "find") shouldBe 0
            checkLifecycleState(session, deletewanted)
          } finally session.close()
        }
      }

      "DLC-E6 require the current committed witness for create/delete despite actual ACKs or prior successful commit history" in {
        for (create <- Vector(false, true); mode <- Commit.values.toVector; historical <- Vector(false, true)) {
          Given(s"a real SQLite normal lifecycle command with create=$create, enclosing commit=$mode and historical success=$historical")
          val session = open(backend = "sqlite", lifecycle = Some(LifecycleSetup()))
          try {
            session.prepareLifecycle(create)
            val before = session.lifecycleSnapshot()
            val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields)
              else expectedLifecycleDelete(session, before, 1L, true)
            val active = session.execution(mode, historical = historical)
            val caller = active.context.unitOfWork
            val callertermination = caller.lastCommitTermination
            val callerresult = caller.lastCommitResult
            When("the ordinary ActionEngine captures one actual command and invokes its configured current enclosing commit")
            val attempt = session.execute(session.lifecycleRequest(if (create) Operation.Create else Operation.Delete), active)
            Then("only the actual successful current commit yields a receipt; every other branch retains independent durable effects without replay or invented rollback")
            if (mode == Commit.Normal) checkLifecycleResult(attempt, session, create, 1L)
            else {
              attempt.result shouldBe a[Consequence.Failure[?]]
              session.log.events.count(_.event == "find") shouldBe 0
              attempt.execution.lifecycleObservers.map(_.creates.size).sum shouldBe (if (create) 1 else 0)
              attempt.execution.lifecycleObservers.map(_.deletes.size).sum shouldBe (if (create) 0 else 1)
              attempt.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
              if (mode == Commit.Noop) {
                conclusion(attempt.result).observation.taxonomy shouldBe conclusion(DisplayMutationResult.indeterminateC[Unit]()).observation.taxonomy
                active.uow.lastCommitTermination shouldBe None
                active.uow.lastCommitResult shouldBe None
              }
              if (mode == Commit.Reject) active.uow.lastCommitTermination shouldBe Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Aborted)
              if (mode == Commit.Abort) active.uow.lastAbortResult shouldBe Some(Consequence.unit)
              if (mode == Commit.Cleanup) conclusion(attempt.result).observation.taxonomy shouldBe projectionFailure.conclusion.observation.taxonomy
            }
            And("the original caller retains its bound UoW and independently established historical commit state in every mode")
            checkActionOwnership(attempt)
            (active.callerUnitOfWork eq caller) shouldBe true
            (active.context.unitOfWork eq caller) shouldBe true
            caller.lastCommitTermination shouldBe callertermination
            caller.lastCommitResult shouldBe callerresult
            caller.lastCommitTermination shouldBe (if (historical) Some(org.goldenport.cncf.unitofwork.UnitOfWorkTermination.Committed) else None)
            caller.lastCommitResult shouldBe (if (historical) Some(Consequence.unit) else None)
            session.log.events.count(_.event == "capture") shouldBe 1
            session.log.events.count(_.event == "commit") shouldBe 1
            session.log.events.foreach(event => (event.uow eq active.uow) shouldBe true)
            checkLifecycleState(session, wanted)
          } finally session.close()
        }
      }
    }
  }
}
