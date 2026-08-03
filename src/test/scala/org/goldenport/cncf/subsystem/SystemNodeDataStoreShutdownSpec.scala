package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStoreIdentity}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, SystemNodeShutdownConfiguration}
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationValue}
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemNodeDataStoreShutdownSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp01, example:$example, rules:DSP01-R8-R20, phase:54, slice:DSP-01C")

  private def _example(example: String, condition: String)(body: => Unit): Unit =
    s"$example $condition" must _metadata(example) {
      condition in {
        Given(s"Spec: docs/phase/phase-54.md; Rules: DSP01-R8-R20; Example: $example")
        body
      }
    }

  "SystemNode datastore shutdown" when {
    "which records the SystemNode lifecycle contract in docs/phase/phase-54.md" that {
    _example("E1", "reject a non-positive canonical drain timeout before any Subsystem is constructed") {
      Given("a zero SystemNode-scoped drain timeout")
      val result = _create_node_c("0")

      When("the canonical typed configuration is admitted before SystemNode construction")

      Then("it fails structurally with the canonical key and rejected value")
      _failure_display(result) should include ("textus.system-node.shutdown.drain-timeout-millis")
      _failure_display(result) should include ("0")
    }

    _example("E2", "reject a negative canonical drain timeout before any Subsystem is constructed") {
      Given("a negative SystemNode-scoped drain timeout")
      val result = _create_node_c("-1")

      When("the canonical typed configuration is admitted before SystemNode construction")

      Then("it fails structurally with the canonical key and rejected value")
      _failure_display(result) should include ("textus.system-node.shutdown.drain-timeout-millis")
      _failure_display(result) should include ("-1")
    }

    _example("E3", "reject a non-numeric canonical drain timeout before any Subsystem is constructed") {
      Given("a non-numeric SystemNode-scoped drain timeout")
      val result = _create_node_c("thirty-seconds")

      When("the canonical typed configuration is admitted before SystemNode construction")

      Then("it fails structurally with the canonical key and rejected value")
      _failure_display(result) should include ("textus.system-node.shutdown.drain-timeout-millis")
      _failure_display(result) should include ("thirty-seconds")
    }

    _example("E4", "reject every sampled non-positive canonical drain timeout with keyed structured evidence") {
      Given("sampled zero and negative SystemNode-scoped drain timeouts")
      val property = Prop.forAll(Gen.chooseNum(-300000, 0)) { value =>
        _create_node_c(value.toString) match {
          case Consequence.Failure(conclusion) =>
            conclusion.display.contains("textus.system-node.shutdown.drain-timeout-millis") &&
              conclusion.display.contains(value.toString)
          case Consequence.Success(_) => false
        }
      }

      When("the provisional parser-admission property is checked")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("every non-positive value is rejected with attributable structured evidence")
      checked.passed shouldBe true
    }

    _example("E5", "allow a pre-Stopping lease to finish lazy acquisition and reject a later lease") {
      Given("one lease granted before Stopping and a later binding without a lease")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(19.toByte))
      val node = SystemNode.withHmacKey(key)
      val admittedbinding = node.bind()
      val admittedlease = admittedbinding.acquireLeaseC().toOption.get
      val laterbinding = node.bind()
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-prestopping.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get

      When("the node begins stopping before the admitted lease resolves lazily")
      node.beginStopping()
      val resolved = admittedbinding.resolveC(admittedlease, "catalog", identity)(
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => ()))
      )
      val laterlease = laterbinding.acquireLeaseC()

      Then("the admitted lease drains while later admission fails structurally")
      resolved.toOption.isDefined shouldBe true
      laterlease.toOption shouldBe None
      admittedlease.release()
      node.shutdownC().toOption shouldBe Some(())
    }

    _example("E6", "prevent factory invocation when shutdown wins before lease grant") {
      Given("a SystemNode finalized before a binding requests a lease")
      val node = SystemNode.create()
      val binding = node.bind()

      When("the losing acquisition is observed")
      node.shutdownC().toOption shouldBe Some(())
      val lease = binding.acquireLeaseC()

      Then("it is rejected and no managed resource is created or published")
      lease.toOption shouldBe None
    }

    _example("E7", "revoke timed-out leases, reject later borrow, and aggregate affected work") {
      Given("a deterministic Node drain clock with one active managed lease")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(47.toByte))
      val runtime = new SystemNode.DrainRuntime {
        private var _now: Long = 0L
        def nanoTime(): Long = _now
        def await(monitor: Object, millis: Long): Unit =
          _now += millis * 1000000L
      }
      val node = SystemNode.withHmacKey(key, () => (), 1L, runtime)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-timeout.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      val resource = ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet())
      binding.resolveC(lease, "catalog", identity)(Consequence.success(resource)).toOption shouldBe Some(resource)

      When("the bounded drain expires")
      val result = node.shutdownC()
      val later = binding.resolveC(lease, "catalog", identity)(Consequence.success(resource))

      Then("remaining work is reported, later borrow fails, and owned resources close best-effort")
      result.toOption shouldBe None
      lease.isRevoked shouldBe true
      later.toOption shouldBe None
      closecount.get() shouldBe 1
      node.state shouldBe SystemNode.State.Stopped
    }

    _example("E8", "make repeated shutdown idempotent and close each node-owned resource once") {
      Given("three managed resources and repeated terminal callers")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(23.toByte))
      val node = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      (1 to 3).foreach { index =>
        val identity = SqlDataStoreIdentity.sqliteC(
          s"target/shutdown-repeat-$index.db",
          SqlDataStoreIdentity.Credential.reference("test", "v$index"),
          key
        ).toOption.get
        binding.resolveC(lease, s"catalog-$index", identity)(
          Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet()))
        ).toOption.isDefined shouldBe true
      }
      lease.release()

      When("all callers observe the terminal shutdown result")
      val start = new CountDownLatch(1)
      val executor = Executors.newFixedThreadPool(8)
      val results = try {
        val futures = Vector.fill(8)(executor.submit(new Callable[Consequence[Unit]] {
          def call(): Consequence[Unit] = {
            start.await()
            node.shutdownC()
          }
        }))
        start.countDown()
        futures.map(_.get(10, TimeUnit.SECONDS))
      } finally {
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
      }

      Then("one terminal result and one close per owned resource are observed")
      results.map(_.toOption) shouldBe Vector.fill(8)(Some(()))
      closecount.get() shouldBe 3
    }

    _example("E9", "continue deterministic canonical-key close after one close failure") {
      Given("three node-owned resources inserted out of canonical identity order")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(29.toByte))
      val node = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val closeorder = scala.collection.mutable.ArrayBuffer.empty[String]
      val identities = Vector("catalog-c", "catalog-a", "catalog-b").map { name =>
        name -> SqlDataStoreIdentity.sqliteC(
          s"target/$name.db",
          SqlDataStoreIdentity.Credential.reference("test", name),
          key
        ).toOption.get
      }
      identities.foreach { case (name, identity) =>
        binding.resolveC(lease, name, identity) {
          val close = () => {
            closeorder += name
            if (name == "catalog-b")
              throw new IllegalStateException("planned close failure")
          }
          Consequence.success(ManagedSqlDataStoreResource.hikari(null, close))
        }.toOption.isDefined shouldBe true
      }
      lease.release()

      When("the middle close fails")
      val result = node.shutdownC()

      Then("later resources still close and the keyed failure is aggregated")
      result.toOption shouldBe None
      closeorder.toVector shouldBe identities.sortBy(_._2.canonicalKey).map(_._1)
    }

    _example("E10", "order every insertion permutation by secret-safe canonical key during close") {
      Given("noncanonical insertion orders for three secret-safe canonical identities")
      val insertionorders = Vector(
        Vector("catalog-a", "catalog-b", "catalog-c"),
        Vector("catalog-a", "catalog-c", "catalog-b"),
        Vector("catalog-b", "catalog-a", "catalog-c"),
        Vector("catalog-b", "catalog-c", "catalog-a"),
        Vector("catalog-c", "catalog-a", "catalog-b"),
        Vector("catalog-c", "catalog-b", "catalog-a")
      )
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(37.toByte))
      val identities = Vector("catalog-a", "catalog-b", "catalog-c").map { name =>
        name -> SqlDataStoreIdentity.sqliteC(
          s"target/$name.db",
          SqlDataStoreIdentity.Credential.reference("test", name),
          key
        ).toOption.get
      }.toMap
      val expected = identities.toVector.sortBy(_._2.canonicalKey).map(_._1)

      When("every deterministic insertion order is closed")
      val reports = insertionorders.map { insertionkeys =>
        val node = SystemNode.withHmacKey(key)
        val binding = node.bind()
        val lease = binding.acquireLeaseC().toOption.get
        val closeorder = scala.collection.mutable.ArrayBuffer.empty[String]
        insertionkeys.foreach { name =>
          binding.resolveC(lease, name, identities(name))(
            Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => closeorder += name))
          ).toOption.isDefined shouldBe true
        }
        lease.release()
        node.shutdownC().toOption shouldBe Some(())
        closeorder.toVector
      }

      Then("every insertion order closes in ascending canonical identity order")
      reports shouldBe Vector.fill(6)(expected)
    }

    _example("E11", "retain a zero-binding pool until SystemNode shutdown and never close it from Subsystem release") {
      Given("two resident bindings sharing one node-owned pool")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(31.toByte))
      val node = SystemNode.withHmacKey(key)
      val firstbinding = node.bind()
      val secondbinding = node.bind()
      val firstlease = firstbinding.acquireLeaseC().toOption.get
      val secondlease = secondbinding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-zero-binding.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      val resource = ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet())
      firstbinding.resolveC(firstlease, "catalog", identity)(Consequence.success(resource)).toOption shouldBe Some(resource)
      secondbinding.resolveC(secondlease, "catalog", identity)(Consequence.success(resource)).toOption shouldBe Some(resource)

      When("all logical bindings are released before node shutdown")
      firstbinding.release()
      secondbinding.release()
      val before = closecount.get()
      val result = node.shutdownC()

      Then("the shared pool remains retained until final node closure")
      before shouldBe 0
      result.toOption shouldBe Some(())
      closecount.get() shouldBe 1
    }

    _example("E12", "exclude caller-owned and injected resources from SystemNode shutdown") {
      Given("one node-owned resource and caller-owned plus injected resources")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(41.toByte))
      val node = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-owned-only.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val ownedclosecount = new java.util.concurrent.atomic.AtomicInteger(0)
      val callerownedclosecount = new java.util.concurrent.atomic.AtomicInteger(0)
      val injectedclosecount = new java.util.concurrent.atomic.AtomicInteger(0)
      binding.resolveC(lease, "catalog", identity)(Consequence.success(
        ManagedSqlDataStoreResource.hikari(null, () => ownedclosecount.incrementAndGet())
      )).toOption.isDefined shouldBe true
      val callerowned = ManagedSqlDataStoreResource.hikari(null, () => callerownedclosecount.incrementAndGet())
      val injected = ManagedSqlDataStoreResource.hikari(null, () => injectedclosecount.incrementAndGet())
      lease.release()

      When("the SystemNode is finalized")
      val result = node.shutdownC()

      Then("only the node-owned resource closes")
      result.toOption shouldBe Some(())
      ownedclosecount.get() shouldBe 1
      callerownedclosecount.get() shouldBe 0
      injectedclosecount.get() shouldBe 0
      callerowned.closeC()
      injected.closeC()
    }

    _example("E13", "close each owned resource once for sampled repeated shutdown counts") {
      Given("sampled repeated shutdown call counts")
      val property = Prop.forAll(Gen.chooseNum(1, 16)) { calls =>
        val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(43.toByte))
        val node = SystemNode.withHmacKey(key)
        val binding = node.bind()
        val lease = binding.acquireLeaseC().toOption.get
        val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
        (1 to 2).foreach { index =>
          val identity = SqlDataStoreIdentity.sqliteC(
            s"target/shutdown-property-$index.db",
            SqlDataStoreIdentity.Credential.reference("test", s"v$index"),
            key
          ).toOption.get
          binding.resolveC(lease, s"catalog-$index", identity)(
            Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet()))
          )
        }
        lease.release()
        Vector.fill(calls)(node.shutdownC()).forall(_.toOption.contains(())) && closecount.get() == 2
      }

      When("the shutdown idempotence property is checked")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("every sampled repetition preserves exactly-once node-owned closure")
      checked.passed shouldBe true
    }

    _example("E14", "terminalize and close node-owned pools when timeout cancellation itself fails") {
      Given("one active lease, one node-owned resource, and a throwing timeout callback")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(53.toByte))
      val runtime = new SystemNode.DrainRuntime {
        private var _now: Long = 0L
        def nanoTime(): Long = _now
        def await(monitor: Object, millis: Long): Unit =
          _now += millis * 1000000L
      }
      val node = SystemNode.withHmacKey(key, () => (), 1L, runtime)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-timeout-callback.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      binding.resolveC(lease, "catalog", identity)(
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet()))
      ).toOption.isDefined shouldBe true

      When("the timeout cancellation callback throws")
      val result = node.shutdownC(() => throw new IllegalStateException("planned cancellation failure"))

      Then("failure evidence is aggregated while terminal state and physical close are guaranteed")
      result.toOption shouldBe None
      node.state shouldBe SystemNode.State.Stopped
      closecount.get() shouldBe 1
    }

    _example("E15", "drain only a shared Subsystem binding without closing the node-owned pool") {
      Given("two resident bindings, with an active lease only in the binding being released")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(59.toByte))
      val runtime = new SystemNode.DrainRuntime {
        private var _now: Long = 0L
        def nanoTime(): Long = _now
        def await(monitor: Object, millis: Long): Unit =
          _now += millis * 1000000L
      }
      val node = SystemNode.withHmacKey(key, () => (), 1L, runtime)
      val retiring = node.bind()
      val resident = node.bind()
      val retiringlease = retiring.acquireLeaseC().toOption.get
      val residentlease = resident.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shared-binding-drain.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      retiring.resolveC(retiringlease, "catalog", identity)(
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet()))
      ).toOption.isDefined shouldBe true

      When("only the retiring binding reaches its bounded drain timeout")
      val result = retiring.drainC()
      retiring.release()
      val residentresource = resident.resolveC(residentlease, "catalog", identity)(
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => ()))
      )

      Then("the retiring lease is revoked while the SystemNode and shared pool remain available")
      result.toOption shouldBe None
      retiringlease.isRevoked shouldBe true
      node.state shouldBe SystemNode.State.Running
      residentresource.toOption.isDefined shouldBe true
      closecount.get() shouldBe 0
      residentlease.release()
      node.shutdownC().toOption shouldBe Some(())
      closecount.get() shouldBe 1
    }

    _example("E16", "close and terminalize when the bounded drain runtime fails") {
      Given("an active lease and a drain runtime that fails while waiting")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(61.toByte))
      val runtime = new SystemNode.DrainRuntime {
        def nanoTime(): Long = 0L
        def await(monitor: Object, millis: Long): Unit = {
          Thread.currentThread().interrupt()
          throw new InterruptedException("planned drain interruption")
        }
      }
      val node = SystemNode.withHmacKey(key, () => (), 1L, runtime)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/shutdown-drain-interruption.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new java.util.concurrent.atomic.AtomicInteger(0)
      val closeobservedinterrupt = new java.util.concurrent.atomic.AtomicBoolean(false)
      binding.resolveC(lease, "catalog", identity)(
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => {
          closeobservedinterrupt.set(Thread.currentThread().isInterrupted)
          closecount.incrementAndGet()
        }))
      ).toOption.isDefined shouldBe true

      When("the drain runtime interrupts shutdown")
      val result = node.shutdownC()
      val interruptrestored = Thread.interrupted()

      Then("the interruption is structured while node-owned resources still close and terminalize")
      result.toOption shouldBe None
      node.state shouldBe SystemNode.State.Stopped
      closecount.get() shouldBe 1
      closeobservedinterrupt.get() shouldBe false
      interruptrestored shouldBe true
    }
    }
  }

  private def _failure_display(result: Consequence[SystemNode]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(_) => fail("expected structured configuration failure")
    }

  private def _create_node_c(value: String): Consequence[SystemNode] =
    CncfConfigurationParameterCatalog.systemNodeShutdownDrainTimeoutMillis.codec
      .decode(ConfigurationValue.StringValue(value))
      .flatMap(timeout => SystemNodeShutdownConfiguration.from(
        ConfigurationBindingCollection.empty,
        Some(timeout)
      ))
      .flatMap(configuration => SystemNode.createC(configuration))

}
