package org.goldenport.cncf.display

import logicalui.{DisplayMutationContractFixture, TypedDisplayProjectionFixture}
import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationResultSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationResult.*
  import DisplayMutationContractFixture.*
  private def _problem[A](result: Consequence[A]): Diagnostic = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.getException.collect {
      case carrier: MutationException => carrier.diagnostic
    }.getOrElse(fail("expected typed redacted result failure"))
    case _ => fail("expected result rejection")
  }
  private def _receipt(): Receipt = {
    val binding = success(bindingC())
    success(receiptC(binding.selection, update(binding, Vector(FieldInput(title, DisplayValue.Text("changed")))),
      OutcomeKind.Updated, resource(revision = 8), Some(resource().revision)))
  }
  "E1 R1 canonical supplied receipts" should afterWord("in spec:display-mutation, example:E1, rules:R2,R4, phase:96") {
    "admit each outcome only with its exact requested identity and canonical revision relation" in {
      Given("docs/spec/display-mutation.md R2,R4 E1: explicit positive-contract requests and authoritative supplied resources")
      val binding = success(bindingC(positive = true))
      val update = DisplayMutationContractFixture.update(binding, Vector(FieldInput(title, DisplayValue.Text("changed"))))
      val create = Request.Create(binding.selection, Vector.empty)
      val delete = Request.Delete(binding.selection, resource().entityId)
      val equal = update.copy(selection = binding.selection.copy(source = success(sourceC())))
      When("Created Updated Unchanged and Deleted supplied receipts are admitted")
      val created = success(receiptC(binding.selection, create, OutcomeKind.Created, resource(revision = 1), None))
      val updated = success(receiptC(binding.selection, equal, OutcomeKind.Updated, resource(revision = 8), Some(resource().revision)))
      val unchanged = success(receiptC(binding.selection, update, OutcomeKind.Unchanged, resource(), Some(resource().revision)))
      val deleted = success(receiptC(binding.selection, delete, OutcomeKind.Deleted, resource(), Some(resource().revision)))
      Then("create starts INITIAL1 update verifies nextC unchanged and deleted retain the observed source revision")
      created.resource.revision.value shouldBe 1L
      updated.resource.revision shouldBe success(resource().revision.nextC)
      unchanged.resource.revision shouldBe resource().revision
      deleted.resource.revision shouldBe resource().revision
      updated.selection shouldBe binding.selection
      Given("docs/spec/display-mutation.md R4 E1: wrong kind ID revision selection and null receipt candidates")
      val foreign = update.copy(selection = binding.selection.copy(source = success(sourceC("org.goldenport.fixture.Foreign"))))
      val candidates = Vector(
        (create: Request, OutcomeKind.Updated, resource(revision = 1), None),
        (create: Request, OutcomeKind.Created, resource(revision = 2), None),
        (create: Request, OutcomeKind.Created, resource(revision = 1), Some(resource().revision)),
        (update: Request, OutcomeKind.Updated, resource(index = 2, revision = 8), Some(resource().revision)),
        (update: Request, OutcomeKind.Updated, resource(revision = 9), Some(resource().revision)),
        (update: Request, OutcomeKind.Unchanged, resource(revision = 8), Some(resource().revision)),
        (delete: Request, OutcomeKind.Deleted, resource(revision = 8), Some(resource().revision)),
        (delete: Request, OutcomeKind.Created, resource(), Some(resource().revision)),
        (foreign: Request, OutcomeKind.Updated, resource(revision = 8), Some(resource().revision)),
        (null: Request, OutcomeKind.Updated, resource(revision = 8), Some(resource().revision)),
        (update: Request, null: OutcomeKind, resource(revision = 8), Some(resource().revision)),
        (update: Request, OutcomeKind.Updated, null: DisplayModel.Resource, Some(resource().revision)),
        (update: Request, OutcomeKind.Updated, resource(revision = 8), null: Option[org.simplemodeling.model.datatype.EntityRevision]))
      When("invalid supplied representations are admitted")
      val invalid = candidates.map { case (request, kind, committed, observed) =>
        receiptC(binding.selection, request, kind, committed, observed)
      }
      Then("none can certify a Receipt or substitute a framework empty marker")
      invalid.foreach(result => result.isSuccess shouldBe false)
    }
  }
  "E2 R2 direct Ready candidate admission" should afterWord("in spec:display-mutation, example:E2, rules:R2,R4, phase:96") {
    "require source-bearing exact Detail correspondence and reject invalid candidates structurally" in {
      Given("docs/spec/display-mutation.md R2,R4 E2: an updated receipt and separately allocated equal logical source")
      val receipt = _receipt()
      val snapshot = reload(sourceValue = success(sourceC()))
      When("the exact source-bearing Ready candidate is admitted")
      val ready = success(createC(receipt, Some(ProjectionState.Ready(snapshot))))
      Then("exact correspondence yields Ready with no command replay or reload requirement")
      ready.projection shouldBe Some(ProjectionState.Ready(snapshot))
      ready.retryCommand shouldBe false
      ready.reloadRequired shouldBe false
      val sources = Vector(success(sourceC("org.goldenport.fixture.Foreign")) -> "componentId", success(sourceC(resourceType = "Foreign")) -> "resourceType")
      sources.foreach { case (foreign, member) =>
        Given("docs/spec/display-mutation.md R2 E2: identical definition ID revision but foreign logical source")
        When("a foreign source label is presented as direct Ready")
        val invalid = createC(receipt, Some(ProjectionState.Ready(snapshot.copy(source = foreign))))
        Then("foreign source rejects before any revision-lag interpretation")
        _problem(invalid).code shouldBe "DSP96_MUTATION_SELECTION_MISMATCH"
        _problem(invalid).path shouldBe "projection.snapshot.source." + member
      }
      val nulls = Vector((null: ReloadedDisplay) -> "projection.snapshot",
        snapshot.copy(source = null) -> "projection.snapshot.source", snapshot.copy(display = null) -> "projection.snapshot.display")
      nulls.foreach { case (candidate, path) =>
        Given("docs/spec/display-mutation.md R4 E2: a null snapshot source or display candidate")
        When("the null-bearing candidate is admitted")
        val invalid = createC(receipt, Some(ProjectionState.Ready(candidate)))
        Then("null input has its exact static ELEMENT_INVALID path")
        _problem(invalid).code shouldBe "DSP96_MUTATION_ELEMENT_INVALID"
        _problem(invalid).path shouldBe path
      }
      Given("docs/spec/display-mutation.md R4 E2: wrong ID old/new revision and changed definition candidates")
      val changeddefinitions = success(GeneratedLogicalUiAbi.admitC(definitions.definitions.map(definition =>
        definition.copy(definitionRevision = "changed-definition"))))
      val changeddisplay = success(success(TypedDisplayProjectionFixture.detailC(changeddefinitions))
        .projectC(success(TypedDisplayProjectionFixture.captureC(1, 8))))
      val listdisplay = success(success(TypedDisplayProjectionFixture.listC(definitions))
        .projectC(Vector(success(TypedDisplayProjectionFixture.captureC(1, 8))), None))
      When("each noncorresponding Detail or List candidate is admitted")
      val invalid = Vector(reload(revision = 7), reload(revision = 9), reload(index = 2),
        snapshot.copy(display = changeddisplay), snapshot.copy(display = listdisplay))
        .map(candidate => createC(receipt, Some(ProjectionState.Ready(candidate))))
      Then("all wrong definition shape identity or revision representations reject")
      invalid.foreach(result => result.isSuccess shouldBe false)
      Given("docs/spec/display-mutation.md R4 E2: a Deleted receipt carries no Display or fresh tombstone")
      val binding = success(bindingC(positive = true))
      val deletedreceipt = success(receiptC(binding.selection, Request.Delete(binding.selection, resource().entityId),
        OutcomeKind.Deleted, resource(), Some(resource().revision)))
      When("deleted no-projection and prohibited ready-projection candidates are admitted")
      val deleted = success(createC(deletedreceipt, None))
      val withdisplay = createC(deletedreceipt, Some(ProjectionState.Ready(snapshot)))
      Then("deletion requires list reload and never contains a Display")
      deleted.projection shouldBe None
      deleted.reloadRequired shouldBe true
      deleted.retryCommand shouldBe false
      withdisplay.isSuccess shouldBe false
    }
  }
  "E3 R3 one-shot postcommit reload" should afterWord("in spec:display-mutation, example:E3, rules:R2,R4, phase:96") {
    "retain the committed receipt and classify lag versus unavailable without replay" in {
      val callbacks: Vector[(() => Consequence[ReloadedDisplay], Option[PendingReason])] = Vector(
        (() => Consequence.success(reload()), None),
        (() => Consequence.success(reload(revision = 7)), Some(PendingReason.ViewNotCurrent)),
        (() => Consequence.success(reload(revision = 9)), Some(PendingReason.ViewNotCurrent)),
        (() => Consequence.success(reload(sourceValue = success(sourceC("org.goldenport.fixture.Foreign")))), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.success(reload(sourceValue = success(sourceC(resourceType = "Foreign")))), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.success(reload(index = 2)), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.success(null: ReloadedDisplay), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.success(reload().copy(source = null)), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.success(reload().copy(display = null)), Some(PendingReason.ProjectionUnavailable)),
        (() => Consequence.argumentMissing("fixture reload"), Some(PendingReason.ProjectionUnavailable)),
        (() => throw new IllegalStateException("SECRET reload"), Some(PendingReason.ProjectionUnavailable)),
        (() => null, Some(PendingReason.ProjectionUnavailable)))
      callbacks.foreach { case (callback, expected) =>
        Given("docs/spec/display-mutation.md R2,R4 E3: supplied committed receipt and one pure reload outcome")
        val receipt = _receipt()
        var calls = 0
        When("the postcommit result is completed once")
        val result = success(completeC(receipt, () => { calls += 1; callback() }))
        Then("one reload preserves the exact receipt and reports ready lag or unavailable without retry")
        calls shouldBe 1
        (result.receipt eq receipt) shouldBe true
        result.retryCommand shouldBe false
        expected match {
          case Some(reason) => result.projection shouldBe Some(ProjectionState.Pending(reason)); result.reloadRequired shouldBe true
          case None => result.projection.exists { case ProjectionState.Ready(_) => true; case _ => false } shouldBe true
        }
      }
      Given("docs/spec/display-mutation.md R4 E3: changed definition and List reload shape")
      val receipt = _receipt()
      val changed = success(GeneratedLogicalUiAbi.admitC(definitions.definitions.map(_.copy(definitionRevision = "changed"))))
      val changeddisplay = success(success(TypedDisplayProjectionFixture.detailC(changed)).projectC(success(TypedDisplayProjectionFixture.captureC(1, 8))))
      val list = success(success(TypedDisplayProjectionFixture.listC(definitions)).projectC(Vector.empty, None))
      When("wrong definition and shape are completed after commit")
      val unavailable = Vector(changeddisplay, list).map(display => success(completeC(receipt, () => Consequence.success(ReloadedDisplay(source, display)))))
      Then("both keep the receipt with ProjectionUnavailable")
      unavailable.foreach(result => result.projection shouldBe Some(ProjectionState.Pending(PendingReason.ProjectionUnavailable)))
      Given("docs/spec/display-mutation.md R4 E3: Deleted receipt and valid versus null callbacks")
      val binding = success(bindingC(positive = true))
      val deletedreceipt = success(receiptC(binding.selection, Request.Delete(binding.selection, resource().entityId), OutcomeKind.Deleted,
        resource(), Some(resource().revision)))
      var calls = 0
      When("Deleted completes and null callback/receipt admission is attempted")
      val deleted = success(completeC(deletedreceipt, () => { calls += 1; Consequence.success(reload()) }))
      val nullcallback = completeC(deletedreceipt, null)
      val nullreceipt = completeC(null, () => Consequence.success(reload()))
      Then("Deleted never invokes reload and invalid admission never invents committed success")
      calls shouldBe 0
      deleted.projection shouldBe None
      _problem(nullcallback).code shouldBe "DSP96_MUTATION_CALLBACK_INVALID"
      _problem(nullreceipt).code shouldBe "DSP96_MUTATION_ELEMENT_INVALID"
    }
  }
  "E4 R4 precommit and indeterminate failure" should afterWord("in spec:display-mutation, example:E4, rules:R3,R4, phase:96") {
    "produce no receipt and preserve the canonical transaction-indeterminate reason and cause" in {
      Given("docs/spec/display-mutation.md R3,R4 E4: ordinary precommit rejection and indeterminate acknowledgment")
      When("the failures are represented through existing Consequence contracts")
      val precommit: Consequence[Result] = inputInvalidC("VALUE_INVALID", "request.fields")
      val indeterminate: Consequence[Result] = indeterminateC()
      Then("neither is a committed success and canonical reason cause and service-unavailable taxonomy survive")
      precommit.isSuccess shouldBe false
      indeterminate match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Reason("transaction-indeterminate"))
          conclusion.observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
          conclusion.observation.taxonomy shouldBe Taxonomy.serviceUnavailable
        case _ => fail("indeterminate acknowledgement cannot create a Receipt")
      }
    }
  }
}
