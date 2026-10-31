package org.goldenport.cncf.job

import java.net.{StandardProtocolFamily, UnixDomainSocketAddress}
import java.nio.channels.ServerSocketChannel
import java.nio.file.{Files, Path}
import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
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
final class JobDurableRestoreIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-backup-restore, example:$example, rules:$rules, phase:69.7, slice:JM69-09D3")
  private def _refused[A](result: Consequence[A]): Unit = {
    result.isSuccess shouldBe false
    failure(result).show should include ("durable job restore refused")
    failure(result).show should not include "PRIVATE"
    failure(result).show should not include "jdbc:"
  }
  private def _restore(artifact: Path, target: Path)(using ctx: ExecutionContext): Consequence[DurableJobRestoreResult] =
    DurableJobBackupOperations.restore(artifact, target, "local", "default")

  "R1 Physical restore requires actual fresh native acceptance" must _scenario("R1", "BR1,BR10,BR14") {
    "preserve paid V2 records revisions retention metadata and multiple payloads without fresh payment writes" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR1,BR10,BR14 R1: two genuinely Published terminal roots with signed retention and multiple actual LocalBlob payloads")
      DurableJobBackupFixtures.withFixture { fixture =>
        val roots = Vector(fixture.terminal(fixture.publish("restore_first", Vector(Array[Byte](1, 2), Array[Byte](3, 4)))),
          fixture.terminal(fixture.publish("restore_second", Vector(Array[Byte](5, 6, 7)))))
        val native = fixture.state(roots*); val artifact = fixture.root.getParent.resolve("artifact")
        backup(fixture, artifact); val before = physical(artifact); val effects = fixture.effects
        When("the operator restores physical data into a fresh namespace")
        val result = restore(artifact, fixture.root.getParent.resolve("target"))(using fixture.context)
        Then("the result only awaits startup and copy neither opens an engine nor mutates original native facts")
        result.state shouldBe DurableJobRestoreState.RestoredAwaitingStartup
        fixture.effects shouldBe effects; physical(artifact) shouldBe before
        val fresh = new Fixture(result.dataroot); fresh.select(roots*)
        try {
          When("independently supplied trusted evidence bootstraps fresh original node Subsystem context SQL Blob store and provider")
          success(fresh.start())
          val report = success(fresh.engine.inspectDurableIntegrity(roots.size)(using fresh.context))
          Then("native startup is Ready and original physical integrity accepts all exact signed rows revisions and paid quotas")
          fresh.owner.health.state shouldBe JobDurableOperationalState.Ready
          report.isHealthy shouldBe true; fresh.state(roots*) shouldBe native
          fresh.entity.writes.get() shouldBe 0; fresh.entity.lifeWrites.get() shouldBe 0
          fresh.original.port.calls.get() shouldBe 0; fresh.engine._work_queue.size() shouldBe 0
          fresh.engine._workers_started.get() shouldBe false
          success(fresh.stop()); fresh.node.state shouldBe SystemNode.State.Stopped
        } finally fresh.close()
      }
    }
  }
  "R2 Native empty storage remains genuinely empty" must _scenario("R2", "BR5,BR10") {
    "restore empty or missing blobs and a native zero-size SQLite database into a fresh Ready owner" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR5,BR10 R2: actual empty native owners without arbitrary paid singleton preseed")
      Vector(false, true).foreach { present => DurableJobBackupFixtures.withFixture { fixture =>
        if (present) Files.createDirectory(fixture.root.resolve("blobs"))
        val source = fixture.prepare(); success(fixture.stop())
        val database = fixture.root.resolve("jobs.db")
        Files.size(database) shouldBe 0L
        val artifact = fixture.root.getParent.resolve("empty")
        When("empty native physical data is backed up and restored")
        success(DurableJobBackupOperations.backup(source, artifact)(using fixture.context))
        val result = restore(artifact, fixture.root.getParent.resolve("target"))(using fixture.context)
        val fresh = new Fixture(result.dataroot)
        try {
          success(fresh.start())
          Then("the independently trusted empty discovery reaches original Ready without paid rows or body rehydration")
          fresh.owner.health.state shouldBe JobDurableOperationalState.Ready
          val report = success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context))
          report.entries shouldBe Vector.empty
          report.physicalBytesRead shouldBe 0L
          report.isHealthy shouldBe false
          fresh.row(lifetimeId) shouldBe None; fresh.row(storageId) shouldBe None; fresh.row(blobQuotaId) shouldBe None
          fresh.original.port.calls.get() shouldBe 0; fresh.entity.writes.get() shouldBe 0
          Files.isDirectory(result.blobs) shouldBe true; physical(result.blobs) shouldBe Map.empty
        } finally fresh.close()
      } }
    }
  }
  "R3 The current closed completeness marker is mandatory" must _scenario("R3", "BR6,BR9") {
    "refuse missing malformed oversized count-mismatched and independently untrusted identity markers before reservation" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR6,BR9 R3: independently generated complete native artifacts and trusted expected store/container")
      Vector("missing", "duplicate", "extra", "version", "oversize", "count", "bytes", "store", "container", "overflow", "traversal", "control").foreach { mode =>
        DurableJobBackupFixtures.withFixture { fixture =>
          fixture.terminal(fixture.publish(s"marker_$mode"))
          val artifact = fixture.root.getParent.resolve("artifact"); backup(fixture, artifact)
          val path = marker(artifact); val text = Files.readString(path)
          mode match {
            case "missing" => Files.delete(path)
            case "duplicate" => Files.writeString(path, text.replace("blob.store=local", "version=1"))
            case "extra" => Files.writeString(path, text + "unexpected=PRIVATE\n")
            case "version" => Files.writeString(path, text.replace("version=1", "version=2"))
            case "oversize" => Files.writeString(path, "x" * 4097)
            case "count" => Files.writeString(path, text.replaceAll("file.count=[0-9]+", "file.count=99"))
            case "bytes" => Files.writeString(path, text.replaceAll("byte.count=[0-9]+", "byte.count=1"))
            case "overflow" => Files.writeString(path, text.replaceAll("byte.count=[0-9]+", "byte.count=9223372036854775808"))
            case "traversal" => Files.writeString(path, text.replace("blob.container=default", "blob.container=../PRIVATE"))
            case "control" => Files.writeString(path, text.replace("blob.store=local", "blob.store=lo\u0000cal"))
            case _ => ()
          }
          val target = fixture.root.getParent.resolve("refused")
          When(s"restore independently consumes the current $mode marker and trusted identity")
          val result = DurableJobBackupOperations.restore(artifact, target,
            if (mode == "store") "different" else "local", if (mode == "container") "different" else "default")(using fixture.context)
          Then("constant restore refusal precedes any fresh target namespace")
          _refused(result); Files.exists(target) shouldBe false
        }
      }
    }
  }
  "R4 Hostile physical layouts never escape or overwrite namespaces" must _scenario("R4", "BR8,BR9") {
    "refuse artifact tree links traversal extra files special files existing and overlapping targets without mutation" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR8,BR9 R4: actual complete artifacts and bounded target-owned hostile physical layouts")
      Vector("root-link", "tree-link", "extra", "special", "existing", "overlap", "parent-link").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        fixture.terminal(fixture.publish(s"layout_${mode.replace('-', '_')}", Vector(Array[Byte](1, 2))))
        val parent = fixture.root.getParent; val artifact = parent.resolve("artifact"); backup(fixture, artifact)
        val target = if (mode == "overlap") artifact.resolve("child") else parent.resolve("target")
        var selected = artifact
        var socket = Option.empty[ServerSocketChannel]
        mode match {
          case "root-link" => selected = Files.createSymbolicLink(parent.resolve("artifact-link"), artifact)
          case "tree-link" => Files.createSymbolicLink(artifact.resolve("data/blobs/outward"), fixture.root)
          case "extra" => Files.writeString(artifact.resolve("data/extra"), "PRIVATE")
          case "special" =>
            val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            socket = Some(channel); channel.bind(UnixDomainSocketAddress.of(Path.of("").toAbsolutePath.normalize().relativize(artifact.resolve("data/blobs/socket"))))
          case "existing" => Files.createDirectory(target); Files.writeString(target.resolve("retained"), "retained")
          case "parent-link" => Files.createSymbolicLink(parent.resolve("linked-parent"), fixture.root)
          case _ => ()
        }
        val currentmarker = Files.readString(marker(artifact)); val currentdatabase = Files.readAllBytes(artifact.resolve("data/jobs.db")).toVector
        try {
          When(s"restore encounters the actual $mode layout")
          val result = _restore(selected, if (mode == "parent-link") parent.resolve("linked-parent/target") else target)(using fixture.context)
          Then("the unsafe copy refuses and existing native marker database and retained target content stay untouched")
          _refused(result); Files.readString(marker(artifact)) shouldBe currentmarker
          Files.readAllBytes(artifact.resolve("data/jobs.db")).toVector shouldBe currentdatabase
          if (mode == "existing") Files.readString(target.resolve("retained")) shouldBe "retained"
          else Files.exists(target) shouldBe false
        } finally socket.foreach(_.close())
      } }
    }
  }
  "R5 Fresh native authority rejects damaged or unpaid records" must _scenario("R5", "BR10,BR12") {
    "refuse native signature identity trusted-key policy and missing-payment mismatches without repair or body execution" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR10,BR12 R5: genuinely paid native rows preceding controlled signature access key policy or singleton damage")
      Vector("signature", "identity", "key", "policy", "missing").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        val root = fixture.terminal(fixture.publish(s"authority_$mode"))
        val entityid = fixture.jobEntityId(root.jobId)
        mode match {
          case "signature" =>
            val text = fixture.row(entityid).get.getString("canonicalJson").get
            val digest = root.snapshot.record.integrity.unsignedBodySha256
            fixture.replaceText(entityid, text.replace(digest, (if (digest.head == '0') "1" else "0") + digest.tail))
          case "key" =>
            val text = fixture.row(entityid).get.getString("canonicalJson").get
            val key = root.snapshot.record.body.definitionSnapshot.key
            fixture.replaceText(entityid, text.replace(s"\"key\":\"$key\"", s"\"key\":\"${"x" * key.length}\""))
          case "policy" =>
            val text = fixture.row(lifetimeId).get.getString("canonicalJson").get
            val quota = success(DurableJobLifetimeQuotaCodec.decode(text))
            fixture.replaceText(lifetimeId, success(DurableJobLifetimeQuotaCodec.encode(quota.copy(policy = quota.policy.copy(maxledgerjobs = 2)))))
          case "missing" => fixture.removeRow(lifetimeId)
          case _ => ()
        }
        val before = Vector(entityid, lifetimeId, storageId, blobQuotaId).map(fixture.row)
        val artifact = fixture.root.getParent.resolve("artifact"); backup(fixture, artifact)
        val result = restore(artifact, fixture.root.getParent.resolve("target"))(using fixture.context)
        val fresh = new Fixture(result.dataroot); fresh.select(root)
        if (mode == "identity") fresh.source.entries = fresh.source.entries.map(_.copy(
          access = access.copy(tenantId = "PRIVATE foreign", subjectId = "PRIVATE foreign", scopes = Set.empty)))
        try {
          When(s"fresh ordinary native startup consumes the $mode source with independent trusted authority")
          val started = fresh.start()
          Then("startup or original native integrity refuses healthy acceptance before body port payment or automatic repair")
          val healthy = if (!started.isSuccess) false else success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context)).isHealthy
          healthy shouldBe false
          fresh.engine.durableOperationalHealth.get.state should not be JobDurableOperationalState.Ready
          fresh.original.port.calls.get() shouldBe 0; fresh.entity.writes.get() shouldBe 0
          fresh.entity.lifeWrites.get() shouldBe 0; fresh.engine.drainAll() shouldBe 0
          Vector(entityid, lifetimeId, storageId, blobQuotaId).map(fresh.row) shouldBe before
          success(fresh.stop()); fresh.node.state shouldBe SystemNode.State.Stopped
        } finally fresh.close()
      } }
    }
  }
  "R6 Physical payload errors retain the original native refusal" must _scenario("R6", "BR10,BR12") {
    "reject same-size corruption missing payloads and missing sidecars through fresh original integrity then stop" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR10,BR12 R6: a paid terminal root with genuine native Blob metadata and a physical payload sidecar")
      Vector("corrupt", "missing", "sidecar").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        val root = fixture.terminal(fixture.publish(s"payload_$mode", Vector(Array[Byte](1, 2, 3))))
        val relative = fixture.root.relativize(fixture.physicalPath(root.references.head))
        val before = Vector(fixture.jobEntityId(root.jobId), lifetimeId, storageId, blobQuotaId).map(fixture.row)
        val artifact = fixture.root.getParent.resolve("artifact"); backup(fixture, artifact)
        if (mode == "corrupt") Files.write(artifact.resolve("data").resolve(relative), Array[Byte](3, 2, 1))
        val result = restore(artifact, fixture.root.getParent.resolve("target"))(using fixture.context)
        val payload = result.dataroot.resolve(relative)
        if (mode == "missing") Files.delete(payload)
        if (mode == "sidecar") Files.delete(payload.resolveSibling(payload.getFileName.toString + ".blob-meta.properties"))
        val fresh = new Fixture(result.dataroot); fresh.select(root)
        try {
          When("fresh original native startup and integrity consume the actual damaged physical payload")
          val started = fresh.start()
          val healthy = if (!started.isSuccess) false else success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context)).isHealthy
          Then("physical error is never healthy acceptance and retained native authority is not rewritten")
          healthy shouldBe false; fresh.engine.durableOperationalHealth.get.state should not be JobDurableOperationalState.Ready
          fresh.entity.writes.get() shouldBe 0; fresh.entity.lifeWrites.get() shouldBe 0; fresh.original.port.calls.get() shouldBe 0
          Vector(fresh.jobEntityId(root.jobId), lifetimeId, storageId, blobQuotaId).map(fresh.row) shouldBe before
          success(fresh.stop()); fresh.node.state shouldBe SystemNode.State.Stopped
        } finally fresh.close()
      } }
    }
  }
  "R7 Genuine V1 physical restore retains explicit D2 migration authority" must _scenario("R7", "BR1,BR10") {
    "refuse default startup and allow only explicit fresh contiguous migration without duplicated payment" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR1,BR10 R7: actual published associated terminal native data checkpointed to genuine paid V1")
      DurableJobBackupFixtures.withFixture { fixture =>
        val root = fixture.legacy(fixture.terminal(fixture.publish("restore_legacy", Vector(Array[Byte](4, 5)))))
        val paid = fixture.row(lifetimeId); val metadata = root.references.map(fixture.metadata)
        val artifact = fixture.root.getParent.resolve("artifact"); backup(fixture, artifact)
        Vector(false, true).foreach { migrate =>
          val result = restore(artifact, fixture.root.getParent.resolve(s"target-$migrate"))(using fixture.context)
          val fresh = new Fixture(result.dataroot); fresh.migrateV1 = migrate; fresh.select(root)
          try {
            When("a separately fresh owner starts with the original default or explicit existing D2 opt-in")
            val started = fresh.start()
            Then("default refuses legacy while explicit original migration checkpoints contiguously and retains the exact Published payment")
            started.isSuccess shouldBe migrate
            fresh.row(lifetimeId) shouldBe paid; fresh.entity.lifeWrites.get() shouldBe 0
            fresh.original.port.calls.get() shouldBe 0
            if (migrate) {
              fresh.owner.health.state shouldBe JobDurableOperationalState.Ready
              success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context)).isHealthy shouldBe true
              val changed = fresh.signed(root.jobId)
              changed.record.format shouldBe DurableRecordFormat.V2
              changed.record.body shouldBe root.snapshot.record.body.copy(identity = root.snapshot.record.body.identity.copy(
                revision = root.snapshot.record.body.identity.revision + 1L))
              changed.providerrevision.value shouldBe root.snapshot.providerrevision.value + 1L
              root.references.map(fresh.metadata) shouldBe metadata
              // Storage reserve, job checkpoint, storage confirm.
              fresh.entity.writes.get() shouldBe 3
            } else {
              fresh.signed(root.jobId) shouldBe root.snapshot; fresh.entity.writes.get() shouldBe 0
              fresh.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Degraded
            }
          } finally fresh.close()
        }
      }
    }
  }
  "R8 Incomplete restore targets cannot be bootstrapped or retried" must _scenario("R8", "BR7,BR9,BR12") {
    "retain incomplete data on copy failure interruption or publication collision without any result ready for startup" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR7,BR9,BR12 R8: complete actual artifacts with deterministic bounded copy-reader and publication faults")
      Vector("throw", "interrupt", "publish").foreach { mode => DurableJobBackupFixtures.withFixture { fixture =>
        fixture.terminal(fixture.publish(s"restore_fault_$mode", Vector(Array.fill[Byte](20000)(1))))
        val parent = fixture.root.getParent; val artifact = parent.resolve("artifact"); backup(fixture, artifact)
        val target = parent.resolve("target"); val before = physical(artifact)
        val reader = new CopyReader(if (mode == "publish") "normal" else mode,
          () => if (mode == "publish") { Files.createDirectory(target.resolve("data")); Files.writeString(target.resolve("data/retained"), "retained"); () })
        When(s"restore experiences the actual $mode failure after fresh reservation")
        val result = new DurableJobBackupOperations(reader).restore(artifact, target, "local", "default")(using fixture.context)
        val interrupted = Thread.currentThread().isInterrupted
        if (interrupted) Thread.interrupted()
        Then("no startup result is returned and the incomplete target is neither a backup artifact nor an overwrite retry")
        _refused(result); Files.exists(target.resolve(".incomplete")) shouldBe true
        reader.opens.get() shouldBe reader.closes.get(); interrupted shouldBe (mode == "interrupt")
        _refused(_restore(target, parent.resolve("invalid-artifact"))(using fixture.context))
        _refused(_restore(artifact, target)(using fixture.context))
        Files.exists(parent.resolve("invalid-artifact")) shouldBe false; physical(artifact) shouldBe before
      } }
    }
  }
  "R9 Concurrent read-only restores have one fresh-target winner" must _scenario("R9", "BR9,BR11") {
    "retain the artifact and all original business effects while only one independent copy publishes data" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR9,BR11 R9: one actual read-only artifact and a bounded latch-held fresh destination copy")
      DurableJobBackupFixtures.withFixture { fixture =>
        fixture.terminal(fixture.publish("restore_race", Vector(Array[Byte](1, 2))))
        val parent = fixture.root.getParent; val artifact = parent.resolve("artifact"); backup(fixture, artifact)
        val before = physical(artifact); val effects = fixture.effects; val target = parent.resolve("target")
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val reader = new CopyReader("normal", () => { entered.countDown(); await(release) })
        When("two restores contend for the same create-new target while the first copy is held in flight")
        val running = actor(new DurableJobBackupOperations(reader).restore(artifact, target, "local", "default")(using fixture.context))
        try {
          await(entered)
          val competing = _restore(artifact, target)(using fixture.context)
          Then("the second restore refuses without replacing the first reserved copy")
          competing.isSuccess shouldBe false
          failure(competing).show should include ("durable job restore refused")
        } finally { release.countDown(); join(running._1) }
        val winner = success(running._2.get().toOption.get)
        Then("only one completed data result awaits startup with artifact bytes and original effects unchanged")
        winner.state shouldBe DurableJobRestoreState.RestoredAwaitingStartup
        physical(winner.dataroot) shouldBe physical(artifact.resolve("data"))
        physical(artifact) shouldBe before; fixture.effects shouldBe effects
        reader.opens.get() shouldBe reader.closes.get()
      }
    }
  }
  "R10 Shrinking native restoration preserves genuine paid facts" must _scenario("R10", "BR1,BR10") {
    "retain rows provider revisions physical metadata and integrity for24 seeded terminal payload cases" in {
      Given("docs/spec/durable-job-backup-restore-contract.md BR1,BR10 R10: ordinary shrinking seed69709705 over genuine native terminal roots and nonempty bounded payloads")
      val generator = for { count <- Gen.choose(1, 3); size <- Gen.choose(1, 128); byte <- Gen.choose(1, 127) } yield (count, size, byte)
      val property = Prop.forAll(generator) { case (count, size, byte) => DurableJobBackupFixtures.withFixture { fixture =>
        val roots = Vector.tabulate(count)(index => fixture.terminal(fixture.publish(s"restore_property_$index",
          Vector(Array.fill[Byte](size)(byte.toByte)))))
        val native = fixture.state(roots*); val artifact = fixture.root.getParent.resolve("artifact")
        backup(fixture, artifact)
        val result = restore(artifact, fixture.root.getParent.resolve("target"))(using fixture.context)
        val raw = physical(result.dataroot)
        val fresh = new Fixture(result.dataroot); fresh.select(roots*)
        try {
          val started = fresh.start()
          val healthy = if (!started.isSuccess) false else success(fresh.engine.inspectDurableIntegrity(count)(using fresh.context)).isHealthy
          healthy && fresh.owner.health.state == JobDurableOperationalState.Ready && fresh.state(roots*) == native &&
            fresh.entity.writes.get() == 0 && fresh.entity.lifeWrites.get() == 0 && fresh.original.port.calls.get() == 0 &&
            raw == physical(artifact.resolve("data"))
        } finally fresh.close()
      } }
      When("the seeded genuine native restore property is checked with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69709705L)).withMinSuccessfulTests(24), property)
      Then("all24 sampled physical restores pass fresh native integrity with exact paid revisions and no payment rewrite")
      checked.passed shouldBe true; checked.succeeded should be >= 24
    }
  }
}
