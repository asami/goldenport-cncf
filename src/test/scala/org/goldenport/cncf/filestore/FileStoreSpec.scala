package org.goldenport.cncf.filestore

import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.bag.{Bag, BagBackend}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.record.Record
import org.goldenport.protocol.{Property, Protocol}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class FileStoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import FileStoreSpec.*

  "FileStore consumer programs" should {
    "separate metadata observation from content reads" which {
      "accepts opaque S3-shaped keys and preserves unknown observations" in {
        Given("a provider-neutral fixture and a slash/Unicode key without an Entity ID or URL")
        val fixture = new Fixture
        val reference = FileStoreRef("configured-bucket", "prefix/資料/zero.bin")
        val parent = FileStoreRef("configured-bucket", "prefix/資料")
        fixture.seed(FileStoreMetadata(reference, "zero.bin", _mime, None, Vector(parent), FileStoreKind.File), Bag.binary(Array[Byte](0, -1)))
        When("a shared consumer observes metadata, validates its expected parent and MIME, then reads")
        val result = fixture.run(_read_program(fixture.store, reference, parent))
        Then("unknown size/version remain absent and exactly stat then read were interpreted")
        result._1.byteLength shouldBe None
        result._1.version shouldBe None
        result._1.parents shouldBe Vector(parent)
        _bytes(result._2.content).toVector shouldBe Vector[Byte](0, -1)
        fixture.operations.map(_.asInstanceOf[UnitOfWorkOp.HttpGet].headers("Fixture-Action")) shouldBe Vector("stat", "read")
        fixture.operations.map(_.asInstanceOf[UnitOfWorkOp.HttpGet].headers("Fixture-Key")) shouldBe Vector(reference.key, reference.key)
      }

      "stops before read when trusted source grouping does not match" in {
        Given("metadata whose source parent differs from the caller's trusted grouping")
        val fixture = new Fixture
        val reference = FileStoreRef("configured-bucket", "opaque/key")
        val actualparent = FileStoreRef(reference.store, "other")
        fixture.seed(FileStoreMetadata(reference, "label", _mime, None, Vector(actualparent), FileStoreKind.File), Bag.empty)
        When("the consumer validates metadata before asking for bytes")
        val result = fixture.evaluate(_read_program(fixture.store, reference, FileStoreRef(reference.store, "expected")))
        Then("source rejection leaves only the explicit stat operation")
        result.isFaillure shouldBe true
        fixture.operations.size shouldBe 1
        fixture.operations.head.asInstanceOf[UnitOfWorkOp.HttpGet].headers("Fixture-Action") shouldBe "stat"
      }
    }

    "create fresh objects" which {
      "defers all writes until the actual UoW interpreter executes the native program" in {
        Given("a conforming test provider and a caller-owned materialized Bag")
        val fixture = new Fixture
        val source = Bag.binary(Array[Byte](0, -1))
        val program = _create_program(fixture.store, FileStoreRef("configured-bucket", "prefix/資料"), "label", source, source)
        val before = fixture.operations
        When("the actual interpreter runs the already constructed Free/ConsequenceT program")
        val result = fixture.run(program)
        Then("construction recorded no operations and interpretation alone produced three fresh references")
        before shouldBe empty
        fixture.operations.size shouldBe 3
        Set(result._1.reference, result._2.reference, result._3.reference).size shouldBe 3
      }

      "keeps duplicate labels and earlier payloads under provider-issued references" in {
        Given("a configured grouping root and two payloads with the same display label")
        val fixture = new Fixture
        val root = FileStoreRef("configured-bucket", "bucket/prefix")
        val first = Bag.binary(Array[Byte](0, -128, -1))
        val second = Bag.binary(Array[Byte](1, 2))
        When("the shared consumer creates a collection then two fresh objects")
        val result = fixture.run(_create_program(fixture.store, root, "same label", first, second))
        val earlier = fixture.run(fixture.store.read(result._2.reference))
        val later = fixture.run(fixture.store.read(result._3.reference))
        Then("grouping and object references are fresh, duplicate names do not replace content, and no hidden operations occur")
        result._1.kind shouldBe FileStoreKind.Collection
        result._1.parents shouldBe Vector(root)
        result._2.reference should not be result._3.reference
        result._2.name shouldBe result._3.name
        result._2.parents shouldBe Vector(result._1.reference)
        result._3.parents shouldBe Vector(result._1.reference)
        _bytes(earlier.content).toVector shouldBe Vector[Byte](0, -128, -1)
        _bytes(later.content).toVector shouldBe Vector[Byte](1, 2)
        fixture.operations.map {
          case _: UnitOfWorkOp.HttpPost => "collection"
          case _: UnitOfWorkOp.HttpPutBag => "create"
          case _: UnitOfWorkOp.HttpGet => "read"
          case _ => "unexpected"
        } shouldBe Vector("collection", "create", "create", "read", "read")
      }

      "preserves bounded generated binary bytes, display names and opaque keys without discards" in {
        Given("bounded binary payloads and labels/keys containing slash and Unicode")
        val inputs = for {
          length <- Gen.choose(0, 256)
          bytes <- Gen.listOfN(length, Gen.choose(-128, 127).map(_.toByte))
          suffix <- Gen.choose(0, 100000)
          label <- Gen.oneOf("same", "資料 / label", "é")
        } yield (bytes.toVector, s"bucket/資料/$suffix", label)
        When("each shared create program is interpreted by the test-only native-operation provider")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(inputs) { input =>
          val (bytes, key, label) = input
          val fixture = new Fixture
          val source = Bag.binary(bytes.toArray)
          val created = fixture.run(_create_program(fixture.store, FileStoreRef("configured-bucket", key), label, source, source))
          val read = fixture.run(fixture.store.read(created._2.reference))
          _bytes(source).toVector == bytes && _bytes(read.content).toVector == bytes &&
            created._2.name == label && created._2.reference != created._3.reference &&
            created._1.parents == Vector(FileStoreRef("configured-bucket", key)) && fixture.operations.size == 4
        })
        Then("at least one hundred cases preserve the original Bag with zero discarded cases")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "rejects URL-backed writes before constructing an external operation" in {
        Given("a URL-backed Bag that would otherwise perform another network read")
        val fixture = new Fixture
        val source = Bag.Instance(BagBackend.Url(java.net.URI.create("https://example.invalid/sensitive").toURL))
        When("the test provider receives a create request")
        val result = fixture.evaluate(fixture.store.create(FileStoreRef("configured-bucket", "parent"), "label", _mime, source))
        Then("the provider rejects it without interpreting an operation")
        result.isFaillure shouldBe true
        fixture.operations shouldBe empty
      }
    }
  }

  private def _read_program(store: FileStore, reference: FileStoreRef, parent: FileStoreRef): ExecUowM[(FileStoreMetadata, FileStoreContent)] = {
    for {
      metadata <- store.stat(reference)
      _ <- ConsequenceT.fromConsequence[ [A] =>> Free[UnitOfWorkOp, A], Unit](
        if (metadata.parents.contains(parent) && metadata.mediaType == _mime) Consequence.unit
        else Consequence.securityPermissionDenied("Unexpected source grouping or media type"))
      content <- store.read(reference)
    } yield (metadata, content)
  }

  private def _create_program(store: FileStore, root: FileStoreRef, label: String, first: Bag, second: Bag): ExecUowM[(FileStoreMetadata, FileStoreMetadata, FileStoreMetadata)] = {
    for {
      collection <- store.createCollection(root, "source grouping")
      one <- store.create(collection.reference, label, _mime, first)
      two <- store.create(collection.reference, label, _mime, second)
    } yield (collection, one, two)
  }
}

private object FileStoreSpec {
  private val _mime = MimeType("application/octet-stream")

  private def _bytes(bag: Bag): Array[Byte] = {
    val in = bag.openInputStream()
    try in.readAllBytes() finally in.close()
  }

  final class Fixture {
    private var _entries = Map.empty[String, (FileStoreMetadata, Bag)]
    private var _operations = Vector.empty[UnitOfWorkOp[?]]
    def operations: Vector[UnitOfWorkOp[?]] = _operations
    val store: FileStore = new Provider

    private val _context = {
      val owner = ComponentId("org.example.FileStoreFixture")
      val admission = HttpExecutionAdmission.fromRuntimeC("https://filestore.example.invalid/",
        HttpExecutionLimits(1000, 1000, 4096), Map(owner -> HttpExecutionGrant(
          Vector("https://filestore.example.invalid"), HttpExecutionMethod.values.toSet))).TAKE
      val base = ExecutionContext.create()
      val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      component.withScopeContext(root)
      base.withScope(component.scopeContext)
    }

    private val _driver = new HttpDriver {
      def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        _operations = _operations :+ UnitOfWorkOp.HttpGet(path, headers, properties)
        val (metadata, content) = _entries(headers("Fixture-Key"))
        _response(metadata, if (headers("Fixture-Action") == "read") content else Bag.empty)
      }
      def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        _operations = _operations :+ UnitOfWorkOp.HttpPost(path, body, headers, properties)
        _create(headers("Fixture-Parent"), headers, FileStoreKind.Collection, Bag.empty)
      }
      def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new UnsupportedOperationException("Unexpected text PUT in FileStore fixture")
      override def putBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        _operations = _operations :+ UnitOfWorkOp.HttpPutBag(path, body, headers, properties)
        val content = Bag.binary(_bytes(body.get))
        _create(headers("Fixture-Parent"), headers, FileStoreKind.File, content)
      }
    }

    def seed(metadata: FileStoreMetadata, content: Bag): Unit = {
      _entries = _entries.updated(metadata.reference.key, (metadata, content))
    }

    def evaluate[A](program: ExecUowM[A]): Consequence[A] = {
      val uow = new UnitOfWork(_context) {
        override def httpDriver: HttpDriver = _driver
      }
      new UnitOfWorkInterpreter(uow).run(program)
    }

    def run[A](program: ExecUowM[A]): A = evaluate(program).TAKE

    private def _create(parent: String, headers: Map[String, String], kind: FileStoreKind, content: Bag): HttpResponse = {
      val reference = FileStoreRef(headers("Fixture-Store"), s"fresh/資料/${_entries.size + 1}")
      val metadata = FileStoreMetadata(reference, headers("Fixture-Name"),
        headers.get("Content-Type").map(MimeType(_)).getOrElse(_mime), content.metadata.size,
        Vector(FileStoreRef(reference.store, parent)), kind)
      seed(metadata, content)
      _response(metadata, Bag.empty)
    }

    private def _response(metadata: FileStoreMetadata, content: Bag): HttpResponse = {
      val fields = Vector("reference" -> metadata.reference.key, "name" -> metadata.name,
        "parent" -> metadata.parents.head.key, "kind" -> metadata.kind.toString) ++
        metadata.byteLength.map(value => "length" -> value.toString).toVector ++
        metadata.version.map(value => "version" -> value).toVector
      HttpResponse.Binary(HttpStatus.Ok, ContentType(metadata.mediaType, None), content.promoteToBinary(), Record.create(fields))
    }
  }

  final class Provider extends FileStore {
    val name: String = "configured-bucket"

    def stat(reference: FileStoreRef): ExecUowM[FileStoreMetadata] =
      _lift(UnitOfWorkOp.HttpGet("/object", Map("Fixture-Key" -> reference.key, "Fixture-Action" -> "stat"))).map(_metadata)

    def read(reference: FileStoreRef): ExecUowM[FileStoreContent] =
      _lift(UnitOfWorkOp.HttpGet("/object", Map("Fixture-Key" -> reference.key, "Fixture-Action" -> "read"))).map(response => FileStoreContent(response.mime, response.bag))

    def createCollection(parent: FileStoreRef, name: String): ExecUowM[FileStoreMetadata] =
      _lift(UnitOfWorkOp.HttpPost("/collection", None, Map("Fixture-Store" -> parent.store,
        "Fixture-Parent" -> parent.key, "Fixture-Name" -> name))).map(_metadata)

    def create(parent: FileStoreRef, name: String, mediaType: MimeType, content: Bag): ExecUowM[FileStoreMetadata] = {
      content.backend match {
        case BagBackend.Url(_) =>
          ConsequenceT.fromConsequence(Consequence.argumentInvalid("FileStore create requires materialized local content"))
        case _ =>
          _lift(UnitOfWorkOp.HttpPutBag("/object", Some(content), Map("Fixture-Store" -> parent.store,
            "Fixture-Parent" -> parent.key, "Fixture-Name" -> name, "Content-Type" -> mediaType.toString))).map(_metadata)
      }
    }

    private def _lift(operation: UnitOfWorkOp[HttpResponse]): ExecUowM[HttpResponse] =
      ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation))

    private def _metadata(response: HttpResponse): FileStoreMetadata =
      FileStoreMetadata(FileStoreRef(name, response.headerValue("reference").get), response.headerValue("name").get,
        response.mime, response.headerValue("length").map(_.toLong),
        Vector(FileStoreRef(name, response.headerValue("parent").get)),
        FileStoreKind.valueOf(response.headerValue("kind").get), response.headerValue("version"))
  }
}
