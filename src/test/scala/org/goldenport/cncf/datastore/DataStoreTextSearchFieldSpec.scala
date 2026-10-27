package org.goldenport.cncf.datastore

import java.util.Locale
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class DataStoreTextSearchFieldSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DataStoreTextSearchFieldSpec.*

  "E1 DataStoreTextSearchField declaration" should afterWord(
    "in spec:datastore-text-search, example:E1, rules:R2, phase:3"
  ) {
    "versioned code-unit boundaries" which {
      "retain empty, NUL and individual surrogate units in ASCII" in {
        Given("docs/spec/datastore-text-search.md R2 E1: a sensitive declared search column")
        val field = DataStoreTextSearchField("search_text")
        val inputs = Vector("", "A", "\u0000", "\ud800", "\udc00", "\ud83d\ude00", " \t")
        When("each admitted String is projected")
        val stored = inputs.map(field.toStoreValue)
        Then("version 1 preserves each UTF16 unit without a charset conversion")
        stored shouldBe Vector("u16x1:", "u16x1:x0041", "u16x1:x0000", "u16x1:xd800",
          "u16x1:xdc00", "u16x1:xd83dxde00", "u16x1:x0020x0009")
        stored.forall(_.forall(_ <= 127)) shouldBe true
      }

      "reject blank or padded declaration paths" in {
        Given("docs/spec/datastore-text-search.md R2 E1: paths supplied by a declaration owner")
        val paths = Vector("", " ", "\t", " search_text", "search_text ")
        When("invalid declarations are constructed")
        val attempts = paths.map(path => scala.util.Try(DataStoreTextSearchField(path)))
        Then("each broken declaration invariant is detected")
        attempts.foreach(_.failed.get shouldBe a[IllegalArgumentException])
      }
    }
  }

  "E2 DataStoreTextSearchField policy" should afterWord(
    "in spec:datastore-text-search, example:E2, rules:R2,R3, phase:3"
  ) {
    "sensitive and Locale.ROOT declarations" which {
      "pair the declared fold with ordinary case-sensitive predicates" in {
        Given("docs/spec/datastore-text-search.md R3 E2: sensitive and ROOT-folded columns")
        val sensitive = DataStoreTextSearchField("search_text")
        val folded = DataStoreTextSearchField("search_text", caseInsensitive = true)
        When("A and dotted-I are projected and A predicates are built")
        val values = Vector(sensitive.toStoreValue("A"), folded.toStoreValue("A"), folded.toStoreValue("\u0130"))
        val predicates = Vector(folded.contains("A"), folded.startsWith("A"), folded.endsWith("A"))
        Then("ROOT folding precedes encoding and database case folding is disabled")
        values shouldBe Vector("u16x1:x0041", "u16x1:x0061", "u16x1:x0069x0307")
        predicates shouldBe Vector(EntityQuery.Contains("search_text", "x0061", false),
          EntityQuery.StartsWith("search_text", "u16x1:x0061", false),
          EntityQuery.EndsWith("search_text", "x0061", false))
      }

      "match empty, control, surrogate, combining, non-BMP and contextual-case examples" in {
        Given("docs/spec/datastore-text-search.md R2,R3 E2: original JVM Strings under both policies")
        val pairs = fixedSources.flatMap(source => Vector("", source, source.take(1), "\u0000", "\ud800",
          "\udc00", "\u0301", "\ude00", "\u03a3", "i", "\u0130").map(source -> _)) :+
          ("\u1234\u5678" -> "\u2345")
        pairs.foreach { case (source, needle) =>
          Vector(false, true).foreach { policy =>
            When("the framework predicates are evaluated against only the stored projection")
            val observed = evaluated(source, needle, policy)
            Then("each result agrees with the independent original String primitive")
            observed shouldBe expected(source, needle, policy)
          }
        }
      }
    }
  }

  "E3 DataStoreTextSearchField arbitrary UTF16" should afterWord(
    "in spec:datastore-text-search, example:E3, rules:R2,R3, phase:3"
  ) {
    "unfiltered code-unit generation" which {
      "preserve all three String predicates for positive substrings and independent needles" in {
        Given("docs/spec/datastore-text-search.md R2,R3 E3: every UTF16 unit and arbitrary substring boundaries")
        val property = Prop.forAll(samples) { sample =>
          Vector(false, true).foreach { policy =>
            Vector(sample.substring, sample.needle).foreach { needle =>
              When("the projected source is queried under its declared policy")
              val observed = evaluated(sample.source, needle, policy)
              Then("contains, startsWith and endsWith equal independently folded String results")
              observed shouldBe expected(sample.source, needle, policy)
            }
          }
          true
        }
        When("at least 100 undiscarded arbitrary UTF16 samples are checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        Then("the property passes with no discarded inputs")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }
}

private[datastore] object DataStoreTextSearchFieldSpec {
  final case class Sample(source: String, substring: String, needle: String)

  val text: Gen[String] = Gen.choose(0, 24).flatMap(size =>
    Gen.listOfN(size, Gen.choose(0, 65535).map(_.toChar)).map(_.mkString))

  val samples: Gen[Sample] = for {
    source <- text
    start <- Gen.choose(0, source.length)
    end <- Gen.choose(start, source.length)
    needle <- text
  } yield Sample(source, source.substring(start, end), needle)

  val fixedSources: Vector[String] = Vector("", " \t\n", "a\u0000b", "\ud800", "\udc00",
    "\ud800A\udc00", "\ud83d\ude00", "e\u0301", "\u00e9", "\u039f\u03a3", "\u03a3\u03bf\u03a3",
    "\u0130I\u0131i", "\u1234\u5678", "u16x1:x0000%_!\\'")

  def normalized(value: String, caseInsensitive: Boolean): String =
    if (caseInsensitive) value.toLowerCase(Locale.ROOT) else value

  def expected(source: String, needle: String, caseInsensitive: Boolean): Vector[Boolean] = {
    val value = normalized(source, caseInsensitive)
    val token = normalized(needle, caseInsensitive)
    Vector(value.contains(token), value.startsWith(token), value.endsWith(token))
  }

  def predicates(field: DataStoreTextSearchField, needle: String): Vector[EntityQuery.Expr] =
    Vector(field.contains(needle), field.startsWith(needle), field.endsWith(needle))

  def evaluated(source: String, needle: String, caseInsensitive: Boolean): Vector[Boolean] = {
    val field = DataStoreTextSearchField("search_text", caseInsensitive)
    val record = Record.data("search_text" -> field.toStoreValue(source))
    predicates(field, needle).map(EntityQuery.eval(_, record))
  }
}
