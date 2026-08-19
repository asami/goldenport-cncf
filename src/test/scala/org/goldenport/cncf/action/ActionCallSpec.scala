package org.goldenport.cncf.action

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import cats.free.Free
import cats.~>
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition, CmlOperationField}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.{Field, Record}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version Apr. 28, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionCallSpec extends AnyWordSpec with Matchers {

  "ActionCall" should {

    "allow authenticated_only for authenticated subject" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("access_token" -> "abc.def.ghi")
      )
      val call = _call("changePassword", Some(CmlOperationAccess("authenticated_only")))

      call.authorize() shouldBe Consequence.unit
    }

    "reject authenticated_only for anonymous subject" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call("changePassword", Some(CmlOperationAccess("authenticated_only")))

      call.authorize() shouldBe a[Consequence.Failure[_]]
    }

    "apply declared authorization to a service-qualified action name" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call(
        "changePassword",
        Some(CmlOperationAccess("authenticated_only")),
        servicename = Some("account")
      )

      call.action.name shouldBe "account.changePassword"
      call.authorize() shouldBe a[Consequence.Failure[_]]
    }

    "not confuse a qualified action with a shorter suffix operation" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call(
        "changePassword",
        Some(CmlOperationAccess("authenticated_only")),
        servicename = Some("account"),
        definitionname = Some("password")
      )

      call.action.name shouldBe "account.changePassword"
      call.authorize() shouldBe Consequence.unit
    }

    "allow anonymous_only for anonymous subject" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call("register", Some(CmlOperationAccess("anonymous_only")))

      call.authorize() shouldBe Consequence.unit
    }

    "reject anonymous_only for authenticated subject" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true", "access_token" -> "abc.def.ghi")
      )
      val call = _call("register", Some(CmlOperationAccess("anonymous_only")))

      call.authorize() shouldBe a[Consequence.Failure[_]]
    }

    "allow public for anonymous subject without a component authorizer" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call("refreshAccessToken", Some(CmlOperationAccess("public")))

      call.authorize() shouldBe Consequence.unit
    }

    "allow public for authenticated subject without a component authorizer" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true", "refresh_token" -> "refresh-token")
      )
      val call = _call("refreshAccessToken", Some(CmlOperationAccess("public")))

      call.authorize() shouldBe Consequence.unit
    }

    "run ProcedureActionCall ExecUowM programs through the runtime interpreter" in {
      val response = _http_response_ok()
      val interpreter = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          fa match {
            case UnitOfWorkOp.HttpGet("/procedure-dsl", _, _) =>
              Consequence.success(response.asInstanceOf[A])
            case other =>
              Consequence.operationIllegal("procedure_action_call_spec", s"unexpected op: $other")
          }
      }
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true"),
        interpreter = Some(interpreter)
      )
      val program: ExecUowM[String] =
        ConsequenceT
          .liftF[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], HttpResponse](
            Free.liftF(UnitOfWorkOp.HttpGet("/procedure-dsl"))
          )
          .map(_.status.code.toString)
      val call = _program_call(program)

      call.execute() shouldBe Consequence.success(OperationResponse.Scalar("200"))
    }

    "propagate ProcedureActionCall ExecUowM interpreter failures" in {
      val interpreter = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          Consequence.operationIllegal("procedure_action_call_spec", "interpreter failure")
      }
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true"),
        interpreter = Some(interpreter)
      )
      val program: ExecUowM[String] =
        ConsequenceT
          .liftF[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], HttpResponse](
            Free.liftF(UnitOfWorkOp.HttpGet("/procedure-dsl"))
          )
          .map(_.status.code.toString)
      val call = _program_call(program)

      call.execute() shouldBe a[Consequence.Failure[_]]
    }

    "allow ProviderBehavior to use common Behavior helpers without ActionCall core" in {
      val response = _http_response_ok()
      val interpreter = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          fa match {
            case UnitOfWorkOp.HttpGet("/provider-dsl", _, _) =>
              Consequence.success(response.asInstanceOf[A])
            case other =>
              Consequence.operationIllegal("provider_behavior_spec", s"unexpected op: $other")
          }
      }
      val context = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true"),
        interpreter = Some(interpreter)
      )
      val behavior = new _TestProviderBehavior(
        Behavior.Core(
          executionContext = context,
          component = None,
          correlationId = Some(CorrelationId("test", "provider"))
        )
      )

      behavior.run() shouldBe Consequence.success("200")
    }

    "provide ActionCall internal DSL helpers for config and structured text parsing" in {
      val context = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true")
      )
      context.runtime.setResolvedParameters(
        ResolvedParameters.fromResolvedConfiguration(
          ResolvedConfiguration(
            Configuration(Map(
              "demo.message" -> ConfigurationValue.StringValue("runtime-value")
            )),
            ConfigurationTrace.empty
          )
        )
      )
      val call = _internal_dsl_call(
        context,
        List(Property("demo.message", "action-value", None))
      )

      call.execute() shouldBe Consequence.success(OperationResponse.RecordResponse(_record(
        "message" -> "action-value",
        "runtimeMessage" -> "runtime-value",
        "title" -> "府中散歩",
        "lat" -> "35.613528"
      )))
    }

    "provide component-local embedded datastore helpers through UnitOfWork" in {
      val root = Files.createTempDirectory("cncf-action-embedded-datastore")
      val context = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true"),
        realInterpreter = true
      )
      context.runtime.setResolvedParameters(
        ResolvedParameters.fromResolvedConfiguration(
          ResolvedConfiguration(
            Configuration(Map(
              "cncf.local-data.root" -> ConfigurationValue.StringValue(root.toString)
            )),
            ConfigurationTrace.empty
          )
        )
      )
      val call = _embedded_datastore_call(context)

      call.execute() shouldBe Consequence.success(OperationResponse.RecordResponse(_record(
        "dir" -> root.resolve("embedded-spec").toAbsolutePath.normalize.toString,
        "db" -> root.resolve("embedded-spec").resolve("gazetteer.db").toAbsolutePath.normalize.toString,
        "name" -> "長池見附橋",
        "lat" -> "35.61178333"
      )))
    }

    "resolve response confidentiality by the operation segment of a qualified action name" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true")
      )
      val call = _confidentiality_call()

      call.action.name shouldBe "Scraper.Scraping.FetchPage"
      call.resultFieldConfidentiality.get("body").map(_.label) shouldBe Some("internal")
    }
  }

  private def _confidentiality_call()(using ExecutionContext): ActionCall = {
    val targetcomponent = new Component {
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
        CmlOperationDefinition(
          name = "FetchPage",
          kind = "command",
          inputType = "FetchPageRequest",
          outputType = "FetchPageResult",
          inputValueKind = "record",
          resultFields = Vector(CmlOperationField("body", "string", confidentiality = Some("internal")))
        )
      )
    }
    val targetaction = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request.of("Scraper", "Scraping", "FetchPage")
    }
    new ProcedureActionCall with ActionCall.Core.Holder {
      val core = ActionCall.Core(targetaction, summon[ExecutionContext], Some(targetcomponent), None)
      def execute(): Consequence[OperationResponse] = Consequence.success(OperationResponse.void)
    }
  }

  private def _call(
    operationname: String,
    access: Option[CmlOperationAccess],
    servicename: Option[String] = None,
    definitionname: Option[String] = None
  )(using ExecutionContext): ActionCall = {
    val comp = new Component {
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
        CmlOperationDefinition(
          name = definitionname.getOrElse(operationname),
          kind = "command",
          inputType = "Input",
          outputType = "Output",
          inputValueKind = "record",
          access = access
        )
      )
    }
    val act = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request(
          component = None,
          service = servicename,
          operation = operationname,
          arguments = Nil,
          switches = Nil,
          properties = List(Property("dummy", "x", None))
        )
    }
    new ProcedureActionCall with ActionCall.Core.Holder {
      val core = ActionCall.Core(
        action = act,
        executionContext = summon[ExecutionContext],
        component = Some(comp),
        correlationId = None
      )
      def execute(): Consequence[OperationResponse] =
        Consequence.success(OperationResponse.void)
    }
  }

  private def _execution_context(
    principalId: String,
    attrs: Map[String, String],
    interpreter: Option[UnitOfWorkOp ~> Consequence] = None,
    realInterpreter: Boolean = false
  ): ExecutionContext = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace = new EntityStoreSpace()
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "action-call-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitofworksupplier = () => new org.goldenport.cncf.unitofwork.UnitOfWork(context),
      unitofworkinterpreterfn = interpreter.getOrElse(new (org.goldenport.cncf.unitofwork.UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: org.goldenport.cncf.unitofwork.UnitOfWorkOp[A]): Consequence[A] =
          if (realInterpreter)
            new org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter(new org.goldenport.cncf.unitofwork.UnitOfWork(context)).interpret(fa)
          else
            throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
      }),
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "action-call-spec-runtime"
    )
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId = PrincipalId(principalId)
          def attributes: Map[String, String] = attrs
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = Set.empty,
              level = SecurityLevel("user")
            )
          )
        )
      case _ =>
        context
    }
  }

  private def _program_call(
    program: ExecUowM[String]
  )(using ExecutionContext): ActionCall = {
    val act = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request(
          component = None,
          service = None,
          operation = "procedure_dsl",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }
    new ProcedureActionCall with ActionCall.Core.Holder {
      val core = ActionCall.Core(
        action = act,
        executionContext = summon[ExecutionContext],
        component = None,
        correlationId = None
      )

      def execute(): Consequence[OperationResponse] =
        executeProgram(program).map(OperationResponse.Scalar.apply)
    }
  }

  private def _internal_dsl_call(
    context: ExecutionContext,
    properties: List[Property]
  ): ActionCall = {
    val operationproperties = properties
    val act = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request(
          component = None,
          service = None,
          operation = "internal_dsl",
          arguments = Nil,
          switches = Nil,
          properties = operationproperties
        )
    }
    new ProcedureActionCall with ActionCall.Core.Holder {
      val core = ActionCall.Core(
        action = act,
        executionContext = context,
        component = None,
        correlationId = None
      )

      def execute(): Consequence[OperationResponse] =
        for {
          yaml <- parse_dsl_content_yaml(
            """title: 府中散歩
              |""".stripMargin
          )
          json <- parse_dsl_content_json(
            """{"location_investigation":{"recommended":{"lat":35.613528,"lon":139.393111}}}"""
          )
        } yield {
          val investigation = json.values.get("location_investigation").collect {
            case value: ConfigurationValue.ObjectValue => value
          }
          val recommended = investigation.flatMap(_.values.get("recommended")).collect {
            case value: ConfigurationValue.ObjectValue => value
          }
          OperationResponse.RecordResponse(_record(
            "message" -> config_string("demo.message").getOrElse(""),
            "runtimeMessage" -> execution_property_string("demo.message").getOrElse(""),
            "title" -> yaml.values.get("title").collect {
              case ConfigurationValue.StringValue(value) => value
            }.getOrElse(""),
            "lat" -> recommended.flatMap(_.values.get("lat")).collect {
              case ConfigurationValue.NumberValue(value) => value.toString
            }.getOrElse("")
          ))
      }
    }
  }

  private def _embedded_datastore_call(
    context: ExecutionContext
  ): ActionCall = {
    val act = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request(
          component = Some("embedded-spec"),
          service = None,
          operation = "embedded_datastore",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }
    val testcomponent = new EmbeddedSpecComponent()
    new FunctionalActionCall with ActionCall.Core.Holder with ActionCallEmbeddedDataStorePart {
      val core = ActionCall.Core(
        action = act,
        executionContext = context,
        component = Some(testcomponent),
        correlationId = None
      )

      protected def build_Program: ExecUowM[OperationResponse] =
        for {
          dir <- component_local_data_dir
          store <- embedded_datastore("gazetteer")
          _ <- embedded_datastore_migrate(store, Vector(
            """CREATE TABLE IF NOT EXISTS location_entry (
              |  id TEXT PRIMARY KEY,
              |  name TEXT NOT NULL,
              |  lat REAL NOT NULL,
              |  lon REAL NOT NULL
              |)""".stripMargin
          ))
          _ <- embedded_datastore_update(
            store,
            "INSERT OR REPLACE INTO location_entry (id, name, lat, lon) VALUES (?, ?, ?, ?)",
            Vector("nagaike-mitsuke-bridge", "長池見附橋", 35.61178333, 139.3925748)
          )
          rows <- embedded_datastore_read(
            store,
            "SELECT name, lat FROM location_entry WHERE id = ?",
            Vector("nagaike-mitsuke-bridge")
          )
        } yield {
          val row = rows.headOption.getOrElse(Record.empty)
          OperationResponse.RecordResponse(_record(
            "dir" -> dir.toString,
            "db" -> store.path.toString,
            "name" -> row.getString("name").getOrElse(""),
            "lat" -> row.getDouble("lat").map(_.toString).getOrElse("")
          ))
        }
    }
  }

  private final class EmbeddedSpecComponent extends Component

  private def _record(values: (String, Any)*): Record =
    Record(values.toVector.map { case (key, value) => Field(key, Field.Value.Single(value)) })

  private def _http_response_ok(): HttpResponse =
    HttpResponse.Text(
      HttpStatus.Ok,
      ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
      Bag.text("ok", StandardCharsets.UTF_8)
    )

  private final class _TestProviderBehavior(
    val behaviorCore: Behavior.Core
  ) extends ProviderBehavior {
    def run(): Consequence[String] =
      exec_from_calltree("provider:test", Map("provider" -> "test")) {
        Consequence.success("provider-ok")
      }.flatMap { _ =>
        http_get("/provider-dsl").map(_.status.code.toString)
      }.value.foldMap(behaviorCore.executionContext.runtime.unitOfWorkInterpreter).flatMap(identity)
  }
}
