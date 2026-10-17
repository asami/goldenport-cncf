package org.goldenport.cncf.display

import java.nio.file.Path
import logicalui.{DisplayMutationServiceDomain, DisplayServiceReadFixture, TypedDisplayProjectionFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.*
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.protocol.Property
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
final class DisplayMutationServiceUpdateSpec extends AnyWordSpec with DisplayMutationServiceFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  private val _title = Vector(field("title", DisplayValue.Text("Changed 日本 Ω")))

  "Ordinary Display update" should {
    "Detail, descriptor and canonical writers" which {
      "C3-E1 refresh Entity Detail after a real current commit on both representations and portable providers" in {
        for (representation <- representations; backend <- Vector("memory", "sqlite")) {
          Given(s"a normally bootstrapped $backend Entity with $representation, complete resident/View state and an independent oracle")
          val session = open(representation = representation, backend = backend)
          try {
            val before = session.rows()
            val tables = session.physical()
            val wanted = session.expected(before, tables, _title, true, 2L)
            When("Detail is read first and its inert mutation descriptor is read through ordinary Operations")
            val detail = session.read()
            val descriptor = session.descriptor()
            Then("the original read schema and typed descriptor identify the admitted component and complete definition")
            json(detail.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
            json(detail.result).hcursor.get[String]("componentId") shouldBe Right(session.component.name)
            DisplayMutationCodec.decodeDescriptorC(session.component.inputBinding, json(descriptor.result).noSpaces).toOption shouldBe defined
            Given("the original state oracle remains fixed before the update and prior read traces are cleared")
            session.log.reset()
            When("the selected update runs through the ordinary resolver and ActionEngine, followed by a fresh Detail")
            val attempt = session.execute(session.request(_title))
            val after = session.rows()
            val physical = session.physical()
            val events = session.log.events
            val refreshed = session.read()
            Then("one actual captured update and enclosing commit produce Ready and preserve the complete nonselected state")
            checkRows(after, wanted.rows)
            physical shouldBe wanted.physical
            session.log.events should not be empty
            checkCompleteState(session, wanted)
            json(attempt.result).hcursor.get[String]("outcome") shouldBe Right("updated")
            attempt.execution.attempts shouldBe 1
            events.map(_.event) shouldBe Vector("authorize", "availability", "authorize", "capture", "commit", "find")
            events.foreach(event => (event.uow eq attempt.execution.uow) shouldBe true)
            json(refreshed.result).hcursor.downField("model").downField("resource").get[String]("revision") shouldBe Right("2")
            session.component.collection.descriptor.persistent shouldBe session.component.probe
            session.component.entitySpace.entityOption(session.component.collectionId).get shouldBe session.component.collection
            DisplayMutationServiceDomain.privateConstructionDenied shouldBe true
            DisplayMutationServiceDomain.erasedWriterDenied shouldBe true
            DisplayMutationServiceDomain.captureConstructionDenied shouldBe true
          } finally session.close()
        }
      }

      "C3-E2 preserve complete Aggregate and alias authorization around the fixed domain command" in {
        for (target <- Vector(Target.Aggregate, Target.Alias); representation <- representations) {
          Given(s"a normally bootstrapped $target with $representation and a predeclared full-save oracle")
          val session = open(target, representation)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _title, true, 2L)
            val authorization = UnitOfWorkAuthorization("aggregate", Some(session.component.ownerName), Some(session.component.rootName),
              Some(session.component.id), "command:changeFields", Some(CmlOperationAccess("authenticated_only")),
              Some(session.component.name), Some(session.component.name), Vector(session.component.ownerName),
              EntityAccessMode.UserPermission, Some(ServiceOperationModel.BusinessService), Some(EntityOperationKind.Task), Some(EntityApplicationDomain.Business))
            When("Detail and descriptor precede the ordinary Aggregate update")
            val first = session.read()
            val descriptor = session.descriptor()
            session.log.reset()
            val attempt = session.execute(session.request(_title))
            val after = session.rows()
            Then("the fixed command runs once after full authorization and exactly one real current commit reloads its registered Browser")
            json(first.result).hcursor.get[String]("componentId") shouldBe Right(session.component.name)
            json(descriptor.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-mutation-descriptor.v1")
            checkResult(attempt, session, "updated", 2L)
            checkRows(after, wanted.rows)
            session.log.commands shouldBe 1
            checkCompleteState(session, wanted)
            session.log.authorizations.map(_._1) shouldBe Vector(authorization, authorization)
            session.log.events.map(_.event) shouldBe Vector("authorize", "aggregate", "availability", "authorize", "capture", "commit", "find")
            Given("the same exact owner without authentication and the current revision after the successful write")
            val preserved = session.rows()
            val tables = session.physical()
            val resident = session.resident
            val anonymous = session.execution(principal = "owner", authenticated = false)
            val authentication = conclusion(Consequence.securityAuthenticationRequired[Unit](
              s"Authenticated user is required: ${session.component.name}.display.update"))
            session.log.reset()
            When("the owner submits another ordinary Aggregate update with the current observation")
            val rejected = session.execute(session.request(_title, 2L), anonymous)
            Then("authentication rejection precedes conversion, domain execution, provider attempts and contextual reads")
            rejected.result shouldBe a[Consequence.Failure[?]]
            val failure = conclusion(rejected.result)
            failure.observation.taxonomy shouldBe authentication.observation.taxonomy
            failure.status shouldBe authentication.status
            failure.displayMessage shouldBe authentication.displayMessage
            rejected.execution.attempts shouldBe 0
            session.log.authorizations shouldBe Vector.empty
            session.log.events.count(_.event == "aggregate") shouldBe 0
            checkPreserved(session, preserved, tables, resident)
          } finally session.close()
        }
      }

      "C3-E3 preserve current and maximum no-op audit while rejecting stale equal and changed input before conversion" in {
        for (target <- Target.values.toVector; representation <- representations) {
          Given(s"a canonical $target/$representation at revision one with fixed audit and unchanged title")
          val session = open(target, representation)
          try {
            val unchanged = Vector(field("title", DisplayValue.Text("Before")))
            val before = session.rows()
            val tables = session.physical()
            When("the current equal update is committed and contextually reloaded")
            val current = session.execute(session.request(unchanged))
            val after = session.rows()
            Then("the actual NoOp preserves complete revision and audit state")
            checkResult(current, session, "unchanged", 1L)
            checkRows(after, before)
            session.physical() shouldBe tables
            for (changes <- Vector(unchanged, _title)) {
              Given("a fresh empty invocation trace and an independently stale observation")
              session.log.reset()
              val resident = session.resident
              When("a stale request reaches the normal canonical observation gate")
              val stale = session.execute(session.request(changes, 2L))
              Then("the full structured conflict precedes every converter, domain command and writer")
              stale.result shouldBe a[Consequence.Failure[?]]
              conclusion(stale.result).observation.taxonomy.symptom shouldBe org.goldenport.observation.Taxonomy.Symptom.Conflict
              stale.execution.attempts shouldBe 0
              checkPreserved(session, before, tables, resident)
            }
            Given("the genuine canonical provider has a prearranged maximum positive managed revision")
            session.seed(initialRevision = Long.MaxValue)
            val maximum = session.rows()
            When("a current equal update at the maximum revision reaches the same normal operation")
            val noop = session.execute(session.request(unchanged, Long.MaxValue))
            val finalrows = session.rows()
            Then("no successor is needed and the exact maximum receipt and audit survive")
            checkResult(noop, session, "unchanged", Long.MaxValue)
            checkRows(finalrows, maximum)
          } finally session.close()
        }
      }
    }

    "rejection and admitted native fields" which {
      "C3-E4 preserve complete state for factory, canonical access, operation, domain and transition denial" in {
        for (target <- Target.values.toVector; mode <- Vector("factory", "canonical-access", "operation", "domain", "transition")) {
          Given(s"a $target and $mode denial: canonical Entity access requires its owner, while Aggregate and alias access requires authentication")
          val session = open(target)
          try {
            if (mode == "factory") session.log.denyFactory = true
            if (mode == "transition") session.log.rejectTransition = true
            if (mode == "operation") session.subsystem.withDescriptor(GenericSubsystemDescriptor(Path.of("<normal-update>"), session.subsystem.name,
              operationAuthorization = Map((session.component.name + ".display.update") -> OperationAuthorizationRule(deny = true))))
            val before = session.rows()
            val tables = session.physical()
            val resident = session.resident
            val changes = if (mode == "domain") Vector(field("title", DisplayValue.Text("  "))) else _title
            val active = session.execution(
              principal = if (mode == "canonical-access" && target == Target.Entity) "foreign-principal" else "owner",
              authenticated = mode != "canonical-access" || target == Target.Entity)
            When("the ordinary bound command applies its authoritative denial boundary")
            val attempt = session.execute(session.request(changes), active)
            val after = session.rows()
            Then("the original structured denial has no provider publication or contextual reload and retains selected and unrelated state")
            attempt.result shouldBe a[Consequence.Failure[?]]
            if (mode == "factory") (conclusion(attempt.result) eq denied.conclusion) shouldBe true
            if (mode == "domain") (conclusion(attempt.result) eq domainFailure.conclusion) shouldBe true
            if (mode == "transition") (conclusion(attempt.result) eq projectionFailure.conclusion) shouldBe true
            checkRows(after, before)
            session.physical() shouldBe tables
            session.resident shouldBe resident
            attempt.execution.attempts shouldBe 0
            session.log.events.count(_.event == "find") shouldBe 0
            if (Set("factory", "canonical-access", "operation").contains(mode)) session.log.conversions shouldBe Vector.empty
          } finally session.close()
        }
      }

      "C3-E5 retain native values and Missing clear with declaration-ordered callbacks and reject inadmissible selected fields" in {
        for (target <- Target.values.toVector) {
          Given(s"a $target with six explicit typed mappings and five native Display kinds")
          val session = open(target, backend = "sqlite")
          try {
            val changes = Vector(field("nullable", DisplayValue.Missing), field("instant", DisplayValue.Timestamp(java.time.Instant.parse("2026-10-15T02:03:04.987654321Z"))),
              field("flag", DisplayValue.Boolean(false)), field("number", DisplayValue.Number(BigDecimal("123456789.0012300"))),
              field("description", DisplayValue.Text("")), field("title", DisplayValue.Text("  日本 Ω\ud800  ")))
            val before = session.rows()
            val tables = session.physical()
            val wanted = session.expected(before, tables, changes, true, 2L)
            When("the reverse-ordered native patch is admitted, committed and reloaded")
            val attempt = session.execute(session.request(changes))
            val after = session.rows()
            val physical = session.physical()
            Then("exact lossless storage codecs, logical SQL NULL omission and predeclared physical NULL retain every unrelated field")
            checkResult(attempt, session, "updated", 2L)
            checkRows(after, wanted.rows)
            physical shouldBe wanted.physical
            after.selected.get.getAny("nullable") shouldBe None
            checkCompleteState(session, wanted)
            session.log.conversions shouldBe DisplayMutationServiceDomain.fieldIds
            session.log.validations shouldBe 1
            session.log.commands shouldBe (if (target == Target.Entity) 0 else 1)
            val invalid = Vector(
              Vector(field("unknown", DisplayValue.Text("private input"))), Vector(field("stage", DisplayValue.Text("private input"))),
              Vector(field("title", DisplayValue.Boolean(true))), Vector(_title.head, _title.head))
            invalid.foreach { selected =>
              Given("the committed independent baseline and a malformed selected field vector")
              session.log.reset()
              val resident = session.resident
              When("the same ordinary operation rejects selected-field admission")
              val request = if (selected.map(_.location).distinct.size != selected.size)
                session.rawRequest(session.duplicateFieldPayload(selected.head), 2L) else session.request(selected, 2L)
              val rejected = session.execute(request)
              Then("no callback or writer runs and all complete state is retained with a redacted structured failure")
              rejected.result shouldBe a[Consequence.Failure[?]]
              conclusion(rejected.result).show should not include "private input"
              rejected.execution.attempts shouldBe 0
              checkPreserved(session, after, physical, resident)
            }
            Given("a previously issued descriptor and a current readonly veto for title")
            session.log.availability = Consequence.success(DisplayMutation.Availability.enabled.copy(readonlyFields = Set(DisplayMutation.FieldLocator("main", "title"))))
            session.log.reset()
            When("an update uses the old descriptor after current availability has changed")
            val readonly = session.execute(session.request(_title, 2L))
            Then("descriptor metadata grants no permission to bypass the current readonly veto")
            readonly.result shouldBe a[Consequence.Failure[?]]
            session.log.conversions shouldBe Vector.empty
            readonly.execution.attempts shouldBe 0
          } finally session.close()
        }
      }

      "C3-E5 preserve explicit aliases and reject aliased double-selection and unselected seed assignments before writing" in {
        for (target <- Target.values.toVector) {
          Given(s"a $target whose description editor explicitly targets the canonical title field")
          val session = open(target, registration = Registration.AliasedTarget)
          try {
            val alias = Vector(field("description", DisplayValue.Text("Alias title")))
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), Vector(field("title", DisplayValue.Text("Alias title"))), true, 2L)
            When("the ordinary operation converts the admitted alias exactly once")
            val attempt = session.execute(session.request(alias))
            Then("only its explicit canonical title target changes and the descriptor label does not infer another writer")
            checkResult(attempt, session, "updated", 2L)
            checkCompleteState(session, wanted)
            session.log.conversions shouldBe Vector("description")
            Given("the committed independent state and both editors selecting the same admitted canonical target")
            val current = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.reset()
            When("a request selects title and description together")
            val rejected = session.execute(session.request(Vector(_title.head, alias.head), 2L))
            Then("the aliased double-selection fails before any converter, writer or read")
            rejected.result shouldBe a[Consequence.Failure[?]]
            rejected.execution.attempts shouldBe 0
            checkPreserved(session, current, tables, resident)
          } finally session.close()
          Given(s"a $target with an unselected non-noop description in its retained typed seed")
          val seeded = open(target, registration = Registration.UnselectedSeed)
          try {
            val before = seeded.rows(); val tables = seeded.physical(); val resident = seeded.resident
            When("ordinary title preparation produces a patch containing the unselected seeded assignment")
            val rejected = seeded.execute(seeded.request(_title))
            Then("the canonical patch scope rejects before its provider and retains all complete state")
            rejected.result shouldBe a[Consequence.Failure[?]]
            conclusion(rejected.result).show should not include "private seed"
            rejected.execution.attempts shouldBe 0
            checkRows(seeded.rows(), before)
            seeded.physical() shouldBe tables
            seeded.resident shouldBe resident
            seeded.log.events.count(_.event == "find") shouldBe 0
          } finally seeded.close()
        }
      }

      "C3-E5 preserve availability Conclusions and contain null or NonFatal descriptor/update callbacks without granting authority" in {
        for (mode <- Vector(Reload.Failure, Reload.NullConsequence, Reload.NullSuccess, Reload.Throw); descriptor <- Vector(false, true)) {
          Given(s"a normal component whose current availability callback has $mode behavior for descriptor=$descriptor")
          val session = open()
          try {
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            session.log.availabilityMode = mode
            When("the ordinary descriptor or command re-evaluates current availability under its actual context")
            val attempt = session.execute(if (descriptor) session.descriptorRequest else session.request(_title))
            Then("the real structured Failure survives or a redacted callback-invalid Failure stops every downstream callback and write")
            attempt.result shouldBe a[Consequence.Failure[?]]
            if (mode == Reload.Failure) (conclusion(attempt.result) eq projectionFailure.conclusion) shouldBe true
            conclusion(attempt.result).show should not include "private availability credential"
            attempt.execution.attempts shouldBe 0
            session.log.events.count(_.event == "availability") shouldBe 1
            checkPreserved(session, before, tables, resident)
          } finally session.close()
        }
      }

      "C3-E5 distinguish current disabled and hidden availability from an earlier descriptor without granting mutation authority" in {
        for (state <- Vector(DisplayMutation.AvailabilityState.Disabled, DisplayMutation.AvailabilityState.Hidden)) {
          Given(s"a normal component with an earlier enabled descriptor and current $state update availability")
          val session = open()
          try {
            val previous = session.descriptor()
            session.log.availability = Consequence.success(DisplayMutation.Availability.enabled.copy(update = state))
            val before = session.rows(); val tables = session.physical(); val resident = session.resident
            When("the ordinary descriptor reports current availability and an update still attempts normal admission")
            val descriptor = session.descriptor()
            session.log.reset()
            val attempt = session.execute(session.request(_title))
            Then("the metadata is inert and current unavailability rejects before converters, writer or Browser")
            json(previous.result).hcursor.downField("availability").get[String]("update") shouldBe Right("enabled")
            json(descriptor.result).hcursor.downField("availability").get[String]("update") shouldBe Right(if (state == DisplayMutation.AvailabilityState.Disabled) "disabled" else "hidden")
            attempt.result shouldBe a[Consequence.Failure[?]]
            conclusion(attempt.result).observation.taxonomy shouldBe conclusion(DisplayMutation.operationUnavailableC[Unit](DisplayMutation.Operation.Update)).observation.taxonomy
            attempt.execution.attempts shouldBe 0
            checkPreserved(session, before, tables, resident)
          } finally session.close()
        }
      }
    }

    "committed read correspondence" which {
      "C3-E6 retain actual committed receipts for every reachable Browser, row, projection and coherence failure without replay" in {
        for (target <- Vector(Target.Entity, Target.Aggregate); reload <- Reload.values.toVector) {
          Given(s"a normal $target with a canonical provider and reachable selected $reload Browser/row mode")
          val session = open(target)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _title, true, 2L)
            session.log.reload = reload
            When("one canonical mutation and actual same-UnitOfWork commit attempt the selected contextual read exactly once")
            val attempt = session.execute(session.request(_title))
            val after = session.rows()
            Then("Ready, lag or unavailable retain the exact authoritative outcome and never run a second writer or command")
            val reason = if (reload == Reload.Older || reload == Reload.Newer) "view-not-current" else "projection-unavailable"
            checkResult(attempt, session, "updated", 2L, if (reload == Reload.Current) "ready" else "pending",
              if (reload == Reload.Current) None else Some(reason))
            checkRows(after, wanted.rows)
            session.log.events.count(_.event == "find") shouldBe 1
            checkCompleteState(session, wanted)
            session.log.validations shouldBe 1
            session.log.commands shouldBe (if (target == Target.Entity) 0 else 1)
          } finally session.close()
        }
        for (target <- Vector(Target.Entity, Target.Aggregate); fault <- Vector(CaptureFault.Projection, CaptureFault.Coherence)) {
          Given(s"a real $target capture with a delegated post-ACK $fault fault")
          val session = open(target)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _title, true, 2L, captureFault = fault)
            session.log.captureFault = fault
            When("the acknowledged canonical mutation is retained through the actual enclosing commit")
            val attempt = session.execute(session.request(_title))
            val after = session.rows()
            Then("capture failure short-circuits before Browser invocation while retaining the committed receipt and complete physical state")
            checkResult(attempt, session, "updated", 2L, "pending", Some("projection-unavailable"))
            checkRows(after, wanted.rows)
            session.log.events.count(_.event == "find") shouldBe 0
            checkCompleteState(session, wanted)
          } finally session.close()
        }
      }

      "C3-E7 reject every typed registration mismatch before ordinary service publication and retain original no-provider reads" in {
        Registration.values.toVector.filterNot(value => Set(Registration.Normal, Registration.Empty, Registration.AliasedTarget, Registration.UnselectedSeed).contains(value)).foreach { mode =>
          Given(s"an ordinary component with $mode typed registration and no published Display service")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("normal-registration")
          val component = new NormalComponent("registrationroot", Target.Entity, EntityRevisionRepresentation.Embedded, new Log, mode)
          initialize(component, subsystem)
          try {
            When("ordinary ComponentFactory bootstrap admits the complete read and mutation vector")
            val result = new ComponentFactory().bootstrapC(component)
            Then("a structured redacted failure exposes neither a partial route nor a domain or writer invocation")
            result shouldBe a[Consequence.Failure[?]]
            conclusion(result).show should not include "private provider credential"
            component.protocol.services.services.exists(_.name == "display") shouldBe false
            component.displayRuntime shouldBe None
            component.collectionsBootstrapped shouldBe false
            component.log.conversions shouldBe Vector.empty
            component.log.commands shouldBe 0
          } finally Subsystem.shutdownOwned(subsystem)
        }
        Given("the original actual-generated read-only component with no mutation provider")
        val row = TypedDisplayProjectionFixture.captureC(1, 7L).TAKE
        val original = DisplayServiceReadFixture.open(Vector(row))
        try {
          When("ordinary read and unknown update requests use the original service")
          val read = original.execute(original.request("read", screenId, Some(row.resource.entityId)))
          val absent = original.execute(original.request("update", screenId))
          Then("original read bytes and query/read defaults survive while no update route is advertised")
          json(read).hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
          absent shouldBe a[Consequence.Failure[?]]
          original.component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
        } finally original.close()
      }

      "C3-E7 select component providers before factory fallback and preserve an empty selected provider's query/read service" in {
        for (componentprovider <- Vector(false, true)) {
          Given(s"ordinary bootstrap with component-provider-present=$componentprovider and an observed empty factory fallback")
          val subsystem = TestComponentFactory.admittedEmptySubsystem("mutation-provider-precedence")
          var reads = 0
          val fallback = new Component.Factory with DisplayMutationServiceBindingProvider {
            protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
            protected def create_Component(params: ComponentCreate): Component = new Component() {}
            def displayMutationBindings(component: Component): Vector[DisplayMutationRuntime.Binding] = { reads += 1; Vector.empty }
          }
          val component: Component = if (componentprovider)
            new NormalComponent("providerprecedence", Target.Entity, EntityRevisionRepresentation.Embedded, new Log, Registration.NullProvider)
          else new DisplayServiceReadFixture.ReadComponent(Vector(TypedDisplayProjectionFixture.captureC(1, 7L).TAKE))
          DisplayServiceReadFixture.initialize(component, subsystem, "ProviderPrecedence", org.goldenport.protocol.Protocol(handler = org.goldenport.protocol.handler.ProtocolHandler.default), Some(fallback))
          try {
            When("ordinary ComponentFactory performs the complete optional mutation-provider join")
            val result = new ComponentFactory().bootstrapC(component)
            Then("only the selected getter is consulted and an invalid component provider cannot fall back")
            if (componentprovider) {
              result shouldBe a[Consequence.Failure[?]]
              reads shouldBe 0
              component.protocol.services.services.exists(_.name == "display") shouldBe false
            } else {
              result.toOption shouldBe Some(component)
              reads shouldBe 1
              component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
            }
          } finally Subsystem.shutdownOwned(subsystem)
        }
        Given("a component-selected empty vector after normal canonical Entity and Browser admission")
        val empty = open(registration = Registration.Empty)
        try {
          When("the original ordinary read executes and descriptor/update lookup is attempted")
          val read = empty.read()
          val descriptor = empty.descriptor()
          Then("the exact original query/read service remains available without new mutation operations")
          json(read.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
          descriptor.result shouldBe a[Consequence.Failure[?]]
          empty.component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
        } finally empty.close()
      }

      "C3-E8 isolate two ordinarily bootstrapped routes, canonical stores, Browsers and definition provenance" in {
        Given("two installed components with identical screen identifiers, explicit canonical IDs and distinct complete domain state")
        val first = open(Target.Entity, backend = "sqlite")
        val second = try open(Target.Alias, sharedSession = Some(first), backend = "sqlite")
          catch { case scala.util.control.NonFatal(error) => first.close(); throw error }
        try {
          second.seed("Second canonical title")
          val initialfirst = first.rows()
          val initialsecond = second.rows()
          val initialphysical = first.physical()
          val initialsecondresident = second.resident
          val firstexpected = first.expected(initialfirst, initialphysical, _title, true, 2L)
          val secondexpected = second.expected(initialsecond, firstexpected.physical, _title, true, 2L)
          val secondafterfirstexpected = Expected(initialsecond, firstexpected.physical, initialsecondresident)
          val firstaftersecondexpected = Expected(firstexpected.rows, secondexpected.physical, firstexpected.resident)
          When("component A performs Detail, descriptor and its selected update")
          first.read(); first.descriptor(); first.log.reset()
          val firstattempt = first.execute(first.request(_title))
          val firstafter = first.rows()
          val secondafter = second.rows()
          Then("A alone writes and reads its registered context and admitted source while B's complete state is unchanged")
          checkResult(firstattempt, first, "updated", 2L)
          checkRows(firstafter, firstexpected.rows)
          checkCompleteState(first, firstexpected)
          checkRows(secondafter, initialsecond)
          checkCompleteState(second, secondafterfirstexpected)
          second.log.commands shouldBe 0
          json(firstattempt.result).hcursor.get[String]("componentId") shouldBe Right(first.component.name)
          Given("B's independent state and A's now committed state before B's valid route")
          second.log.reset(); first.log.reset()
          When("B performs its own Detail, descriptor and selected alias update")
          second.read(); second.descriptor(); second.log.reset()
          val secondattempt = second.execute(second.request(_title))
          val finalfirst = first.rows()
          val finalsecond = second.rows()
          Then("B alone invokes its command and Browser and retains A's exact committed state")
          checkResult(secondattempt, second, "updated", 2L)
          checkRows(finalfirst, firstafter)
          checkRows(finalsecond, secondexpected.rows)
          checkCompleteState(second, secondexpected)
          checkCompleteState(first, firstaftersecondexpected)
          first.log.events shouldBe Vector.empty
          json(secondattempt.result).hcursor.get[String]("componentId") shouldBe Right(second.component.name)
          Vector("resourceType" -> "foreign-resource", "revision" -> "foreign-definition", "sourceId" -> "foreign-source").foreach { case (name, value) =>
            Given("a foreign source/definition/provenance candidate against both predeclared complete states after the two successful updates")
            val raw = first.payload(_title).replace("\"" + name + "\":\"" + (name match {
              case "resourceType" => resourceType
              case "revision" => first.component.inputBinding.selection.definition.definitionRevision
              case _ => first.component.inputBinding.selection.definition.provenance.sourceId
            }) + "\"", "\"" + name + "\":\"" + value + "\"")
            val request = first.request(_title, 2L).copy(properties = first.request(_title, 2L).properties.map(property =>
              if (property.name == "payload") Property("payload", raw, None) else property))
            first.log.reset(); second.log.reset()
            When("the foreign payload is sent to A's normally resolved route")
            val rejected = first.execute(request)
            Then("request admission rejects before any conversion, writer or Browser and preserves both complete states")
            rejected.result shouldBe a[Consequence.Failure[?]]
            first.log.conversions shouldBe Vector.empty
            first.log.events.count(_.event == "find") shouldBe 0
            rejected.execution.attempts shouldBe 0
            checkRows(first.rows(), finalfirst)
            checkRows(second.rows(), finalsecond)
            checkCompleteState(first, firstaftersecondexpected)
            checkCompleteState(second, secondexpected)
          }
          Given("both predeclared complete successful states and B's actually admitted source/provenance with A's valid canonical target, so foreign target identity cannot mask the selection boundary")
          val foreign = second.payload(_title).replace(second.component.id.value, first.component.id.value)
          val request = first.request(_title, 2L).copy(properties = first.request(_title, 2L).properties.map(property =>
            if (property.name == "payload") Property("payload", foreign, None) else property))
          first.log.reset(); second.log.reset()
          When("B's source-bearing candidate is sent to the ordinary A route")
          val rejected = first.execute(request)
          Then("the complete admitted provenance mismatch rejects before domain, provider and contextual read with both actual stores preserved")
          rejected.result shouldBe a[Consequence.Failure[?]]
          rejected.execution.attempts shouldBe 0
          first.log.conversions shouldBe Vector.empty
          first.log.events.count(_.event == "find") shouldBe 0
          second.log.events shouldBe Vector.empty
          checkRows(first.rows(), finalfirst)
          checkRows(second.rows(), finalsecond)
          checkCompleteState(first, firstaftersecondexpected)
          checkCompleteState(second, secondexpected)
        } finally {
          try second.close()
          finally first.close()
        }
      }
    }
  }
}
