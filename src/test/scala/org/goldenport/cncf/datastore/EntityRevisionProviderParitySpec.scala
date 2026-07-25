package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import java.util.concurrent.{
  ConcurrentLinkedQueue,
  CountDownLatch,
  TimeUnit
}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{
  SqlDataStore,
  SqliteDialectDriver
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityRevisionProviderParitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity revision provider parity" should {
    "preserve the ordinary mutation matrix across admitted representations and local providers" in {
      Given(
        "embedded and detached revision fields backed independently by in-memory and SQLite providers"
      )
      val scenarios =
        for {
          revisionfield <- Vector("revision", "cncf_revision")
          provider <- Vector("in-memory", "sqlite")
        } yield {
          val fixture = _fixture(provider)
          val collection =
            DataStore.CollectionId(
              s"revision_${provider.replace("-", "_")}_${revisionfield}"
            )
          _ordinary_matrix(
            fixture.store,
            collection,
            revisionfield
          )
        }

      When("each provider executes apply, no-op, stale, exhaustion, and admission cases")
      val evidence = scenarios

      Then("every provider and representation returns the same authoritative outcomes")
      all(evidence) shouldBe true
    }

    "retain committed SQLite revision state after provider reconstruction" in {
      Given("one file-backed SQLite Entity with embedded revision")
      val path = _database_path("restart")
      val collection = DataStore.CollectionId("revision_restart")
      val entry = DataStore.StringEntryId("root")
      val first = SqlDataStore.sqlite(path.toString)
      given ExecutionContext = _context(first)
      first.create(
        collection,
        entry,
        _record(entry, "before", "revision", 1L)
      ) shouldBe Consequence.unit

      When("the first provider commits and a new provider instance loads the same Entity")
      val mutated =
        _provider(first).mutateVersionedEntity(
          _plan(
            collection,
            entry,
            "revision",
            _revision(1L),
            "after"
          )
        )
      val restarted = SqlDataStore.sqlite(path.toString)
      val restartedcontext = _context(restarted)
      val loaded =
        restarted.load(collection, entry)(using restartedcontext)

      Then("the reconstructed provider observes the committed business state and revision")
      mutated.toOption.collect {
        case EntityVersionedMutationResult.Applied(record) =>
          record.getLong("revision")
      }.flatten shouldBe Some(2L)
      loaded.toOption.flatten.flatMap(_.getString("name")) shouldBe
        Some("after")
      loaded.toOption.flatten.flatMap(_.getLong("revision")) shouldBe
        Some(2L)
    }

    "rollback root and side state when local providers fail before publication" in {
      Given("in-memory and SQLite providers with an injected pre-publication failure")
      val stores: Vector[(String, DataStore)] = Vector(
        "in-memory" -> new FailingInMemoryDataStore,
        "sqlite" -> new FailingSqliteDataStore(
          _data_source(_database_path("rollback"))
        )
      )

      When("each provider prepares a root update and one side effect")
      val evidence = stores.map { case (name, store) =>
        val collection =
          DataStore.CollectionId(s"revision_${name.replace("-", "_")}_rollback")
        val sidecollection =
          DataStore.CollectionId(s"revision_${name.replace("-", "_")}_side")
        val entry = DataStore.StringEntryId("root")
        val sideentry = DataStore.StringEntryId("side")
        given ExecutionContext = _context(store)
        val setup = store.create(
          collection,
          entry,
          _record(entry, "before", "revision", 1L)
        )
        val result = setup.flatMap(_ =>
          _provider(store).mutateVersionedEntity(
            _plan(
              collection,
              entry,
              "revision",
              _revision(1L),
              "after"
            ).copy(
              sideEffects = Vector(
                EntityVersionedSideEffect.Save(
                  sidecollection,
                  sideentry,
                  Record.dataAuto(
                    "id" -> sideentry.print,
                    "body" -> "candidate"
                  )
                )
              )
            )
          )
        )
        val root = store.load(collection, entry)
        val side = store.load(sidecollection, sideentry)
        (
          result.toOption.isEmpty,
          root.toOption.flatten.flatMap(_.getString("name")),
          root.toOption.flatten.flatMap(_.getLong("revision")),
          side.toOption.flatten
        )
      }

      Then("neither provider publishes candidate state or advances revision")
      all(evidence) shouldBe ((true, Some("before"), Some(1L), None))
    }

    "admit exactly one optimistic writer through each local provider" in {
      Given("four callers sharing revision one through in-memory and SQLite providers")
      val memory = DataStore.inMemorySearchable()
      val sqlitepath = _database_path("concurrent")
      val providers = Vector(
        (
          "in-memory",
          memory,
          () => memory
        ),
        (
          "sqlite",
          SqlDataStore.sqlite(sqlitepath.toString),
          () => SqlDataStore.sqlite(sqlitepath.toString)
        )
      )

      When("the callers race with the same expected revision")
      val evidence = providers.map { case (name, seedstore, callerstore) =>
        _race(name, seedstore, callerstore)
      }

      Then("each provider commits one revision-two winner and reports all others stale")
      all(evidence) shouldBe ((1, 3, Some(2L)))
    }
  }

  private def _ordinary_matrix(
    store: DataStore,
    collection: DataStore.CollectionId,
    revisionfield: String
  ): Boolean = {
    given ExecutionContext = _context(store)
    val appliedentry = DataStore.StringEntryId("applied")
    val exhaustedentry = DataStore.StringEntryId("exhausted")
    val missingentry = DataStore.StringEntryId("missing")
    val setup =
      Vector(
        appliedentry -> _record(
          appliedentry,
          "before",
          revisionfield,
          1L
        ),
        exhaustedentry -> _record(
          exhaustedentry,
          "before",
          revisionfield,
          Long.MaxValue
        ),
        missingentry -> Record.dataAuto(
          "id" -> missingentry.print,
          "name" -> "legacy"
        )
      ).foldLeft(Consequence.unit) { case (result, (entry, record)) =>
        result.flatMap(_ => store.create(collection, entry, record))
      }
    val provider = _provider(store)
    val applied = setup.flatMap(_ =>
      provider.mutateVersionedEntity(
        _plan(
          collection,
          appliedentry,
          revisionfield,
          _revision(1L),
          "after"
        )
      )
    )
    val duplicate = applied.flatMap(_ =>
      provider.mutateVersionedEntity(
        _plan(
          collection,
          appliedentry,
          revisionfield,
          _revision(2L),
          "after"
        ).copy(
          writePolicy = EntityWritePolicy.WriteIfChanged
        )
      )
    )
    val stale = duplicate.flatMap(_ =>
      provider.mutateVersionedEntity(
        _plan(
          collection,
          appliedentry,
          revisionfield,
          _revision(1L),
          "after"
        ).copy(
          writePolicy = EntityWritePolicy.WriteIfChanged,
          preconditionPolicy =
            RevisionPreconditionPolicy.ObservedRequired
        )
      )
    )
    val exhausted = provider.mutateVersionedEntity(
      _plan(
        collection,
        exhaustedentry,
        revisionfield,
        _revision(Long.MaxValue),
        "candidate"
      )
    )
    val missing = provider.mutateVersionedEntity(
      _plan(
        collection,
        missingentry,
        revisionfield,
        _revision(1L),
        "candidate"
      )
    )
    val authoritative =
      store.load(collection, appliedentry).toOption.flatten
    val exhaustedrecord =
      store.load(collection, exhaustedentry).toOption.flatten
    applied.toOption.exists(
      _.isInstanceOf[EntityVersionedMutationResult.Applied]
    ) &&
    duplicate.toOption.exists(
      _.isInstanceOf[EntityVersionedMutationResult.NoOp]
    ) &&
    stale.toOption.contains(
      EntityVersionedMutationResult.Stale(
        _revision(1L),
        _revision(2L)
      )
    ) &&
    exhausted.toOption.isEmpty &&
    missing.toOption.isEmpty &&
    authoritative.flatMap(_.getString("name")).contains("after") &&
    authoritative.flatMap(_.getLong(revisionfield)).contains(2L) &&
    exhaustedrecord.flatMap(_.getString("name")).contains("before") &&
    exhaustedrecord
      .flatMap(_.getLong(revisionfield))
      .contains(Long.MaxValue)
  }

  private def _race(
    name: String,
    seedstore: DataStore,
    callerstore: () => DataStore
  ): (Int, Int, Option[Long]) = {
    val collection =
      DataStore.CollectionId(s"revision_${name.replace("-", "_")}_race")
    val entry = DataStore.StringEntryId("root")
    val seedcontext = _context(seedstore)
    seedstore.create(
      collection,
      entry,
      _record(entry, "before", "revision", 1L)
    )(using seedcontext) shouldBe Consequence.unit
    val ready = new CountDownLatch(4)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(4)
    val results =
      new ConcurrentLinkedQueue[
        Consequence[EntityVersionedMutationResult]
      ]()
    val workers = Vector.tabulate(4) { index =>
      new Thread(
        () => {
          val store = callerstore()
          val context = _context(store)
          ready.countDown()
          try {
            if (start.await(5L, TimeUnit.SECONDS))
              results.add(
                _provider(store).mutateVersionedEntity(
                  _plan(
                    collection,
                    entry,
                    "revision",
                    _revision(1L),
                    s"candidate-$index"
                  )
                )(using context)
              )
          } finally {
            done.countDown()
          }
        },
        s"revision-provider-$name-$index"
      )
    }
    workers.foreach(_.start())
    ready.await(5L, TimeUnit.SECONDS) shouldBe true
    start.countDown()
    done.await(20L, TimeUnit.SECONDS) shouldBe true
    workers.foreach(_.join(5000L))
    val outcomes =
      results.toArray.toVector.map(
        _.asInstanceOf[Consequence[EntityVersionedMutationResult]]
      )
    val applied = outcomes.count(
      _.toOption.exists(
        _.isInstanceOf[EntityVersionedMutationResult.Applied]
      )
    )
    val stale = outcomes.count(
      _.toOption.exists(
        _.isInstanceOf[EntityVersionedMutationResult.Stale]
      )
    )
    val verifier = callerstore()
    val loaded =
      verifier.load(collection, entry)(using _context(verifier))
    (
      applied,
      stale,
      loaded.toOption.flatten.flatMap(_.getLong("revision"))
    )
  }

  private def _fixture(
    provider: String
  ): ProviderFixture =
    provider match {
      case "in-memory" =>
        ProviderFixture(DataStore.inMemorySearchable())
      case "sqlite" =>
        ProviderFixture(
          SqlDataStore.sqlite(
            _database_path("matrix").toString
          )
        )
      case other =>
        fail(s"unknown provider fixture: $other")
    }

  private def _plan(
    collection: DataStore.CollectionId,
    entry: DataStore.EntryId,
    revisionfield: String,
    expectedrevision: EntityRevision,
    name: String
  ): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(
      collection = collection,
      entryId = entry,
      revisionField = revisionfield,
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

  private def _record(
    entry: DataStore.EntryId,
    name: String,
    revisionfield: String,
    revision: Long
  ): Record =
    Record.dataAuto(
      "id" -> entry.print,
      "name" -> name,
      revisionfield -> revision
    )

  private def _provider(
    store: DataStore
  ): EntityVersionedMutationDataStore =
    store match {
      case provider: EntityVersionedMutationDataStore =>
        provider
      case other =>
        fail(
          s"EntityVersionedMutationDataStore expected: ${other.getClass.getName}"
        )
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _database_path(
    name: String
  ): Path =
    Files.createTempFile(s"cncf-revision-$name-", ".db")

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

  private final case class ProviderFixture(store: DataStore)

  private final class FailingInMemoryDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    override protected def versioned_mutation_checkpoint(
      checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      if (checkpoint == EntityVersionedMutationCheckpoint.BeforePublish)
        Consequence.operationInvalid(
          "injected-versioned-mutation-checkpoint"
        )
      else
        Consequence.unit
  }

  private final class FailingSqliteDataStore(
    datasource: SQLiteDataSource
  ) extends SqlDataStore(
        SqliteDialectDriver,
        datasource
      ) {
    override protected def versioned_mutation_checkpoint(
      checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      if (checkpoint == EntityVersionedMutationCheckpoint.BeforePublish)
        Consequence.operationInvalid(
          "injected-versioned-mutation-checkpoint"
        )
      else
        Consequence.unit
  }
}
