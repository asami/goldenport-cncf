package org.goldenport.cncf.filestore

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.security.{KeyPair, KeyPairGenerator, Signature}
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.config.ComponentParameterKey
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, ScopeContext, SecurityContext, SecurityLevel, SubjectKind}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.oauth.OAuthConnectionRuntime
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 */
private[filestore] object GoogleServiceAccountFixture {
  val componentId: ComponentId = ComponentId("org.example.ServiceAccountLake")
  val instanceId: ComponentInstanceId = ComponentInstanceId(componentId, "one")
  val observed: Instant = Instant.parse("2026-10-24T00:00:00Z")
  val email: String = "synthetic@fixture.iam.gserviceaccount.com"
  val scope: String = "https://www.googleapis.com/auth/drive.readonly"
  val reference: FileStoreRef = FileStoreRef("service-account-store", "file_1")

  def keyPair(): KeyPair = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair()
  }
  def keyDocument(pair: KeyPair): Json = Json.obj("type" -> Json.fromString("service_account"),
    "private_key_id" -> Json.fromString("synthetic-key-id"), "client_email" -> Json.fromString(email),
    "token_uri" -> Json.fromString("https://oauth2.googleapis.com/token"),
    "private_key" -> Json.fromString("-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder.encodeToString(pair.getPrivate.getEncoded) + "\n-----END PRIVATE KEY-----\n"))
  def configuration(root: Path): Json = Json.obj("schema" -> Json.fromString("cncf.google-drive-service-account/1"),
    "connectionId" -> Json.fromString("shared-drive"), "allowedSubjects" -> Json.arr(Json.fromString("owner")),
    "scopes" -> Json.arr(Json.fromString(scope)), "credentialFile" -> Json.fromString(root.resolve("key.json").toString))
  def response(document: Json, code: Int = 200): HttpResponse = HttpResponse.Text(
    HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError),
    ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)), Bag.text(document.noSpaces))
  def tokenResponse: HttpResponse = response(Json.obj("access_token" -> Json.fromString("synthetic-service-token"),
    "token_type" -> Json.fromString("Bearer"), "expires_in" -> Json.fromInt(3600)))
  def decodeAssertion(body: String): String = body.split("&").map { field =>
    val parts = field.split("=", 2)
    URLDecoder.decode(parts(0), StandardCharsets.UTF_8) -> URLDecoder.decode(parts(1), StandardCharsets.UTF_8)
  }.toMap.apply("assertion")
  def claims(assertion: String): Json = parser.parse(new String(Base64.getUrlDecoder.decode(assertion.split("\\.")(1)), StandardCharsets.UTF_8)).toOption.get
  def signatureValid(assertion: String, pair: KeyPair): Boolean = {
    val parts = assertion.split("\\.")
    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(pair.getPublic)
    verifier.update((parts(0) + "." + parts(1)).getBytes(StandardCharsets.US_ASCII))
    verifier.verify(Base64.getUrlDecoder.decode(parts(2)))
  }
  def writePrivate(path: Path, text: String): Path = {
    Files.writeString(path, text)
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
    path
  }
  def withRoot[A](body: Path => A): A = {
    val base = Files.createDirectories(Paths.get("target", "google-service-account-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "case-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath()
    try body(root) finally Using.resource(Files.walk(root))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_)))
  }

  final case class Request(method: String, path: String, body: Option[String], headers: Map[String, String]) {
    override def toString: String = "ServiceAccountFixtureRequest(<redacted>)"
  }
  final class Driver extends HttpDriver {
    var token: HttpResponse = tokenResponse
    private var _requests = Vector.empty[Request]
    def requests: Vector[Request] = _requests
    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _requests :+= Request("GET", path, None, headers)
      response(Json.obj("id" -> Json.fromString("file_1"), "name" -> Json.fromString("original"),
        "mimeType" -> Json.fromString("image/png"), "trashed" -> Json.False, "parents" -> Json.arr(Json.fromString("parent_1")),
        "size" -> Json.fromString("4")))
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _requests :+= Request("POST", path, body, headers)
      token
    }
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      throw new AssertionError("No business write is part of credential acquisition")
  }
  final class MutableClock extends Clock {
    var current: Instant = observed
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    def instant(): Instant = current
  }
  final class NativeComponent extends GoogleDriveConnectionRuntime
  final class NativeFactory extends Component.Factory {
    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector(OAuthConnectionRuntime.configurationKey, GoogleDriveConnectionRuntime.configurationKey)
    protected def create_Component(params: ComponentCreate): Component = new NativeComponent
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      Component.Core.create(componentId.name, componentId, ComponentInstanceId.default(componentId), Protocol.empty, this)
  }
  final class Harness(root: Path, document: Option[Json] = None, oauth: Boolean = false, componentInstance: String = "one") {
    val pair: KeyPair = keyPair()
    val keyFile: Path = writePrivate(root.resolve("key.json"), keyDocument(pair).noSpaces)
    val source: Path = writePrivate(root.resolve("connection.json"), document.getOrElse(configuration(root)).noSpaces)
    val clock: MutableClock = new MutableClock
    val driver: Driver = new Driver
    val owner = TestComponentFactory.emptySubsystem("service-account-owner")
    private val _factory = new NativeFactory
    private val _settings = Map(GoogleDriveConnectionRuntime.configurationKey.name -> source.toUri.toString) ++
      Option.when(oauth) {
        val segment = NamingConventions.toNormalizedSegment(componentId.localId.value())
        val config = Json.obj("schema" -> Json.fromString("cncf.oauth-connection/1"), "connectionId" -> Json.fromString("shared-drive"),
          "ownerId" -> Json.fromString("owner"), "provider" -> Json.fromString("google-drive"), "clientId" -> Json.fromString("synthetic-client"),
          "clientSecret" -> Json.fromString("synthetic-client-secret"), "scopes" -> Json.arr(Json.fromString(scope)),
          "redirectUri" -> Json.fromString(s"http://127.0.0.1:8123/web/$segment/connections/shared-drive/callback"),
          "credentialFile" -> Json.fromString(root.resolve("grant.json").toString), "returnPath" -> Json.fromString("/web/home"))
        OAuthConnectionRuntime.configurationKey.name -> writePrivate(root.resolve("oauth.json"), config.noSpaces).toUri.toString
      }.toMap
    val created: Consequence[Component] = _factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(componentId.name), componentName = Some(componentId.name), schemaVersion = Some(3), componentId = Some(componentId))))
      .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, componentInstance,
        _settings, componentId = Some(componentId))))
    def context(subjectId: String = "owner", provider: Boolean = true, tokenOrigin: Boolean = true,
      componentOverride: Option[Component] = None): ExecutionContext = {
      val security = SecurityContext(new Principal {
        val id = PrincipalId(subjectId)
        val attributes: Map[String, String] = Map("authenticated" -> "true")
      }, Set.empty, SecurityLevel("user"), SubjectKind.User)
      if (provider) SecurityAuthenticationProvenance.providerAuthenticated(security)
      val base = ExecutionContext.withSecurityContext(ExecutionContext.create(clock), security)
      val origins = Vector("https://www.googleapis.com") ++ Option.when(tokenOrigin)("https://oauth2.googleapis.com")
      val admission = HttpExecutionAdmission.fromRuntimeC("https://www.googleapis.com/", HttpExecutionLimits(1000, 1000, 65536),
        Map(componentId -> HttpExecutionGrant(origins, Set(HttpExecutionMethod.Get, HttpExecutionMethod.Post)))).TAKE
      val parent = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      val component = componentOverride.getOrElse(created.TAKE)
      component.withScopeContext(parent)
      base.withScope(component.scopeContext)
    }
    def stat(subjectId: String = "owner", provider: Boolean = true, tokenOrigin: Boolean = true,
      connectionId: String = "shared-drive"): Consequence[FileStoreMetadata] = {
      val context = this.context(subjectId, provider, tokenOrigin)
      val uow = new UnitOfWork(context) { override def httpDriver: HttpDriver = driver }
      val store = GoogleDriveFileStore.createC(reference.store, GoogleDriveCredentials.managedConnectionC(connectionId).TAKE).TAKE
      new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(store.stat(reference))
    }
  }
}
