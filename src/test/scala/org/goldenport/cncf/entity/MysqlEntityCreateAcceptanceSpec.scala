package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.{DirectSession, LiveMysqlContainer, MysqlDatabase, race, storageScalar}
import org.goldenport.record.Record
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import scala.util.control.NonFatal

/*
 * @since   Oct. 14, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class MysqlEntityCreateAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterAll {
  import EntityCreateFailureSafetyFixture.*
  import EntityObservedDeleteCanonicalFixture.{reason, success}

  private var _container: Option[LiveMysqlContainer] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (!sys.env.get("CNCF_LIVE_MYSQL_TEST").contains("true"))
      fail("Required atomic Entity create acceptance needs CNCF_LIVE_MYSQL_TEST=true and actual MySQL 8.4")
    val container = new LiveMysqlContainer()
    try {
      container.start()
      _container = Some(container)
    } catch {
      case NonFatal(error) => container.stop(); throw error
    }
  }

  override protected def afterAll(): Unit =
    try {
      _container.foreach(_.stop())
      _container = None
    } finally super.afterAll()

  "CA12 positively executing MySQL 8.4 strict Entity create" should {
    "commit canonical generated identities and authoritative initialization" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- BodyMode.values.toVector
      } {
        s"CFS-E1/S5/CA1/CA9/CA11 expose exact $representation $mode INITIAL and hydration to a new pool" in {
          Given("an actual MySQL container, admitted canonical binding and complete older root/body rows")
          _with_mysql { database =>
            val writer = database.open()
            val token = s"mysql_positive_${representation}_$mode"
            val setup = fixture(token, representation = representation, provider = writer)
            setup.seed()
            val before = setup.base.snapshot(database.open())
            val candidate = setup.input(mode, None)
            When("strict create generates the identity, publishes through native JDBC and an independent pool hydrates it")
            val result = setup.createC(candidate)
            val created = success(result)
            val reader = database.open()
            val reopened = fixture(token, representation = representation, provider = reader)
            val after = reopened.snapshot(created.id)
            val hydrated = success(reopened.hydratedC(created.id))
            Then("the returned stored map has exact canonical identity and INITIAL, and complete body/old-row correspondence")
            created.id.collection shouldBe setup.base.collectionId
            created.id.timestamp.nonEmpty shouldBe true
            created.id.entropy.nonEmpty shouldBe true
            created.record.map(_.asMap) shouldBe after.root
            success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
            val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(created.id, candidate.record))
            prepared.record.fields.filter(_.key.startsWith("content")).foreach { field =>
              storageScalar(created.record.get.getAny(field.key).get) shouldBe storageScalar(field.value.single)
            }
            if (mode == BodyMode.Overflow) {
              after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
              hydrated.flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
            } else {
              after.overflow shouldBe None
              hydrated.flatMap(_.record.getString("content")) shouldBe (if (mode == BodyMode.Inline) Some("small candidate") else None)
            }
            after.preexisting shouldBe before
          }
        }
      }
    }

    "preserve all existing rows when native absence admission fails" which {
      for { representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached); mode <- BodyMode.values.toVector } {
        s"CFS-E2/E3/E4/S1/S2 reject the $representation $mode duplicate without body damage" in {
          Given("an actual seeded MySQL root/body pair and a new candidate for its selected existing identity")
          _with_mysql { database =>
            val writer = database.open()
            val setup = fixture(s"mysql_duplicate_${representation}_$mode", representation = representation, provider = writer)
            setup.seed()
            val before = setup.snapshot(setup.base.id, database.open())
            When("canonical strict create attempts the duplicate in the real provider")
            val result = setup.createC(setup.input(mode, Some(setup.base.id)))
            Then("native duplicate taxonomy and the complete durable before snapshot are retained")
            taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
            setup.snapshot(setup.base.id, database.open()) shouldBe before
          }
        }
      }
    }

    "roll back known prepublication failures and distinguish acknowledgement loss" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- Vector(FaultMode.SideSave, FaultMode.RootCreate, FaultMode.BeforePublish, FaultMode.ThrowSide,
          FaultMode.ThrowRoot, FaultMode.ThrowPublish, FaultMode.CommitReject, FaultMode.CommitAckLoss,
          FaultMode.CommitAckThrow, FaultMode.CommitAckNull)
      } {
        s"CFS-E5/E6/S3/S4/CA-PUBLISH/CA-COMMIT expose the actual $representation $mode outcome" in {
          Given("a real MySQL checkpoint writer, complete older rows and an exact fresh root/side target")
          _with_mysql { database =>
            val writer = database.openFault()
            val setup = fixture(s"mysql_failure_${representation}_$mode", representation = representation, provider = writer)
            setup.seed()
            val before = setup.snapshot(setup.freshId, database.open())
            val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
            writer.arm(setup.fault(mode))
            When("one native create reaches side prewrite, root after side, before publish or the real commit boundary")
            val result = setup.createC(candidate)
            val reader = database.open()
            val after = setup.snapshot(setup.freshId, reader)
            Then("known failure preserves all full rows; injected loss after real commit retains a correct pair without success")
            result shouldBe a[Consequence.Failure[?]]
            writer.sideSaves shouldBe 1
            writer.rootCreates shouldBe (if (mode == FaultMode.SideSave || mode == FaultMode.ThrowSide) 0 else 1)
            writer.commitAttempts shouldBe (if (Vector(FaultMode.CommitReject, FaultMode.CommitAckLoss, FaultMode.CommitAckThrow, FaultMode.CommitAckNull).contains(mode)) 1 else 0)
            if (Vector(FaultMode.CommitAckLoss, FaultMode.CommitAckThrow, FaultMode.CommitAckNull).contains(mode)) {
              reason(result) shouldBe Some("transaction-indeterminate")
              val root = success(reader.load(setup.base.dataCollection, DataStore.EntryId(setup.freshId))(using setup.base.context)).get
              success(EntityRevision.createC(root.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
              root.getString("title") shouldBe Some("Candidate")
              after.root shouldBe Some(root.asMap)
              val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidate.record))
              storageScalar(root.getAny("id").get) shouldBe DataStore.EntryId(setup.freshId).print
              prepared.record.fields.filter(_.key.startsWith("content")).foreach(field =>
                storageScalar(root.getAny(field.key).get) shouldBe storageScalar(field.value.single))
              after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
              after.preexisting shouldBe before.preexisting
            } else {
              reason(result) shouldBe Some(if (mode == FaultMode.CommitReject) "transaction-failure" else "provider-failure")
              after shouldBe before
            }
          }
        }
      }
    }

    "prepare first-use schema outside MySQL's data transaction" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"CA8 retain complete row absence after $representation root rejection on new tables" in {
          Given("a positively executing MySQL database with no selected canonical root table")
          _with_mysql { database =>
            val writer = database.openFault()
            val setup = fixture(s"mysql_first_use_$representation", representation = representation, provider = writer)
            val before = setup.snapshot(setup.freshId, database.open())
            writer.arm(setup.fault(FaultMode.RootCreate))
            When("schema is prepared before transaction row work, then BeforeRootWrite rejects after a real side INSERT")
            val result = setup.createC(setup.input(BodyMode.Overflow, Some(setup.freshId)))
            Then("no selected or unrelated partial row is visible to an independent pool")
            reason(result) shouldBe Some("provider-failure")
            writer.sideSaves shouldBe 1
            writer.rootCreates shouldBe 1
            setup.snapshot(setup.freshId, database.open()) shouldBe before
          }
        }
      }
    }

    "resolve native table and primary-key collation ownership" which {
      Vector("Id", "Title", "Åfield").foreach { alias =>
        s"CA-PORT reject native $alias column aliases before schema or row effects" in {
          Given("a real MySQL provider and a first-use record with names equal under native column identifier rules")
          _with_mysql { database =>
            val setup = new DirectSession(s"mysql_column_alias_${alias.length}", database.open())
            val before = setup.snapshot(database.open())
            val added = if (alias == "Åfield") Record.dataAuto("Åfield" -> "a", "åfield" -> "b")
              else Record.dataAuto(alias -> "foreign")
            val plan = setup.plan().copy(record = setup.plan().record ++ added)
            When("strict create validates native column identity before preparing schema")
            val result = setup.createC(plan)
            Then("the malformed native-column plan fails and full independent row absence remains")
            result shouldBe a[Consequence.Failure[?]]
            setup.snapshot(database.open()) shouldBe before
          }
        }
      }

      "CA-PORT reject differently typed CollectionIds naming the same physical root" in {
        Given("a real MySQL root table addressed by Instance and EntityStore collection variants")
        _with_mysql { database =>
          val setup = new DirectSession("mysql_typed_alias", database.open())
          setup.seed()
          val before = setup.snapshot(database.open())
          val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("other", "owner", setup.rootCollection.collectionName))
          val plan = setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(alias, setup.rootEntry)))
          When("strict create admits actual physical addresses")
          val result = setup.createC(plan)
          Then("root alias rejection preserves every independently read full row")
          result shouldBe a[Consequence.Failure[?]]
          setup.snapshot(database.open()) shouldBe before
        }
      }

      Vector("utf8mb4_0900_ai_ci", "utf8mb4_bin", "utf8mb4_0900_as_cs").foreach { collation =>
        s"CA4 use actual $collation id equality without over-rejecting distinct case-sensitive targets" in {
          Given("a real MySQL table declaring the selected native text primary-key collation")
          _with_mysql { database =>
            val setup = new DirectSession(s"mysql_collation_$collation", database.open())
            database.statement(s"CREATE TABLE `${setup.rootCollection.collectionName}` (`id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE $collation PRIMARY KEY)")
            setup.seed()
            val before = setup.snapshot(database.open())
            val sideentry = DataStore.StringEntryId("FRESH")
            val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("other", "owner", setup.rootCollection.collectionName))
            val plan = setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Save(alias, sideentry,
              Record.dataAuto("id" -> sideentry.print, "title" -> "Distinct case target"))))
            When("the provider compares bound entries using catalog charset and native id collation")
            val result = setup.createC(plan)
            val reader = database.open()
            val after = setup.snapshot(reader)
            Then("case-insensitive alias rejects, while distinct case-sensitive ids positively persist without changing older rows")
            if (collation == "utf8mb4_0900_ai_ci") {
              result shouldBe a[Consequence.Failure[?]]
              after shouldBe before
            } else {
              success(result).record.asMap shouldBe after.root.get
              success(reader.load(setup.rootCollection, sideentry)(using setup.context)).get.getString("title") shouldBe Some("Distinct case target")
              after.oldroot shouldBe before.oldroot
              after.oldside shouldBe before.oldside
            }
          }
        }
      }

      "CA4 honor lower_case_table_names and write the same table address used by admission" in {
        Given("an actual MySQL server with its native table-case setting and a differently cased side table request")
        _with_mysql { database =>
          val setup = new DirectSession("mysql_table_case", database.open())
          setup.seed()
          val before = setup.snapshot(database.open())
          val lowercase = database.lowerCaseTableNames
          val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("other", "owner", setup.rootCollection.collectionName.toUpperCase(java.util.Locale.ROOT)))
          val plan = setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Save(alias, setup.rootEntry,
            Record.dataAuto("id" -> setup.rootEntry.print, "title" -> "Separate native table"))))
          When("strict create resolves the existing catalog using the server's actual table equality")
          val result = setup.createC(plan)
          val reader = database.open()
          Then("the operation respects native table ownership rather than an unconditional case fold")
          if (lowercase == 0) {
            success(result).record.asMap shouldBe setup.snapshot(reader).root.get
            success(reader.load(alias, setup.rootEntry)(using setup.context)).get.getString("title") shouldBe Some("Separate native table")
            setup.snapshot(reader).oldroot shouldBe before.oldroot
            setup.snapshot(reader).oldside shouldBe before.oldside
          } else {
            result shouldBe a[Consequence.Failure[?]]
            setup.snapshot(reader) shouldBe before
          }
        }
      }

      "CA-PORT reject repeated side ids equal under the actual MySQL collation" in {
        Given("a native case-insensitive MySQL side primary key and two differently cased side targets")
        _with_mysql { database =>
          val setup = new DirectSession("mysql_repeated_side", database.open())
          database.statement(s"CREATE TABLE `${setup.sideCollection.collectionName}` (`id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci PRIMARY KEY)")
          setup.seed()
          val before = setup.snapshot(database.open())
          val plan = setup.plan().copy(sideEffects = Vector(
            EntityVersionedSideEffect.Delete(setup.sideCollection, DataStore.StringEntryId("FreshBody")),
            EntityVersionedSideEffect.Delete(setup.sideCollection, DataStore.StringEntryId("FRESHBODY"))))
          When("strict create admits repeated native side addresses")
          val result = setup.createC(plan)
          Then("the repeated physical target fails before row effects and complete storage remains")
          result shouldBe a[Consequence.Failure[?]]
          setup.snapshot(database.open()) shouldBe before
        }
      }
    }

    "enforce the unique root boundary across independently owned pools" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"CA-RACE retain one exact acknowledged $representation root/body winner with no caller mutex" in {
          Given("independent real MySQL pools and different overflow candidates for the same fresh canonical identity")
          _with_mysql { database =>
            val first = database.open()
            val second = database.open()
            val token = s"mysql_race_$representation"
            val setup = fixture(token, representation = representation, provider = first)
            val other = fixture(token, representation = representation, provider = second)
            setup.seed()
            val before = setup.base.snapshot(database.open())
            val candidates = Vector(setup.input(BodyMode.Overflow, Some(setup.freshId), "A" * 6000),
              other.input(BodyMode.Overflow, Some(other.freshId), "B" * 7000))
            When("a start barrier releases both canonical creates on separate native connections")
            val results = race(() => setup.createC(candidates(0)), () => other.createC(candidates(1)))
            val winners = results.zipWithIndex.collect { case (Consequence.Success(created), index) => created -> index }
            val after = setup.snapshot(setup.freshId, database.open())
            Then("one acknowledged root/body pair matches its full candidate and the loser cannot alter it or unrelated rows")
            winners.size shouldBe 1
            results.filter(_.isFaillure).foreach(result =>
              (taxonomy(result) == duplicateTaxonomy(setup.freshId) || reason(result).contains("provider-failure")) shouldBe true)
            val (created, index) = winners.head
            created.record.map(_.asMap) shouldBe after.root
            success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
            val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidates(index).record))
            prepared.record.fields.filter(_.key.startsWith("content")).foreach(field =>
              storageScalar(created.record.get.getAny(field.key).get) shouldBe storageScalar(field.value.single))
            after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
            after.preexisting shouldBe before
          }
        }
      }
    }
  }

  private def _with_mysql[A](body: MysqlDatabase => A): A = _container match {
    case Some(container) =>
      val database = new MysqlDatabase(container.getJdbcUrl, container.getUsername, container.getPassword)
      try body(database) finally database.close()
    case None => fail("Real MySQL 8.4 is required; cancellation and provider substitution do not satisfy acceptance")
  }
}
