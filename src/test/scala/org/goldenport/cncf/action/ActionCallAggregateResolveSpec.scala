package org.goldenport.cncf.action

import cats.syntax.flatMap.*
import scala.collection.mutable.ListBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateSpaceSpecHelper, ProductBuilder, SalesOrderBuilder, UserBuilder}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.runtime.testdomain.{ProductAggregate, SalesOrder, SalesOrderAggregate, SalesOrderLine, UserAggregate}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointHook, DslChokepointOutcome, DslChokepointPhase}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 *  version Mar. 24, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallAggregateResolveSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ActionCallHelper
  with AggregateSpaceSpecHelper {

  "ActionCall aggregate" should {
    "resolve sales order aggregate as typed value" in {
      Given("a component with aggregate collections registered")
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val salesorderid = sales_order_id()
      val expected = expected_sales_order_aggregate(salesorderid)

      When("executing an action call that resolves a typed aggregate")
      val call = action_call(
        actionname = "resolve-sales-order-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[SalesOrderAggregate](core, salesorderid)
      }
      val result = call.execute()

      Then("the call completes with record response")
      result shouldBe a[Consequence.Success[OperationResponse]]
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.print.nonEmpty shouldBe true
        case other =>
          fail(s"unexpected result: $other")
      }

      And("the aggregate is resolved through aggregate space as typed value")
      call.asInstanceOf[ResolveAggregateCall[SalesOrderAggregate]].resolved shouldBe Some(expected)
    }

    "resolve user aggregate as typed value" in {
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val userid = user_id()
      val expected = expected_user_aggregate(userid)

      val call = action_call(
        actionname = "resolve-user-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[UserAggregate](core, userid)
      }
      val result = call.execute()

      result shouldBe a[Consequence.Success[OperationResponse]]
      call.asInstanceOf[ResolveAggregateCall[UserAggregate]].resolved shouldBe Some(expected)
    }

    "resolve product aggregate as typed value" in {
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val productid = product_id()
      val expected = expected_product_aggregate(productid)

      val call = action_call(
        actionname = "resolve-product-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[ProductAggregate](core, productid)
      }
      val result = call.execute()

      result shouldBe a[Consequence.Success[OperationResponse]]
      call.asInstanceOf[ResolveAggregateCall[ProductAggregate]].resolved shouldBe Some(expected)
    }

    "resolve and apply diff update to sales order aggregate" in {
      Given("a component with aggregate collections registered")
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPairWithTestData(
        component,
        ActionCallSupport.TestDataKind.AllData
      )
      val salesorderid = sales_order_id()
      val patch = SalesOrderAggregatePatch(
        sku = Some("sku-updated"),
        quantityDelta = Some(2)
      )

      When("executing an action call that resolves and applies a diff update")
      val call = action_call(
        actionname = "resolve-and-diff-update-sales-order",
        pair
      ) { core =>
        ResolveAndDiffUpdateAggregateCall(core, salesorderid, patch)
      }
      val result = call.execute()

      Then("the call returns RecordResponse with update result")
      result shouldBe a[Consequence.Success[OperationResponse]]
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.print.contains("after") shouldBe true
        case other =>
          fail(s"unexpected result: $other")
      }

      And("the aggregate is loaded and updated as typed value")
      val typed = call.asInstanceOf[ResolveAndDiffUpdateAggregateCall]
      typed.before.map(_.line.quantity) shouldBe Some(1)
      typed.updated.map(_.line.quantity) shouldBe Some(3)
      typed.updated.map(_.line.sku) shouldBe Some("sku-updated")
      typed.storeUpdated shouldBe true
    }

    "search aggregate through aggregate DSL" in {
      Given("a component with query-enabled aggregate collection")
      val component = new Component() {}
      val expected = expected_user_aggregate(user_id())
      component.aggregateSpace.register(
        "user",
        new AggregateCollection(
          new UserBuilder,
          _ => Consequence.success(Vector(expected))
        )
      )
      val pair = ActionCallSupport.componentPair(component)

      When("executing an action call that searches aggregate")
      val call = action_call(
        actionname = "search-user-aggregate",
        pair
      ) { core =>
        SearchAggregateCall[UserAggregate](core, "user")
      }
      val result = call.execute()

      Then("the call returns search result payload")
      result shouldBe a[Consequence.Success[OperationResponse]]
      call.asInstanceOf[SearchAggregateCall[UserAggregate]].searched.map(_.data) shouldBe Some(Vector(expected))
    }

    "record aggregate DSL chokepoint and phases in calltree" in {
      Given("an execution context with calltree enabled")
      val component = new Component() {}
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      val base = ActionCallSupport.componentPair(component)
      val context = ExecutionContext.withFrameworkCallTreeEnabled(base.executioncontext, enabled = true)
      val pair = ActionCallSupport.pair(base.component, context)

      When("executing an action call that loads an aggregate")
      val call = action_call(
        actionname = "load-user-aggregate-calltree",
        pair
      ) { core =>
        ResolveAggregateCall[UserAggregate](core, user_id())
      }
      val result = call.execute()

      Then("the aggregate DSL chokepoint and phases are captured in calltree")
      result shouldBe a[Consequence.Success[_]]
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("dsl:aggregate.load")
      text should include("dsl:aggregate.load.authorization")
      text should include("dsl:aggregate.load.resolve")
    }

    "record aggregate DSL chokepoint metrics" in {
      Given("an aggregate DSL metrics snapshot before the call")
      val before = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total
      val component = new Component() {}
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      val pair = ActionCallSupport.componentPair(component)

      When("executing an aggregate load through the DSL")
      val call = action_call(
        actionname = "load-user-aggregate-metrics",
        pair
      ) { core =>
        ResolveAggregateCall[UserAggregate](core, user_id())
      }
      val result = call.execute()

      Then("the DSL chokepoint metrics are incremented")
      result shouldBe a[Consequence.Success[_]]
      val after = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total
      after should be > before
    }

    "use execution context DSL chokepoint hook registration" in {
      Given("an execution context with a registered custom DSL chokepoint hook")
      val hook = new RecordingDslChokepointHook
      val component = new Component() {}
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      val base = ActionCallSupport.componentPair(component)
      val context = ExecutionContext.withFrameworkDslChokepointHooks(
        base.executioncontext,
        Vector(hook)
      )
      val pair = ActionCallSupport.pair(base.component, context)

      When("executing an aggregate load through the DSL")
      val call = action_call(
        actionname = "load-user-aggregate-custom-hook",
        pair
      ) { core =>
        ResolveAggregateCall[UserAggregate](core, user_id())
      }
      val result = call.execute()

      Then("the registered hook receives chokepoint and phase callbacks")
      result shouldBe a[Consequence.Success[_]]
      hook.events should contain("enter:aggregate.load")
      hook.events should contain("phase-enter:aggregate.load.authorization")
      hook.events should contain("phase-leave:aggregate.load.resolve.success")
      hook.events should contain("leave:aggregate.load.success")
    }

    "emit aggregate DSL audit events when authorization fails" in {
      val backend = new MemoryBackend
      LogBackendHolder.reset()
      LogBackendHolder.install(backend)
      try {
        Given("a private aggregate backing entity owned by another subject")
        given EntityPersistent[NoticeProbeAggregate] = NoticeProbeAggregate.persistent
        val cid = org.simplemodeling.model.datatype.EntityCollectionId("test", "a", "notice")
        val id = EntityId("test", "private_notice_audit", cid)
        val component = new Component() {}
        component.entitySpace.registerEntity(
          "notice",
          NoticeProbeAggregate.collection(
            cid,
            NoticeProbeAggregate.privateOwnedBy(id, "private", "other-owner")
          )
        )
        val pair = ActionCallSupport.componentPair(component)

        When("executing an aggregate update denied at the DSL chokepoint")
        val call = action_call(
          actionname = "update-notice-audit",
          pair
        ) { core =>
          UpdateNoticeProbeAggregateCall(
            core,
            id,
            NoticeProbeAggregate(id, "updated")
          )
        }
        val result = call.execute()

        Then("DSL audit event names identify the failed aggregate phase")
        result shouldBe a[Consequence.Failure[_]]
        backend.lines.exists(_.contains("dsl.audit.aggregate.update.authorization.failure")) shouldBe true
        backend.lines.exists(_.contains("dsl.audit.aggregate.update.failure")) shouldBe true
      } finally {
        LogBackendHolder.reset()
      }
    }

    "reject aggregate update at the ActionCall chokepoint before running update logic" in {
      Given("a component with a private aggregate backing entity owned by another subject")
      given EntityPersistent[NoticeProbeAggregate] = NoticeProbeAggregate.persistent
      val cid = org.simplemodeling.model.datatype.EntityCollectionId("test", "a", "notice")
      val id = EntityId("test", "private_notice", cid)
      val component = new Component() {}
      component.entitySpace.registerEntity(
        "notice",
        NoticeProbeAggregate.collection(
          cid,
          NoticeProbeAggregate.privateOwnedBy(id, "private", "other-owner")
        )
      )
      val pair = ActionCallSupport.componentPair(component)

      When("executing an action call that only uses aggregate_update")
      val call = action_call(
        actionname = "update-notice",
        pair
      ) { core =>
        UpdateNoticeProbeAggregateCall(
          core,
          id,
          NoticeProbeAggregate(id, "updated")
        )
      }
      val result = call.execute()

      Then("authorization is enforced by aggregate_update before the update action runs")
      result shouldBe a[Consequence.Failure[_]]
      call.asInstanceOf[UpdateNoticeProbeAggregateCall].actionRan shouldBe false
    }

    "reject aggregate create at the ActionCall chokepoint before running create logic" in {
      Given("a component operation that restricts aggregate create to managers")
      val component = new Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
          CmlOperationDefinition(
            name = "createNotice",
            kind = "command",
            inputType = "Notice",
            outputType = "Notice",
            inputValueKind = "record",
            access = Some(CmlOperationAccess("manager_only"))
          )
        )
      }
      val pair = ActionCallSupport.componentPair(component)
      val cid = org.simplemodeling.model.datatype.EntityCollectionId("test", "a", "notice")
      val id = EntityId("test", "new_notice", cid)

      When("executing an action call that only uses aggregate_create")
      val call = action_call(
        actionname = "create-notice",
        pair
      ) { core =>
        CreateNoticeProbeAggregateCall(
          core,
          NoticeProbeAggregate(id, "new")
        )
      }
      val result = call.execute()

      Then("authorization is enforced by aggregate_create before the create action runs")
      result shouldBe a[Consequence.Failure[_]]
      call.asInstanceOf[CreateNoticeProbeAggregateCall].actionRan shouldBe false
    }
  }

}

private final case class SearchAggregateCall[A](
  core: ActionCall.Core,
  collectionname: String
) extends ProcedureActionCall {
  private var _searched: Option[org.goldenport.cncf.directive.SearchResult[A]] = None

  def searched: Option[org.goldenport.cncf.directive.SearchResult[A]] = _searched

  override def execute(): Consequence[OperationResponse] =
    aggregate_search_c[A](collectionname, Query("any")).map { result =>
      _searched = Some(result)
      OperationResponse.create(result)
    }
}

private final case class ResolveAggregateCall[A](
  core: ActionCall.Core,
  targetid: EntityId
) extends ProcedureActionCall {
  private var _resolved: Option[A] = None

  def resolved: Option[A] = _resolved

  override def execute(): Consequence[OperationResponse] = {
    aggregate_load_c[A](targetid)
      .map { value =>
      _resolved = Some(value)
      OperationResponse.RecordResponse(Record.dataAuto("aggregate" -> value))
    }
  }
}

private final case class SalesOrderAggregatePatch(
  sku: Option[String] = None,
  quantityDelta: Option[Int] = None
)

private final case class ResolveAndDiffUpdateAggregateCall(
  core: ActionCall.Core,
  targetid: EntityId,
  patch: SalesOrderAggregatePatch
) extends FunctionalActionCall {
  private var _before: Option[SalesOrderAggregate] = None
  private var _updated: Option[SalesOrderAggregate] = None
  private var _storeupdated: Boolean = false

  def before: Option[SalesOrderAggregate] = _before
  def updated: Option[SalesOrderAggregate] = _updated
  def storeUpdated: Boolean = _storeupdated

  protected def build_Program =
    exec_from {
      aggregate_load_c[SalesOrderAggregate](targetid).flatMap { current =>
        val updatedLine = current.line.copy(
          sku = patch.sku.getOrElse(current.line.sku),
          quantity = current.line.quantity + patch.quantityDelta.getOrElse(0)
        )
        val updated = current.copy(
          order = current.order.copy(line = updatedLine),
          line = updatedLine
        )
        _before = Some(current)
        _updated = Some(updated)

        store_update(updated).map { _ =>
          OperationResponse.RecordResponse(
            Record.dataAuto(
              "before" -> current,
              "patch" -> patch,
              "after" -> updated
            )
          )
        }
      }
    }

  private def store_update(
    updated: SalesOrderAggregate
  ): Consequence[Unit] = {
    val _ = store_update(updated.order.id, updated.order.toRecord())
    val _ = store_update(updated.line.id, updated.line.toRecord())
    _storeupdated = true
    Consequence.unit
  }
}

private final case class UpdateNoticeProbeAggregateCall(
  core: ActionCall.Core,
  targetid: EntityId,
  updated: NoticeProbeAggregate
) extends ProcedureActionCall {
  private var _actionran: Boolean = false

  def actionRan: Boolean = _actionran

  override def execute(): Consequence[OperationResponse] =
    aggregate_update_c(
      "notice",
      targetid,
      "updateNotice",
      {
        _actionran = true
        Consequence.success(updated)
      }
    ).map(x => OperationResponse.RecordResponse(x.toRecord()))
}

private final case class CreateNoticeProbeAggregateCall(
  core: ActionCall.Core,
  created: NoticeProbeAggregate
) extends ProcedureActionCall {
  private var _actionran: Boolean = false

  def actionRan: Boolean = _actionran

  override def execute(): Consequence[OperationResponse] =
    aggregate_create_c(
      "notice",
      "createNotice",
      {
        _actionran = true
        Consequence.success(created)
      }
    ).map(x => OperationResponse.RecordResponse(x.toRecord()))
}

private final case class NoticeProbeAggregate(
  id: EntityId,
  name: String,
  securityAttributes: Option[Record] = None
) extends org.goldenport.cncf.entity.EntityPersistable {
  def toRecord(): Record =
    securityAttributes.map { security =>
      Record.dataAuto(
        "id" -> id,
        "name" -> name,
        "owner_id" -> security.getString("owner_id"),
        "group_id" -> security.getString("group_id"),
        "privilege_id" -> security.getString("privilege_id"),
        "rights" -> security.getRecord("rights")
      )
    }.getOrElse(Record.dataAuto(
      "id" -> id,
      "name" -> name
    ))
}

private object NoticeProbeAggregate {
  def privateOwnedBy(
    id: EntityId,
    name: String,
    ownerId: String
  ): NoticeProbeAggregate =
    NoticeProbeAggregate(
      id,
      name,
      Some(Record.dataAuto(
        "owner_id" -> ownerId,
        "group_id" -> ownerId,
        "privilege_id" -> ownerId,
        "rights" -> Record.dataAuto(
          "owner" -> Record.dataAuto("read" -> true, "write" -> true, "execute" -> false),
          "group" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false),
          "other" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false)
        )
      ))
    )

  def collection(
    cid: org.simplemodeling.model.datatype.EntityCollectionId,
    entity: NoticeProbeAggregate
  )(using EntityPersistent[NoticeProbeAggregate]): EntityCollection[NoticeProbeAggregate] = {
    val storerealm = new EntityRealm[NoticeProbeAggregate](
      entityName = cid.name,
      loader = EntityLoader[NoticeProbeAggregate](_ => None),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    storerealm.put(entity)
    val descriptor = EntityDescriptor(
      collectionId = cid,
      plan = EntityRuntimePlan(
        entityName = cid.name,
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[NoticeProbeAggregate]]
    )
    new EntityCollection[NoticeProbeAggregate](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, None)
    )
  }

  val persistent: EntityPersistent[NoticeProbeAggregate] =
    new EntityPersistent[NoticeProbeAggregate] {
      def id(e: NoticeProbeAggregate): EntityId = e.id
      def toRecord(e: NoticeProbeAggregate): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[NoticeProbeAggregate] = {
        val id = r.getAs[EntityId]("id") match {
          case Some(v) => Consequence.success(v)
          case None => Consequence.argumentMissing("id")
        }
        val name = r.getString("name") match {
          case Some(v) => Consequence.success(v)
          case None => Consequence.argumentMissing("name")
        }
        for {
          i <- id
          n <- name
        } yield NoticeProbeAggregate(i, n)
      }
    }
}

private final class RecordingDslChokepointHook extends DslChokepointHook {
  private val _events = ListBuffer.empty[String]

  def events: Vector[String] = _events.synchronized {
    _events.toVector
  }

  override def enter(
    ctx: DslChokepointContext
  )(using ExecutionContext): Unit =
    _record(s"enter:${ctx.domain}.${ctx.operation}")

  override def phaseEnter(
    ctx: DslChokepointContext,
    phase: DslChokepointPhase
  )(using ExecutionContext): Unit =
    _record(s"phase-enter:${ctx.domain}.${ctx.operation}.${phase.name}")

  override def phaseLeave(
    ctx: DslChokepointContext,
    phase: DslChokepointPhase,
    outcome: DslChokepointOutcome
  )(using ExecutionContext): Unit =
    _record(s"phase-leave:${ctx.domain}.${ctx.operation}.${phase.name}.${outcome.name}")

  override def leave(
    ctx: DslChokepointContext,
    outcome: DslChokepointOutcome
  )(using ExecutionContext): Unit =
    _record(s"leave:${ctx.domain}.${ctx.operation}.${outcome.name}")

  private def _record(event: String): Unit = _events.synchronized {
    _events += event
  }
}

private final class MemoryBackend extends LogBackend {
  private val _lines = ListBuffer.empty[String]

  def lines: Vector[String] = _lines.synchronized {
    _lines.toVector
  }

  override def writeLine(line: String): Unit = _lines.synchronized {
    _lines += line
  }
}
