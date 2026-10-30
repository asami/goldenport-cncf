package org.goldenport.cncf.resource

import java.io.{FilterInputStream, InputStream, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceReadLimitsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Runtime resource read limits" should {
    "preflight strict UTF-8 with the File cap-first precedence and a bounded overflow count" in {
      Given("a four-byte policy without a File or storage capability")
      val scope = ResourceTextPreflight.scope(ResourceReadLimits(4))
      When("complete text, malformed small text and an oversized malformed tail are inspected")
      val accepted = Vector("", "abcd", "éé", "😀").map(scope.checkC(_).toOption.get)
      val invalid = scope.checkC("\uD800").toOption.get
      val overflow = scope.checkC("abcde" + "x" * 1000 + "\uD800").toOption.get
      Then("only admitted text has an exact size and overflow is the first observed prefix")
      accepted shouldBe Vector(ResourceTextPreflight.Accepted(0, 4),
        ResourceTextPreflight.Accepted(4, 4), ResourceTextPreflight.Accepted(4, 4),
        ResourceTextPreflight.Accepted(4, 4))
      invalid shouldBe ResourceTextPreflight.InvalidText(4)
      scope.checkC(null).toOption.get shouldBe ResourceTextPreflight.InvalidText(4)
      overflow shouldBe ResourceTextPreflight.SizeExceeded(5, 4)
    }

    "default to one MiB and reject every out-of-range construction" in {
      Given("the default policy, both admitted endpoints and invalid integer limits")
      val invalid = Vector(Int.MinValue, -1, 0, 268435457, Int.MaxValue)

      When("runtime policies are constructed directly and through structured validation")
      val defaults = ResourceReadLimits()
      val endpoints = Vector(1, 268435456).map(ResourceReadLimits.createC)
      val rejected = invalid.map(ResourceReadLimits.createC)
      val thrown = invalid.map(value => intercept[IllegalArgumentException](ResourceReadLimits(value)))

      Then("the default is finite, endpoints are admitted and invalid policies never exist")
      defaults.maximumBytes shouldBe 1048576
      ResourceReadLimits.default shouldBe defaults
      endpoints.flatMap(_.toOption.map(_.maximumBytes)) shouldBe Vector(1, 268435456)
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
      Given("a finite 256-MiB stream and the maximum admitted runtime policy")
      val stream = new TrackingInputStream(268435456)

      When("the helper consumes the complete maximum-size resource")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(268435456))

      Then("the complete immutable value is returned and the stream is released")
      result.toOption.map(_.size) shouldBe Some(268435456)
      result.toOption.map(bytes => bytes.head -> bytes.last) shouldBe Some(65.toByte -> 65.toByte)
      stream.consumed shouldBe 268435456
      stream.closed shouldBe true
    }

    "refuse one byte above the maximum admitted cap and close the stream" in {
      Given("a stream containing exactly one byte beyond the 256-MiB maximum")
      val stream = new TrackingInputStream(268435457)

      When("ordinary acquisition applies the maximum admitted runtime policy")
      val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(268435456))

      Then("the finite read consumes maximum plus one, returns no partial value and releases ownership")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
      stream.consumed shouldBe 268435457
      stream.closed shouldBe true
    }

    "acquire a real UTF-8 file above sixteen MiB only under a sufficient selected limit" in {
      Given("a test-owned seventeen-MiB UTF-8 file containing multibyte characters")
      val text = "日" * (17 * 1024 * 1024 / 3) + "ab"
      val expected = text.getBytes(StandardCharsets.UTF_8)
      val path = Files.createTempFile("resource-read-above-sixteen-mib-", ".txt")
      try {
        Files.write(path, expected)
        val stream = new TrackingFileInputStream(path)

        When("ordinary acquisition reads the actual file under 128 MiB, the default and sixteen MiB")
        val result = ResourceReadAcquisition.readC(stream, ResourceReadLimits(134217728))
        val refusals = Vector(ResourceReadLimits.default, ResourceReadLimits(16777216)).map { limits =>
          val limitedstream = new TrackingFileInputStream(path)
          val refused = ResourceReadAcquisition.readC(limitedstream, limits)
          refused -> limitedstream.closed
        }

        Then("every UTF-8 byte survives the selected policy while smaller policies return no partial value and all streams close")
        Files.size(path) shouldBe 17L * 1024 * 1024
        result.toOption.map(_.size) shouldBe Some(expected.length)
        result.toOption.map(bytes => java.util.Arrays.equals(bytes.toArray, expected)) shouldBe Some(true)
        result.toOption.map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8) == text) shouldBe Some(true)
        stream.closed shouldBe true
        refusals.foreach { case (refused, closed) =>
          refused.isFaillure shouldBe true
          refused.toOption shouldBe None
          closed shouldBe true
        }
      } finally {
        Files.deleteIfExists(path)
      }
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

  private final class TrackingFileInputStream(path: Path) extends FilterInputStream(Files.newInputStream(path)) {
    private var _closed = false
    def closed: Boolean = _closed
    override def close(): Unit = {
      try super.close()
      finally { _closed = true }
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
