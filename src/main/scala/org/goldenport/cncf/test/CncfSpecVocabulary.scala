package org.goldenport.cncf.test

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.record.Record
import org.goldenport.test.matchers.SpecVocabulary
import org.scalatest.matchers.{MatchResult, Matcher}

/*
 * @since   Jun. 28, 2026
 * @version Jun. 28, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Shared executable-spec vocabulary for CNCF consumers.
 *
 * This trait intentionally lives in src/main of goldenport-cncf. Downstream
 * component projects such as SIE need to reuse the vocabulary from their own
 * test suites, and adding a separate testkit artifact would increase the
 * launcher/runtime dependency surface. The package is reserved for test code;
 * production code should not depend on org.goldenport.cncf.test.
 *
 * See docs/design/cncf-test-vocabulary.md for the design decision.
 */
trait CncfSpecVocabulary extends SpecVocabulary {
  protected final def json_content(record: Record): Json =
    parse(record.getString("content").getOrElse("")).toOption.get

  protected final def contain_operation(expected: String): Matcher[Iterable[String]] = Matcher { actual =>
    MatchResult(
      actual.exists(_ == expected),
      s"""operation definitions did not contain "$expected"""",
      s"""operation definitions contained "$expected""""
    )
  }

  protected final def advertise_mcp_tool(expected: String): Matcher[Json] = Matcher { actual =>
    val names =
      actual.hcursor
        .downField("tools")
        .focus
        .flatMap(_.asArray)
        .getOrElse(Vector.empty)
        .flatMap(_.hcursor.get[String]("name").toOption)
    MatchResult(
      names.contains(expected),
      s"""MCP tool list did not advertise "$expected"""",
      s"""MCP tool list advertised "$expected""""
    )
  }

  protected final def have_json_string(field: String): Matcher[Json] = Matcher { actual =>
    MatchResult(
      actual.hcursor.get[String](field).isRight,
      s"""JSON did not contain string field "$field"""",
      s"""JSON contained string field "$field""""
    )
  }

  protected final def have_json_string_value(
    field: String,
    expected: String
  ): Matcher[Json] = Matcher { actual =>
    val value = actual.hcursor.get[String](field)
    MatchResult(
      value.contains(expected),
      s"""JSON field "$field" was ${value.fold(_.message, identity)} instead of "$expected"""",
      s"""JSON field "$field" was "$expected""""
    )
  }

  protected final def have_json_int_value(
    field: String,
    expected: Int
  ): Matcher[Json] = Matcher { actual =>
    val value = actual.hcursor.get[Int](field)
    MatchResult(
      value.contains(expected),
      s"""JSON field "$field" was ${value.fold(_.message, _.toString)} instead of $expected""",
      s"""JSON field "$field" was $expected"""
    )
  }

  protected final def have_json_nested_int(
    field: String,
    nestedfield: String
  ): Matcher[Json] = Matcher { actual =>
    val value = actual.hcursor.downField(field).get[Int](nestedfield)
    MatchResult(
      value.isRight,
      s"""JSON object "$field" did not contain int field "$nestedfield"""",
      s"""JSON object "$field" contained int field "$nestedfield""""
    )
  }

  protected final def have_record_string_value(
    field: String,
    expected: String
  ): Matcher[Record] = Matcher { actual =>
    val value = actual.getString(field)
    MatchResult(
      value.contains(expected),
      s"""record field "$field" was ${value.getOrElse("<missing>")} instead of "$expected"""",
      s"""record field "$field" was "$expected""""
    )
  }

  protected final def have_record_string_containing(
    field: String,
    expected: String
  ): Matcher[Record] = Matcher { actual =>
    val value = actual.getString(field)
    MatchResult(
      value.exists(_.contains(expected)),
      s"""record field "$field" did not contain "$expected": ${value.getOrElse("<missing>")}""",
      s"""record field "$field" contained "$expected""""
    )
  }

  protected final def have_record_string(field: String): Matcher[Record] = Matcher { actual =>
    val value = actual.getString(field)
    MatchResult(
      value.isDefined,
      s"""record did not contain string field "$field"""",
      s"""record contained string field "$field""""
    )
  }

  protected final def have_record_int_value(
    field: String,
    expected: Int
  ): Matcher[Record] = Matcher { actual =>
    val value = actual.getInt(field)
    MatchResult(
      value.contains(expected),
      s"""record field "$field" was ${value.map(_.toString).getOrElse("<missing>")} instead of $expected""",
      s"""record field "$field" was $expected"""
    )
  }

  protected final def have_record_int_at_least(
    field: String,
    expected: Int
  ): Matcher[Record] = Matcher { actual =>
    val value = actual.getInt(field)
    MatchResult(
      value.exists(_ >= expected),
      s"""record field "$field" was ${value.map(_.toString).getOrElse("<missing>")} instead of at least $expected""",
      s"""record field "$field" was at least $expected"""
    )
  }
}
