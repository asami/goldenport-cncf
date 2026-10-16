package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.SystemNode
import org.scalatest.matchers.should.Matchers
import DurableJobOperationResponseQueryFixtures.{Fixture, Produced, assertDomain, assertNative, caller, expected, message as failureMessage, success}

/*
 * Fresh native writer and caller-authorized readers assert domain before receipt.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponseQueryProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path, kind: String,
    input: String, id: Option[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    var subsystemclosed = false
    try {
      facts = if (arguments.mode == "writer") _writer(fixture, arguments)
        else _reader(fixture, arguments)
      owner = fixture.native.engine._durable_runtime_owner_option
    } finally {
      val stopped = fixture.native.subsystem.shutdownC()
      val nodeclosed = fixture.native.node.shutdownC()
      try {
        success(stopped)
        success(nodeclosed)
        subsystemclosed = stopped.isSuccess
        fixture.native.node.state shouldBe SystemNode.State.Stopped
        fixture.native.node.activeLeaseCount shouldBe 0
        fixture.closed shouldBe true
        owner.foreach(value => value._backup_closed shouldBe true)
      } finally fixture.close()
    }
    val identity = DurableJobOperationResponseQueryFixtures.identity()
    DurableJobOperationResponseQueryFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "subsystemclosed" -> subsystemclosed.toString,
      "ownerclosed" -> owner.exists(_._backup_closed).toString,
      "nodeclosed" -> (fixture.native.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> fixture.closed.toString, "leases" -> fixture.native.node.activeLeaseCount.toString))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length == 5 || args.length == 6, "closed result probe arguments required")
    val mode = args(0)
    require(Set("writer", "reader", "foreign-reader").contains(mode), "closed result mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-"),
      "actual absolute owned native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && !Files.exists(receipt), "new owned result receipt required")
    val kind = args(3)
    val input = args(4)
    expected(kind, input)
    require(kind != "review" || input.isEmpty, "Review uses independent literal constants")
    require((mode == "writer") == (args.length == 5), "reader requires only opaque Job ID in addition to independent input")
    val id = args.drop(5).headOption.map(value => success(JobId.parse(value)))
    Arguments(mode, root, receipt, kind, input, id)
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    success(fixture.start())
    fixture.native.owner.health.state shouldBe JobDurableOperationalState.Ready
    val selected = expected(arguments.kind, arguments.input)
    val produced = fixture.produce(selected)
    val before = fixture.rows(produced.id, Some(produced.reference))
    val domain = success(fixture.query(produced.id, fixture.current)).get.result.get
    assertDomain(domain, arguments.kind, arguments.input)
    fixture.native.engine.getResult(produced.id) shouldBe Some(JobResult.Success(selected))
    success(fixture.native.engine.queryManagementResult(produced.id)(using fixture.current)) shouldBe
      Some(JobManagementResult.Available(JobResult.Success(selected)))
    val native = assertNative(fixture, produced.id, arguments.kind, arguments.input)
    native.snapshot shouldBe produced.snapshot
    fixture.rows(produced.id, Some(produced.reference)) shouldBe before
    fixture.effects.tasks shouldBe 1
    fixture.effects.canonical shouldBe 1
    fixture.effects.sources shouldBe 6
    fixture.effects.ports shouldBe 0
    fixture.effects.resolvers shouldBe 0
    fixture.effects.puts shouldBe 1
    fixture.effects.gets shouldBe 0
    fixture.effects.opens shouldBe 0
    fixture.effects.closes shouldBe 0
    fixture.effects.deletes shouldBe 0
    fixture.effects.writes should be > 0
    _facts(fixture, native, false, false, true, "", 1)
  }

  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val id = arguments.id.get
    // Only the independently supplied internal startup candidate is installed.
    // This process never opens any other process's receipt or control state.
    fixture.open(id)
    val native = assertNative(fixture, id, arguments.kind, arguments.input)
    val before = fixture.rows(id, Some(native.reference))
    val payment = fixture.native.native(id)
    val context: ExecutionContext = if (arguments.mode == "foreign-reader")
      caller(fixture.native.context, subject = "foreign-subject", overview = true) else fixture.current
    val baseline = fixture.effects
    val result = fixture.query(id, context)
    val foreign = arguments.mode == "foreign-reader"
    val refusal = if (foreign) {
      result.isSuccess shouldBe false
      failureMessage(result) shouldBe Some("durable job result query refused")
      fixture.effects.metadata shouldBe baseline.metadata
      fixture.effects.gets shouldBe 0
      fixture.effects.opens shouldBe 0
      fixture.effects.closes shouldBe 0
      failureMessage(result).get
    } else {
      assertDomain(success(result).get.result.get, arguments.kind, arguments.input)
      fixture.effects.metadata - baseline.metadata shouldBe 1
      fixture.effects.gets shouldBe 1
      fixture.effects.opens shouldBe 1
      fixture.effects.closes shouldBe 1
      ""
    }
    fixture.resolver.contexts.size shouldBe 1
    fixture.resolver.contexts.head should be theSameInstanceAs context
    fixture.native.engine.query(id).get.result shouldBe None
    fixture.native.engine.getResult(id) shouldBe None
    val management = success(fixture.native.engine.queryManagementResult(id)(using context))
    management shouldBe Some(JobManagementResult.UnavailableAfterRestart(fixture.native.engine.query(id).get.resultSummary))
    fixture.rows(id, Some(native.reference)) shouldBe before
    fixture.native.signed(id) shouldBe native.snapshot
    fixture.native.native(id) shouldBe payment
    fixture.effects.writes shouldBe 0
    fixture.effects.entitywrites shouldBe 0
    fixture.effects.tasks shouldBe 0
    fixture.effects.getters shouldBe 0
    fixture.effects.canonical shouldBe 0
    fixture.effects.sources shouldBe 0
    fixture.effects.ports shouldBe 0
    fixture.effects.puts shouldBe 0
    fixture.effects.deletes shouldBe 0
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.native.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.native.engine._work_queue.isEmpty shouldBe true
    val drained = fixture.native.engine.drainAll()
    drained shouldBe 0
    _facts(fixture, native, true, true, true, refusal, drained)
  }

  private def _facts(fixture: Fixture, produced: Produced, rawabsent: Boolean,
    managementunavailable: Boolean, rowsunchanged: Boolean, refusal: String, drained: Int): Map[String, String] = {
    val payment = produced.payment
    val effects = fixture.effects
    val entry = fixture.quota.entries.find(_.blobid == produced.reference.reference).get
    Map("id" -> produced.id.value, "revision" -> produced.snapshot.record.body.identity.revision.toString,
      "providerrevision" -> produced.snapshot.providerrevision.toString, "reference" -> produced.reference.reference,
      "status" -> produced.snapshot.record.body.lifecycle.status.wire, "quotastate" -> entry.state.toString,
      "chargedtasks" -> payment.seed.paid.chargedtasks.toString, "historycredits" -> payment.seed.paid.historycredits.toString,
      "admissions" -> payment.seed.paid.admissions.toString, "cancelcredits" -> payment.seed.paid.cancelcredits.toString,
      "remaining" -> payment.seed.remaining.toString, "normalids" -> payment.seed.normalids.size.toString,
      "claims" -> payment.seed.claims.size.toString, "cancelclaimed" -> payment.cancelclaimed.toString,
      "tasks" -> effects.tasks.toString, "getters" -> effects.getters.toString, "canonical" -> effects.canonical.toString,
      "sources" -> effects.sources.toString, "ports" -> effects.ports.toString, "writes" -> effects.writes.toString,
      "entitywrites" -> effects.entitywrites.toString, "metadata" -> effects.metadata.toString,
      "gets" -> effects.gets.toString, "opens" -> effects.opens.toString, "closes" -> effects.closes.toString,
      "puts" -> effects.puts.toString, "deletes" -> effects.deletes.toString, "resolvers" -> effects.resolvers.toString,
      "drained" -> drained.toString, "rawabsent" -> rawabsent.toString, "managementunavailable" -> managementunavailable.toString,
      "rowsunchanged" -> rowsunchanged.toString, "ready" -> (fixture.native.owner.health.state == JobDurableOperationalState.Ready).toString,
      "refusal" -> refusal, "domainChecked" -> "true")
  }
}
