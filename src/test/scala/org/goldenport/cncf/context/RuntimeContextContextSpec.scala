package org.goldenport.cncf.context

import java.time.{Instant, ZoneId}
import java.util.Locale
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  5, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeContextContextSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "RuntimeContext.Context" should {
    "apply default snake_case output naming" in {
      Given("a record with canonical camelCase keys")
      val record = Record.dataAuto(
        "totalCount" -> 2,
        "traceId" -> "trace-1"
      )

      When("transforming the record with the default runtime context")
      val transformed = RuntimeContext.Context.default.transformRecord(record)

      Then("keys are converted to snake_case")
      transformed.getInt("total_count") shouldBe Some(2)
      transformed.getString("trace_id") shouldBe Some("trace-1")
    }

    "format temporal values with the configured timezone" in {
      Given("a formatting context with Asia/Tokyo timezone")
      val ctx = RuntimeContext.Context(
        formatting = RuntimeContext.FormattingContext(
          locale = Locale.US,
          timezone = ZoneId.of("Asia/Tokyo")
        )
      )
      val record = Record.dataAuto(
        "updatedAt" -> Instant.parse("2026-04-05T00:00:00Z")
      )

      When("transforming the record")
      val transformed = ctx.transformRecord(record)

      Then("the timestamp is rendered as a timezone-adjusted string")
      transformed.getString("updated_at").map(_.replace('\u202f', ' ')) shouldBe Some("Apr 5, 2026, 9:00:00 AM")
    }

    "optionally render numbers as localized strings" in {
      Given("a formatting context configured for localized number strings")
      val ctx = RuntimeContext.Context(
        formatting = RuntimeContext.FormattingContext(
          locale = Locale.US,
          numberStyle = RuntimeContext.NumberStyle.LocalizedString
        )
      )
      val record = Record.dataAuto(
        "totalCount" -> 12345,
        "score" -> BigDecimal("1234.5")
      )

      When("transforming the record")
      val transformed = ctx.transformRecord(record)

      Then("numbers are formatted using the locale")
      transformed.getString("total_count") shouldBe Some("12,345")
      transformed.getString("score") shouldBe Some("1,234.5")
    }

    "resolve i18n messages with fallback default text" in {
      Given("an i18n context with one registered message")
      val i18n = RuntimeContext.I18nContext(
        locale = Locale.JAPAN,
        messages = Map("view.cache.hit" -> "キャッシュヒット")
      )

      When("looking up known and unknown keys")
      val known = i18n.message("view.cache.hit", "cache hit")
      val unknown = i18n.message("view.cache.miss", "cache miss")

      Then("registered messages are returned and unknown keys fall back")
      known shouldBe "キャッシュヒット"
      unknown shouldBe "cache miss"
    }
  }
}
