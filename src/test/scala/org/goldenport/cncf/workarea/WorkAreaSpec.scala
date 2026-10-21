package org.goldenport.cncf.workarea

import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDate
import java.util.Comparator
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.SystemNode
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class WorkAreaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "WorkArea ownership" should {
    "release scoped paths after success, failure, and a thrown callback" in {
      Given("independent callback scopes under a caller-owned directory")
      _with_base { base =>
        val space = _space(base)
        val sentinel = Files.writeString(base.resolve("caller-owned"), "keep")

        When("callbacks complete normally, fail, or throw after allocating a directory")
        val outcomes = Vector("success", "failure", "throw").map { outcome =>
          var allocated: Option[Path] = None
          val result = space.execute { handle =>
            handle.createTempDir("callback-").flatMap { path =>
              allocated = Some(path)
              Files.writeString(path.resolve("payload"), "owned")
              outcome match {
                case "success" => Consequence.success("done")
                case "failure" => Consequence.resourceInvalid("callback failed")
                case _ => throw new IllegalArgumentException("callback threw")
              }
            }
          }
          (outcome, result, allocated.get)
        }

        Then("each scope disappears while the original result and caller-owned sentinel remain")
        outcomes.foreach { case (outcome, result, path) =>
          result.toOption shouldBe (if (outcome == "success") Some("done") else None)
          Files.exists(path.getParent) shouldBe false
        }
        Files.readString(sentinel) shouldBe "keep"
        space.close()
        Files.isDirectory(base) shouldBe true
      }
    }

    "release an interrupted callback and rethrow the original interruption" in {
      Given("a callback with an allocated file and an interruption")
      _with_base { base =>
        val space = _space(base)
        var allocated: Option[Path] = None
        val interruption = new InterruptedException("callback interrupted")

        When("the callback is interrupted")
        val thrown = the [InterruptedException] thrownBy space.execute[Unit] { handle =>
          allocated = Some(handle.createTempFile("interrupted-").TAKE)
          throw interruption
        }

        Then("cleanup runs before the same interruption escapes")
        (thrown eq interruption) shouldBe true
        Files.exists(allocated.get.getParent) shouldBe false
        space.close()
      }
    }

    "keep nested partitions and independent owners isolated across generated sibling counts" in {
      val property = Prop.forAll(Gen.choose(2, 8)) { count =>
        _with_base { base =>
          Given(s"separate owners sharing a caller-owned base with $count partitions")
          val first = _space(base)
          val second = _space(base)
          val handles = Vector.fill(count)(first.allocate().TAKE)
          val paths = handles.map(_.createTempFile("sibling-").TAKE)
          val nested = handles.head.areas.allocate().TAKE
          val nestedpath = nested.createTempDir("nested-").TAKE
          val foreign = second.createTempFile("independent-").TAKE

          When("the first partition closes")
          handles.head.close()

          Then("its nested scope disappears while siblings and the independent owner remain")
          val isolated = !Files.exists(paths.head) && !Files.exists(nestedpath) &&
            paths.tail.forall(path => Files.exists(path)) && Files.exists(foreign)

          When("the first parent closes")
          first.close()

          Then("its remaining siblings disappear while the independent owner remains")
          val parentclosed = paths.forall(path => !Files.exists(path)) && Files.exists(foreign)

          When("the independent owner closes")
          second.close()

          Then("all owned paths disappear while the caller-owned base remains")
          isolated && parentclosed && !Files.exists(foreign) && Files.isDirectory(base)
        }
      }

      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
      checked.passed shouldBe true
    }

    "retain inaccessible owned paths for retry and continue cleaning accessible siblings" in {
      _with_base { base =>
        Given("an owned child file and an accessible sibling on a POSIX filesystem")
        assume(Files.getFileStore(base).supportsFileAttributeView("posix"))
        val space = _space(base)
        val blocked = space.allocate().TAKE
        val blockedpath = blocked.createTempFile("inaccessible-").TAKE
        val blockedroot = blockedpath.getParent
        val sibling = space.allocate().TAKE
        val siblingpath = sibling.createTempFile("accessible-").TAKE
        val originalpermissions = Files.getPosixFilePermissions(blockedroot)
        val searchpermissions = Set(
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_EXECUTE
        )
        val blockedpermissions = originalpermissions.asScala.toSet.diff(searchpermissions).asJava
        try {
          When("the child loses directory search permission before parent disposal")
          Files.setPosixFilePermissions(blockedroot, blockedpermissions)
          val failed = space.dispose()

          Then("disposal reports the inaccessible child and still removes its accessible sibling")
          failed.toOption shouldBe None
          Files.exists(siblingpath.getParent) shouldBe false

          When("the exact original directory permissions are restored")
          Files.setPosixFilePermissions(blockedroot, originalpermissions)

          Then("the inaccessible file is still available for the pending cleanup")
          Files.exists(blockedpath) shouldBe true

          When("disposal retries after permission restoration")
          val retried = space.dispose()

          Then("the retained file and child root are removed successfully")
          retried.toOption shouldBe Some(())
          Files.exists(blockedpath) shouldBe false
          Files.exists(blockedroot) shouldBe false
          Files.isDirectory(base) shouldBe true
        } finally {
          if (Files.exists(blockedroot, LinkOption.NOFOLLOW_LINKS))
            Files.setPosixFilePermissions(blockedroot, originalpermissions)
          space.close()
        }
      }
    }

    "allow concurrent callbacks to close without deleting a live sibling" in {
      Given("two concurrent callbacks using one parent WorkArea")
      _with_base { base =>
        val space = _space(base)
        val ready = new CountDownLatch(2)
        val closed = new CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
          When("one callback finishes while its sibling remains active")
          val first = executor.submit(new Callable[Path] {
            def call(): Path = {
              val path = space.execute { handle =>
                val allocated = handle.createTempFile("concurrent-first-").TAKE
                ready.countDown()
                if (!ready.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("sibling did not allocate")
                Consequence.success(allocated)
              }.TAKE
              closed.countDown()
              path
            }
          })
          val second = executor.submit(new Callable[(Path, Boolean)] {
            def call(): (Path, Boolean) = space.execute { handle =>
              val path = handle.createTempFile("concurrent-second-").TAKE
              ready.countDown()
              if (!closed.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("sibling did not close")
              Consequence.success(path -> Files.exists(path))
            }.TAKE
          })
          val firstpath = first.get(15, TimeUnit.SECONDS)
          val (secondpath, survived) = second.get(15, TimeUnit.SECONDS)

          Then("the active sibling survives and both completed scopes are removed")
          survived shouldBe true
          Files.exists(firstpath.getParent) shouldBe false
          Files.exists(secondpath.getParent) shouldBe false
        } finally {
          executor.shutdownNow()
          space.close()
        }
      }
    }

    "close idempotently and reject allocations and partitions after closure" in {
      Given("an owner with a still-open child")
      _with_base { base =>
        val space = _space(base)
        val child = space.allocate().TAKE
        val path = child.createTempFile("owned-").TAKE

        When("the parent is closed repeatedly")
        space.close()
        space.close()
        child.close()

        Then("the child is reclaimed and no owner can allocate again")
        Files.exists(path.getParent) shouldBe false
        space.allocate().toOption shouldBe None
        space.createTempDir("late-").toOption shouldBe None
        child.createTempFile("late-").toOption shouldBe None
        an [IllegalStateException] should be thrownBy space.areas.file.partition()
      }
    }

    "remove owned symbolic links without following them to outside sentinels" in {
      Given("an owned directory containing a link and an owned root replaced by a link")
      _with_base { base =>
        val outside = Files.createDirectory(base.resolve("outside"))
        val sentinel = Files.writeString(outside.resolve("sentinel"), "keep")
        val space = _space(base)
        val directory = space.createTempDir("links-").TAKE
        Files.createSymbolicLink(directory.resolve("outside"), outside)
        val replaced = space.createTempDir("replaced-").TAKE
        Files.delete(replaced)
        Files.createSymbolicLink(replaced, outside)

        When("the owner disposes both allocations")
        space.dispose().toOption shouldBe Some(())

        Then("only the owned paths disappear")
        Files.exists(directory, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(replaced, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readString(sentinel) shouldBe "keep"
      }
    }

    "place independent factory roots beneath the current YYYY/MM bucket" in {
      Given("two factory owners without changing the JVM temporary directory")
      val before = LocalDate.now()
      val first = WorkAreaSpace.create(RuntimeConfig.default)
      val second = WorkAreaSpace.create(RuntimeConfig.default)
      try {
        val firstpath = first.createTempFile("first-").TAKE
        val secondpath = second.createTempFile("second-").TAKE
        val firstroot = firstpath.getParent
        val secondroot = secondpath.getParent
        val after = LocalDate.now()
        val buckets = Set(before, after).map { date =>
          Path.of(System.getProperty("java.io.tmpdir", "tmp"), "cncf", f"${date.getYear}%04d", f"${date.getMonthValue}%02d")
        }

        When("the first owner closes")
        first.close()

        Then("its unique root disappears while its month bucket and second owner remain")
        buckets should contain(firstroot.getParent)
        firstroot.getFileName.toString should startWith("workarea-")
        firstroot should not be secondroot
        Files.exists(firstroot) shouldBe false
        Files.isDirectory(firstroot.getParent) shouldBe true
        Files.exists(secondpath) shouldBe true
      } finally {
        first.close()
        second.close()
      }
    }

    "report cleanup failure, finish other allocations, and retry remaining cleanup" in {
      Given("a factory root blocked by an unowned entry")
      val space = WorkAreaSpace.create(RuntimeConfig.default)
      val allocated = space.createTempFile("managed-").TAKE
      val root = allocated.getParent
      val blocker = Files.writeString(root.resolve("unowned"), "keep")
      try {
        When("cleanup cannot delete the nonempty owned base")
        val result = space.dispose()

        Then("failure is visible while owned allocations are already removed")
        result.toOption shouldBe None
        Files.exists(allocated) shouldBe false
        Files.readString(blocker) shouldBe "keep"
        an [Exception] should be thrownBy space.close()

        When("the caller removes its blocking entry and retries disposal")
        Files.delete(blocker)
        val retried = space.dispose()

        Then("the pending base cleanup succeeds without reopening allocation")
        retried.toOption shouldBe Some(())
        Files.exists(root) shouldBe false
        space.allocate().toOption shouldBe None
      } finally {
        Files.deleteIfExists(blocker)
        space.close()
      }
    }
  }

  "Retained WorkArea execution" should {
    "keep a successful scope alive until its registered runtime owner closes" in {
      Given("a runtime owner and an independent sibling scope")
      _with_base { base =>
        val space = _space(base)
        val node = SystemNode.create()
        val sibling = space.createTempFile("sibling-").TAKE
        try {
          When("a successful callback registers its allocated scope with the runtime")
          val path = space.executeRetained(node.ownRuntimeResourceC) { handle =>
            handle.createTempFile("retained-")
          }.TAKE

          Then("the returned path remains usable until runtime shutdown")
          Files.writeString(path, "still live")
          Files.readString(path) shouldBe "still live"
          node.shutdownC().toOption shouldBe Some(())
          Files.exists(path.getParent) shouldBe false
          Files.exists(sibling) shouldBe true
        } finally {
          node.shutdownC()
          space.close()
        }
      }
    }

    "release failed, throwing, interrupted, and rejected registrations while preserving siblings" in {
      Given("a sibling and callback or registration failures")
      _with_base { base =>
        val space = _space(base)
        val sibling = space.createTempFile("sibling-").TAKE
        val rejection = Consequence.resourceInvalid[Unit]("owner rejected registration")
        var registrations = 0
        val interruption = new InterruptedException("retained callback interrupted")

        When("retained callbacks fail before registration or their owner rejects them")
        val results = Vector("failure", "throw", "interrupt", "reject", "register-interrupt").map { mode =>
          var path: Option[Path] = None
          val register: AutoCloseable => Consequence[Unit] = _ => {
            registrations += 1
            if (mode == "register-interrupt") throw interruption
            rejection
          }
          val result = if (mode == "interrupt" || mode == "register-interrupt") {
            val thrown = the [InterruptedException] thrownBy space.executeRetained[Unit](register) { handle =>
              path = Some(handle.createTempDir("retained-failed-").TAKE)
              if (mode == "interrupt") throw interruption
              Consequence.success(())
            }
            (thrown eq interruption) shouldBe true
            None
          } else {
            space.executeRetained[Unit](register) { handle =>
              path = Some(handle.createTempDir("retained-failed-").TAKE)
              mode match {
                case "failure" => Consequence.resourceInvalid("callback failed")
                case "throw" => throw new IllegalArgumentException("callback threw")
                case _ => Consequence.success(())
              }
            }.toOption
          }
          (mode, result, path.get)
        }

        Then("every failed scope is removed and registration occurs only after successful callbacks")
        results.foreach { case (_, result, path) =>
          result shouldBe None
          Files.exists(path.getParent) shouldBe false
        }
        registrations shouldBe 2
        Files.exists(sibling) shouldBe true
        val rejected = space.executeRetained[Unit](_ => rejection)(_ => Consequence.success(()))
        rejected shouldBe rejection
        space.close()
      }
    }

    "reclaim a retained child when the parent closes first" in {
      Given("a retained child registered with another owner")
      _with_base { base =>
        val space = _space(base)
        var retained: Option[AutoCloseable] = None
        val path = space.executeRetained { resource =>
          retained = Some(resource)
          Consequence.success(())
        }(_.createTempFile("retained-child-")).TAKE

        When("the parent closes before that owner")
        space.close()
        retained.get.close()

        Then("the retained scope is reclaimed and its later close stays idempotent")
        Files.exists(path.getParent) shouldBe false
      }
    }
  }

  private def _space(base: Path): WorkAreaSpace =
    WorkAreaSpace(WorkAreaGroup(new FileWorkArea(WorkAreaId("spec"), WorkArea.Limit(None, None, None), base)))

  private def _with_base[T](body: Path => T): T = {
    val base = Files.createTempDirectory("workarea-spec-")
    try body(base)
    finally {
      Using.resource(Files.walk(base)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
}
