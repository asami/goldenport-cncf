package org.goldenport.cncf.datastore

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStore, SqliteDialectDriver}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.{Await, ExecutionContext as ScalaExecutionContext, Future}
import scala.concurrent.duration.DurationInt

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class SqlDataStoreLifecycleSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ConsequenceMatchers {
  private given ScalaExecutionContext = ScalaExecutionContext.global

  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp02, example:$example, rules:DSP02-R1-R7, phase:54, slice:DSP-02")

  "Managed SQL datastore lifecycle" when {
    "E1 close a factory-owned SQLite datasource explicitly" must _metadata("E1") {
      "close the physical datasource and reject later borrows" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R1-R7; Example: E1")
        val path = Files.createTempFile("cncf-dsp02-lifecycle", ".db").toString
        val datastore = SqlDataStore.sqlite(path)
        given ExecutionContext = ExecutionContext.create()

        When("the caller closes its factory-created datastore")
        val closed = datastore.closeC()
        val later = datastore.load(DataStore.CollectionId("after_close"), DataStore.StringEntryId("one"))

        Then("close succeeds and later managed borrowing has structured failure")
        closed should be_success
        later.toOption shouldBe None
      }
    }

    "E2 linearize concurrent close callers and execute physical close once" must _metadata("E2") {
      "cache one terminal close result" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R1-R7; Example: E2")
        val closes = new AtomicInteger(0)
        val resource = ManagedSqlDataStoreResource.hikari(null, () => closes.incrementAndGet())
        val start = new CountDownLatch(1)

        When("eight callers are released together")
        val results = Vector.fill(8)(Future {
          start.await()
          resource.closeC()
        })
        start.countDown()
        val completed = Await.result(Future.sequence(results), 10.seconds)

        Then("each caller receives the one cached success and physical close occurs once")
        closes.get shouldBe 1
        completed.foreach(_ should be_success)
      }
    }

    "E3 retain a structured close failure as terminal" must _metadata("E3") {
      "not retry a failed physical close" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R1-R7; Example: E3")
        val closes = new AtomicInteger(0)
        val resource = ManagedSqlDataStoreResource.hikari(null, () => {
          closes.incrementAndGet()
          throw new IllegalStateException("expected close failure")
        })

        When("the caller repeats a failed close")
        val first = resource.closeC()
        val second = resource.closeC()

        Then("the same failure is retained without another physical close")
        first.toOption shouldBe None
        second.toOption shouldBe None
        closes.get shouldBe 1
      }
    }

    "E4 close an acquired but unpublished resource on construction failure" must _metadata("E4") {
      "not leak a resource when wrapping fails" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R1-R7; Example: E4")
        val closes = new AtomicInteger(0)
        val resource = ManagedSqlDataStoreResource.hikari(null, () => closes.incrementAndGet())

        When("the post-allocation construction seam throws")
        val thrown = intercept[IllegalStateException] {
          ManagedSqlDataStoreResource.closeUnpublished(resource) {
            throw new IllegalStateException("expected construction failure")
          }
        }

        Then("the resource is closed before the failure escapes")
        thrown.getMessage shouldBe "expected construction failure"
        closes.get shouldBe 1
      }
    }

    "E5 preserve injected datasource ownership and require explicit transfer" must _metadata("E5") {
      "distinguish constructor injection from ownership transfer" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R1-R7; Example: E5")
        val injected = _datasource()
        val transferred = _datasource()
        val injectedstore = new SqlDataStore(SqliteDialectDriver, injected)
        val ownedstore = SqlDataStore.owning(SqliteDialectDriver, transferred, () => transferred.close())

        When("each store receives closeC")
        val injectedresult = injectedstore.closeC()
        val transferredresult = ownedstore.closeC()

        Then("only the explicit ownership transfer closes its datasource")
        injectedresult should be_success
        transferredresult should be_success
        injected.isClosed shouldBe false
        transferred.isClosed shouldBe true
        injected.close()
      }
    }
  }

  private def _datasource(): HikariDataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl("jdbc:sqlite::memory:")
    config.setDriverClassName("org.sqlite.JDBC")
    new HikariDataSource(config)
  }
}
