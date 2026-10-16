package org.goldenport.cncf.entity

import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.{Field, Record}
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedSideEffect}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  4, 2026
 *  version Sep. 17, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ContentBodyStoragePolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ContentBodyStoragePolicySpec.*
  import ContentBodyStoragePolicySpec.given

  "ContentBodyStoragePolicy" should {
    "keep small text content inline with charset-aware byte metadata" in {
      Given("a small UTF-8 text record")
      given ExecutionContext = ExecutionContext.create()
      val id = _id("inline")
      val record = Record.dataAuto(
        "id" -> id.value,
        "content" -> "abc",
        "content_charset" -> "UTF-8"
      )

      When("the record is prepared for storage")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(id, record))

      Then("the content remains inline with encoded byte metadata")
      stored.getString("content") shouldBe Some("abc")
      stored.getString("content_storage") shouldBe Some("inline")
      stored.getInt("content_byte_size") shouldBe Some(3)
      stored.getString("content_charset") shouldBe Some(StandardCharsets.UTF_8.name())
    }

    "overflow large text content by encoded byte length and hydrate it back" in {
      Given("a large UTF-8 text record and a five-byte inline threshold")
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow")
      val text = "日本語"
      val record = Record.dataAuto(
        "id" -> id.value,
        "content" -> text,
        "content_charset" -> "UTF-8"
      )

      When("the record is prepared and hydrated")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        record,
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, stored))

      Then("the content is stored as overflow and restored on hydration")
      stored.getString("content") shouldBe None
      stored.getString("content_storage") shouldBe Some("overflow")
      stored.getInt("content_byte_size") shouldBe Some(text.getBytes(StandardCharsets.UTF_8).length)
      hydrated.getString("content") shouldBe Some(text)
    }

    "preserve existing overflow content when a partial update omits content" in {
      Given("an existing overflow record and an update that changes only the title")
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow_update")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id" -> id.value,
          "content" -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val updateRecord = stored ++ Record.dataAuto("title" -> "updated")

      When("the partial update is prepared and hydrated")
      val updated = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        updateRecord,
        preserveExistingOverflowOnMissingContent = true
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, updated))

      Then("the existing overflow content is preserved alongside the title")
      updated.getString("content_storage") shouldBe Some("overflow")
      hydrated.getString("content") shouldBe Some("日本語")
      hydrated.getString("title") shouldBe Some("updated")
    }

    "clear overflow content on explicit setNull" in {
      Given("an existing overflow record and an explicit content null update")
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow_clear")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id" -> id.value,
          "content" -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val clearRecord = stored ++ Record.dataAuto("content" -> Update.setNull[String])

      When("the explicit null update is prepared and hydrated")
      val cleared = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        clearRecord,
        preserveExistingOverflowOnMissingContent = true
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, cleared))

      Then("the overflow content and storage marker are cleared")
      cleared.getString("content") shouldBe None
      cleared.getString("content_storage") shouldBe None
      hydrated.getString("content") shouldBe None
    }
  }

  "ContentBody field ownership" should {
    "hydrate overflow over an existing SQL NULL while preserving every nonowned field" in {
      Given("a saved overflow body and a root with SQL NULL content, business and audit fields")
      given ExecutionContext = ExecutionContext.create()
      val id = _id("sqlnull")
      val text = "日本語の本文"
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(id,
        Record.dataAuto("id" -> id.value, "content" -> text),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 1)))
      val nonowned = _business_fields
      val sqlrecord = Record(stored.fields ++ Vector(
        Field("content", Field.Value.Single(null)),
        Field("Content", Field.Value.Single(null))) ++ nonowned)

      When("the logical content is hydrated from the saved overflow")
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, sqlrecord))

      Then("one authoritative nonnull content replaces all logical content aliases and other NULLs remain")
      hydrated.getString("content") shouldBe Some(text)
      hydrated.fields.filter(field => _normalize(field.key) == "content") shouldBe
        Vector(Field("content", Field.Value.Single(text)))
      hydrated.fields.filter(field => nonowned.exists(_.key == field.key)) shouldBe nonowned
      hydrated.fields.filterNot(field => _normalize(field.key) == "content") shouldBe
        sqlrecord.fields.filterNot(field => _normalize(field.key) == "content")
    }

    "replace body metadata" which {
      for {
        oldtext <- Vector("a", "日本語の長い本文")
        nexttext <- Vector("b", "変更した長い本文")
      } {
        s"retain one current metadata set across ${oldtext.length} to ${nexttext.length} character preparation" in {
          Given("an existing inline or overflow root with old body metadata and business/audit SQL NULLs")
          val id = _id("metadata")
          val config = ContentBodyStoragePolicy.Config(inlineByteThreshold = 8)
          val first = _success(ContentBodyStoragePolicy.planForVersionedSave(id,
            Record(Record.dataAuto("id" -> id.value, "content" -> oldtext).fields ++ _business_fields), config))
          val candidate = _replace_content(first.record, nexttext, "UTF-16BE")

          When("changed content and then the same content are prepared without executing side effects")
          val changed = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate, config))
          val repeated = _success(ContentBodyStoragePolicy.planForVersionedSave(id,
            _replace_content(changed.record, nexttext, "UTF-16BE"), config))

          Then("encoded byte size, digest, charset, storage/ref and side effects describe exactly the current body")
          _require_body(id, changed, nexttext, "UTF-16BE", config.inlineByteThreshold)
          _require_body(id, repeated, nexttext, "UTF-16BE", config.inlineByteThreshold)
          repeated.record.fields shouldBe changed.record.fields
          repeated.sideEffects shouldBe changed.sideEffects
          changed.record.fields.filter(field => _business_fields.exists(_.key == field.key)) shouldBe _business_fields
        }
      }

      "retain total bounded metadata and business-field correspondence across at least one hundred preparations" in {
        Given("bounded nonempty old/new text, explicit charsets, thresholds and a strictly reducing shrinker")
        val samples = for {
          oldsize <- Gen.chooseNum(1, 24)
          nextsize <- Gen.chooseNum(1, 24)
          oldcharacter <- Gen.oneOf("x", "日", "λ")
          nextcharacter <- Gen.oneOf("z", "界", "μ")
          charset <- Gen.oneOf("UTF-8", "UTF-16BE")
          threshold <- Gen.chooseNum(1, 24)
        } yield Sample(oldcharacter * oldsize, nextcharacter * nextsize, charset, threshold)
        val property = Prop.forAll(samples) { sample =>
          Given("one root carrying prior owned metadata and complete nonowned business/audit fields")
          val id = _id("property")
          val config = ContentBodyStoragePolicy.Config(sample.threshold)
          val first = _success(ContentBodyStoragePolicy.planForVersionedSave(id,
            Record(Record.dataAuto("id" -> id.value, "content" -> sample.oldtext).fields ++ _business_fields), config))
          val aliases = Record.dataAuto("contentByteSize" -> -1, "contentDigest" -> "old",
            "contentStorage" -> "old", "contentRef" -> "old", "contentCharset" -> "old")
          val candidate = _replace_content(first.record ++ aliases, sample.nexttext, sample.charset)
          When("changed and repeated content preparations replace the owned metadata")
          val changed = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate, config))
          val repeated = _success(ContentBodyStoragePolicy.planForVersionedSave(id,
            _replace_content(changed.record, sample.nexttext, sample.charset), config))
          Then("each preparation has current unique metadata and exactly preserves all nonowned fields")
          _require_body(id, changed, sample.nexttext, sample.charset, sample.threshold)
          _require_body(id, repeated, sample.nexttext, sample.charset, sample.threshold)
          changed.record.fields.filterNot(field => _owned_keys.contains(_normalize(field.key)) ||
            _normalize(field.key) == "content") shouldBe first.record.fields.filterNot(field =>
              _owned_keys.contains(_normalize(field.key)) || _normalize(field.key) == "content")
          repeated.record.fields shouldBe changed.record.fields
          repeated.sideEffects shouldBe changed.sideEffects
          Prop.all("repeated preparation preserves the exact current root" |: Prop(repeated.record.fields == changed.record.fields),
            "repeated preparation preserves the exact current effects" |: Prop(repeated.sideEffects == changed.sideEffects))
        }
        When("ScalaCheck evaluates the total content-ownership law")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful cases and zero discards are required")
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  "ContentBody payload preservation" should {
    "retain exact nonblank whitespace through storage and hydration" which {
      val bodies = Vector(
        ("leading and trailing spaces", "  leading and trailing  ", "UTF-8"),
        ("Japanese with tab and CRLF", "日本語\t本文\r\n", "UTF-8"),
        ("Markdown double spaces and newline", "first line  \nsecond **line**  \n", "UTF-16BE"),
        ("mixed tab and CRLF padding around multiline text", "\t\r\n  multiline\n本文 λ \r\n\t", "UTF-16BE"))
      Vector("inline" -> 65536, "overflow" -> 1).foreach { case (mode, threshold) =>
        bodies.foreach { case (label, text, charset) =>
          s"preserve $label in forced $mode storage with trimmed charset metadata" in {
            _require_payload_round_trip(text, charset, threshold)
          }
        }
      }
    }

    "retain exact bounded generated payloads" which {
      Vector("inline" -> 65536, "overflow" -> 1).foreach { case (mode, threshold) =>
        s"preserve at least one hundred whitespace-padded payloads in forced $mode storage" in {
          Given("a total bounded ASCII/Japanese/Greek core, nonempty whitespace padding and UTF-8 or UTF-16BE")
          val samples = for {
            coresize <- Gen.chooseNum(1, 40)
            core <- Gen.listOfN(coresize, Gen.oneOf('a', 'Z', '日', '界', 'λ', 'β')).map(_.mkString)
            prefixsize <- Gen.chooseNum(1, 6)
            prefix <- Gen.listOfN(prefixsize, Gen.oneOf(' ', '\t', '\r', '\n')).map(_.mkString)
            suffixsize <- Gen.chooseNum(1, 6)
            suffix <- Gen.listOfN(suffixsize, Gen.oneOf(' ', '\t', '\r', '\n')).map(_.mkString)
            charset <- Gen.oneOf("UTF-8", "UTF-16BE")
          } yield PayloadSample(core, prefix, suffix, charset)
          val property = Prop.forAll(samples) { sample =>
            _require_payload_round_trip(sample.prefix + sample.core + sample.suffix, sample.charset, threshold)
            Prop.passed
          }
          When("ScalaCheck evaluates actual memory preparation and hydration with meaningful strictly reducing shrinking")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the payload law requires at least one hundred successes and zero discards")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }

    "retain the existing absent-content and explicit-clear policy" which {
      for {
        input <- Vector("missing", "SQL NULL", "empty", "ASCII whitespace", "SetNull", "Some(SetNull)")
        preserve <- Vector(false, true)
      } {
        s"plan $input with preserve-existing-overflow $preserve without changing the saved body" in {
          Given("an already saved overflow body with raw whitespace and complete business/audit NULL Fields")
          given ExecutionContext = ExecutionContext.create()
          val id = _id("blankpolicy")
          val text = " \t以前の本文\r\n "
          val stored = _success(ContentBodyStoragePolicy.prepareForSave(id,
            Record(Record.dataAuto("id" -> id.value, "content" -> text).fields ++ _business_fields),
            ContentBodyStoragePolicy.Config(inlineByteThreshold = 1)))
          val candidate = input match {
            case "missing" => stored
            case "SQL NULL" => Record(stored.fields :+ Field("content", Field.Value.Single(null)))
            case "empty" => stored ++ Record.dataAuto("content" -> "")
            case "ASCII whitespace" => stored ++ Record.dataAuto("content" -> " \t\r\n ")
            case "SetNull" => stored ++ Record.dataAuto("content" -> Update.SetNull)
            case "Some(SetNull)" => Record(stored.fields :+ Field("content", Field.Value.Single(Some(Update.SetNull))))
          }
          val explicitclear = input == "SetNull" || input == "Some(SetNull)"
          val expectedfields = if (preserve && !explicitclear) candidate.fields else {
            candidate.fields.filterNot { field =>
              val key = _normalize(field.key)
              Set("contentstorage", "contentref").contains(key) || (explicitclear && key == "content")
            }
          }
          val before = _load_overflow(id)
          val hydratedbefore = _success(ContentBodyStoragePolicy.hydrate(id, stored))

          When("pure versioned planning interprets the absent or explicit-clear input")
          val planned = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate,
            preserveExistingOverflowOnMissingContent = preserve))
          val after = _load_overflow(id)
          val hydratedafter = _success(ContentBodyStoragePolicy.hydrate(id, stored))

          Then("the complete planned Fields and established delete or no-effect intent correspond")
          withClue(s"input=$input preserve=$preserve ordered keys=${planned.record.fields.map(_.key)}: ") {
            (planned.record.fields == expectedfields) shouldBe true
          }
          planned.sideEffects shouldBe
            (if (preserve && !explicitclear) Vector.empty else ContentBodyStoragePolicy.planForDelete(id))
          (_payload_nonowned(planned.record) == _payload_nonowned(candidate)) shouldBe true
          And("planning leaves the actual saved overflow and its exact hydrated whitespace unchanged")
          before.isDefined shouldBe true
          (after.map(_.fields) == before.map(_.fields)) shouldBe true
          (hydratedafter.fields == hydratedbefore.fields) shouldBe true
          hydratedbefore.getString("content") shouldBe Some(text)
          hydratedafter.getString("content") shouldBe Some(text)
        }
      }
    }
  }

  "ContentBody decoded metadata reads" should {
    "hydrate an exact saved reference without changing noncontent Fields" which {
      Vector(("snake", "content_storage", "content_ref"), ("camel", "contentStorage", "contentRef")).foreach {
        case (label, storagekey, refkey) =>
          s"restore padded multilingual text through $label metadata at a nondefault reference" in {
            Given("a real memory body saved at an exact nondefault reference and ordered root NULL, security and audit Fields")
            given ExecutionContext = ExecutionContext.create()
            val id = _id("decodedhydrate")
            val ref = s"${id.value}:selected-body"
            val text = " \t日本語 λ body\r\n "
            val body = _save_referenced_body(ref, text)
            val candidate = Record(Record.dataAuto("id" -> id.value,
              storagekey -> " \toverflow\r\n ", refkey -> s" \t$ref\r\n ").fields ++
              Vector(Field("content", Field.Value.Single(null)), Field("Content", Field.Value.Single(null))) ++
              _business_fields ++ Vector(Field("owner_id", Field.Value.Single("retained-owner")),
                Field("updated_at", Field.Value.Single("retained-audit"))))
            val retained = candidate.fields.filterNot(field => _normalize(field.key) == "content")

            When("hydration reads the explicitly selected saved body through the decoded metadata")
            val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, candidate))
            val after = _load_referenced_body(ref)

            Then("one exact padded content value replaces only the logical placeholders and every other ordered Field is retained")
            hydrated.getString("content") shouldBe Some(text)
            (hydrated.fields == retained :+ Field("content", Field.Value.Single(text))) shouldBe true
            (after.map(_.fields) == Some(body.fields)) shouldBe true
            _load_overflow(id).isDefined shouldBe false
          }
      }
    }

    "preserve successful raw fallback when the exact referenced body is absent" which {
      Vector(("snake", "content_storage", "content_ref"), ("camel", "contentStorage", "contentRef")).foreach {
        case (label, storagekey, refkey) =>
          s"retain the same complete raw Record for missing $label referenced content" in {
            Given("a real saved default body and a root explicitly selecting an absent nondefault reference")
            given ExecutionContext = ExecutionContext.create()
            val id = _id("decodedmissing")
            val defaultref = s"${id.value}:content"
            val body = _save_referenced_body(defaultref, " \tdefault body 日本\r\n ")
            val ref = s"${id.value}:absent-body"
            val candidate = Record(Record.dataAuto("id" -> id.value, storagekey -> "overflow", refkey -> ref).fields ++
              Vector(Field("content", Field.Value.Single(null))) ++ _business_fields)

            When("hydration resolves the exact absent reference rather than the available default body")
            val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, candidate))
            val after = _load_referenced_body(defaultref)

            Then("the original raw object and every ordered nullable Field remain unchanged with the saved default body intact")
            (hydrated eq candidate) shouldBe true
            (hydrated.fields == candidate.fields) shouldBe true
            _load_referenced_body(ref).isDefined shouldBe false
            (after.map(_.fields) == Some(body.fields)) shouldBe true
          }
      }
    }

    "retain omitted content under existing overflow preservation" which {
      for {
        (label, storagekey, refkey) <- Vector(("snake", "content_storage", "content_ref"),
          ("camel", "contentStorage", "contentRef"))
        metadata <- Vector("storage", "reference", "both")
      } {
        s"preserve complete $label $metadata metadata with no side intent or saved-body change" in {
          Given("an exact real saved body and a partial root with only the admitted storage marker, reference or both")
          given ExecutionContext = ExecutionContext.create()
          val id = _id("decodedpreserve")
          val ref = s"${id.value}:retained-body"
          val body = _save_referenced_body(ref, " \tretained 日本 Ω\r\n ")
          val storagefields = if (metadata == "reference") Vector.empty else
            Vector(Field(storagekey, Field.Value.Single(" \toverflow\r\n ")))
          val reffields = if (metadata == "storage") Vector.empty else
            Vector(Field(refkey, Field.Value.Single(s" \t$ref\r\n ")))
          val candidate = Record(Record.dataAuto("id" -> id.value).fields ++ storagefields ++ reffields ++
            _business_fields ++ Vector(Field("owner_id", Field.Value.Single("retained-owner"))))

          When("pure planning preserves an existing overflow while content is omitted")
          val planned = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate,
            preserveExistingOverflowOnMissingContent = true))
          val after = _load_referenced_body(ref)

          Then("all input Fields and the same raw object survive, side intent is empty and the saved body is exact")
          (planned.record eq candidate) shouldBe true
          (planned.record.fields == candidate.fields) shouldBe true
          planned.sideEffects shouldBe Vector.empty
          (after.map(_.fields) == Some(body.fields)) shouldBe true
        }
      }
    }

    "resolve padded charset metadata before sizing and canonical writing" which {
      for {
        charsetkey <- Vector("content_charset", "contentCharset")
        boundary <- Vector("below", "equal")
      } {
        s"use $charsetkey UTF-16BE at the $boundary encoded-byte threshold" in {
          Given("exact padded multilingual content with an explicitly padded UTF-16BE charset and complete business/audit Fields")
          given ExecutionContext = ExecutionContext.create()
          val id = _id("decodedcharset")
          val text = " \tA日本 λ\r\n "
          val bytes = text.getBytes(StandardCharsets.UTF_16BE)
          val digest = MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
          val threshold = if (boundary == "below") bytes.length - 1 else bytes.length
          val config = ContentBodyStoragePolicy.Config(inlineByteThreshold = threshold)
          val candidate = Record(Record.dataAuto("id" -> id.value, "content" -> text,
            charsetkey -> " \tUTF-16BE\r\n ", "charset" -> "UTF-8").fields ++ _business_fields)

          When("planning and real memory save encode the selected charset and hydration restores the exact content")
          val planned = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate, config))
          val stored = _success(ContentBodyStoragePolicy.prepareForSave(id, candidate, config))
          val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, stored))
          val savedbody = _load_overflow(id)

          Then("independently computed bytes and digest determine the exact threshold while writes retain only canonical snake metadata")
          _require_body(id, planned, text, "UTF-16BE", threshold)
          stored.getInt("content_byte_size") shouldBe Some(bytes.length)
          stored.getString("content_digest") shouldBe Some(digest)
          stored.getString("content_charset") shouldBe Some("UTF-16BE")
          stored.fields.filter(field => _owned_keys.contains(_normalize(field.key))).map(_.key).toSet shouldBe
            (if (boundary == "below") Set("content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref")
              else Set("content_byte_size", "content_digest", "content_charset", "content_storage"))
          (stored.fields == planned.record.fields) shouldBe true
          hydrated.getString("content") shouldBe Some(text)
          (_payload_nonowned(hydrated) == _payload_nonowned(candidate)) shouldBe true
          if (boundary == "below") {
            val ref = s"${id.value}:content"
            val save = planned.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save]
            save.collection shouldBe a[DataStore.CollectionId.Instance]
            save.collection.collectionName shouldBe "cncf_content_body_overflow"
            save.entryId.print shouldBe ref
            (savedbody.map(_.fields) == Some(save.record.fields)) shouldBe true
          } else savedbody.isDefined shouldBe false
        }
      }
    }

    "retain first-nonempty snake precedence and camel fallback" which {
      Vector(false, true).foreach { emptysnake =>
        s"select the exact body and charset with empty snake metadata=$emptysnake" in {
          Given("different real bodies at snake and camel references plus both metadata spellings")
          given ExecutionContext = ExecutionContext.create()
          val id = _id("decodedprecedence")
          val snakeref = s"${id.value}:snake-body"
          val camelref = s"${id.value}:camel-body"
          val snakebody = _save_referenced_body(snakeref, " \t蛇 日本\r\n ")
          val camelbody = _save_referenced_body(camelref, " \tcamel λ\r\n ")
          val text = " \tencoded 日本 λ\r\n "
          val candidate = Record(Record.dataAuto("id" -> id.value,
            "content_storage" -> (if (emptysnake) " \t\r\n " else " \toverflow\r\n "),
            "contentStorage" -> "overflow", "content_ref" -> (if (emptysnake) " \t\r\n " else snakeref),
            "contentRef" -> camelref).fields ++ _business_fields)
          val encoded = candidate ++ Record.dataAuto("content" -> text,
            "content_charset" -> (if (emptysnake) " \t\r\n " else " \tUTF-16BE\r\n "),
            "contentCharset" -> "UTF-8", "charset" -> "UTF-16LE")
          val charset = if (emptysnake) "UTF-8" else "UTF-16BE"

          When("hydration and pure save planning use the established first nonempty metadata spelling")
          val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, candidate))
          val planned = _success(ContentBodyStoragePolicy.planForVersionedSave(id, encoded,
            ContentBodyStoragePolicy.Config(inlineByteThreshold = 1)))

          Then("nonempty snake values win and empty snake values fall through to camel without changing either real saved body")
          val selected = if (emptysnake) camelbody else snakebody
          hydrated.getString("content") shouldBe selected.getString("content")
          (hydrated.fields == candidate.fields :+ Field("content", Field.Value.Single(selected.getString("content").get))) shouldBe true
          _require_body(id, planned, text, charset, 1)
          (_load_referenced_body(snakeref).map(_.fields) == Some(snakebody.fields)) shouldBe true
          (_load_referenced_body(camelref).map(_.fields) == Some(camelbody.fields)) shouldBe true
        }
      }
      "honor a nonempty inline snake marker over a conflicting camel overflow marker" in {
        Given("a real saved body and both nonempty marker spellings with inline selected by the snake marker")
        given ExecutionContext = ExecutionContext.create()
        val id = _id("decodedmarker")
        val ref = s"${id.value}:marker-body"
        val body = _save_referenced_body(ref, " \tunused saved 日本\r\n ")
        val candidate = Record(Record.dataAuto("id" -> id.value, "content" -> "exact inline text",
          "content_storage" -> " \tinline\r\n ", "contentStorage" -> "overflow", "contentRef" -> ref).fields ++ _business_fields)

        When("hydration interprets the first nonempty snake marker")
        val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, candidate))

        Then("the exact original inline Record remains and the conflicting saved body is unchanged")
        (hydrated eq candidate) shouldBe true
        (hydrated.fields == candidate.fields) shouldBe true
        (_load_referenced_body(ref).map(_.fields) == Some(body.fields)) shouldBe true
      }
    }
  }

  private def _save_referenced_body(ref: String, text: String)(using ctx: ExecutionContext): Record = {
    val collection = DataStore.CollectionId("cncf_content_body_overflow")
    val datastore = _success(ctx.dataStoreSpace.dataStore(collection))
    val body = Record.dataAuto("id" -> ref, "content" -> text)
    _success(datastore.save(collection, DataStore.StringEntryId(ref), body))
    body
  }

  private def _load_referenced_body(ref: String)(using ctx: ExecutionContext): Option[Record] = {
    val collection = DataStore.CollectionId("cncf_content_body_overflow")
    val datastore = _success(ctx.dataStoreSpace.dataStore(collection))
    _success(datastore.load(collection, DataStore.StringEntryId(ref)))
  }

  private def _require_payload_round_trip(text: String, charset: String, threshold: Int): Unit = {
    Given("a nonblank raw payload, padded charset metadata and ordered business/audit SQL NULL Fields")
    given ExecutionContext = ExecutionContext.create()
    val id = _id("payload")
    val config = ContentBodyStoragePolicy.Config(inlineByteThreshold = threshold)
    val candidate = Record(Record.dataAuto("id" -> id.value, "content" -> text,
      "content_charset" -> s" \t$charset\r\n ").fields ++ _business_fields)
    val nonowned = _payload_nonowned(candidate)
    val bytes = text.getBytes(Charset.forName(charset))
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
    val overflow = bytes.length > threshold
    val expectedside = if (overflow) Some(Record.dataAuto(
      "id" -> s"${id.value}:content", "source_entity_id" -> id.value, "field" -> "content",
      "content" -> text, "content_byte_size" -> bytes.length, "content_digest" -> digest,
      "content_charset" -> Charset.forName(charset).name())) else None

    When("real versioned planning, memory preparation and hydration handle the exact payload twice")
    val planned = _success(ContentBodyStoragePolicy.planForVersionedSave(id, candidate, config))
    val stored = _success(ContentBodyStoragePolicy.prepareForSave(id, candidate, config))
    val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, stored))
    val savedside = _load_overflow(id)
    val repeatedcandidate = _repeat_payload(stored, text, s" \t$charset\r\n ")
    val repeatedplan = _success(ContentBodyStoragePolicy.planForVersionedSave(id, repeatedcandidate, config))
    val repeated = _success(ContentBodyStoragePolicy.prepareForSave(id, repeatedcandidate, config))
    val repeatedhydrated = _success(ContentBodyStoragePolicy.hydrate(id, repeated))
    val repeatedside = _load_overflow(id)

    Then("original text, independently encoded bytes/digest, trimmed metadata and side intent correspond")
    _require_body(id, planned, text, charset, threshold)
    _require_body(id, repeatedplan, text, charset, threshold)
    hydrated.getString("content") shouldBe Some(text)
    repeatedhydrated.getString("content") shouldBe Some(text)
    withClue(s"planned keys=${planned.record.fields.map(_.key)}; stored keys=${stored.fields.map(_.key)}: ") {
      (stored.fields == planned.record.fields) shouldBe true
      (repeatedplan.record.fields == planned.record.fields) shouldBe true
      (repeated.fields == stored.fields) shouldBe true
      (repeatedhydrated.fields == hydrated.fields) shouldBe true
      (repeatedplan.sideEffects == planned.sideEffects) shouldBe true
    }
    val expectedhydrated = if (overflow) stored.fields :+ Field("content", Field.Value.Single(text)) else stored.fields
    (hydrated.fields == expectedhydrated) shouldBe true
    (savedside.map(_.fields) == expectedside.map(_.fields)) shouldBe true
    (repeatedside.map(_.fields) == expectedside.map(_.fields)) shouldBe true
    if (overflow) {
      planned.sideEffects should have size 1
      val save = planned.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save]
      save.collection shouldBe a[DataStore.CollectionId.Instance]
      save.collection.collectionName shouldBe "cncf_content_body_overflow"
      (save.record.fields == expectedside.get.fields) shouldBe true
    }
    And("every nonowned Field retains its complete ordered value, including both raw SQL NULLs")
    Vector(planned.record, stored, hydrated, repeatedplan.record, repeated, repeatedhydrated).foreach { record =>
      withClue(s"nonowned keys=${_payload_nonowned(record).map(_.key)}: ") {
        (_payload_nonowned(record) == nonowned) shouldBe true
      }
    }
  }

  private def _payload_nonowned(record: Record): Vector[Field] =
    record.fields.filterNot(field => _owned_keys.contains(_normalize(field.key)) || _normalize(field.key) == "content")

  private def _repeat_payload(record: Record, text: String, charset: String): Record = {
    val fields = record.fields.map { field =>
      _normalize(field.key) match {
        case "content" => field.copy(value = Field.Value.Single(text))
        case "contentcharset" => field.copy(value = Field.Value.Single(charset))
        case _ => field
      }
    }
    val content = if (fields.exists(field => _normalize(field.key) == "content")) Vector.empty
      else Vector(Field("content", Field.Value.Single(text)))
    val charsetfields = if (fields.exists(field => _normalize(field.key) == "contentcharset")) Vector.empty
      else Vector(Field("content_charset", Field.Value.Single(charset)))
    Record(fields ++ content ++ charsetfields)
  }

  private def _load_overflow(id: EntityId)(using ctx: ExecutionContext): Option[Record] = {
    val collection = DataStore.CollectionId("cncf_content_body_overflow")
    val datastore = _success(ctx.dataStoreSpace.dataStore(collection))
    _success(datastore.load(collection, DataStore.StringEntryId(s"${id.value}:content")))
  }

  private val _owned_keys = Set("contentbytesize", "contentdigest", "contentcharset", "contentstorage", "contentref")
  private val _business_fields = Vector(
    Field("title", Field.Value.Single("Retained business")),
    Field("business_null", Field.Value.Single(null)),
    Field("audit_null", Field.Value.Single(null)),
    Field("audit_number", Field.Value.Single(41L)))

  private def _normalize(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)

  private def _replace_content(record: Record, text: String, charset: String): Record =
    Record(record.fields.filterNot(field => Set("content", "contentcharset").contains(_normalize(field.key)))) ++
      Record.dataAuto("content" -> text, "content_charset" -> charset)

  private def _require_body(id: EntityId, preparation: ContentBodyStoragePolicy.VersionedPreparation,
    text: String, charset: String, threshold: Int): Unit = {
    val bytes = text.getBytes(Charset.forName(charset))
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
    val overflow = bytes.length > threshold
    val metadata = preparation.record.fields.filter(field => _owned_keys.contains(_normalize(field.key)))
    metadata.groupBy(field => _normalize(field.key)).values.foreach(_.size shouldBe 1)
    metadata.map(field => _normalize(field.key)).toSet shouldBe
      (if (overflow) _owned_keys else _owned_keys - "contentref")
    preparation.record.getInt("content_byte_size") shouldBe Some(bytes.length)
    preparation.record.getString("content_digest") shouldBe Some(digest)
    preparation.record.getString("content_charset") shouldBe Some(Charset.forName(charset).name())
    preparation.record.getString("content_storage") shouldBe Some(if (overflow) "overflow" else "inline")
    if (overflow) {
      val ref = s"${id.value}:content"
      preparation.record.fields.exists(field => _normalize(field.key) == "content") shouldBe false
      preparation.record.getString("content_ref") shouldBe Some(ref)
      preparation.sideEffects should have size 1
      val save = preparation.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save]
      save.entryId.print shouldBe ref
      save.record.getString("content") shouldBe Some(text)
      save.record.getInt("content_byte_size") shouldBe Some(bytes.length)
      save.record.getString("content_digest") shouldBe Some(digest)
      save.record.getString("content_charset") shouldBe Some(Charset.forName(charset).name())
    } else {
      preparation.record.getString("content") shouldBe Some(text)
      preparation.record.getString("content_ref") shouldBe None
      preparation.sideEffects shouldBe ContentBodyStoragePolicy.planForDelete(id)
    }
    preparation.record.fields.filter(field => _business_fields.exists(_.key == field.key)) shouldBe _business_fields
  }

  private def _id(entropy: String): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "content", EntityCollectionId("test", "content", "article"),entropy = entropy)

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(c) => fail(c.toString)
    }
}

private object ContentBodyStoragePolicySpec {
  final case class PayloadSample(core: String, prefix: String, suffix: String, charset: String)
  given Shrink[PayloadSample] = Shrink { sample =>
    Stream(
      sample.copy(core = sample.core.take(math.max(1, sample.core.length / 2))),
      sample.copy(prefix = sample.prefix.take(math.max(1, sample.prefix.length / 2))),
      sample.copy(suffix = sample.suffix.take(math.max(1, sample.suffix.length / 2))))
      .filter(_ != sample)
  }

  final case class Sample(oldtext: String, nexttext: String, charset: String, threshold: Int)
  given Shrink[Sample] = Shrink { sample =>
    Stream(
      sample.copy(oldtext = sample.oldtext.take(math.max(1, sample.oldtext.length / 2))),
      sample.copy(nexttext = sample.nexttext.take(math.max(1, sample.nexttext.length / 2))),
      sample.copy(threshold = math.max(1, sample.threshold / 2)))
      .filter(_ != sample)
  }
}
