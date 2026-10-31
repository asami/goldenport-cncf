package org.goldenport.cncf.job

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.cncf.blob.{BlobRepository, InMemoryBlobStore}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.subsystem.SystemNode
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobBackupFixtures.*

/*
 * @since   Oct.  4, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class DurableJobBackupSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-backup-restore, example:$example, rules:$rules, phase:69.7, slice:JM69-09D3")
  private def _refused[A](result: Consequence[A]): Unit = {
    result.isSuccess shouldBe false
    failure(result).show should include ("durable job backup refused")
    failure(result).show should not include "PRIVATE"
    failure(result).show should not include "jdbc:"
  }

  "B1 Operator admission precedes all native and path work" must _scenario("B1", "BR2,BR12") {
    "deny ordinary and view-only callers with constant redacted refusal and no effects" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR2,BR12 B1: a genuine Ready owner and independently ordinary/job_view contexts")
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); val before = fixture.effects
        Vector(fixture.ordinary, operator("job_view"), operator("content_manager")).foreach { context =>
          When("an unprivileged caller prepares copies or restores using hostile null paths")
          val prepare = DurableJobBackupOperations.prepare(fixture.owner)(using context)
          val backup = DurableJobBackupOperations.backup(source, null)(using context)
          val restore = DurableJobBackupOperations.restore(null, null, "PRIVATE", "PRIVATE")(using context)
          Then("each operation refuses safely before provider access or filesystem work")
          _refused(prepare); _refused(backup)
          failure(restore).show should include ("durable job restore refused")
          failure(restore).show should not include "PRIVATE"
          fixture.effects shouldBe before; fixture.node.state shouldBe SystemNode.State.Running
        }
        success(fixture.stop())
        val artifact = fixture.root.getParent.resolve("authorized-artifact")
        success(DurableJobBackupOperations.backup(source, artifact)(using fixture.context))
        val reader = new CopyReader; val operations = new DurableJobBackupOperations(reader)
        val stopped = fixture.effects
        Vector(fixture.ordinary, operator("job_view")).zipWithIndex.foreach { case (context, index) =>
          val target = fixture.root.getParent.resolve(s"denied-copy-$index")
          val restored = fixture.root.getParent.resolve(s"denied-restore-$index")
          When("ordinary or view-only callers use otherwise valid stopped sources and complete artifacts")
          val copied = operations.backup(source, target)(using context)
          val restoredresult = operations.restore(artifact, restored, "local", "default")(using context)
          Then("denial precedes actual reader opening and fresh destination reservation")
          _refused(copied); restoredresult.isSuccess shouldBe false
          reader.opens.get() shouldBe 0; Files.exists(target) shouldBe false; Files.exists(restored) shouldBe false
          fixture.effects shouldBe stopped
        }
      }
    }
  }
  "B2 Original Ready preparation does not confer live-copy admission" must _scenario("B2", "BR3,BR4") {
    "refuse live and closing shutdown attempts without implicitly stopping the original source" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR3,BR4 B2: an actual Ready owner bound to its actual running node")
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); val destination = fixture.root.getParent.resolve("live")
        When("backup is requested while the original owner is still Running")
        val result = DurableJobBackupOperations.backup(source, destination)(using fixture.context)
        Then("copy refuses without destination creation or implicit original shutdown")
        _refused(result); Files.exists(destination) shouldBe false
        fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
        fixture.node.state shouldBe SystemNode.State.Running
        When("copy competes with the actual owner close while Subsystem and node completion remain absent")
        val closing = actor(fixture.owner._stop_c())
        val raced = DurableJobBackupOperations.backup(source, destination)(using fixture.context)
        join(closing._1); success(closing._2.get().toOption.get)
        Then("the closing or merely Closed owner cannot manufacture complete native shutdown")
        _refused(raced); Files.exists(destination) shouldBe false
        fixture.subsystem._shutdown_result_option shouldBe None
        fixture.node.state shouldBe SystemNode.State.Running
      }
    }
  }
  "B3 Complete actual native shutdown admits private physical backup" must _scenario("B3", "BR1,BR4,BR5,BR8") {
    "preserve all database rows revisions payments payloads sidecars and private permissions" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR1,BR4,BR5,BR8 B3: genuinely paid terminal native records and two physical payloads")
      DurableJobBackupFixtures.withFixture { fixture =>
        val root = fixture.terminal(fixture.publish("backup_full", Vector(Array[Byte](1, 2), Array[Byte](3, 4, 5))))
        fixture.unrelated(); val unrelated = fixture.row(otherId)
        val native = fixture.state(root); val source = fixture.prepare()
        success(fixture.stop())
        val before = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs")); val effects = fixture.effects
        val artifact = fixture.root.getParent.resolve("artifact")
        When("the offline operator copies after the actual successful Subsystem node and resource completion")
        val result = DurableJobBackupOperations.backup(source, artifact)(using fixture.context)
        Then("complete physical data equals the original and native fresh consumers retain exact signed and paid rows")
        success(result); physical(artifact.resolve("data")) shouldBe before
        dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs")) shouldBe before
        fixture.effects shouldBe effects
        Files.getPosixFilePermissions(artifact) shouldBe PosixFilePermissions.fromString("rwx------")
        Files.getPosixFilePermissions(artifact.resolve("data/jobs.db")) shouldBe PosixFilePermissions.fromString("rw-------")
        Files.getPosixFilePermissions(marker(artifact)) shouldBe PosixFilePermissions.fromString("rw-------")
        val restored = restore(artifact, fixture.root.getParent.resolve("restored"))(using fixture.context)
        val fresh = new Fixture(restored.dataroot); fresh.select(root)
        try {
          success(fresh.start()); fresh.state(root) shouldBe native
          fresh.row(otherId) shouldBe unrelated
          fresh.entity.lifeWrites.get() shouldBe 0; fresh.entity.writes.get() shouldBe 0
        } finally fresh.close()
      }
    }
  }
  "B4 Terminal state alone cannot replace completed native success" must _scenario("B4", "BR4,BR12") {
    "refuse actual failed node physical close and absent subsystem completion" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR4,BR12 B4: actual native extra resource throws during node physical shutdown")
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); failedClose(fixture.node, fixture.root)
        success(fixture.subsystem.shutdownC())
        When("the actual node reaches Stopped with a failed physical-close result")
        fixture.node.shutdownC().isSuccess shouldBe false
        val target = fixture.root.getParent.resolve("failed-shutdown")
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        Then("the replayed failure refuses even though the owner is Closed and node is Stopped")
        fixture.owner.health.state shouldBe JobDurableOperationalState.Closed
        fixture.node.state shouldBe SystemNode.State.Stopped
        _refused(result); Files.exists(target) shouldBe false
        fixture.sql.get.managedResourceOption.get.closeC().isSuccess shouldBe true
      }
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); success(fixture.owner._stop_c())
        When("an owner closes without actual Subsystem completion")
        val target = fixture.root.getParent.resolve("absent-subsystem")
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        Then("absence refuses before creating a destination")
        _refused(result); Files.exists(target) shouldBe false
      }
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare()
        val loader = new java.net.URLClassLoader(Array.empty[java.net.URL], getClass.getClassLoader) {
          override def close(): Unit = { super.close(); throw new java.io.IOException("PRIVATE actual Subsystem cleanup") }
        }
        fixture.subsystem.registerComponentClassLoader(loader)
        When("actual Subsystem cleanup stores a failure while node physical close completes successfully")
        fixture.subsystem.shutdownC().isSuccess shouldBe false; success(fixture.node.shutdownC())
        val target = fixture.root.getParent.resolve("failed-subsystem")
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        Then("a completed failed Subsystem is not accepted as successful offline ownership")
        _refused(result); Files.exists(target) shouldBe false
      }
      withPath { path =>
        val fixture = new Fixture(Files.createDirectory(path.resolve("drain")), boundedDrainNode())
        var held = Option.empty[org.goldenport.cncf.subsystem.SystemNodeResourceLease]
        var binding = Option.empty[org.goldenport.cncf.subsystem.SystemNodeDataStoreBinding]
        try {
          success(fixture.start()); val source = fixture.prepare()
          val extra = fixture.node.bind(); binding = Some(extra); held = Some(success(extra.acquireLeaseC()))
          success(fixture.subsystem.shutdownC())
          When("the original bounded node exhausts its drain deadline with an actual outstanding native lease")
          fixture.node.shutdownC().isSuccess shouldBe false
          val target = path.resolve("failed-drain")
          val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
          Then("forced physical closure retains its failed drain and never admits copy as successful shutdown")
          fixture.node.state shouldBe SystemNode.State.Stopped
          _refused(result); Files.exists(target) shouldBe false
          fixture.sql.get.managedResourceOption.get.closeC().isSuccess shouldBe true
        } finally { held.foreach(_.release()); binding.foreach(_.release()); fixture.close() }
      }
    }
  }
  "B5 Only original supported physical routing can be captured" must _scenario("B5", "BR3,BR5,BR8") {
    "refuse unsupported routes nonlocal stores missing databases links and native companions" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR3,BR5,BR8 B5: actual native memory URI and mismatched Blob metadata routing")
      DurableJobBackupFixtures.withFixture { fixture =>
        Vector(":memory:", s"file:${fixture.root.resolve("uri.db")}?mode=rwc").foreach { path =>
          val provider = SqlDataStore.sqlite(path)
          try {
            fixture.context.dataStoreSpace.bindDataStore(provider)
            When("the original context selects an unsupported native JDBC file form")
            val result = DurableJobBackupOperations.prepare(fixture.owner)(using fixture.context)
            Then("preparation refuses rather than capturing an unsupported database")
            result.isSuccess shouldBe false
            failure(result).show should include ("durable job backup refused")
          } finally { fixture.context.dataStoreSpace.clearBoundDataStore(); success(provider.closeC()) }
        }
        val second = SqlDataStore.sqlite(fixture.root.resolve("second.db").toString)
        val selective = new SqlDataStore(SqliteDialectDriver, second.managedResourceOption.get.datasource) {
          override def isAccept(collection: DataStore.CollectionId): Boolean =
            collection == DataStore.CollectionId.EntityStore(BlobRepository.CollectionId)
          override private[cncf] def managedResourceOption = second.managedResourceOption
        }
        try {
          fixture.context.dataStoreSpace.bindDataStore(selective)
          When("only Blob metadata routes to a different native physical resource")
          val result = DurableJobBackupOperations.prepare(fixture.owner)(using fixture.context)
          Then("the five original routes must agree and mismatch refuses")
          _refused(result)
        } finally { fixture.context.dataStoreSpace.clearBoundDataStore(); success(second.closeC()) }
      }
      withPath { path =>
        val fixture = new Fixture(Files.createDirectory(path.resolve("nonlocal")))
        fixture.selectedBlob = new InMemoryBlobStore
        try {
          When("native startup is requested with a nonlocal Blob store")
          val result = fixture.start()
          Then("the unsupported provider never yields an admitted backup source")
          result.isSuccess shouldBe false; fixture.engine._durable_runtime_owner_option shouldBe None
        } finally fixture.close()
      }
      withPath { path =>
        val fixture = new Fixture(Files.createDirectory(path.resolve("wrapped-local")))
        fixture.selectedBlob = new DurableJobIntegrityInspectionFixtures.ObservedBlobStore(fixture.blob)
        try {
          success(fixture.start())
          When("a Ready native owner exposes a wrapper instead of an actual LocalBlobStore")
          val result = DurableJobBackupOperations.prepare(fixture.owner)(using fixture.context)
          Then("preparation refuses the unsupported physical source identity")
          _refused(result); result.isSuccess shouldBe false
        } finally fixture.close()
      }
      Vector("missing", "link", "-wal", "-shm", "-journal").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); success(fixture.stop())
        val database = fixture.root.resolve("jobs.db")
        mode match {
          case "missing" => Files.delete(database)
          case "link" =>
            val original = fixture.root.resolve("retained.db"); Files.move(database, original); Files.createSymbolicLink(database, original)
          case suffix => Files.writeString(database.resolveSibling(s"jobs.db$suffix"), "PRIVATE authoritative native companion")
        }
        When(s"actual offline backup encounters the $mode native file condition")
        val target = fixture.root.getParent.resolve("refused")
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        Then("physical preflight refuses before reserving the target")
        _refused(result); Files.exists(target) shouldBe false
      } }
    }
  }
  "B6 Inclusive resource ceilings bound the entire physical tree" must _scenario("B6", "BR7,BR8") {
    "accept exact bytes files entries depth and path capacity while refusing one-over or invalid policy" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR7,BR8 B6: an actual zero-payload SQLite tree and controlled raw sidecar-sized files")
      DurableJobBackupFixtures.withFixture { fixture =>
        val blobs = Files.createDirectory(fixture.root.resolve("blobs"))
        val directory = Files.createDirectory(blobs.resolve("a")); Files.write(directory.resolve("b"), Array[Byte](1))
        Files.write(blobs.resolve("empty"), Array.emptyByteArray)
        val bytes = Files.size(fixture.root.resolve("jobs.db")) + 1L
        val exact = DurableJobBackupResourcePolicy(maxBytes = math.max(1L, bytes), maxFiles = 3, maxEntries = 5,
          maxDepth = 3, maxRelativePathBytes = 11, maxSegmentBytes = 7)
        val source = fixture.prepare(exact)
        val tighter = Vector(exact.copy(maxBytes = math.max(1L, bytes - 1L)), exact.copy(maxFiles = 2),
          exact.copy(maxEntries = 4), exact.copy(maxDepth = 2), exact.copy(maxRelativePathBytes = 8), exact.copy(maxSegmentBytes = 6))
          .filterNot(_ == exact).map(fixture.prepare)
        success(fixture.stop())
        When("the exact policy copies every entry including a zero-byte file")
        val target = fixture.root.getParent.resolve("exact")
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        Then("inclusive exact limits admit the complete tree")
        success(result); Files.exists(target.resolve("data/blobs/empty")) shouldBe true
        tighter.zipWithIndex.foreach { case (selected, index) =>
          When("one actual source dimension exceeds a tightened ceiling")
          val destination = fixture.root.getParent.resolve(s"tight-$index")
          val refused = DurableJobBackupOperations.backup(selected, destination)(using fixture.context)
          Then("preflight refuses without any reserved destination")
          _refused(refused); Files.exists(destination) shouldBe false
        }
      }
      DurableJobBackupFixtures.withFixture { fixture =>
        Vector(DurableJobBackupResourcePolicy(maxBytes = 0L), DurableJobBackupResourcePolicy(maxBytes = Long.MaxValue),
          DurableJobBackupResourcePolicy(maxFiles = Int.MaxValue), DurableJobBackupResourcePolicy(maxDepth = 17)).foreach { policy =>
          When("an invalid or overflowing capacity policy is supplied")
          val result = DurableJobBackupOperations.prepare(fixture.owner, policy)(using fixture.context)
          Then("policy admission refuses before any copy")
          _refused(result)
        }
      }
    }
  }
  "B7 Fresh destinations preserve existing native namespaces" must _scenario("B7", "BR8,BR9") {
    "refuse existing empty targets missing parents overlap and link parents without alteration" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR8,BR9 B7: a stopped actual source and independently controlled destination parents")
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); success(fixture.stop())
        val parent = fixture.root.getParent
        val empty = Files.createDirectory(parent.resolve("empty"))
        val existing = Files.writeString(parent.resolve("retained"), "retained")
        val link = Files.createSymbolicLink(parent.resolve("link"), fixture.root)
        val before = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs"))
        Vector(empty, existing, parent.resolve("missing/child"), fixture.root, fixture.root.resolve("blobs/inside"),
          link.resolve("child"), fixture.root.resolve("jobs.db")).foreach { target =>
          When("backup is requested into an existing unsafe or overlapping namespace")
          val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
          Then("refusal preserves both the original native tree and existing target content")
          _refused(result); Files.readString(existing) shouldBe "retained"
          dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs")) shouldBe before
        }
      }
    }
  }
  "B8 Midstream failure never publishes a usable backup" must _scenario("B8", "BR7,BR9,BR12") {
    "close real readers preserve source bytes and retain only unmarked incomplete targets" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR7,BR9,BR12 B8: actual native files with bounded reader failure interruption zero shortening and growth")
      Vector("throw", "interrupt", "zero", "short", "growth").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        fixture.terminal(fixture.publish(s"fault_$mode", Vector(Array.fill[Byte](20000)(7))))
        val source = fixture.prepare(); success(fixture.stop())
        val before = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs"))
        val reader = new CopyReader(mode); val target = fixture.root.getParent.resolve("faulted")
        When(s"real copy-reader I/O fails through the $mode seam after reservation")
        val result = new DurableJobBackupOperations(reader).backup(source, target)(using fixture.context)
        val interrupted = Thread.currentThread().isInterrupted
        if (interrupted) Thread.interrupted()
        Then("refusal has no usable marker source mutation leaked reader or private diagnostics")
        _refused(result); Files.exists(target.resolve(".incomplete")) shouldBe true
        Files.exists(marker(target)) shouldBe false; reader.closes.get() shouldBe reader.opens.get()
        dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs")) shouldBe before
        interrupted shouldBe (mode == "interrupt")
      } }
      DurableJobBackupFixtures.withFixture { fixture =>
        val source = fixture.prepare(); success(fixture.stop()); val target = fixture.root.getParent.resolve("preset")
        When("the copy caller is already interrupted")
        Thread.currentThread().interrupt()
        val result = DurableJobBackupOperations.backup(source, target)(using fixture.context)
        val retained = Thread.interrupted()
        Then("the flag is retained and no destination is reserved")
        retained shouldBe true; _refused(result); Files.exists(target) shouldBe false
      }
    }
  }
  "B9 Concurrent source and destination use has one winner" must _scenario("B9", "BR9,BR11") {
    "reject simultaneous source use and release its guard after a failed real copy" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR9,BR11 B9: stopped real native source with a bounded latch-controlled copy")
      DurableJobBackupFixtures.withFixture { fixture =>
        fixture.terminal(fixture.publish("concurrent", Vector(Array.fill[Byte](20000)(1))))
        val first = fixture.prepare(); val second = fixture.prepare(); success(fixture.stop())
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val reader = new CopyReader("throw", () => { entered.countDown(); await(release) })
        val target = fixture.root.getParent.resolve("race")
        val before = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs")); val effects = fixture.effects
        When("one physical copy is held in flight and both the same source and same target are challenged")
        val running = actor(new DurableJobBackupOperations(reader).backup(first, target)(using fixture.context))
        try {
          await(entered)
          val same = DurableJobBackupOperations.backup(first, fixture.root.getParent.resolve("same-source"))(using fixture.context)
          val collision = DurableJobBackupOperations.backup(second, target)(using fixture.context)
          Then("source guard and atomic target reservation refuse the competing attempts without overwrite")
          _refused(same); _refused(collision); Files.exists(fixture.root.getParent.resolve("same-source")) shouldBe false
        } finally { release.countDown(); join(running._1) }
        _refused(running._2.get().toOption.get)
        When("the original source retries into a different fresh destination after its failed copy")
        val retry = fixture.root.getParent.resolve("retry")
        val result = DurableJobBackupOperations.backup(first, retry)(using fixture.context)
        Then("the finally-released guard admits one complete winner while the failed destination remains unmarked")
        success(result); physical(retry.resolve("data")) shouldBe before
        Files.exists(marker(target)) shouldBe false; fixture.effects shouldBe effects
      }
    }
  }
  "B10 Bounded physical trees obey shrinking capacity properties" must _scenario("B10", "BR1,BR7") {
    "preserve complete actual bytes and sidecars or refuse before publication for every sampled tighter budget" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR1,BR7 B10: ordinary shrinking seed69709705 and at least24 real controlled payload trees")
      val generator = for { count <- Gen.choose(1, 3); size <- Gen.choose(1, 256); fits <- Gen.oneOf(true, false) } yield (count, size, fits)
      val property = Prop.forAll(generator) { case (count, size, fits) => DurableJobBackupFixtures.withFixture { fixture =>
        val root = fixture.terminal(fixture.publish("property_backup", Vector.tabulate(count)(index => Array.fill[Byte](size)((index + 1).toByte))))
        success(fixture.stop())
        val raw = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs"))
        val total = raw.values.map(_.size.toLong).sum
        val fresh = new Fixture(fixture.root); fresh.select(root)
        try {
          success(fresh.start())
          val source = fresh.prepare(DurableJobBackupResourcePolicy(maxBytes = if (fits) total else total - 1L))
          success(fresh.stop())
          val before = dataFacts(fixture.root.resolve("jobs.db"), fixture.root.resolve("blobs"))
          val target = fixture.root.getParent.resolve("property")
          val result = DurableJobBackupOperations.backup(source, target)(using fresh.context)
          if (fits) result.isSuccess && physical(target.resolve("data")) == before && Files.exists(marker(target))
          else !result.isSuccess && !Files.exists(target)
        } finally fresh.close()
      } }
      When("the seeded native physical-copy property is checked with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69709705L)).withMinSuccessfulTests(24), property)
      Then("all24 bounded real trees preserve complete native bytes or refuse the one-byte tighter capacity")
      checked.passed shouldBe true; checked.succeeded should be >= 24
    }
  }
}
