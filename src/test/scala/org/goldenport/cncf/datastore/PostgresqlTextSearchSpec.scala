package org.goldenport.cncf.datastore

import java.util.concurrent.atomic.AtomicInteger
import org.scalacheck.{Prop, Test}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.control.NonFatal

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlTextSearchSpec extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterAll {
  import DataStoreTextSearchFieldSpec.*
  import TextSearchSqlSpecification.*
  import PostgresqlPersistenceFixture.{Database, LivePostgresqlContainer}
  import org.goldenport.cncf.directive.{Query as EntityQuery}

  private val _container = new LivePostgresqlContainer()
  private val _tokens = new AtomicInteger()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    try {
      _container.start()
      _with_database { database =>
        database.scalar("SHOW server_version_num") shouldBe "170011"
        database.scalar("SHOW fsync") shouldBe "on"
        database.scalar("SHOW synchronous_commit") shouldBe "on"
      }
    } catch {
      case NonFatal(error) =>
        _container.stop()
        throw error
    }
  }

  override protected def afterAll(): Unit = {
    try _container.stop() finally super.afterAll()
  }

  "E6 PostgreSQL literal text search" should afterWord(
    "in spec:datastore-text-search, example:E6, rules:R1,R4, phase:3"
  ) {
    "literal operators and explicit Like" which {
      "preserve literal symbols, counts, order, pages and original cells" in {
        Given("docs/spec/datastore-text-search.md R1,R4 E6: owned PostgreSQL 17.11 and literal-symbol decoys")
        _with_database { database =>
          val store = database.open()
          val reader = database.open()
          literalNeedles.foreach { needle =>
            val collection = DataStore.CollectionId(database.token("literal"))
            val originals = literalRows(needle)
            seed(store, collection, originals, "source_text")
            Vector(false, true).foreach { policy =>
              literalPredicates(needle, policy).zipWithIndex.foreach { case (predicate, operation) =>
                When("PostgreSQL interprets a literal predicate with normal ordering, count and paging")
                val observed = observe(store, reader, collection, predicate, "source_text", originals.size)
                Then("IDs equal independent String expectations and both providers read the original cells")
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
        Given("docs/spec/datastore-text-search.md R1 E6: explicit-pattern rows in owned PostgreSQL")
        _with_database { database =>
          val store = database.open()
          val reader = database.open()
          val collection = DataStore.CollectionId(database.token("like_control"))
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

  "E7 PostgreSQL declared search projection" should afterWord(
    "in spec:datastore-text-search, example:E7, rules:R2,R3,R4, phase:3"
  ) {
    "complete UTF16 carried as physical ASCII" which {
      "retain control, surrogate and Unicode semantics through a separately opened reader" in {
        Given("docs/spec/datastore-text-search.md R2,R3,R4 E7: original JVM examples and both field policies")
        _with_database { database =>
          val store = database.open()
          val reader = database.open()
          Vector(false, true).foreach { policy =>
            val collection = DataStore.CollectionId(database.token("fixed_projection"))
            val field = DataStoreTextSearchField("search_text", policy)
            val originals = identified(fixedSources)
            val projected = originals.map { case (id, source) => id -> field.toStoreValue(source) }
            seed(store, collection, projected, field.path)
            (fixedSources ++ Vector("\u2345", "\ude00", "\u0301", "i", "\u03a3")).foreach { needle =>
              predicates(field, needle).zipWithIndex.foreach { case (predicate, operation) =>
                When("PostgreSQL searches the declared projection using the common field API")
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
        Given("docs/spec/datastore-text-search.md R2,R3,R4 E7: unfiltered UTF16 sources and owned PostgreSQL")
        _with_database { database =>
          val store = database.open()
          val reader = database.open()
          val property = Prop.forAll(samples) { sample =>
            Vector(false, true).foreach { policy =>
              val collection = DataStore.CollectionId(database.token("arbitrary_projection"))
              val field = DataStoreTextSearchField("search_text", policy)
              val originals = identified(Vector(sample.source, "p" + sample.source + "s", sample.needle, ""))
              val projected = originals.map { case (id, source) => id -> field.toStoreValue(source) }
              seed(store, collection, projected, field.path)
              Vector(sample.substring, sample.needle).foreach { needle =>
                predicates(field, needle).zipWithIndex.foreach { case (predicate, operation) =>
                  When("PostgreSQL evaluates a common projection predicate for an arbitrary UTF16 sample")
                  val observed = observe(store, reader, collection, predicate, field.path, originals.size)
                  Then("the independently folded originals determine exact IDs, count, page and unchanged ASCII cells")
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
          When("the real PostgreSQL property checks at least 100 samples")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("every sample passes without a discard, cancellation or provider substitution")
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _with_database(body: Database => Unit): Unit = {
    val database = new Database(_container.getJdbcUrl, _container.getUsername, _container.getPassword, _tokens)
    try body(database) finally database.close()
  }
}
