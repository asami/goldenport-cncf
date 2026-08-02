package org.goldenport.cncf.datastore

import java.util.{Collections, IdentityHashMap}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStoreIdentity}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemNodeDataStorePoolRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String) = {
    val (stage, rules, slice) =
      if (Set("E1", "E2", "E7").contains(example))
        ("phase-54-dsp01", "DSP01-R1-R7", "DSP-01C")
      else
        ("phase-54-dsp03", "DSP03-R1-R6", "DSP-03")
    afterWord(s"in spec:$stage, example:$example, rules:$rules, phase:54, slice:$slice")
  }

  "SystemNode datastore-pool runtime" when {
    "recording the SystemNode ownership contract in docs/phase/phase-54.md" which {
    "E1 reject an out-of-range canonical drain timeout before runtime construction" must _metadata("E1") {
      "reject the upper-bound violation" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP01-R1-R7; Example: E1")
      Given("a SystemNode-scoped drain timeout above the configured upper bound")
      val configuration = _configuration(
        "textus.system-node.shutdown.drain-timeout-millis" -> "300001"
      )

      When("SystemNode construction resolves the canonical configuration")
      val result = SystemNode.createC(configuration)

      Then("it fails structurally with the canonical key and rejected value")
      _failure_display(result) should include ("textus.system-node.shutdown.drain-timeout-millis")
      _failure_display(result) should include ("300001")
      }
    }

    "E2 reject every sampled over-bound canonical drain timeout with keyed structured evidence" must _metadata("E2") {
      "reject every sampled violation" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP01-R1-R7; Example: E2")
      Given("sampled canonical drain timeouts above the upper bound")
      val property = Prop.forAll(Gen.chooseNum(300001, 301000)) { value =>
        SystemNode.createC(_configuration(
          "textus.system-node.shutdown.drain-timeout-millis" -> value.toString
        )) match {
          case Consequence.Failure(conclusion) =>
            conclusion.display.contains("textus.system-node.shutdown.drain-timeout-millis") &&
              conclusion.display.contains(value.toString)
          case Consequence.Success(_) => false
        }
      }

      When("the provisional parser-admission property is checked")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("every over-bound value is rejected with attributable structured evidence")
      checked.passed shouldBe true
      }
    }

    }

    "linearizing managed resource identity and creation" which {
    "E3 reuse one managed token for repeated same-key resolution in one SystemNode" must _metadata("E3") {
      "reuse one token" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E3")
      Given("sampled repeated resolutions of one opaque canonical key in one node")
      val property = Prop.forAll(Gen.chooseNum(2, 32)) { repetitions =>
        _pool_report(PoolRuntimeRequest("node-a", Vector.fill(repetitions)("catalog-v1"))).exists { report =>
          report.tokens.distinct.size == 1 && report.factoryinvocations == 1
        }
      }

      When("the SystemNode pool contract is exercised")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("one canonical identity has one published managed pool")
      checked.passed shouldBe true
      }
    }

    "E4 share equal identities across resident Subsystems and isolate another SystemNode" must _metadata("E4") {
      "share only inside one node" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E4")
      Given("two logical Subsystem bindings in one node and the same binding in another node")
      val request = PoolRuntimeRequest(
        "node-a",
        Vector("catalog-v1", "catalog-v1"),
        othernodekeys = Vector("catalog-v1")
      )

      When("the managed pool topology is resolved")
      val result = _pool_report(request)

      Then("resident bindings share one token while another node receives a different token")
      result.map { report =>
        report.tokens(0) == report.tokens(1) && report.othernodetokens.head != report.tokens.head
      } shouldBe Some(true)
      }
    }

    "E5 publish one pool after one hundred concurrent same-key requests" must _metadata("E5") {
      "single-flight one hundred requests" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E5")
      Given("one hundred deterministically coordinated requests for one canonical key")
      val request = PoolRuntimeRequest("node-a", Vector.fill(100)("catalog-v1"), concurrent = true)

      When("the SystemNode single-flight contract is exercised")
      val result = _pool_report(request)

      Then("one token is published and the factory is called once")
      result.map { report =>
        report.tokens.size == 100 && report.tokens.distinct.size == 1 && report.factoryinvocations == 1
      } shouldBe Some(true)
      }
    }

    "E6 close an unpublished failed resource and permit a later retry" must _metadata("E6") {
      "close failure before retry" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E6")
      Given("a same-key creation that fails before publication")
      val request = PoolRuntimeRequest("node-a", Vector("catalog-v1"), failfirstcreation = true)

      When("failure and retry are exercised through the SystemNode contract")
      val result = _pool_report(request)

      Then("the first resource closes once, no failed entry remains, and retry publishes once")
      result.map { report =>
        report.unpublishedclosecount == 1 && report.factoryinvocations == 2 && report.tokens.size == 1
      } shouldBe Some(true)
      }
    }

    }

    "recording terminal pool-finalization evidence" which {
    "E7 bound fake live resources by canonical identity cardinality and release them at node finalization" must _metadata("E7") {
      "bound sampled fake resources" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP01-R1-R7; Example: E7")
      Given("sampled bounded identities and acquisitions")
      val property = Prop.forAll(Gen.zip(Gen.chooseNum(1, 8), Gen.chooseNum(1, 16))) {
        case (identities, acquisitions) =>
          val keys = Vector.tabulate(acquisitions)(i => s"catalog-${i % identities}")
          _pool_report(PoolRuntimeRequest("node-a", keys, finalizenode = true)).exists { report =>
            report.maxliveresources <= identities && report.liveresourcesafterfinalization == 0
          }
      }

      When("the fake-resource acceptance contract is exercised")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("live resources remain bounded and finalization returns the count to zero")
      checked.passed shouldBe true
      }
    }

    }

    "binding leases and stopping admission" which {
    "E8 release a Subsystem binding without closing its SystemNode-owned resource" must _metadata("E8") {
      "revoke its lease while retaining the resource" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E8")
      Given("one binding with an issued lease and one published node-owned resource")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(9.toByte))
      val node = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/binding-release.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val closecount = new AtomicInteger(0)
      val resource = ManagedSqlDataStoreResource.hikari(null, () => closecount.incrementAndGet())

      When("the binding resolves its resource and is then released")
      binding.resolveC(lease, "catalog", identity)(Consequence.success(resource)).toOption shouldBe Some(resource)
      binding.release()
      val laterresolution = binding.resolveC(lease, "catalog", identity)(Consequence.success(resource))

      Then("its lease is no longer admitted and the SystemNode-owned resource remains open")
      laterresolution.toOption shouldBe None
      resource.isOpen shouldBe true
      closecount.get() shouldBe 0
      }
    }

    "E9 admit lazy resolution only for a lease granted before SystemNode Stopping" must _metadata("E9") {
      "allow the admitted lease and reject a later one" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E9")
      Given("one binding with a lease issued before Stopping and a second binding without one")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(11.toByte))
      val node = SystemNode.withHmacKey(key)
      val admittedbinding = node.bind()
      val admittedlease = admittedbinding.acquireLeaseC().toOption.get
      val laterbinding = node.bind()
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/pre-stopping-lease.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val factoryinvocations = new AtomicInteger(0)

      When("the node enters Stopping before the admitted lease resolves lazily")
      node.beginStopping()
      val resolved = admittedbinding.resolveC(admittedlease, "catalog", identity) {
        factoryinvocations.incrementAndGet()
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => ()))
      }
      val laterlease = laterbinding.acquireLeaseC()

      Then("the admitted lease may finish while a later lease is structurally rejected")
      resolved.toOption.isDefined shouldBe true
      laterlease.toOption shouldBe None
      node.state shouldBe SystemNode.State.Stopping
      factoryinvocations.get() shouldBe 1
      }
    }

    "E10 keep distinct canonical identities in one SystemNode on separate resources" must _metadata("E10") {
      "publish a resource per identity" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E10")
      Given("two distinct canonical datastore identities in one SystemNode")

      When("both identities are resolved")
      val result = _pool_report(PoolRuntimeRequest("node-a", Vector("catalog-a", "catalog-b")))

      Then("each identity receives a distinct published resource")
      result.map { report =>
        report.tokens.distinct.size == 2 && report.factoryinvocations == 2
      } shouldBe Some(true)
      }
    }

    "E11 reject a logical binding that changes its canonical identity" must _metadata("E11") {
      "preserve its first effective definition" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E11")
      Given("one logical datastore binding with an already resolved identity")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(13.toByte))
      val node = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get
      def _identity_(path: String) = SqlDataStoreIdentity.sqliteC(
        path,
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val factoryinvocations = new AtomicInteger(0)
      val firstresource = ManagedSqlDataStoreResource.hikari(null, () => ())

      When("the same logical name is resolved with a different effective identity")
      binding.resolveC(lease, "catalog", _identity_("target/catalog-a.db")) {
        factoryinvocations.incrementAndGet()
        Consequence.success(firstresource)
      }.toOption shouldBe Some(firstresource)
      val conflicting = binding.resolveC(lease, "catalog", _identity_("target/catalog-b.db")) {
        factoryinvocations.incrementAndGet()
        Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => ()))
      }

      Then("it fails structurally without replacing the published resource")
      conflicting.toOption shouldBe None
      factoryinvocations.get() shouldBe 1
      firstresource.isOpen shouldBe true
      }
    }

    "E12 inherit one binding lease without inflating SystemNode admission accounting" must _metadata("E12") {
      "return the already admitted lease" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E12")
      Given("one SystemNode binding with one admitted resource lease")
      val node = SystemNode.create()
      val binding = node.bind()
      val lease = binding.acquireLeaseC().toOption.get

      When("a nested caller inherits the active lease")
      val inherited = binding.inheritLeaseC(lease)

      Then("the same lease is reused and active admission stays singular")
      inherited.toOption shouldBe Some(lease)
      node.activeLeaseCount shouldBe 1
      binding.release()
      node.activeLeaseCount shouldBe 0
      inherited.toOption.get.isReleased shouldBe true
      }
    }

    "E13 return one concurrent failed creation outcome to its waiters and allow a later retry" must _metadata("E13") {
      "release the failed flight before retry" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP03-R1-R6; Example: E13")
      Given("a creator blocked in one same-key flight and a deterministically observed waiter")
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(17.toByte))
      val factoryentered = new CountDownLatch(1)
      val waiterentered = new CountDownLatch(1)
      val allowfailure = new CountDownLatch(1)
      val node = SystemNode.withHmacKey(key, () => waiterentered.countDown())
      val firstbinding = node.bind()
      val secondbinding = node.bind()
      val firstlease = firstbinding.acquireLeaseC().toOption.get
      val secondlease = secondbinding.acquireLeaseC().toOption.get
      val identity = SqlDataStoreIdentity.sqliteC(
        "target/concurrent-failure-retry.db",
        SqlDataStoreIdentity.Credential.reference("test", "v1"),
        key
      ).toOption.get
      val factoryinvocations = new AtomicInteger(0)
      val unpublishedclosecount = new AtomicInteger(0)
      val executor = Executors.newFixedThreadPool(2)

      try {
        When("the creator fails after a same-key waiter has joined its flight")
        val first = executor.submit(new Callable[Consequence[ManagedSqlDataStoreResource]] {
          def call(): Consequence[ManagedSqlDataStoreResource] =
            firstbinding.resolveC(firstlease, "catalog", identity) {
              factoryinvocations.incrementAndGet()
              factoryentered.countDown()
              allowfailure.await()
              val candidate = ManagedSqlDataStoreResource.hikari(null, () => unpublishedclosecount.incrementAndGet())
              candidate.closeC()
              throw new IllegalStateException("planned concurrent creation failure")
            }
        })
        factoryentered.await(10, TimeUnit.SECONDS) shouldBe true
        val second = executor.submit(new Callable[Consequence[ManagedSqlDataStoreResource]] {
          def call(): Consequence[ManagedSqlDataStoreResource] =
            secondbinding.resolveC(secondlease, "catalog", identity)(Consequence.success(
              ManagedSqlDataStoreResource.hikari(null, () => ())
            ))
        })
        waiterentered.await(10, TimeUnit.SECONDS) shouldBe true
        allowfailure.countDown()
        val firstresult = first.get(10, TimeUnit.SECONDS)
        val secondresult = second.get(10, TimeUnit.SECONDS)
        val retry = firstbinding.resolveC(firstlease, "catalog", identity) {
          factoryinvocations.incrementAndGet()
          Consequence.success(ManagedSqlDataStoreResource.hikari(null, () => ()))
        }

        Then("both concurrent callers receive the one failure and a later resolution creates once")
        firstresult.toOption shouldBe None
        secondresult.toOption shouldBe None
        retry.toOption.isDefined shouldBe true
        factoryinvocations.get() shouldBe 2
        unpublishedclosecount.get() shouldBe 1
      } finally {
        allowfailure.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
      }
      }
    }

    }
  }

  private def _failure_display(result: Consequence[SystemNode]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(_) => fail("expected structured configuration failure")
    }

  private def _configuration(
    values: (String, String)*
  ): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap),
      ConfigurationTrace.empty
    )

  private def _pool_report(request: PoolRuntimeRequest): Option[PoolRuntimeReport] =
    PoolRuntimePort.current.observe(request).toOption
}

private final case class PoolRuntimeRequest(
  nodeid: String,
  canonicalkeys: Vector[String],
  othernodekeys: Vector[String] = Vector.empty,
  concurrent: Boolean = false,
  failfirstcreation: Boolean = false,
  finalizenode: Boolean = false
)

private final case class PoolRuntimeReport(
  tokens: Vector[String],
  othernodetokens: Vector[String] = Vector.empty,
  factoryinvocations: Int = 0,
  unpublishedclosecount: Int = 0,
  maxliveresources: Int = 0,
  liveresourcesafterfinalization: Int = 0
)

private trait PoolRuntimePort {
  def observe(request: PoolRuntimeRequest): Consequence[PoolRuntimeReport]
}

private object PoolRuntimePort {
  val current: PoolRuntimePort = new PoolRuntimePort {
    def observe(request: PoolRuntimeRequest): Consequence[PoolRuntimeReport] = {
      val key = SqlDataStoreIdentity.HmacKey(Array.fill(32)(7.toByte))
      val creatorentered = new CountDownLatch(1)
      val waiterentered = new CountDownLatch(1)
      val allowcreation = new CountDownLatch(1)
      val node = SystemNode.withHmacKey(key, () => waiterentered.countDown())
      val othernode = SystemNode.withHmacKey(key)
      val binding = node.bind()
      val residentbinding = node.bind()
      val otherbinding = othernode.bind()
      val lease = binding.acquireLeaseC().toOption.get
      val residentlease = residentbinding.acquireLeaseC().toOption.get
      val otherlease = otherbinding.acquireLeaseC().toOption.get
      val factoryinvocations = new AtomicInteger(0)
      val unpublishedclosecount = new AtomicInteger(0)
      val failfirstcreation = new AtomicInteger(if (request.failfirstcreation) 1 else 0)
      val resourceids = Collections.synchronizedMap(new IdentityHashMap[ManagedSqlDataStoreResource, String]())
      val nextresourceid = new AtomicInteger(0)

      def _identity_(key: String, owner: SystemNode) =
        SqlDataStoreIdentity.sqliteC(s"target/$key.db", SqlDataStoreIdentity.Credential.reference("test", "v1"), owner.hmacKey).toOption.get
      def _resource_() = {
        val resource = ManagedSqlDataStoreResource.hikari(null, () => unpublishedclosecount.incrementAndGet())
        resourceids.put(resource, s"resource-${nextresourceid.incrementAndGet()}")
        resource
      }
      def _token_(resource: ManagedSqlDataStoreResource): String =
        Option(resourceids.get(resource)).getOrElse(throw new IllegalStateException("untracked managed resource"))
      def _resolve_(
        targetbinding: org.goldenport.cncf.subsystem.SystemNodeDataStoreBinding,
        targetlease: org.goldenport.cncf.subsystem.SystemNodeResourceLease,
        key: String
      ): Consequence[String] = {
        val target = _identity_(key, node)
        targetbinding.resolveC(targetlease, key, target) {
          factoryinvocations.incrementAndGet()
          if (request.concurrent) {
            creatorentered.countDown()
            allowcreation.await()
          }
          val candidate = _resource_()
          if (failfirstcreation.compareAndSet(1, 0)) {
            candidate.closeC()
            throw new IllegalStateException("planned first creation failure")
          } else {
            Consequence.success(candidate)
          }
        }.map(_token_)
      }
      val tokens =
        if (request.concurrent)
          _resolve_concurrently(node, request.canonicalkeys, _resolve_, creatorentered, waiterentered, allowcreation)
        else
          request.canonicalkeys.zipWithIndex.flatMap { case (key, index) =>
            val (targetbinding, targetlease) =
              if (index % 2 == 0) (binding, lease) else (residentbinding, residentlease)
            _resolve_(targetbinding, targetlease, key).toOption
          }
      val othertokens = request.othernodekeys.flatMap { key =>
        val target = _identity_(key, othernode)
        otherbinding.resolveC(otherlease, key, target) {
          factoryinvocations.incrementAndGet()
          Consequence.success(_resource_())
        }.toOption.map(_token_)
      }
      val retrytokens =
        if (request.failfirstcreation)
          request.canonicalkeys.take(1).flatMap(key => _resolve_(binding, lease, key).toOption)
        else Vector.empty
      val maxliveresources = resourceids.size()
      val liveresourcesafterfinalization =
        if (request.finalizenode) {
          lease.release()
          residentlease.release()
          node.shutdownC().toOption.getOrElse(
            throw new IllegalStateException("SystemNode finalization failed")
          )
          resourceids.keySet().toArray.toVector.collect {
            case resource: ManagedSqlDataStoreResource if resource.isOpen => resource
          }.size
        } else
          0
      Consequence.success(PoolRuntimeReport(
        tokens ++ retrytokens,
        othertokens,
        factoryinvocations.get(),
        unpublishedclosecount.get(),
        maxliveresources,
        liveresourcesafterfinalization
      ))
    }

    private def _resolve_concurrently(
      node: SystemNode,
      keys: Vector[String],
      resolve: (
        org.goldenport.cncf.subsystem.SystemNodeDataStoreBinding,
        org.goldenport.cncf.subsystem.SystemNodeResourceLease,
        String
      ) => Consequence[String],
      creatorentered: CountDownLatch,
      waiterentered: CountDownLatch,
      allowcreation: CountDownLatch
    ): Vector[String] = {
      val executor = Executors.newFixedThreadPool(math.min(keys.size, 16))
      val start = new CountDownLatch(1)
      try {
        val futures = keys.map { key =>
          executor.submit(new Callable[Consequence[String]] {
            def call(): Consequence[String] = {
              start.await()
              val binding = node.bind()
              binding.acquireLeaseC().flatMap { lease =>
                resolve(binding, lease, key)
              }
            }
          })
        }
        start.countDown()
        if (!creatorentered.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("concurrent creator did not enter its factory")
        if (!waiterentered.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("concurrent same-key waiter did not join the flight")
        allowcreation.countDown()
        futures.map { future =>
          future.get(10, TimeUnit.SECONDS).toOption.getOrElse(
            throw new IllegalStateException("concurrent same-key resolution failed")
          )
        }
      } finally {
        allowcreation.countDown()
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS))
          executor.shutdownNow()
      }
    }
  }
}
