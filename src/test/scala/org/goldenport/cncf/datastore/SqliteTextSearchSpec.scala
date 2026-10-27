package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class SqliteTextSearchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DataStoreTextSearchFieldSpec.*
  import TextSearchSqlSpecification.*

  "E4 SQLite literal text search" should afterWord(
    "in spec:datastore-text-search, example:E4, rules:R1,R4, phase:3"
  ) {
    "literal operators and explicit Like" which {
      "preserve literal symbols, counts, order, pages and original cells" in {
        Given("docs/spec/datastore-text-search.md R1,R4 E4: an owned SQLite database and literal-symbol decoys")
        _with_stores { (store, reader) =>
          literalNeedles.zipWithIndex.foreach { case (needle, index) =>
            val collection = DataStore.CollectionId("literal_" + index)
            val originals = literalRows(needle)
            seed(store, collection, originals, "source_text")
            Vector(false, true).foreach { policy =>
              literalPredicates(needle, policy).zipWithIndex.foreach { case (predicate, operation) =>
                When("SQLite interprets a literal predicate with normal ordering, count and paging")
                val observed = observe(store, reader, collection, predicate, "source_text", originals.size)
                Then("IDs equal independent String expectations and stored source text is unchanged")
                val ids = expectedIds(originals, needle, policy, operation)
                observed.ids shouldBe ids
                observed.count shouldBe ids.size
                observed.page shouldBe ids.drop(1).take(2)
                observed.cells shouldBe originals
              }
            }
          }
        }
      }

      "retain underscore and percent wildcard meaning for explicit Like" in {
        Given("docs/spec/datastore-text-search.md R1 E4: explicit-pattern rows in an owned SQLite database")
        _with_stores { (store, reader) =>
          val collection = DataStore.CollectionId("like_control")
          val originals = identified(Vector("abc", "a_c", "a%c", "axxc", "zz"))
          seed(store, collection, originals, "source_text")
          Vector("a_c" -> originals.take(3).map(_._1), "a%c" -> originals.take(4).map(_._1)).foreach {
            case (pattern, ids) =>
              When("the caller supplies an explicit Like pattern")
              val observed = observe(store, reader, collection, EntityQuery.Like("source_text", pattern),
                "source_text", originals.size)
              Then("wildcards still select the expected rows, count and page")
              observed.ids shouldBe ids
              observed.count shouldBe ids.size
              observed.page shouldBe ids.drop(1).take(2)
              observed.cells shouldBe originals
          }
        }
      }
    }
  }

  "E5 SQLite declared search projection" should afterWord(
    "in spec:datastore-text-search, example:E5, rules:R2,R3,R4, phase:3"
  ) {
    "complete UTF16 carried as physical ASCII" which {
      "retain control, surrogate and Unicode semantics through a fresh provider" in {
        Given("docs/spec/datastore-text-search.md R2,R3,R4 E5: original JVM examples and both field policies")
        _with_stores { (store, reader) =>
          Vector(false, true).foreach { policy =>
            val collection = DataStore.CollectionId("fixed_" + policy)
            val field = DataStoreTextSearchField("search_text", policy)
            val originals = identified(fixedSources)
            val projected = originals.map { case (id, source) => id -> field.toStoreValue(source) }
            seed(store, collection, projected, field.path)
            (fixedSources ++ Vector("\u2345", "\ude00", "\u0301", "i", "\u03a3")).foreach { needle =>
              predicates(field, needle).zipWithIndex.foreach { case (predicate, operation) =>
                When("SQLite searches the declared projection using the common field API")
                val observed = observe(store, reader, collection, predicate, field.path, originals.size)
                Then("the SQL result matches original String semantics and reads preserve the exact ASCII cell")
                val ids = expectedIds(originals, needle, policy, operation)
                observed.ids shouldBe ids
                observed.count shouldBe ids.size
                observed.page shouldBe ids.drop(1).take(2)
                observed.cells shouldBe projected
                observed.cells.forall(_._2.forall(_ <= 127)) shouldBe true
              }
            }
          }
        }
      }

      "match at least 100 undiscarded arbitrary UTF16 sources and both needle families" in {
        Given("docs/spec/datastore-text-search.md R2,R3,R4 E5: unfiltered UTF16 sources with arbitrary substring boundaries")
        _with_stores { (store, reader) =>
          var serial = 0
          val property = Prop.forAll(samples) { sample =>
            serial += 1
            Vector(false, true).foreach { policy =>
              val collection = DataStore.CollectionId("arbitrary_" + serial + "_" + policy)
              val field = DataStoreTextSearchField("search_text", policy)
              val originals = identified(Vector(sample.source, "p" + sample.source + "s", sample.needle, ""))
              val projected = originals.map { case (id, source) => id -> field.toStoreValue(source) }
              seed(store, collection, projected, field.path)
              Vector(sample.substring, sample.needle).foreach { needle =>
                predicates(field, needle).zipWithIndex.foreach { case (predicate, operation) =>
                  When("SQLite evaluates a common projection predicate for an arbitrary UTF16 sample")
                  val observed = observe(store, reader, collection, predicate, field.path, originals.size)
                  Then("the independently folded original Strings determine exact IDs, count and page")
                  val ids = expectedIds(originals, needle, policy, operation)
                  observed.ids shouldBe ids
                  observed.count shouldBe ids.size
                  observed.page shouldBe ids.drop(1).take(2)
                  observed.cells shouldBe projected
                  observed.cells.forall(_._2.forall(_ <= 127)) shouldBe true
                }
              }
            }
            true
          }
          When("the real SQLite property checks at least 100 samples")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("every sample passes without a discard")
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _with_stores(body: (SqlDataStore, SqlDataStore) => Unit): Unit = {
    val work = Files.createDirectories(Path.of("target", "text-search", "work"))
    val path = Files.createTempFile(work, "sqlite-text-search-", ".db")
    try {
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val reader = SqlDataStore.sqlite(path.toString)
        try body(store, reader) finally success(reader.closeC())
      } finally success(store.closeC())
    } finally Files.deleteIfExists(path)
  }
}

private[datastore] object TextSearchSqlSpecification {
  final case class Observation(ids: Vector[String], count: Int, page: Vector[String], cells: Vector[(String, String)])

  val literalNeedles: Vector[String] = Vector("%", "_", "!", "\\", "'", "a%_!\\'b", "")

  def identified(values: Vector[String]): Vector[(String, String)] =
    values.zipWithIndex.map { case (value, index) => ("r" + (1000 + index)) -> value }

  def literalRows(needle: String): Vector[(String, String)] = identified(Vector(
    needle, needle + "tail", "head" + needle, "head" + needle + "tail", "unrelated", "",
    needle.replace('%', 'p').replace('_', 'q').replace('!', 'z'), "other\\slash'quote"))

  def literalPredicates(needle: String, caseInsensitive: Boolean): Vector[EntityQuery.Expr] = Vector(
    EntityQuery.Contains("source_text", needle, caseInsensitive),
    EntityQuery.StartsWith("source_text", needle, caseInsensitive),
    EntityQuery.EndsWith("source_text", needle, caseInsensitive))

  def expectedIds(originals: Vector[(String, String)], needle: String, caseInsensitive: Boolean,
    operation: Int): Vector[String] = originals.collect {
      case (id, source) if DataStoreTextSearchFieldSpec.expected(source, needle, caseInsensitive)(operation) => id
    }

  def success[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case _ => throw new IllegalStateException("owned text-search datastore operation failed")
  }

  def seed(store: SqlDataStore, collection: DataStore.CollectionId, values: Vector[(String, String)],
    columnName: String): Unit = {
    given ExecutionContext = ExecutionContext.create()
    values.foreach { case (id, value) =>
      success(store.create(collection, DataStore.StringEntryId(id), Record.data("id" -> id, columnName -> value)))
    }
  }

  def observe(store: SqlDataStore, reader: SqlDataStore, collection: DataStore.CollectionId,
    predicate: EntityQuery.Expr, columnName: String, rowCount: Int): Observation = {
    given ExecutionContext = ExecutionContext.create()
    val directive = QueryDirective(Query.Expr(predicate), order = QueryOrder.By("id", OrderDirection.Asc))
    val found = success(store.search(collection, directive)).records.map(_.getString("id").get)
    val count = success(store.count(collection, directive))
    val page = success(store.search(collection,
      directive.copy(limit = QueryLimit.Limit(2), offset = 1))).records.map(_.getString("id").get)
    val cells = (0 until rowCount).toVector.map { index =>
      val id = "r" + (1000 + index)
      val normal = success(store.load(collection, DataStore.StringEntryId(id))).get.getString(columnName).get
      val fresh = success(reader.load(collection, DataStore.StringEntryId(id))).get.getString(columnName).get
      if (normal != fresh) throw new IllegalStateException("owned provider readers disagree on the stored cell")
      id -> fresh
    }
    Observation(found, count, page, cells)
  }
}
