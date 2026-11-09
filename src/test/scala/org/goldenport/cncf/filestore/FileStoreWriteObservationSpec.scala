package org.goldenport.cncf.filestore

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import io.circe.{Json, parser}
import org.goldenport.cncf.http.HttpExecutionMethod
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class FileStoreWriteObservationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  private val _binding = FileStoreWriteBinding("operation-1", "caller-1", parent, "label")
  private val _reservation = FileStoreCollectionReservation(_binding, reference, None)

  "Reserved collection observation" should {
    "preserve the ordinary FileStore creation API" in {
      Given("an existing caller using only the base FileStore capability")
      val fixture = new Harness(_ => jsonResponse(metadata(mime = folderMime, size = None)))
      val provider: FileStore = store()
      When("the caller creates a collection without the optional reservation API")
      val result = fixture.evaluate(provider.createCollection(parent, "label")).TAKE
      Then("the existing creation returns provider metadata through its normal POST")
      result.reference shouldBe reference
      fixture.requests.map(_.method) shouldBe Vector("POST")
    }

    "reserve before creation and confirm the exact reserved provider identity" in {
      Given("a parent collection and a generated opaque identity")
      val fixture = new Harness(request => if (request.path.contains("generateIds"))
        jsonResponse(Json.obj("ids" -> Json.arr(Json.fromString(reference.key))))
      else if (request.method == "GET")
        jsonResponse(metadata(id = parent.key, mime = folderMime, size = None, parents = Some(Vector("root_01"))))
      else jsonResponse(metadata(mime = folderMime, size = None)))
      When("reservation is interpreted and the caller subsequently submits that reservation")
      val reservation = fixture.evaluate(store().reserveCollection(_binding)).TAKE
      val result = fixture.evaluate(store().createReservedCollection(reservation)).TAKE
      Then("the reserved identity survives and a matching successful response confirms it")
      reservation shouldBe _reservation
      result.matches(reservation) shouldBe true
      result.state.isInstanceOf[FileStoreWriteState.Confirmed] shouldBe true
      fixture.requests.map(_.method) shouldBe Vector("GET", "GET", "POST")
    }

    "record native refusal before invoking the write driver" in {
      Given("a reserved collection and host authority for GET only")
      val fixture = new Harness(_ => throw new IOException("unexpected"), methods = Set(HttpExecutionMethod.Get))
      When("the reserved collection POST is interpreted")
      val result = fixture.evaluate(store().createReservedCollection(_reservation)).TAKE
      Then("the exact reservation is bound to a known pre-dispatch refusal and no driver is acquired")
      result shouldBe FileStoreWriteObservation(_reservation, FileStoreWriteState.NotDispatched)
      fixture.requests shouldBe empty
      fixture.acquisitions shouldBe 0
    }

    "retain uncertainty after a lost response and never retry" in {
      Given("a driver that loses the response after receiving the POST")
      val fixture = new Harness(_ => throw new IOException("lost response"))
      When("the reserved create is interpreted")
      val result = fixture.evaluate(store().createReservedCollection(_reservation)).TAKE
      Then("the observation records possible dispatch with exactly one POST")
      result.state shouldBe FileStoreWriteState.PossiblyDispatched
      fixture.requests.map(_.method) shouldBe Vector("POST")
      fixture.requests.head.text.get should include (reference.key)
    }

    "confirm only the reserved identity and retain unknown for ambiguous responses" in {
      Given("a response for a same-named but different collection and a 404 on exact-identity inspection")
      val fixture = new Harness(request => if (request.method == "POST")
        jsonResponse(metadata(id = "other_01", mime = folderMime, size = None)) else textResponse("", 404))
      When("the create and then the independent exact-reference inspection execute")
      val created = fixture.evaluate(store().createReservedCollection(_reservation)).TAKE
      val inspected = fixture.evaluate(store().inspectReservedCollection(_reservation)).TAKE
      Then("neither equal names nor a 404 proves a confirmed create or non-dispatch")
      created.state shouldBe FileStoreWriteState.Unknown
      inspected.state shouldBe FileStoreWriteState.Unknown
      fixture.requests.map(_.method) shouldBe Vector("POST", "GET")
    }

    "retain a native observation across a checkpoint and fresh interpreter" in {
      Given("a reserved collection and a host that refuses the managed POST before dispatch")
      val original = new Harness(_ => throw new IOException("unexpected"), methods = Set(HttpExecutionMethod.Get))
      val observation = original.evaluate(store().createReservedCollection(_reservation)).TAKE
      observation.state shouldBe FileStoreWriteState.NotDispatched
      original.requests shouldBe empty
      original.acquisitions shouldBe 0
      val reservation = observation.reservation
      val binding = reservation.binding
      val state = observation.state match {
        case FileStoreWriteState.NotDispatched => "not-dispatched"
        case other => fail(s"Unexpected native observation: $other")
      }
      val saved = Json.obj("operation" -> Json.fromString(binding.operation), "caller" -> Json.fromString(binding.caller),
        "state" -> Json.fromString(state), "parentStore" -> Json.fromString(binding.parent.store),
        "parent" -> Json.fromString(binding.parent.key), "name" -> Json.fromString(binding.name),
        "referenceStore" -> Json.fromString(reservation.reference.store),
        "reference" -> Json.fromString(reservation.reference.key),
        "root" -> reservation.storageRoot.fold(Json.Null)(ref => Json.obj(
          "store" -> Json.fromString(ref.store), "key" -> Json.fromString(ref.key)))).noSpaces
      val checkpoint = Files.createTempFile("filestore-observation-", ".json")
      try {
        Files.write(checkpoint, saved.getBytes(StandardCharsets.UTF_8))
        When("the owner restores the checkpoint and a fresh interpreter inspects its reserved reference")
        val fields = parser.parse(new String(Files.readAllBytes(checkpoint), StandardCharsets.UTF_8)).toOption.get.hcursor
        val root = fields.downField("root")
        val restoredroot = if (root.focus.contains(Json.Null)) None else Some(FileStoreRef(
          root.get[String]("store").toOption.get, root.get[String]("key").toOption.get))
        val restored = FileStoreCollectionReservation(FileStoreWriteBinding(fields.get[String]("operation").toOption.get,
          fields.get[String]("caller").toOption.get, FileStoreRef(fields.get[String]("parentStore").toOption.get,
            fields.get[String]("parent").toOption.get), fields.get[String]("name").toOption.get),
          FileStoreRef(fields.get[String]("referenceStore").toOption.get, fields.get[String]("reference").toOption.get), restoredroot)
        val restoredstate = fields.get[String]("state").toOption.get match {
          case "not-dispatched" => FileStoreWriteState.NotDispatched
          case other => fail(s"Unexpected checkpoint state: $other")
        }
        val retained = FileStoreWriteObservation(restored, restoredstate)
        val fresh = new Harness(_ => jsonResponse(metadata(mime = folderMime, size = None)))
        val result = fresh.evaluate(store().inspectReservedCollection(retained.reservation)).TAKE
        Then("the actual native observation survives and the inspection confirms the same binding")
        retained shouldBe observation
        retained.matches(_reservation) shouldBe true
        result.state.isInstanceOf[FileStoreWriteState.Confirmed] shouldBe true
        result.matches(retained.reservation) shouldBe true
        fresh.requests.map(_.method) shouldBe Vector("GET")
      } finally Files.deleteIfExists(checkpoint)
    }

    "reject changed operation, caller, target and reserved identity when matching a retained observation" in {
      Given("a retained observation and independently varied binding fields")
      val observation = FileStoreWriteObservation(_reservation, FileStoreWriteState.NotDispatched)
      val property = Prop.forAll(Gen.alphaNumStr.suchThat(x => x.nonEmpty && x != "operation-1" && x != "caller-1" && x != parent.key && x != reference.key)) { value =>
        val variants = Vector(_reservation.copy(binding = _binding.copy(operation = value)),
          _reservation.copy(binding = _binding.copy(caller = value)),
          _reservation.copy(binding = _binding.copy(parent = FileStoreRef(parent.store, value))),
          _reservation.copy(reference = FileStoreRef(reference.store, value)))
        variants.forall(candidate => !observation.matches(candidate))
      }
      When("the persisted observation is compared to each candidate")
      val result = Test.check(Test.Parameters.default, property)
      Then("no changed binding matches")
      result.passed shouldBe true
    }
  }
}
