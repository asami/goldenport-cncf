package org.goldenport.cncf.information

import java.lang.reflect.Modifier
import domain.statemachine.informationLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityPersistentQuery, EntityPersistentUpdate}
import org.goldenport.record.Record
import org.simplemodeling.model.SimpleEntity
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.{Condition, Update}
import org.simplemodeling.model.value.LifecycleAttributes
import org.simplemodeling.model.value.LifecycleAttributesUpdate
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 30, 2026
 * @version Sep.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationCmlCanonicalContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Information CML canonical contract" should {
    "generate the complete Information output, value, and powertype families in their canonical packages" in {
      Given("the Information CML generated through Cozy value mode")

      When("the generated public type families are resolved")
      val outputclassnames = _output_classes.map(_.getName)
      val valueclassnames = _value_classes.map(_.getName)
      val powertypeclassnames = _powertype_classes.map(_.getName)

      Then("the Information output family remains in the canonical entity packages")
      outputclassnames shouldBe Vector(
        "org.goldenport.cncf.information.entity.Information",
        "org.goldenport.cncf.information.entity.read.Information",
        "org.goldenport.cncf.information.entity.operation.Information",
        "org.goldenport.cncf.information.entity.aggregate.Information",
        "org.goldenport.cncf.information.entity.view.Information",
        "org.goldenport.cncf.information.entity.view.summary.Information",
        "org.goldenport.cncf.information.entity.view.detail.Information"
      )

      And("every declared Information value is generated in the value package")
      valueclassnames shouldBe Vector(
        "org.goldenport.cncf.information.value.InformationImportContext",
        "org.goldenport.cncf.information.value.InformationValidationIssue",
        "org.goldenport.cncf.information.value.InformationIdentityBinding",
        "org.goldenport.cncf.information.value.InformationResolutionCandidate",
        "org.goldenport.cncf.information.value.InformationPublicationStatus",
        "org.goldenport.cncf.information.value.InformationConflict",
        "org.goldenport.cncf.information.value.InformationFieldEvent",
        "org.goldenport.cncf.information.value.InformationSpaceSnapshot",
        "org.goldenport.cncf.information.value.InformationSpaceCounts"
      )

      And("every declared Information powertype is generated in the value package")
      powertypeclassnames shouldBe Vector(
        "org.goldenport.cncf.information.value.InformationLifecycleState",
        "org.goldenport.cncf.information.value.InformationBindingStatus",
        "org.goldenport.cncf.information.value.InformationPublicationState",
        "org.goldenport.cncf.information.value.InformationConflictState",
        "org.goldenport.cncf.information.value.InformationFieldState"
      )
    }

    "keep managed revision on outputs and outside Information inputs" in {
      Given("the revision-aware SimpleEntity CML generation contract")

      When("the public output and input methods are inspected")
      val outputrevisionmethods = _output_classes.map { klass =>
        klass -> klass.getDeclaredMethods.filter(_.getName == "revision")
      }
      val inputrevisionmethods =
        _input_classes.flatMap(_.getMethods.filter(_.getName == "revision"))

      Then("every generated output is a revision-aware SimpleEntity")
      outputrevisionmethods.foreach { case (klass, methods) =>
        classOf[SimpleEntity].isAssignableFrom(klass) shouldBe true
        methods.length shouldBe 1
        methods.head.getReturnType shouldBe classOf[EntityRevision]
      }

      And("Create, Update, and Query expose no managed revision input")
      inputrevisionmethods shouldBe empty
    }

    "expose the canonical schema through every generated Information boundary" in {
      Given("generated Information root, Create, Update, and Query values")
      val root = _root_information
      val create = _create_information
      val update = _update_information
      val query = _query_information

      When("the generated schema methods and companions are read")
      val schemas = Vector(
        root.schema(),
        create.schema(),
        update.schema(),
        query.schema(),
        org.goldenport.cncf.information.entity.Information.schema,
        org.goldenport.cncf.information.entity.create.Information.schema,
        org.goldenport.cncf.information.entity.update.Information.schema,
        org.goldenport.cncf.information.entity.query.Information.schema
      )
      val canonicalnames = Vector(
        "domain",
        "rawData",
        "workingData",
        "state",
        "importContext",
        "validationIssues",
        "resolutionCandidates",
        "identityBindings",
        "publicationStatuses",
        "conflicts",
        "fieldEvents",
        "confirmedAt"
      )

      Then("all generated boundaries expose the canonical Information columns")
      schemas.foreach { schema =>
        schema.columns.map(_.name.value).takeRight(canonicalnames.size) shouldBe canonicalnames
      }

      And("the generated revision column is output-managed")
      val revisioncolumn = schemas.head.columns.find(_.name.value == "revision").getOrElse(
        fail("generated revision column is missing")
      )
      revisioncolumn.web.system shouldBe true
      revisioncolumn.web.readonly shouldBe true
      revisioncolumn.web.required shouldBe Some(true)
      revisioncolumn.web.help shouldBe Some("Framework-managed persistence revision.")
    }

    "round-trip the generated root through its record persistence contract" in {
      Given("a generated Information root and its EntityPersistent instance")
      val root = _root_information
      val persistent = summon[EntityPersistent[org.goldenport.cncf.information.entity.Information]]

      When("the root is encoded and reconstructed through the generated persistence path")
      val record = persistent.toRecord(root)
      val reconstructed = _success(persistent.fromRecord(record))

      Then("the record contains the canonical root identity and Information fields")
      record.getAny("id") should not be empty
      record.getAny("revision") should not be empty
      record.getAny("domain") shouldBe Some(root.domain)
      record.getAny("fieldEvents") should not be empty
      record.getAny("confirmedAt") should not be empty

      And("reconstruction preserves the root values and managed revision")
      reconstructed.id shouldBe root.id
      reconstructed.revision shouldBe root.revision
      reconstructed.domain shouldBe root.domain
      reconstructed.rawData shouldBe root.rawData
      reconstructed.workingData shouldBe root.workingData
      reconstructed.fieldEvents shouldBe root.fieldEvents
      reconstructed.confirmedAt shouldBe root.confirmedAt
    }

    "provide generated persistence contracts for Create Update and Query" in {
      Given("generated Create, Update, and Query values and their concrete persistence type classes")
      val create = _create_information
      val update = _update_information
      val query = _query_information
      val createpersistent = summon[EntityPersistentCreate[org.goldenport.cncf.information.entity.create.Information]]
      val updatepersistent = summon[EntityPersistentUpdate[org.goldenport.cncf.information.entity.update.Information]]
      val querypersistent = summon[EntityPersistentQuery[org.goldenport.cncf.information.entity.query.Information]]

      When("each generated boundary is encoded for its supported record or store path")
      val createrecord = createpersistent.toRecord(create)
      val updaterecord = updatepersistent.toStoreRecord(update)
      val queryrecord = querypersistent.toRecord(query)

      Then("Create and Query records carry their canonical application fields")
      createrecord.getAny("domain") shouldBe Some(create.domain)
      createrecord.getAny("rawData") shouldBe Some(create.rawData)
      queryrecord.getAny("domain") shouldBe Some("book")
      queryrecord.getAny("confirmedAt") should not be empty

      And("Update persistence retains its generated application update fields")
      updaterecord.getAny("confirmedAt").collect { case value: Update[?] => value.isSet } shouldBe Some(true)
      updaterecord.getAny("fieldEvents").collect { case value: Update[?] => value.isSet } shouldBe Some(true)
      updaterecord.getAny("revision") shouldBe None
    }

    "represent optional and collection fields with explicit generated Update wrappers" in {
      Given("a representative confirmation timestamp and Information field-event collection")
      val confirmedat = java.time.Instant.parse("2026-08-30T00:00:00Z")
      val fieldevents = Vector(_field_event)

      When("the generated Update builder receives both application updates")
      val update = _success(
        org.goldenport.cncf.information.entity.update.Information.Builder()
          .withConfirmedAt(Update.set(confirmedat))
          .withFieldEvents(Update.set(fieldevents))
          .buildC()
      )

      Then("the optional field is an explicit Set update")
      update.confirmedAt shouldBe Update.set(confirmedat)
      update.confirmedAt.isSet shouldBe true
      update.confirmedAt.fold(fail("confirmedAt update is not set"), value => value shouldBe confirmedat, fail("confirmedAt unexpectedly clears"))

      And("the collection field is an explicit Set update")
      update.fieldEvents shouldBe Update.set(fieldevents)
      update.fieldEvents.isSet shouldBe true
      update.fieldEvents.fold(fail("fieldEvents update is not set"), value => value shouldBe fieldevents, fail("fieldEvents unexpectedly clears"))
    }

    "distinguish required and defaulted generated Information contracts" in {
      Given("a public Information Builder with only its identity and required application fields")
      val minimalbuilder = _minimal_information_builder

      When("the minimal Information root is built through the public API")
      val root = _success(minimalbuilder.buildC())

      Then("managed revision and optional root values retain their canonical defaults")
      root.revision shouldBe EntityRevision.INITIAL
      root.importContext shouldBe None
      root.confirmedAt shouldBe None
      root.validationIssues shouldBe empty
      root.resolutionCandidates shouldBe empty
      root.identityBindings shouldBe empty
      root.publicationStatuses shouldBe empty
      root.conflicts shouldBe empty
      root.fieldEvents shouldBe empty

      Given("otherwise valid root builders with one required application field omitted")
      val missingrequired = Vector(
        "domain" -> _minimal_information_builder.withDomain(None),
        "rawData" -> _minimal_information_builder.withRawData(None),
        "workingData" -> _minimal_information_builder.withWorkingData(None),
        "state" -> _minimal_information_builder.withState(None)
      )

      When("each required-field omission is submitted to buildC")
      val missingrequiredresults = missingrequired.map { case (name, builder) =>
        name -> builder.buildC()
      }

      Then("every CML-required root application field is rejected")
      missingrequiredresults.foreach { case (_, result) =>
        result shouldBe a[Consequence.Failure[?]]
      }

      Given("an InformationImportContext Builder with no members and one with only sourceType")
      val missingsourcetypebuilder =
        org.goldenport.cncf.information.value.InformationImportContext.Builder()
      val contextbuilder =
        org.goldenport.cncf.information.value.InformationImportContext.Builder()
          .withSourceType("catalog")

      When("the nested value builders are built through their public APIs")
      val missingsourcetype = missingsourcetypebuilder.buildC()
      val context = _success(contextbuilder.buildC())

      Then("sourceType is required and every other member defaults to None")
      missingsourcetype shouldBe a[Consequence.Failure[?]]
      context.sourceName shouldBe None
      context.sourceUri shouldBe None
      context.operation shouldBe None
      context.jobId shouldBe None
      context.sagaId shouldBe None
      context.taskId shouldBe None
      context.importedAt shouldBe None
      context.importedBy shouldBe None
    }

    "make LifecycleAttributes the only semantic owner of updatedAt" in {
      Given("the CML model without a direct Information updatedAt attribute")

      When("the root and generated input public semantic members are inspected")
      val classes = _root_class +: _input_classes

      Then("no root or input type declares updatedAt directly")
      classes.foreach { klass =>
        _public_declared_method_names(klass) should not contain "updatedAt"
      }

      And("root and Create retain the non-update lifecycle aggregate")
      _lifecycle_attributes_method(_root_class).getReturnType shouldBe classOf[LifecycleAttributes]
      _lifecycle_attributes_method(_create_input_class).getReturnType shouldBe classOf[LifecycleAttributes]

      And("Update uses the update lifecycle aggregate and Query omits it")
      _lifecycle_attributes_method(_update_input_class).getReturnType shouldBe classOf[LifecycleAttributesUpdate]
      _lifecycle_attributes_method_option(_query_input_class) shouldBe empty

      And("LifecycleAttributes remains the semantic timestamp owner")
      classOf[LifecycleAttributes].getMethods.exists { method =>
        method.getName == "updatedAt" && method.getParameterCount == 0
      } shouldBe true
    }

    "preserve the ordered static Information lifecycle topology" in {
      Given("the generated informationLifecycle static transition descriptor")
      val expectedstates: Vector[(String, Either[String, Int])] = Vector(
        "imported" -> Right(1),
        "invalid" -> Right(2),
        "needs_resolution" -> Right(3),
        "ready_for_confirmation" -> Right(4),
        "confirmed" -> Right(5),
        "published" -> Right(6),
        "rejected" -> Right(7),
        "conflict" -> Right(8)
      )
      val expectedtransitions: Vector[(String, Option[String], String)] = Vector(
        ("imported", Some("validateInvalid"), "invalid"),
        ("imported", Some("validateNeedsResolution"), "needs_resolution"),
        ("imported", Some("validateReady"), "ready_for_confirmation"),
        ("imported", Some("reject"), "rejected"),
        ("invalid", Some("update"), "imported"),
        ("invalid", Some("reject"), "rejected"),
        ("needs_resolution", Some("update"), "imported"),
        ("needs_resolution", Some("selectResolution"), "ready_for_confirmation"),
        ("needs_resolution", Some("reject"), "rejected"),
        ("ready_for_confirmation", Some("update"), "imported"),
        ("ready_for_confirmation", Some("confirm"), "confirmed"),
        ("ready_for_confirmation", Some("reject"), "rejected"),
        ("confirmed", Some("publish"), "published"),
        ("confirmed", Some("reopen"), "imported"),
        ("confirmed", Some("detectConflict"), "conflict"),
        ("published", Some("detectConflict"), "conflict"),
        ("rejected", Some("reopen"), "imported"),
        ("conflict", Some("resolveConflict"), "confirmed")
      )

      When("the descriptor states and transitions are read")
      val states = informationLifecycle.states.map(state => state.name -> state.value)
      val transitions = informationLifecycle.transitions.map { transition =>
        (transition.from, transition.event, transition.to)
      }

      Then("the eight CML states retain their declared order and values")
      states shouldBe expectedstates

      And("every declared transition retains CML order")
      transitions shouldBe expectedtransitions

      And("the static permits API accepts every declared transition")
      expectedtransitions.foreach { case (from, event, to) =>
        informationLifecycle.permits(from, event.getOrElse(fail("CML transition event is missing")), to) shouldBe true
      }

      And("static transition evidence rejects undeclared moves")
      transitions.exists { case (from, event, to) =>
        from == "imported" && event.contains("publish") && to == "published"
      } shouldBe false
      informationLifecycle.permits("imported", "publish", "published") shouldBe false
    }

    "remain a value-mode CML contract without a DomainComponent" in {
      Given("the Information generated type class loader")
      When("the optional generated DomainComponent class is resolved")

      Then("no generated DomainComponent is present or required")
      an[ClassNotFoundException] shouldBe thrownBy {
        Class.forName("domain.DomainComponent", false, getClass.getClassLoader)
      }
    }
  }

  private val _root_class: Class[?] =
    classOf[org.goldenport.cncf.information.entity.Information]

  private val _information_id: EntityId =
    EntityId(
      major = "major",
      minor = "minor",
      collection = EntityCollectionId("major", "minor", "information"),
      timestamp = Some(java.time.Instant.parse("2026-08-30T00:00:00Z")),
      entropy = Some("information_contract_1")
    )

  private val _output_classes: Vector[Class[?]] = Vector(
    _root_class,
    classOf[org.goldenport.cncf.information.entity.read.Information],
    classOf[org.goldenport.cncf.information.entity.operation.Information],
    classOf[org.goldenport.cncf.information.entity.aggregate.Information],
    classOf[org.goldenport.cncf.information.entity.view.Information],
    classOf[org.goldenport.cncf.information.entity.view.summary.Information],
    classOf[org.goldenport.cncf.information.entity.view.detail.Information]
  )

  private val _create_input_class: Class[?] =
    classOf[org.goldenport.cncf.information.entity.create.Information]

  private val _update_input_class: Class[?] =
    classOf[org.goldenport.cncf.information.entity.update.Information]

  private val _query_input_class: Class[?] =
    classOf[org.goldenport.cncf.information.entity.query.Information]

  private val _input_classes: Vector[Class[?]] = Vector(
    _create_input_class,
    _update_input_class,
    _query_input_class
  )

  private val _value_classes: Vector[Class[?]] = Vector(
    classOf[org.goldenport.cncf.information.value.InformationImportContext],
    classOf[org.goldenport.cncf.information.value.InformationValidationIssue],
    classOf[org.goldenport.cncf.information.value.InformationIdentityBinding],
    classOf[org.goldenport.cncf.information.value.InformationResolutionCandidate],
    classOf[org.goldenport.cncf.information.value.InformationPublicationStatus],
    classOf[org.goldenport.cncf.information.value.InformationConflict],
    classOf[org.goldenport.cncf.information.value.InformationFieldEvent],
    classOf[org.goldenport.cncf.information.value.InformationSpaceSnapshot],
    classOf[org.goldenport.cncf.information.value.InformationSpaceCounts]
  )

  private val _powertype_classes: Vector[Class[?]] = Vector(
    classOf[org.goldenport.cncf.information.value.InformationLifecycleState],
    classOf[org.goldenport.cncf.information.value.InformationBindingStatus],
    classOf[org.goldenport.cncf.information.value.InformationPublicationState],
    classOf[org.goldenport.cncf.information.value.InformationConflictState],
    classOf[org.goldenport.cncf.information.value.InformationFieldState]
  )

  private def _public_declared_method_names(klass: Class[?]): Set[String] =
    klass.getDeclaredMethods.iterator
      .filter(method => Modifier.isPublic(method.getModifiers))
      .map(_.getName)
      .toSet

  private def _lifecycle_attributes_method(klass: Class[?]) =
    _lifecycle_attributes_method_option(klass).
      getOrElse(fail(s"missing lifecycleAttributes method on ${klass.getName}"))

  private def _lifecycle_attributes_method_option(klass: Class[?]) =
    klass.getDeclaredMethods.find { method =>
      method.getName == "lifecycleAttributes" && method.getParameterCount == 0
    }

  private def _root_information: org.goldenport.cncf.information.entity.Information =
    _success(
      org.goldenport.cncf.information.entity.Information.Builder()
        .withId(_information_id)
        .withDomain("book")
        .withRawData(Record.data("source" -> "import"))
        .withWorkingData(Record.data("title" -> "Canonical Information"))
        .withState(org.goldenport.cncf.information.value.InformationLifecycleState.imported)
        .withFieldEvents(Vector(_field_event))
        .withConfirmedAt(java.time.Instant.parse("2026-08-30T00:00:00Z"))
        .buildC()
    )

  private def _minimal_information_builder:
      org.goldenport.cncf.information.entity.Information.Builder =
    org.goldenport.cncf.information.entity.Information.Builder()
      .withId(_information_id)
      .withDomain("book")
      .withRawData(Record.data("source" -> "import"))
      .withWorkingData(Record.data("title" -> "Canonical Information"))
      .withState(org.goldenport.cncf.information.value.InformationLifecycleState.imported)

  private def _create_information: org.goldenport.cncf.information.entity.create.Information =
    _success(
      org.goldenport.cncf.information.entity.create.Information.Builder()
        .withId(_information_id)
        .withDomain("book")
        .withRawData(Record.data("source" -> "import"))
        .withWorkingData(Record.data("title" -> "Canonical Information"))
        .withState(org.goldenport.cncf.information.value.InformationLifecycleState.imported)
        .buildC()
    )

  private def _update_information: org.goldenport.cncf.information.entity.update.Information =
    _success(
      org.goldenport.cncf.information.entity.update.Information.Builder()
        .withConfirmedAt(Update.set(java.time.Instant.parse("2026-08-30T00:00:00Z")))
        .withFieldEvents(Update.set(Vector(_field_event)))
        .buildC()
    )

  private def _query_information: org.goldenport.cncf.information.entity.query.Information =
    _success(
      org.goldenport.cncf.information.entity.query.Information.Builder()
        .withDomain(Condition.is("book"))
        .withConfirmedAt(Condition.is(java.time.Instant.parse("2026-08-30T00:00:00Z")))
        .withFieldEvents(Condition.is(Vector(_field_event)))
        .buildC()
    )

  private def _field_event: org.goldenport.cncf.information.value.InformationFieldEvent =
    org.goldenport.cncf.information.value.InformationFieldEvent(
      "title",
      org.goldenport.cncf.information.value.InformationFieldState.imported,
      "editor",
      Some("update"),
      None,
      None,
      Some("before"),
      Some("after"),
      Some("contract-test"),
      None,
      java.time.Instant.parse("2026-08-30T00:00:00Z"),
      Some("tester")
    )

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"generated Information contract construction failed: $failure")
  }
}
