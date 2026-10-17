package org.goldenport.cncf.resource

import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceReadLimitsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Runtime resource read limits" should {
    "default to one MiB and reject every out-of-range construction" in {
      Given("the default policy, both admitted endpoints and invalid integer limits")
      val invalid = Vector(Int.MinValue, -1, 0, 16777217, Int.MaxValue)

      When("runtime policies are constructed directly and through structured validation")
      val defaults = ResourceReadLimits()
      val endpoints = Vector(1, 16777216).map(ResourceReadLimits.createC)
      val rejected = invalid.map(ResourceReadLimits.createC)
      val thrown = invalid.map(value => intercept[IllegalArgumentException](ResourceReadLimits(value)))

      Then("the default is finite, endpoints are admitted and invalid policies never exist")
      defaults.maximumBytes shouldBe 1048576
      ResourceReadLimits.default shouldBe defaults
      endpoints.flatMap(_.toOption.map(_.maximumBytes)) shouldBe Vector(1, 16777216)
      rejected.forall(_.isFaillure) shouldBe true
      thrown should have size invalid.size
    }

    "return complete exact-cap bytes and close the stream" in {
      Given("a finite stream containing exactly four bytes")
      val stream = new TrackingInputStream(4)

      When("the ordinary acquisition helper consumes it with a four-byte runtime cap")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(4))

      Then("all bytes survive and the owned stream has closed")
      result.toOption shouldBe Some(Vector.fill(4)(65.toByte))
      stream.consumed shouldBe 4
      stream.closed shouldBe true
    }

    "refuse one-byte overflow without returning partial content and close the stream" in {
      Given("a five-byte stream and a four-byte runtime cap")
      val stream = new TrackingInputStream(5)

      When("the helper attempts complete acquisition")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(4))

      Then("overflow returns only failure after consuming five bytes and releasing the stream")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
      stream.consumed shouldBe 5
      stream.closed shouldBe true
    }

    "accept immediate EOF as complete empty content and close the stream" in {
      Given("an empty stream and the minimum positive runtime cap")
      val stream = new TrackingInputStream(0)

      When("the helper reads until immediate EOF")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(1))

      Then("empty content is a successful complete value with no bytes consumed")
      result.toOption shouldBe Some(Vector.empty)
      stream.consumed shouldBe 0
      stream.closed shouldBe true
    }

    "stop at cap plus one even when size hints understate a growing source" in {
      Given("a finite source whose available hint reports zero while its body supplies a thousand bytes")
      val stream = new TrackingInputStream(1000)
      stream.available() shouldBe 0

      When("the helper reads under a seven-byte runtime cap independently of that hint")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(7))

      Then("only eight bytes are consumed, no partial content escapes, and ownership ends")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
      stream.consumed shouldBe 8
      stream.closed shouldBe true
    }

    "close a stream that raises an I/O exception during acquisition" in {
      Given("an owned stream that fails after supplying two bytes")
      val stream = new TrackingInputStream(100, 2)

      When("the helper encounters the I/O failure under a larger cap")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(16))

      Then("failure returns no bytes, closes the stream and omits the exception's private detail")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
      stream.consumed shouldBe 2
      stream.closed shouldBe true
      result match {
        case org.goldenport.Consequence.Failure(conclusion) => conclusion.display should not include "private source detail"
        case _ => fail("expected acquisition failure")
      }
    }

    "acquire the maximum admitted cap without integer overflow" in {
      Given("a finite sixteen-MiB stream and the maximum admitted runtime policy")
      val stream = new TrackingInputStream(16777216)

      When("the helper consumes the complete maximum-size resource")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(16777216))

      Then("the complete immutable value is returned and the stream is released")
      result.toOption.map(_.size) shouldBe Some(16777216)
      result.toOption.map(bytes => bytes.head -> bytes.last) shouldBe Some(65.toByte -> 65.toByte)
      stream.consumed shouldBe 16777216
      stream.closed shouldBe true
    }

    "encode preowned Unicode only when its complete UTF-8 bytes fit" in {
      Given("finite generated Unicode texts including supplementary and malformed surrogate tokens")
      val texts = Gen.listOfN(12, Gen.oneOf("a", "日", "—", "\uD83D\uDE80", "\uD800", "\n")).map(_.mkString)
      val property = Prop.forAll(texts) { text =>
        val expected = text.getBytes(StandardCharsets.UTF_8).toVector
        val exact = ResourceReadAcquisition.utf8C(text, ResourceReadLimits(expected.size))
        val excess = ResourceReadAcquisition.utf8C(text, ResourceReadLimits(expected.size - 1))
        exact.toOption.contains(expected) && excess.isFaillure && excess.toOption.isEmpty
      }

      When("at least thirty-two generated texts are encoded at equality and one byte below their size")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)
      val empty = ResourceReadAcquisition.utf8C("", ResourceReadLimits(1))

      Then("the actual encoder bytes survive exactly, overflow has no partial value, and empty text is admitted")
      checked.passed shouldBe true
      checked.succeeded should be >= 32
      empty.toOption shouldBe Some(Vector.empty)
    }
  }

  private final class TrackingInputStream(length: Int, failafter: Int = Int.MaxValue) extends InputStream {
    private var _consumed = 0
    private var _closed = false
    def consumed: Int = _consumed
    def closed: Boolean = _closed
    override def available(): Int = 0
    override def read(): Int = {
      if (_consumed >= failafter) throw new IOException("private source detail")
      else if (_consumed >= length) -1
      else {
        _consumed += 1
        65
      }
    }
    override def read(buffer: Array[Byte], offset: Int, count: Int): Int = {
      if (_consumed >= failafter) throw new IOException("private source detail")
      else if (_consumed >= length) -1
      else {
        val actual = math.min(count, math.min(length - _consumed, failafter - _consumed))
        java.util.Arrays.fill(buffer, offset, offset + actual, 65.toByte)
        _consumed += actual
        actual
      }
    }
    override def close(): Unit = { _closed = true }
  }
}
