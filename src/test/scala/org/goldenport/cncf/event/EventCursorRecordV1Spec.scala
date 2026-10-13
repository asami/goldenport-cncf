package org.goldenport.cncf.event

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 */
final class EventCursorRecordV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def record(sequence: Any, revision: Any = 1L, schema: String = EventCursorRecordV1.schema) =
    Record.dataAuto("cursor_schema" -> schema, "revision" -> revision, "last_sequence" -> sequence)

  "Pure versioned event cursor" should {
    "round-trip initial and extreme facts without numeric width or precision loss" in {
      Given("initial and extreme revision and sequence facts")
      for (r <- Vector(1L, Int.MaxValue.toLong + 1L, Long.MaxValue);
           s <- Vector(0L, 9007199254740993L, Long.MaxValue)) {
        val cursor = take(EventCursorRecordV1.createC(r, s))
        When("the canonical cursor record is decoded")
        val decoded = take(EventCursorRecordV1.decodeC(cursor.toRecord))
        Then("both counters retain their exact width and decimal-text precision")
        decoded.revision shouldBe r
        decoded.lastSequence shouldBe s
        decoded.toRecord.getString("last_sequence") shouldBe Some(s.toString)
      }
      EventCursorRecordV1.initial.revision shouldBe 1L
      EventCursorRecordV1.initial.lastSequence shouldBe 0L
      When("an integer revision with zero decimal-text sequence is decoded")
      val decodedinteger = take(EventCursorRecordV1.decodeC(record("0", 1)))
      Then("the integer revision is retained")
      decodedinteger.revision shouldBe 1L
    }

    "reject noncanonical, negative, overflowing and nontext sequences" in {
      Given("noncanonical, overflowing and nontext sequence representations")
      Vector[Any](null, "", "00", "01", "-0", "-1", "+1", " 1", "1 ", "1.0",
        "1e2", "١", "9223372036854775808", "9" * 1000, 0L, 1, 1.0, true).foreach { value =>
        When("the invalid sequence record is decoded")
        val result = EventCursorRecordV1.decodeC(record(value))
        Then("the sequence is rejected rather than normalized")
        result.toOption shouldBe None
      }
    }

    "reject invalid revision types and values rather than coercing them" in {
      Given("invalid revision representations and invalid constructor counters")
      Vector[Any](null, 0L, -1L, "1", 1.0, BigDecimal(1), true).foreach { value =>
        When("the invalid revision record is decoded")
        val result = EventCursorRecordV1.decodeC(record("0", value))
        Then("the revision is rejected rather than coerced")
        result.toOption shouldBe None
      }
      When("a zero revision is constructed")
      val zerorevision = EventCursorRecordV1.createC(0L, 0L)
      Then("the invalid revision is rejected")
      zerorevision.toOption shouldBe None
      When("a negative sequence is constructed")
      val negativesequence = EventCursorRecordV1.createC(1L, -1L)
      Then("the invalid sequence is rejected")
      negativesequence.toOption shouldBe None
    }

    "require exact logical schema and fields while ignoring field order" in {
      Given("a valid logical cursor record and reordered fields")
      val valid = EventCursorRecordV1.initial.toRecord
      When("the same fields are decoded in reverse order")
      val reordered = take(EventCursorRecordV1.decodeC(Record(valid.fields.reverse)))
      Then("the sequence is unchanged")
      reordered.lastSequence shouldBe 0L
      Vector(null, record("0", schema = "cncf.event-cursor.v2"),
        Record(valid.fields.drop(1)), valid ++ Record.dataAuto("id" -> "physical"),
        valid ++ Record.dataAuto("revision" -> 2L)).foreach { value =>
        When("missing, duplicate, physical or unknown-schema fields are decoded")
        val result = EventCursorRecordV1.decodeC(value)
        Then("the noncanonical logical record is rejected")
        result.toOption shouldBe None
      }
    }

    "propose contiguous sequences and preserve immutable expected cursor facts" in {
      Given("an immutable cursor at revision seven and sequence ten")
      val cursor = take(EventCursorRecordV1.createC(7L, 10L))
      When("two event sequences are proposed")
      val proposal = take(cursor.proposeC(2)).get
      Then("the proposal advances contiguously without changing its expected cursor")
      proposal.expected shouldBe cursor
      proposal.sequences shouldBe Vector(11L, 12L)
      proposal.next.revision shouldBe 8L
      proposal.next.lastSequence shouldBe 12L
      cursor.revision shouldBe 7L
      cursor.lastSequence shouldBe 10L
      When("the maximum bounded batch is proposed")
      val maximum = take(cursor.proposeC(256)).get
      Then("all 256 sequences are present")
      maximum.sequences.size shouldBe 256
    }

    "produce no cursor work for zero facts even when either counter is exhausted" in {
      Given("initial and exhausted cursors with no facts to publish")
      Vector(EventCursorRecordV1.initial, take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue)))
        .foreach { cursor =>
          When("zero sequences are proposed")
          val result = take(cursor.proposeC(0))
          Then("no cursor work is produced")
          result shouldBe None
        }
    }

    "reject overflow and unbounded proposals without resetting either counter" in {
      Given("cursors near revision or sequence exhaustion")
      Vector(take(EventCursorRecordV1.createC(Long.MaxValue, 0L)),
        take(EventCursorRecordV1.createC(1L, Long.MaxValue)),
        take(EventCursorRecordV1.createC(1L, Long.MaxValue - 1L))).foreach { cursor =>
        When("two more sequences would overflow a counter")
        val result = cursor.proposeC(2)
        Then("the proposal is rejected without a reset")
        result.toOption shouldBe None
      }
      Vector(-1, 257, Int.MaxValue).foreach { count =>
        When("an invalid or unbounded batch size is proposed")
        val result = EventCursorRecordV1.initial.proposeC(count)
        Then("the batch is rejected")
        result.toOption shouldBe None
      }
      val edge = take(EventCursorRecordV1.createC(Long.MaxValue - 1L, Long.MaxValue - 1L))
      When("the final representable sequence is proposed")
      val next = take(edge.proposeC(1)).get.next
      Then("both counters reach their exact maximum")
      next.revision shouldBe Long.MaxValue
      next.lastSequence shouldBe Long.MaxValue
      When("one further sequence is proposed")
      val exhausted = next.proposeC(1)
      Then("the exhausted cursor rejects further work")
      exhausted.toOption shouldBe None
    }
  }
}
