package org.goldenport.cncf.oauth

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import scala.util.Using
import cats.free.Free
import cats.syntax.all.*
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentParameterKey, SecretReference}
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, SubjectKind}
import org.goldenport.cncf.filestore.{FileStoreRef, GoogleDriveCredentials, GoogleDriveFileStore}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod, RuntimeDashboardMetrics}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{CallTreeValueSummary, ConclusionDiagnostics, ConclusionDisclosure}
import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{CommitRecorder, ExecUowM, UnitOfWork, UnitOfWorkAuthorization, UnitOfWorkEffectClass, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramPlanner, UnitOfWorkPlanSegment}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class OAuthConnectionNativeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthConnectionNativeSpec.*

  "Native OAuth connection capability" should {
    "N1 declared component bootstrap" which {
      "leave an absent optional declaration disabled on its actual owner" in {
        Given("a normal Factory declaring the stable optional key and no configured reference")
        _with_root { root =>
          val owner = TestComponentFactory.emptySubsystem("oauth-native-disabled")
          val factory = new NativeFactory(_component, Vector(OAuthConnectionRuntime.configurationKey))
          When("normal primary creation and subsystem installation receive the absent setting")
          val built = _install(factory, owner, None).TAKE
          val context = _context(built, new BoundClock)
          val uow = new NativeUow(context, new NativeDriver(root))
          val outcome = new UnitOfWorkInterpreter(uow).interpret(_operation(OAuthConnectionRequest.status(_connection_id)))
          Then("the component is initialized once, disabled, and its intent has the fixed not-configured failure")
          factory.created.last.initializations shouldBe 1
          factory.created.last.oauthConnection shouldBe None
          owner.components should contain (built)
          _same_failure(outcome, Consequence.resourceNotFound("OAuth connection is not configured"))
          uow.acquisitions shouldBe 0
          _names(root) shouldBe empty
        }
      }

      "install the exact prepared component and instance once while keeping bootstrap projections opaque" in {
        Given("a real private configuration source, named component instance and owning Subsystem")
        _with_root { root =>
          val fixture = new Harness(root)
          val connection = fixture.component.oauthConnection.get
          val before = _names(root)
          When("owner status and the declared initialization snapshot are observed after normal creation")
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          val resolution = fixture.component.initializationParameters.resolve(OAuthConnectionRuntime.configurationKey).TAKE
          val outputs = Vector(fixture.component.initializationParameters.toString, resolution.value.get.toString,
            connection.toString, connection.configuration.toString) ++
            RuntimeDashboardMetrics.componentInitializationParameterRecords.takeRight(4).map(_.print)
          Then("bootstrap retains actual ownership and one connection without a flow, credential write or HTTP")
          fixture.component.componentId shouldBe _component
          fixture.component.instanceId shouldBe _instance
          fixture.component.subsystem.get should be theSameInstanceAs fixture.owner
          connection.configuration.componentId shouldBe _component
          connection.configuration.instanceId shouldBe _instance
          fixture.component.initializations shouldBe 1
          fixture.component.oauthConnection.get should be theSameInstanceAs connection
          fixture.owner.components.count(_ eq fixture.component) shouldBe 1
          status.state shouldBe OAuthConnectionState.AuthorizationRequired
          status.authorizationPending shouldBe false
          Files.exists(fixture.file) shouldBe false
          _names(root) shouldBe before
          fixture.driver.requests shouldBe empty
          outputs.foreach(output => _private_markers(root).foreach(marker => output should not include marker))
        }
      }

      "retain the one bootstrapped grant without reloading custody during later native observations" in {
        Given("a saved unexpired grant loaded through the ordinary declared Factory bootstrap")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val connection = fixture.component.oauthConnection.get
          _write(fixture.file, "{synthetic-later-record-marker")
          When("the owner requests status and cached authorization after the selected file changes")
          val result = new UnitOfWorkInterpreter(fixture.uow(fixture.context())).run(for {
            status <- _lift(_operation(OAuthConnectionRequest.status(_connection_id)))
            header <- _lift(_operation(OAuthConnectionRequest.driveAuthorization(_connection_id)))
          } yield (status, header)).TAKE
          Then("the installed runtime remains the same initialized instance and does not reload or overwrite the record")
          result._1.state shouldBe OAuthConnectionState.Connected
          result._2 shouldBe Map("Authorization" -> s"Bearer ${_access}")
          fixture.component.oauthConnection.get should be theSameInstanceAs connection
          fixture.component.initializations shouldBe 1
          Files.readString(fixture.file, StandardCharsets.UTF_8) shouldBe "{synthetic-later-record-marker"
          fixture.driver.requests shouldBe empty
        }
      }

      "refuse malformed private sources, unsafe selected targets and malformed saved records before installation" in {
        Vector("source", "target", "record").foreach { variant =>
          Given(s"a normal declared Factory and a private bootstrap $variant refusal")
          _with_root { root =>
            val source = _write(root.resolve("operator.json"), _document(root).noSpaces)
            variant match {
              case "source" => _write(source, "{synthetic-source-secret-marker")
              case "target" =>
                _write(root.resolve("grant.json"), "synthetic-record-secret-marker")
                Files.setPosixFilePermissions(root.resolve("grant.json"), PosixFilePermissions.fromString("rw-r--r--"))
              case _ => _write(root.resolve("grant.json"), "{synthetic-record-secret-marker")
            }
            val owner = TestComponentFactory.emptySubsystem("oauth-native-bootstrap-refused")
            val factory = new NativeFactory(_component, Vector(OAuthConnectionRuntime.configurationKey))
            val before = _names(root)
            When("the actual primary creation attempts to load the explicitly selected source")
            val outcome = _install(factory, owner, Some(source.toUri.toString))
            Then("the original structured bootstrap category is preserved and no component or credential is installed")
            val expected = variant match {
              case "source" => Consequence.configurationInvalid("OAuth connection configuration is invalid")
              case "target" => Consequence.configurationInvalid("OAuth credential storage configuration is invalid")
              case _ => Consequence.resourceInvalid("OAuth credential storage record is invalid")
            }
            _same_failure(outcome, expected)
            owner.components shouldBe empty
            factory.created.last.oauthConnection shouldBe None
            factory.created.last.initializations shouldBe 0
            _names(root) shouldBe before
            val outputs = _disclosures(_failure(outcome)) ++
              RuntimeDashboardMetrics.componentInitializationParameterRecords.takeRight(4).map(_.print)
            outputs.foreach(output => (_private_markers(root) ++ Vector("synthetic-source-secret-marker",
              "synthetic-record-secret-marker")).foreach(marker => output should not include marker))
          }
        }
      }

      "require the exact declared key identity instead of accepting a reconstructed or undeclared key" in {
        Vector(Vector.empty[ComponentParameterKey[?]], Vector(ComponentParameterKey.optionalSecretReference(
          OAuthConnectionRuntime.configurationKey.name))).foreach { declarations =>
          Given("a selected private reference whose Factory does not declare the capability's stable key object")
          _with_root { root =>
            val source = _write(root.resolve("operator.json"), _document(root).noSpaces)
            val owner = TestComponentFactory.emptySubsystem("oauth-native-undeclared")
            val factory = new NativeFactory(_component, declarations)
            When("normal Factory bootstrap reaches the capability's typed snapshot resolution")
            val outcome = _install(factory, owner, Some(source.toUri.toString))
            Then("the undeclared snapshot failure prevents installation and does not read a credential or begin a flow")
            outcome.isSuccess shouldBe false
            _failure(outcome).cause.getMessage.get should include ("not declared")
            factory.created.last.oauthConnection shouldBe None
            factory.created.last.initializations shouldBe 0
            owner.components shouldBe empty
            Files.exists(root.resolve("grant.json")) shouldBe false
            _disclosures(_failure(outcome)).foreach(output => _private_markers(root).foreach(marker => output should not include marker))
          }
        }
      }
    }

    "N2 inert typed intent and passive planning" which {
      "keep all five requests and repeated occurrences external without preparing a flow or acquiring HTTP" in {
        Given("a bootstrapped owner, five closed typed requests and existing local and control operations")
        _with_root { root =>
          val fixture = new Harness(root)
          val requests = _requests(_connection_id)
          val repeated = _operation(OAuthConnectionRequest.begin(_connection_id))
          val operations = Vector[UnitOfWorkOp[?]](_local, _control) ++ requests ++ Vector(repeated, repeated, _local)
          val before = _names(root)
          When("factories and Free programs are constructed and the original operation sequence is passively planned")
          val programs = operations.map(operation => _void(operation))
          val storeprogram = fixture.store.read(_reference)
          val plan = UnitOfWorkProgramPlanner.plan(operations)
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("each intent retains its original ordinal and repeated object identity without executing any intent")
          programs.size shouldBe operations.size
          storeprogram should not be null
          plan.occurrences.map(_.operation) shouldBe operations
          plan.occurrences.map(_.ordinal) shouldBe operations.indices.toVector
          plan.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.Control) ++
            Vector.fill(7)(UnitOfWorkEffectClass.External) ++ Vector(UnitOfWorkEffectClass.Local)
          plan.segments.collect { case UnitOfWorkPlanSegment.ExternalBoundary(occurrence) => occurrence.ordinal } shouldBe (2 to 8).toVector
          plan.occurrences(7).operation should be theSameInstanceAs repeated
          plan.occurrences(8).operation should be theSameInstanceAs repeated
          status.authorizationPending shouldBe false
          fixture.driver.requests shouldBe empty
          _names(root) shouldBe before
        }
      }

      "preserve generated valid IDs and mixed effect order in at least one hundred passive programs without discards" in {
        Given("bounded admitted connection IDs and generated mixtures of all five intents, local and control operations")
        _with_root { root =>
          val fixture = new Harness(root)
          val ids = for {
            first <- Gen.oneOf("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toVector)
            size <- Gen.choose(0, 127)
            rest <- Gen.listOfN(size, Gen.oneOf("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-".toVector))
          } yield first.toString + rest.mkString
          val mixtures = for { id <- ids; count <- Gen.choose(0, 35); kinds <- Gen.listOfN(count, Gen.choose(0, 6)) }
            yield (id, kinds.toVector)
          val before = _names(root)
          When("ScalaCheck constructs and plans each generated sequence with two repeated selected intents")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(mixtures) { case (id, kinds) =>
            val choices = _requests(id) ++ Vector(_local, _control)
            val repeated = _operation(OAuthConnectionRequest.begin(id))
            val operations = kinds.map(choices(_)) ++ Vector(repeated, repeated)
            val programs = operations.map(operation => _void(operation))
            val plan = UnitOfWorkProgramPlanner.plan(operations)
            val expected = kinds.map {
              case 5 => UnitOfWorkEffectClass.Local
              case 6 => UnitOfWorkEffectClass.Control
              case _ => UnitOfWorkEffectClass.External
            } ++ Vector.fill(2)(UnitOfWorkEffectClass.External)
            programs.size == operations.size && GoogleDriveCredentials.managedConnectionC(id).isSuccess &&
              plan.occurrences.map(_.operation) == operations && plan.occurrences.map(_.ordinal) == operations.indices.toVector &&
              plan.occurrences.map(_.effectClass) == expected &&
              plan.segments.collect { case UnitOfWorkPlanSegment.ExternalBoundary(occurrence) => occurrence.ordinal } ==
                expected.zipWithIndex.collect { case (UnitOfWorkEffectClass.External, index) => index } &&
              fixture.driver.requests.isEmpty && _names(root) == before
          })
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("every generated order is preserved without a pending authorization, filesystem mutation or driver effect")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          status.authorizationPending shouldBe false
          fixture.driver.requests shouldBe empty
          _names(root) shouldBe before
        }
      }

      "reject invalid managed selectors through the legacy fixed configuration category" in {
        Given("null, empty, path-shaped, whitespace, non-ASCII and oversized connection selectors")
        val values = Vector(null, "", ".drive", "drive/path", "drive name", "資料", "a" * 129)
        When("the public managed strategy factory interprets each malformed selector")
        val outcomes = values.map(GoogleDriveCredentials.managedConnectionC)
        Then("no strategy is constructed and the fixed existing credential argument failure contains no submitted selector")
        outcomes.foreach(_same_failure(_, Consequence.argumentInvalid("Invalid Google Drive credential configuration")))
      }
    }

    "N3 canonical native authority and lifecycle" which {
      "complete an owner-started flow anonymously in the same scope with one outer commit and one token POST" in {
        Given("a normal runtime owner, provider-authenticated management and a same-scope anonymous callback")
        _with_root { root =>
          val fixture = new Harness(root)
          val ownercontext = fixture.context()
          val beginuow = fixture.uow(ownercontext)
          val handoff = new UnitOfWorkInterpreter(beginuow).run(_lift(_operation(OAuthConnectionRequest.begin(_connection_id)))).TAKE
          val anonymous = fixture.context(authentication = "anonymous")
          val nested = anonymous.withScope(anonymous.cncfCore.scope.createChildScope(ScopeKind.Action, "native-callback"))
          val callbackuow = fixture.uow(nested)
          When("the closed completion intent reaches the canonical interpreter and the owner subsequently consumes status and headers")
          val completed = new UnitOfWorkInterpreter(callbackuow).run(_lift(_operation(OAuthConnectionRequest.complete(
            _connection_id, _state(handoff), handoff.browserProof, Some(_code), None)))).TAKE
          val consumptionuow = fixture.uow(ownercontext)
          val consumed = new UnitOfWorkInterpreter(consumptionuow).run(for {
            status <- _lift(_operation(OAuthConnectionRequest.status(_connection_id)))
            header <- _lift(_operation(OAuthConnectionRequest.driveAuthorization(_connection_id)))
          } yield (status, header)).TAKE
          Then("the persisted grant and typed header share native ownership while each enclosing run alone commits")
          completed.state shouldBe OAuthConnectionState.Connected
          consumed._1.state shouldBe OAuthConnectionState.Connected
          consumed._2 shouldBe Map("Authorization" -> s"Bearer ${_access}")
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          fixture.driver.requests.head.path shouldBe _token
          _grant(root).hcursor.get[String]("accessToken").toOption shouldBe Some(_access)
          Vector(beginuow, callbackuow, consumptionuow).foreach { uow =>
            uow.commits shouldBe 1
            uow.aborts shouldBe 0
            uow.recorded.count(_ == "UnitOfWork.commit") shouldBe 1
          }
          callbackuow.acquisitions shouldBe 1
          callbackuow.executionContext should be theSameInstanceAs nested
          SecuritySubject.from(nested.security).isAuthenticated shouldBe false
          SecuritySubject.from(nested.security).isProviderAuthenticated shouldBe false
          nested.security should be theSameInstanceAs anonymous.security
        }
      }

      "keep anonymous callback authority narrow after successful completion" in {
        Given("an owner-started native flow and a matching anonymous completion")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.interpret(OAuthConnectionRequest.begin(_connection_id)).TAKE
          val anonymous = fixture.context(authentication = "anonymous")
          val uow = fixture.uow(anonymous)
          new UnitOfWorkInterpreter(uow).interpret(_operation(OAuthConnectionRequest.complete(
            _connection_id, _state(handoff), handoff.browserProof, Some(_code), None))).TAKE
          val before = Files.readAllBytes(fixture.file).toVector
          When("that same anonymous ingress requests management or an existing public Drive read")
          val denied = Vector(OAuthConnectionRequest.status(_connection_id), OAuthConnectionRequest.begin(_connection_id),
            OAuthConnectionRequest.forget(_connection_id), OAuthConnectionRequest.driveAuthorization(_connection_id))
            .map(request => new UnitOfWorkInterpreter(uow).interpret(_operation(request)))
          val drive = new UnitOfWorkInterpreter(uow).run(fixture.store.read(_reference))
          Then("every owner or Drive request is refused and no callback authority becomes a service or ingress grant")
          (denied :+ drive).foreach(_same_failure(_, _owner_denied))
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          Files.readAllBytes(fixture.file).toVector shouldBe before
          SecuritySubject.from(anonymous.security).isAuthenticated shouldBe false
          uow.commits shouldBe 0
        }
      }

      "select only the actual current component without searching configured ancestors or the subsystem" in {
        Given("one installed configured runtime and ordinary foreign component and foreign instance scopes")
        _with_root { root =>
          val fixture = new Harness(root)
          val configuredcontext = fixture.context()
          val foreignid = ComponentId("org.example.ForeignNative")
          val foreign = Component.create(foreignid.name, foreignid, ComponentInstanceId.default(foreignid), Protocol.empty)
          val otherinstance = Component.create(_component.name, _component, ComponentInstanceId(_component, "foreign-instance"), Protocol.empty)
          Vector(foreign, otherinstance).foreach(_.withScopeContext(configuredcontext.cncfCore.scope))
          val contexts = Vector(configuredcontext.withScope(foreign.scopeContext),
            configuredcontext.withScope(otherinstance.scopeContext), configuredcontext.withScope(
              ScopeContext.Instance(configuredcontext.cncfCore.scope.core.copy(parent = None))))
          val before = _names(root)
          When("all five typed intents are interpreted outside a current OAuth-capable component")
          val outcomes = contexts.flatMap { context =>
            val uow = fixture.uow(context)
            _requests(_connection_id).map(operation => new UnitOfWorkInterpreter(uow).interpret(operation))
          }
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("the fixed runtime-unavailable failure precedes HTTP, flow creation and storage mutation")
          outcomes.foreach(_same_failure(_, Consequence.serviceUnavailable("OAuth connection runtime is unavailable")))
          fixture.driver.requests shouldBe empty
          _names(root) shouldBe before
          status.authorizationPending shouldBe false
        }
      }

      "refuse foreign selectors and invalid requests before selecting a runtime or changing its pending flow" in {
        Given("a configured current owner and malformed or foreign connection requests")
        _with_root { root =>
          val fixture = new Harness(root)
          val uow = fixture.uow(fixture.context())
          val interpreter = new UnitOfWorkInterpreter(uow)
          val invalid = Vector(null, "", "x/y", "資料", "a" * 129)
          When("null and malformed requests and all five foreign connection selectors reach native interpretation")
          val nullrequest = interpreter.interpret(UnitOfWorkOp.OAuthConnectionExecute[OAuthConnectionStatus](null))
          val invalidrequests = invalid.flatMap(id => _requests(id).map(interpreter.interpret(_)))
          val foreignrequests = _requests("foreign-connection").map(interpreter.interpret(_))
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("invalid data has the fixed argument category and a different valid ID has the fixed not-configured category")
          (invalidrequests :+ nullrequest).foreach(_same_failure(_, Consequence.argumentInvalid("OAuth connection request is invalid")))
          foreignrequests.foreach(_same_failure(_, Consequence.resourceNotFound("OAuth connection is not configured")))
          uow.acquisitions shouldBe 0
          fixture.driver.requests shouldBe empty
          status.authorizationPending shouldBe false
          Files.exists(fixture.file) shouldBe false
        }
      }

      "deny anonymous, forged, trusted-local and foreign owners before the bound clock or managed effects" in {
        Given("an actual configured component and unadmitted security provenance despite copied public attributes")
        _with_root { root =>
          val fixture = new Harness(root)
          val contexts = Vector("anonymous", "forged", "local", "provider-anonymous").map(mode => fixture.context(authentication = mode)) :+
            fixture.context(ownerId = "foreign-owner")
          fixture.clock.reset()
          val before = _names(root)
          When("every unadmitted subject requests all four owner operations through the actual native route")
          val outcomes = contexts.flatMap { context =>
            val uow = fixture.uow(context)
            Vector(OAuthConnectionRequest.status(_connection_id), OAuthConnectionRequest.begin(_connection_id),
              OAuthConnectionRequest.forget(_connection_id), OAuthConnectionRequest.driveAuthorization(_connection_id))
              .map(request => new UnitOfWorkInterpreter(uow).interpret(_operation(request)))
          }
          val ticks = fixture.clock.ticks
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("all refusals retain the owner category and occur before time, entropy, driver acquisition or storage mutation")
          outcomes.foreach(_same_failure(_, _owner_denied))
          ticks shouldBe 0
          fixture.driver.requests shouldBe empty
          _names(root) shouldBe before
          status.authorizationPending shouldBe false
        }
      }

      "preserve a legitimate pending flow when callback state or browser proof does not match" in {
        Given("a native owner-started flow and an anonymous callback in the exact same component scope")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.interpret(OAuthConnectionRequest.begin(_connection_id)).TAKE
          val uow = fixture.uow(fixture.context(authentication = "anonymous"))
          val interpreter = new UnitOfWorkInterpreter(uow)
          When("a wrong state and then a wrong proof are submitted as closed completion intents")
          val outcomes = Vector(interpreter.interpret(_operation(OAuthConnectionRequest.complete(
            _connection_id, "z" * 43, handoff.browserProof, Some(_code), None))),
            interpreter.interpret(_operation(OAuthConnectionRequest.complete(
              _connection_id, _state(handoff), "z" * 43, Some(_code), None))))
          val pending = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("the native flow category is returned without token exchange, storage writes or consumption of the real match")
          outcomes.foreach(_same_failure(_, Consequence.securityPermissionDenied("OAuth authorization flow is invalid")))
          pending.authorizationPending shouldBe true
          fixture.driver.requests shouldBe empty
          uow.acquisitions shouldBe 0
          Files.exists(fixture.file) shouldBe false
        }
      }

      "persist native success even when the enclosing commit later reports failure" in {
        Given("a matched native flow and a failure in the outer post-commit callback")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.interpret(OAuthConnectionRequest.begin(_connection_id)).TAKE
          val uow = fixture.uow(fixture.context(authentication = "anonymous"))
          uow.stagePostCommitC(Consequence.stateConflict("Synthetic outer commit callback failed"))
          When("native completion succeeds before the outer commit reports its independent failure")
          val result = new UnitOfWorkInterpreter(uow).run(_lift(_operation(OAuthConnectionRequest.complete(
            _connection_id, _state(handoff), handoff.browserProof, Some(_code), None))))
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("the outer failure claims no rollback of the token POST, persisted grant or connection state")
          result.isSuccess shouldBe false
          uow.commits shouldBe 1
          uow.aborts shouldBe 0
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          _grant(root).hcursor.get[String]("accessToken").toOption shouldBe Some(_access)
          status.state shouldBe OAuthConnectionState.Connected
        }
      }

      "execute explicit owner disconnect through the canonical intent without deleting unrelated data" in {
        Given("a normal owner with a persisted grant, a pending flow and adjacent unrelated domain data")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val sentinel = _write(root.resolve("domain-sentinel.txt"), "retained-domain-data")
          fixture.interpret(OAuthConnectionRequest.begin(_connection_id)).TAKE
          val uow = fixture.uow(fixture.context())
          When("the provider-authenticated exact owner runs its closed forget intent")
          val status = new UnitOfWorkInterpreter(uow).run(_lift(_operation(OAuthConnectionRequest.forget(_connection_id)))).TAKE
          Then("only selected local credential custody and the pending flow are cleared with one enclosing commit")
          status.state shouldBe OAuthConnectionState.AuthorizationRequired
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Disconnected)
          status.authorizationPending shouldBe false
          status.canRefresh shouldBe false
          Files.exists(fixture.file) shouldBe false
          Files.readString(sentinel, StandardCharsets.UTF_8) shouldBe "retained-domain-data"
          fixture.driver.requests shouldBe empty
          uow.commits shouldBe 1
          uow.aborts shouldBe 0
        }
      }
    }

    "N4 managed public Drive consumption" which {
      "use cached native authorization for existing public stat and read with the original octets" in {
        Given("a normal bootstrapped component with a persisted unexpired grant and an opaque managed strategy")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val context = fixture.context()
          val uow = fixture.uow(context)
          When("the existing FileStore stat then read program is interpreted by the standard native UoW")
          val result = new UnitOfWorkInterpreter(uow).run(for {
            metadata <- fixture.store.stat(_reference)
            content <- fixture.store.read(_reference)
          } yield (metadata, content)).TAKE
          val octets = Using.resource(result._2.content.openInputStream())(_.readAllBytes().toVector)
          Then("only the two admitted Drive GETs use the cached header and return exact metadata and binary content")
          result._1.reference shouldBe _reference
          result._1.byteLength shouldBe Some(_octets.size.toLong)
          octets shouldBe _octets
          result._2.content should be theSameInstanceAs fixture.driver.content
          fixture.driver.requests.map(_.method) shouldBe Vector("GET", "GET")
          fixture.driver.requests.foreach(_.headers("Authorization") shouldBe s"Bearer ${_access}")
          fixture.driver.requests.head.path should include ("?supportsAllDrives=true&fields=")
          fixture.driver.requests.last.path should include ("?alt=media&supportsAllDrives=true")
          uow.acquisitions shouldBe 2
          uow.executionContext should be theSameInstanceAs context
          uow.commits shouldBe 1
          uow.aborts shouldBe 0
        }
      }

      "refresh one expired persisted grant and save rotation before either Drive GET in the same outer UoW" in {
        Given("an expired saved grant and a controlled native token response rotating access and refresh material")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          fixture.driver.tokenResponse = _token_response(_rotated_access, _rotated_refresh)
          val context = fixture.context()
          val uow = fixture.uow(context)
          When("the public FileStore stat and read chain requests authorization twice")
          val result = new UnitOfWorkInterpreter(uow).run(for {
            metadata <- fixture.store.stat(_reference)
            content <- fixture.store.read(_reference)
          } yield (metadata, content)).TAKE
          Then("one token POST precedes saved rotation and both GETs, with no nested commit, retry or context substitution")
          result._1.reference shouldBe _reference
          fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET", "GET")
          fixture.driver.requests.head.text.get should include ("grant_type=refresh_token")
          fixture.driver.requests.head.text.get should include (_refresh)
          fixture.driver.requests.tail.foreach(_.headers("Authorization") shouldBe s"Bearer ${_rotated_access}")
          fixture.driver.savedAtGet shouldBe Vector.fill(2)(Some(_rotated_refresh))
          _grant(root).hcursor.get[String]("refreshToken").toOption shouldBe Some(_rotated_refresh)
          uow.acquisitions shouldBe 3
          uow.executionContext should be theSameInstanceAs context
          uow.commits shouldBe 1
          uow.aborts shouldBe 0
          uow.recorded.count(_ == "UnitOfWork.commit") shouldBe 1
        }
      }

      "refuse a generic provider impersonating the Google preset before token or Drive HTTP" in {
        Given("a generic provider object with Google's exact name and endpoints and an expired saved grant")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)), generic = true)
          val before = Files.readAllBytes(fixture.file).toVector
          val uow = fixture.uow(fixture.context())
          When("the existing public FileStore read requests the fixed managed Drive authorization intent")
          val result = new UnitOfWorkInterpreter(uow).run(fixture.store.read(_reference))
          Then("provider provenance rejects the impostor with no refresh, Drive GET or credential mutation")
          _same_failure(result, Consequence.securityPermissionDenied("OAuth connection provider is not allowed"))
          uow.acquisitions shouldBe 0
          fixture.driver.requests shouldBe empty
          Files.readAllBytes(fixture.file).toVector shouldBe before
        }
      }

      "retain the actual native token permission Conclusion and stop before driver acquisition or business retry" in {
        Given("an expired saved grant, admitted Drive GETs and a host policy refusing token POSTs")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          val context = fixture.context(origins = Vector(_drive_origin))
          val expected = HttpExecutionAdmission.resolveC(context, HttpExecutionMethod.Post, _token,
            Vector(Property("http.max-response-bytes", "16384", None)))
          val before = Files.readAllBytes(fixture.file).toVector
          val uow = fixture.uow(context)
          When("the native managed authorization encounters the actual HTTP destination admission refusal")
          val result = new UnitOfWorkInterpreter(uow).run(fixture.store.read(_reference))
          val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
          Then("the native structured status, taxonomy and policy survive with uncertainty and zero driver or Drive requests")
          _same_failure(result, expected)
          ConclusionDiagnostics.classify(_failure(result)).policy shouldBe Some("http.execution.destination")
          uow.acquisitions shouldBe 0
          fixture.driver.requests shouldBe empty
          Files.readAllBytes(fixture.file).toVector shouldBe before
          status.state shouldBe OAuthConnectionState.Uncertain
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          uow.commits shouldBe 0
          uow.aborts shouldBe 1
        }
      }

      "stop Drive access after a failed refresh or failed credential save and preserve uncertainty" in {
        Vector("refresh", "save").foreach { variant =>
          Given(s"an expired persisted grant and an actual native $variant failure")
          _with_root { root =>
            val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
            val before = Files.readAllBytes(fixture.file).toVector
            if (variant == "refresh") fixture.driver.tokenResponse = _json_response("{}", 503)
            else fixture.driver.beforeTokenResponse = () => Files.setPosixFilePermissions(fixture.file,
              PosixFilePermissions.fromString("rw-r--r--"))
            val uow = fixture.uow(fixture.context())
            When("the existing public FileStore read reaches the native refresh and then another read is attempted")
            val failed = new UnitOfWorkInterpreter(uow).run(fixture.store.read(_reference))
            val blocked = new UnitOfWorkInterpreter(fixture.uow(fixture.context())).run(fixture.store.read(_reference))
            val status = fixture.interpret(OAuthConnectionRequest.status(_connection_id)).TAKE
            Then("only one token POST occurs, both reads stop before GET, and no optimistic success or automatic retry is claimed")
            val expected = if (variant == "refresh") Consequence.serviceUnavailable("OAuth authorization service is unavailable")
              else Consequence.configurationInvalid("OAuth credential storage configuration is invalid")
            _same_failure(failed, expected)
            _same_failure(blocked, Consequence.stateConflict("OAuth connection requires reconciliation"))
            fixture.driver.requests.map(_.method) shouldBe Vector("POST")
            Files.readAllBytes(fixture.file).toVector shouldBe before
            status.state shouldBe OAuthConnectionState.Uncertain
            status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
            uow.commits shouldBe 0
            uow.aborts shouldBe 1
          }
        }
      }
    }

    "N5 actual native disclosures" which {
      "redact successful requests, operations, managed strategies and enabled CallTree result summaries" in {
        Given("enabled framework CallTree, a normal owner and synthetic secret-bearing callback material")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val uow = fixture.uow(context)
          val handoff = new UnitOfWorkInterpreter(uow).interpret(_operation(OAuthConnectionRequest.begin(_connection_id))).TAKE
          val request = OAuthConnectionRequest.complete(_connection_id, _state(handoff), handoff.browserProof, Some(_code), None)
          When("native completion and managed Drive read produce their real enabled CallTree and safe summaries")
          val status = new UnitOfWorkInterpreter(uow).interpret(_operation(request)).TAKE
          new UnitOfWorkInterpreter(uow).run(fixture.store.read(_reference)).TAKE
          val outputs = Vector(request.toString, _operation(request).toString, fixture.credentials.toString, handoff.toString,
            RecordEncoder.json(CallTreeValueSummary.summary(handoff)), RecordEncoder.json(CallTreeValueSummary.summary(status)), _trace(context))
          Then("OAuth and HTTP labels remain visible while only the intentional browser URI contains its authorization state")
          _trace(context) should include ("uow:oauth:connection")
          _trace(context) should include ("uow:http:post")
          _trace(context) should include ("uow:http:get")
          val markers = _private_markers(root) ++ Vector(_state(handoff), handoff.browserProof, _code, _access, _refresh)
          outputs.foreach(output => markers.foreach(marker => output should not include marker))
          handoff.authorizationUri should include (_state(handoff))
          handoff.authorizationUri should not include handoff.browserProof
          status.state shouldBe OAuthConnectionState.Connected
        }
      }

      "preserve native transport Conclusion structure while standard, full and log projections omit private markers" in {
        Given("a genuine native callback and a controlled driver exception carrying synthetic code, state, proof, token and path markers")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val uow = fixture.uow(context)
          val handoff = new UnitOfWorkInterpreter(uow).interpret(_operation(OAuthConnectionRequest.begin(_connection_id))).TAKE
          val markers = _private_markers(root) ++ Vector(_state(handoff), handoff.browserProof, _code, _access, _refresh)
          val exception = new IOException(markers.mkString(" "))
          fixture.driver.beforeTokenResponse = () => throw exception
          When("the canonical completion reaches native token HTTP and receives the original transport failure")
          val result = new UnitOfWorkInterpreter(uow).run(_lift(_operation(OAuthConnectionRequest.complete(
            _connection_id, _state(handoff), handoff.browserProof, Some(_code), None))))
          val conclusion = _failure(result)
          val outputs = _disclosures(conclusion) :+ _trace(context)
          Then("the original exception and structured native taxonomy survive without leaking into any actual disclosure")
          conclusion.getException.get should be theSameInstanceAs exception
          conclusion.status shouldBe Conclusion.from(exception).status
          conclusion.observation.taxonomy shouldBe Conclusion.from(exception).observation.taxonomy
          outputs.foreach(output => markers.foreach(marker => output should not include marker))
          _trace(context) should include ("uow:oauth:connection")
          _trace(context) should include ("uow:http:post")
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          Files.exists(fixture.file) shouldBe false
          uow.commits shouldBe 0
          uow.aborts shouldBe 1
        }
      }
    }
  }

  private def _same_failure(actual: Consequence[?], expected: Consequence[?]): Unit = {
    val conclusion = _failure(actual)
    val reference = _failure(expected)
    conclusion.status shouldBe reference.status
    conclusion.observation.taxonomy shouldBe reference.observation.taxonomy
    conclusion.cause.getMessage shouldBe reference.cause.getMessage
  }
}

object OAuthConnectionNativeSpec {
  private val _component = ComponentId("org.example.NativeStudio")
  private val _instance = ComponentInstanceId(_component, "native-one")
  private val _connection_id = "native-drive"
  private val _owner = "synthetic-native-owner-marker"
  private val _code = "synthetic-native-code-marker"
  private val _access = "synthetic-native-access-marker"
  private val _refresh = "synthetic-native-refresh-marker"
  private val _rotated_access = "synthetic-native-rotated-access-marker"
  private val _rotated_refresh = "synthetic-native-rotated-refresh-marker"
  private val _client_secret = "synthetic-native-client-secret-marker"
  private val _observed = Instant.parse("2026-10-21T00:00:00Z")
  private val _token = "https://oauth2.googleapis.com/token"
  private val _authorization = "https://accounts.google.com/o/oauth2/v2/auth"
  private val _drive_origin = "https://www.googleapis.com"
  private val _origins = Vector("https://oauth2.googleapis.com", _drive_origin)
  private val _scopes = Vector("https://www.googleapis.com/auth/drive.file")
  private val _reference = FileStoreRef("native-store", "native_file")
  private val _octets = Vector[Byte](0, -1, -128, 1, 13, 10)
  private val _local = UnitOfWorkOp.LocalDataDir("native-planning")
  private val _control = UnitOfWorkOp.Authorize(UnitOfWorkAuthorization(resourceFamily = "component", accessKind = "read"))
  private val _directory_attribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))

  private final class NativeComponent extends OAuthConnectionRuntime {
    private var _initializations = 0
    def initializations: Int = _initializations
    override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
      super.initialize_component_c(params).map { _ => _initializations += 1 }
  }

  private final class NativeFactory(componentid: ComponentId, declarations: Vector[ComponentParameterKey[?]]) extends Component.Factory {
    private var _created = Vector.empty[NativeComponent]
    def created: Vector[NativeComponent] = _created
    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] = declarations
    protected def create_Component(params: ComponentCreate): Component = {
      val component = new NativeComponent
      _created = _created :+ component
      component
    }
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      Component.Core.create(componentid.name, componentid, ComponentInstanceId.default(componentid), Protocol.empty, this)
  }

  private final class BoundClock extends Clock {
    private val _ticks = new AtomicInteger
    def ticks: Int = _ticks.get()
    def reset(): Unit = _ticks.set(0)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(_observed, zone)
    def instant(): Instant = { _ticks.incrementAndGet(); _observed }
  }

  private final class NativeRequest(val method: String, val path: String, val text: Option[String],
    val headers: Map[String, String], val properties: Vector[Property]) {
    override def toString: String = "NativeRequest(<redacted>)"
  }

  private final class NativeDriver(root: Path) extends HttpDriver {
    private var _requests = Vector.empty[NativeRequest]
    private var _saved_at_get = Vector.empty[Option[String]]
    val content: BinaryBag = Bag.binary(_octets.toArray)
    var tokenResponse: HttpResponse = _token_response(_access, _refresh)
    var beforeTokenResponse: () => Unit = () => ()
    def requests: Vector[NativeRequest] = _requests
    def savedAtGet: Vector[Option[String]] = _saved_at_get
    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _requests = _requests :+ new NativeRequest("GET", path, None, headers, properties)
      _saved_at_get = _saved_at_get :+ _grant(root).hcursor.get[String]("refreshToken").toOption
      if (path.contains("alt=media")) HttpResponse.Binary(HttpStatus.Ok, ContentType(MimeType("application/octet-stream"), None), content)
      else _json_response(Json.obj("id" -> Json.fromString(_reference.key), "name" -> Json.fromString("native original"),
        "mimeType" -> Json.fromString("application/octet-stream"), "size" -> Json.fromString(_octets.size.toString),
        "parents" -> Json.arr(Json.fromString("native_parent")), "trashed" -> Json.False, "version" -> Json.fromString("1")).noSpaces)
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _requests = _requests :+ new NativeRequest("POST", path, body, headers, properties)
      beforeTokenResponse()
      tokenResponse
    }
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      throw new AssertionError("This native connection scenario has no PUT intent")
  }

  private final class RecordingCommit extends CommitRecorder {
    private var _messages = Vector.empty[String]
    def messages: Vector[String] = _messages
    def record(message: String): Unit = { _messages = _messages :+ message }
  }

  private final class NativeUow(context: ExecutionContext, driver: HttpDriver, recording: RecordingCommit = new RecordingCommit)
      extends UnitOfWork(context, recorder = recording) {
    private var _acquisitions = 0
    private var _commits = 0
    private var _aborts = 0
    def acquisitions: Int = _acquisitions
    def commits: Int = _commits
    def aborts: Int = _aborts
    def recorded: Vector[String] = recording.messages
    override def httpDriver: HttpDriver = { _acquisitions += 1; driver }
    override def commit() = { _commits += 1; super.commit() }
    override def abort() = { _aborts += 1; super.abort() }
  }

  private final class Harness(root: Path, expiry: Option[Instant] = None, generic: Boolean = false) {
    val owner: Subsystem = TestComponentFactory.emptySubsystem("oauth-native-owner")
    val source: Path = _write(root.resolve("operator.json"), _document(root, generic).noSpaces)
    val file: Path = root.resolve("grant.json")
    expiry.foreach { expiration =>
      val configuration = OAuthConnectionConfiguration.loadC(SecretReference.fromConfiguration(source.toUri.toString).TAKE,
        _component, _instance).TAKE
      configuration._store.saveC(configuration._client._restore_credential_c(_access, Some(_refresh), _scopes, expiration).TAKE).TAKE
    }
    private val _factory = new NativeFactory(_component, Vector(OAuthConnectionRuntime.configurationKey))
    private val _installed = _install(_factory, owner, Some(source.toUri.toString)).TAKE
    val component: NativeComponent = _factory.created.last
    val clock: BoundClock = new BoundClock
    val driver: NativeDriver = new NativeDriver(root)
    val credentials: GoogleDriveCredentials = GoogleDriveCredentials.managedConnectionC(_connection_id).TAKE
    val store: GoogleDriveFileStore = GoogleDriveFileStore.createC(_reference.store, credentials).TAKE
    def context(ownerId: String = _owner, authentication: String = "provider", origins: Vector[String] = _origins): ExecutionContext =
      _context(component, clock, ownerId, authentication, origins)
    def uow(context: ExecutionContext): NativeUow = new NativeUow(context, driver)
    def interpret[A](request: OAuthConnectionRequest[A]): Consequence[A] =
      new UnitOfWorkInterpreter(uow(context())).interpret(_operation(request))
  }

  private def _install(factory: NativeFactory, owner: Subsystem, reference: Option[String]): Consequence[Component] = {
    val descriptor = ComponentDescriptor(name = Some(_component.name), componentName = Some(_component.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(_component))
    val settings = reference.map(value => OAuthConnectionRuntime.configurationKey.name -> value).toMap
    factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin, Vector(descriptor))
      .withInstanceMetadata(ComponentInstanceMetadata(_component.name, _instance.instance, settings, componentId = Some(_component))))
      .map { component => owner.add(component); component }
  }

  private def _context(component: Component, clock: Clock, ownerid: String = _owner, authentication: String = "provider",
    origins: Vector[String] = _origins): ExecutionContext = {
    val anonymous = authentication == "anonymous" || authentication == "provider-anonymous"
    val security = SecurityContext(new Principal {
      val id: PrincipalId = PrincipalId(ownerid)
      val attributes: Map[String, String] = if (anonymous) Map("anonymous" -> "true") else Map("authenticated" -> "true",
        SecuritySubject.AuthenticationProvenanceAttribute -> SecuritySubject.ProviderAuthenticationProvenance)
    }, Set.empty, SecurityLevel("user"), if (authentication == "anonymous") SubjectKind.Anonymous else SubjectKind.User)
    authentication match {
      case "provider" | "provider-anonymous" => SecurityAuthenticationProvenance.providerAuthenticated(security)
      case "local" => SecurityAuthenticationProvenance.trustedLocalAuthenticated(security)
      case _ => ()
    }
    val base = ExecutionContext.withSecurityContext(ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(clock), true), security)
    val admission = HttpExecutionAdmission.fromRuntimeC(_drive_origin + "/", HttpExecutionLimits(1000, 1000, 1048576),
      Map(_component -> HttpExecutionGrant(origins, Set(HttpExecutionMethod.Get, HttpExecutionMethod.Post)))).TAKE
    val parent = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
    component.withScopeContext(parent)
    base.withScope(component.scopeContext)
  }

  private def _requests(id: String): Vector[UnitOfWorkOp[?]] = Vector(
    _operation(OAuthConnectionRequest.status(id)), _operation(OAuthConnectionRequest.begin(id)),
    _operation(OAuthConnectionRequest.complete(id, "s" * 43, "p" * 43, Some(_code), None)),
    _operation(OAuthConnectionRequest.forget(id)), _operation(OAuthConnectionRequest.driveAuthorization(id)))
  private def _operation[A](request: OAuthConnectionRequest[A]): UnitOfWorkOp[A] = UnitOfWorkOp.OAuthConnectionExecute(request)
  private def _lift[A](operation: UnitOfWorkOp[A]): ExecUowM[A] = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, A](operation))
  private def _void[A](operation: UnitOfWorkOp[A]): ExecUowM[Unit] = _lift(operation).map(_ => ())
  private def _owner_denied: Consequence[Nothing] = Consequence.securityPermissionDenied("OAuth connection owner is not allowed")
  private def _failure(value: Consequence[?]): Conclusion = value match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured native OAuth failure")
  }
  private def _disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full), ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
  private def _trace(context: ExecutionContext): String = context.cncfCore.observability.callTreeContext.build()
    .map(tree => RecordEncoder.json(tree.toRecord)).getOrElse("")
  private def _state(handoff: OAuthAuthorizationHandoff): String = new URI(handoff.authorizationUri).getRawQuery.split("&").map { part =>
    val pair = part.split("=", 2)
    URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
  }.toMap.apply("state")
  private def _document(root: Path, generic: Boolean = false): Json = {
    val provider = if (generic) Json.obj("name" -> Json.fromString("google-drive"),
      "authorizationEndpoint" -> Json.fromString(_authorization), "tokenEndpoint" -> Json.fromString(_token))
      else Json.fromString("google-drive")
    Json.obj("schema" -> Json.fromString("cncf.oauth-connection/1"), "connectionId" -> Json.fromString(_connection_id),
      "ownerId" -> Json.fromString(_owner), "provider" -> provider, "clientId" -> Json.fromString("synthetic-native-client-marker"),
      "clientSecret" -> Json.fromString(_client_secret), "redirectUri" -> Json.fromString(
        s"http://127.0.0.1:8123/web/${NamingConventions.toNormalizedSegment(_component.localId.value())}/connections/${_connection_id}/callback"),
      "scopes" -> Json.fromValues(_scopes.map(Json.fromString)), "credentialFile" -> Json.fromString(root.resolve("grant.json").toString),
      "returnPath" -> Json.fromString("/web/native-studio/home"))
  }
  private def _private_markers(root: Path): Vector[String] = Vector(_client_secret, _owner,
    root.resolve("operator.json").toString, root.resolve("grant.json").toString)
  private def _token_response(access: String, refresh: String): HttpResponse = _json_response(Json.obj(
    "access_token" -> Json.fromString(access), "token_type" -> Json.fromString("Bearer"), "expires_in" -> Json.fromInt(3600),
    "refresh_token" -> Json.fromString(refresh)).noSpaces)
  private def _json_response(text: String, code: Int = 200): HttpResponse = HttpResponse.Text(
    HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError), ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
    Bag.text(text, StandardCharsets.UTF_8), Record.empty)
  private def _grant(root: Path): Json = parser.parse(Files.readString(root.resolve("grant.json"), StandardCharsets.UTF_8)).toOption.get
  private def _write(file: Path, text: String): Path = {
    Files.write(file, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
    file
  }
  private def _names(root: Path): Set[String] = Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toSet)
  private def _with_root[A](body: Path => A): A = {
    val base = Files.createDirectories(Paths.get("target", "oauth-connection-native-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "native-", _directory_attribute).toRealPath()
    try body(root) finally {
      Using.resource(Files.walk(root)) { stream =>
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
}
