package org.goldenport.cncf.datastore

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.zaxxer.hikari.HikariDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStorePoolResourceAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp06, example:$example, rules:DSP06-R5-R7, phase:54, slice:DSP-06")

  "SystemNode managed SQLite resource acceptance" when {
    "two resident bindings resolve one component datastore" which {
      "E1 use one physical resource for real JDBC work and close it only at node finalization" must _metadata("E1") {
        "retain the pool across binding release" in {
          Given("one canonical Control Center-style application SQLite target and two node bindings")
          val database = Files.createTempDirectory("cncf-dsp06-resource").resolve("registry.sqlite")
          val environment = ComponentDataStore.Environment(
            ResolvedParameters.empty(),
            Some(_configuration(
              "textus.component.control-center.datastores.application.sqlite.path" -> database.toString
            ))
          )
          val request = ComponentDataStore.Request("control-center")
          val baselineHousekeepers = _hikari_housekeepers
          val node = SystemNode.create()
          val firstbinding = node.bind()
          val secondbinding = node.bind()
          val firstlease = firstbinding.acquireLeaseC().toOption.get
          val secondlease = secondbinding.acquireLeaseC().toOption.get
          val first = ComponentDataStore.resolveManagedForDataStoreSpaceC(
            environment, request, firstbinding, firstlease, node.hmacKey
          ).toOption.get.get.asInstanceOf[SqlDataStore]
          val resource = first.managedResourceOption.get

          When("both bindings repeatedly use the managed SqlDataStore borrow path")
          val second = ComponentDataStore.resolveManagedForDataStoreSpaceC(
            environment, request, secondbinding, secondlease, node.hmacKey
          ).toOption.get.get.asInstanceOf[SqlDataStore]
          val collection = DataStore.CollectionId("phase54_resource_acceptance")
          (1 to 64).foreach { index =>
            val store = if (index % 2 == 0) first else second
            store.count(collection, QueryDirective(Query.Empty)).toOption shouldBe Some(0)
          }
          val duringHousekeepers = _hikari_housekeepers
          val ownedHousekeepers = duringHousekeepers.filterNot(baselineHousekeepers.contains)
          val opened = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val heldResult = new AtomicReference[Throwable]()
          val holdingBorrow = new Thread(new Runnable {
            def run(): Unit =
              try {
                resource.borrowC { datasource =>
                  val connection = datasource.getConnection()
                  try {
                    opened.countDown()
                    release.await()
                    Consequence.unit
                  } finally {
                    connection.close()
                  }
                }
              } catch {
                case e: Throwable => heldResult.set(e)
              }
          })
          holdingBorrow.start()
          opened.await(5000L, TimeUnit.MILLISECONDS) shouldBe true
          release.countDown()
          holdingBorrow.join(5000L)
          Option(heldResult.get()) shouldBe None
          first.closeC()
          second.closeC()
          firstlease.release()
          secondlease.release()
          firstbinding.release()
          secondbinding.release()
          val beforefinalization = resource.isOpen
          val entriesbeforefinalization = node.managedDataStoreCount
          val pool = resource.datasource.asInstanceOf[HikariDataSource]
          val openSqliteConnections = pool.getHikariPoolMXBean.getTotalConnections
          val result = node.shutdownC()
          ownedHousekeepers.foreach(_.join(5000L))
          val afterHousekeepers = _hikari_housekeepers

          Then("one node-owned pool bounds its SQLite connections and returns its threads and descriptors at final close")
          node.bindingCount shouldBe 0
          entriesbeforefinalization shouldBe 1
          duringHousekeepers.size shouldBe baselineHousekeepers.size + 1
          openSqliteConnections shouldBe 1
          node.managedDataStoreCount shouldBe 0
          beforefinalization shouldBe true
          result.toOption shouldBe Some(())
          resource.isOpen shouldBe false
          pool.isClosed shouldBe true
          afterHousekeepers shouldBe baselineHousekeepers
        }
      }
    }
  }

  private def _configuration(values: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap),
      ConfigurationTrace.empty
    )

  private def _hikari_housekeepers: Set[Thread] =
    Thread.getAllStackTraces.keySet().toArray.collect {
      case thread: Thread if thread.getName.matches("HikariPool-[0-9]+ housekeeper") => thread
    }.toSet

}
