package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import java.sql.Connection
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{
  SqlDataStore,
  SqliteDialectDriver
}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class SqliteConditionalTransitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import SqliteConditionalTransitionSpec.*

  private val _provider_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, examples:E5,E6,E8-E11,E17, rules:R8-R13,R22, phase:49"
    )

  "SQLite conditional transition" should {
    "publish create and bind transitions through one native transaction" must
      _provider_metadata {
        "when the root guard and successor revision match" in {
          Given(
            "independent SQLite databases containing one root and either a create or bind successor intent"
          )
          val createpath = _database_path("create")
          val createstore = SqlDataStore.sqlite(createpath.toString)
          given ExecutionContext = _context(createstore)
          _seed_root(createstore) shouldBe Consequence.unit

          val bindpath = _database_path("bind")
          val bindstore = _store(bindpath)
          val bindcontext = _context(bindstore)
          val bindsetup = {
            given ExecutionContext = bindcontext
            _seed_root(bindstore).flatMap(_ =>
              bindstore.create(
                _successor_collection,
                _successor_entry,
                _successor_record(_successor_entry, 3L)
              )
            )
          }

          When("each provider executes the compound operation")
          val create =
            createstore.conditionalTransition(
              _create_plan(_successor_entry, _side_entry)
            )
          val bind = {
            given ExecutionContext = bindcontext
            bindsetup.flatMap(_ =>
              bindstore.conditionalTransition(
                _bind_plan(_successor_entry, 3L)
              )
            )
          }

          val created = _transitioned(create)
          val bound = _transitioned(bind)

          Then("both results expose authoritative root and successor records")
          created.flatMap(_.rootRecord.getInt(_revision_field)) shouldBe
            Some(2)
          created.flatMap(_.rootRecord.getString("successor_id")) shouldBe
            Some(_successor_entry.print)
          created.flatMap(_.successorRecord.getInt(_revision_field)) shouldBe
            Some(1)
          bound.flatMap(_.rootRecord.getInt(_revision_field)) shouldBe
            Some(2)
          bound.flatMap(_.successorRecord.getInt(_revision_field)) shouldBe
            Some(3)
        }
      }

    "return authoritative NotMatched without candidate publication" must
      _provider_metadata {
        "when one exact root expectation differs" in {
          Given("a SQLite root whose status is already closed")
          val path = _database_path("not-matched")
          val store = _store(path)
          given ExecutionContext = _context(store)
          store.create(
            _root_collection,
            _root_entry,
            _root_record("closed", 1L, None)
          ) shouldBe Consequence.unit

          When("a transition expects the root to remain open")
          val result =
            store.conditionalTransition(
              _create_plan(_successor_entry, _side_entry)
            )

          Then("the root is returned and no successor or side record exists")
          result.toOption.collect {
            case value: DataStoreConditionalTransitionResult.NotMatched =>
              value.existingRootRecord.getString("status")
          }.flatten shouldBe Some("closed")
          store.load(_successor_collection, _successor_entry) shouldBe
            Consequence.success(None)
          store.load(_side_collection, _side_entry) shouldBe
            Consequence.success(None)
        }
      }

    "preserve structured create and bind failures without root mutation" must
      _provider_metadata {
        "when successor ownership collides, is absent, or has a stale revision" in {
          Given("three independent SQLite successor failure scenarios")
          val scenarios: Vector[
            (
              String,
              (SqlDataStore, ExecutionContext) => Consequence[Unit],
              DataStoreConditionalTransitionPlan
            )
          ] = Vector(
            (
              "create collision",
              (store: SqlDataStore, context: ExecutionContext) => {
                given ExecutionContext = context
                store.create(
                  _successor_collection,
                  _successor_entry,
                  _successor_record(_successor_entry, 1L)
                )
              },
              _create_plan(_successor_entry, _side_entry)
            ),
            (
              "missing bind",
              (_: SqlDataStore, _: ExecutionContext) => Consequence.unit,
              _bind_plan(_successor_entry, 1L)
            ),
            (
              "stale bind",
              (store: SqlDataStore, context: ExecutionContext) => {
                given ExecutionContext = context
                store.create(
                  _successor_collection,
                  _successor_entry,
                  _successor_record(_successor_entry, 2L)
                )
              },
              _bind_plan(_successor_entry, 1L)
            )
          )

          When("each intent is checked inside the provider transaction")
          val outcomes = scenarios.map { case (name, setup, plan) =>
            val path = _database_path(name.replace(' ', '-'))
            val store = _store(path)
            val context = _context(store)
            given ExecutionContext = context
            val prepared =
              _seed_root(store).flatMap(_ => setup(store, context))
            val before = store.load(_root_collection, _root_entry)
            val result =
              prepared.flatMap(_ => store.conditionalTransition(plan))
            val after = store.load(_root_collection, _root_entry)
            (name, prepared, before, result, after)
          }

          Then("every failure rolls back and leaves the root unchanged")
          outcomes.foreach { case (name, prepared, before, result, after) =>
            withClue(name) {
              prepared shouldBe Consequence.unit
              _is_failure(result) shouldBe true
              after shouldBe before
            }
          }
        }
      }

    "roll back every staged record after deterministic provider failures" must
      _provider_metadata {
        "when execution fails after guard, successor, root, side effects, or before commit" in {
          Given("one failure injection for every provider checkpoint and commit")
          val failures: Vector[
            (
              String,
              Option[DataStoreConditionalTransitionCheckpoint],
              Boolean
            )
          ] =
            Vector(
              (
                "after-guard",
                Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
                false
              ),
              (
                "after-successor",
                Some(
                  DataStoreConditionalTransitionCheckpoint.SuccessorPrepared
                ),
                false
              ),
              (
                "after-root",
                Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
                false
              ),
              (
                "before-publish",
                Some(DataStoreConditionalTransitionCheckpoint.BeforePublish),
                false
              ),
              ("during-commit", None, true)
            )

          When("each provider rejects the same valid transition")
          val outcomes =
            failures.map { case (name, checkpoint, failcommit) =>
              val path = _database_path(name)
              val store = _store(path, checkpoint, failcommit)
              given ExecutionContext = _context(store)
              val sidebefore =
                Record.dataAuto(
                  "id" -> _side_entry.print,
                  "event" -> "before"
                )
              val prepared =
                _seed_root(store).flatMap(_ =>
                  store.create(
                    _side_collection,
                    _side_entry,
                    sidebefore
                  )
                )
              val rootbefore =
                store.load(_root_collection, _root_entry)
              val result =
                prepared.flatMap(_ =>
                  store.conditionalTransition(
                    _create_plan(_successor_entry, _side_entry)
                  )
                )
              val rootafter =
                store.load(_root_collection, _root_entry)
              val successorafter =
                store.load(_successor_collection, _successor_entry)
              val sideafter =
                store.load(_side_collection, _side_entry)
              (
                name,
                prepared,
                sidebefore,
                rootbefore,
                result,
                rootafter,
                successorafter,
                sideafter
              )
            }

          Then("root, successor, and side records retain the pre-transaction state")
          outcomes.foreach {
            case (
                  name,
                  prepared,
                  sidebefore,
                  rootbefore,
                  result,
                  rootafter,
                  successorafter,
                  sideafter
                ) =>
            withClue(name) {
              prepared shouldBe Consequence.unit
              _is_failure(result) shouldBe true
              rootafter shouldBe rootbefore
              successorafter shouldBe Consequence.success(None)
              sideafter shouldBe
                Consequence.success(Some(sidebefore))
            }
          }
        }
      }

    "admit one winner across independent callers and connections" must
      _provider_metadata {
        "when bounded concurrent callers race with one root token" in {
          Given(
            "caller counts from two through twelve, each opening its own SQLite DataStore and JDBC connection"
          )
          val property = Prop.forAll(Gen.chooseNum(2, 12)) { callercount =>
            _race(
              _database_path(s"race-$callercount"),
              callercount,
              path => _store(path)
            )
          }

          When("SQLite serializes each native write transaction")
          val checked = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(20),
            property
          )

          Then("one transition wins, every loser mismatches, and no orphan exists")
          checked.passed shouldBe true
        }
      }

    "configure strict SQLite transactions through the generic JDBC factory" must
      _provider_metadata {
        "when independent JDBC-configured callers race with one root token" in {
          Given(
            "two callers using SqlDataStore.jdbc with a SQLite URL rather than the SQLite convenience factory"
          )
          val path = _database_path("jdbc-factory-race")

          When("the generic factory resolves and executes the SQLite provider")
          val onewinner =
            _race(
              path,
              2,
              candidate => _jdbc_store(candidate)
            )

          Then("the generic factory preserves one-winner and authoritative loser semantics")
          onewinner shouldBe true
        }
      }

    "retain the authoritative compound state after provider restart" must
      _provider_metadata {
        "when a fresh datastore instance opens the same database" in {
          Given("one committed SQLite transition")
          val path = _database_path("restart")
          val first = _store(path)
          val firstcontext = _context(first)
          _seed_root(first)(using firstcontext) shouldBe Consequence.unit
          _transitioned(
            first.conditionalTransition(
              _create_plan(_successor_entry, _side_entry)
            )(using firstcontext)
          ).isDefined shouldBe true

          When("an independently constructed provider reopens the database")
          val restarted = _store(path)
          val restartedcontext = _context(restarted)
          val root =
            restarted.load(
              _root_collection,
              _root_entry
            )(using restartedcontext)
          val successor =
            restarted.load(
              _successor_collection,
              _successor_entry
            )(using restartedcontext)
          val side =
            restarted.load(
              _side_collection,
              _side_entry
            )(using restartedcontext)

          Then("the root reference, successor, side effect, and revision are visible")
          root.toOption.flatten
            .flatMap(_.getString("successor_id")) shouldBe
            Some(_successor_entry.print)
          root.toOption.flatten
            .flatMap(_.getInt(_revision_field)) shouldBe Some(2)
          successor.toOption.flatten should not be empty
          side.toOption.flatten
            .flatMap(_.getString("event")) shouldBe Some("transitioned")
        }
      }
  }
}

object SqliteConditionalTransitionSpec {
  private val _root_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "sql_root")
    )
  private val _successor_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "sql_successor")
    )
  private val _side_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "sql_side")
    )
  private val _root_entry = DataStore.StringEntryId("root-1")
  private val _successor_entry = DataStore.StringEntryId("successor-1")
  private val _side_entry = DataStore.StringEntryId("side-1")
  private val _revision_field = "cncf_revision"
  private val _component_owner =
    _success(DataStoreComponentOwner.create("test-component"))

  private def _database_path(
    name: String
  ): Path =
    Files.createTempFile(s"cncf-sqlite-conditional-$name-", ".db")

  private def _store(
    path: Path,
    checkpoint: Option[DataStoreConditionalTransitionCheckpoint] = None,
    failcommit: Boolean = false
  ): SqlDataStore =
    new TestSqlDataStore(
      _data_source(path),
      checkpoint,
      failcommit
    )

  private def _jdbc_store(
    path: Path
  ): SqlDataStore =
    SqlDataStore.jdbc(
      jdbcUrl = s"jdbc:sqlite:${path.toAbsolutePath}",
      dialect = SqlDataStore.Sqlite,
      driverClassName = Some("org.sqlite.JDBC")
    )

  private def _race(
    path: Path,
    callercount: Int,
    storefactory: Path => SqlDataStore
  ): Boolean = {
    val seedstore = storefactory(path)
    val seedcontext = _context(seedstore)
    val seeded =
      _seed_provider_tables(seedstore)(using seedcontext)
    val executor = Executors.newFixedThreadPool(callercount)
    val futurecontext =
      scala.concurrent.ExecutionContext.fromExecutorService(executor)
    val ready = new CountDownLatch(callercount)
    val start = new CountDownLatch(1)
    try {
      val calls = Vector.tabulate(callercount) { index =>
        Future {
          val callerstore = storefactory(path)
          val callercontext = _context(callerstore)
          val successor =
            DataStore.StringEntryId(s"successor-$index")
          val side = DataStore.StringEntryId(s"side-$index")
          ready.countDown()
          start.await(5L, TimeUnit.SECONDS)
          successor ->
            callerstore.conditionalTransition(
              _create_plan(successor, side)
            )(using callercontext)
        }(futurecontext)
      }
      val callersready = ready.await(5L, TimeUnit.SECONDS)
      start.countDown()
      val results = calls.map(Await.result(_, 20.seconds))
      val winners = results.collect {
        case (successor, result)
            if _transitioned(result).isDefined =>
          successor
      }
      val losers = results.count { case (_, result) =>
        result.toOption.exists(
          _.isInstanceOf[
            DataStoreConditionalTransitionResult.NotMatched
          ]
        )
      }
      val verifier = storefactory(path)
      val verifiercontext = _context(verifier)
      val root =
        verifier
          .load(
            _root_collection,
            _root_entry
          )(using verifiercontext)
          .toOption
          .flatten
      val successorexistence =
        results.map { case (successor, _) =>
          verifier
            .load(
              _successor_collection,
              successor
            )(using verifiercontext)
            .toOption
            .flatten
            .isDefined
        }
      val sideexistence =
        Vector.tabulate(callercount) { index =>
          verifier
            .load(
              _side_collection,
              DataStore.StringEntryId(s"side-$index")
            )(using verifiercontext)
            .toOption
            .flatten
            .isDefined
        }
      callersready &&
      seeded.isSuccess &&
      results.size == callercount &&
      winners.size == 1 &&
      losers == callercount - 1 &&
      successorexistence.count(identity) == 1 &&
      sideexistence.count(identity) == 1 &&
      root.flatMap(_.getInt(_revision_field)).contains(2) &&
      root
        .flatMap(_.getString("successor_id"))
        .contains(winners.head.print)
    } finally {
      futurecontext.shutdown()
      futurecontext.awaitTermination(5L, TimeUnit.SECONDS)
    }
  }

  private def _data_source(
    path: Path
  ): SQLiteDataSource = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(10000)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    val datasource = new SQLiteDataSource(config)
    datasource.setUrl(s"jdbc:sqlite:${path.toAbsolutePath}")
    datasource
  }

  private def _seed_root(
    store: SqlDataStore
  )(using
    context: ExecutionContext
  ): Consequence[Unit] =
    store.create(
      _root_collection,
      _root_entry,
      _root_record("open", 1L, None)
    )

  private def _seed_provider_tables(
    store: SqlDataStore
  )(using
    context: ExecutionContext
  ): Consequence[Unit] = {
    val successorplaceholder = DataStore.StringEntryId("placeholder")
    val sideplaceholder = DataStore.StringEntryId("placeholder")
    for {
      _ <- _seed_root(store)
      _ <- store.create(
        _successor_collection,
        successorplaceholder,
        _successor_record(successorplaceholder, 1L)
      )
      _ <- store.delete(_successor_collection, successorplaceholder)
      _ <- store.create(
        _side_collection,
        sideplaceholder,
        Record.dataAuto(
          "id" -> sideplaceholder.print,
          "event" -> "placeholder"
        )
      )
      _ <- store.delete(_side_collection, sideplaceholder)
    } yield ()
  }

  private def _create_plan(
    successorid: DataStore.EntryId,
    sideid: DataStore.EntryId
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(successorid),
        DataStoreConditionalSuccessor.Create(
          _component_owner,
          _successor_collection,
          successorid,
          _revision_field,
          _successor_record(successorid, 1L)
        ),
        Vector(
          EntityVersionedSideEffect.Save(
            _side_collection,
            sideid,
            Record.dataAuto(
              "id" -> sideid.print,
              "event" -> "transitioned"
            )
          )
        )
      )
    )

  private def _bind_plan(
    successorid: DataStore.EntryId,
    revision: Long
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(successorid),
        DataStoreConditionalSuccessor.Bind(
          _component_owner,
          _successor_collection,
          successorid,
          _revision_field,
          _revision(revision)
        )
      )
    )

  private def _root(
    successorid: DataStore.EntryId
  ): DataStoreConditionalRoot =
    _success(
      DataStoreConditionalRoot.create(
        _component_owner,
        _root_collection,
        _root_entry,
        _revision_field,
        Some(EntityRevision.INITIAL),
        Vector(
          DataStoreConditionalExpectedField(
            "status",
            _success(DataStoreConditionalValue.from("open"))
          )
        ),
        Record.dataAuto(
          "status" -> "closed",
          "successor_id" -> successorid.print
        ),
        _revision(2L)
      )
    )

  private def _root_record(
    status: String,
    revision: Long,
    successorid: Option[String]
  ): Record =
    successorid
      .map(id =>
        Record.dataAuto(
          "id" -> _root_entry.print,
          "status" -> status,
          "successor_id" -> id,
          _revision_field -> revision
        )
      )
      .getOrElse(
        Record.dataAuto(
          "id" -> _root_entry.print,
          "status" -> status,
          _revision_field -> revision
        )
      )

  private def _successor_record(
    entryid: DataStore.EntryId,
    revision: Long
  ): Record =
    Record.dataAuto(
      "id" -> entryid.print,
      "name" -> "successor",
      _revision_field -> revision
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _transitioned(
    consequence: Consequence[DataStoreConditionalTransitionResult]
  ): Option[DataStoreConditionalTransitionResult.Transitioned] =
    consequence.toOption.collect {
      case value: DataStoreConditionalTransitionResult.Transitioned =>
        value
    }

  private def _success[A](
    consequence: Consequence[A]
  ): A =
    consequence match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalArgumentException(conclusion.display)
    }

  private def _is_failure[A](
    consequence: Consequence[A]
  ): Boolean =
    consequence match {
      case _: Consequence.Failure[?] => true
      case _ => false
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      throw new IllegalArgumentException(
        s"valid EntityRevision expected: $value"
      )
    )

  private final class TestSqlDataStore(
    datasource: SQLiteDataSource,
    checkpoint: Option[DataStoreConditionalTransitionCheckpoint],
    failcommit: Boolean
  ) extends SqlDataStore(
        SqliteDialectDriver,
        datasource
      ) {
    override protected def conditional_transition_checkpoint(
      current: DataStoreConditionalTransitionCheckpoint
    ): Consequence[Unit] =
      if (checkpoint.contains(current))
        Consequence.operationInvalid(
          "injected-conditional-transition-checkpoint"
        )
      else
        Consequence.unit

    override protected def conditional_transition_commit(
      connection: Connection
    ): Consequence[Unit] =
      if (failcommit)
        DataStoreConditionalTransitionFailure.transactionFailure(
          "injected conditional transition commit rejection"
        )
      else
        super.conditional_transition_commit(connection)
  }
}
