package org.goldenport.cncf.job

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Native owned bytes and closed typed consumer behavior; no CBD/JVM claim.
 *
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobOperationResponsePayloadsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobOperationResponsePayloadFixtures.{
    Fixture, ReadEffects, access, bytes, generatedRecord, generatedWire, prefix,
    review, reviewFields, reviewWire, sha256, success
  }

  private val _service_label = "durable job operation response payload refused"
  private val _owned_label = "Durable Job-owned payload refused"
  private val _spec = "docs/spec/durable-job-operation-response-payload-contract.md"
  private val _maximum_bytes = 8388608

  private def _scenario(example: String) =
    afterWord(s"in spec:durable-job-operation-response-payload example:$example rules:R1,R2,R3,R4,R5,R6,R7,R8,R9 phase:69.7 slice:JM69-10B2")

  "RP1 Full completed Review domain publication" should _scenario("RP1") {
    "retain native bytes and exclusive facts while leaving checkpoint ownership with the caller" in {
      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP1/R1..R9: a current native signed V2 Running/Pending Job and independent six-field Review wire and domain value")
        val running = fixture.running("review-domain")
        val originalrow = fixture.native.row(fixture.native.jobEntityId(running.record.body.identity.jobId))
        When("the private publisher acknowledges an owned result reference without checkpointing the Job")
        val reference = fixture.publish(running)
        val publishedrow = fixture.native.row(fixture.native.jobEntityId(running.record.body.identity.jobId))
        val metadata = fixture.native.metadata(reference)
        val nativebytes = fixture.physical(reference)
        val entry = fixture.quota.entries.find(_.blobid == reference.reference).get
        Then("the Job revision/lifecycle are unchanged and exact bytes/content/size/digest have exclusive Published ownership")
        publishedrow shouldBe originalrow
        fixture.native.signed(running.record.body.identity.jobId) shouldBe running
        running.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
        running.record.body.result shouldBe DurableResultOutcome.Pending
        nativebytes shouldBe bytes(reviewWire).toVector
        reference.contentType shouldBe Some("application/vnd.cncf.job-operation-response+json")
        reference.storageKind shouldBe "job-owned-blob"
        reference.byteSize shouldBe bytes(reviewWire).length.toLong
        reference.sha256 shouldBe sha256(bytes(reviewWire))
        metadata.id.value shouldBe reference.reference
        metadata.byteSize shouldBe Some(reference.byteSize)
        metadata.contentType.map(_.header) shouldBe reference.contentType
        metadata.digest shouldBe Some(reference.sha256)
        metadata.attributes(DurableJobOwnedBlobPayloads.ownershipAttribute) shouldBe "exclusive-v1"
        metadata.attributes(DurableJobOwnedBlobPayloads.ownerAttribute) shouldBe entry.ownersha256
        metadata.attributes(DurableJobOwnedBlobPayloads.bindingAttribute) shouldBe entry.bindingsha256.get
        entry.state shouldBe DurableJobOwnedBlobQuotaState.Published
        entry.payloadsha256 shouldBe sha256(bytes(reviewWire))
        entry.bytesize shouldBe reference.byteSize

        Given("the caller attaches the actual acknowledged reference in a normal signed terminal fixture checkpoint")
        val terminal = fixture.terminal(running, reference)
        val before = fixture.facts(terminal, reference)
        fixture.reset()
        When("the original ordinary principal reads that completed native result")
        val result = fixture.read(terminal)
        val effects = fixture.effects
        Then("full ordered native fields, values and types return after a single closed stream with no read-side native effects")
        result.toOption shouldBe Some(review)
        val record = result.toOption.get.asInstanceOf[OperationResponse.RecordResponse].record
        record.getClass shouldBe classOf[Record]
        record.fields.map(_.key) shouldBe reviewFields.map(_._1)
        record.fields.map(_.value.single) shouldBe reviewFields.map(_._2)
        record.fields.map(_.value.single.getClass).distinct shouldBe Vector(classOf[String])
        effects.gets shouldBe 1
        effects.opens shouldBe 1
        effects.closes shouldBe 1
        effects.consumed shouldBe reference.byteSize
        _no_writes(effects)
        fixture.facts(terminal, reference) shouldBe before
      }
    }
  }

  "RP2 Fresh native ownership" should _scenario("RP2") {
    "restore exact stored result through separately constructed managed owners after the writer closes" in {
      DurableJobBackupFixtures.withPath { path =>
        Given(s"$_spec RP2/R2..R9: one actual native namespace with a completed payload and a successfully closed writer")
        val root = Files.createDirectory(path.resolve("native"))
        val writer = new Fixture(root)
        val retained = try {
          writer.start()
          val (snapshot, reference) = writer.completed("fresh-result")
          (snapshot.record.body.identity.jobId, reference, writer.facts(snapshot, reference).rows)
        } finally writer.close()
        writer.closed shouldBe true
        val fresh = new Fixture(root)
        try {
          fresh.start()
          val snapshot = fresh.native.signed(retained._1)
          val before = fresh.facts(snapshot, retained._2)
          val entries = fresh.quota.entries
          fresh.reset()
          When("a fresh context/EntityStore/LocalBlob/owned-payload/result composition loads and reads the signed terminal Job")
          val result = fresh.read(snapshot)
          val detached = fresh.readBytes(snapshot, retained._2)
          val effects = fresh.effects
          Then("independent domain and exact native bytes plus Published quota survive with no native/runtime reader writes")
          result.toOption shouldBe Some(review)
          detached.toOption.map(_.bytes.toVector) shouldBe Some(bytes(reviewWire).toVector)
          detached.toOption.map(_.sha256) shouldBe Some(sha256(bytes(reviewWire)))
          before.rows shouldBe retained._3
          entries.find(_.blobid == retained._2.reference).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Published)
          fresh.quota.entries shouldBe entries
          effects.gets shouldBe 2
          effects.opens shouldBe 2
          effects.closes shouldBe 2
          _no_writes(effects)
          fresh.facts(snapshot, retained._2) shouldBe before
        } finally fresh.close()
        Then("the separately constructed native reader owner also closes its actual managed resource")
        fresh.closed shouldBe true
      }
    }
  }

  "RP3 Signed ordinary authority" should _scenario("RP3") {
    "refuse foreign null corrupt stale missing and inactive observations before Blob metadata or physical lookup" in {
      val modes = Vector("tenant", "subject", "scope", "null-access", "corrupt", "stale", "deleted", "missing", "null-snapshot")
      modes.foreach { mode => DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP3/R2,R9: a terminal native payload and the pre-measurement hostile authority case $mode")
        val (original, reference) = fixture.completed(s"authority-$mode")
        val selectedaccess = mode match {
          case "tenant" => access.copy(tenantId = "foreign")
          case "subject" => access.copy(subjectId = "foreign")
          case "scope" => access.copy(scopes = Set.empty)
          case "null-access" => null
          case _ => access
        }
        val snapshot = mode match {
          case "corrupt" => original.copy(record = original.record.copy(
            integrity = original.record.integrity.copy(unsignedBodySha256 = "0" * 64)))
          case "stale" => fixture.native.checkpoint(original, original.record.body); original
          case "deleted" => fixture.native.checkpoint(original, original.record.body.copy(
            retention = original.record.body.retention.copy(deletionState = DurableDeletionState.Deleted,
              deletedAt = Some(DurableJobBackupFixtures.instant.plusSeconds(30)))))
          case "missing" => fixture.native.removeRow(fixture.native.jobEntityId(original.record.body.identity.jobId)); original
          case "null-snapshot" => null
          case _ => original
        }
        val before = fixture.facts(original, reference)
        fixture.reset()
        When("both private primitive and typed reader receive that unadmitted observed authority")
        val primitive = fixture.readBytes(snapshot, reference, selectedaccess)
        val typed = fixture.read(snapshot, selectedaccess)
        val effects = fixture.effects
        Then("each exact fixed failure is redacted and no Blob metadata/get/open/read or native effect occurs")
        _refused(primitive, _owned_label)
        _refused(typed, _service_label)
        effects.metadata shouldBe 0
        effects.gets shouldBe 0
        effects.opens shouldBe 0
        effects.reads shouldBe 0
        _no_writes(effects)
        fixture.facts(original, reference) shouldBe before
      }}
    }
  }

  "RP4 Complete owned reference admission" should _scenario("RP4") {
    "refuse invalid membership ownership and protected plans while admitting the full inclusive vector" in {
      val modes = Vector("absent", "wrong-complete", "shared", "external-locator", "owner", "store", "binding", "duplicate", "definition", "excess")
      modes.foreach { mode => DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP4/R2..R5,R9: actual native ownership with a signed invalid plan or native fact $mode established before measurement")
        val (first, reference) = fixture.completed(s"reference-$mode")
        val offered = mode match {
          case "wrong-complete" => reference.copy(sha256 = "0" * 64)
          case "shared" => reference.copy(storageKind = "shared")
          case "external-locator" => reference.copy(storageKind = "external", reference = "s3://PRIVATE/unadmitted")
          case _ => reference
        }
        val body = mode match {
          case "absent" => first.record.body.copy(result = DurableResultOutcome.Succeeded(DurableValue.Absent))
          case "wrong-complete" | "shared" | "external-locator" =>
            first.record.body.copy(result = DurableResultOutcome.Succeeded(DurableValue.External(offered)))
          case "duplicate" => first.record.body.copy(inputs = Vector(DurableInputReference("other",
            DurableValue.External(reference.copy(byteSize = reference.byteSize + 1L)))))
          case "definition" => first.record.body.copy(definitionSnapshot =
            first.record.body.definitionSnapshot.copy(declaredSource = Some(reference)))
          case "excess" => first.record.body.copy(inputs = Vector.tabulate(1024)(index =>
            DurableInputReference(s"input-$index", DurableValue.External(reference))))
          case _ => first.record.body
        }
        val snapshot = if (body == first.record.body) first else fixture.native.checkpoint(first, body)
        mode match {
          case "owner" => fixture.replaceBlob(reference, blob => blob.copy(attributes = blob.attributes.updated(
            DurableJobOwnedBlobPayloads.ownerAttribute, "0" * 64)))
          case "store" => fixture.replaceBlob(reference, blob => blob.copy(storageRef = blob.storageRef.map(_.copy(store = "foreign"))))
          case "binding" => fixture.replaceBlob(reference, blob => blob.copy(attributes = blob.attributes.updated(
            DurableJobOwnedBlobPayloads.bindingAttribute, "0" * 64)))
          case _ => ()
        }
        val before = fixture.facts(snapshot, reference)
        fixture.reset()
        When("the selected reference is offered to the primitive and the same signed plan to the typed reader")
        val primitive = fixture.readBytes(snapshot, offered)
        val typed = fixture.read(snapshot)
        val effects = fixture.effects
        Then("invalid authority refuses before physical get/open and an excessive vector stops before metadata lookup")
        _refused(primitive, _owned_label)
        _refused(typed, _service_label)
        effects.gets shouldBe 0
        effects.opens shouldBe 0
        if (mode == "excess") effects.metadata shouldBe 0
        _no_writes(effects)
        fixture.facts(snapshot, reference) shouldBe before
      }}

      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP4/R3,R4: a signed current complete vector of 1024 references sharing one consistent owned fact")
        val (first, reference) = fixture.completed("inclusive-references")
        val snapshot = fixture.native.checkpoint(first, first.record.body.copy(inputs = Vector.tabulate(1023)(index =>
          DurableInputReference(s"input-$index", DurableValue.External(reference)))))
        fixture.reset()
        When("the full inclusive vector is consumed without truncation")
        val result = fixture.read(snapshot)
        val effects = fixture.effects
        Then("one distinct verified native target restores the exact result, after reading the entire admitted plan")
        DurableJobOwnedBlobPayloads.referenceCount(snapshot.record) shouldBe 1024
        result.toOption shouldBe Some(review)
        effects.metadata shouldBe 1
        effects.gets shouldBe 1
        effects.closes shouldBe 1
        _no_writes(effects)
      }
    }
  }

  "RP5 Native Published quota authority" should _scenario("RP5") {
    "permit only exact existing Published facts without bootstrap reservation release or census" in {
      val modes = Vector("published", "missing", "pending", "released", "owner", "size", "digest", "binding", "policy", "invalid-policy", "lower-payload")
      modes.foreach { mode => DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP5/R4,R9: actual native quota authority with bounded pre-measurement fault $mode")
        val (snapshot, reference) = fixture.completed(s"quota-$mode")
        mode match {
          case "missing" => fixture.native.removeRow(DurableJobBackupFixtures.blobQuotaId)
          case "pending" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(
            _.copy(state = DurableJobOwnedBlobQuotaState.Pending, bindingsha256 = None))))
          case "released" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(
            _.copy(state = DurableJobOwnedBlobQuotaState.Released))))
          case "owner" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(_.copy(ownersha256 = "0" * 64))))
          case "size" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(entry => entry.copy(bytesize = entry.bytesize + 1L))))
          case "digest" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(_.copy(payloadsha256 = "0" * 64))))
          case "binding" => fixture.replaceQuota(record => record.copy(entries = record.entries.map(_.copy(bindingsha256 = Some("0" * 64)))))
          case "policy" => fixture.replaceQuota(record => record.copy(policy = record.policy.copy(maxownedbytes = record.policy.maxownedbytes - 1L)))
          case _ => ()
        }
        val policy = fixture.native.original.blobPolicy
        val selectedpolicy = if (mode == "invalid-policy") policy.copy(maxpayloadbytes = 0)
          else if (mode == "lower-payload") policy.copy(maxpayloadbytes = reference.byteSize.toInt - 1) else policy
        val adapter = new DurableJobOperationResponsePayloads(new DurableJobOwnedBlobPayloads(
          fixture.store, fixture.entity, fixture.blob, selectedpolicy))
        val before = fixture.facts(snapshot, reference)
        fixture.reset()
        When("the typed reader consumes only the original existing native authority")
        val result = fixture.use(adapter.readObserved(snapshot, access)(using fixture.context))
        val effects = fixture.effects
        Then("only Published permits one closed physical stream and every read has zero native quota/provider writes or census")
        if (mode == "published") result.toOption shouldBe Some(review) else _refused(result, _service_label)
        effects.gets shouldBe (if (mode == "published") 1 else 0)
        effects.opens shouldBe (if (mode == "published") 1 else 0)
        effects.closes shouldBe (if (mode == "published") 1 else 0)
        _no_writes(effects)
        fixture.facts(snapshot, reference) shouldBe before
      }}
    }
  }

  "RP6 Exact physical reply admission" should _scenario("RP6") {
    "refuse null and mismatched replies before opening while retaining allowed uppercase digest spelling" in {
      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        val modes = Vector("null-result", "null-payload", "null-stream", "wrong-id", "wrong-ref", "null-ref",
          "wrong-content", "null-content", "wrong-size", "wrong-digest", "null-digest", "uppercase-digest")
        val (snapshot, reference) = fixture.completed("physical-shapes")
        modes.foreach { mode =>
          Given(s"$_spec RP6/R5..R9: an admitted terminal payload and the measured native get reply $mode")
          val before = fixture.facts(snapshot, reference)
          fixture.blob.mode = mode
          fixture.reset()
          When("the configured single physical provider supplies that exact reply shape")
          val result = fixture.read(snapshot)
          val effects = fixture.effects
          Then("only case-insensitive matching digest succeeds, shape failures do not open, null streams are never closed, and URLs are unused")
          if (mode == "uppercase-digest") result.toOption shouldBe Some(review) else _refused(result, _service_label)
          effects.gets shouldBe 1
          effects.opens shouldBe (if (Set("null-stream", "uppercase-digest").contains(mode)) 1 else 0)
          effects.closes shouldBe (if (mode == "uppercase-digest") 1 else 0)
          fixture.blob.requested shouldBe Vector(fixture.native.metadata(reference).storageRef.get)
          _no_writes(effects)
          fixture.facts(snapshot, reference) shouldBe before
        }
      }
    }
  }

  "RP7 Bounded one-stream physical integrity" should _scenario("RP7") {
    "require exact EOF size and digest and validate bulk and fallback counts within one sentinel" in {
      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        val (snapshot, reference) = fixture.completed("stream-integrity")
        val modes = Vector("normal", "zero", "short", "trailing", "tamper", "negative-count", "oversized-count", "fallback-negative", "fallback-large")
        modes.foreach { mode =>
          Given(s"$_spec RP7/R6,R7,R9: actual matching published bytes with measured stream mode $mode")
          val before = fixture.facts(snapshot, reference)
          fixture.blob.mode = mode
          fixture.reset()
          When("the owned primitive performs its bounded physical read once")
          val result = fixture.readBytes(snapshot, reference)
          val effects = fixture.effects
          Then("only exact EOF/size/digest succeeds, accumulation and physical consumption are bounded and the single stream closes")
          if (Set("normal", "zero").contains(mode)) {
            result.toOption.map(_.bytes.toVector) shouldBe Some(bytes(reviewWire).toVector)
            result.toOption.map(_.sha256) shouldBe Some(sha256(bytes(reviewWire)))
            effects.consumed shouldBe reference.byteSize
          } else _refused(result, _owned_label)
          effects.gets shouldBe 1
          effects.opens shouldBe 1
          effects.closes shouldBe 1
          effects.maxrequest should be <= 8192
          effects.maxrequest.toLong should be <= (reference.byteSize + 1L)
          effects.consumed should be <= (reference.byteSize + 1L)
          effects.fallbacks shouldBe (if (mode == "zero" || mode.startsWith("fallback-")) 1 else 0)
          if (mode == "trailing") effects.consumed shouldBe (reference.byteSize + 1L)
          _no_writes(effects)
          fixture.facts(snapshot, reference) shouldBe before
        }
      }
    }
  }

  "RP8 Strict UTF-8 and closed wire consumption" should _scenario("RP8") {
    "decode only exact native Unicode wires and refuse malformed bytes grammar canonicality and version without repair" in {
      val unicodewire = prefix + """{"kind":"scalar","value":{"type":"string","value":"界😀"}}}"""
      val cases = Vector("unicode" -> bytes(unicodewire), "malformed-utf8" -> Array[Byte](0xc3.toByte, 0x28),
        "grammar" -> bytes("{"), "noncanonical" -> bytes(" " + reviewWire),
        "version" -> bytes(reviewWire.replace("\"version\":1", "\"version\":2")),
        "bom" -> bytes("\ufeff" + reviewWire))
      cases.foreach { case (label, value) => DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP8/R5..R9: independently supplied $label bytes published under genuine matching ownership and quota")
        val running = fixture.running(s"wire-$label")
        val reference = fixture.publishBytes(running, value)
        val snapshot = fixture.terminal(running, reference)
        val before = fixture.facts(snapshot, reference)
        fixture.reset()
        When("the primitive verifies actual bytes and the typed reader applies strict UTF-8 followed by actual B1 decode")
        val primitive = fixture.readBytes(snapshot, reference)
        val typed = fixture.read(snapshot)
        val effects = fixture.effects
        Then("physical bytes remain exact in every case, only the independent Unicode wire yields its domain response and no replacement/fallback occurs")
        primitive.toOption.map(_.bytes.toVector) shouldBe Some(value.toVector)
        primitive.toOption.map(_.sha256) shouldBe Some(sha256(value))
        if (label == "unicode") typed.toOption shouldBe Some(OperationResponse.Scalar("界😀"))
        else _refused(typed, _service_label)
        effects.gets shouldBe 2
        effects.opens shouldBe 2
        effects.closes shouldBe 2
        _no_writes(effects)
        fixture.facts(snapshot, reference) shouldBe before
      }}
    }
  }

  "RP9 Final observed authority and resource ownership" should _scenario("RP9") {
    "refuse detected revision deletion release races provider errors and interruption while closing every opened stream" in {
      val modes = Vector("revision-read", "deletion-close", "release-read", "release-close", "get-throw", "get-refuse",
        "get-interrupt", "stream-throw", "stream-interrupt", "close-throw", "close-interrupt", "entry-interrupt")
      modes.foreach { mode => DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP9/R7,R9: an admitted terminal payload with deterministic callback or exception $mode")
        val (snapshot, reference) = fixture.completed(s"race-$mode")
        var injectedwrites = 0
        var injectedsearches = 0
        val callback: () => Unit = () => {
          val before = fixture.effects
          if (mode.startsWith("revision")) fixture.native.checkpoint(snapshot, snapshot.record.body)
          else if (mode.startsWith("deletion")) fixture.native.checkpoint(snapshot, snapshot.record.body.copy(
            retention = snapshot.record.body.retention.copy(deletionState = DurableDeletionState.Deleted,
              deletedAt = Some(DurableJobBackupFixtures.instant.plusSeconds(30)))))
          else fixture.replaceQuota(record => record.copy(entries = record.entries.map(_.copy(state = DurableJobOwnedBlobQuotaState.Released))))
          val after = fixture.effects
          injectedwrites += after.writes - before.writes
          injectedsearches += after.searches - before.searches
        }
        if (mode.endsWith("-read")) fixture.blob.beforeRead = callback
        if (mode.endsWith("-close")) fixture.blob.beforeClose = callback
        fixture.blob.mode = if (mode.endsWith("-read") || mode.endsWith("-close") || mode == "entry-interrupt") "normal" else mode
        val nativebytes = fixture.physical(reference)
        fixture.reset()
        try {
          When("the old observed read encounters the deterministic native change or resource failure")
          if (mode == "entry-interrupt") Thread.currentThread().interrupt()
          val result = fixture.read(snapshot)
          val interrupted = Thread.currentThread().isInterrupted
          val effects = fixture.effects
          Then("no typed result escapes, only explicit fault writes occurred, non-null opened streams close and private exception details never leak")
          _refused(result, _service_label)
          effects.gets shouldBe (if (mode == "entry-interrupt") 0 else 1)
          val streamed = !mode.startsWith("get-") && mode != "entry-interrupt"
          effects.opens shouldBe (if (streamed) 1 else 0)
          effects.closes shouldBe (if (streamed) 1 else 0)
          effects.writes shouldBe injectedwrites
          effects.searches shouldBe injectedsearches
          effects.puts shouldBe 0
          effects.deletes shouldBe 0
          effects.urls shouldBe 0
          effects.statuses shouldBe 0
          interrupted shouldBe mode.contains("interrupt")
          if (interrupted) Thread.interrupted() shouldBe true
          fixture.physical(reference) shouldBe nativebytes
          if (mode.startsWith("revision")) fixture.native.signed(snapshot.record.body.identity.jobId).providerrevision should not be snapshot.providerrevision
          if (mode.startsWith("deletion")) fixture.native.signed(snapshot.record.body.identity.jobId).record.body.retention.deletionState shouldBe DurableDeletionState.Deleted
          if (mode.startsWith("release")) fixture.quota.entries.find(_.blobid == reference.reference).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Released)
        } finally { Thread.interrupted(); () }
      }}
    }
  }

  "RP10 Closed producer and existing rollback" should _scenario("RP10") {
    "allocate nothing for unsupported values or invalid publisher shapes and retain original failed-publication rollback" in {
      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP10/R1,R9: a signed Running/Pending native Job, unsupported response families and non-Running/non-Pending publisher shapes")
        val running = fixture.running("publisher-refusal")
        val before = fixture.native.row(fixture.native.jobEntityId(running.record.body.identity.jobId))
        val quota = fixture.native.row(DurableJobBackupFixtures.blobQuotaId)
        var observations = 0
        val arbitrary = new OperationResponse {
          def toResponse: Response = { observations += 1; throw new IllegalStateException("PRIVATE capability") }
          override def print: String = { observations += 1; throw new IllegalStateException("PRIVATE capability") }
          override def display: String = { observations += 1; throw new IllegalStateException("PRIVATE capability") }
          override def show: String = { observations += 1; throw new IllegalStateException("PRIVATE capability") }
        }
        val values: Vector[OperationResponse] = Vector(null, OperationResponse.Http(null), OperationResponse.Opaque(new Object), arbitrary)
        val invalidshapes = Vector(null, running.copy(record = null),
          running.copy(record = running.record.copy(body = running.record.body.copy(lifecycle =
            running.record.body.lifecycle.copy(status = DurableJobLifecycleStatus.Submitted)))),
          running.copy(record = running.record.copy(body = running.record.body.copy(result = DurableResultOutcome.Succeeded(DurableValue.Absent)))))
        val ids = fixture.native.original.ids.calls.get()
        fixture.reset()
        When("unsupported values and pure invalid shapes are offered to publication")
        val refused = fixture.use(values.map(value => fixture.responses.publishObserved(running, access, value)(using fixture.context)) ++
          invalidshapes.map(snapshot => fixture.responses.publishObserved(snapshot, access, review)(using fixture.context)))
        val effects = fixture.effects
        Then("every service failure is fixed, no observation or native allocation occurs and the signed Pending Job stays unchanged")
        refused.foreach(_refused(_, _service_label))
        observations shouldBe 0
        effects.puts shouldBe 0
        effects.metadata shouldBe 0
        _no_writes(effects)
        fixture.native.original.ids.calls.get() shouldBe ids
        fixture.native.row(fixture.native.jobEntityId(running.record.body.identity.jobId)) shouldBe before
        fixture.native.row(DurableJobBackupFixtures.blobQuotaId) shouldBe quota

        Given("an actual unrelated published payload and a malformed acknowledgement for each newly issued service and original private put")
        val retained = fixture.publishBytes(running, Array[Byte](7), ContentType.TEXT_PLAIN)
        val retainedbytes = fixture.physical(retained)
        fixture.blob.putTransform = _.copy(digest = "bad")
        fixture.reset()
        When("the service publication and separate original put each encounter existing acknowledgement refusal")
        val service = fixture.use(fixture.responses.publishObserved(running, access, review)(using fixture.context))
        val primitive = fixture.use(fixture.payloads.putObserved(running, access, None, ContentType.TEXT_PLAIN,
          Bag.binary(Array[Byte](9)))(using fixture.context))
        val effectsafter = fixture.effects
        Then("both remain refusals and roll back only their own acknowledged bytes and exact quota capacity without inventing a checkpoint or Absent result")
        _refused(service, _service_label)
        _refused(primitive, _owned_label)
        effectsafter.puts shouldBe 2
        effectsafter.deletes shouldBe 2
        val newissued = fixture.blob.issued.takeRight(2)
        newissued.foreach(result => fixture.native.blob.get(result.storageRef).isSuccess shouldBe false)
        val entries = fixture.quota.entries
        newissued.foreach(result => entries.find(_.blobid == result.id.value).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Released))
        entries.find(_.blobid == retained.reference).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Published)
        fixture.physical(retained) shouldBe retainedbytes
        fixture.native.signed(running.record.body.identity.jobId) shouldBe running
      }
    }
  }

  "RP11 Inclusive native payload capacity" should _scenario("RP11") {
    "roundtrip the independent exact 8 MiB Scalar wire and reject one-over producers and hostile physical excess boundedly" in {
      DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
        Given(s"$_spec RP11/R1,R4..R9: independently assembled canonical Scalar-string wires exactly 8388608 bytes and one byte over")
        val scalarprefix = prefix + "{\"kind\":\"scalar\",\"value\":{\"type\":\"string\",\"value\":\""
        val suffix = "\"}}}"
        val padding = "x" * (_maximum_bytes - bytes(scalarprefix + suffix).length)
        val exactwire = scalarprefix + padding + suffix
        val overwire = scalarprefix + padding + "x" + suffix
        val running = fixture.running("capacity")
        When("the exact response is published and its actual reference is attached and consumed")
        val reference = fixture.publish(running, OperationResponse.Scalar(padding))
        val snapshot = fixture.terminal(running, reference)
        fixture.reset()
        val result = fixture.read(snapshot)
        val effects = fixture.effects
        Then("the unchanged inclusive ceiling admits the entire wire and exact value through one bounded closed stream")
        bytes(exactwire).length shouldBe _maximum_bytes
        bytes(overwire).length shouldBe (_maximum_bytes + 1)
        reference.byteSize shouldBe _maximum_bytes.toLong
        fixture.physical(reference) shouldBe bytes(exactwire).toVector
        reference.sha256 shouldBe sha256(bytes(exactwire))
        result.toOption shouldBe Some(OperationResponse.Scalar(padding))
        effects.consumed shouldBe _maximum_bytes.toLong
        effects.maxrequest should be <= 8192
        effects.opens shouldBe 1
        effects.closes shouldBe 1
        _no_writes(effects)

        Given("another current Running/Pending Job and the independently one-over producer value")
        val overjob = fixture.running("over-capacity")
        val quota = fixture.native.row(DurableJobBackupFixtures.blobQuotaId)
        fixture.reset()
        When("the oversized producer is offered without increasing policy")
        val refused = fixture.use(fixture.responses.publishObserved(overjob, access, OperationResponse.Scalar(padding + "x"))(using fixture.context))
        val overeffects = fixture.effects
        Then("encode refuses before any native put, quota reservation or metadata allocation")
        _refused(refused, _service_label)
        overeffects.metadata shouldBe 0
        overeffects.puts shouldBe 0
        _no_writes(overeffects)
        fixture.native.row(DurableJobBackupFixtures.blobQuotaId) shouldBe quota

        Vector("wrong-size", "trailing").foreach { mode =>
          Given(s"an otherwise admitted exact-capacity body with lying-size or trailing-sentinel provider mode $mode")
          fixture.blob.mode = mode
          fixture.reset()
          When("the bounded consumer encounters hostile physical excess")
          val hostile = fixture.read(snapshot)
          val hosteffects = fixture.effects
          Then("the response refuses without truncation and consumes at most max+1 bytes, closing every opened stream")
          _refused(hostile, _service_label)
          hosteffects.gets shouldBe 1
          hosteffects.opens shouldBe (if (mode == "trailing") 1 else 0)
          hosteffects.closes shouldBe hosteffects.opens
          hosteffects.consumed shouldBe (if (mode == "trailing") _maximum_bytes.toLong + 1L else 0L)
          hosteffects.maxrequest should be <= 8192
          _no_writes(hosteffects)
        }
      }
    }
  }

  "RP12 Generated native result invariants" should _scenario("RP12") {
    "preserve ordered typed Records and independent bytes across native V1 and V2 jobs with ordinary shrinking" in {
      Given(s"$_spec RP12/R1..R9: bounded generated Boolean/Int/String/List/Vector/JSON Record atoms and existing V1/V2 selection")
      val generator = for {
        size <- Gen.choose(0, 8)
        values <- Gen.listOfN(size, Gen.choose(-100, 100))
        legacy <- Gen.oneOf(false, true)
      } yield (values, legacy)
      val property = Prop.forAll(generator) { case (values, legacy) =>
        DurableJobOperationResponsePayloadFixtures.withFixture { fixture =>
          Given(s"$_spec RP12 generated case: an independent native namespace and ordinary generated domain values with a V2 Running/Pending producer and selected completed consumer legacy=$legacy")
          val expected = generatedRecord(values)
          val expectedwire = generatedWire(values)
          val running = fixture.running("generated")
          When("the generated native Record is published, signed with its real terminal reference and read through the actual adapters")
          val reference = fixture.publish(running, OperationResponse.RecordResponse(expected))
          val snapshot = fixture.terminal(running, reference, legacy)
          val before = fixture.facts(snapshot, reference)
          val quota = fixture.quota.entries
          fixture.reset()
          val result = fixture.read(snapshot)
          val effects = fixture.effects
          Then("domain order/value/concrete atom kinds, independent native bytes, Published quota and read-no-write/closure invariants all hold")
          result.toOption shouldBe Some(OperationResponse.RecordResponse(expected))
          val record = result.toOption.get.asInstanceOf[OperationResponse.RecordResponse].record
          record.getClass shouldBe classOf[Record]
          _record_observation(record) shouldBe _record_observation(expected)
          fixture.physical(reference) shouldBe bytes(expectedwire).toVector
          reference.byteSize shouldBe bytes(expectedwire).length.toLong
          reference.sha256 shouldBe sha256(bytes(expectedwire))
          snapshot.record.format shouldBe (if (legacy) DurableRecordFormat.V1 else DurableRecordFormat.V2)
          quota.find(_.blobid == reference.reference).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Published)
          fixture.quota.entries shouldBe quota
          effects.gets shouldBe 1
          effects.opens shouldBe 1
          effects.closes shouldBe 1
          effects.consumed shouldBe reference.byteSize
          _no_writes(effects)
          fixture.facts(snapshot, reference) shouldBe before
          true
        }
      }
      When("ScalaCheck runs ordinary tuple/list/scalar shrinking with seed69710022L and minimum24 actual successful cases")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710022L)).withMinSuccessfulTests(24), property)
      Then("the actual checked result passes and reports at least 24 successful native cases")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _no_writes(effects: ReadEffects): Unit = {
    effects.writes shouldBe 0
    effects.searches shouldBe 0
    effects.puts shouldBe 0
    effects.deletes shouldBe 0
    effects.urls shouldBe 0
    effects.statuses shouldBe 0
  }

  private def _refused(actual: Consequence[?], label: String): Unit = {
    actual.toOption shouldBe None
    val expected: Consequence[Nothing] = Consequence.stateInvalid[Nothing](label)
    (actual, expected) match {
      case (Consequence.Failure(actualconclusion), Consequence.Failure(expectedconclusion)) =>
        actualconclusion.status shouldBe expectedconclusion.status
        actualconclusion.observation.taxonomy shouldBe expectedconclusion.observation.taxonomy
        actualconclusion.observation.phenomenon shouldBe expectedconclusion.observation.phenomenon
        actualconclusion.observation.cause.kind shouldBe expectedconclusion.observation.cause.kind
        actualconclusion.displayMessage shouldBe expectedconclusion.displayMessage
        actualconclusion.interpretation shouldBe expectedconclusion.interpretation
        actualconclusion.disposition shouldBe expectedconclusion.disposition
        actualconclusion.previous shouldBe expectedconclusion.previous
        actualconclusion.getException shouldBe None
        actualconclusion.displayMessage should not include "PRIVATE"
      case (Consequence.Success(_), _) => fail("expected payload refusal")
      case (_, Consequence.Success(_)) => fail("expected payload refusal")
    }
  }

  private def _record_observation(record: Record): Vector[Any] = record.fields.map { field =>
    val value = field.value.single
    val kind = value match {
      case _: List[?] => "list"
      case _: Vector[?] => "vector"
      case other => other.getClass.getName
    }
    (field.key, field.getClass, field.value.getClass, kind, value)
  }
}
