package org.goldenport.cncf.repository

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._
import org.goldenport.cncf.component.identity.{ComponentId, ComponentReleaseCoordinate}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the direct canonical CAR repository/cache boundary.
 *
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class CanonicalCarRepositoryResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The canonical CAR repository resolver" should {
    "local and file repository resolution" which {
      "resolve filesystem and file URI repositories through the exact shared path" in {
      val work = _work("local")
      try {
        Given("a local repository containing only shared coordinate-relative archives")
        val root = work.resolve("repository")
        val cache = work.resolve("cache")
        val coordinate = _coordinate("org.alpha.textus.Shared", "0.6.0")
        val snapshotcoordinate = _coordinate("org.alpha.textus.Shared", "0.6.1-SNAPSHOT")
        val archive = _write(root.resolve(coordinate.carRepositoryRelativePath()), "local-car")
        val snapshotarchive = _write(root.resolve(snapshotcoordinate.carRepositoryRelativePath()), "local-snapshot-car")

        When("the resolver receives filesystem and file URI roots")
        val filesystem = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(root.toString), cache)
        val fileuri = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(root.toUri.toString), cache)
        val localsnapshot = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.1-SNAPSHOT", Vector(root.toString), cache)

        Then("both local forms and a local SNAPSHOT return their exact archives rather than an artifact-name lookup")
        val filesystemarchive = _success(filesystem)
        val fileuriarchive = _success(fileuri)
        val localsnapshotarchive = _success(localsnapshot)
        Files.isSameFile(filesystemarchive, archive) shouldBe true
        Files.isSameFile(fileuriarchive, archive) shouldBe true
        filesystemarchive.toAbsolutePath.normalize shouldBe archive.toAbsolutePath.normalize
        fileuriarchive.toAbsolutePath.normalize shouldBe archive.toAbsolutePath.normalize
        Files.isSameFile(localsnapshotarchive, snapshotarchive) shouldBe true
        localsnapshotarchive.toAbsolutePath.normalize shouldBe snapshotarchive.toAbsolutePath.normalize
        _bytes(filesystemarchive) shouldBe "local-car"
        _bytes(fileuriarchive) shouldBe "local-car"
        _bytes(localsnapshotarchive) shouldBe "local-snapshot-car"
      } finally _delete_tree(work)
      }
    }

    "HTTP retrieval and cache publication" which {
      "resolve namespace-isolated same-filename remote archives and reuse their exact caches offline" in {
      val work = _work("remote")
      try {
        Given("two loopback paths for namespace-distinct coordinates with one shared filename")
        val cache = work.resolve("cache")
        val alpha = _coordinate("org.alpha.textus.Shared", "0.6.0")
        val beta = _coordinate("org.beta.textus.Shared", "0.6.0")
        _with_http_fixture(Map(
          s"/${alpha.carRepositoryRelativePath()}" -> "alpha-car".getBytes(StandardCharsets.UTF_8),
          s"/${beta.carRepositoryRelativePath()}" -> "beta-car".getBytes(StandardCharsets.UTF_8)
        )) { fixture =>
          When("both qualified coordinates resolve from the remote repository")
          val base = s"http://127.0.0.1:${fixture.server.getAddress.getPort}"
          val first = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(base), cache)
          val second = CanonicalCarRepositoryResolver.resolve("org.beta.textus.Shared", "0.6.0", Vector(base), cache)
          val remotesnapshot = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.1-SNAPSHOT", Vector(base), cache)
          fixture.server.stop(0)
          val offlinefirst = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(base), cache)
          val offlinesecond = CanonicalCarRepositoryResolver.resolve("org.beta.textus.Shared", "0.6.0", Vector(base), cache)

          Then("request paths, cache paths, archive bytes, and SNAPSHOT policy remain namespace isolated and cache-first")
          alpha.carFilename() shouldBe beta.carFilename()
          alpha.carRepositoryRelativePath() should not be beta.carRepositoryRelativePath()
          alpha.carCacheRelativePath() should not be beta.carCacheRelativePath()
          _bytes(_success(first)) shouldBe "alpha-car"
          _bytes(_success(second)) shouldBe "beta-car"
          _failure(remotesnapshot) should include("dependency=org.alpha.textus.Shared:0.6.1-SNAPSHOT")
          _bytes(_success(offlinefirst)) shouldBe "alpha-car"
          _bytes(_success(offlinesecond)) shouldBe "beta-car"
          fixture.requestpaths.asScala.toVector shouldBe Vector(s"/${alpha.carRepositoryRelativePath()}", s"/${beta.carRepositoryRelativePath()}")
          fixture.requestpaths.size() shouldBe 2
        }
      } finally _delete_tree(work)
      }

      "retain a concurrent winner without replacing it while publishing a completed download" in {
        val work = _work("publication-race")
        try {
          Given("an in-flight loopback archive download and a cache destination owned by a concurrent writer")
          val cache = work.resolve("cache")
          val coordinate = _coordinate("org.alpha.textus.Shared", "0.6.0")
          val destination = cache.resolve(coordinate.carCacheRelativePath())
          _with_blocking_http_fixture(s"/${coordinate.carRepositoryRelativePath()}", "downloaded-car".getBytes(StandardCharsets.UTF_8)) { fixture =>
            val base = s"http://127.0.0.1:${fixture.server.getAddress.getPort}"
            val result = new AtomicReference[Either[String, Path]]()
            val resolverthread = new Thread(
              new Runnable {
                override def run(): Unit = result.set(
                  CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(base), cache)
                )
              },
              "canonical-car-repository-publication-race"
            )
            try {
              When("the concurrent writer creates the final cache archive before HTTP bytes are released")
              resolverthread.start()
              fixture.started.await(5, TimeUnit.SECONDS) shouldBe true
              _write(destination, "winning-car")
              fixture.release.countDown()
              resolverthread.join(5000)

              Then("the resolver returns the existing winner unchanged and removes its sibling temporary archive")
              resolverthread.isAlive shouldBe false
              result.get() should not be null
              val resolved = _success(result.get())
              resolved shouldBe destination
              _bytes(resolved) shouldBe "winning-car"
              _bytes(destination) shouldBe "winning-car"
              _temporary_files(destination.getParent) shouldBe Vector.empty
              fixture.requestpaths.asScala.toVector shouldBe Vector(s"/${coordinate.carRepositoryRelativePath()}")
              fixture.requestpaths.size() shouldBe 1
            } finally {
              fixture.release.countDown()
              resolverthread.join(5000)
            }
          }
        } finally _delete_tree(work)
      }
    }

    "repository boundaries and failed-download cleanup" which {
      "preserve shared identity diagnostics and reject invalid repository boundaries without throwing" in {
      val work = _work("invalid")
      try {
        Given("bare null and malformed direct inputs plus invalid repository settings")
        val cache = work.resolve("cache")

        When("the direct boundary is asked to resolve them")
        val bare = CanonicalCarRepositoryResolver.resolve("Shared", "0.6.0", Vector.empty, cache)
        val nullid = CanonicalCarRepositoryResolver.resolve(null, "0.6.0", Vector.empty, cache)
        val nullrelease = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", null, Vector.empty, cache)
        val blankrepository = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(" "), cache)
        val absentport = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.1-SNAPSHOT", Vector("http://127.0.0.1"), cache)
        val zeroport = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.1-SNAPSHOT", Vector("http://127.0.0.1:0"), cache)
        val invalidport = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector("http://127.0.0.1:99999"), cache)
        val unsupportedscheme = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector("ftp://127.0.0.1:21"), cache)
        val nullrepositories = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", null, cache)
        val nullcache = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector.empty, null)

        Then("identity failures retain the ABI code and repository failures retain stable boundary diagnostics")
        _failure(bare) should include("component.identity.id.qualified")
        _failure(nullid) should include("component.identity.id.required")
        _failure(nullrelease) should include("component.identity.release.required")
        _failure(blankrepository) should include("component.repository.repository.blank")
        _failure(absentport) should include("component.repository.car.not-found")
        _failure(zeroport) should include("component.repository.car.not-found")
        _failure(invalidport) should include("component.repository.repository.invalid")
        _failure(unsupportedscheme) should include("component.repository.repository.invalid")
        _failure(nullrepositories) should include("component.repository.repositories.required")
        _failure(nullcache) should include("component.repository.cache-root.required")
      } finally _delete_tree(work)
      }

      "fall through repositories in order and leave no archive or temporary debris after failed downloads" in {
      val work = _work("fallthrough")
      try {
        Given("an empty first local root, a second local archive, and a failing loopback source")
        val firstroot = work.resolve("first-repository")
        val secondroot = work.resolve("second-repository")
        val cache = work.resolve("cache")
        val coordinate = _coordinate("org.alpha.textus.Shared", "0.6.0")
        val archive = _write(secondroot.resolve(coordinate.carRepositoryRelativePath()), "second-car")
        _with_http_fixture(Map.empty) { fixture =>
          val base = s"http://127.0.0.1:${fixture.server.getAddress.getPort}"

          When("the resolver falls through local roots and then encounters a remote failure")
          val resolved = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(firstroot.toString, secondroot.toString), cache)
          val missing = CanonicalCarRepositoryResolver.resolve("org.alpha.textus.Shared", "0.6.0", Vector(base), cache)
          val destination = cache.resolve(coordinate.carCacheRelativePath())

          Then("the ordered local candidate wins and failed HTTP retrieval leaves no final or sibling temporary file")
          _success(resolved) shouldBe archive
          _failure(missing) should include(s"dependency=${coordinate.dependencyKey()}")
          Files.exists(destination) shouldBe false
          _temporary_files(destination.getParent) shouldBe Vector.empty
          fixture.requestpaths.asScala.toVector shouldBe Vector(s"/${coordinate.carRepositoryRelativePath()}")
          fixture.requestpaths.size() shouldBe 1
        }
      } finally _delete_tree(work)
      }
    }

    "canonical coordinate projection properties" which {
      "retain namespace isolation for bounded generated shared release coordinates" in {
      Given("namespace segments ending in one artifact-bearing segment and a fixed local id/release")
      val segment = Gen.oneOf("alpha", "beta", "gamma", "delta")
      val property = Prop.forAll(segment, segment) { (firstvalue, secondvalue) =>
        val first = _coordinate(s"org.$firstvalue.textus.Shared", "0.6.0")
        val second = _coordinate(s"org.$secondvalue.textus.Shared", "0.6.0")
        if (firstvalue == secondvalue)
          first.dependencyKey() == second.dependencyKey() && first.carCacheRelativePath() == second.carCacheRelativePath()
        else
          first.carFilename() == second.carFilename() &&
            first.dependencyKey() != second.dependencyKey() &&
            first.carRepositoryRelativePath() != second.carRepositoryRelativePath() &&
            first.carCacheRelativePath() != second.carCacheRelativePath()
      }

      When("the shared coordinate projection is sampled")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("same filenames never collapse qualified dependency or cache identity")
      checked.passed shouldBe true
      }
    }
  }

  private val _work_root = Path.of("target", "cncf-test", "work", "canonical-car-repository-resolver-spec")

  private def _work(name: String): Path = {
    Files.createDirectories(_work_root)
    Files.createTempDirectory(_work_root, s"$name-")
  }

  private def _coordinate(qualifiedid: String, release: String): ComponentReleaseCoordinate =
    ComponentReleaseCoordinate.require(ComponentId.require(qualifiedid), release)

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _bytes(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_http_fixture[A](routes: Map[String, Array[Byte]])(f: HttpFixture => A): A = {
    val fixture = _http_fixture(routes)
    try f(fixture)
    finally fixture.server.stop(0)
  }

  private def _http_fixture(routes: Map[String, Array[Byte]]): HttpFixture = {
    val requestpaths = new ConcurrentLinkedQueue[String]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        requestpaths.add(exchange.getRequestURI.getPath)
        routes.get(exchange.getRequestURI.getPath) match {
          case Some(bytes) =>
            exchange.sendResponseHeaders(200, bytes.length.toLong)
            val output = exchange.getResponseBody
            try output.write(bytes)
            finally output.close()
          case None =>
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
      }
    })
    server.start()
    HttpFixture(server, requestpaths)
  }

  private def _with_blocking_http_fixture[A](route: String, bytes: Array[Byte])(f: BlockingHttpFixture => A): A = {
    val fixture = _blocking_http_fixture(route, bytes)
    try f(fixture)
    finally {
      fixture.release.countDown()
      fixture.server.stop(0)
    }
  }

  private def _blocking_http_fixture(route: String, bytes: Array[Byte]): BlockingHttpFixture = {
    val requestpaths = new ConcurrentLinkedQueue[String]()
    val started = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val requestpath = exchange.getRequestURI.getPath
        requestpaths.add(requestpath)
        if (requestpath == route) {
          started.countDown()
          try {
            if (release.await(5, TimeUnit.SECONDS)) {
              exchange.sendResponseHeaders(200, bytes.length.toLong)
              val output = exchange.getResponseBody
              try output.write(bytes)
              finally output.close()
            } else exchange.close()
          } catch {
            case _: InterruptedException =>
              Thread.currentThread.interrupt()
              exchange.close()
          }
        } else {
          exchange.sendResponseHeaders(404, -1)
          exchange.close()
        }
      }
    })
    server.start()
    BlockingHttpFixture(server, requestpaths, started, release)
  }

  private def _temporary_files(parent: Path): Vector[Path] =
    if (Files.exists(parent)) {
      val stream = Files.list(parent)
      try stream.iterator().asScala.toVector.filter(_.getFileName.toString.endsWith(".tmp"))
      finally stream.close()
    } else Vector.empty

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(Comparator.reverseOrder()).forEach(value => Files.deleteIfExists(value))
      finally stream.close()
    }

  private def _success(result: Either[String, Path]): Path =
    result.fold(message => fail(message), identity)

  private def _failure(result: Either[String, Path]): String =
    result.fold(identity, _ => fail("expected canonical resolver failure"))

  private final case class HttpFixture(server: HttpServer, requestpaths: ConcurrentLinkedQueue[String])
  private final case class BlockingHttpFixture(
    server: HttpServer,
    requestpaths: ConcurrentLinkedQueue[String],
    started: CountDownLatch,
    release: CountDownLatch
  )
}
