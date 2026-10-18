package org.goldenport.cncf.entity

import java.nio.file.{Files, Path, StandardCopyOption}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

/*
 * Source-only Phase 100 cutover rehearsal. All databases belong to this spec.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class Phase100MappingRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import Phase100EntityStoreMappingFixture.*

  "Source-only metadata adoption" should {
    "resume a read-only inventory without rewriting original SQL text, and restore a damaged disposable copy" in {
      Given("an immutable test-owned source database with retained raw String inputs and a versioned mapping")
      val work = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase100/recovery")), "adoption-").toAbsolutePath
      val source = work.resolve("source.db")
      val backup = work.resolve("original-backup.db")
      val candidate = work.resolve("candidate.db")
      val records = Vector(row("recovery_one", " \n{ \"one\" : 1 } 雪  "), row("recovery_two", "  [two]\t "))
      val writer = _open(source)
      locally {
        given ExecutionContext = _context(writer)
        given EntityPersistent[Record] = persistent
        try records.foreach(value => _take(EntityStore.standard().save(value)))
        finally _take(writer.closeC())
      }
      Files.copy(source, backup)
      Files.copy(backup, candidate)
      val originals = Files.readAllBytes(backup).toVector
      val targets = records.map(id)

      When("the first read-only inventory is interrupted by close and then restarted from its explicit targets")
      val interrupted = _open(candidate)
      locally {
        given ExecutionContext = _context(interrupted)
        try {
          val initial = _inventory(interrupted, targets.take(1), Some("phase100-fixture/v1"))
          Then("the interrupted first target is ready without a write")
          initial shouldBe Vector("ready")
        }
        finally _take(interrupted.closeC())
      }
      When("the same explicit inventory targets are resumed and retried")
      val resumed = _open(candidate)
      locally {
        given ExecutionContext = _context(resumed)
        given EntityPersistent[Record] = persistent
        try {
          val first = _inventory(resumed, targets, Some("phase100-fixture/v1"))
          val retry = _inventory(resumed, targets, Some("phase100-fixture/v1"))
          val restored = targets.map(entityid => _take(EntityStore.standard().load[Record](entityid)).get)

          Then("restart and retry agree, logical values decode, and adoption changes no original bytes")
          first shouldBe Vector("ready", "ready")
          retry shouldBe first
          restored.zip(records).foreach { case (actual, expected) =>
            Vector("original", "payload", "evidence", "labels", "summary", "number").foreach { key =>
              actual.getAny(key) shouldBe expected.getAny(key)
            }
          }
        } finally _take(resumed.closeC())
      }
      Files.readAllBytes(candidate).toVector shouldBe originals
      Files.readAllBytes(source).toVector shouldBe originals

      When("a disposable working copy is damaged and replaced from the retained closed backup")
      val damaged = _open(candidate)
      locally {
        given ExecutionContext = _context(damaged)
        try {
          val raw = _take(damaged.load(address, DataStore.EntryId(targets.head))).get
          _take(damaged.save(address, DataStore.EntryId(targets.head), raw.upsertSingle("payload_json", "{")))
          val inventory = _inventory(damaged, targets, Some("phase100-fixture/v1"))
          Then("read-only diagnosis identifies only the damaged payload")
          inventory shouldBe Vector("corrupt", "ready")
        } finally _take(damaged.closeC())
      }
      When("the damaged disposable file is restored from its retained closed backup")
      Files.copy(backup, candidate, StandardCopyOption.REPLACE_EXISTING)
      val restored = _open(candidate)
      locally {
        given ExecutionContext = _context(restored)
        try {
          Then("the same read-only verification succeeds and the retained original remains untouched")
          _inventory(restored, targets, Some("phase100-fixture/v1")) shouldBe Vector("ready", "ready")
        } finally _take(restored.closeC())
      }
      Files.readAllBytes(candidate).toVector shouldBe originals
      Files.readAllBytes(backup).toVector shouldBe originals
    }

    "refuse to classify historical JSON-looking text without its trusted mapping provenance" in {
      Given("a test-owned physical row and no historical producer/mapping evidence")
      val work = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase100/recovery")), "unknown-")
      val source = row("unknown", " {\"looksLikeJson\": true} ")
      val provider = _open(work.toAbsolutePath.resolve("source.db"))
      given ExecutionContext = _context(provider)
      given EntityPersistent[Record] = persistent
      try {
        _take(EntityStore.standard().save(source))

        When("read-only diagnosis encounters missing or incompatible provenance")
        val unknown = _inventory(provider, Vector(id(source)), None)
        val incompatible = _inventory(provider, Vector(id(source)), Some("unknown-producer/v0"))
        val raw = _take(provider.load(address, DataStore.EntryId(id(source)))).get

        Then("both require owner disposition and JSON spelling supplies no authorization to rewrite")
        unknown shouldBe Vector("unavailable-provenance")
        incompatible shouldBe unknown
        raw.getString("original_text") shouldBe source.getString("original")
      } finally _take(provider.closeC())
    }
  }

  private def _inventory(provider: DataStore, targets: Vector[EntityId], provenance: Option[String])
    (using ExecutionContext): Vector[String] = targets.map { entityid =>
    val physical = _take(provider.load(address, DataStore.EntryId(entityid))).get
    provenance match {
      case Some("phase100-fixture/v1") => mapping.decode(collection, physical) match {
        case Consequence.Success(_) => "ready"
        case Consequence.Failure(_) => "corrupt"
      }
      case _ => "unavailable-provenance"
    }
  }

  private def _open(path: Path): SqlDataStore = SqlDataStore.jdbc(s"jdbc:sqlite:$path")
  private def _context(provider: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(provider)
    context
  }
  private def _take[A](result: Consequence[A]): A = result.getOrElse(fail(result.toString))
}
