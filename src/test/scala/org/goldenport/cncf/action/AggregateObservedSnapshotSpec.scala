package org.goldenport.cncf.action

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.{EntityConcurrencyMetadata, EntityObservedDeleteCanonicalProviderFixture, EntityRevisionRepresentation}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class AggregateObservedSnapshotSpec extends AnyWordSpec with AggregateObservedSnapshotFixture.Vocabulary {
  import AggregateObservedSnapshotFixture.*
  import AggregateObservedSnapshotFixture.sampleShrink

  private val _tokens = new AtomicInteger()
  private def _token(): String = s"aos_${_tokens.incrementAndGet()}"

  "Registered Aggregate observed snapshots" should {
    "retain authoritative full outcomes on real memory and SQLite" which {
      for {
        representation <- representations
        backend <- Vector("memory", "sqlite")
        alias <- Vector(false, true)
        mode <- Mode.values.toVector
      } {
        s"retain $representation $mode and registered alias=$alias on $backend" in {
          Given("a real provider and independent canonical reader for a registered contextual Aggregate")
          _with_provider(backend) { (writer, reader, physical) =>
            val setup = fixture(_token(), representation, writer, alias = alias)
            val independent = setup.peer(reader)
            checkSample(Sample("初期 Alpha", "unrelated Ω business", mode, representation), setup, independent, physical) shouldBe true
          }
        }
      }
      for { representation <- representations; backend <- Vector("memory", "sqlite") } {
        s"retain a full current $representation NoOp at Long.MAX_VALUE on $backend" in {
          Given("a managed maximum revision with resident and View state and a complete body pair")
          _with_provider(backend) { (writer, reader, physical) =>
            val setup = fixture(_token(), representation, writer)
            val independent = setup.peer(reader)
            setup.seed(initialRevision = Long.MaxValue)
            setup.prime()
            val before = setup.snapshot(reader)
            val beforephysical = physical()
            val current = success(independent.authoritativeC())
            val builds = setup.viewBuilds
            When("the actual Aggregate command returns its unchanged typed root")
            val attempt = setup.attempt(value => Consequence.success(value), expected = revision(Long.MaxValue))
            Then("the complete authoritative record and revision return without requiring a successor or publication")
            attempt.result shouldBe Consequence.success(current)
            attempt.callbacks shouldBe 1
            setup.snapshot(reader) shouldBe before
            physical() shouldBe beforephysical
            success(setup.view.resolve(setup.id)) shouldBe "Before"
            setup.viewBuilds shouldBe builds
            writer match {
              case store: MemoryStore => store.rootPublications shouldBe 0; store.bodyPublications shouldBe 0
              case _ => ()
            }
          }
        }
        s"preserve the canonical $representation concurrent winner on $backend" in {
          Given("separate actual canonical providers over one root/body pair and a registered Aggregate")
          _with_provider(backend) { (writer, reader, physical) =>
            val setup = fixture(_token(), representation, writer, alias = true)
            val independent = setup.peer(reader)
            setup.seed()
            setup.prime()
            val before = setup.snapshot(reader)
            val beforephysical = physical()
            var winner = Option.empty[Consequence[org.goldenport.cncf.entity.EntityRecordSnapshot]]
            When("the typed command coordinates an actual second-provider write after its initial resolution")
            val attempt = setup.attempt { current =>
              winner = Some(independent.writeC("Concurrent winner"))
              Consequence.success(current.withTitle("Losing candidate"))
            }
            Then("the provider atomic recheck rejects the candidate and retains the complete winner")
            winner.get shouldBe a[Consequence.Success[?]]
            attempt.callbacks shouldBe 1
            checkStale(attempt.result, 1L, 2L)
            setup.snapshot(reader) shouldBe setup.expectedChanged(before, "Concurrent winner")
            physical() shouldBe changedPhysical(beforephysical, setup, "Concurrent winner")
            val loaded = success(independent.authoritativeC())
            loaded.revision.value shouldBe 2L
            loaded.record.getString("title") shouldBe Some("Concurrent winner")
            setup.collection.storage.memoryRealm.flatMap(_.get(setup.id)) shouldBe None
          }
        }
      }
    }

    "reject ownership and metadata admission before the command" which {
      for {
        representation <- representations
        rejected <- Vector("missing-aggregate", "missing-root", "ambiguous-root", "foreign-collection",
          "unmanaged", "nonoptimistic", "unsupported-binding", "missing-row", "root-mismatch", "manager-only")
      } {
        s"preserve full $representation state after $rejected rejection" in {
          Given("a complete actual provider pair with the selected registered ownership or policy defect")
          val provider = new MemoryStore()
          val setup = fixture(_token(), representation, provider, alias = true,
            registered = rejected != "missing-aggregate", rootRegistered = rejected != "missing-root",
            managed = rejected != "unmanaged", optimistic = rejected != "nonoptimistic",
            managerOnly = rejected == "manager-only", mismatch = rejected == "root-mismatch",
            invalidBinding = rejected == "unsupported-binding")
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
          val before = setup.snapshot()
          When("the actual observed Aggregate helper resolves and authorizes the selected owner")
          val attempt = setup.attempt(value => Consequence.success(value.withTitle("Forbidden")), targetId = target)
          Then("a structured failure precedes every domain callback and preserves complete storage")
          attempt.result shouldBe a[Consequence.Failure[?]]
          attempt.callbacks shouldBe 0
          setup.snapshot() shouldBe before
          provider.rootPublications shouldBe 0
          provider.bodyPublications shouldBe 0
          if (rejected == "ambiguous-root") reason(attempt.result) shouldBe Some("entity-collection-name-ambiguous")
          if (rejected == "root-mismatch") reason(attempt.result) shouldBe Some("aggregate-root-snapshot-mismatch")
          if (rejected == "manager-only") symptom(attempt.result) shouldBe
            symptom(Consequence.securityPermissionDenied("expected authorization denial"))
        }
      }
      for { invalid <- Vector("null-aggregate", "blank-aggregate", "padded-aggregate", "null-command-name",
          "empty-command-name", "padded-command-name", "null-id", "null-revision", "null-function") } {
        s"reject $invalid before callback or storage effects" in {
          Given("a valid registered Aggregate and complete independent storage observations")
          val provider = new MemoryStore()
          val setup = fixture(_token(), EntityRevisionRepresentation.Embedded, provider)
          setup.seed()
          val before = setup.snapshot()
          val aggregate = invalid match {
            case "null-aggregate" => null
            case "blank-aggregate" => " "
            case "padded-aggregate" => " " + setup.aggregateName
            case _ => setup.aggregateName
          }
          val commandname = invalid match {
            case "null-command-name" => null
            case "empty-command-name" => ""
            case "padded-command-name" => "changeTitle "
            case _ => "changeTitle"
          }
          val command: Root => Consequence[Root] = if (invalid == "null-function") null
            else value => Consequence.success(value.withTitle("Forbidden"))
          When("the actual protected API receives the malformed input")
          val attempt = setup.attempt(command, expected = if (invalid == "null-revision") null else EntityRevision.INITIAL,
            targetId = if (invalid == "null-id") null else setup.id, aggregateName = aggregate, commandName = commandname)
          Then("structured admission failure preserves all data without running the callback")
          attempt.result shouldBe a[Consequence.Failure[?]]
          attempt.callbacks shouldBe 0
          setup.snapshot() shouldBe before
          provider.rootPublications shouldBe 0
          provider.bodyPublications shouldBe 0
        }
      }
    }

    "preserve domain failures and contain callback boundaries" which {
      Vector("domain", "throw", "null-consequence", "null-result", "throw-record", "null-record",
        "wrong-id", "missing-id", "malformed-id", "foreign-id").foreach { failure =>
        s"preserve complete data after $failure across both representations and providers" in {
          for { representation <- representations; backend <- Vector("memory", "sqlite") } {
          Given("full selected/body/security/audit and unrelated data with resident and View observations")
          _with_provider(backend) { (writer, reader, physical) =>
            val setup = fixture(_token(), representation, writer, alias = true)
            setup.seed()
            setup.prime()
            val before = setup.snapshot(reader)
            val beforephysical = physical()
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
            When("the actual typed command produces the selected failing domain or callback result")
            val attempt = setup.attempt(command)
            Then("structured failure preserves every full row and cache without exposing callback secrets")
            attempt.result shouldBe a[Consequence.Failure[?]]
            attempt.callbacks shouldBe 1
            if (failure == "domain") attempt.result shouldBe domain
            else if (Vector("throw", "null-consequence", "null-result", "throw-record", "null-record").contains(failure))
              reason(attempt.result) shouldBe Some("aggregate-callback-invalid")
            attempt.result.toString should not include "private callback secret"
            setup.snapshot(reader) shouldBe before
            physical() shouldBe beforephysical
            setup.collection.resolveScoped(setup.id)(using setup.context) shouldBe resident
            setup.view.resolve(setup.id) shouldBe view
            setup.viewBuilds shouldBe builds
          }
          }
        }
      }
    }

    "obey total bounded real-provider laws" which {
      representations.foreach { representation =>
        s"execute at least one hundred $representation outcomes without discarded input" in {
          Given("one reusable seeded memory provider and explicit nonempty value/mode shrinking")
          val provider = new MemoryStore()
          val setup = fixture(_token(), representation, provider, alias = true)
          val reader = setup.peer(provider)
          val property = Prop.forAll(samples.map(_.copy(representation = representation))) { sample =>
            checkSample(sample, setup, reader)
          }
          When("ScalaCheck executes the actual registered Aggregate law sequentially")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("at least one hundred complete outcome/preservation cases succeed with zero discards")
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _with_provider[A](backend: String)(body: (DataStore, DataStore,
    () => Map[(String, String), org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable]) => A): A =
    if (backend == "sqlite") EntityObservedDeleteCanonicalProviderFixture.withSqlite { database =>
      body(database.open(), database.open(), () => sqlitePhysical(database.path))
    }
    else {
      val provider = new MemoryStore()
      body(provider, provider, () => Map.empty)
    }
}
