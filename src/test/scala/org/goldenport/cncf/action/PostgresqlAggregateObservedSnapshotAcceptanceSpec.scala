package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{PostgresqlAcceptance, PostgresqlPersistenceFixture}
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlAggregateObservedSnapshotAcceptanceSpec extends AnyWordSpec
    with AggregateObservedSnapshotFixture.Vocabulary with PostgresqlAcceptance {
  import AggregateObservedSnapshotFixture.*
  import AggregateObservedSnapshotFixture.sampleShrink

  "Native registered Aggregate observed snapshots" should {
    "return actual PostgreSQL provider outcomes" which {
      for {
        representation <- representations
        alias <- Vector(false, true)
        mode <- Mode.values.toVector
      } {
        s"retain full $representation $mode with registered alias=$alias" in {
          Given("required PostgreSQL 17.11 durability and independent actual provider pools")
          with_postgresql { database =>
            val setup = fixture(database.token("aggregate_outcome"), representation, database.open(), alias = alias)
            val independent = setup.peer(database.open())
            checkSample(Sample("日本 Alpha Ω", "untouched native security/audit", mode, representation),
              setup, independent, () => database.snapshotAll()) shouldBe true
          }
        }
      }
      representations.foreach { representation =>
        s"retain a full native $representation maximum-revision NoOp" in {
          Given("a native root at Long.MAX_VALUE with complete body/unrelated rows and primed resident/View state")
          with_postgresql { database =>
            val setup = fixture(database.token("aggregate_maximum"), representation, database.open(), alias = true)
            val independent = setup.peer(database.open())
            setup.seed(initialRevision = Long.MaxValue)
            setup.prime()
            val before = setup.snapshot(independent.provider)
            val beforephysical = database.snapshotAll()
            val expected = success(independent.authoritativeC())
            val builds = setup.viewBuilds
            When("the actual registered Aggregate method returns its unchanged business value")
            val attempt = setup.attempt(value => Consequence.success(value), expected = revision(Long.MaxValue))
            Then("the provider returns its exact complete current snapshot without advancing exhausted revision")
            attempt.result shouldBe Consequence.success(expected)
            attempt.callbacks shouldBe 1
            setup.snapshot(independent.provider) shouldBe before
            database.snapshotAll() shouldBe beforephysical
            success(setup.view.resolve(setup.id)) shouldBe "Before"
            setup.viewBuilds shouldBe builds
          }
        }
        s"reject the losing $representation candidate after an actual independent PostgreSQL winner" in {
          Given("one registered aliased Aggregate over two real providers and independent native connections")
          with_postgresql { database =>
            val setup = fixture(database.token("aggregate_race"), representation, database.open(), alias = true)
            val independent = setup.peer(database.open())
            setup.seed()
            setup.prime()
            val before = setup.snapshot(independent.provider)
            val beforephysical = database.snapshotAll()
            var winner = Option.empty[Consequence[org.goldenport.cncf.entity.EntityRecordSnapshot]]
            When("the command coordinates a second canonical versioned write between resolution and persistence")
            val attempt = setup.attempt { current =>
              winner = Some(independent.writeC("Native winner 日本"))
              Consequence.success(current.withTitle("Native losing candidate"))
            }
            Then("the actual provider atomic revision recheck preserves the complete winner without an overwrite")
            winner.get shouldBe a[Consequence.Success[?]]
            attempt.callbacks shouldBe 1
            checkStale(attempt.result, 1L, 2L)
            setup.snapshot(independent.provider) shouldBe setup.expectedChanged(before, "Native winner 日本")
            database.snapshotAll() shouldBe changedPhysical(beforephysical, setup, "Native winner 日本")
            val actual = success(independent.authoritativeC())
            actual.record.getString("title") shouldBe Some("Native winner 日本")
            actual.record.getString("content") shouldBe Some(body)
            actual.revision.value shouldBe 2L
            setup.collection.storage.memoryRealm.flatMap(_.get(setup.id)) shouldBe None
          }
        }
      }
    }

    "preserve complete native persistence after admission and authorization rejection" which {
      Vector("missing-aggregate", "missing-root", "ambiguous-root", "foreign-collection", "unmanaged",
        "nonoptimistic", "unsupported-binding", "missing-row", "root-mismatch", "manager-only").foreach { rejected =>
        s"reject $rejected before the callback across both native representations" in {
          representations.foreach { representation =>
            Given("a complete native root/body pair with the selected actual registration or declared operation policy")
            with_postgresql { database =>
              val setup = fixture(database.token("aggregate_rejection"), representation, database.open(), alias = true,
                registered = rejected != "missing-aggregate", rootRegistered = rejected != "missing-root",
                managed = rejected != "unmanaged", optimistic = rejected != "nonoptimistic",
                managerOnly = rejected == "manager-only", mismatch = rejected == "root-mismatch",
                invalidBinding = rejected == "unsupported-binding")
              val reader = database.open()
              setup.seed()
              if (rejected == "ambiguous-root") {
                val otherid = EntityCollectionId("other", "aggregate", setup.rootName)
                setup.component.entitySpace.registerEntity(setup.rootName, setup.makeCollection(otherid))
              }
              val target = if (rejected == "foreign-collection")
                org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "foreign",
                  EntityCollectionId("test", "foreign", "foreign"), entropy = "foreign")
              else if (rejected == "missing-row")
                org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "missing", setup.collectionId, entropy = "missing")
              else setup.id
              val before = setup.snapshot(reader)
              val beforephysical = database.snapshotAll()
              When("the canonical registered Aggregate API admits the selected owner and operation")
              val attempt = setup.attempt(value => Consequence.success(value.withTitle("Forbidden")), targetId = target)
              Then("structured failure precedes the domain method and preserves every independently read native row")
              attempt.result shouldBe a[Consequence.Failure[?]]
              attempt.callbacks shouldBe 0
              setup.snapshot(reader) shouldBe before
              database.snapshotAll() shouldBe beforephysical
              if (rejected == "ambiguous-root") reason(attempt.result) shouldBe Some("entity-collection-name-ambiguous")
              if (rejected == "root-mismatch") reason(attempt.result) shouldBe Some("aggregate-root-snapshot-mismatch")
              if (rejected == "manager-only") symptom(attempt.result) shouldBe
                symptom(Consequence.securityPermissionDenied("expected authorization denial"))
            }
          }
        }
      }
    }

    "retain structured domain and redacted callback failure boundaries" which {
      Vector("domain", "throw", "null-consequence", "null-result", "throw-record", "null-record",
        "wrong-id", "missing-id", "malformed-id", "foreign-id").foreach { failure =>
        s"preserve complete native data after $failure" in {
          representations.foreach { representation =>
            Given("independent PostgreSQL persistence snapshots plus actual resident and View observations")
            with_postgresql { database =>
              val setup = fixture(database.token("aggregate_callback"), representation, database.open(), alias = true)
              val reader = database.open()
              setup.seed()
              setup.prime()
              val before = setup.snapshot(reader)
              val beforephysical = database.snapshotAll()
              val resident = setup.collection.resolveScoped(setup.id)(using setup.context)
              val view = setup.view.resolve(setup.id)
              val builds = setup.viewBuilds
              val domain: Consequence[Root] = Consequence.operationConflict("private domain failure",
                Vector(org.goldenport.observation.Descriptor.Facet.Reason("aggregate-domain-failure")))
              val command: Root => Consequence[Root] = current => failure match {
                case "domain" => domain
                case "throw" => throw new IllegalStateException("private callback secret")
                case "null-consequence" => null
                case "null-result" => Consequence.success(null)
                case "throw-record" => Consequence.success(current.copy(rendering = Rendering.Throw))
                case "null-record" => Consequence.success(current.copy(rendering = Rendering.Null))
                case "wrong-id" => Consequence.success(current.copy(id = setup.unrelatedId,
                  record = current.record.upsertSingle("id", setup.unrelatedId)))
                case "missing-id" => Consequence.success(current.copy(record = Record(current.record.fields.filterNot(_.key == "id"))))
                case "malformed-id" => Consequence.success(current.copy(record = current.record.upsertSingle("id", "invalid id")))
                case "foreign-id" =>
                  val foreign = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "foreign",
                    EntityCollectionId("test", "foreign", "foreign"), entropy = "foreign")
                  Consequence.success(current.copy(id = foreign, record = current.record.upsertSingle("id", foreign)))
              }
              When("the actual domain method produces its selected failure through the canonical Aggregate helper")
              val attempt = setup.attempt(command)
              Then("the exact structured domain failure or fixed callback diagnostic preserves full native storage")
              attempt.result shouldBe a[Consequence.Failure[?]]
              attempt.callbacks shouldBe 1
              if (failure == "domain") attempt.result shouldBe domain
              else if (Vector("throw", "null-consequence", "null-result", "throw-record", "null-record").contains(failure))
                reason(attempt.result) shouldBe Some("aggregate-callback-invalid")
              attempt.result.toString should not include "private callback secret"
              setup.snapshot(reader) shouldBe before
              database.snapshotAll() shouldBe beforephysical
              setup.collection.resolveScoped(setup.id)(using setup.context) shouldBe resident
              setup.view.resolve(setup.id) shouldBe view
              setup.viewBuilds shouldBe builds
            }
          }
        }
      }
    }

    "obey one total bounded native law over both representations" which {
      "execute at least one hundred actual outcomes with zero discarded samples" in {
        Given("two bounded seeded native collections, reusable independent pools and explicit shrinking")
        with_postgresql { database =>
          val setups = representations.map { representation =>
            val setup = fixture(database.token("aggregate_property"), representation, database.open(), alias = true)
            representation -> (setup -> setup.peer(database.open()))
          }.toMap
          val property = Prop.forAll(samples) { sample =>
            val (setup, reader) = setups(sample.representation)
            checkSample(sample, setup, reader, () => database.snapshotAll())
          }
          When("ScalaCheck executes the real registered Aggregate native law sequentially")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("complete canonical/physical outcome and preservation cases pass without discard or substitution")
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }
}
