package org.goldenport.cncf.display

import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Oct. 17, 2026
 * @version Oct. 19, 2026
 */
final class DisplayEditingStudioReferenceSpec extends AnyWordSpec with DisplayEditingStudioReferenceFixture.Vocabulary {
  import DisplayEditingStudioReferenceFixture.*
  import DisplayMutationServiceFixture.{Target, representations, field, json}

  "REF-E1 Declared List selection and inert descriptors" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E1, rules:R1,R2,R5, phase:96") {
    "carry exact independent values and definition provenance from List to selected Detail on every canonical target" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R1/R2/R5 REF-E1: real SQLite $target/$representation UpdateOnly")
        val session = openReference(target, representation)
        try {
          val declaration = prepare(session)
          When("the client queries List, follows its decoded selection and reads both inert descriptors")
          val selected = select(session, declaration)
          describe(session, declaration)
          Then("the actual selected observation equals the independently declared canonical resource without writer authority")
          selected.resource shouldBe declaration.initial.resource
        } finally session.close()
      }
    }
  }

  "REF-E2 Observed standard update and stale-before-equality" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E2, rules:R2,R3,R4, phase:96") {
    "retain raw supplied text, no-op revision and complete unrelated state through fresh Detail and explicit List" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R2/R3/R4 REF-E2: independent SQLite $target/$representation selected/unrelated state")
        val session = openReference(target, representation)
        try {
          val declaration = prepare(session)
          val selected = select(session, declaration)
          val inputs = Vector(field("title", DisplayValue.Text("Updated:" + witnessText)), field("description", DisplayValue.Text(witnessText)))
          When("the selected Detail's actual observation drives a raw typed update, current no-op and stale equal request")
          val updated = update(session, selected, declaration.initial, inputs)
          query(session, Vector(updated.entry, declaration.other))
          val unchanged = update(session, updated.selected, updated.entry, inputs, applied = false)
          rejectMode(session, unchanged.selected, unchanged.entry, Rejection.StaleEqual, inputs, 1L)
          Then("explicit List alone reports the committed revision two and the original List/Detail JSON retain their earlier values")
          query(session, Vector(unchanged.entry, declaration.other))
          checkList(selected.listjson, session, Vector(declaration.initial, declaration.other))
          checkDetail(selected.detailjson, session, declaration.initial)
          unchanged.entry.version shouldBe 2L
        } finally session.close()
      }
    }
  }

  "REF-E3 Same-resource direct Business and explicit reload" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E3, rules:R2,R3,R6, phase:96") {
    "continue the observed update in the same session and expose revision three only through explicit Detail and List" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R2/R3/R6 REF-E3: one SQLite $target/$representation session and independent raw text")
        val session = openReference(target, representation)
        val sample = Sample(witnessText, witnessText, 1L, target, representation, Profile.UpdateOnly)
        try {
          When("the actual connected List/select/update/no-op/stale/direct Business/Detail/List sequence consumes returned observations")
          val result = connected(session, sample)
          Then("the same canonical resource retains exact supplied title/description and has stage review-requested at revision three")
          result.entry.version shouldBe 3L
          result.entry.values(0) shouldBe DisplayValue.Text(sample.title)
          result.entry.values(1) shouldBe DisplayValue.Text(sample.description)
          result.entry.values(6) shouldBe DisplayValue.Text("review-requested")
        } finally session.close()
      }
    }
  }

  "REF-E4 Structured invalid input preservation" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E4, rules:R4, phase:96") {
    "reject readonly, unknown, duplicate, blank, wrong-kind, stale and invalid selectors without changing complete state" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R4 REF-E4: canonical SQLite $target/$representation baseline")
        val session = openReference(target, representation)
        try {
          When("every independently declared malformed field and List/Detail selection reaches its ordinary admission gate")
          rejections(session)
          Then("the full raw text and canonical revision-seven state survive every rejected input")
          checkSemantic(session.lifecycleSnapshot(), Entry(session.component.id, 7L, values("Before")))
        } finally session.close()
      }
    }
  }

  "REF-E5 Enabled presentation and canonical authority" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E5, rules:R5, phase:96") {
    "preserve authentication, factory denial and foreign-owner Entity/List policy without inventing Aggregate owner-only authorization" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R5 REF-E5: Enabled SQLite $target/$representation reference")
        val session = openReference(target, representation)
        try {
          When("ordinary mutation and Business apply canonical anonymous/factory and Entity-specific foreign-owner gates")
          denials(session)
          Then("the selected canonical state and fixed Enabled presentation remain independent of execution permission")
          checkSemantic(session.lifecycleSnapshot(), Entry(session.component.id, 1L, values("Before")))
        } finally session.close()
      }
    }
  }

  "REF-E6 Explicit capability profiles" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E6, rules:R1,R7, phase:96") {
    "reject UpdateOnly create/delete and separately advertise the real Crud profile" in {
      for (target <- Target.values.toVector; representation <- representations; profile <- Profile.values.toVector) {
        Given(s"docs/spec/display-editing-studio-reference.md R1/R7 REF-E6: explicit $profile on SQLite $target/$representation")
        val session = openReference(target, representation, profile)
        try {
          val declaration = prepare(session)
          When("the ordinary descriptor reports the selected immutable profile and UpdateOnly receives create/delete attempts")
          if (profile == Profile.UpdateOnly) unsupported(session, declaration) else describe(session, declaration)
          Then("read/query/update remain supported and create/delete exactly match the explicit profile")
          session.component.inputBinding.capabilities shouldBe capabilities(profile)
          session.component.createBinding.capabilities shouldBe capabilities(profile)
          Given("the exact admitted composition and independently inconsistent profile/setup/custom/shared candidates")
          val setup = lifecycle(profile)
          val business = DisplayBusinessReviewFixture.Setup()
          When("the opt-in guard sees a missing reference, missing Business setup, inconsistent capabilities or production/custom/shared setup")
          val missingprofile = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, lifecycle = Some(setup), businessSetup = Some(business))
          }
          val missingbusiness = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, lifecycle = Some(setup), referenceProfile = Some(profile))
          }
          val inconsistent = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, lifecycle = Some(setup.copy(withList = false)),
              businessSetup = Some(business), referenceProfile = Some(profile))
          }
          val production = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, lifecycle = Some(setup),
              businessSetup = Some(business.copy(production = true)), referenceProfile = Some(profile))
          }
          val custom = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, registration = DisplayMutationServiceFixture.Registration.Empty,
              lifecycle = Some(setup), businessSetup = Some(business), referenceProfile = Some(profile))
          }
          val shared = intercept[IllegalArgumentException] {
            DisplayMutationServiceFixture.open(target, representation, sharedSession = Some(session), lifecycle = Some(setup),
              businessSetup = Some(business), referenceProfile = Some(profile))
          }
          Then("every inconsistent combination rejects before acquiring a new owned runtime")
          Vector(missingprofile, missingbusiness, inconsistent, production, custom, shared).foreach { failure =>
            failure shouldBe a[IllegalArgumentException]
          }
        } finally session.close()
      }
    }
  }

  "REF-E7 Explicit connected Crud lifecycle" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E7, rules:R2,R6,R7, phase:96") {
    "connect supplied create, returned Detail, explicit List, direct Business, reload, observed delete and List absence" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R2/R6/R7 REF-E7: real SQLite $target/$representation explicit Crud")
        val session = openReference(target, representation, Profile.Crud)
        try {
          When("the same canonical supplied identity traverses the real create/selection/Business/delete sequence")
          crud(session)
          Then("only explicit List reveals absence and the unrelated canonical root remains present")
          session.rows().unrelated shouldBe defined
        } finally session.close()
      }
    }
  }

  "REF-E8 Durable commands and separate read recovery" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-E8, rules:R3,R6,R8, phase:96") {
    "recover only presentation after a committed standard update or Business command without replaying either writer" in {
      for (target <- Target.values.toVector; representation <- representations) {
        Given(s"docs/spec/display-editing-studio-reference.md R3/R6/R8 REF-E8: complete SQLite $target/$representation pre-state")
        val session = openReference(target, representation)
        try {
          val declaration = prepare(session)
          val selected = select(session, declaration)
          val inputs = Vector(field("title", DisplayValue.Text("Durable:" + witnessText)), field("description", DisplayValue.Text(witnessText)))
          When("standard update commits before contextual projection fails and a subsequent Business commits before an explicit read fails")
          val updated = update(session, selected, declaration.initial, inputs, pending = true)
          val reviewed = business(session, updated.selected, updated.entry, recoverRead = true)
          Then("explicit recovered Detail and List retain the durable exact text/stage at revision three")
          reviewed.entry.version shouldBe 3L
          query(session, Vector(reviewed.entry, declaration.other))
        } finally session.close()
      }
    }
  }

  "Action-owned Display execution" should {
    "P103-DISPLAY-ACTION-ISOLATION-01 commit two independent Actions while preserving each caller's original transaction" in {
      Given("the ordinary SQLite reference session, selected Detail and complete independent initial state under R9/R10")
      val session = openReference()
      try {
        val declaration = prepare(session)
        val selected = select(session, declaration)
        var resource = selected.resource
        var entry = declaration.initial
        var previous = Option.empty[UnitOfWork]
        for (title <- Vector("First:" + witnessText, "Second:" + witnessText)) {
          Given("a fresh caller binding and its pre-call commit history, with an independently declared full update delta")
          val active = session.execution()
          val caller = active.context.unitOfWork
          val callertermination = caller.lastCommitTermination
          val callerresult = caller.lastCommitResult
          val inputs = Vector(field("title", DisplayValue.Text(title)), field("description", DisplayValue.Text(witnessText)))
          val before = session.lifecycleSnapshot()
          val next = changed(entry, inputs, entry.version + 1L)
          val wanted = DisplayMutationServiceListFixture.expectedUpdate(session, before, inputs, true, next.version)
          val model = updateModel(session, resource, inputs)
          val request = updateRequest(session, resource, inputs)
          resetTrace(session)
          When("the ordinary update consumes the actual selected resource observation in this caller's context")
          val attempt = session.execute(request, active)
          Then("one actual Action writer commits its exact receipt and complete state while its caller remains untouched")
          val value = json(attempt.result)
          val result = DisplayMutationCodec.decodeResultC(session.component.inputBinding.selection, definitions(session), model,
            Some(resource.revision), value.noSpaces).TAKE
          result.receipt.resource shouldBe next.resource
          result.receipt.kind shouldBe DisplayMutationResult.OutcomeKind.Updated
          result.retryCommand shouldBe false
          checkCommitted(attempt, session, 1)
          (active.callerUnitOfWork eq caller) shouldBe true
          (active.context.unitOfWork eq caller) shouldBe true
          caller.lastCommitTermination shouldBe callertermination
          caller.lastCommitResult shouldBe callerresult
          previous.foreach { earlier =>
            (active.uow eq earlier) shouldBe false
            (active.uow.executionContext eq earlier.executionContext) shouldBe false
          }
          session.log.events.count(_.event == "find") shouldBe 1
          session.log.queries shouldBe empty
          checkLifecycleState(session, wanted)
          checkSemantic(wanted, next)
          val fresh = value.hcursor.downField("projection").downField("display").focus.get
          resource = checkDetail(fresh, session, next)
          checkDetail(selected.detailjson, session, declaration.initial)
          entry = next
          previous = Some(active.uow)
        }
      } finally session.close()
    }
  }
}
