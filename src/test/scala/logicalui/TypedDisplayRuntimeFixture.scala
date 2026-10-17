package logicalui

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.display.{DisplayModel, DisplayProjection, DisplayRuntime}
import org.goldenport.cncf.entity.view.{Browser, ContextualViewBuilder, ViewCollection, ViewSpace}
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Development-only application handles; no production Entity or producer claim. */
object TypedDisplayRuntimeFixture {
  import TypedDisplayProjectionFixture.Capture

  final class SourceBrowser[V](
    findSource: (EntityId, ExecutionContext) => Consequence[V],
    querySource: (Query[?], ExecutionContext) => Consequence[Vector[V]]
  ) extends Browser[V] {
    private val _find_source = findSource
    private val _query_source = querySource
    private var _find_calls: Vector[(EntityId, ExecutionContext)] = Vector.empty
    private var _query_calls: Vector[(Query[?], ExecutionContext)] = Vector.empty
    private var _context_free_calls: Int = 0
    private var _count_calls: Int = 0

    def findCalls: Vector[(EntityId, ExecutionContext)] = _find_calls
    def queryCalls: Vector[(Query[?], ExecutionContext)] = _query_calls
    def contextFreeCalls: Int = _context_free_calls
    def countCalls: Int = _count_calls

    def find(id: EntityId): Consequence[V] = {
      _context_free_calls += 1
      Consequence.operationInvalid("fixture requires contextual find")
    }
    def query(q: Query[?]): Consequence[Vector[V]] = {
      _context_free_calls += 1
      Consequence.operationInvalid("fixture requires contextual query")
    }
    override def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] = {
      _find_calls = _find_calls :+ (id -> ctx)
      _find_source(id, ctx)
    }
    override def query_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Vector[V]] = {
      _query_calls = _query_calls :+ (q -> ctx)
      _query_source(q, ctx)
    }
    override def count(q: Query[?]): Consequence[Int] = {
      _count_calls += 1
      Consequence.operationInvalid("fixture count must not be called")
    }
    override def count_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Int] = {
      _count_calls += 1
      Consequence.operationInvalid("fixture count must not be called")
    }
  }

  final case class Installed(browser: SourceBrowser[Capture], bindings: Vector[DisplayRuntime.Binding])
  final case class Badge(resource: DisplayModel.Resource, caption: String)

  def captureBrowser(rows: Vector[Capture]): SourceBrowser[Capture] = {
    new SourceBrowser(
      (id, _) => rows.find(_.resource.entityId == id) match {
        case Some(source) => Consequence.success(source)
        case None => Consequence.argumentInvalid("fixture resource is absent")
      },
      (_, _) => Consequence.success(rows)
    )
  }

  def collection[V](browser: Browser[V]): ViewCollection[V] = {
    new ViewCollection(new ContextualViewBuilder[V] {
      def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] = {
        browser.find_with_context(id)
      }
    })
  }

  def register[V](space: ViewSpace, name: String, browser: Browser[V], named: Boolean = false): Unit = {
    space.register(name, collection(browser), browser)
    if (named) space.register(name, "typed", browser)
  }

  def bindings(browser: Browser[Capture]): Vector[DisplayRuntime.Binding] = {
    Vector(
      DisplayRuntime.list("collecting-resources", "ess.reference", browser,
        TypedDisplayProjectionFixture.listRow, Vector.empty),
      DisplayRuntime.detail("capture-detail", "ess.reference", browser,
        TypedDisplayProjectionFixture.detailRow)
    )
  }

  def install(component: Component, rows: Vector[Capture], name: String = "ess-runtime-source",
    named: Boolean = false): Installed = {
    val browser = captureBrowser(rows)
    register(component.viewSpace, name, browser, named)
    Installed(browser, bindings(browser))
  }

  def installBindings(component: Component): Vector[DisplayRuntime.Binding] = {
    install(component, Vector(TypedDisplayProjectionFixture.captureC(1, 7).TAKE)).bindings
  }

  /** Called by the R8 behavior scenario, alongside a real successful typed read. */
  def verifyTypeBoundary(): Unit = {
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.{DisplayProjection, DisplayRuntime}
      import org.goldenport.cncf.entity.view.Browser
      val browser: Browser[String] = null
      val row: DisplayProjection.RowBinding[Int] = null
      DisplayRuntime.detail[String]("screen", "view", browser, row)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.{DisplayProjection, DisplayRuntime}
      import org.goldenport.cncf.entity.view.Browser
      val browser: Browser[String] = null
      val row: DisplayProjection.RowBinding[String] = null
      val actions: Vector[DisplayProjection.ActionBinding[Vector[Int]]] = Vector.empty
      DisplayRuntime.list[String]("screen", "view", browser, row, actions)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayRuntime
      new DisplayRuntime.Binding() {
        def screenId = "screen"
        def viewBindingId = "view"
      }
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayRuntime
      new DisplayRuntime.Registry(Vector.empty)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayRuntime
      val registry: DisplayRuntime.Registry = null
      registry.copy()
    """)
  }
}
