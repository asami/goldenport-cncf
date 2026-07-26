package org.goldenport.cncf.datastore

import java.sql.Connection
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

import com.mysql.cj.jdbc.MysqlDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{
  MySqlDialectDriver,
  SqlDataStore
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

/*
 * Opt-in executable specification for Phase 49 shared-provider acceptance.
 * Normal test runs cancel this suite before contacting Docker.
 *
 * @since   Jul. 24, 2026
 *  version Jul. 25, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class MysqlConditionalTransitionAcceptanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  import MysqlConditionalTransitionAcceptanceSpec.*

  private var _container: Option[LiveMysqlContainer] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (_live_enabled) {
      val container = new LiveMysqlContainer()
      container.start()
      _container = Some(container)
    }
  }

  override protected def afterAll(): Unit =
    try {
      _container.foreach(_.stop())
      _container = None
    } finally {
      super.afterAll()
    }

  private val _provider_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, examples:E5,E6,E8-E11,E17, rules:R8-R13,R23, phase:49"
    )

  "MySQL conditional transition" should {
    "preserve create bind and mismatch semantics through the provider-neutral JDBC boundary" must
      _provider_metadata {
        "when independent stores execute the same admitted plans" in {
          _with_live_mysql { database =>
            Given(
              "one live MySQL database and independent create, bind, and mismatch roots"
            )
            val createcollections = _collections("basic_create")
            val createstore = _jdbc_store(database)
            val createcontext = _context(createstore)
            _seed_root(
              createstore,
              createcollections,
              "open"
            )(using createcontext) shouldBe Consequence.unit

            val bindcollections = _collections("basic_bind")
            val bindstore = _store(database)
            val bindcontext = _context(bindstore)
            _seed_root(
              bindstore,
              bindcollections,
              "open"
            )(using bindcontext) shouldBe Consequence.unit
            bindstore.create(
              bindcollections.successor,
              _successor_entry,
              _successor_record(_successor_entry, 3L)
            )(using bindcontext) shouldBe Consequence.unit

            val mismatchcollections = _collections("basic_mismatch")
            val mismatchstore = _store(database)
            val mismatchcontext = _context(mismatchstore)
            _seed_root(
              mismatchstore,
              mismatchcollections,
              "closed"
            )(using mismatchcontext) shouldBe Consequence.unit

            When("the provider evaluates create, bind, and exact-match guards")
            val created =
              createstore.conditionalTransition(
                _create_plan(
                  createcollections,
                  _successor_entry,
                  _side_entry
                )
              )(using createcontext)
            val bound =
              bindstore.conditionalTransition(
                _bind_plan(bindcollections, _successor_entry, 3L)
              )(using bindcontext)
            val mismatched =
              mismatchstore.conditionalTransition(
                _create_plan(
                  mismatchcollections,
                  _successor_entry,
                  _side_entry
                )
              )(using mismatchcontext)

            Then(
              "create and bind return authoritative records while mismatch publishes nothing"
            )
            _transitioned(created)
              .flatMap(_.rootRecord.getInt(_revision_field)) shouldBe Some(2)
            _transitioned(created)
              .flatMap(_.successorRecord.getInt(_revision_field)) shouldBe
              Some(1)
            _transitioned(bound)
              .flatMap(_.successorRecord.getInt(_revision_field)) shouldBe
              Some(3)
            mismatched.toOption.collect {
              case result: DataStoreConditionalTransitionResult.NotMatched =>
                result.existingRootRecord.getString("status")
            }.flatten shouldBe Some("closed")
            mismatchstore.load(
              mismatchcollections.successor,
              _successor_entry
            )(using mismatchcontext) shouldBe Consequence.success(None)
            mismatchstore.load(
              mismatchcollections.side,
              _side_entry
            )(using mismatchcontext) shouldBe Consequence.success(None)
          }
        }
      }

    "preserve successor failures without mutating the admitted root" must
      _provider_metadata {
        "when create ownership collides or bind state is absent or stale" in {
          _with_live_mysql { database =>
            Given("three isolated live MySQL successor failure scenarios")
            val scenarios: Vector[
              (
                String,
                (
                  (SqlDataStore, Collections, ExecutionContext) =>
                    Consequence[Unit]
                ),
                Collections => DataStoreConditionalTransitionPlan,
                Taxonomy.Symptom,
                Option[String]
              )
            ] = Vector(
              (
                "collision",
                (
                  store: SqlDataStore,
                  collections: Collections,
                  context: ExecutionContext
                ) =>
                  store.create(
                    collections.successor,
                    _successor_entry,
                    _successor_record(_successor_entry, 1L)
                  )(using context),
                collections =>
                  _create_plan(
                    collections,
                    _successor_entry,
                    _side_entry
                  ),
                Taxonomy.Symptom.Conflict,
                Some("successor-collision")
              ),
              (
                "missing_bind",
                (
                  _: SqlDataStore,
                  _: Collections,
                  _: ExecutionContext
                ) => Consequence.unit,
                collections =>
                  _bind_plan(collections, _successor_entry, 1L),
                Taxonomy.Symptom.NotFound,
                None
              ),
              (
                "stale_bind",
                (
                  store: SqlDataStore,
                  collections: Collections,
                  context: ExecutionContext
                ) =>
                  store.create(
                    collections.successor,
                    _successor_entry,
                    _successor_record(_successor_entry, 2L)
                  )(using context),
                collections =>
                  _bind_plan(collections, _successor_entry, 1L),
                Taxonomy.Symptom.Conflict,
                Some("bound-successor-revision-conflict")
              )
            )

            When("each failure is evaluated inside its provider transaction")
            val outcomes = scenarios.map {
              case (
                    name,
                    setup,
                    planfactory,
                    expectedsymptom,
                    expectedreason
                  ) =>
                val collections = _collections(s"failure_$name")
                val store = _store(database)
                val context = _context(store)
                val prepared =
                  _seed_root(store, collections, "open")(using context)
                    .flatMap(_ => setup(store, collections, context))
                val before =
                  store.load(
                    collections.root,
                    _root_entry
                  )(using context)
                val successorbefore =
                  store.load(
                    collections.successor,
                    _successor_entry
                  )(using context)
                val sidebefore =
                  store.load(
                    collections.side,
                    _side_entry
                  )(using context)
                val result =
                  prepared.flatMap(_ =>
                    store.conditionalTransition(
                      planfactory(collections)
                    )(using context)
                  )
                val after =
                  store.load(
                    collections.root,
                    _root_entry
                  )(using context)
                val successorafter =
                  store.load(
                    collections.successor,
                    _successor_entry
                  )(using context)
                val sideafter =
                  store.load(
                    collections.side,
                    _side_entry
                  )(using context)
                (
                  name,
                  prepared,
                  expectedsymptom,
                  expectedreason,
                  before,
                  successorbefore,
                  sidebefore,
                  result,
                  after,
                  successorafter,
                  sideafter
                )
            }

            Then(
              "every failure preserves its portable classification and all stored state"
            )
            outcomes.foreach {
              case (
                    name,
                    prepared,
                    expectedsymptom,
                    expectedreason,
                    before,
                    successorbefore,
                    sidebefore,
                    result,
                    after,
                    successorafter,
                    sideafter
                  ) =>
                withClue(name) {
                  prepared shouldBe Consequence.unit
                  val diagnostic = _failure_diagnostic(result)
                  diagnostic.map(_._1) shouldBe
                    Some(expectedsymptom)
                  expectedreason.foreach { reason =>
                    diagnostic.exists(_._2.contains(reason)) shouldBe true
                  }
                  after shouldBe before
                  successorafter shouldBe successorbefore
                  sideafter shouldBe sidebefore
                }
            }
          }
        }
      }

    "roll back every staged record across the provider failure matrix" must
      _provider_metadata {
        "when checkpoints or the commit admission reject a valid transition" in {
          _with_live_mysql { database =>
            Given(
              "one failure injection after guard, successor, root, side work, and before native commit"
            )
            val failures = Vector(
              (
                "after_guard",
                Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
                false
              ),
              (
                "after_successor",
                Some(
                  DataStoreConditionalTransitionCheckpoint.SuccessorPrepared
                ),
                false
              ),
              (
                "after_root",
                Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
                false
              ),
              (
                "before_publish",
                Some(DataStoreConditionalTransitionCheckpoint.BeforePublish),
                false
              ),
              ("during_commit", None, true)
            )

            When("each injected provider failure terminates the transaction")
            val outcomes =
              failures.map { case (name, checkpoint, failcommit) =>
                val collections = _collections(s"rollback_$name")
                val store =
                  _injected_store(
                    database,
                    checkpoint,
                    failcommit
                  )
                val context = _context(store)
                val sidebefore =
                  Record.dataAuto(
                    "id" -> _side_entry.print,
                    "event" -> "before"
                  )
                val prepared =
                  _seed_root(store, collections, "open")(using context)
                    .flatMap(_ =>
                      store.create(
                        collections.side,
                        _side_entry,
                        sidebefore
                      )(using context)
                    )
                val rootbefore =
                  store.load(
                    collections.root,
                    _root_entry
                  )(using context)
                val result =
                  prepared.flatMap(_ =>
                    store.conditionalTransition(
                      _create_plan(
                        collections,
                        _successor_entry,
                        _side_entry
                      )
                    )(using context)
                  )
                val rootafter =
                  store.load(
                    collections.root,
                    _root_entry
                  )(using context)
                val successorafter =
                  store.load(
                    collections.successor,
                    _successor_entry
                  )(using context)
                val sideafter =
                  store.load(
                    collections.side,
                    _side_entry
                  )(using context)
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

            Then(
              "root, successor, and side records retain the pre-transaction state"
            )
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
                  sideafter shouldBe Consequence.success(Some(sidebefore))
                }
            }
          }
        }
      }

    "admit one winner across independent shared-database callers" must
      _provider_metadata {
        "when generated bounded callers race with one authoritative root token" in {
          _with_live_mysql { database =>
            Given(
              "caller counts from two through twelve, each using its own DataStore and connection"
            )
            val sequence = new AtomicInteger(0)
            val property = Prop.forAll(Gen.chooseNum(2, 12)) { callercount =>
              val evidence = _race(
                database,
                _collections(
                  s"race_${sequence.incrementAndGet()}_$callercount"
                ),
                callercount
              )
              Prop(evidence.passed) :| evidence.diagnostic
            }

            When("MySQL locking admission serializes each guarded transition")
            val checked =
              Test.check(
                Test.Parameters.default.withMinSuccessfulTests(20),
                property
              )

            Then(
              "one transition wins and every loser observes the authoritative winner without orphans"
            )
            withClue(checked.toString) {
              checked.passed shouldBe true
            }
          }
        }
      }

    "retain the committed compound state after provider reconstruction" must
      _provider_metadata {
        "when a fresh MySQL DataStore reads the same physical database" in {
          _with_live_mysql { database =>
            Given("one committed shared-provider transition")
            val collections = _collections("restart")
            val first = _store(database)
            val firstcontext = _context(first)
            _seed_root(first, collections, "open")(using firstcontext) shouldBe
              Consequence.unit
            _transitioned(
              first.conditionalTransition(
                _create_plan(
                  collections,
                  _successor_entry,
                  _side_entry
                )
              )(using firstcontext)
            ).isDefined shouldBe true

            When("an independently constructed provider reads the database")
            val restarted = _store(database)
            val restartedcontext = _context(restarted)
            val root =
              restarted.load(
                collections.root,
                _root_entry
              )(using restartedcontext)
            val successor =
              restarted.load(
                collections.successor,
                _successor_entry
              )(using restartedcontext)
            val side =
              restarted.load(
                collections.side,
                _side_entry
              )(using restartedcontext)

            Then(
              "the root reference, successor, side effect, and revision remain authoritative"
            )
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

  "MySQL Entity revision provider" should {
    "preserve the ordinary mutation matrix through the shared provider" in {
      _with_live_mysql { database =>
        Given(
          "independent shared-provider roots for apply, no-op, stale, exhaustion, and missing-revision admission"
        )
        val store = _store(database)
        val context = _context(store)
        val collection = _collection("mysql_revision_provider")
        val appliedentry = DataStore.StringEntryId("applied")
        val exhaustedentry = DataStore.StringEntryId("exhausted")
        val missingentry = DataStore.StringEntryId("missing")
        val setup =
          Vector(
            appliedentry -> _versioned_record(
              appliedentry,
              "before",
              1L
            ),
            exhaustedentry -> _versioned_record(
              exhaustedentry,
              "before",
              Long.MaxValue
            ),
            missingentry -> Record.dataAuto(
              "id" -> missingentry.print,
              "name" -> "legacy"
            )
          ).foldLeft(Consequence.unit) { case (result, (entry, record)) =>
            result.flatMap(_ =>
              store.create(collection, entry, record)(using context)
            )
          }

        When("the shared provider executes the common revision kernel")
        val applied = setup.flatMap(_ =>
          store.mutateVersionedEntity(
            _versioned_plan(
              collection,
              appliedentry,
              _revision(1L),
              "after"
            )
          )(using context)
        )
        val duplicate = applied.flatMap(_ =>
          store.mutateVersionedEntity(
            _versioned_plan(
              collection,
              appliedentry,
              _revision(2L),
              "after"
            ).copy(
              writePolicy = EntityWritePolicy.WriteIfChanged
            )
          )(using context)
        )
        val stale = duplicate.flatMap(_ =>
          store.mutateVersionedEntity(
            _versioned_plan(
              collection,
              appliedentry,
              _revision(1L),
              "after"
            ).copy(
              writePolicy = EntityWritePolicy.WriteIfChanged,
              preconditionPolicy =
                RevisionPreconditionPolicy.ObservedRequired
            )
          )(using context)
        )
        val exhausted =
          store.mutateVersionedEntity(
            _versioned_plan(
              collection,
              exhaustedentry,
              _revision(Long.MaxValue),
              "candidate"
            )
          )(using context)
        val missing =
          store.mutateVersionedEntity(
            _versioned_plan(
              collection,
              missingentry,
              _revision(1L),
              "candidate"
            )
          )(using context)

        Then("MySQL matches the local provider outcomes without fallback writes")
        applied.toOption.exists(
          _.isInstanceOf[EntityVersionedMutationResult.Applied]
        ) shouldBe true
        duplicate.toOption.exists(
          _.isInstanceOf[EntityVersionedMutationResult.NoOp]
        ) shouldBe true
        stale.toOption shouldBe Some(
          EntityVersionedMutationResult.Stale(
            _revision(1L),
            _revision(2L)
          )
        )
        exhausted.toOption shouldBe None
        missing.toOption shouldBe None
        store
          .load(collection, exhaustedentry)(using context)
          .toOption
          .flatten
          .flatMap(_.getLong(_revision_field)) shouldBe
          Some(Long.MaxValue)
      }
    }

    "preserve native direct compare-and-set and invalid-revision admission" in {
      _with_live_mysql { database =>
        Given(
          "one valid native root plus separate fractional and missing-revision roots"
        )
        val store = _store(database)
        val context = _context(store)
        val validcollection =
          _collection("mysql_native_revision_provider")
        val invalidcollection =
          _collection("mysql_native_invalid_revision")
        val missingcollection =
          _collection("mysql_native_missing_revision")
        val validentry = DataStore.StringEntryId("valid")
        val invalidentry = DataStore.StringEntryId("fractional")
        val missingentry = DataStore.StringEntryId("missing")
        val setup =
          store
            .create(
              validcollection,
              validentry,
              _versioned_record(validentry, "before", 1L)
            )(using context)
            .flatMap(_ =>
              store.create(
                invalidcollection,
                invalidentry,
                Record.dataAuto(
                  "id" -> invalidentry.print,
                  "name" -> "fractional",
                  _revision_field -> 1.5d
                )
              )(using context)
            )
            .flatMap(_ =>
              store.create(
                missingcollection,
                missingentry,
                Record.dataAuto(
                  "id" -> missingentry.print,
                  "name" -> "missing"
                )
              )(using context)
            )

        When("native direct and compare-and-set operations execute")
        val direct =
          setup.flatMap(_ =>
            store.mutateEntityDirect(
              _direct_plan(validcollection, validentry, "direct")
            )(using context)
          )
        val applied =
          direct.flatMap(_ =>
            store.compareAndSetEntity(
              _compare_and_set_plan(
                validcollection,
                validentry,
                _revision(2L),
                "cas"
              )
            )(using context)
          )
        val stale =
          applied.flatMap(_ =>
            store.compareAndSetEntity(
              _compare_and_set_plan(
                validcollection,
                validentry,
                _revision(2L),
                "stale"
              )
            )(using context)
          )
        val invalid =
          setup.flatMap(_ =>
            store.mutateEntityDirect(
              _direct_plan(
                invalidcollection,
                invalidentry,
                "changed"
              )
            )(using context)
          )
        val missing =
          setup.flatMap(_ =>
            store.mutateEntityDirect(
              _direct_plan(
                missingcollection,
                missingentry,
                "changed"
              )
            )(using context)
          )

        Then("valid revisions advance and invalid storage remains unchanged")
        direct shouldBe Consequence.success(
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Omitted
          )
        )
        applied shouldBe Consequence.success(
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Omitted
          )
        )
        stale shouldBe Consequence.success(
          EntityMutationProviderResult.Stale(
            _revision(2L),
            _revision(3L)
          )
        )
        invalid shouldBe a[Consequence.Failure[?]]
        missing shouldBe a[Consequence.Failure[?]]
        store
          .load(validcollection, validentry)(using context)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("cas") -> Some(3L)))
        store
          .load(invalidcollection, invalidentry)(using context)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("fractional") -> Some(1.5d)))
        store
          .load(missingcollection, missingentry)(using context)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("missing") -> None))
      }
    }
  }

  private def _with_live_mysql[A](
    body: LiveDatabase => A
  ): A =
    _container match {
      case Some(container) =>
        body(
          LiveDatabase(
            container.getJdbcUrl,
            container.getUsername,
            container.getPassword
          )
        )
      case None =>
        cancel(
          s"Set $LIVE_ENV=true to run the MySQL shared-provider acceptance specification."
        )
    }

  private def _versioned_plan(
    collection: DataStore.CollectionId,
    entry: DataStore.EntryId,
    expectedrevision: EntityRevision,
    name: String
  ): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(
      collection = collection,
      entryId = entry,
      revisionField = _revision_field,
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      writePolicy = EntityWritePolicy.AlwaysWrite,
      preconditionPolicy = RevisionPreconditionPolicy.Managed,
      expectedRevision = Some(expectedrevision),
      rootMutation = EntityVersionedRootMutation.Replace(
        Record.dataAuto(
          "id" -> entry.print,
          "name" -> name
        )
      )
    )

  private def _versioned_record(
    entry: DataStore.EntryId,
    name: String,
    revision: Long
  ): Record =
    Record.dataAuto(
      "id" -> entry.print,
      "name" -> name,
      _revision_field -> revision
    )

  private def _direct_plan(
    collection: DataStore.CollectionId,
    entry: DataStore.EntryId,
    name: String
  ): EntityDirectMutationPlan =
    EntityDirectMutationPlan(
      collection,
      entry,
      _revision_field,
      Record.dataAuto("name" -> name)
    )

  private def _compare_and_set_plan(
    collection: DataStore.CollectionId,
    entry: DataStore.EntryId,
    expectedrevision: EntityRevision,
    name: String
  ): EntityCompareAndSetMutationPlan =
    EntityCompareAndSetMutationPlan(
      collection,
      entry,
      _revision_field,
      expectedrevision,
      Record.dataAuto("name" -> name)
    )
}

object MysqlConditionalTransitionAcceptanceSpec {
  val LIVE_ENV = "CNCF_LIVE_MYSQL_TEST"
  private val _live_enabled =
    sys.env.get(LIVE_ENV).contains("true")
  private val _root_entry = DataStore.StringEntryId("root-1")
  private val _successor_entry =
    DataStore.StringEntryId("successor-1")
  private val _side_entry = DataStore.StringEntryId("side-1")
  private val _revision_field = "cncf_revision"
  private val _component_owner =
    _success(DataStoreComponentOwner.create("test-component"))

  private final case class Collections(
    root: DataStore.CollectionId,
    successor: DataStore.CollectionId,
    side: DataStore.CollectionId
  )

  private final case class LiveDatabase(
    jdbcurl: String,
    username: String,
    password: String
  )

  private final case class RaceEvidence(
    callersready: Boolean,
    seeded: Boolean,
    callercount: Int,
    resultcount: Int,
    winnercount: Int,
    losercount: Int,
    failurecount: Int,
    failurediagnostics: Vector[String],
    successorcount: Int,
    sidecount: Int,
    rootrevision: Option[Int],
    rootsuccessorid: Option[String],
    winnersuccessorid: Option[String]
  ) {
    def passed: Boolean =
      callersready &&
        seeded &&
        resultcount == callercount &&
        winnercount == 1 &&
        losercount == callercount - 1 &&
        failurecount == 0 &&
        successorcount == 1 &&
        sidecount == 1 &&
        rootrevision.contains(2) &&
        rootsuccessorid == winnersuccessorid

    def diagnostic: String =
      s"callersReady=$callersready seeded=$seeded callers=$callercount results=$resultcount winners=$winnercount losers=$losercount failures=$failurecount failureDiagnostics=${failurediagnostics.mkString("[", ",", "]")} successors=$successorcount sides=$sidecount rootRevision=$rootrevision rootSuccessor=$rootsuccessorid winnerSuccessor=$winnersuccessorid"
  }

  private final class LiveMysqlContainer
      extends MySQLContainer(
        DockerImageName.parse("mysql:8.4")
      ) {
    withDatabaseName("cncf_phase49")
    withUsername("cncf")
    withPassword("cncf-phase49")
  }

  private def _collections(
    scenario: String
  ): Collections = {
    val prefix = s"mysql_${scenario}"
    Collections(
      _collection(s"${prefix}_root"),
      _collection(s"${prefix}_successor"),
      _collection(s"${prefix}_side")
    )
  }

  private def _collection(
    name: String
  ): DataStore.CollectionId =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", name)
    )

  private def _jdbc_store(
    database: LiveDatabase
  ): SqlDataStore =
    SqlDataStore.jdbc(
      jdbcUrl = database.jdbcurl,
      dialect = SqlDataStore.Mysql,
      username = Some(database.username),
      password = Some(database.password),
      driverClassName = Some("com.mysql.cj.jdbc.Driver")
    )

  private def _store(
    database: LiveDatabase
  ): SqlDataStore =
    new SqlDataStore(
      MySqlDialectDriver,
      _data_source(database)
    )

  private def _injected_store(
    database: LiveDatabase,
    checkpoint: Option[DataStoreConditionalTransitionCheckpoint],
    failcommit: Boolean
  ): SqlDataStore =
    new TestSqlDataStore(
      _data_source(database),
      checkpoint,
      failcommit
    )

  private def _data_source(
    database: LiveDatabase
  ): MysqlDataSource = {
    val datasource = new MysqlDataSource()
    datasource.setURL(database.jdbcurl)
    datasource.setUser(database.username)
    datasource.setPassword(database.password)
    datasource
  }

  private def _race(
    database: LiveDatabase,
    collections: Collections,
    callercount: Int
  ): RaceEvidence = {
    val seedstore = _store(database)
    val seedcontext = _context(seedstore)
    val seeded =
      _seed_provider_tables(
        seedstore,
        collections
      )(using seedcontext)
    val executor = Executors.newFixedThreadPool(callercount)
    val futurecontext =
      scala.concurrent.ExecutionContext.fromExecutorService(executor)
    val ready = new CountDownLatch(callercount)
    val start = new CountDownLatch(1)
    try {
      val calls = Vector.tabulate(callercount) { index =>
        Future {
          val callerstore = _store(database)
          val callercontext = _context(callerstore)
          val successor =
            DataStore.StringEntryId(s"successor-$index")
          val side = DataStore.StringEntryId(s"side-$index")
          ready.countDown()
          start.await(5L, TimeUnit.SECONDS)
          successor ->
            callerstore.conditionalTransition(
              _create_plan(collections, successor, side)
            )(using callercontext)
        }(futurecontext)
      }
      val callersready = ready.await(5L, TimeUnit.SECONDS)
      start.countDown()
      val results = calls.map(Await.result(_, 30.seconds))
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
      val failures = results.count(result => _is_failure(result._2))
      val failurediagnostics = results.collect {
        case (_, Consequence.Failure(conclusion)) =>
          conclusion.display
      }
      val verifier = _store(database)
      val verifiercontext = _context(verifier)
      val root =
        verifier
          .load(
            collections.root,
            _root_entry
          )(using verifiercontext)
          .toOption
          .flatten
      val successorexistence =
        results.map { case (successor, _) =>
          verifier
            .load(
              collections.successor,
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
              collections.side,
              DataStore.StringEntryId(s"side-$index")
            )(using verifiercontext)
            .toOption
            .flatten
            .isDefined
        }
      RaceEvidence(
        callersready,
        seeded.isSuccess,
        callercount,
        results.size,
        winners.size,
        losers,
        failures,
        failurediagnostics,
        successorexistence.count(identity),
        sideexistence.count(identity),
        root.flatMap(_.getInt(_revision_field)),
        root.flatMap(_.getString("successor_id")),
        winners.headOption.map(_.print)
      )
    } finally {
      futurecontext.shutdown()
      futurecontext.awaitTermination(5L, TimeUnit.SECONDS)
    }
  }

  private def _seed_root(
    store: SqlDataStore,
    collections: Collections,
    status: String
  )(using
    context: ExecutionContext
  ): Consequence[Unit] =
    store.create(
      collections.root,
      _root_entry,
      _root_record(status, 1L, None)
    )

  private def _seed_provider_tables(
    store: SqlDataStore,
    collections: Collections
  )(using
    context: ExecutionContext
  ): Consequence[Unit] = {
    val successorplaceholder =
      DataStore.StringEntryId("placeholder")
    val sideplaceholder = DataStore.StringEntryId("placeholder")
    for {
      _ <- _seed_root(store, collections, "open")
      _ <- store.create(
        collections.successor,
        successorplaceholder,
        _successor_record(successorplaceholder, 1L)
      )
      _ <- store.delete(
        collections.successor,
        successorplaceholder
      )
      _ <- store.create(
        collections.side,
        sideplaceholder,
        Record.dataAuto(
          "id" -> sideplaceholder.print,
          "event" -> "placeholder"
        )
      )
      _ <- store.delete(collections.side, sideplaceholder)
    } yield ()
  }

  private def _create_plan(
    collections: Collections,
    successorid: DataStore.EntryId,
    sideid: DataStore.EntryId
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(collections, successorid),
        DataStoreConditionalSuccessor.Create(
          _component_owner,
          collections.successor,
          successorid,
          _revision_field,
          _successor_record(successorid, 1L)
        ),
        Vector(
          EntityVersionedSideEffect.Save(
            collections.side,
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
    collections: Collections,
    successorid: DataStore.EntryId,
    revision: Long
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(collections, successorid),
        DataStoreConditionalSuccessor.Bind(
          _component_owner,
          collections.successor,
          successorid,
          _revision_field,
          _revision(revision)
        )
      )
    )

  private def _root(
    collections: Collections,
    successorid: DataStore.EntryId
  ): DataStoreConditionalRoot =
    _success(
      DataStoreConditionalRoot.create(
        _component_owner,
        collections.root,
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

  private def _failure_diagnostic[A](
    consequence: Consequence[A]
  ): Option[(Taxonomy.Symptom, Set[String])] =
    consequence match {
      case Consequence.Failure(conclusion) =>
        val reasons =
          conclusion.observation.cause.descriptor.facets.collect {
            case Descriptor.Facet.Reason(name) => name
          }.toSet
        Some(conclusion.observation.taxonomy.symptom -> reasons)
      case _ =>
        None
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
    datasource: MysqlDataSource,
    checkpoint: Option[DataStoreConditionalTransitionCheckpoint],
    failcommit: Boolean
  ) extends SqlDataStore(
        MySqlDialectDriver,
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
