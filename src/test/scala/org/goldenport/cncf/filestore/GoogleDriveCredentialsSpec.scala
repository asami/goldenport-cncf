package org.goldenport.cncf.filestore

import java.io.IOException
import java.nio.charset.StandardCharsets
import cats.free.Free
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.observability.{ConclusionDiagnostics, ConclusionDisclosure}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class GoogleDriveCredentialsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  "Google Drive outbound credentials" should {
    "pure private construction" which {
      "supplies fixed bearer authorization without acquiring an HTTP driver" in {
        Given("a synthetic RFC6750 token and a counted native runtime")
        val fixture = new Harness(_ => throw new AssertionError("Fixed credentials must not request HTTP"))
        When("the pure factory constructs credentials and its native authorization program is interpreted")
        val credentials = GoogleDriveCredentials.accessTokenC("synthetic._~+/token-==").TAKE
        val program = credentials._authorization
        val before = fixture.acquisitions
        val result = fixture.evaluate(program).TAKE
        Then("factory and program construction perform no HTTP, and interpretation returns only the bearer header")
        before shouldBe 0
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
        result shouldBe Map("Authorization" -> "Bearer synthetic._~+/token-==")
        credentials.toString shouldBe "GoogleDriveCredentials(<redacted>)"
        credentials.asInstanceOf[Any].isInstanceOf[Product] shouldBe false
        credentials.getClass.getMethods.map(_.getName).toSet.intersect(
          Set("accessToken", "refreshToken", "clientSecret", "copy", "productIterator")) shouldBe empty
      }

      "rejects malformed token and refresh configuration with one fixed redacted failure" in {
        Given("empty, non-ASCII, whitespace, control, comma, misplaced padding and oversized synthetic credentials")
        val invalidtokens = Vector("", " ", "a\tb", "a\nb", "a,b", "λ", "=abc", "a=b", "a" * 16385)
        val invalidvalues = Vector("", "a b", "a\u007fb", "é", "x" * 16385)
        val fixture = new Harness(_ => throw new AssertionError("Factories must not request HTTP"))
        When("both credential factories validate the malformed values")
        val outcomes = invalidtokens.map(GoogleDriveCredentials.accessTokenC) ++ invalidvalues.flatMap { value =>
          Vector(GoogleDriveCredentials.refreshTokenC(value, None, "synthetic-refresh"),
            GoogleDriveCredentials.refreshTokenC("synthetic-client", Some(value), "synthetic-refresh"),
            GoogleDriveCredentials.refreshTokenC("synthetic-client", None, value))
        }
        Then("every failure has the same structured argument category and contains no submitted value")
        val expected = failure(Consequence.argumentInvalid("Invalid Google Drive credential configuration"))
        outcomes.foreach { outcome =>
          val actual = failure(outcome)
          actual.status shouldBe expected.status
          actual.observation.taxonomy shouldBe expected.observation.taxonomy
          actual.cause.getMessage shouldBe Some("Invalid Google Drive credential configuration")
          disclosures(actual).foreach(output => output should not include "synthetic-refresh")
        }
        fixture.acquisitions shouldBe 0
      }

      "accepts the bounded RFC6750 token space without discards or HTTP" in {
        Given("bounded nonempty b64token alphabets and optional terminal padding")
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~+/".toVector
        val tokens = for {
          size <- Gen.choose(1, 80)
          chars <- Gen.listOfN(size, Gen.oneOf(alphabet))
          padding <- Gen.choose(0, 3)
        } yield chars.mkString + ("=" * padding)
        When("at least one hundred pure constructions cover the admitted token space")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(tokens) { token =>
          GoogleDriveCredentials.accessTokenC(token).toOption.exists(_.toString == "GoogleDriveCredentials(<redacted>)")
        })
        Then("all generated credentials are admitted without exposing their values")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "one refresh per interpreted authorization" which {
      "form-escapes every field and accepts a case-insensitive Bearer with integral positive expiry" in {
        Given("printable synthetic refresh inputs with form delimiters and a valid provider response")
        val fixture = new Harness(_ => jsonResponse(tokenJson("synthetic-refreshed")))
        val credentials = GoogleDriveCredentials.refreshTokenC("client+id/a?b=c&d%20", Some("secret&=+/%"), "refresh/+&=%").TAKE
        val display = credentials.toString
        When("the same immutable credential value supplies two separate logical authorizations")
        val first = fixture.evaluate(credentials._authorization).TAKE
        val second = fixture.evaluate(credentials._authorization).TAKE
        Then("exactly one native POST per interpretation carries escaped values only in the form body")
        first shouldBe Map("Authorization" -> "Bearer synthetic-refreshed")
        second shouldBe first
        fixture.requests.map(_.method) shouldBe Vector("POST", "POST")
        fixture.requests.foreach { request =>
          request.path shouldBe oauthUrl
          request.path should not include "?"
          request.text shouldBe Some("client_id=client%2Bid%2Fa%3Fb%3Dc%26d%2520&client_secret=secret%26%3D%2B%2F%25&refresh_token=refresh%2F%2B%26%3D%25&grant_type=refresh_token")
          request.headers shouldBe Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
          request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("16384")
        }
        display shouldBe "GoogleDriveCredentials(<redacted>)"
        GoogleDriveCredentials.toString should not include "secret&=+/%"
      }

      "omits an absent client secret and ignores response refresh material" in {
        Given("refresh credentials without a client secret and a response suggesting different refresh material")
        val fixture = new Harness(_ => jsonResponse(tokenJson("synthetic-access").deepMerge(
          Json.obj("refresh_token" -> Json.fromString("provider-replacement-marker")))))
        val credentials = GoogleDriveCredentials.refreshTokenC("synthetic-client", None, "configured-refresh-marker").TAKE
        When("two independent authorizations are interpreted")
        val results = Vector.fill(2)(fixture.evaluate(credentials._authorization).TAKE)
        Then("the configured refresh value is reused without persistence, rotation or a secret field")
        results shouldBe Vector.fill(2)(Map("Authorization" -> "Bearer synthetic-access"))
        fixture.requests.foreach { request =>
          request.text shouldBe Some("client_id=synthetic-client&refresh_token=configured-refresh-marker&grant_type=refresh_token")
          request.text.get should not include "provider-replacement-marker"
          request.text.get should not include "client_secret"
        }
      }

      "rejects wrong primitives, missing fields, malformed JSON and invalid expiry or token type" in {
        Given("successful HTTP responses whose credential shape or MIME violates the provider contract")
        val valid = tokenJson("synthetic-access")
        val invalid = Vector(Json.Null, Json.arr(valid), Json.obj(),
          valid.deepMerge(Json.obj("access_token" -> Json.Null)), valid.deepMerge(Json.obj("access_token" -> Json.fromString("bad token"))),
          valid.deepMerge(Json.obj("token_type" -> Json.fromString("Basic"))), valid.deepMerge(Json.obj("token_type" -> Json.Null))) ++
          Vector(Json.Null, Json.fromString("3600"), Json.fromInt(0), Json.fromInt(-1), Json.fromDoubleOrNull(1.5), Json.True, Json.obj())
            .map(value => valid.deepMerge(Json.obj("expires_in" -> value))) ++
          Vector(Json.obj("access_token" -> Json.fromString("synthetic-access"), "token_type" -> Json.fromString("Bearer")))
        val responses = invalid.map(jsonResponse(_)) ++ Vector(textResponse("{provider-body-marker"),
          textResponse(valid.noSpaces, mime = "text/plain"))
        When("each malformed successful response is interpreted once")
        val outcomes = responses.map { response =>
          val fixture = new Harness(_ => response)
          val outcome = fixture.evaluate(refreshCredentials()._authorization)
          (outcome, fixture.requests.size)
        }
        Then("each outcome is a fixed resource failure without body echo or another request")
        outcomes.foreach { case (outcome, count) =>
          failure(outcome).cause.getMessage shouldBe Some("Google Drive credential response is invalid")
          failure(outcome).observation.taxonomy shouldBe failure(Consequence.resourceInvalid("fixed")).observation.taxonomy
          count shouldBe 1
          disclosures(failure(outcome)).foreach(output => output should not include "provider-body-marker")
        }
      }

      "maps denied and unavailable provider statuses once using the public HTTP status representation" in {
        Vector(400, 401, 403, 404, 429, 500, 503, 302, 201).foreach { code =>
          Given(s"a provider HTTP status $code represented by the existing native HttpStatus API")
          val response = textResponse("provider-body-marker", code)
          val fixture = new Harness(_ => response)
          val expected = response.code match {
            case 400 | 401 | 403 => Consequence.securityPermissionDenied("Google Drive credential refresh was denied")
            case value if value == 429 || value >= 500 => Consequence.serviceUnavailable("Google Drive credential refresh is unavailable")
            case _ => Consequence.resourceInvalid("Google Drive credential response is invalid")
          }
          When("the refresh response is interpreted")
          val actual = failure(fixture.evaluate(refreshCredentials()._authorization))
          Then("the fixed category and message are retained without retry or provider echo")
          actual.status shouldBe failure(expected).status
          actual.observation.taxonomy shouldBe failure(expected).observation.taxonomy
          actual.cause.getMessage shouldBe failure(expected).cause.getMessage
          fixture.requests.size shouldBe 1
          disclosures(actual).foreach(output => output should not include "provider-body-marker")
        }
      }
    }

    "native authority and diagnostics" which {
      "denies an ungranted OAuth host before driver acquisition without relabeling its Conclusion" in {
        Given("a real Component scope whose runtime grants only the Drive origin")
        val fixture = new Harness(_ => throw new AssertionError("OAuth host was not admitted"), origins = Vector(driveOrigin))
        val native = failure(HttpExecutionAdmission.resolveC(fixture.context, HttpExecutionMethod.Post, oauthUrl,
          Vector(Property("http.max-response-bytes", "16384", None))))
        When("refresh credentials request their independent native OAuth POST")
        val actual = failure(fixture.evaluate(refreshCredentials()._authorization))
        Then("the original native admission status, taxonomy and cause survive with no driver call")
        actual.status shouldBe native.status
        actual.observation.isMatch(native.observation) shouldBe true
        actual.interpretation shouldBe native.interpretation
        actual.disposition shouldBe native.disposition
        ConclusionDiagnostics.classify(actual).policy shouldBe Some("http.execution.destination")
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
      }

      "preserves native transport failure and excludes outbound secrets from enabled calltree and disclosure projections" in {
        Given("an enabled real calltree and a controlled OAuth driver exception containing synthetic secret/body/session markers")
        val markers = Vector("synthetic-access-marker", "synthetic-secret-marker", "synthetic-refresh-marker",
          "provider-body-marker", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session-marker")
        val error = new IOException(markers.mkString(" "))
        val fixture = new Harness(_ => throw error, enabled = true)
        val credentials = GoogleDriveCredentials.refreshTokenC("synthetic-client", Some(markers(1)), markers(2)).TAKE
        When("the native interpreter contains the failed token exchange")
        val actual = failure(fixture.evaluate(credentials._authorization))
        val outputs = disclosures(actual)
        val trace = fixture.trace
        Then("the transport exception and its native taxonomy remain, while every existing projection omits sensitive markers")
        actual.getException.get should be theSameInstanceAs error
        actual.status shouldBe Conclusion.from(error).status
        actual.observation.taxonomy shouldBe Conclusion.from(error).observation.taxonomy
        fixture.requests.size shouldBe 1
        trace should include ("uow:http:post")
        (outputs :+ trace).foreach(output => markers.foreach(marker => output should not include marker))
        outputs.foreach { output =>
          val json = parser.parse(output).toOption.get
          json.hcursor.downField("status").focus shouldBe Some(jsonRecord(actual.status.toRecord))
          json.hcursor.downField("observation").downField("taxonomy").focus shouldBe Some(jsonRecord(actual.observation.taxonomy.toRecord))
        }
      }
    }
  }
}

/** Shared deterministic native-runtime fixtures; no alternative interpreter or cloud access. */
private[filestore] object GoogleDriveFixture {
  val driveOrigin: String = "https://www.googleapis.com"
  val oauthUrl: String = "https://oauth2.googleapis.com/token"
  val fields: String = "id,name,mimeType,size,parents,trashed,version"
  val folderMime: String = "application/vnd.google-apps.folder"
  val sessionUrl: String = s"$driveOrigin/upload/drive/v3/files?uploadType=resumable&upload_id=synthetic-session-marker"
  val parent: FileStoreRef = FileStoreRef("drive-main", "parent_01")
  val reference: FileStoreRef = FileStoreRef("drive-main", "file_01")

  final case class Request(method: String, path: String, text: Option[String], bag: Option[Bag],
    headers: Map[String, String], properties: Vector[Property])

  final class Harness(answer: Request => HttpResponse,
    origins: Vector[String] = Vector(driveOrigin, "https://oauth2.googleapis.com"),
    methods: Set[HttpExecutionMethod] = HttpExecutionMethod.values.toSet,
    bytes: Long = 1048576, enabled: Boolean = false) {
    private var _requests = Vector.empty[Request]
    private var _acquisitions = 0
    val context: ExecutionContext = {
      val owner = ComponentId("org.example.GoogleDriveOwner")
      val admission = HttpExecutionAdmission.fromRuntimeC(driveOrigin + "/", HttpExecutionLimits(1000, 1000, bytes),
        Map(owner -> HttpExecutionGrant(origins, methods))).TAKE
      val base = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled)
      val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      component.withScopeContext(root)
      base.withScope(component.scopeContext)
    }
    private val _driver = new HttpDriver {
      def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        _record(Request("GET", path, None, None, headers, properties))
      def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        _record(Request("POST", path, body, None, headers, properties))
      def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("Drive must use native binary PUT")
      override def putBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        _record(Request("PUT", path, None, body, headers, properties))
    }
    def requests: Vector[Request] = _requests
    def acquisitions: Int = _acquisitions
    def trace: String = context.cncfCore.observability.callTreeContext.build().map(tree => RecordEncoder.json(tree.toRecord)).getOrElse("")
    def evaluate[A](program: ExecUowM[A]): Consequence[A] = {
      val uow = new UnitOfWork(context) {
        override def httpDriver: HttpDriver = { _acquisitions += 1; _driver }
      }
      new UnitOfWorkInterpreter(uow).run(program)
    }
    private def _record(request: Request): HttpResponse = {
      _requests = _requests :+ request
      answer(request)
    }
  }

  def fixedCredentials(): GoogleDriveCredentials = GoogleDriveCredentials.accessTokenC("synthetic-access-marker").TAKE
  def refreshCredentials(): GoogleDriveCredentials =
    GoogleDriveCredentials.refreshTokenC("synthetic-client", Some("synthetic-secret-marker"), "synthetic-refresh-marker").TAKE
  def store(credentials: GoogleDriveCredentials = fixedCredentials()): GoogleDriveFileStore =
    GoogleDriveFileStore.createC(parent.store, credentials).TAKE
  def native(operation: UnitOfWorkOp[HttpResponse]): ExecUowM[HttpResponse] =
    ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation))
  def failure[A](outcome: Consequence[A]): Conclusion = outcome match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured failure")
  }
  def jsonRecord(record: Record): Json = parser.parse(RecordEncoder.json(record)).toOption.get
  def disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full), ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
  def tokenJson(token: String): Json = Json.obj("access_token" -> Json.fromString(token),
    "token_type" -> Json.fromString("bEaReR"), "expires_in" -> Json.fromInt(3600))
  def metadata(id: String = reference.key, name: String = "label", mime: String = "application/octet-stream",
    size: Option[String] = Some("3"), parents: Option[Vector[String]] = Some(Vector(parent.key)), version: Option[String] = Some("1")): Json =
    Json.obj((Vector("id" -> Json.fromString(id), "name" -> Json.fromString(name), "mimeType" -> Json.fromString(mime),
      "trashed" -> Json.False) ++ size.map(value => "size" -> Json.fromString(value)).toVector ++
      parents.map(values => "parents" -> Json.arr(values.map(Json.fromString)* )).toVector ++
      version.map(value => "version" -> Json.fromString(value)).toVector)*)
  def jsonResponse(json: Json, code: Int = 200, headers: Record = Record.empty): HttpResponse =
    textResponse(json.noSpaces, code, "application/json", headers)
  def textResponse(text: String, code: Int = 200, mime: String = "application/json", headers: Record = Record.empty): HttpResponse =
    HttpResponse.Text(HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError),
      ContentType(MimeType(mime), Some(StandardCharsets.UTF_8)), Bag.text(text, StandardCharsets.UTF_8), headers)
  def initiation(location: String = sessionUrl): HttpResponse =
    textResponse("", headers = Record.data("Location" -> location))
  def bytes(bag: Bag): Vector[Byte] = {
    val input = bag.openInputStream()
    try input.readAllBytes().toVector finally input.close()
  }
}
