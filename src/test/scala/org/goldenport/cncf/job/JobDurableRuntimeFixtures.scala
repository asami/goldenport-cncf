package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.time.{Clock, Duration, ZoneOffset}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.configuration.*
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfHandle}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.config.*
import org.goldenport.cncf.context.*
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.{ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * Only target-owned paths and real managed bindings are positive runtimes.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object JobDurableRuntimeFixtures {
  val instant = JobDurableLifetimeRecoveryFixtures.instant
  val access = JobDurableLifetimeRecoveryFixtures.access
  val providerId = "fixture_provider"
  val emptyConfiguration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

  def success[A](result: Consequence[A]): A = JobDurableLifetimeRecoveryFixtures.success(result)
  def failureConclusion[A](result: Consequence[A]): org.goldenport.Conclusion = result match {
    case Consequence.Failure(value) => value
    case Consequence.Success(_) => throw new IllegalStateException("expected a consequence failure")
  }
  def configuration(values: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap), ConfigurationTrace.empty)

  def bindings(values: (String, ConfigurationValue)*): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] = {
    val identity = success(SubsystemInstanceId.create("platform", "default"))
    decodeBindings(values.toVector, success(CncfConfigurationTarget.SubsystemInstance.create(identity)), identity)
  }

  def decodeBindings(values: Vector[(String, ConfigurationValue)], target: CncfConfigurationTarget,
    identity: SubsystemInstanceId): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] = {
    val fields = ConfigurationDocument.Object(values.map { case (key, value) =>
      ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
    })
    val document: ConfigurationDocument = target match {
      case CncfConfigurationTarget.Global => ConfigurationDocument.Object(Vector(ConfigurationDocument.Field("global",
        ConfigurationDocument.Object(Vector(ConfigurationDocument.Field("config", fields))))))
      case _ => fields
    }
    val location = target match {
      case CncfConfigurationTarget.Global => CncfConfigurationDocumentLocation.Consolidated
      case value: CncfConfigurationTarget.SubsystemInstance => new CncfConfigurationDocumentLocation.SubsystemInstance(value)
      case value: CncfConfigurationTarget.ComponentClass => success(CncfConfigurationDocumentLocation.ComponentClass.create(value.component.name))
      case value: CncfConfigurationTarget.ComponentInstance => success(CncfConfigurationDocumentLocation.ComponentInstance.create(value.subsystem.subsystem,value.subsystem.instance,value.component.name,value.component.instance))
    }
    if (values.isEmpty) Consequence.success(ConfigurationBindingCollection.empty[CncfConfigurationTarget])
    else for {
      admission <- ConfigurationSourceAdmission.create(ConfigurationOrigin.Home, "home", "durable-fixture", 10,
        "durable-fixture", () => Consequence.success(document))
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(CncfConfigurationDocumentBatch(location, admission)))
      context <- CncfConfigurationResolutionContext.forSubsystem(identity)
      result <- ConfigurationBindingResolver.resolve(candidates, context.generic)
    } yield result
  }

  def root(): Path = Files.createTempDirectory(Files.createDirectories(Paths.get("target/configured-durable-runtime/work").toAbsolutePath.normalize()), "case-")
  // A native managed view is lease-local. Retain the physical resource, and
  // validate the current real Subsystem lease before each SQL borrow.
  def managedSql(selected: Subsystem, environment: ComponentDataStore.Environment,
    request: ComponentDataStore.Request, root: Path, original: SqlDataStore): SqlDataStore = {
    val resource = original.managedResourceOption.get
    SqlDataStore.managedView(resource, s"jdbc:sqlite:${root.resolve("jobs.db")}", SqlDataStore.Sqlite,
      () => selected.resolveManagedComponentDataStoreC(environment, request).flatMap {
        case current: SqlDataStore if current.managedResourceOption.contains(resource) => Consequence.unit
        case _ => DurableJobRuntimeOwner.refused
      })
  }

  def remove(root: Path): Unit = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { Files.deleteIfExists(path); () })
    finally stream.close()
  }

  def withFixture[A](body: Fixture => A): A = {
    val path = root()
    val fixture = new Fixture(path)
    try body(fixture) finally { fixture.close(); remove(path) }
  }

  final class StartupSource(var entries: Vector[DurableJobStartupRecoveryCandidate] = Vector.empty) extends DurableJobStartupRecoverySource {
    val calls = new AtomicInteger
    var before: () => Unit = () => ()
    var fault: Option[Consequence[Vector[DurableJobStartupRecoveryCandidate]]] = None
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      calls.incrementAndGet(); before(); fault.getOrElse(Consequence.success(entries))
    }
  }
  final class RetentionSource(var entries: Vector[DurableJobRetentionCandidate] = Vector.empty) extends DurableJobRetentionCandidateSource {
    val calls = new AtomicInteger
    var before: () => Unit = () => ()
    var fault = false
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] = {
      calls.incrementAndGet(); before()
      if (fault) Consequence.stateInvalid("PRIVATE retention refusal") else Consequence.success(entries)
    }
  }

  final class Fixture(val root: Path, val node: SystemNode = SystemNode.create(), manual: Boolean = true,
    val ownsNode: Boolean = true) {
    private val _profile_configuration = RuntimeExecutionProfileConfiguration.default.copy(
      mode = if (manual) ExecutionProfileMode.Controlled else ExecutionProfileMode.Seeded, runKey = Some("durable-runtime-69709702"),
      timeMode = if (manual) ExecutionTimeMode.Manual else ExecutionTimeMode.System,
      timeStartAt = if (manual) Some(instant) else None, randomMode = ExecutionRandomMode.Seeded,
      randomSeed = Some("69709702"), idMode = if (manual) ExecutionIdMode.Deterministic else ExecutionIdMode.Production,
      schedulerMode = if (manual) ExecutionSchedulerMode.Manual else ExecutionSchedulerMode.Realtime,
      orderingMode = if (manual) ExecutionOrderingMode.Deterministic else ExecutionOrderingMode.Concurrent)
    private val _profile = success(ExecutionProfileResolver.resolve(_profile_configuration, ExecutionProfileActivation.InProcessSpec, Map.empty))
    private val _runtime = GlobalRuntimeContext.create("durable-spec", RuntimeConfig.from(emptyConfiguration, None, _profile),
      emptyConfiguration, ExecutionContext.create().observability, AliasResolver.empty)
    val subsystem = new Subsystem("durable-fixture", configuration = emptyConfiguration, scopeContext = Some(_runtime), systemnode = node)
    val engine = subsystem.jobEngine.asInstanceOf[InMemoryJobEngine]
    val ids = new JobInputBoundsFixtures.CountingIds
    val clock = new JobInputBoundsFixtures.CountingClock
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    val environment = ComponentDataStore.Environment(ResolvedParameters.empty(), Some(configuration(
      "textus.component.component-a.datastores.application.sqlite.path" -> root.resolve("jobs.db").toString)))
    val request = ComponentDataStore.Request("component-a")
    val builds = new AtomicInteger
    val startup = new StartupSource
    val retention = new RetentionSource
    val evidence = new DurableJobKeyedEvidenceFixtures.Source(_ => DurableJobKeyedEvidenceFixtures.recipe())
    var selectedEvidence: DurableJobKeyedEvidenceSource = evidence
    val tasks = scala.collection.mutable.HashMap.empty[JobId, List[JobTask]]
    val port = new JobDurableLifetimeRecoveryFixtures.Port(snapshot => {
      val id = success(JobId.parse(snapshot.record.body.identity.jobId))
      DurableJobRuntimeRehydration(id, tasks.getOrElse(id, Nil), context, None, None)
    })
    var selectedPort: DurableJobRuntimeRehydrationPort = port
    val entity = new JobDurableLifetimeRecoveryFixtures.ObservedEntityStore
    val blob = new LocalBlobStore(root.resolve("blobs"))
    var selectedBlob: BlobStore = blob
    var storagePolicy = DurableJobStorageResourcePolicy.default
    var blobPolicy = DurableJobOwnedBlobResourcePolicy.default
    var lifetimePolicy = DurableJobLifetimeQuotaPolicy.default
    var inputBefore: () => Unit = () => ()
    var provider: Option[SqlDataStore] = None
    var inputs: Option[DurableJobRuntimeInputs] = None
    val registered = new ConfiguredDurableJobRuntimeProvider(providerId, selected => {
      builds.incrementAndGet(); inputBefore()
      selected.resolveManagedComponentDataStoreC(environment, request).map { datastore =>
        val sql = datastore.asInstanceOf[SqlDataStore]
        provider = Some(sql)
        context.dataStoreSpace.useDataStore(managedSql(selected, environment, request, root, sql))
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        val value = DurableJobRuntimeInputs(entity, selectedBlob, selectedEvidence, startup, selectedPort, retention,
          context, storagePolicy, blobPolicy, lifetimePolicy)
        inputs = Some(value)
        value
      }
    })

    def admit(id: String = providerId): Unit = success(subsystem.admitRuntimeConfigurationBindingsC(success(bindings(
      JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(id),
      JobDurableRuntimeConfiguration.periodKey -> ConfigurationValue.StringValue("1000")))))
    def start(): Consequence[Unit] = {
      admit(); success(subsystem._register_durable_job_provider_c(registered)); subsystem._initialize_durable_job_runtime_c()
    }
    def owner: DurableJobRuntimeOwner = engine._durable_runtime_owner_option.get
    def signed(id: JobId): DurableJobStoreSnapshot = success(owner._execute_c {
      owner._store.load(id.value, access)(using context).map(_.get)
    })
    def native(id: JobId): DurableJobLifetimeQuotaSnapshot = success(owner._execute_c {
      owner._lifetime.snapshot(id.value, access)(using context)
    })
    def submit(task: JobTask): JobId = success(owner._execute_c {
      engine.submit(List(task), context,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))
    })
    def advance(duration: Duration = Duration.ofSeconds(1)): Unit =
      _runtime.executionProfileRuntime.schedulingRuntime.testControl.get.advanceBy(duration)
    def close(): Unit = {
      subsystem.shutdownC()
      if (ownsNode) node.shutdownC()
      ()
    }
  }

  final class CliInputs(val root: Path) {
    val startup = new StartupSource
    val retention = new RetentionSource
    val builds = new AtomicInteger
    val context = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))
    val entity = new JobDurableLifetimeRecoveryFixtures.ObservedEntityStore
    val evidence = new DurableJobKeyedEvidenceFixtures.Source(_ => DurableJobKeyedEvidenceFixtures.recipe())
    val port = new JobDurableLifetimeRecoveryFixtures.Port(snapshot => DurableJobRuntimeRehydration(
      success(JobId.parse(snapshot.record.body.identity.jobId)), Nil, context, None, None))
    val blob = new LocalBlobStore(root.resolve("blobs"))
    var subsystem = Option.empty[Subsystem]
    var provider = Option.empty[SqlDataStore]
    var before: Subsystem => Unit = _ => ()
    var finalComponents = Vector.empty[String]
    var extra = false
    val environment = ComponentDataStore.Environment(ResolvedParameters.empty(), Some(configuration(
      "textus.component.component-a.datastores.application.sqlite.path" -> root.resolve("jobs.db").toString)))
    val request = ComponentDataStore.Request("component-a")
    val registered = new ConfiguredDurableJobRuntimeProvider(providerId, selected => {
      builds.incrementAndGet(); before(selected)
      finalComponents = selected.components.toVector.map(_.name)
      selected.resolveManagedComponentDataStoreC(environment, request).map { datastore =>
        val sql = datastore.asInstanceOf[SqlDataStore]
        provider = Some(sql)
        context.dataStoreSpace.useDataStore(managedSql(selected, environment, request, root, sql))
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        DurableJobRuntimeInputs(entity, blob, evidence, startup, port, retention, context,
          DurableJobStorageResourcePolicy.default, DurableJobOwnedBlobResourcePolicy.default,
          DurableJobLifetimeQuotaPolicy.default)
      }
    })
    def extras(selected: Subsystem): Seq[Component] = {
      subsystem = Some(selected)
      success(selected._register_durable_job_provider_c(registered))
      if (!extra) Nil else {
        val id = ComponentId("org.goldenport.cncf.test.DurableExtra")
        val component = new Component() {}
        val core = Component.Core.create(name = id.name, componentId = id,
          instanceId = ComponentInstanceId.default(id), protocol = Protocol.empty)
        Vector(component.initialize(ComponentInit(selected, core, ComponentOrigin.Builtin)))
      }
    }
    def initialize(realtime: Boolean = false): Consequence[CncfHandle] = {
      val arguments = cliArguments(root)
      if (realtime) {
        val file = root.resolve("test.yaml")
        Files.writeString(file, Files.readString(file).replace("profile: controlled", "profile: seeded")
          .replace("mode: manual", "mode: realtime").replace("mode: deterministic", "mode: concurrent"))
        Files.writeString(file, Files.readString(file).replace("ids:\n    mode: concurrent", "ids:\n    mode: production")
          .replace("time:\n    mode: realtime", "time:\n    mode: system")
          .replace(s"    start-at: $instant\n", ""))
      }
      new CncfRuntime().initializeHandle(BootstrapConfig(cwd = root, args = arguments, extraComponents = extras))
    }
  }

  def preseed(root: Path, records: Vector[DurableJobRecord]): Unit = {
    val database = new JobDurableLifetimeRecoveryFixtures.Database(root)
    val original = database.open()
    try records.foreach(record => JobDurableLifetimeRecoveryFixtures.publish(original.store, record)(using original.context))
    finally database.closeBeforeReopen(original)
  }

  def eventually(body: => Boolean): Unit = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!body && System.nanoTime() < deadline) Thread.onSpinWait()
    if (!body) throw new IllegalStateException("durable runtime state timed out")
  }

  def candidate(id: JobId, ordinal: Long = 0L): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, id.value, access, JobDurableLifetimeRecoveryFixtures.replay)

  def await(latch: CountDownLatch): Unit =
    if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("durable runtime barrier timed out")
  def join(thread: Thread): Unit = {
    thread.join(5000)
    if (thread.isAlive) { thread.interrupt(); thread.join(5000) }
    if (thread.isAlive) throw new IllegalStateException("durable runtime actor did not finish")
  }
  def actor[A](body: => A): (Thread, AtomicReference[Either[Throwable, A]]) = {
    val result = new AtomicReference[Either[Throwable, A]]()
    val thread = new Thread(() => try result.set(Right(body)) catch { case exception: Throwable => result.set(Left(exception)) })
    thread.start()
    thread -> result
  }

  def descriptor(path: Path): Path = {
    val file = path.resolve("test.yaml")
    Files.writeString(file, s"""kind: test-descriptor
      |execution:
      |  profile: controlled
      |  key: durable-runtime-69709702
      |  time:
      |    mode: manual
      |    start-at: $instant
      |  random:
      |    mode: seeded
      |    seed: durable-runtime-69709702
      |  ids:
      |    mode: deterministic
      |  scheduler:
      |    mode: manual
      |  ordering:
      |    mode: deterministic
      |""".stripMargin)
    file
  }

  def cliArguments(path: Path): Array[String] = Array(
    "--no-default-components", "--textus.subsystem=platform", s"--textus.test.descriptor=${descriptor(path)}",
    s"--textus.test.home.path=${path.resolve("home")}", "--textus.test.home.inherit.runtime=false",
    "--textus.test.home.inherit.repositories=false", "--textus.test.home.inherit.local-data=false",
    s"--textus.binding=@s/platform/default:${JobDurableRuntimeConfiguration.providerKey}=$providerId",
    s"--textus.binding=@s/platform/default:${JobDurableRuntimeConfiguration.periodKey}=1000")
}
