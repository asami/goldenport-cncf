package org.goldenport.cncf.component.repository

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.goldenport.cncf.component.{ComponentId, _}
import org.goldenport.cncf.observability.CallTreeContext
import org.goldenport.observation.calltree.CallTreeNode
import org.goldenport.tree.{TreeDir, TreeLeaf, TreeNode}
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

        def _resolve_blocking_(owner: String): ComponentResourceLifecycleSnapshot = {
          ready.countDown()
          permit.await()
          store.resolveBlocking(key, owner, loader.loadBlocking)
        }

        When("both owner requests are admitted and the loader is released")
        val first = Future(_resolve_blocking_("component-instance-a"))
        val second = Future(_resolve_blocking_("component-instance-b"))
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

    "return the completed first refresh flight to a concurrently admitted refresh owner" must _e1 {
      "when a second refresh reaches admission while the first refresh loader remains blocked" in {
        Given("a ready generation, a blocked replacement loader, and a second refresh request released only after it reaches the admission boundary")
        val initial = _resource(_documentation_id, "Documentation", _digest)
        val replacement = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(initial)
        val loader = new BlockingLoader(replacement)
        val store = new ComponentResourceLifecycleStore()
        store.resolveBlocking(key, "component-instance-a", () => initial)
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)

        When("the first refresh starts loading and the second owner admission is observed before the loader is released")
        val first = Future(store.refreshBlocking(key, "component-instance-a", loader.loadBlocking))
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val second = Future {
          store.refreshBlocking(key, "component-instance-b", () => fail("a joined refresh must not invoke a second loader"))
        }
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        loader.release.countDown()
        val snapshots = try Vector(Await.result(first, 5.seconds), Await.result(second, 5.seconds)) finally executor.shutdownNow()

        Then("both refresh callers receive the one completed generation and one loader publication")
        loader.calls.get() shouldBe 1
        snapshots.map(_.generation).distinct should have size 1
        snapshots.foreach(_.resource shouldBe Some(replacement))
        snapshots.foreach(_.owners should contain allOf ("component-instance-a", "component-instance-b"))
      }
    }

    "refresh deterministically creates a new generation and invalidation prevents stale reuse" must _e1 {
      "when a claimed resource is refreshed and then invalidated" in {
        Given("a stable key with immutable generation-one and generation-two evidence")
        val firstresource = _resource(_documentation_id, "Documentation", _digest)
        val secondresource = _resource(_documentation_id, "Documentation", _digest)
        val key = _key(firstresource)
        val store = new ComponentResourceLifecycleStore()

        When("the owner resolves, refreshes, invalidates, and resolves the same key again")
        val first = store.resolveBlocking(key, "component-instance-a", () => firstresource)
        val refreshed = store.refreshBlocking(key, "component-instance-a", () => secondresource)
        store.invalidate(key)
        val afterinvalidation = store.resolveBlocking(key, "component-instance-a", () => firstresource)

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
        store.resolveBlocking(key, "component-instance-a", () => resource)
        val shared = store.resolveBlocking(key, "component-instance-b", () => fail("a second loader must not run"))

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
        store.resolveBlocking(key, "component-instance-a", () => resource)
        store.resolveBlocking(key, "component-instance-b", () => resource)

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
        store.resolveBlocking(key, "component-instance-a", () => resource)
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
        store.resolveBlocking(key, "component-instance-a", () => resource)

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
        val pending = Future(store.resolveBlocking(key, "component-instance-a", loader.loadBlocking, cancellation))
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

    "isolate a loading owner cancellation from an admitted waiting owner" must _e2 {
      "when the producer is canceled only after a second owner is observed in the shared flight" in {
        Given("a blocked producer with its own cancellation token and a second owner waiting in the same flight")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val loader = new BlockingLoader(resource)
        val cancellation = new ComponentResourceLifecycleCancellation()
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)

        When("the second owner admission is proven before the producer is canceled and released")
        val producer = Future(store.resolveBlocking(key, "component-instance-a", loader.loadBlocking, cancellation))
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val waiter = Future {
          store.resolveBlocking(key, "component-instance-b", () => fail("an admitted waiter must not invoke a second loader"))
        }
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        cancellation.cancel() shouldBe true
        loader.release.countDown()
        val snapshots = try {
          Vector(Await.result(producer, 5.seconds), Await.result(waiter, 5.seconds))
        } finally executor.shutdownNow()

        Then("the canceled producer receives no resource while the admitted waiter retains the one ready publication")
        loader.calls.get() shouldBe 1
        snapshots.head.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        snapshots.head.resource shouldBe None
        snapshots(1).state shouldBe ComponentResourceLifecycleState.Ready
        snapshots(1).resource shouldBe Some(resource)
        store.snapshot(key).state shouldBe ComponentResourceLifecycleState.Ready
        store.snapshot(key).resource shouldBe Some(resource)
        store.snapshot(key).owners shouldBe Set("component-instance-b")
        store.metrics.cancellationCount shouldBe 1
      }
    }

    "cancel only a waiting owner while the loading owner publishes the shared resource" must _e2 {
      "when one waiter is admitted before its cancellation arrives and the first blocked loader completes" in {
        Given("a blocked loading owner, an observable admitted waiter, and a cancellation token owned only by that waiter")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val loader = new BlockingLoader(resource)
        val cancellation = new ComponentResourceLifecycleCancellation()
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)

        When("the waiter admission is proven against the store state, is canceled, and the producer later completes")
        val producer = Future(store.resolveBlocking(key, "component-instance-a", loader.loadBlocking))
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val waiter = Future {
          store.resolveBlocking(key, "component-instance-b", () => fail("a waiting owner must not invoke the loader"), cancellation)
        }
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        cancellation.cancel() shouldBe true
        val canceled = Await.result(waiter, 5.seconds)
        loader.calls.get() shouldBe 1
        loader.release.countDown()
        val ready = try Await.result(producer, 5.seconds) finally executor.shutdownNow()

        Then("the waiter receives only its cancelled no-resource result while the producer retains the ready generation")
        canceled.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        canceled.resource shouldBe None
        ready.state shouldBe ComponentResourceLifecycleState.Ready
        store.snapshot(key).resource shouldBe Some(resource)
        store.snapshot(key).owners shouldBe Set("component-instance-a")
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
        val canceled = store.resolveBlocking(key, "component-instance-a", () => original, cancellation)
        val refreshed = store.refreshBlocking(key, "component-instance-a", () => replacement)

        Then("the refresh reports the preserved cancellation and does not overwrite its terminal generation")
        canceled.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        refreshed.outcome shouldBe ComponentResourceLifecycleOutcome.Cancelled
        refreshed.state shouldBe ComponentResourceLifecycleState.Cancelled
        refreshed.resource shouldBe None
      }
    }

    "propagate an interrupted wait without corrupting the producer flight or its owner claims" must _e2 {
      "when a waiting caller thread is interrupted while another owner remains in the blocked loader" in {
        Given("one blocked producer, one waiter thread, and a captured interruption result")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val loader = new BlockingLoader(resource)
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newSingleThreadExecutor()
        given ExecutionContext = ExecutionContext.fromExecutor(executor)
        val waiterfailure = new AtomicReference[Throwable]()

        When("the waiter admission is proven against the store state before its thread is interrupted")
        val producer = Future(store.resolveBlocking(key, "component-instance-a", loader.loadBlocking))
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val waiter = new Thread(() => {
          try {
            store.resolveBlocking(key, "component-instance-b", () => fail("an interrupted waiter must not invoke a loader"))
            ()
          }
          catch { case failure: Throwable => waiterfailure.set(failure) }
        })
        waiter.start()
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        waiter.interrupt()
        waiter.join(5000L)
        loader.release.countDown()
        val ready = try Await.result(producer, 5.seconds) finally executor.shutdownNow()

        Then("interruption is preserved and the producer remains the only ready owner")
        waiterfailure.get() shouldBe a[InterruptedException]
        ready.state shouldBe ComponentResourceLifecycleState.Ready
        store.snapshot(key).owners shouldBe Set("component-instance-a")
        store.snapshot(key).resource shouldBe Some(resource)
      }
    }

    "propagate a fatal loader error unchanged to an admitted waiter and permit a later retry" must _e2 {
      "when a LinkageError is raised after the waiter has joined the blocked shared flight" in {
        Given("a blocked fatal loader, an admitted waiter, and valid immutable retry evidence")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val failure = new LinkageError("fatal loader failure")
        val loader = new BlockingFailureLoader(failure)
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)
        val producerfailure = new AtomicReference[Throwable]()
        val waiterfailure = new AtomicReference[Throwable]()

        When("the waiter admission is proven before the fatal loader is released and a valid retry follows")
        val producer = Future {
          try store.resolveBlocking(key, "component-instance-a", loader.loadBlocking)
          catch { case caught: Throwable => producerfailure.set(caught) }
        }
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val waiter = Future {
          try store.resolveBlocking(key, "component-instance-b", () => fail("an admitted waiter must not invoke a second loader"))
          catch { case caught: Throwable => waiterfailure.set(caught) }
        }
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        loader.release.countDown()
        try {
          Await.result(producer, 5.seconds)
          Await.result(waiter, 5.seconds)
        } finally executor.shutdownNow()
        val abandoned = store.snapshot(key)
        val ready = store.resolveBlocking(key, "component-instance-a", () => resource)

        Then("both participants receive the original fatal error and only the later retry publishes valid evidence")
        producerfailure.get() should be theSameInstanceAs failure
        waiterfailure.get() should be theSameInstanceAs failure
        store.metrics.failureCount shouldBe 0
        abandoned.state shouldBe ComponentResourceLifecycleState.Empty
        abandoned.resource shouldBe None
        abandoned.owners shouldBe empty
        ready.state shouldBe ComponentResourceLifecycleState.Ready
        ready.resource shouldBe Some(resource)
        store.snapshot(key).owners shouldBe Set("component-instance-a")
      }
    }

    "propagate an interrupted loader exception unchanged to an admitted waiter and permit a later retry" must _e2 {
      "when an InterruptedException is raised after the waiter has joined the blocked shared flight" in {
        Given("a blocked interrupted loader, an admitted waiter, and valid immutable retry evidence")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val failure = new InterruptedException("interrupted loader failure")
        val loader = new BlockingFailureLoader(failure)
        val store = new ComponentResourceLifecycleStore()
        val executor = Executors.newFixedThreadPool(2)
        given ExecutionContext = ExecutionContext.fromExecutor(executor)
        val producerfailure = new AtomicReference[Throwable]()
        val waiterfailure = new AtomicReference[Throwable]()

        When("the waiter admission is proven before the interrupted loader is released and a valid retry follows")
        val producer = Future {
          try store.resolveBlocking(key, "component-instance-a", loader.loadBlocking)
          catch { case caught: Throwable => producerfailure.set(caught) }
        }
        loader.entered.await(5, TimeUnit.SECONDS) shouldBe true
        val waiter = Future {
          try store.resolveBlocking(key, "component-instance-b", () => fail("an admitted waiter must not invoke a second loader"))
          catch { case caught: Throwable => waiterfailure.set(caught) }
        }
        def _await_owner_admission_(): Unit = {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (!store.snapshot(key).owners.contains("component-instance-b") && System.nanoTime() < deadline)
            Thread.`yield`()
        }
        _await_owner_admission_()
        store.snapshot(key).owners should contain ("component-instance-b")
        loader.release.countDown()
        try {
          Await.result(producer, 5.seconds)
          Await.result(waiter, 5.seconds)
        } finally executor.shutdownNow()
        val abandoned = store.snapshot(key)
        val ready = store.resolveBlocking(key, "component-instance-a", () => resource)

        Then("both participants receive the original interruption and only the later retry publishes valid evidence")
        producerfailure.get() should be theSameInstanceAs failure
        waiterfailure.get() should be theSameInstanceAs failure
        store.metrics.failureCount shouldBe 0
        abandoned.state shouldBe ComponentResourceLifecycleState.Empty
        abandoned.resource shouldBe None
        abandoned.owners shouldBe empty
        ready.state shouldBe ComponentResourceLifecycleState.Ready
        ready.resource shouldBe Some(resource)
        store.snapshot(key).owners shouldBe Set("component-instance-a")
      }
    }
  }

  "RSC07-AC-03 bounded safe lifecycle observability" should {
    "reject unbounded key text with a fixed safe construction error" must _e3 {
      "when caller supplied role text cannot be admitted to lifecycle observability" in {
        Given("an otherwise valid lifecycle key whose role text contains unsafe caller data")

        When("the key is constructed before any lifecycle admission")
        val error = intercept[IllegalArgumentException] {
          ComponentResourceLifecycleKey(
            componentId = _source_id,
            logicalRelease = _release,
            role = "SourceCode credential=unbounded",
            sourceKind = ComponentResourceSourceKind.ExpandedCar,
            artifactDigest = _digest
          )
        }

        Then("construction rejects it with no caller-provided text in the error")
        error.getMessage shouldBe "requirement failed: invalid component resource lifecycle key"
        error.getMessage should not include "credential"
      }
    }

    "reject mismatched resource provenance before cache publication and permit a later valid retry" must _e3 {
      "when loader evidence has a different source identity and digest than its lifecycle key" in {
        Given("a valid key and immutable resource evidence whose source kind and digest do not match that key")
        val resource = _resource(_source_id, "SourceCode", _digest)
        val mismatched = resource.copy(provenance = resource.provenance.copy(
          sourceKind = ComponentResourceSourceKind.RemoteRepository,
          sha256 = _digest.reverse
        ))
        val key = _key(resource)
        val store = new ComponentResourceLifecycleStore()

        When("the mismatched evidence is loaded and the matching evidence is retried")
        val failed = store.resolveBlocking(key, "component-instance-a", () => mismatched)
        val ready = store.resolveBlocking(key, "component-instance-a", () => resource)

        Then("mismatched evidence is never cached and only the later matching evidence becomes ready")
        failed.outcome shouldBe ComponentResourceLifecycleOutcome.Failed
        failed.resource shouldBe None
        ready.resource shouldBe Some(resource)
        ready.state shouldBe ComponentResourceLifecycleState.Ready
        store.metrics.failureCount shouldBe 1
        store.diagnostics.head.outcome shouldBe ComponentResourceLifecycleOutcome.Failed
      }
    }

    "emit only bounded component, release, role, source, digest, operation, and outcome evidence" must _e3 {
      "when a failing resource produces more diagnostics than the configured bound" in {
        Given("a lifecycle store with a bounded diagnostic budget and an enabled optional CallTreeContext")
        val calltree = CallTreeContext.enabled
        val store = new ComponentResourceLifecycleStore(
          diagnosticLimit = 2,
          callTreeContext = Some(calltree),
          callTreeEventLimit = 3
        )
        val resource = _resource(_source_id, "SourceCode", _digest)
        val key = _key(resource)
        val secret = "source-content:do-not-disclose credential=password secret=phase58-secret"

        When("the same safe identity records repeated failures containing hostile provider text")
        (1 to 5).foreach { _ =>
          val snapshot = store.resolveBlocking(key, "component-instance-a", () => throw new IllegalStateException(secret))
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
        val renderedcalltree = calltree.build().toString
        renderedcalltree should not include secret
        def _count_lifecycle_events_(node: TreeNode[CallTreeNode]): Int =
          node match {
            case directory: TreeDir[CallTreeNode] =>
              directory.children.map(entry => _count_lifecycle_events_(entry.node)).sum
            case leaf: TreeLeaf[CallTreeNode] =>
              if (leaf.value.label == "component-resource-lifecycle") 1 else 0
          }
        val lifecycleevents = calltree.build().map(tree => _count_lifecycle_events_(tree.tree.root)).getOrElse(0)
        lifecycleevents should be <= 3
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
        val failed = store.resolveBlocking(failedkey, "component-instance-a", () => throw new RuntimeException("provider output /Users/asami/private secret=redact"))
        val good = store.resolveBlocking(goodkey, "component-instance-b", () => goodresource)

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

    def loadBlocking(): ResolvedComponentResource = {
      calls.incrementAndGet()
      entered.countDown()
      release.await(5, TimeUnit.SECONDS) shouldBe true
      resource
    }
  }

  private final class BlockingFailureLoader(failure: Throwable) {
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)

    def loadBlocking(): ResolvedComponentResource = {
      entered.countDown()
      release.await(5, TimeUnit.SECONDS) shouldBe true
      throw failure
    }
  }
}
