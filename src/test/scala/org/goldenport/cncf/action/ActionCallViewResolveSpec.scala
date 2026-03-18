package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallViewResolveSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ActionCallHelper {

  "ActionCall view" should {
    "load default and named view through view DSL" in {
      Given("a component with default and named view browser")
      val component = _component_with_views()
      val pair = ActionCallSupport.componentPair(component)
      val targetid = EntityId("test", "u1", EntityCollectionId("test", "1", "user"))

      When("executing default load")
      val defaultcall = action_call("load-default-view", pair) { core =>
        LoadDefaultViewCall(core, targetid)
      }
      val defaultresult = defaultcall.execute()

      When("executing named load")
      val namedcall = action_call("load-summary-view", pair) { core =>
        LoadNamedViewCall(core, "summary", targetid)
      }
      val namedresult = namedcall.execute()

      Then("both calls resolve through ActionCall view DSL")
      defaultresult shouldBe a[Consequence.Success[OperationResponse]]
      namedresult shouldBe a[Consequence.Success[OperationResponse]]
      defaultcall.asInstanceOf[LoadDefaultViewCall].loaded shouldBe Some("default-u1")
      namedcall.asInstanceOf[LoadNamedViewCall].loaded shouldBe Some("summary-u1")
    }

    "search named view through view DSL" in {
      Given("a component with named view browser")
      val component = _component_with_views()
      val pair = ActionCallSupport.componentPair(component)

      When("executing named search")
      val call = action_call("search-summary-view", pair) { core =>
        SearchNamedViewCall(core, "summary")
      }
      val result = call.execute()

      Then("the search result is wrapped as SearchResult")
      result shouldBe a[Consequence.Success[OperationResponse]]
      val searched = call.asInstanceOf[SearchNamedViewCall].searched
      searched.map(_.data) shouldBe Some(Vector("summary-search"))
      searched.map(_.totalCount) shouldBe Some(Some(1))
    }
  }

  private def _component_with_views(): Component = {
    val component = new Component() {}
    val collection = new ViewCollection[String](
      new ViewBuilder[String] {
        def build(id: EntityId): Consequence[String] =
          Consequence.success(s"default-${id.minor}")
      }
    )
    val defaultbrowser = Browser.from(
      collection,
      _ => Consequence.success(Vector("default-search"))
    )
    val namedbrowser = new Browser[String] {
      def find(id: EntityId): Consequence[String] =
        Consequence.success(s"summary-${id.minor}")

      def query(q: Query[_]): Consequence[Vector[String]] =
        Consequence.success(Vector("summary-search"))
    }
    component.viewSpace.register("user", collection, defaultbrowser)
    component.viewSpace.register("user", "summary", namedbrowser)
    component
  }
}

private final case class LoadDefaultViewCall(
  core: ActionCall.Core,
  id: EntityId
) extends ProcedureActionCall {
  private var _loaded: Option[String] = None

  def loaded: Option[String] = _loaded

  override def execute(): Consequence[OperationResponse] =
    view_load_c[String]("user", id).map { value =>
      _loaded = Some(value)
      OperationResponse.void
    }
}

private final case class LoadNamedViewCall(
  core: ActionCall.Core,
  viewname: String,
  id: EntityId
) extends ProcedureActionCall {
  private var _loaded: Option[String] = None

  def loaded: Option[String] = _loaded

  override def execute(): Consequence[OperationResponse] =
    view_load_c[String]("user", viewname, id).map { value =>
      _loaded = Some(value)
      OperationResponse.void
    }
}

private final case class SearchNamedViewCall(
  core: ActionCall.Core,
  viewname: String
) extends ProcedureActionCall {
  private var _searched: Option[org.goldenport.cncf.directive.SearchResult[String]] = None

  def searched: Option[org.goldenport.cncf.directive.SearchResult[String]] = _searched

  override def execute(): Consequence[OperationResponse] =
    view_search_c[String]("user", viewname, Query("any")).map { result =>
      _searched = Some(result)
      OperationResponse.create(result)
    }
}
