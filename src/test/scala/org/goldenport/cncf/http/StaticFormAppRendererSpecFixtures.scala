package org.goldenport.cncf.http

import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.{ZipEntry, ZipOutputStream}
import cats.data.State
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyVector
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{MediaType, Method, Request, Uri}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.{Argument, Property, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentFactory, ComponentId, ComponentInstanceId, ComponentletDescriptor}
import org.goldenport.cncf.testutil.DevelopmentRuntimeManifestFixture
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, PrincipalId, RuntimeContext}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.{
  EntityConcurrencyMetadata,
  EntityMutationAdapterDefaults,
  EntityPersistent,
  EntityRevisionBinding,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityRevisionTransport,
  EntityStoreSpace
}
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationDefinition, CmlOperationField, CmlOperationImageBinding, CmlOperationUpdateField}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.information.*
import org.goldenport.cncf.knowledge.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] trait StaticFormAppRendererSpecFixtures extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererHttpSpecFixtures {
  private[http] val _renderer = StaticFormAppRenderer()
  private[http] val _test_csrf_token = WebCsrf.issue(None)
  private[http] val _notice_board_component_name = ComponentId("org.goldenport.cncf.test.NoticeBoard").name
  private[http] val _embedded_notice_board_component_name = ComponentId("org.goldenport.cncf.test.EmbeddedNoticeBoard").name
  private[http] val _in_phase53_spec =
    afterWord("in spec:static-web-execution-context-projection, example:PM-53-01, rules:SWEP-3, phase:53")
  private[http] val _aes05b = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R13, phase:57.2, slice:AES-05B"
  )
  private[http] val _ic06e2 = afterWord(
    "in spec:phase-61.4-information-projection-compatibility, example:E2, rules:IC-06, phase:61.4, slice:IC-06B"
  )



  private[http] def _with_session(
    req: Request[IO],
    sessionid: String
  ): Request[IO] =
    req.putHeaders(
      org.http4s.Header.Raw(org.typelevel.ci.CIString("X-Textus-Session"), sessionid)
    )

  private[http] def _install_auth_session(
    subsystem: Subsystem,
    summary: AuthComponent.SessionSummary
  ): Unit =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH).foreach { component =>
      component.withPort(Component.Port.of(new AuthComponent.AuthService {
        def login(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          Consequence.success(summary)

        def logout(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.LogoutSummary] =
          Consequence.success(AuthComponent.LogoutSummary(loggedOut = true, request.sessionId))

        def currentSession(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          if (request.sessionId.contains(summary.sessionId.getOrElse("")))
            Consequence.success(summary)
          else
            Consequence.success(_anonymous_session_summary)
      }))
    }

  private[http] def _install_echoing_auth_session(
    subsystem: Subsystem,
    summary: AuthComponent.SessionSummary
  ): Unit =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH).foreach { component =>
      component.withPort(Component.Port.of(new AuthComponent.AuthService {
        def login(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          Consequence.success(summary.copy(attributes = summary.attributes ++ request.attributes))

        def logout(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.LogoutSummary] =
          Consequence.success(AuthComponent.LogoutSummary(loggedOut = true, request.sessionId))

        def currentSession(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          if (request.sessionId.contains(summary.sessionId.getOrElse("")))
            Consequence.success(summary.copy(attributes = summary.attributes ++ request.attributes))
          else
            Consequence.success(_anonymous_session_summary)
      }))
    }

  private[http] def _anonymous_session_summary: AuthComponent.SessionSummary =
    AuthComponent.SessionSummary(
      sessionId = None,
      principalId = Some("anonymous"),
      subjectKind = "Anonymous",
      securityLevel = "anonymous",
      capabilities = Vector.empty,
      authenticated = false,
      attributes = Map.empty
    )

  private[http] def _structured_conclusion(): Conclusion =
    Conclusion.simple("coded failure")

  private[http] final case class DataFixture(
    subsystem: Subsystem,
    runtime: GlobalRuntimeContext,
    datastorespace: DataStoreSpace
  )

  private[http] def _data_fixture(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Supported
  ): DataFixture = {
    val datastorespace = _data_store_space(totalcountcapability)
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId("audit")
    val _ = datastorespace.inject(cid, Record.create(Vector(
      "id" -> "audit_1",
      "action" -> "created",
      "actor" -> "alice"
    )))
    val _ = datastorespace.inject(cid, Record.create(Vector(
      "id" -> "audit_existing",
      "action" -> "updated",
      "actor" -> "bob"
    )))
    val runtime = GlobalRuntimeContext.create(
      "data-admin-test",
      RuntimeConfig.default.copy(dataStoreSpace = datastorespace),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      org.goldenport.cncf.context.ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val component = TestComponentFactory.create("notice_board", Protocol.empty)
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    DataFixture(subsystem, runtime, datastorespace)
  }

  private[http] def _data_store_space(
    totalcountcapability: TotalCountCapability
  ): DataStoreSpace =
    totalcountcapability match {
      case TotalCountCapability.Supported =>
        DataStoreSpace.default()
      case other =>
        new DataStoreSpace().addDataStore(
          new TotalCountCapabilityDataStore(DataStore.inMemorySearchable(), other)
        )
    }

  private[http] final class TotalCountCapabilityDataStore(
    delegate: SearchableDataStore,
    capability: TotalCountCapability
  ) extends SearchableDataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean =
      delegate.isAccept(cid)

    def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)

    def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)

    def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)

    def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)

    def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      delegate.search(collection, directive)

    override def totalCountCapability(collection: DataStore.CollectionId): TotalCountCapability =
      capability

    def prepare(tx: TransactionContext): PrepareResult =
      delegate.prepare(tx)

    def commit(tx: TransactionContext): Unit =
      delegate.commit(tx)

    def abort(tx: TransactionContext): Unit =
      delegate.abort(tx)
  }

  private[http] def _load_data_record(
    space: DataStoreSpace,
    collection: String,
    id: String
  ): Record = {
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId(collection)
    (for {
      ds <- space.dataStore(cid)
      entry <- DataStore.EntryId.parse(id)
      record <- ds.load(cid, entry).map(_.getOrElse(Record.empty))
    } yield record).toOption.getOrElse(Record.empty)
  }

  private[http] def _admin_record_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Record = {
    val response = _admin_response(subsystem, service, operation, args*)
    response.toOption.collect {
      case OperationResponse.RecordResponse(record) => record
    }.getOrElse(fail(s"admin.${service}.${operation} did not return RecordResponse: ${response}"))
  }

  private[http] def _tag_record_response(
    subsystem: Subsystem,
    operation: String,
    args: (String, String)*
  ): Record = {
    val request = GRequest.of(
      component = BuiltinComponentIdentity.TAG.name,
      service = "tag",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList
    )
    val response = subsystem.executeOperationResponse(request)
    response.toOption.collect {
      case OperationResponse.RecordResponse(record) => record
    }.getOrElse(fail(s"tag.tag.${operation} did not return RecordResponse: ${response}"))
  }

  private[http] def _page_body(
    consequence: Consequence[StaticFormAppRenderer.Page],
    label: String
  ): String =
    consequence match {
      case Consequence.Success(page) => page.body
      case Consequence.Failure(conclusion) => fail(s"$label is missing: ${conclusion.show}")
    }

  private[http] def _admin_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Consequence[OperationResponse] = {
    val request = GRequest.of(
      component = BuiltinComponentIdentity.ADMIN.name,
      service = service,
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList
    )
    subsystem.executeOperationResponse(request)
  }

  private[http] def _view_fixture_subsystem(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Unsupported,
    backingentityname: String = "notice",
    ambiguousbacking: Boolean = false
  ): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "notice_view",
            entityName = backingentityname,
            viewNames = Vector("default"),
            queries = Vector(ViewQueryDefinition("recent", Some("notice.updatedAt desc")))
          )
        )
    }
    _initialize_component("notice_board", component)
    given EntityPersistent[NoticeEntity] = _notice_persistent
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = NoticeEntity.collectionid,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "note"))
          )
        )
      )
    ))
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(Vector(NoticeEntity(_notice_entity_id_from_shortid("notice_1"), "notice", "view")))
    )
    if (ambiguousbacking)
      component.entitySpace.registerEntity(
        "notice",
        _notice_collection(Vector.empty, EntityCollectionId("sample", "other", "notice"))
      )
    val collection = new ViewCollection[String](
      new ViewBuilder[String] {
        def build(id: EntityId): Consequence[String] =
          Consequence.success(s"notice detail ${id.parts.entropy}")
      }
    )
    val browser = Browser.from(
      collection,
      _ => Consequence.success(Vector("notice summary", "notice next")),
      countfn = if (totalcountcapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
      totalCountCapabilityValue = totalcountcapability
    )
    component.viewSpace.register("notice_view", collection, browser)
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private[http] def _aggregate_fixture_subsystem(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Unsupported,
    backingentityname: String = "notice",
    ambiguousbacking: Boolean = false
  ): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = backingentityname,
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_protocol())
    given EntityPersistent[NoticeEntity] = _notice_persistent
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = NoticeEntity.collectionid,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "status"))
          )
        )
      )
    ))
    val aggregate = NoticeAggregate(_notice_entity_id_from_shortid("notice_1"), "notice aggregate")
    val nextaggregate = NoticeAggregate(_notice_entity_id_from_shortid("notice_2"), "notice next")
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(Vector(NoticeEntity(aggregate.id, "notice aggregate", "aggregate")))
    )
    if (ambiguousbacking)
      component.entitySpace.registerEntity(
        "notice",
        _notice_collection(Vector.empty, EntityCollectionId("sample", "other", "notice"))
      )
    component.aggregateSpace.register(
      "notice_aggregate",
      new AggregateCollection[NoticeAggregate](
        new AggregateBuilder[NoticeAggregate] {
          def build(id: EntityId): Consequence[NoticeAggregate] =
            Consequence.success(aggregate)
        },
        q => Consequence.success(org.goldenport.cncf.directive.Query.sliceValues(Vector(aggregate, nextaggregate), q.offset, q.limit)),
        countfn = if (totalcountcapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
        totalCountCapabilityValue = totalcountcapability
      )
    )
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private[http] def _aggregate_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("read-notice-aggregate"),
                NoopOperation("create-notice-aggregate"),
                NoopOperation("approve-notice-aggregate", Vector("id"))
              )
            )
          )
        )
      )
    )

  private[http] def _form_type_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {}
    _initialize_component("notice_board", component, _form_type_protocol())
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private[http] def _validation_hints_fixture(): (Subsystem, WebDescriptor) = {
    val component = new org.goldenport.cncf.component.Component() {}
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("validate-hints", Vector("code", "count"))
              )
            )
          )
        )
      )
    )
    _initialize_component("notice_board", component, protocol)
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    val selector = "notice-board.notice.validate-hints"
    val descriptor = WebDescriptor(
      expose = Map(selector -> WebDescriptor.Exposure.Protected),
      form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "code" -> WebDescriptor.FormControl(
            validation = WebValidationHints(minLength = Some(1), maxLength = Some(4))
          ),
          "count" -> WebDescriptor.FormControl(
            validation = WebValidationHints(min = Some(BigDecimal(-10)), max = Some(BigDecimal(200)))
          )
        )
      ))
    )
    subsystem -> descriptor
  }

  private[http] def _form_type_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("post-secret-notice", Vector("body", "accessToken"))
              )
            )
          )
        )
      )
    )

  private[http] def _aggregate_http_fixture_subsystem(
    configuration: Configuration = Configuration.empty,
    messagecatalogs: Vector[WebMessageCatalog] = Vector.empty
  ): Subsystem = {
    var ownersubsystem: Option[Subsystem] = None
    val component = new org.goldenport.cncf.component.Component() {
      override def subsystem: Option[Subsystem] = ownersubsystem
      override def webMessageCatalogs: Vector[WebMessageCatalog] = messagecatalogs

      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = "notice",
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_http_protocol())
    val subsystem = new Subsystem(
      name = "sample-web",
      configuration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    )
    ownersubsystem = Some(subsystem)
    subsystem.add(Vector(component))
    HttpRuntimeBindingAdmissionFixture.admit(subsystem)
    subsystem
  }

  private[http] def _with_multi_user_authentication(subsystem: Subsystem): Subsystem = {
    val ownersubsystem = subsystem
    val provider = new Component {
      override val core: Component.Core = Component.Core.create(
        "org.goldenport.cncf.test.StaticWebAuthentication",
        ComponentId("org.goldenport.cncf.test.StaticWebAuthentication"),
        ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.StaticWebAuthentication")),
        Protocol.empty
      )
      override def subsystem: Option[Subsystem] = Some(ownersubsystem)
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(new AuthenticationProvider {
        override val name: String = "static-web-authentication"
        override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
          Consequence.success(
            request.sessionId
              .map(id => AuthenticationResult(PrincipalId(s"static-web-$id")))
              .orElse(request.accessToken.map(id => AuthenticationResult(PrincipalId(s"static-web-$id"))))
          )
        override def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
          authenticate(request)
      })
    }
    provider.withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = "StaticWebAuthentication",
      version = "0.0.0",
      component = Some("static-web-authentication")
    ))
    subsystem.add(provider)
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = Path.of("build.sbt").toAbsolutePath,
      subsystemName = "sample-web",
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(
        GenericSubsystemAuthenticationBinding(
          convention = Some("disabled"),
          fallbackPrivilege = Some("disabled"),
          providers = Vector(GenericSubsystemAuthenticationProviderBinding(
            name = "static-web-authentication",
            component = "static-web-authentication",
            enabled = Some(true)
          ))
        )
      )))
    ))
    subsystem
  }

  private[http] def _aggregate_http_fixture_subsystem_with_componentlet_metadata_only(
    configuration: Configuration = Configuration.empty
  ): Subsystem = {
    val subsystem = _aggregate_http_fixture_subsystem(configuration)
    subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).foreach { component =>
      val componentlets = Vector(
        ComponentletDescriptor(
          name = "notice-admin",
          kind = Some("componentlet"),
          archiveScope = Some("car-bundled")
        )
      )
      component.withComponentDescriptors(
        if (component.componentDescriptors.nonEmpty)
          component.componentDescriptors.map(_.copy(componentlets = componentlets))
        else
          Vector(ComponentDescriptor(
            name = Some(component.name),
            componentName = Some(component.name),
            componentlets = componentlets
          ))
      )
    }
    subsystem
  }

  private[http] def _aggregate_http_fixture_subsystem_with_componentlets(
    configuration: Configuration = Configuration.empty
  ): Subsystem = {
    val subsystem = _aggregate_http_fixture_subsystem_with_componentlet_metadata_only(configuration)
    val component = new org.goldenport.cncf.component.Component() {}
    _initialize_component_with_id("notice-admin", "notice_admin", component, _aggregate_http_protocol())
    subsystem.add(Vector(component))
  }

  private[http] def _web_template_fixture_root(
    filename: String,
    content: String
  ): java.nio.file.Path = {
    val root = Files.createTempDirectory("cncf-web-template-")
    Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
    Files.writeString(root.resolve(filename), content, StandardCharsets.UTF_8)
    root
  }

  private[http] def _web_archive_fixture(
    filename: String,
    entries: Vector[(String, String)]
  ): Path = {
    val root = Files.createTempDirectory("cncf-web-archive-")
    val path = root.resolve(filename)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach {
        case (name, content) =>
          out.putNextEntry(new ZipEntry(name))
          out.write(content.getBytes(StandardCharsets.UTF_8))
          out.closeEntry()
      }
    } finally {
      out.close()
    }
    path
  }

  private[http] def _aggregate_http_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                SuccessfulAggregateOperation("create-notice-aggregate", "title", "aggregate-created"),
                SuccessfulAggregateOperation("approve-notice-aggregate", "id", "aggregate-updated")
              )
            )
          )
        )
      ),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector(RestIngress())),
        egresses = EgressCollection(Vector(RestEgress())),
        projections = ProjectionCollection()
      )
    )

  private[http] def _initialize_component(
    name: String,
    component: org.goldenport.cncf.component.Component,
    protocol: Protocol = Protocol.empty
  ): org.goldenport.cncf.component.Component = {
    _initialize_component_with_id(name, name, component, protocol)
  }

  private[http] def _initialize_component_with_id(
    name: String,
    componentidname: String,
    component: org.goldenport.cncf.component.Component,
    protocol: Protocol = Protocol.empty,
    origin: org.goldenport.cncf.component.ComponentOrigin = org.goldenport.cncf.component.ComponentOrigin.Builtin
  ): org.goldenport.cncf.component.Component = {
    val componentid = TestComponentFactory.componentId(name)
    val instanceid = org.goldenport.cncf.component.ComponentInstanceId(componentid, componentidname)
    val factory = new org.goldenport.cncf.component.Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: org.goldenport.cncf.component.ComponentCreate): org.goldenport.cncf.component.Component =
        component

      override protected def create_Core(
        params: org.goldenport.cncf.component.ComponentCreate,
        comp: org.goldenport.cncf.component.Component
      ): org.goldenport.cncf.component.Component.Core =
        org.goldenport.cncf.component.Component.Core.create(componentid.name, componentid, instanceid, protocol, this)
    }
    val core = org.goldenport.cncf.component.Component.Core.create(componentid.name, componentid, instanceid, protocol, factory)
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        TestComponentFactory.emptySubsystem("test"),
        core,
        origin
      )
    )
  }

  private[http] def _management_console_fixture_subsystem(
    configuration: Configuration = Configuration.empty,
    schema: Schema = _schema("id", "title", "author"),
    viewfields: Map[String, Vector[String]] = Map.empty,
    relationships: Vector[CmlEntityRelationshipDefinition] = Vector.empty
  ): Subsystem = {
    val resolvedconfiguration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    val runtimeconfig = RuntimeConfig.default.copy(
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = EntityStoreSpace.create(resolvedconfiguration)
    )
    val runtime = GlobalRuntimeContext.create(
      "static-form-app-renderer-spec",
      runtimeconfig,
      resolvedconfiguration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    given EntityPersistent[NoticeEntity] = _notice_persistent
    val cid = NoticeEntity.collectionid
    val descriptor = ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = cid,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(schema),
          revisionModelKind = Some(EntityRevisionModelKind.NonSimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Detached)
        )
      )
    )
    val component =
      if (viewfields.isEmpty && relationships.isEmpty) {
        TestComponentFactory.create("notice_board", Protocol.empty)
      } else {
        val c = new org.goldenport.cncf.component.Component() {
          override def viewDefinitions: Vector[ViewDefinition] =
            Vector(
              ViewDefinition(
                name = "notice_view",
                entityName = "notice",
                viewNames = viewfields.keys.toVector,
                viewFields = viewfields
              )
            )
          override def relationshipDefinitions: Vector[CmlEntityRelationshipDefinition] =
            relationships
        }
        _initialize_component("notice_board", c, Protocol.empty)
      }
    component.withComponentDescriptors(Vector(descriptor))
    val notices = Vector(
      NoticeEntity(
        _new_notice_entity_id(entropy = "board_update"),
        "board update",
        "alice"
      ),
      NoticeEntity(
        _new_notice_entity_id(entropy = "board_followup"),
        "board followup",
        "bob"
      )
    )
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(notices)
    )
    val subsystem = HttpRuntimeBindingAdmissionFixture.defaultWithScope(
      runtime,
      Some(org.goldenport.cncf.cli.RunMode.Server),
      resolvedconfiguration
    ).add(Vector(component))
    given ExecutionContext =
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
        .getOrElse(fail("admin component is missing"))
        .logic.executionContext()
    EntityRevisionSpecSupport.registerRevisionBinding(
      summon[ExecutionContext],
      cid,
      _notice_persistent,
      EntityRevisionRepresentation.Detached
    )
    val noticecollection =
      component.entitySpace.entity[NoticeEntity]("notice")
    notices.foreach { notice =>
      val authorization =
        org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("notice"),
          collectionName = Some(cid.name),
          targetId = Some(notice.id),
          accessKind = "update",
          accessMode = org.goldenport.cncf.security.EntityAccessMode.System
        )
      new org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter(
        new org.goldenport.cncf.unitofwork.UnitOfWork(summon[ExecutionContext])
      ).interpret(
        org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreSaveUnversioned(
          notice,
          org.goldenport.cncf.entity.EntityUnversionedMutationPurpose.SeedImport,
          _notice_persistent,
          Some(authorization)
        )
      ).getOrElse(fail(s"notice fixture seed failed: ${notice.id.print}"))
      noticecollection.put(notice)
    }
    subsystem
  }

  private[http] def _embedded_revision_fixture_subsystem(): Subsystem = {
    val resolvedconfiguration =
      ResolvedConfiguration(
        Configuration.empty,
        ConfigurationTrace.empty
      )
    val runtimeconfig = RuntimeConfig.default.copy(
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace =
        EntityStoreSpace.create(resolvedconfiguration)
    )
    val runtime = GlobalRuntimeContext.create(
      "embedded-revision-admin-spec",
      runtimeconfig,
      resolvedconfiguration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val component =
      TestComponentFactory.create(
        "embedded_notice_board",
        Protocol.empty
      )
    val persistent = _embedded_notice_persistent
    val cid = EmbeddedNoticeEntity.collectionid
    val descriptor = ComponentDescriptor(
      componentName = Some("embedded_notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = cid,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy =
            PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(_schema("id", "title")),
          revisionModelKind =
            Some(EntityRevisionModelKind.SimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Embedded)
        )
      )
    )
    component.withComponentDescriptors(Vector(descriptor))
    given EntityPersistent[EmbeddedNoticeEntity] = persistent
    val store = new EntityRealm[EmbeddedNoticeEntity](
      entityName = "notice",
      loader = EntityLoader[EmbeddedNoticeEntity](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memory = new PartitionedMemoryRealm[EmbeddedNoticeEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    component.entitySpace.registerEntity(
      "notice",
      new EntityCollection(
        EntityDescriptor(
          collectionId = cid,
          plan = EntityRuntimePlan(
            entityName = "notice",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = None,
            partitionStrategy =
              PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ),
          persistent = persistent,
          revisionBinding = Some(
            EntityRevisionBinding(
              EntityRevisionRepresentation.Embedded
            )
          )
        ),
        EntityStorage(store, Some(memory))
      )
    )
    HttpRuntimeBindingAdmissionFixture
      .defaultWithScope(
        runtime,
        Some(org.goldenport.cncf.cli.RunMode.Server),
        resolvedconfiguration
      )
      .add(Vector(component))
  }

  private[http] def _entity_schema_web_descriptor_fixture(): (Subsystem, WebDescriptor) = {
    val descriptor = ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = EntityCollectionId("sys", "sys", "notice"),
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(Schema(Vector(
            Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
            Column(
              BaseContent.Builder("body").label("Notice body").build(),
              ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
              web = WebColumn(
                controlType = Some("textarea"),
                placeholder = Some("Schema body placeholder."),
                help = Some("Schema body help."),
                required = Some(true)
              )
            ),
            Column(
              BaseContent.Builder("status").label("Publication status").build(),
              ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
              web = WebColumn(
                controlType = Some("select"),
                values = Vector("draft", "published"),
                required = Some(true)
              )
            )
          )))
        )
      )
    )
    val component = TestComponentFactory
      .create("notice_board", Protocol.empty)
      .withComponentDescriptors(Vector(descriptor))
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    val webdescriptor = WebDescriptor(admin = Map(
      "notice-board.entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
        WebDescriptor.AdminField("id"),
        WebDescriptor.AdminField(
          "body",
          WebDescriptor.FormControl(
            placeholder = Some("Descriptor body placeholder."),
            help = Some("Descriptor body help.")
          )
        ),
        WebDescriptor.AdminField(
          "status",
          WebDescriptor.FormControl(
            values = Vector("draft", "published", "archived"),
            required = Some(false)
          )
        )
      ))
    ))
    subsystem -> webdescriptor
  }

  private[http] def _data_schema_web_descriptor(
    includenote: Boolean = true
  ): WebDescriptor = {
    val fields = Vector(
      WebDescriptor.AdminField("id"),
      WebDescriptor.AdminField(
        "action",
        WebDescriptor.FormControl(
          controlType = Some("select"),
          values = Vector("created", "updated")
        )
      ),
      WebDescriptor.AdminField(
        "actor",
        WebDescriptor.FormControl(
          required = Some(true),
          placeholder = Some("Descriptor actor placeholder."),
          help = Some("Descriptor actor help.")
        )
      )
    ) ++ (
      if (includenote)
        Vector(WebDescriptor.AdminField("note", WebDescriptor.FormControl(controlType = Some("textarea"))))
      else
        Vector.empty
    )
    WebDescriptor(
      admin = Map(
        "data.audit" -> WebDescriptor.AdminSurface(fields = fields)
      )
    )
  }

  private[http] def _notice_fixture_component(
    subsystem: Subsystem
  ): Component =
    subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).getOrElse(fail("notice fixture component is missing"))

  private[http] def _schema(names: String*): Schema =
    Schema(names.toVector.map { name =>
      Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
    })

  private[http] def _schema(fields: Vector[(String, WebColumn)]): Schema =
    Schema(fields.toVector.map {
      case (name, web) =>
        Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One), web = web)
    })

  private[http] def _notice_collection(
    entities: Vector[NoticeEntity],
    collectionid: EntityCollectionId = NoticeEntity.collectionid
  )(using EntityPersistent[NoticeEntity]): EntityCollection[NoticeEntity] = {
    val store = new EntityRealm[NoticeEntity](
      entityName = "notice",
      loader = EntityLoader[NoticeEntity](id => entities.find(_.id == id)),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memory = new PartitionedMemoryRealm[NoticeEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = collectionid,
      plan = EntityRuntimePlan(
        entityName = "notice",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 100
      ),
      persistent = summon[EntityPersistent[NoticeEntity]],
      revisionBinding = Some(
        EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      )
    )
    val collection = new EntityCollection[NoticeEntity](
      descriptor = descriptor,
      storage = EntityStorage(store, Some(memory))
    )
    entities.foreach(collection.put)
    collection
  }

  private[http] def _notice_persistent: EntityPersistent[NoticeEntity] =
    new EntityPersistent[NoticeEntity] {
      def id(e: NoticeEntity): EntityId = e.id
      def toRecord(e: NoticeEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[NoticeEntity] =
        Consequence.success(
          NoticeEntity(
            _notice_entity_id(r.getAny("id")),
            r.getString("title").getOrElse(""),
            r.getString("author").getOrElse("")
          )
        )
    }

  private[http] def _embedded_notice_persistent
      : EntityPersistent[EmbeddedNoticeEntity] =
    new EntityPersistent[EmbeddedNoticeEntity] {
      def id(e: EmbeddedNoticeEntity): EntityId =
        e.id

      def toRecord(e: EmbeddedNoticeEntity): Record =
        Record.dataAuto(
          "id" -> e.id,
          "revision" -> e.revision.value,
          "title" -> e.title
        )

      def fromRecord(
        r: Record
      ): Consequence[EmbeddedNoticeEntity] =
        for {
          idoption <- r.getAsC[EntityId]("id")
          id <- Consequence.fromOption(
            idoption,
            "id is required"
          )
          revisionvalue <- Consequence.fromOption(
            r.getLong("revision"),
            "revision is required"
          )
          revision <- EntityRevision.createC(revisionvalue)
          title <- Consequence.fromOption(
            r.getString("title"),
            "title is required"
          )
        } yield EmbeddedNoticeEntity(id, revision, title)
    }

  private[http] def _notice_entity_id(value: Option[Any]): EntityId =
    value match {
      case Some(id: EntityId) => id
      case Some(text: String) =>
        EntityId.parse(text).toOption.getOrElse(_new_notice_entity_id(entropy = "notice_entity_id_fallback"))
      case Some(other) =>
        EntityId.parse(other.toString).toOption.getOrElse(_new_notice_entity_id(entropy = "notice_entity_id_fallback"))
      case None =>
        _new_notice_entity_id(entropy = "notice_entity_id_fallback")
    }

  private[http] def _new_notice_entity_id(entropy: String): EntityId = {
    val collection = NoticeEntity.collectionid
    val generated = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(collection.major, collection.minor, collection, entropy = entropy)
    EntityId.parse(generated.value).getOrElse(fail("notice entity id generation failed"))
  }

  private[http] def _notice_entity_id_from_shortid(shortid: String): EntityId = {
    val collection = NoticeEntity.collectionid
    val generated = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
      collection.major,
      collection.minor,
      collection,entropy = shortid)
    EntityId.parse(generated.value).getOrElse(fail("notice entity id generation failed"))
  }

  private[http] def _load_notice_store_record(
    subsystem: Subsystem,
    id: EntityId
  ): Record = {
    given ExecutionContext = _notice_fixture_component(subsystem).logic.executionContext()
    val collectionid = DataStore.CollectionId.EntityStore(id.collection)
    val entryid = DataStore.EntryId(id)
    val loaded = for {
      ds <- summon[ExecutionContext].dataStoreSpace.dataStore(collectionid)
      record <- ds.load(collectionid, entryid)
    } yield record
    loaded.toOption.flatten.getOrElse(fail(s"notice store record is missing: ${id.print}"))
  }

  private[http] def _notice_entity_version(
      subsystem: Subsystem,
      id: String
  ): String = {
    val collection =
      _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
    val entityid =
      collection.resolveEntityId(id).getOrElse(fail(s"notice entity id is missing: ${id}"))
    val binding = collection.descriptor.revisionBinding
      .getOrElse(fail("notice revision binding is missing"))
    _success(binding.revision(_load_notice_store_record(subsystem, entityid)))
      .value
      .toString
  }

  private[http] def _blob_request(
    operation: String,
    properties: Property*
  ): GRequest =
    _blob_request(operation, Nil, properties.toList)

  private[http] def _blob_request(
    operation: String,
    arguments: List[Argument],
    properties: List[Property]
  ): GRequest =
    GRequest.of(
      component = BuiltinComponentIdentity.BLOB.name,
      service = "blob",
      operation = operation,
      arguments = arguments,
      properties = properties
    )

  private[http] def _blob_record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected Blob record response but got $other")
    }

  private[http] def _register_external_blob(
    subsystem: Subsystem,
    filename: String,
    url: String
  ): String = {
    val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
      "register_blob",
      Property("sourceMode", "external_url", None),
      Property("kind", "image", None),
      Property("filename", filename, None),
      Property("contentType", ContentType.IMAGE_PNG.header, None),
      Property("externalUrl", url, None)
    ))))
    blob.getString("id").getOrElse(fail("Blob id is missing"))
  }

  private[http] def _create_legacy_external_blob(
    subsystem: Subsystem,
    filename: String,
    url: String
  ): String = {
    val component = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).getOrElse(fail("Blob component is missing"))
    given ExecutionContext = component.logic.executionContext()
    val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(BlobRepository.CollectionId.major, BlobRepository.CollectionId.minor, BlobRepository.CollectionId, entropy = BlobRepository.CollectionId.minor)
    val created = _success(BlobRepository.entityStore().create(
      BlobCreate(
        id = id,
        kind = BlobKind.Image,
        sourceMode = BlobSourceMode.ExternalUrl,
        filename = Some(filename),
        contentType = Some(ContentType.IMAGE_PNG),
        byteSize = None,
        digest = None,
        storageRef = None,
        externalUrl = Some(url),
        accessUrl = BlobAccessUrl(
          displayUrl = url,
          downloadUrl = url,
          urlSource = BlobAccessUrlSource.Backend
        )
      )
    ))
    created.id.value
  }

  private[http] def _blob_records(
    record: Record
  ): Vector[Record] =
    record.getAny("images").collect {
      case xs: Seq[?] => xs.collect { case r: Record => r }.toVector
    }.getOrElse(Vector.empty)

  private[http] def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(v) => v
      case Consequence.Failure(c) => fail(s"unexpected failure: $c")
    }
}
