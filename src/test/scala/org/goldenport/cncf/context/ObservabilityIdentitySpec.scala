package org.goldenport.cncf.context

import java.time.Instant
import org.goldenport.id.UniversalId
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
object ObservabilityIdentitySpec {
  final case class Sample(major: String, minor: String, spankind: String, millis: Long, entropy: String)
  private val _label = for {
    first <- Gen.oneOf('a', 'Z', 'm')
    length <- Gen.chooseNum(0, 10)
    tail <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '0', '9', '_'))
  } yield first.toString + tail.mkString
  val samples: Gen[Sample] = for {
    major <- _label
    minor <- _label
    spankind <- _label
    millis <- Gen.chooseNum(0L, 4102444800000L)
    entropy <- _label
  } yield Sample(major, minor, spankind, millis, entropy)
  given Shrink[Sample] = Shrink { sample =>
    val major = if (sample.major.length <= 1) Stream.empty else Stream(sample.copy(major = sample.major.take(math.max(1, sample.major.length / 2))))
    val minor = if (sample.minor.length <= 1) Stream.empty else Stream(sample.copy(minor = sample.minor.take(math.max(1, sample.minor.length / 2))))
    val spankind = if (sample.spankind.length <= 1) Stream.empty else Stream(sample.copy(spankind = sample.spankind.take(math.max(1, sample.spankind.length / 2))))
    val millis = if (sample.millis == 0L) Stream.empty else Stream(sample.copy(millis = sample.millis / 2L))
    val entropy = if (sample.entropy.length <= 1) Stream.empty else Stream(sample.copy(entropy = sample.entropy.take(math.max(1, sample.entropy.length / 2))))
    major #::: minor #::: spankind #::: millis #::: entropy
  }
}

final class ObservabilityIdentitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ObservabilityIdentitySpec.*
  import ObservabilityIdentitySpec.given
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)

  "Operational observability identity" should {
    "explicit canonical parts" which {
      "retain independently declared timestamp, entropy, kind and subkind for all three identities" in {
        Given("canonical labels, an epoch-millisecond timestamp and three independently declared entropy values")
        val millis = 1792055411123L
        val instant = Instant.ofEpochMilli(millis)
        val expected = Vector(
          UniversalId.Parts("fixture", "ingress", "trace", None, instant, "trace_17"),
          UniversalId.Parts("fixture", "operation", "span", Some("command"), instant, "span_29"),
          UniversalId.Parts("fixture", "boundary", "correlation", None, instant, "correlation_31"))
        val strings = Vector(s"fixture-ingress-trace-$millis-trace_17", s"fixture-operation-span-command-$millis-span_29",
          s"fixture-boundary-correlation-$millis-correlation_31")
        When("TraceId, SpanId and CorrelationId use their explicit generation inputs")
        val actual: Vector[UniversalId] = Vector(
          TraceId("fixture", "ingress", timestamp = Some(instant), entropy = Some("trace_17")),
          SpanId("fixture", "operation", "command", timestamp = Some(instant), entropy = Some("span_29")),
          CorrelationId("fixture", "boundary", timestamp = Some(instant), entropy = Some("correlation_31")))
        Then("every complete value and parsed part equals the input oracle without format changes")
        actual.map(_.value) shouldBe strings
        actual.map(_.parts) shouldBe expected
        actual.zip(expected).foreach { case (identity, parts) =>
          UniversalId.parseParts(identity.value, parts.kind).toOption shouldBe Some(parts)
        }
      }

      "compare and hash the full canonical value rather than constructor labels or option fields" in {
        Given("the same labels and timestamp with equal, different-entropy and submillisecond-equivalent canonical values")
        val instant = Instant.ofEpochMilli(1792055411123L)
        val left: Vector[UniversalId] = Vector(TraceId("fixture", "entry", Some(instant), Some("a")),
          SpanId("fixture", "scope", "operation", Some(instant), Some("a")), CorrelationId("fixture", "boundary", Some(instant), Some("a")))
        When("independent constructors produce identical full values or distinct entropy")
        val equal: Vector[UniversalId] = Vector(TraceId("fixture", "entry", Some(instant.plusNanos(1)), Some("a")),
          SpanId("fixture", "scope", "operation", Some(instant.plusNanos(1)), Some("a")),
          CorrelationId("fixture", "boundary", Some(instant.plusNanos(1)), Some("a")))
        val different: Vector[UniversalId] = Vector(TraceId("fixture", "entry", Some(instant), Some("b")),
          SpanId("fixture", "scope", "operation", Some(instant), Some("b")), CorrelationId("fixture", "boundary", Some(instant), Some("b")))
        Then("canonical equality is symmetric, agrees with hashing and distinguishes each different full identity")
        left.zip(equal).zip(different).foreach { case ((identity, same), other) =>
          identity shouldBe same
          same shouldBe identity
          identity.hashCode shouldBe same.hashCode
          identity.hashCode shouldBe identity.value.hashCode
          Set(identity, same) should have size 1
          identity should not equal other
          identity.value should not equal other.value
        }
      }
    }

    "default source construction" which {
      "keep two- and three-argument generation and reconstruct its full canonical identity from parsed parts" in {
        Given("the existing two-argument Trace/Correlation and three-argument Span source calls")
        val identities: Vector[UniversalId] = Vector(TraceId("fixture", "entry"), SpanId("fixture", "scope", "operation"),
          CorrelationId("fixture", "boundary"))
        val kinds = Vector("trace", "span", "correlation")
        When("their generated full parts are parsed and passed to the explicit constructors")
        val parts = identities.zip(kinds).map { case (identity, kind) => UniversalId.parseParts(identity.value, kind).toOption.get }
        val restored: Vector[UniversalId] = Vector(
          TraceId(parts(0).major, parts(0).minor, Some(parts(0).timestamp), Some(parts(0).entropy)),
          SpanId(parts(1).major, parts(1).minor, parts(1).subkind.get, Some(parts(1).timestamp), Some(parts(1).entropy)),
          CorrelationId(parts(2).major, parts(2).minor, Some(parts(2).timestamp), Some(parts(2).entropy)))
        Then("valid generated identities keep their labels and full-value equality across default and explicit input forms")
        parts.map(_.major) shouldBe Vector.fill(3)("fixture")
        parts.map(_.minor) shouldBe Vector("entry", "scope", "boundary")
        parts.map(_.kind) shouldBe kinds
        parts.map(_.subkind) shouldBe Vector(None, Some("operation"), None)
        parts.foreach(_.entropy should not be empty)
        identities.zip(restored).foreach { case (identity, copy) =>
          copy.value shouldBe identity.value
          copy shouldBe identity
          copy.hashCode shouldBe identity.hashCode
        }
      }
    }

    "total canonical construction law" which {
      "preserve full parts, equality and different-entropy identity for at least one hundred reducing samples" in {
        Given("total bounded canonical labels, nonnegative epoch milliseconds and nonempty separator-free entropy with valid reducing shrinkers")
        val property = Prop.forAll(samples) { sample =>
          Given("independent expected canonical strings and parts declared from the generated input")
          val instant = Instant.ofEpochMilli(sample.millis)
          val expected = Vector(
            UniversalId.Parts(sample.major, sample.minor, "trace", None, instant, sample.entropy),
            UniversalId.Parts(sample.major, sample.minor, "span", Some(sample.spankind), instant, sample.entropy),
            UniversalId.Parts(sample.major, sample.minor, "correlation", None, instant, sample.entropy))
          val strings = Vector(s"${sample.major}-${sample.minor}-trace-${sample.millis}-${sample.entropy}",
            s"${sample.major}-${sample.minor}-span-${sample.spankind}-${sample.millis}-${sample.entropy}",
            s"${sample.major}-${sample.minor}-correlation-${sample.millis}-${sample.entropy}")
          def _identities_(entropy: String): Vector[UniversalId] = Vector(
            TraceId(sample.major, sample.minor, Some(instant), Some(entropy)),
            SpanId(sample.major, sample.minor, sample.spankind, Some(instant), Some(entropy)),
            CorrelationId(sample.major, sample.minor, Some(instant), Some(entropy)))
          When("independent constructors consume the same explicit parts and a distinct entropy")
          val actual = _identities_(sample.entropy)
          val same = _identities_(sample.entropy)
          val different = _identities_(sample.entropy + "x")
          Then("all full values and parts match the independent input oracle and equality never collapses distinct entropy")
          actual.map(_.value) shouldBe strings
          actual.map(_.parts) shouldBe expected
          actual.zip(same).zip(different).foreach { case ((identity, equal), other) =>
            identity shouldBe equal
            identity.hashCode shouldBe equal.hashCode
            identity.hashCode shouldBe identity.value.hashCode
            identity should not equal other
          }
          true
        }
        When("the sequential law runs with a minimum of one hundred successful samples")
        val result = Test.check(_parameters, property)
        Then("the full canonical identity law passes without a discarded sample")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}
