package org.goldenport.cncf.subsystem

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SubsystemStoragePlanCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Storage codec rejected: $failure")
  }
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "codec", name))
  private val owner = ComponentId("org.example.Workflow")
  private val policy = take(EventPersistencePolicy.createC("codec-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist),
    EventPersistencePolicy.Rule(EventTypeId("notice"), EventPersistencePolicy.Mode.Transient))))
  private val persistence = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("facts"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow",
        collection("roots"), collection("continuations"))), DataStore.StringEntryId("host")))
  private val record = take(ComponentRecordStorageDeclaration.createC(Vector(
    ComponentRecordStorageDeclaration.CreateGrant(owner, "workflow", collection("registrations")))))
  private def plan(records: Option[ComponentRecordStorageDeclaration]) =
    take(SubsystemStoragePlan.createC(policy, persistence, records))
  private def wire(value: SubsystemStoragePlan): String = take(SubsystemStoragePlanCodec.encode(value)) match {
    case ConfigurationValue.StringValue(text) => text
    case other => fail(s"Unexpected encoded value: $other")
  }
  private def document: Json = parse(wire(plan(Some(record)))).toOption.get
  private def decode(json: Json) = SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(json.noSpaces))
  private def change(json: Json, path: String*)(f: Json => Json): Json =
    path.foldLeft(json.hcursor)((cursor, name) => cursor.downField(name).success.get)
      .withFocus(f).top.get

  "The pure storage plan configuration codec" should {
    "round trip complete plans with and without explicit record grants" in {
      Given("two valid typed plans differing only in optional record declarations")
      val originals = Vector(plan(Some(record)), plan(None))
      When("each plan is encoded, decoded and re-encoded")
      val pairs = originals.map { original =>
        val text = wire(original)
        val decoded = take(SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(text)))
        (original, decoded, text, wire(decoded))
      }
      Then("every field survives semantically, including the explicit absence of records")
      pairs.foreach { case (original, decoded, before, after) =>
        parse(after) shouldBe parse(before)
        decoded.policy.rules shouldBe original.policy.rules
        decoded.persistence.cursorEntryId shouldBe original.persistence.cursorEntryId
        decoded.records.map(_.grants) shouldBe original.records.map(_.grants)
      }
    }

    "accept harmless whitespace and member order without changing plan identity" in {
      Given("a valid document with reversed object members and pretty formatting")
      val reversed = document.mapObject(obj => io.circe.JsonObject.fromIterable(obj.toVector.reverse))
      When("the formatted document is decoded")
      val decoded = take(SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(reversed.spaces2)))
      Then("the resulting complete plan has the same semantic encoding")
      parse(wire(decoded)) shouldBe Right(document)
    }

    "reject missing, unknown and duplicate members at document and nested boundaries" in {
      Given("documents with missing or extra fields and exact duplicate JSON members")
      val badObjects = Vector(document.mapObject(_.remove("records")),
        document.mapObject(_.add("extra", Json.True)),
        change(document, "policy")(_.mapObject(_.add("extra", Json.True))),
        change(document, "persistence")(_.mapObject(_.remove("cursorEntryId"))),
        change(document, "records")(x => Json.arr(x.asArray.get.head.mapObject(_.add("extra", Json.True)))))
      val text = document.noSpaces
      val duplicates = Vector(
        text.replace("\"schema\":", "\"schema\":\"cncf.subsystem-storage.v1\",\"schema\":"),
        text.replace("\"revision\":", "\"revision\":\"duplicate\",\"revision\":"))
      When("all malformed shapes and raw duplicates are decoded")
      val results = badObjects.map(decode) ++ duplicates.map(x =>
        SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(x)))
      Then("none is accepted through a default, ignored key or last-member override")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject wrong primitive types, schema and mode without coercion" in {
      Given("non-string configuration and structurally invalid typed fields")
      val values = Vector(ConfigurationValue.BooleanValue(true), ConfigurationValue.StringValue(null), null)
      val documents = Vector(document.mapObject(_.add("schema", Json.fromString("legacy"))),
        change(document, "policy", "revision")(_ => Json.fromInt(1)),
        change(document, "policy", "rules")(x => Json.fromValues(x.asArray.get.map(
          _.mapObject(_.add("mode", Json.fromString("Persist")))))),
        document.mapObject(_.add("records", Json.Null)))
      When("the codec receives these inputs")
      val results = values.map(SubsystemStoragePlanCodec.decode) ++ documents.map(decode)
      Then("every invalid input returns failure rather than a coerced plan")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      SubsystemStoragePlanCodec.encode(null) shouldBe a[Consequence.Failure[?]]
    }

    "reject excessive text, nesting and grant arrays before creating resources" in {
      Given("oversized text, deeply nested input, BOM, invalid Unicode and excessive grants")
      val badText = Vector(" " * (SubsystemStoragePlanCodec.MAX_BYTES + 1) + document.noSpaces,
        "[" * (SubsystemStoragePlanCodec.MAX_DEPTH + 1) + "0" +
          "]" * (SubsystemStoragePlanCodec.MAX_DEPTH + 1),
        "\uFEFF" + document.noSpaces, "\uD800", "{not-json")
      val excessive = change(document, "records")(x => Json.fromValues(
        Vector.fill(ComponentRecordStorageDeclaration.MAX_GRANTS + 1)(x.asArray.get.head)))
      When("the bounded decoder receives them")
      val results = badText.map(x => SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(x))) :+
        decode(excessive)
      Then("all are refused by the bounded pure conversion")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "accept the exact byte limit and ignore bracket characters inside strings" in {
      Given("valid JSON padded to the byte limit and a revision containing quoted brackets")
      val text = document.noSpaces
      val padding = SubsystemStoragePlanCodec.MAX_BYTES - text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
      val atLimit = " " * padding + text
      val bracketRevision = "revision" + "[" * (SubsystemStoragePlanCodec.MAX_DEPTH + 1)
      val brackets = change(document, "policy", "revision")(_ => Json.fromString(bracketRevision))
      When("the decoder applies byte and structural nesting limits")
      val exact = SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(atLimit))
      val beyond = SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(" " + atLimit))
      val quoted = decode(brackets)
      Then("only the excessive byte count is rejected and quoted brackets do not add depth")
      exact.isSuccess shouldBe true
      beyond shouldBe a[Consequence.Failure[?]]
      take(quoted).policy.revision shouldBe bracketRevision
    }

    "reject noncanonical owners and infrastructure ownership collisions" in {
      Given("valid-shaped documents with invalid owners or reserved record targets")
      val badOwner = change(document, "persistence", "workflowGrants")(x => Json.fromValues(
        x.asArray.get.map(_.mapObject(_.add("componentId", Json.fromString("not-qualified"))))))
      val badCollection = change(document, "persistence", "cursorCollection")(_ => Json.fromString("not-a-collection"))
      val reserved = change(document, "records")(x => Json.fromValues(x.asArray.get.map(
        _.mapObject(_.add("collection", document.hcursor.downField("persistence")
          .downField("cursorCollection").focus.get)))))
      When("typed identity and composed ownership validators run")
      val results = Vector(badOwner, badCollection, reserved).map(decode)
      Then("no invalid or reserved tuple becomes a storage plan")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "leave enclosing construction captures unchanged during wire conversion" in {
      Given("a validated plan and the current construction and resource captures")
      val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
      When("the plan is serialized and parsed without trusted assembly")
      val decoded = take(SubsystemStoragePlanCodec.decode(ConfigurationValue.StringValue(wire(plan(Some(record))))))
      Then("only immutable plan data is returned, not a node, service or active grant")
      decoded.persistence.storeName shouldBe "events"
      (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
    }
  }
}
