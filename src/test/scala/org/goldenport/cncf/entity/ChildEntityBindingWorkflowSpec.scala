package org.goldenport.cncf.entity

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInit,
  ComponentInstanceId,
  ComponentOrigin
}
import org.goldenport.cncf.component.entity.{Order as OrderEntity, OrderLine as OrderLineEntity}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.operation.{
  ChildEntityBindingOperationDefinition,
  CmlEntityRelationshipDefinition,
  CmlOperationAssociationBinding,
  CmlOperationChildEntityBinding,
  CmlOperationDefinition
}
import org.goldenport.cncf.projection.{DescribeProjection, HelpProjection}
import org.goldenport.cncf.http.StaticFormAppRenderer
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

/*
 * Executable specification for BI-04 operation child Entity binding.
 *
 * @since   Apr. 30, 2026
 *  version May. 18, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class ChildEntityBindingWorkflowSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E6, rules:R1,R5, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:child-binding, rules:R1,R5, phase:52")

  "Subsystem operation child Entity binding adapter" must _in_phase52_spec {
    "which records EID-01 child identity parsing" which {
      "E6 create child records using entity_id from an Entity create result" must _in_eid01_spec {
        "retain the exact canonical child-binding source identity" in {
          Given(
            "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R5; Example: E6; a parent create operation with child Entity binding metadata"
          )
          val (component, context) = _runtime_component()
          given ExecutionContext   = context
          val orderid              = _order_id("order_child_binding_create")
          val request = _create_order_request(
            component,
            orderid,
            Vector(
              Record.dataAuto("name" -> "Widget", "quantity" -> 2),
              Record.dataAuto("name" -> "Cable", "quantity"  -> 4)
            )
          )
          val binding = component.operationDefinitions.find(_.name == "createOrder").flatMap(
            _.childEntityBindings.headOption
          ).getOrElse(fail("binding missing"))
          ChildEntityBindingWorkflow.extract(binding, request).map(_.size) shouldBe Consequence.success(
            2
          )

          When("the operation is executed")
          val response = _success(component.subsystem.get.executeOperationResponse(request))

          Then(
            "the response is preserved and child lines are created with parent id and sort order"
          )
          response match {
            case OperationResponse.RecordResponse(record) =>
              record.getString("entity_id") shouldBe Some(orderid.value)
            case other =>
              fail(s"unexpected response: $other")
          }
          val lines = _order_lines(component, orderid)
          lines.map(_.orderId) shouldBe Vector(orderid, orderid)
          lines.map(_.sortOrder) shouldBe Vector(Some(0), Some(1))

          And("the child-binding response retains its exact source collection after scalar parsing")
          val parsedorder = EntityId.parse(orderid.value).toOption.get
          parsedorder shouldBe orderid
          EntityId.parse(orderid.value).toOption shouldBe Some(orderid)
        }
      }
    }

    "accept a matching parent id already present in child input" in {
      Given("a child row that already carries the same parent Entity id")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val orderid              = _order_id("order_child_binding_matching_parent")
      val request = _create_order_request(
        component,
        orderid,
        Vector(
          Record.dataAuto("orderId" -> orderid.value, "name" -> "Widget", "quantity" -> 2)
        )
      )

      When("the operation is executed")
      val _ = _success(component.subsystem.get.executeOperationResponse(request))

      Then("the child row is accepted")
      _order_lines(component, orderid).map(_.name) shouldBe Vector("Widget")
    }

    "reject mismatched child parent ids and compensate parent create" in {
      Given("a create operation where the second child row points at another parent")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val orderid              = _order_id("order_child_binding_compensate_parent")
      val otherid              = _order_id("order_child_binding_other_parent")
      val request = _create_order_request(
        component,
        orderid,
        Vector(
          Record.dataAuto("name"    -> "Widget", "quantity"  -> 2),
          Record.dataAuto("orderId" -> otherid.value, "name" -> "Bad", "quantity" -> 1)
        )
      )

      When("the operation is executed")
      val result = component.subsystem.get.executeOperationResponse(request)

      Then("the operation fails and both parent and created children are compensated")
      result shouldBe a[Consequence.Failure[_]]
      _resolve_order(component, orderid) shouldBe a[Consequence.Failure[_]]
      _order_lines(component, orderid) shouldBe Vector.empty
    }

    "reject existing child ids without overwriting or deleting them" in {
      Given("an existing child row and a create request that reuses its id")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val existingorderid      = _order_id("order_child_binding_existing_parent")
      val orderid              = _order_id("order_child_binding_existing_reuse")
      val lineid               = _order_line_id("order_child_binding_existing_line")
      _success(_put_order(component, existingorderid))
      _success(_put_order_line(component, lineid, existingorderid, "Original", 9))
      val request = _create_order_request(
        component,
        orderid,
        Vector(
          Record.dataAuto("id" -> lineid.value, "name" -> "Overwrite", "quantity" -> 1)
        )
      )

      When("the operation is executed")
      val result = component.subsystem.get.executeOperationResponse(request)

      Then("the operation fails, the parent is compensated, and the existing child remains intact")
      result shouldBe a[Consequence.Failure[_]]
      _resolve_order(component, orderid) shouldBe a[Consequence.Failure[_]]
      val line = _success(_resolve_order_line(component, lineid))
      line.orderId shouldBe existingorderid
      line.name shouldBe "Original"
      line.quantity shouldBe 9
    }

    "reject a canonical foreign child id before creating or compensating a foreign record" in {
      Given("a child row whose canonical id belongs to the parent collection")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val orderid              = _order_id("order_child_binding_foreign_child")
      val request = _create_order_request(
        component,
        orderid,
        Vector(Record.dataAuto(
          "id" -> orderid.value,
          "name" -> "Foreign",
          "quantity" -> 1
        ))
      )

      When("the child-binding create operation is executed")
      val result = component.subsystem.get.executeOperationResponse(request)

      Then("it fails before creating a child under a rebound collection identity")
      result shouldBe a[Consequence.Failure[_]]
      _resolve_order(component, orderid) shouldBe a[Consequence.Failure[_]]
      _order_lines(component, orderid) shouldBe Vector.empty
    }

    "compensate child and parent records when a later association binding fails" in {
      Given("a create operation whose child binding succeeds before association binding fails")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val orderid              = _order_id("order_child_binding_later_failure")
      val request = _create_order_request(
        component,
        orderid,
        Vector(
          Record.dataAuto("name" -> "Widget", "quantity" -> 2)
        ),
        operation = "createOrderWithBadAssociation",
        extraproperties = List(
          Property("relatedOrderId", "not-an-entity-id", None)
        )
      )

      When("the operation is executed")
      val result = component.subsystem.get.executeOperationResponse(request)

      Then("the overall failure compensates the already-created child and parent")
      result shouldBe a[Consequence.Failure[_]]
      _resolve_order(component, orderid) shouldBe a[Consequence.Failure[_]]
      _order_lines(component, orderid) shouldBe Vector.empty
    }

    "keep the parent for parameter-source child binding failures" in {
      Given("an existing parent and a parameter-source child binding operation")
      val (component, context) = _runtime_component()
      given ExecutionContext   = context
      val orderid              = _order_id("order_child_binding_parameter_parent")
      _success(_put_order(component, orderid))
      val otherid = _order_id("order_child_binding_parameter_other")
      val request = Request.of(
        component = component.name,
        service = "order",
        operation = "appendLines",
        properties = List(
          Property("orderId", orderid.value, None),
          Property(
            "lines",
            Vector(
              Record.dataAuto("name"    -> "Widget", "quantity"  -> 2),
              Record.dataAuto("orderId" -> otherid.value, "name" -> "Bad", "quantity" -> 1)
            ),
            None
          )
        )
      )

      When("the operation is executed")
      val result = component.subsystem.get.executeOperationResponse(request)

      Then("only child creation is compensated")
      result shouldBe a[Consequence.Failure[_]]
      _success(_resolve_order(component, orderid)).id shouldBe orderid
      _order_lines(component, orderid) shouldBe Vector.empty
    }

    "project child Entity binding metadata through help and describe" in {
      Given("a component operation with child Entity binding metadata")
      val (component, _) = _runtime_component()

      When("projecting operation metadata")
      val help = HelpProjection.project(component, Some(s"${component.name}.order.createOrder"))
      val describe =
        DescribeProjection.project(component, Some(s"${component.name}.order.createOrder"))
      val componenthelp     = HelpProjection.project(component, Some(component.name))
      val componentdescribe = DescribeProjection.project(component, Some(component.name))
      val componentmanual = StaticFormAppRenderer()
        .renderComponentManual(component.subsystem.get, component.name)
        .map(_.body)
        .getOrElse(fail("component manual missing"))

      Then("both help and describe expose childEntityBindings")
      _records(help, "childEntityBindings").map(_.getString("entityName")) shouldBe Vector(
        Some("order_line")
      )
      _records(help, "childEntityBindings").map(_.getString("relationshipName")) shouldBe Vector(
        Some("SalesOrder.lines")
      )
      _records(describe, "childEntityBindings").map(_.getString("inputParameter")) shouldBe Vector(
        Some("lines")
      )
      _records(componenthelp, "relationshipDefinitions").map(_.getString("name")) should contain(
        Some("SalesOrder.lines")
      )
      _records(componentdescribe, "relationshipDefinitions").map(
        _.getString("storageMode")
      ) should contain(Some("child-parent-id-field"))
      _records(componentdescribe, "relationshipDefinitions").map(
        _.getString("storageMode")
      ) should contain(Some("embedded-value-object"))
      _records(componentdescribe, "relationshipDefinitions").map(
        _.getString("targetModelKind")
      ) should contain(Some("value"))
      _records(componentdescribe, "relationshipDefinitions").map(
        _.getString("valueField")
      ) should contain(Some("shippingAddress"))
      componentmanual should include("Relationships")
      componentmanual should include("SalesOrder.lines")
      componentmanual should include("embedded-value-object")
      componentmanual should include("shippingAddress")
    }
  }

  private def _runtime_component(): (Component, ExecutionContext) = {
    val subsystem = TestComponentFactory.admittedEmptySubsystem("child_entity_binding_spec")
    val component = _component(subsystem)
    subsystem.add(component)
    val runtime = subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
    runtime -> runtime.logic.executionContext()
  }

  private def _component(subsystem: org.goldenport.cncf.subsystem.Subsystem): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "order",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                OrderOperation("createOrder", createsparent = true),
                OrderOperation("createOrderWithBadAssociation", createsparent = true),
                OrderOperation("appendLines", createsparent = false)
              )
            )
          ),
          spec.ServiceDefinition(
            name = "order_line",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(OrderOperation("noop", createsparent = false))
            )
          )
        )
      )
    )
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "createOrder",
            kind = "QUERY",
            inputType = "CreateOrder",
            outputType = "CreateOrderResult",
            inputValueKind = "COMMAND_VALUE",
            childEntityBindings = Vector(CmlOperationChildEntityBinding(
              name = "lines",
              entityName = "order_line",
              inputParameter = "lines",
              parentIdField = "orderId",
              relationshipName = Some("SalesOrder.lines"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              sortOrderField = Some("sortOrder"),
              createsEntity = true
            ))
          ),
          CmlOperationDefinition(
            name = "appendLines",
            kind = "QUERY",
            inputType = "AppendLines",
            outputType = "AppendLinesResult",
            inputValueKind = "COMMAND_VALUE",
            childEntityBindings = Vector(CmlOperationChildEntityBinding(
              name = "lines",
              entityName = "order_line",
              inputParameter = "lines",
              parentIdField = "orderId",
              relationshipName = Some("SalesOrder.lines"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
              sourceEntityIdParameters = Vector("orderId"),
              sortOrderField = Some("sortOrder"),
              createsEntity = true
            ))
          ),
          CmlOperationDefinition(
            name = "createOrderWithBadAssociation",
            kind = "QUERY",
            inputType = "CreateOrder",
            outputType = "CreateOrderResult",
            inputValueKind = "COMMAND_VALUE",
            childEntityBindings = Vector(CmlOperationChildEntityBinding(
              name = "lines",
              entityName = "order_line",
              inputParameter = "lines",
              parentIdField = "orderId",
              relationshipName = Some("SalesOrder.lines"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              sortOrderField = Some("sortOrder"),
              createsEntity = true
            )),
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "related_entity",
              targetKind = "order",
              createsAssociation = true,
              roles = Vector("related"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("relatedOrderId")
            ))
          )
        )
      override def relationshipDefinitions: Vector[CmlEntityRelationshipDefinition] =
        Vector(
          CmlEntityRelationshipDefinition(
            name = "SalesOrder.lines",
            kind = CmlEntityRelationshipDefinition.KindComposition,
            sourceEntityName = "order",
            targetEntityName = "order_line",
            multiplicity = Some("one-to-many"),
            storageMode = CmlEntityRelationshipDefinition.StorageChildParentIdField,
            parentIdField = Some("orderId"),
            sortOrderField = Some("sortOrder"),
            lifecyclePolicy = Some(CmlEntityRelationshipDefinition.LifecycleDependent)
          ),
          CmlEntityRelationshipDefinition(
            name = "SalesOrder.shippingAddress",
            kind = CmlEntityRelationshipDefinition.KindComposition,
            sourceEntityName = "order",
            targetEntityName = "ShippingAddress",
            targetModelKind = CmlEntityRelationshipDefinition.TargetModelKindValue,
            multiplicity = Some("zero-or-one"),
            storageMode = CmlEntityRelationshipDefinition.StorageEmbeddedValueObject,
            valueField = Some("shippingAddress"),
            lifecyclePolicy = Some(CmlEntityRelationshipDefinition.LifecycleDependent)
          )
        )
    }
    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.ChildEntityBindingSpec",
      componentid = ComponentId("org.goldenport.cncf.test.ChildEntityBindingSpec"),
      instanceid = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ChildEntityBindingSpec")),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _create_order_request(
      component: Component,
      id: EntityId,
      lines: Vector[Record],
      operation: String = "createOrder",
      extraproperties: List[Property] = Nil
  ): Request =
    Request.of(
      component = component.name,
      service = "order",
      operation = operation,
      properties = List(
        Property("id", id.value, None),
        Property("name", "Order", None),
        Property("status", "draft", None),
        Property("lines", lines, None)
      ) ++ extraproperties
    )

  private def _put_order(
      component: Component,
      id: EntityId
  )(using ExecutionContext): Consequence[Unit] =
    component.entitySpace.entity[Any]("order").createRecordSynced(_order_record(id))

  private def _put_order_line(
      component: Component,
      id: EntityId,
      orderid: EntityId,
      name: String,
      quantity: Int
  )(using ExecutionContext): Consequence[Unit] =
    component.entitySpace.entity[Any]("order_line").createRecordSynced(Record.dataAuto(
      "id"       -> id.value,
      "orderId"  -> orderid.value,
      "name"     -> name,
      "quantity" -> quantity
    ))

  private def _resolve_order(
      component: Component,
      id: EntityId
  )(using ExecutionContext): Consequence[OrderEntity] =
    component.entitySpace.entity[Any]("order").resolve(id).map(_.asInstanceOf[OrderEntity])

  private def _resolve_order_line(
      component: Component,
      id: EntityId
  )(using ExecutionContext): Consequence[OrderLineEntity] =
    component.entitySpace.entity[Any]("order_line").resolve(id).map(_.asInstanceOf[OrderLineEntity])

  private def _order_lines(
      component: Component,
      id: EntityId
  )(using ExecutionContext): Vector[OrderLineEntity] = {
    val internal = ExecutionContext.withAggregateInternalRead(summon[ExecutionContext], true)
    val result = _success(component.entitySpace.entity[Any]("order_line").search(
      EntityQuery[Any](OrderLineEntity.collectionId, Query.plan(Record.empty))
    )(using internal))
    result.data.collect {
      case line: OrderLineEntity if line.orderId == id => line
    }.sortBy(_.sortOrder.getOrElse(Int.MaxValue))
  }

  private def _order_record(id: EntityId): Record =
    Record.dataAuto(
      "id"     -> id.value,
      "name"   -> "Order",
      "status" -> "draft"
    )

  private def _order_id(value: String): EntityId =
    EntityId(OrderEntity.collectionId.major, value, OrderEntity.collectionId)

  private def _order_line_id(value: String): EntityId =
    EntityId(OrderLineEntity.collectionId.major, value, OrderLineEntity.collectionId)

  private def _records(record: Record, key: String): Vector[Record] =
    record.getVector(key).getOrElse(Vector.empty).collect {
      case r: Record => r
    }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value)      => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}

private final case class OrderOperation(
    opname: String,
    createsparent: Boolean
) extends spec.OperationDefinition with ChildEntityBindingOperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(OrderAction(req, createsparent))

  override def childEntityBindings: Vector[CmlOperationChildEntityBinding] =
    if (opname == "createOrder" || opname == "createOrderWithBadAssociation")
      Vector(CmlOperationChildEntityBinding(
        name = "lines",
        entityName = "order_line",
        inputParameter = "lines",
        parentIdField = "orderId",
        relationshipName = Some("SalesOrder.lines"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
        sortOrderField = Some("sortOrder"),
        createsEntity = true
      ))
    else if (opname == "appendLines")
      Vector(CmlOperationChildEntityBinding(
        name = "lines",
        entityName = "order_line",
        inputParameter = "lines",
        parentIdField = "orderId",
        relationshipName = Some("SalesOrder.lines"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("orderId"),
        sortOrderField = Some("sortOrder"),
        createsEntity = true
      ))
    else
      Vector.empty
}

private final case class OrderAction(
    request: Request,
    createsparent: Boolean
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    OrderActionCall(core, request, createsparent)
}

private final case class OrderActionCall(
    core: ActionCall.Core,
    oprequest: Request,
    createsparent: Boolean
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    given ExecutionContext = core.executionContext
    val id = EntityId.parse(_string("id").getOrElse(_string("orderId").getOrElse("")))
    id.flatMap { orderid =>
      val created =
        if (createsparent)
          core.component
            .flatMap(_.entitySpace.entityOption[Any]("order"))
            .map(_.createRecordSynced(Record.dataAuto(
              "id"     -> orderid.value,
              "name"   -> _string("name").getOrElse("Order"),
              "status" -> _string("status").getOrElse("draft")
            )))
            .getOrElse(Consequence.operationNotFound("order entity collection"))
        else
          Consequence.unit
      created.map { _ =>
        OperationResponse.RecordResponse(Record.dataAuto("entity_id" -> orderid.value))
      }
    }
  }

  private def _string(name: String): Option[String] =
    oprequest.properties.collectFirst { case p if p.name == name => p.value.toString.trim }.filter(
      _.nonEmpty
    )
}
