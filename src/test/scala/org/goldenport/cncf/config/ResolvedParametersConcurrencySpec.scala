package org.goldenport.cncf.config

import java.util.concurrent.{Callable, CyclicBarrier, Executors, TimeUnit}
import org.goldenport.configuration.ConfigurationValue
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class ResolvedParametersConcurrencySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _entries_gen: Gen[Vector[(String, String)]] =
    for {
      count <- Gen.choose(3, 8)
      texts <- Gen.listOfN(count, Gen.choose(1, 12).flatMap(size =>
        Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString)
      ))
    } yield texts.zipWithIndex.map { case (text, index) =>
      s"setting.$index" -> s"value.$index.$text"
    }.toVector

  "ResolvedParameters shared reads" should {
    "preserve sequential precedence and detached first-use observations across generated entries" in {
      val property = Prop.forAll(_entries_gen) { entries =>
        Given("distinct component entries and a child that shadows one parent key")
        val parent = ResolvedParameters.fromComponentConfig(entries.toMap, "parent", None)
        val shadowkey = entries.head._1
        val localvalue = s"local.${entries.head._2}"
        val child = ResolvedParameters.fromComponentConfig(
          Map(shadowkey -> localvalue), "child", Some(parent)
        )
        val expected = entries.map { case (key, value) =>
          val parameter =
            if (key == shadowkey) {
              ResolvedParameter(
                key, ConfigurationValue.StringValue(localvalue),
                ResolvedParameter.Source.Component("child")
              )
            } else {
              ResolvedParameter(
                key, ConfigurationValue.StringValue(value),
                ResolvedParameter.Source.Component("parent")
              )
            }
          key -> parameter
        }.toMap
        val firstkeys = entries.dropRight(1).map(_._1).reverse
        val missingkey = "missing.setting"
        val readkeys = firstkeys ++ Vector(firstkeys.head, missingkey, firstkeys.last)
        val laterkey = entries.last._1
        val firstexpected = firstkeys.map(expected.apply)

        When("the child resolves an ordered sequence containing repeats and an absent key")
        val actual = readkeys.map(child.get)
        val snapshot = child.usedEntries

        Then("local precedence and exact inherited values and sources are preserved")
        actual shouldBe readkeys.map(expected.get)
        actual(readkeys.indexOf(missingkey)) shouldBe None
        snapshot shouldBe firstexpected

        When("a previously unread inherited key is resolved after the snapshot")
        val later = child.get(laterkey)
        val updated = child.usedEntries

        Then("the new observation is appended once and the earlier vector stays detached")
        later shouldBe Some(expected(laterkey))
        updated shouldBe (firstexpected :+ expected(laterkey))
        snapshot shouldBe firstexpected
        true
      }

      When("the sequential contract is checked over 50 successful generated samples")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("every sample retains its values, precedence, sources and observation semantics")
      checked.passed shouldBe true
    }

    "retain immutable values and coherent complete snapshots during overlapping disjoint reads" in {
      Given("one owned executor with eight workers for finite shared-read rounds")
      val executor = Executors.newFixedThreadPool(8)
      try {
        (0 until 2000).foreach { round =>
          Given(s"a fresh shared configuration with 64 distinct entries for round $round")
          val entries = (0 until 64).map { index =>
            s"setting.$index" -> s"value.$round.$index"
          }.toMap
          val parameters = ResolvedParameters.fromComponentConfig(entries, "shared", None)
          val barrier = new CyclicBarrier(8)
          val workerkeys = (0 until 8).map { worker =>
            (0 until 8).map(offset => s"setting.${worker * 8 + offset}").toVector
          }.toVector

          When("eight tasks cross a bounded barrier, read disjoint keys and observe concurrent snapshots")
          val futures = workerkeys.map { keys =>
            executor.submit(new Callable[Vector[Option[ResolvedParameter]]] {
              override def call(): Vector[Option[ResolvedParameter]] = {
                barrier.await(2, TimeUnit.SECONDS)
                val values = keys.map(parameters.get)
                val snapshot = parameters.usedEntries
                val snapshotkeys = snapshot.map(_.key)
                Then("each concurrent snapshot contains unique keys with exact immutable input values and sources")
                snapshotkeys.distinct shouldBe snapshotkeys
                snapshot.foreach { parameter =>
                  entries.get(parameter.key).map { value =>
                    ResolvedParameter(
                      parameter.key, ConfigurationValue.StringValue(value),
                      ResolvedParameter.Source.Component("shared")
                    )
                  } shouldBe Some(parameter)
                }
                values
              }
            })
          }
          val actual = futures.map(_.get(5, TimeUnit.SECONDS))
          val finalsnapshot = parameters.usedEntries

          Then("all values and sources match the input and completed tracking contains every key once")
          actual.zip(workerkeys).foreach { case (values, keys) =>
            values shouldBe keys.map { key =>
              Some(ResolvedParameter(
                key, ConfigurationValue.StringValue(entries(key)),
                ResolvedParameter.Source.Component("shared")
              ))
            }
          }
          finalsnapshot.size shouldBe 64
          finalsnapshot.map(_.key).distinct.size shouldBe 64
          finalsnapshot.map(_.key).toSet shouldBe entries.keySet
        }
      } finally {
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS) shouldBe true
      }
    }
  }
}
