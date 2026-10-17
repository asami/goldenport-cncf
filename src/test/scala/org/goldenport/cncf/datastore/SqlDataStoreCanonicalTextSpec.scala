package org.goldenport.cncf.datastore

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  1, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class SqlDataStoreCanonicalTextSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _collections = Vector(
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", "durableJob")),
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", "durableJobStorageQuota")),
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", "durableJobOwnedBlobStorageQuota")),
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", "durableJobLifetimeQuota"))
  )
  private val _modes = Vector(false, true)
  private val _raw_texts = Vector(
    "{\"z\":2,\"a\":1}",
    " [1, {\"a\":\"東京🙂\"}, true] \n",
    "{unfinished",
    "{\"a\":1,\"a\":2}",
    " \n{ \"日本語\" : \"雪☃\", \"a\" : [ 1, 2 ] }\t",
    "[not-json]",
    " \tplain 東京🙂 \n"
  )

  "Native SQLite canonical text" should {
    "ordinary reads" which {
      "N1 preserve actual String bytes through create, load and bounded full or projected search" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("one builtin collection and finite raw canonical texts under the selected naming mode")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              When("each text is created and read by load and both bounded search projections")
              _raw_texts.zipWithIndex.foreach { (raw, index) =>
                _success(store.create(collection, _entry(index), _record(raw)))
              }
              val loaded = _raw_texts.indices.map(index => _loaded(store, collection, _entry(index)))
              val full = _search(store, collection, _raw_texts.size)
              val projected = _search(store, collection, _raw_texts.size, QueryProjection.Fields(Vector("canonicalJson")))

              Then("all native values remain Strings with the submitted ordering, grammar and UTF-8 bytes")
              full.size shouldBe _raw_texts.size
              projected.size shouldBe _raw_texts.size
              loaded.zip(_raw_texts).foreach { (record, raw) => _assert_text(record, "canonicalJson", raw) }
              full.zip(_raw_texts).foreach { (record, raw) => _assert_text(record, "canonicalJson", raw) }
              projected.zip(_raw_texts).foreach { (record, raw) => _assert_text(record, "canonicalJson", raw) }
            }
          }
        }
      }

      "N2 preserve opaque object and array shaped Job IDs beside canonical text and managed revision" in {
        _modes.foreach { normalize =>
          Given("the durableJob collection with JSON shaped opaque jobId values and numeric managed revisions")
          val collection = _collections.head
          val jobids = Vector(" {\"opaque\":\"東京🙂\"} ", " [\"job\", 1] \n")
          _with_store(normalize) { (store, _) =>
            given ExecutionContext = ExecutionContext.create()
            When("the native rows are created, loaded and searched")
            jobids.zipWithIndex.foreach { (jobid, index) =>
              _success(store.create(collection, _entry(index), _record(_raw_texts(index), Vector("jobId" -> jobid))))
            }
            val loaded = jobids.indices.map(index => _loaded(store, collection, _entry(index)))
            val searched = _search(store, collection, jobids.size)

            Then("jobId and canonicalJson stay exact Strings and cncf_revision keeps its numeric storage key")
            searched.size shouldBe jobids.size
            Vector(loaded.toVector, searched).foreach { records =>
              records.zipWithIndex.foreach { (record, index) =>
                _assert_text(record, "jobId", jobids(index))
                _assert_text(record, "canonicalJson", _raw_texts(index))
                _assert_revision(record, 1L)
                if (normalize) record.getAny("cncfRevision") shouldBe None
              }
            }
          }
        }
      }

      "N3 preserve save and update replacements for an independently reopened SQLite owner" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("an actual SQLite row and replacement texts containing whitespace, duplicate keys and Unicode")
            _with_store(normalize) { (store, path) =>
              given ExecutionContext = ExecutionContext.create()
              _success(store.create(collection, _entry(0), _record("initial")))
              val savedtext = _raw_texts(4)
              val updatedtext = " \n{\"雪🙂\":1, \"雪🙂\":2} \t"

              When("save replaces the row and update then replaces only canonicalJson")
              _success(store.save(collection, _entry(0), _record(savedtext)))
              val saved = _loaded(store, collection, _entry(0))
              _success(store.update(collection, _entry(0), Record.data("canonicalJson" -> updatedtext)))
              val updated = _loaded(store, collection, _entry(0))
              _success(store.closeC())
              val fresh = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
              try {
                val reopened = _loaded(fresh, collection, _entry(0))
                val searched = _search(fresh, collection, 1)

                Then("replacement bytes survive both writes and the independent provider owner")
                _assert_text(saved, "canonicalJson", savedtext)
                _assert_text(updated, "canonicalJson", updatedtext)
                _assert_text(reopened, "canonicalJson", updatedtext)
                searched.size shouldBe 1
                _assert_text(searched.head, "canonicalJson", updatedtext)
                _assert_revision(reopened, 1L)
              } finally _success(fresh.closeC())
            }
          }
        }
      }
    }

    "authoritative native mutation readbacks" which {
      "N9 preserve semantic Job and quota text through actual strict-create acknowledgement" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("docs/spec/entity-create-atomic-provider.md CA9/CA11: a semantic builtin collection, its physical SQLite table and exact raw text under the selected naming mode")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              val jobid = " [\"opaque Job 東京🙂\", 1] \n"
              val fields = Vector("jsonText" -> "{\"ordinary\":1}") ++
                (if (collection == _collections.head) Vector("jobId" -> jobid) else Vector.empty)

              When("strict create inserts each row and returns the native authoritative result")
              val acknowledged = _raw_texts.zipWithIndex.map { (raw, index) =>
                _success(store.createEntity(EntityCreatePlan(collection, _entry(index), _record(raw, fields)))).record
              }
              val loaded = _raw_texts.indices.map(index => _loaded(store, collection, _entry(index)))

              Then("acknowledgement and independent native reads retain exact semantic text while ordinary JSON still decodes")
              acknowledged.zipWithIndex.foreach { (record, index) =>
                record.fields shouldBe loaded(index).fields
                record.getString("id") shouldBe Some(_entry(index).print)
                _assert_text(record, "canonicalJson", _raw_texts(index))
                _assert_revision(record, 1L)
                record.getRecord("jsonText").flatMap(_.getInt("ordinary")) shouldBe Some(1)
                if (collection == _collections.head) _assert_text(record, "jobId", jobid)
              }
            }
          }
        }
      }

      "N4 preserve exact CAS text at revision two and leave it untouched after a stale revision one attempt" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("a builtin native row at cncf_revision one")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              _success(store.create(collection, _entry(0), _record("before")))
              val raw = _raw_texts(4)
              val plan = EntityCompareAndSetMutationPlan(
                collection, _entry(0), "cncf_revision", EntityRevision.INITIAL,
                Record.data("canonicalJson" -> raw), EntityMutationReadbackRequirement.AuthoritativeRecord
              )

              When("a matching native CAS returns its authoritative record")
              val applied = _success(store.compareAndSetEntity(plan))
              val acknowledged = _authoritative(applied)
              Then("the transactional readback carries exact canonical text and revision two")
              _assert_text(acknowledged, "canonicalJson", raw)
              _assert_revision(acknowledged, 2L)

              When("another native CAS uses stale revision one with different submitted bytes")
              val stale = _success(store.compareAndSetEntity(plan.copy(changes = Record.data("canonicalJson" -> "[\"stale\"]"))))
              val loaded = _loaded(store, collection, _entry(0))
              val searched = _search(store, collection, 1)
              Then("Stale reports the observed revision and neither text nor revision changes")
              stale shouldBe EntityMutationProviderResult.Stale(EntityRevision.INITIAL, _revision(2L))
              _assert_text(loaded, "canonicalJson", raw)
              _assert_revision(loaded, 2L)
              searched.size shouldBe 1
              _assert_text(searched.head, "canonicalJson", raw)
              _assert_revision(searched.head, 2L)
            }
          }
        }
      }

      "N5 preserve direct mutation text in the shared transactional authoritative reader" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("a builtin native row at revision one and malformed duplicate-like replacement text")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              _success(store.create(collection, _entry(0), _record("before")))
              val raw = " \t[\"東京🙂\", {\"a\":1,\"a\":2}, broken] \n"

              When("mutateEntityDirect requests an authoritative record")
              val result = _success(store.mutateEntityDirect(EntityDirectMutationPlan(
                collection, _entry(0), "cncf_revision", Record.data("canonicalJson" -> raw),
                EntityMutationReadbackRequirement.AuthoritativeRecord
              )))
              val acknowledged = _authoritative(result)
              val loaded = _loaded(store, collection, _entry(0))
              val searched = _search(store, collection, 1)

              Then("acknowledgement, load and search agree on raw text and the advanced revision")
              _assert_text(acknowledged, "canonicalJson", raw)
              _assert_revision(acknowledged, 2L)
              _assert_text(loaded, "canonicalJson", raw)
              _assert_revision(loaded, 2L)
              searched.size shouldBe 1
              _assert_text(searched.head, "canonicalJson", raw)
              _assert_revision(searched.head, 2L)
            }
          }
        }
      }
    }

    "legacy structured value compatibility" which {
      "N6 decode matching Instance names and unrelated EntityStore identities as ordinary JSON" in {
        val collections = Vector(
          DataStore.CollectionId("durableJob"),
          DataStore.CollectionId("durableJobStorageQuota"),
          DataStore.CollectionId("durableJobOwnedBlobStorageQuota"),
          DataStore.CollectionId("durableJobLifetimeQuota"),
          DataStore.CollectionId.EntityStore(EntityCollectionId("other", "builtin", "durableJobLifetimeQuota")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "other", "durableJobLifetimeQuota")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("other", "builtin", "durableJobOwnedBlobStorageQuota")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "other", "durableJobOwnedBlobStorageQuota")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("other", "builtin", "durableJob")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "other", "durableJobStorageQuota")),
          DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "builtin", "other"))
        )
        _modes.foreach { normalize =>
          collections.foreach { collection =>
            Given("an isolated database with an ordinary collection and object or array canonicalJson spelling")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              When("ordinary native records are created and read")
              _success(store.create(collection, _entry(0), _record(" {\"name\":\"ordinary\"} ")))
              _success(store.create(collection, _entry(1), _record(" [1, \"ordinary\"] ")))
              val objectrecord = _loaded(store, collection, _entry(0))
              val arrayrecord = _loaded(store, collection, _entry(1))
              val searched = _search(store, collection, 2)

              Then("the exemption does not follow a table name or field spelling alone")
              objectrecord.getAny("canonicalJson").get shouldBe a[Record]
              objectrecord.getRecord("canonicalJson").flatMap(_.getString("name")) shouldBe Some("ordinary")
              arrayrecord.getVector("canonicalJson") shouldBe Some(Vector(1L, "ordinary"))
              searched.size shouldBe 2
              searched.head.getAny("canonicalJson").get shouldBe a[Record]
              searched(1).getVector("canonicalJson") shouldBe Some(Vector(1L, "ordinary"))
            }
          }
        }
      }

      "N7 retain owned, repeated and scalar JSON decoding and numeric normalization beside builtin canonical text" in {
        _modes.foreach { normalize =>
          _collections.foreach { collection =>
            Given("a builtin record with ordinary structured fields and an independently opaque canonicalJson")
            _with_store(normalize) { (store, _) =>
              given ExecutionContext = ExecutionContext.create()
              val raw = _raw_texts(3)
              val fields = Vector(
                "objectValue" -> Record.data("name" -> "record"),
                "ownedValue" -> OwnedValue("owned"),
                "repeatedValues" -> Vector(OwnedValue("first"), OwnedValue("second")),
                "scalarValues" -> Vector("東京", 2, true),
                "jsonText" -> " {\"name\":\"ordinary\"} ",
                "numberValue" -> 37,
                "flagValue" -> true,
                "noteValue" -> "before"
              )

              When("the builtin row is created, its note is set to null, and native reads return it")
              _success(store.create(collection, _entry(0), _record(raw, fields)))
              _success(store.update(collection, _entry(0), Record.dataAuto("noteValue" -> org.simplemodeling.model.directive.Update.SetNull)))
              val loaded = _loaded(store, collection, _entry(0))
              val searched = _search(store, collection, 1)

              Then("only the admitted text fields bypass the existing Record and JDBC conversion")
              searched.size shouldBe 1
              Vector(loaded, searched.head).foreach { record =>
                _assert_text(record, "canonicalJson", raw)
                _assert_revision(record, 1L)
                record.getRecord("objectValue").flatMap(_.getString("name")) shouldBe Some("record")
                record.getRecord("ownedValue").flatMap(_.getString("name")) shouldBe Some("owned")
                record.getVector("repeatedValues").get.collect { case item: Record => item.getString("name") } shouldBe Vector(Some("first"), Some("second"))
                record.getVector("scalarValues") shouldBe Some(Vector("東京", 2L, true))
                record.getRecord("jsonText").flatMap(_.getString("name")) shouldBe Some("ordinary")
                record.getAny("numberValue").get shouldBe a[java.lang.Number]
                record.getInt("numberValue") shouldBe Some(37)
                record.getInt("flagValue") shouldBe Some(1)
                record.getAny("noteValue").filter(_ != null) shouldBe None
                if (normalize) record.getAny("object_value") shouldBe None
              }
            }
          }
        }
      }
    }

    "generated native byte preservation" which {
      "N8 preserve finite seeded JSON-like strings across all four builtin collections, naming modes and fresh owners" in {
        Given("seeded finite raw strings with whitespace, duplicate keys, malformed fragments and multibyte text")
        val generator = for {
          padding <- Gen.oneOf("", " ", "\t", "\n ")
          text <- Gen.oneOf("東京🙂", "雪☃", "é", "plain")
          value <- Gen.chooseNum(0, 9999)
          shape <- Gen.oneOf("object", "array", "broken")
        } yield {
          val body = shape match {
            case "object" => s"{ \"key\" : \"$text\", \"n\" : $value, \"n\" : 0 }"
            case "array" => s"[ \"$text\", $value, { \"n\":1, \"n\":2 } ]"
            case _ => s"{ \"$text\" : [ $value, broken }"
          }
          padding + body + padding
        }
        val property = Prop.forAll(generator) { raw =>
          _modes.foreach { normalize =>
            _collections.foreach { collection =>
              Given("one generated raw value in a test-owned SQLite file")
              _with_store(normalize) { (store, path) =>
                given ExecutionContext = ExecutionContext.create()
                When("the actual row is created, loaded, searched and reopened by a fresh owner")
                _success(store.create(collection, _entry(0), _record(raw)))
                val loaded = _loaded(store, collection, _entry(0))
                val searched = _search(store, collection, 1)
                _success(store.closeC())
                val fresh = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
                try {
                  val reopened = _loaded(fresh, collection, _entry(0))
                  val projected = _search(fresh, collection, 1, QueryProjection.Fields(Vector("canonicalJson")))
                  Then("actual String content and UTF-8 bytes equal the submitted generated evidence")
                  _assert_text(loaded, "canonicalJson", raw)
                  searched.size shouldBe 1
                  _assert_text(searched.head, "canonicalJson", raw)
                  _assert_text(reopened, "canonicalJson", raw)
                  projected.size shouldBe 1
                  _assert_text(projected.head, "canonicalJson", raw)
                } finally _success(fresh.closeC())
              }
            }
          }
          true
        }

        When("the seeded property executes twenty-four successful native scenarios")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(697093102L)), property)
        Then("every generated native scenario satisfies byte preservation")
        checked.passed shouldBe true
        checked.succeeded shouldBe 24
      }
    }
  }

  private def _with_store(normalize: Boolean)(body: (SqlDataStore, Path) => Unit): Unit = {
    val area = Path.of("target", "sql-canonical-text", "work")
    val _ = Files.createDirectories(area)
    val directory = Files.createTempDirectory(area, "native-")
    val path = directory.resolve("native.db")
    val store = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
    try body(store, path)
    finally {
      try _success(store.closeC())
      finally {
        Files.deleteIfExists(directory.resolve("native.db-wal"))
        Files.deleteIfExists(directory.resolve("native.db-shm"))
        Files.deleteIfExists(directory.resolve("native.db-journal"))
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
      }
    }
  }

  private def _entry(index: Int): DataStore.EntryId =
    DataStore.StringEntryId(f"row-$index%04d")

  private def _record(raw: String, fields: Vector[(String, Any)] = Vector.empty): Record =
    Record.dataAuto((Vector("canonicalJson" -> raw, "cncf_revision" -> 1L) ++ fields)*)

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(s"native operation should succeed: $result"))

  private def _loaded(store: SqlDataStore, collection: DataStore.CollectionId, entry: DataStore.EntryId)(using ExecutionContext): Record =
    _success(store.load(collection, entry)).getOrElse(fail("native row should exist"))

  private def _search(store: SqlDataStore, collection: DataStore.CollectionId, limit: Int, projection: QueryProjection = QueryProjection.All)(using ExecutionContext): Vector[Record] =
    _success(store.search(collection, QueryDirective(
      query = Query.Empty, projection = projection,
      order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(limit)
    ))).records

  private def _assert_text(record: Record, key: String, expected: String): Unit = {
    val actual = record.getAny(key).getOrElse(fail(s"native field should exist: $key"))
    actual shouldBe a[String]
    actual shouldBe expected
    actual match {
      case text: String => text.getBytes(UTF_8).toVector shouldBe expected.getBytes(UTF_8).toVector
      case _ => fail("native canonical evidence should be a String")
    }
  }

  private def _assert_revision(record: Record, expected: Long): Unit = {
    val actual = record.getAny("cncf_revision").getOrElse(fail("managed storage revision should exist"))
    actual shouldBe a[java.lang.Number]
    actual match {
      case number: java.lang.Number => number.longValue shouldBe expected
      case _ => fail("managed storage revision should be numeric")
    }
  }

  private def _revision(value: Long): EntityRevision =
    _success(EntityRevision.createC(value))

  private def _authoritative(result: EntityMutationProviderResult): Record =
    result match {
      case EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(record)) => record
      case _ => fail("native mutation should acknowledge an authoritative applied record")
    }

  private final case class OwnedValue(name: String) extends RecordPresentable {
    def toRecord(): Record = Record.data("name" -> name)
  }
}
