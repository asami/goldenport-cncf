package org.goldenport.cncf.component.repository

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import org.goldenport.cncf.component.{ComponentId, _}
import org.goldenport.cncf.observability.CallTreeContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

/*
 * Failing-first executable acceptance specification for
 * RSC07-LIFECYCLE-OBSERVABILITY (Phase 58.6 / RSC-07 / RSC-07A).
 *
 * The lifecycle vocabulary is intentionally referenced before its production
 * implementation.  This specification fixes immutable resource reuse,
 * ownership, terminal races, cancellation, refresh generations, and the safe
 * observability boundary without granting content or authorization authority.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentResourceLifecycleSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _namespace = "org.goldenport.cncf.phase58"
  private val _release = "0.1.0-SNAPSHOT"
  private val _parent_id = ComponentId(s"$_namespace.RscParent")
  private val _documentation_id = ComponentId(s"$_namespace.RscDocumentation")
  private val _source_id = ComponentId(s"$_namespace.RscSourceCode")
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

  private val _e1 = afterWord(
    "in spec:docs/notes/phase-58-rsc01b-failing-first-acceptance-registry.md, example:E1, rules:RSC07-AC-01, phase:58.6, slice:RSC-07A"
  )
  private val _e2 = afterWord(
    "in spec:docs/notes/phase-58-rsc01b-failing-first-acceptance-registry.md, example:E2, rules:RSC07-AC-02, phase:58.6, slice:RSC-07A"
  )
  private val _e3 = afterWord(
    "in spec:docs/notes/phase-58-rsc01b-failing-first-acceptance-registry.md, example:E3, rules:RSC07-AC-03, phase:58.6, slice:RSC-07A"
  )

  "RSC07-AC-01 shared immutable resource evidence, in-flight resolution, refresh, and invalidation" should {
    "share one immutable resolved resource and coalesce concurrent owners to one loader execution" must _e1 {
      "when two owners resolve one stable lifecycle key concurrently" in {
        Given("one immutable ResolvedComponentResource, one stable ComponentResourceLifecycleKey, and a deterministic blocked loader")
        val resource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(resource)
        val loader = new BlockingLoader(resource)
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)
        val ready = new CountDownLatch(2)
        val permit = new CountDownLatch(1)

        def resolve(owner: String): ComponentResourceLifecycleSnapshot = {
          ready.countDown()
          permit.await()
          store.resolve(key, owner, loader.load)
        }

        When("both owner requests are admitted and the loader is released")
        val first = Future(resolve("component-instance-a"))
        val second = Future(resolve("component-instance-b"))
        ready.await(5, TimeUnit.SECONDS) shouldBe true
        permit.countDown()
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        loader.release.countDown()
        val snapshots = try {
          Vector(
            Await.result(first, 5.seconds),
            Await.result(second, 5.seconds)
          )
        } finally executor.shutdownNow()

        Then("both owners observe the same immutable evidence and only one loader execution publishes the generation")
        loader.calls.get() shouldBe 1
        snapshots.map(_.resource.get) should have size 2
        snapshots(0).resource.get should be theSameInstanceAs snapshots(1).resource.get
        snapshots.map(_.generation).distinct should have size 1
        snapshots.last.owners should contain allOf ("component-instance-a", "component-instance-b")
        snapshots.foreach(_.state shouldBe ComponentResourceLifecycleState.Ready)
      }
    }

    "refresh deterministically creates a new generation and invalidation prevents stale reuse" must _e1 {
      "when a claimed resource is refreshed and then invalidated" in {
        Given("a stable key with immutable generation-one and generation-two evidence")
        val firstresource = _resource(_documentation_id, "Documentation", _digest)
        val secondresource = _resource(_documentation_id, "Documentation", _digest.reverse)
        val key = _key(firstresource)
        val store = new ComponentResourceLifecycleStore()

        When("the owner resolves, refreshes, invalidates, and resolves the same key again")
        val first = store.resolve(key, "component-instance-a", () => firstresource)
        val refreshed = store.refresh(key, "component-instance-a", () => secondresource)
        store.invalidate(key)
        val afterinvalidation = store.resolve(key, "component-instance-a", () => firstresource)

        Then("refresh and invalidation advance the generation and never return stale evidence")
        first.resource shouldBe Some(firstresource)
        refreshed.resource shouldBe Some(secondresource)
        refreshed.generation should be > first.generation
        afterinvalidation.resource shouldBe Some(firstresource)
        afterinvalidation.generation should be > refreshed.generation
        afterinvalidation.state shouldBe ComponentResourceLifecycleState.Ready
      }
    }

    "retain independent owner claims while one owner releases a shared resource" must _e1 {
      "when one of two owners releases its claim" in {
        Given("one ready resource claimed by two component instances")
        val resource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(resource)
        val store = new ComponentResourceLifecycleStore()
        store.resolve(key, "component-instance-a", () => resource)
        val shared = store.resolve(key, "component-instance-b", () => fail("a second loader must not run"))

        When("the first owner releases and the second owner remains active")
        val afterfirstrelease = store.release(key, "component-instance-a")

        Then("the resource remains ready for the second owner and its evidence remains immutable")
        afterfirstrelease.state shouldBe ComponentResourceLifecycleState.Ready
        afterfirstrelease.owners shouldBe Set("component-instance-b")
        afterfirstrelease.resource shouldBe shared.resource
        store.snapshot(key).owners shouldBe Set("component-instance-b")
      }
    }

    "retain another owner claim when one component instance shuts down" must _e1 {
      "when shutdown is requested for only one owner of a shared resource" in {
        Given("one ready resource claimed by two component instances")
        val resource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(resource)
        val store = new ComponentResourceLifecycleStore()
        store.resolve(key, "component-instance-a", () => resource)
        store.resolve(key, "component-instance-b", () => resource)

        When("component-instance-a shuts down its lifecycle claim")
        val aftershutdown = store.shutdown(key, "component-instance-a")

        Then("component-instance-b remains ready with the shared immutable evidence")
        aftershutdown.state shouldBe ComponentResourceLifecycleState.Ready
        aftershutdown.owners shouldBe Set("component-instance-b")
        aftershutdown.resource shouldBe Some(resource)
        store.snapshot(key).state shouldBe ComponentResourceLifecycleState.Ready
      }
    }
  }

  "RSC07-AC-02 release, unload, shutdown, cancellation, and refresh races" should {
    "preserve the actual first terminal outcome when release and unload race" must _e2 {
      "when two terminal operations contend for one ready generation" in {
        Given("one ready lifecycle generation and deterministic simultaneous release/unload contenders")
        val resource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(resource)
        val store = new ComponentResourceLifecycleStore()
        store.resolve(key, "component-instance-a", () => resource)
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)
        val barrier = new java.util.concurrent.CyclicBarrier(2)

        When("release and unload are submitted at the same lifecycle boundary")
        val release = Future {
          barrier.await()
          store.release(key, "component-instance-a")
        }
        val unload = Future {
          barrier.await()
          store.unload(key)
        }
        val outcomes = try {
          Vector(
            Await.result(release, 5.seconds),
            Await.result(unload, 5.seconds)
          )
        } finally executor.shutdownNow()

        Then("both observers report the same actual first terminal outcome and no later operation rewrites it")
        outcomes.map(_.outcome).distinct should have size 1
        outcomes.head.outcome should (be(ComponentResourceLifecycleOutcome.Released) or be(ComponentResourceLifecycleOutcome.Unloaded))
        store.snapshot(key).outcome shouldBe outcomes.head.outcome
        store.snapshot(key).state should (be(ComponentResourceLifecycleState.Released) or be(ComponentResourceLifecycleState.Unloaded))
      }
    }

    "let shutdown win once and preserve it against subsequent release or unload calls" must _e2 {
      "when a store is shut down after a resource has been loaded" in {
        Given("a store with one loaded resource and an owner claim")
        val resource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(resource)
        val store = new ComponentResourceLifecycleStore()
        store.resolve(key, "component-instance-a", () => resource)

        When("shutdown is followed by release and unload attempts")
        val shutdown = store.shutdown()
        val release = store.release(key, "component-instance-a")
        val unload = store.unload(key)

        Then("shutdown remains the first terminal outcome for the key")
        shutdown.outcome shouldBe ComponentResourceLifecycleOutcome.Shutdown
        release.outcome shouldBe ComponentResourceLifecycleOutcome.Shutdown
        unload.outcome shouldBe ComponentResourceLifecycleOutcome.Shutdown
        store.snapshot(key).state shouldBe ComponentResourceLifecycleState.Shutdown
      }
    }

    "preserve cancellation as the terminal outcome and never publish canceled evidence" must _e2 {
      "when cancellation wins while resolution is still in flight" in {
        Given("a blocked loader, a cancellation token, and an empty cache generation")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val loader = new BlockingLoader(resource)
        val cancellation = new ComponentResourceLifecycleCancellation()
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newSingleThreadExecutor()
        given ExecutionContext = ExecutionContext.fromExecutor(executor)

        When("the request is canceled before the blocked loader is allowed to complete")
        val pending = Future(store.resolve(key, "component-instance-a", loader.load, cancellation))
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        cancellation.cancel() shouldBe true
        loader.release.countDown()
        val canceled = try Await.result(pending, 5.seconds) finally executor.shutdownNow()

        Then("the first terminal cancellation is retained and the canceled resource is absent from the cache")
        canceled.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        canceled.state shouldBe ComponentResourceLifecycleState.Cancelled
        canceled.resource shouldBe None
        store.snapshot(key).resource shouldBe None
        store.metrics.cancellationCount shouldBe 1
      }
    }

    "keep the first terminal cancellation when refresh races with an in-flight generation" must _e2 {
      "when refresh is requested after cancellation has won" in {
        Given("a canceled key with a replacement resource available to a later refresh request")
        val original = _resource(_documentation_id, "Documentation", _digest)
        val replacement = _resource(_documentation_id, "Documentation", _digest.reverse)
        val key = _key(original)
        val cancellation = new ComponentResourceLifecycleCancellation()
        cancellation.cancel() shouldBe true
        val store = new ComponentResourceLifecycleStore()

        When("the canceled resolution is followed by refresh without a new explicit generation")
        val canceled = store.resolve(key, "component-instance-a", () => original, cancellation)
        val refreshed = store.refresh(key, "component-instance-a", () => replacement)

        Then("the refresh reports the preserved cancellation and does not overwrite its terminal generation")
        canceled.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        refreshed.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        refreshed.state shouldBe ComponentResourceLifecycleState.Cancelled
        refreshed.resource shouldBe None
      }
    }
  }

  "RSC07-AC-03 bounded safe lifecycle observability" should {
    "emit only bounded component, release, role, source, digest, operation, and outcome evidence" must _e3 {
      "when a failing resource produces more diagnostics than the configured bound" in {
        Given("a lifecycle store with a bounded diagnostic budget and an enabled optional CallTreeContext")
        val calltree = CallTreeContext.enabled
        val store = new ComponentResourceLifecycleStore(
          diagnosticLimit = 2,
          callTreeContext = Some(calltree)
        )
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val secret = "source-content:do-not-disclose credential=password secret=phase58-secret"

        When("the same safe identity records repeated failures containing hostile provider text")
        (1 to 5).foreach { _ =>
          val snapshot = store.resolve(key, "component-instance-a", () => throw new IllegalStateException(secret))
          snapshot.outcome shouldBe ComponentResourceLifecycleOutcome.Failed
        }

        Then("diagnostics remain bounded, structured, and free of sensitive or physical evidence")
        store.diagnostics should have size 2
        store.diagnostics.foreach { diagnostic =>
          diagnostic.componentId shouldBe _source_id
          diagnostic.release shouldBe _release
          diagnostic.role shouldBe "SourceCode"
          diagnostic.sourceKind shouldBe ComponentResourceSourceKind.ExpandedCar
          diagnostic.artifactDigest shouldBe _digest
          diagnostic.operation shouldBe "resolve"
          diagnostic.outcome shouldBe ComponentResourceLifecycleOutcome.Failed
          val rendered = diagnostic.toString
          rendered should not include secret
          rendered should not include _repository
          rendered should not include "/Users/asami"
          rendered should not include "password"
          rendered should not include "credential"
        }
        store.metrics.failureCount shouldBe 5
        calltree.build().toString should not include secret
      }
    }

    "isolate one-key failure from an independent key and retain only optional safe CallTree evidence" must _e3 {
      "when one key fails while another key resolves successfully" in {
        Given("one failing source resource and an independent documentation resource with separate lifecycle keys")
        val calltree = CallTreeContext.enabled
        val store = new ComponentResourceLifecycleStore(callTreeContext = Some(calltree))
        val failedresource = _resource(_source_id, "SourceCode", _digest)
        val goodresource = _resource(_documentation_id, "Documentation", _digest.reverse)
        val failedkey = _key(failedresource)
        val goodkey = _key(goodresource)

        When("the source key fails and the independent documentation key is resolved afterward")
        val failed = store.resolve(failedkey, "component-instance-a", () => throw new RuntimeException("provider output /Users/asami/private secret=redact"))
        val good = store.resolve(goodkey, "component-instance-b", () => goodresource)

        Then("the failure remains local and the independent key retains ready immutable evidence")
        failed.state shouldBe ComponentResourceLifecycleState.Failed
        good.state shouldBe ComponentResourceLifecycleState.Ready
        good.resource shouldBe Some(goodresource)
        store.snapshot(goodkey).resource shouldBe Some(goodresource)
        store.diagnostics.find(_.key == failedkey).get.outcome shouldBe ComponentResourceLifecycleOutcome.Failed
        store.diagnostics.find(_.key == goodkey) shouldBe None
        calltree.build().toString should not include "provider output"
        calltree.build().toString should not include "/Users/asami/private"
      }
    }
  }

  private def _key(resource: ResolvedComponentResource): ComponentResourceLifecycleKey =
    ComponentResourceLifecycleKey(
      componentId = resource.logicalIdentity.componentId,
      logicalRelease = resource.logicalIdentity.logicalRelease,
      role = resource.logicalIdentity.childRole,
      sourceKind = resource.provenance.sourceKind,
      artifactDigest = resource.provenance.sha256
    )

  private def _resource(componentId: ComponentId, role: String, digest: String): ResolvedComponentResource =
    ResolvedComponentResource(
      logicalIdentity = ComponentResourceLogicalIdentity(
        componentId = componentId,
        logicalRelease = _release,
        parentComponentId = if (componentId == _parent_id) None else Some(_parent_id),
        childRole = role,
        logicalResource = s"urn:cncf:resource:phase58/${role.toLowerCase}"
      ),
      provenance = ComponentResourceProvenance(
        sourceKind = ComponentResourceSourceKind.ExpandedCar,
        repository = _repository,
        artifactCoordinate = s"org.example:rsc-${role.toLowerCase}:0.1.0",
        sha256 = digest,
        normalizedRelativePath = s"expanded/rsc-${role.toLowerCase}/resource",
        logicalSource = s"composition-registry:rsc-${role.toLowerCase}",
        physicalSource = s"expanded-car:rsc-${role.toLowerCase}",
        resolutionStep = "expanded-car:2",
        childRole = role,
        logicalResource = s"urn:cncf:resource:phase58/${role.toLowerCase}",
        access = "described",
        license = "Apache-2.0",
        externalDeploymentRequired = false
      ),
      availability = ComponentResourceAvailability.Available,
      integrity = ComponentResourceIntegrity.Verified,
      authorization = ComponentResourceAuthorization.Granted,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )

  private final class BlockingLoader(resource: ResolvedComponentResource) {
    val calls = new AtomicInteger(0)
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)

    def load(): ResolvedComponentResource = {
      calls.incrementAndGet()
      entered.countDown()
      release.await(5, TimeUnit.SECONDS) shouldBe true
      resource
    }
  }
}
