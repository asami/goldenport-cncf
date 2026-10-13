package org.goldenport.cncf.datastore

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.config.{ConfigurationAccess, ResolvedParameter}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.goldenport.cncf.datastore.sql.SqlDataStoreIdentity
import org.goldenport.cncf.subsystem.{SystemNodeDataStoreBinding, SystemNodeResourceLease}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Feb. 25, 2026
 *  version Apr. 15, 2026
 *  version May. 11, 2026
 *  version Aug. 11, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStoreSpace private (
  private val _multi_guard_host: Option[DataStoreSpace.MultiGuardHostAdmission]
) {
  def this() = this(None)
  // Nested ActionCalls restore the caller's component-scoped datastore.
  final case class Binding private[DataStoreSpace] (dataStore: Option[DataStore])

  private var _entity_stores: Vector[DataStore] = Vector.empty
  private var _configured_default: Option[() => DataStore] = None
  private val _inject_sequence = new AtomicLong(0L)
  private val _scoped_entity_store = new ThreadLocal[DataStore]()

  def addDataStore(ds: DataStore): DataStoreSpace = {
    _entity_stores = _entity_stores :+ ds
    this
  }

  def useDataStore(ds: DataStore): DataStoreSpace = {
    _entity_stores = Vector(ds)
    _configured_default = None
    this
  }

  def bindDataStore(ds: DataStore): Unit =
    _scoped_entity_store.set(ds)

  def clearBoundDataStore(): Unit =
    _scoped_entity_store.remove()

  def captureBinding(): Binding =
    Binding(Option(_scoped_entity_store.get()))

  def restoreBinding(binding: Binding): Unit =
    binding.dataStore match {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  /**
   * Compatibility entry point. A noncanonical component name cannot select a
   * local datastore; use a canonical ComponentDataStore.Request for local
   * component binding.
   */
  @deprecated("Use a canonical ComponentDataStore.Request for local component binding", "0.5.2")
  def useApplicationDataStore(
    params: org.goldenport.cncf.config.ResolvedParameters,
    componentName: String,
    name: String = "application"
  ): DataStoreSpace =
    useApplicationDataStore(ComponentDataStore.Environment(params), componentName, name)

  /**
   * Compatibility entry point. A noncanonical component name cannot select a
   * local datastore; use a canonical ComponentDataStore.Request for local
   * component binding.
   */
  @deprecated("Use a canonical ComponentDataStore.Request for local component binding", "0.5.2")
  def useApplicationDataStore(
    environment: ComponentDataStore.Environment,
    componentName: String,
    name: String
  ): DataStoreSpace =
    ComponentDataStore.resolveForDataStoreSpace(environment, ComponentDataStore.Request(componentName, name)) match {
      case Some(datastore) => useDataStore(datastore)
      case None => this
    }

  /**
   * Compatibility entry point. A noncanonical component name cannot select a
   * local datastore; use a canonical ComponentDataStore.Request for local
   * component binding.
   */
  @deprecated("Use a canonical ComponentDataStore.Request for local component binding", "0.5.2")
  def bindApplicationDataStore(
    environment: ComponentDataStore.Environment,
    componentName: String,
    name: String
  ): Unit =
    bindApplicationDataStore(environment, ComponentDataStore.Request(componentName, name))

  private[cncf] def bindApplicationDataStore(
    environment: ComponentDataStore.Environment,
    request: ComponentDataStore.Request
  ): Unit =
    ComponentDataStore.resolveForDataStoreSpace(environment, request) match {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  private[cncf] def bindManagedApplicationDataStoreC(
    environment: ComponentDataStore.Environment,
    request: ComponentDataStore.Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey
  ): Consequence[Unit] =
    ComponentDataStore.resolveManagedForDataStoreSpaceC(
      environment,
      request,
      binding,
      lease,
      key
    ).map {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] =
    Consequence {
      Option(_scoped_entity_store.get()).filter(_.isAccept(cid))
        .orElse(_entity_stores.find(_.isAccept(cid)))
        .orElse(_configured_default_store().filter(_.isAccept(cid)))
    }.flatMap(value => Consequence.successOrServiceProviderByKeyNotFound(value)("datastore", cid.print))

  // A managed scoped binding wins before this standalone compatibility default
  // is evaluated. Reading configuration never opens a physical SQL pool.
  private def _configured_default_store(): Option[DataStore] = synchronized {
    _configured_default match {
      case Some(create) =>
        val store = create()
        _entity_stores = _entity_stores :+ store
        _configured_default = None
        Some(store)
      case None => _entity_stores.headOption
    }
  }

  def search(
    cid: DataStore.CollectionId,
    directive: QueryDirective
  )(using ctx: ExecutionContext): Consequence[SearchResult] =
    dataStore(cid).flatMap {
      case m: SearchableDataStore =>
        _with_calltree_c("io:datastore:search", _datastore_calltree_attributes("search", cid, directive, m), "io") {
          m.search(cid, directive)
        }
      case _ =>
        Consequence.dataStoreUnavailable(s"datastore is not searchable: ${cid.print}")
    }

  def count(
    cid: DataStore.CollectionId,
    directive: QueryDirective
  )(using ctx: ExecutionContext): Consequence[Int] =
    dataStore(cid).flatMap {
      case m: SearchableDataStore =>
        _with_calltree_c("io:datastore:count", _datastore_calltree_attributes("count", cid, directive, m), "io") {
          m.count(cid, directive)
        }
      case _ =>
        Consequence.dataStoreUnavailable(s"datastore is not countable: ${cid.print}")
    }

  def totalCountCapability(
    cid: DataStore.CollectionId
  ): Consequence[TotalCountCapability] =
    dataStore(cid).map {
      case m: SearchableDataStore =>
        m.totalCountCapability(cid)
      case _ =>
        TotalCountCapability.Unsupported
    }

  def entityMutationProviderCapabilities(
    cid: DataStore.CollectionId
  ): Consequence[EntityMutationProviderCapabilities] =
    dataStore(cid).map {
      case provider: EntityVersionedMutationDataStore =>
        provider.entityMutationProviderCapabilities
      case _ =>
        EntityMutationProviderCapabilities.empty
    }

  def selectEntityMutationPath(
    cid: DataStore.CollectionId,
    request: EntityMutationPathRequest
  ): Consequence[EntityMutationExecutionPath] =
    entityMutationProviderCapabilities(cid).flatMap(
      EntityMutationPathPlanner.selectC(_, request)
    )

  def mutateEntityDirect(
    plan: EntityDirectMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    _with_calltree_c(
      "space:datastore:entity-direct-mutation",
      _datastore_space_attributes(
        "entity-direct-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            EntityNativeMutationSupport
              .requireCapabilities(
                provider.entityMutationProviderCapabilities,
                "entity-direct-mutation",
                EntityMutationProviderFeature.DirectAlwaysWrite,
                plan.readbackRequirement
              )
              .flatMap(_ => provider.mutateEntityDirect(plan))
          case _ =>
            EntityNativeMutationSupport.unsupported(
              "entity-direct-mutation",
              EntityMutationProviderFeature.DirectAlwaysWrite
            )
        }
        admitted <-
          EntityMutationProviderResult.validateReadbackC(
            result,
            plan.readbackRequirement
          )
      } yield admitted
    }

  def compareAndSetEntity(
    plan: EntityCompareAndSetMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    _with_calltree_c(
      "space:datastore:entity-compare-and-set-mutation",
      _datastore_space_attributes(
        "entity-compare-and-set-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            EntityNativeMutationSupport
              .requireCapabilities(
                provider.entityMutationProviderCapabilities,
                "entity-compare-and-set-mutation",
                EntityMutationProviderFeature.OptimisticCompareAndSet,
                plan.readbackRequirement
              )
              .flatMap(_ => provider.compareAndSetEntity(plan))
          case _ =>
            EntityNativeMutationSupport.unsupported(
              "entity-compare-and-set-mutation",
              EntityMutationProviderFeature.OptimisticCompareAndSet
            )
        }
        admitted <-
          EntityMutationProviderResult.validateReadbackC(
            result,
            plan.readbackRequirement
          )
      } yield admitted
    }

  def compareAndDeleteEntity(
    plan: EntityCompareAndDeletePlan
  )(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] =
    EntityObservedDeleteSupport.validate(plan).flatMap { _ =>
      _with_calltree_c(
        "space:datastore:entity-compare-and-delete",
        _datastore_space_attributes("entity-compare-and-delete", plan.collection),
        "space"
      ) {
        for {
          datastore <- dataStore(plan.collection)
          result <- datastore match {
            case provider: EntityVersionedMutationDataStore =>
              for {
                _ <- EntityObservedDeleteSupport.requireCapabilities(
                  provider.entityMutationProviderCapabilities,
                  plan.sideEffects.nonEmpty
                )
                _ <- _ensure_same_provider(datastore, plan.sideEffects)
                response <- Consequence {
                  provider.compareAndDeleteEntity(plan)
                }.flatMap { outcome =>
                  if (outcome == null)
                    EntityObservedDeleteSupport.invalidResult[EntityCompareAndDeleteResult]
                  else
                    outcome
                }.recoverWith(EntityVersionedMutationFailure.normalizeProvider)
              } yield response
            case _ =>
              EntityNativeMutationSupport.unsupported(
                "entity-compare-and-delete",
                EntityMutationProviderFeature.OptimisticCompareAndDelete
              )
          }
          admitted <- EntityObservedDeleteSupport.validateResult(plan, result)
        } yield admitted
      }
    }

  def mutateVersionedEntity(
    plan: EntityVersionedMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    _with_calltree_c(
      "space:datastore:entity-versioned-mutation",
      _datastore_space_attributes(
        "entity-versioned-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        _ <- _ensure_same_provider(datastore, plan.sideEffects)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            provider.mutateVersionedEntity(plan)
          case _ =>
            Consequence.operationInvalid(
              "entity-versioned-mutation",
              Vector(
                Descriptor.Facet.Reason("unsupported-capability"),
                Descriptor.Facet.Capability(
                  "datastore.entity-versioned-mutation"
                )
              )
            )
        }
      } yield result
    }

  def conditionalTransition(
    plan: DataStoreConditionalTransitionPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[DataStoreConditionalTransitionResult] =
    EntityConditionalTransitionSupport.validate(plan).flatMap { admitted =>
      _with_calltree_c(
        "space:datastore:entity-conditional-transition",
        _datastore_space_attributes(
          "entity-conditional-transition",
          admitted.root.collection
        ),
        "space"
      ) {
        for {
          _ <- _ensure_conditional_component(admitted)
          providers <- _resolve_conditional_providers(admitted)
          rootprovider <- providers.headOption
            .map(Consequence.success)
            .getOrElse(
              Consequence.operationInvalid(
                "entity-conditional-transition",
                Vector(
                  Descriptor.Facet.Reason("missing-provider-domain"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-conditional-transition"
                  )
                )
              )
            )
          _ <- _ensure_conditional_provider_domain(rootprovider, providers)
          result <- rootprovider match {
            case provider: EntityConditionalTransitionDataStore =>
              provider
                .conditionalTransition(admitted)
                .recoverWith(
                  DataStoreConditionalTransitionFailure.normalizeProvider
                )
            case _ =>
              Consequence.operationInvalid(
                "entity-conditional-transition",
                Vector(
                  Descriptor.Facet.Reason("unsupported-capability"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-conditional-transition"
                  )
                )
              )
          }
        } yield result
      }
    }

  /** S12 entry point. Host assembly supplies an immutable call-scoped view;
    * plan scope alone cannot admit a provider call.
    */
  def conditionalMultiGuard(
    plan: DataStoreConditionalMultiGuardPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[DataStoreConditionalTransitionResult] =
    DataStoreConditionalMultiGuardPlan.validate(plan).flatMap { admitted =>
      _multi_guard_host match {
        case None => _multi_guard_rejection("host-admission-required")
        case Some(host) =>
          for {
            _ <- host.ensureLiveC()
            _ <- host.policy.validate(admitted)
            providers <- _multi_guard_provider_snapshot(admitted)
            _ <- if (providers.forall(_ eq host.provider)) Consequence.unit
              else _multi_guard_rejection("provider-domain-mismatch")
            result <- host.provider match {
              case capability: EntityConditionalMultiGuardDataStore =>
                capability.conditionalMultiGuardAdmitted(
                  new DataStoreSpace.MultiGuardInvocation(admitted, host))
              case _ => _multi_guard_rejection[DataStoreConditionalTransitionResult]("unsupported-capability")
            }
          } yield result
      }
    }

  /** Complete readback requires a separate opt-in; legacy raw capability never
    * commits on this route. Provider access remains within the admitted lease.
    */
  private[cncf] def conditionalMultiGuardVerified(
    plan: DataStoreConditionalMultiGuardPlan
  )(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    DataStoreConditionalMultiGuardPlan.validate(plan).flatMap { admitted =>
      _multi_guard_host match {
        case None => _multi_guard_rejection("host-admission-required")
        case Some(host) => for {
          _ <- host.ensureLiveC()
          _ <- host.policy.validate(admitted)
          providers <- _multi_guard_provider_snapshot(admitted)
          _ <- if (providers.forall(_ eq host.provider)) Consequence.unit
            else _multi_guard_rejection("provider-domain-mismatch")
          result <- host.provider match {
            case capability: EntityVerifiedMultiGuardDataStore =>
              capability.conditionalMultiGuardVerifiedAdmitted(new DataStoreSpace.MultiGuardInvocation(admitted, host))
            case _ => _multi_guard_rejection[DataStoreMultiGuardReadbackResult]("unsupported-verified-capability")
          }
        } yield result
      }
    }

  private def _multi_guard_provider_snapshot(
    plan: DataStoreConditionalMultiGuardPlan
  ): Consequence[Vector[DataStore]] = {
    val scoped = Option(_scoped_entity_store.get())
    val stores = _entity_stores
    val collections = (Vector(plan.transition.root.collection, plan.transition.successor.collection) ++
      plan.additionalGuards.map(_.collection) ++ plan.transition.sideEffects.map(_.collection)).distinct
    collections.foldLeft(Consequence.success(Vector.empty[DataStore])) { (acc, collection) =>
      for {
        providers <- acc
        provider <- Consequence.successOrServiceProviderByKeyNotFound(
          scoped.filter(_.isAccept(collection)).orElse(stores.find(_.isAccept(collection))))(
          "datastore", collection.print)
      } yield providers :+ provider
    }
  }

  private[cncf] def conditionalPreparedEventsVerified(transition: DataStoreConditionalTransitionPlan,
      preparation: ManagedEventPreparation)(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    conditionalPreparedEventsWithLocalIntentsVerified(transition, preparation, Vector.empty, Vector.empty)

  /** Internal native composition, not closing-program execution or UoW
    * activation. Local inputs grant no authority: the same captured host must
    * admit each additional guard and Create, in the identical provider domain.
    * All-retained/transient input requires its own composition, never fallback.
    */
  private[cncf] def conditionalPreparedEventsWithLocalIntentsVerified(transition: DataStoreConditionalTransitionPlan,
      preparation: ManagedEventPreparation, guards: Vector[DataStoreConditionalRoot],
      creates: Vector[EntityVersionedSideEffect.Create])(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    _multi_guard_host match {
      case Some(host) if preparation != null => for {
        _ <- preparation.validateHostC(host)
        plan <- preparation.planWithLocalIntentsC(transition, guards, creates)
        _ <- host.policy.validate(plan)
        providers <- _multi_guard_provider_snapshot(plan)
        _ <- if (providers.forall(_ eq host.provider)) Consequence.unit
          else _multi_guard_rejection("provider-domain-mismatch")
        result <- host.provider match {
          case capability: EntityVerifiedMultiGuardDataStore if capability.supportsPreparedEvents =>
            capability.conditionalMultiGuardVerifiedAdmitted(new DataStoreSpace.MultiGuardInvocation(plan, host, Some(preparation)))
          case _ => _multi_guard_rejection[DataStoreMultiGuardReadbackResult]("unsupported-prepared-event-capability")
        }
      } yield result
      case _ => Consequence.configurationInvalid("Prepared events require their original live host")
    }

  private[cncf] def conditionalRetainedEventsVerified(transition: DataStoreConditionalTransitionPlan,
      preparation: ManagedEventPreparation)(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    _conditional_retained_events_c(transition, preparation, None)

  private def _conditional_retained_events_c(transition: DataStoreConditionalTransitionPlan,
      preparation: ManagedEventPreparation, local: Option[DataStoreLocalCompletionPlan])
      (using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    _multi_guard_host match {
      case Some(host) if preparation != null =>
        val invocation = new DataStoreSpace.RetainedEventInvocation(transition, preparation, host, local)
        invocation.validateProviderC(host.provider).flatMap { _ => for {
          targets <- invocation.targetsC
          observed <- preparation.recordsC
        } yield targets ++ observed.map(r => r._1.collection -> r._1.entryId) }.flatMap { targets =>
          targets.map(_._1).distinct.foldLeft(Consequence.unit) { (acc, collection) => acc.flatMap { _ =>
            dataStore(collection).flatMap { actual =>
              if (actual eq host.provider) Consequence.unit else _multi_guard_rejection("provider-domain-mismatch")
            }
          }}
        }.flatMap { _ =>
          host.provider match {
            case capability: EntityRetainedEventDataStore => capability.retainedEventsVerified(invocation)
            case _ => _multi_guard_rejection("unsupported-retained-event-capability")
          }
        }
      case _ => Consequence.configurationInvalid("Retained completion requires its live original host")
    }

  /** Ordinary input is classified only by the captured service. No durable
    * observation/cursor or caller-supplied selection is admitted on this route.
    */
  private[cncf] def conditionalTransientEventsVerified(transition: DataStoreConditionalTransitionPlan,
      records: Vector[org.goldenport.cncf.event.EventRecord])(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    _multi_guard_host match {
      case Some(host) => host.eventStore match {
        case Some(store) => for {
          _ <- host.ensureLiveC()
          selection <- org.goldenport.cncf.workflow.WorkflowEventSelection.prepareFromStoreC(store, records)
          result <- _conditional_transient_selection_c(transition, selection, host)
        } yield result
        case _ => Consequence.configurationInvalid("Transient completion requires the captured EventStore")
      }
      case _ => Consequence.configurationInvalid("Transient completion requires its live host")
    }

  private def _conditional_transient_selection_c(transition: DataStoreConditionalTransitionPlan,
      selection: org.goldenport.cncf.workflow.WorkflowEventSelection,
      host: DataStoreSpace.MultiGuardHostAdmission, local: Option[DataStoreLocalCompletionPlan] = None)
      (using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] = for {
    _ <- if (selection.persistent.isEmpty) Consequence.unit
      else Consequence.configurationInvalid("Transient completion cannot admit durable input")
    inv = new DataStoreSpace.TransientEventInvocation(transition, host, local)
    _ <- inv.validateProviderC(host.provider)
    _ <- inv.targets.map(_._1).distinct.foldLeft(Consequence.unit) { (acc, collection) => acc.flatMap { _ =>
      dataStore(collection).flatMap { actual =>
        if (actual eq host.provider) Consequence.unit else _multi_guard_rejection("provider-domain-mismatch")
      }
    }}
    result <- host.provider match {
      case capability: EntityTransientEventDataStore => capability.transientEventsVerified(inv)
      case _ => _multi_guard_rejection[DataStoreMultiGuardReadbackResult]("unsupported-transient-event-capability")
    }
  } yield result

  /** One ordinary-input host entry. Branch selection is mechanical, never a
    * caller/skill choice. This storage-only result grants no dispatch/UoW permit.
    */
  private[cncf] def conditionalHostEventsVerified(transition: DataStoreConditionalTransitionPlan,
      records: Vector[org.goldenport.cncf.event.EventRecord])(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    conditionalHostEventsWithLocalIntentsVerified(transition, records, Vector.empty, Vector.empty)

  /** The host still selects exactly one route from ordinary events. Local
    * intents cannot introduce EventStore writes or manufacture host admission.
    */
  private[cncf] def conditionalHostEventsWithLocalIntentsVerified(transition: DataStoreConditionalTransitionPlan,
      records: Vector[org.goldenport.cncf.event.EventRecord], guards: Vector[DataStoreConditionalRoot],
      creates: Vector[EntityVersionedSideEffect.Create])(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    conditionalHostEventsFinalizedC(transition, records, guards, creates).map(_._1)

  /** Finalized projections are data, not a delivery permit. Only the managed
    * UoW may dispatch them after its callbacks and resource completion.
    */
  private[cncf] def conditionalHostEventsFinalizedC(transition: DataStoreConditionalTransitionPlan,
      records: Vector[org.goldenport.cncf.event.EventRecord], guards: Vector[DataStoreConditionalRoot],
      creates: Vector[EntityVersionedSideEffect.Create])(using ExecutionContext):
      Consequence[(DataStoreMultiGuardReadbackResult, Vector[org.goldenport.cncf.event.EventRecord])] =
    _multi_guard_host match {
      case Some(host) => host.eventStore match {
        case Some(store) => for {
          _ <- host.ensureLiveC()
          _ <- host.policy.validateRetainedC(transition) // Caller may supply only the effect-free authorized pair.
          local <- if (guards == Vector.empty && creates == Vector.empty) Consequence.success(None)
            else for {
              plan <- DataStoreLocalCompletionPlan.createC(transition, guards, creates)
              _ <- host.validateLocalC(plan)
            } yield Some(plan)
          selection <- org.goldenport.cncf.workflow.WorkflowEventSelection.prepareFromStoreC(store, records)
          result <- if (selection.persistent.isEmpty)
            _conditional_transient_selection_c(transition, selection, host, local).map(_ -> selection.events)
            else host.observationPolicy match {
              case Some(policy) => for {
                request <- org.goldenport.cncf.event.ManagedEventObservationRequest.createC(selection, policy)
                snapshot <- _observe_declared_targets_c(request.initialTargets, Some(request), Some(store))
                preparation <- snapshot.preparationC
                classified <- preparation.classificationC
                committed <- if (classified.newDurableCount > 0)
                  conditionalPreparedEventsWithLocalIntentsVerified(transition, preparation, guards, creates)
                  else _conditional_retained_events_c(transition, preparation, local)
                finalized <- committed match {
                  case verified: DataStoreMultiGuardReadbackResult.Verified =>
                    classified.finalizedReadbackC(selection.events, policy.eventCollection, verified.records)
                  case _ => Consequence.success(Vector.empty)
                }
              } yield committed -> finalized
              case None => Consequence.configurationInvalid("Durable completion requires observation read admission")
            }
        } yield result
        case _ => Consequence.configurationInvalid("Host completion requires the captured EventStore")
      }
      case None => Consequence.configurationInvalid("Host completion requires its live host")
    }

  /** Separate read admission; write-side collection permission is insufficient. */
  private[cncf] def observeDeclaredTargetsC(targets: Vector[DataStoreObservationTarget])
      (using ExecutionContext): Consequence[DataStoreObservationSnapshot] =
    _observe_declared_targets_c(targets, None)

  private[cncf] def observeSelectedEventsC(selection: org.goldenport.cncf.workflow.WorkflowEventSelection)
      (using ExecutionContext): Consequence[DataStoreObservationSnapshot] =
    _multi_guard_host.flatMap(_.observationPolicy) match {
      case Some(policy) => org.goldenport.cncf.event.ManagedEventObservationRequest.createC(selection, policy)
        .flatMap(request => _observe_declared_targets_c(request.initialTargets, Some(request)))
      case None => Consequence.configurationInvalid("Event observation read admission is required")
    }

  /** Ordinary input only: selection comes from the installed host service.
    * Pure selected-input observations above never gain this provenance.
    */
  private[cncf] def observeHostEventsC(records: Vector[org.goldenport.cncf.event.EventRecord])
      (using ExecutionContext): Consequence[DataStoreObservationSnapshot] =
    _multi_guard_host match {
      case Some(host) => (host.eventStore, host.observationPolicy) match {
        case (Some(store), Some(policy)) => for {
          _ <- host.ensureLiveC()
          selection <- org.goldenport.cncf.workflow.WorkflowEventSelection.prepareFromStoreC(store, records)
          request <- org.goldenport.cncf.event.ManagedEventObservationRequest.createC(selection, policy)
          result <- _observe_declared_targets_c(request.initialTargets, Some(request), Some(store))
        } yield result
        case _ => Consequence.configurationInvalid("Installed EventStore/read admission is required")
      }
      case None => Consequence.configurationInvalid("Event observation host is required")
    }

  private def _observe_declared_targets_c(targets: Vector[DataStoreObservationTarget],
      request: Option[org.goldenport.cncf.event.ManagedEventObservationRequest],
      selectedFrom: Option[org.goldenport.cncf.event.EventStore] = None)
      (using ExecutionContext): Consequence[DataStoreObservationSnapshot] =
    _multi_guard_host match {
      case Some(host) => host.observationPolicy match {
        case Some(policy) => for {
          _ <- host.ensureLiveC()
          _ <- policy.validateC(targets)
          _ <- targets.foldLeft(Consequence.unit) { (acc, target) =>
            acc.flatMap(_ => dataStore(target.collection).flatMap { provider =>
              if (provider eq host.provider) Consequence.unit
              else Consequence.configurationInvalid("Observation provider differs")
            })
          }
          result <- host.provider match {
            case provider: EntityObservationDataStore =>
              provider.observeAdmittedC(new DataStoreSpace.ObservationInvocation(targets, policy, host, request, selectedFrom))
            case _ => Consequence.configurationInvalid("Observation native capability is unavailable")
          }
        } yield result
        case None => Consequence.configurationInvalid("Observation read admission is required")
      }
      case None => Consequence.configurationInvalid("Observation host admission is required")
    }

  private def _multi_guard_rejection[A](reason: String): Consequence[A] =
    Consequence.operationInvalid(
      "entity-conditional-multi-guard",
      Vector(Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Capability("datastore.entity-conditional-multi-guard")))

  def inject(
    cid: DataStore.CollectionId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    _with_calltree("space:datastore:inject", _datastore_space_attributes("inject", cid)) {
      val entryid = _entry_id(record, cid)
      dataStore(cid).flatMap(_.create(cid, entryid, record))
    }
  }

  def inject(
    seed: DataStoreSpace.Seed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:datastore:inject-seed", Map("space" -> "datastore", "operation" -> "inject-seed", "entry_count" -> seed.entries.size.toString)) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap(_ => inject(entry.collection, entry.record))
      }
    }

  def importSeed(
    seed: DataStoreSeed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:datastore:import-seed", Map("space" -> "datastore", "operation" -> "import-seed", "entry_count" -> seed.entries.size.toString)) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap { _ =>
          val entryid = _entry_id(entry.record, entry.collection)
          dataStore(entry.collection).flatMap(_.create(entry.collection, entryid, entry.record)).recoverWith {
            case _ =>
              dataStore(entry.collection).flatMap(_.save(entry.collection, entryid, entry.record))
          }
        }
      }
    }

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
          case failure: Consequence.Failure[?] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
          case other =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _with_calltree_c[A](
    label: String,
    attributes: Map[String, String],
    calltreekind: String = "space"
  )(
    body: => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> calltreekind))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
            success
          case failure: Consequence.Failure[A] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
            failure
        }
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _datastore_space_attributes(
    operation: String,
    cid: DataStore.CollectionId
  ): Map[String, String] =
    Map(
      "space" -> "datastore",
      "operation" -> operation,
      "collection" -> cid.print
    )

  private def _ensure_same_provider(
    rootprovider: DataStore,
    effects: Vector[EntityVersionedSideEffect]
  ): Consequence[Unit] =
    effects
      .map(_.collection)
      .distinct
      .foldLeft(Consequence.unit) { (z, collection) =>
        z.flatMap { _ =>
          dataStore(collection).flatMap { provider =>
            if (provider eq rootprovider)
              Consequence.unit
            else
              Consequence.operationInvalid(
                "entity-versioned-mutation",
                Vector(
                  Descriptor.Facet.Reason("provider-domain-mismatch"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-versioned-mutation"
                  )
                )
              )
          }
        }
      }

  private def _ensure_conditional_component(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Unit] =
    (plan.root.collection, plan.successor.collection) match {
      case (
          DataStore.CollectionId.EntityStore(_),
          DataStore.CollectionId.EntityStore(_)
          ) if plan.root.componentOwner == plan.successor.componentOwner =>
        Consequence.unit
      case (
          DataStore.CollectionId.EntityStore(_),
          DataStore.CollectionId.EntityStore(_)
          ) =>
        _conditional_admission_failure("component-owner-mismatch")
      case _ =>
        _conditional_admission_failure("entity-collection-required")
    }

  private def _resolve_conditional_providers(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Vector[DataStore]] = {
    val collections =
      plan.sideEffects
        .map(_.collection)
        .appended(plan.successor.collection)
        .prepended(plan.root.collection)
        .distinct
    collections.foldLeft(Consequence.success(Vector.empty[DataStore])) {
      (result, collection) =>
        for {
          providers <- result
          provider <- dataStore(collection)
        } yield providers :+ provider
    }
  }

  private def _ensure_conditional_provider_domain(
    rootprovider: DataStore,
    providers: Vector[DataStore]
  ): Consequence[Unit] =
    if (providers.forall(_ eq rootprovider))
      Consequence.unit
    else
      _conditional_admission_failure("provider-domain-mismatch")

  private def _conditional_admission_failure(
    reason: String
  ): Consequence[Unit] =
    Consequence.operationInvalid(
      "entity-conditional-transition",
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Capability(
          "datastore.entity-conditional-transition"
        )
      )
    )

  private def _entry_id(
    record: Record,
    cid: DataStore.CollectionId
  ): DataStore.EntryId =
    record.getAny("id") match {
      case Some(m: UniversalId) => DataStore.StringEntryId(m.print)
      case Some(s: String) => DataStore.StringEntryId(s)
      case Some(v) => DataStore.StringEntryId(v.toString)
      case None =>
        val seq = s"n${_inject_sequence.incrementAndGet()}"
        DataStore.DataStoreEntryId("sys", seq, cid.collectionName)
    }

  private def _datastore_calltree_attributes(
    kind: String,
    cid: DataStore.CollectionId,
    directive: QueryDirective,
    store: SearchableDataStore
  )(using ctx: ExecutionContext): Map[String, String] = {
    val base = Map(
      "collection" -> cid.print,
      "source" -> "data-store",
      "datastore" -> store.getClass.getSimpleName.stripSuffix("$"),
      "operation" -> kind,
      "real_io" -> "true",
      "query" -> _calltree_query_summary_json(directive),
      "limit" -> directive.limit.toString,
      "offset" -> directive.offset.toString
    )
    if (_calltree_sql_enabled) {
      store match {
        case m: SqlDataStore =>
          val sql =
            if (kind == "count")
              m.debugCountSql(cid, directive)
            else
              m.debugSearchSql(cid, directive)
          base ++ Map(
            "sql" -> _truncate_calltree_text(_redact_sensitive_text(sql.sql), 8000),
            "sql_params" -> _truncate_calltree_text(_redact_sensitive_text(sql.params.map(_.toString).mkString("[", ", ", "]")), 4000)
          )
        case _ =>
          base ++ Map("sql" -> "unavailable")
      }
    } else {
      base ++ Map("sql" -> "disabled")
    }
  }

  private def _calltree_sql_enabled(
    using ctx: ExecutionContext
  ): Boolean = {
    val keys = Vector(
      "textus.debug.calltree.sql",
      "textus.runtime.debug.calltree.sql",
      "cncf.debug.calltree.sql",
      "cncf.runtime.debug.calltree.sql",
      "x-textus-debug-calltree-sql"
    )
    keys.exists { key =>
      ctx.runtime.resolvedParameters.get(key).exists { param =>
        _truthy(ResolvedParameter.format_value(param.value))
      }
    }
  }

  private def _truthy(
    value: String
  ): Boolean =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => true
      case _ => false
    }

  private def _truncate_calltree_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def _calltree_query_summary_json(
    directive: QueryDirective
  ): String =
    _truncate_calltree_text(RecordEncoder.json(CallTreeValueSummary.recordSummary(Record.dataAuto(
      "query" -> _redact_sensitive_text(directive.query.toString),
      "limit" -> directive.limit,
      "offset" -> directive.offset
    ), includeInline = true)), 4000)

  private def _redact_sensitive_text(value: String): String = {
    val sensitive = "(?i)(password|passwd|secret|token|session|authorization|cookie|credential|api[_-]?key)"
    val jsonlike = (s"""("?$sensitive"?\\s*[:=]\\s*)("[^"]*"|'[^']*'|[^,}\\]\\s]+)""").r
    val formlike = (s"""($sensitive)(\\s*[=:]\\s*)([^&\\s,}\\]]+)""").r
    val jsonredacted = jsonlike.replaceAllIn(value, m => s"${m.group(1)}***")
    formlike.replaceAllIn(jsonredacted, m => s"${m.group(1)}${m.group(2)}***")
  }
}

object DataStoreSpace {
  private[datastore] final class TransientEventInvocation private[DataStoreSpace] (
    val transition: DataStoreConditionalTransitionPlan,
    private val host: MultiGuardHostAdmission,
    private val local: Option[DataStoreLocalCompletionPlan] = None
  ) {
    def nativeTransition: DataStoreConditionalTransitionPlan = local.map(_.transition).getOrElse(transition)
    def localGuards: Vector[DataStoreConditionalRoot] = local.map(_.guards).getOrElse(Vector.empty)
    def validateProviderC(provider: DataStore): Consequence[Unit] = for {
      _ <- host.ensureLiveC()
      _ <- if (provider eq host.provider) Consequence.unit else Consequence.configurationInvalid("Transient provider differs")
      _ <- host.policy.validateRetainedC(transition) // Same authorized, effect-free Workflow pair contract.
      _ <- local.fold(Consequence.unit)(host.validateLocalC)
    } yield ()
    def targets: Vector[(DataStore.CollectionId, DataStore.EntryId)] = local.map(_.targets).getOrElse(Vector(
      transition.root.collection -> transition.root.entryId, transition.successor.collection -> transition.successor.entryId))
    def validateReadbackC(records: Vector[DataStoreMultiGuardRecord]): Consequence[Unit] =
      local.fold(DataStoreMultiGuardReadback.validateTransitionC(transition, records))(_.validateReadbackC(records))
  }

  private[datastore] final class RetainedEventInvocation private[DataStoreSpace] (
    val transition: DataStoreConditionalTransitionPlan,
    val preparation: ManagedEventPreparation,
    private val host: MultiGuardHostAdmission,
    private val local: Option[DataStoreLocalCompletionPlan] = None
  ) {
    def nativeTransition: DataStoreConditionalTransitionPlan = local.map(_.transition).getOrElse(transition)
    def localGuards: Vector[DataStoreConditionalRoot] = local.map(_.guards).getOrElse(Vector.empty)
    private def writeTargets: Vector[(DataStore.CollectionId, DataStore.EntryId)] = local.map(_.targets).getOrElse(Vector(
      transition.root.collection -> transition.root.entryId, transition.successor.collection -> transition.successor.entryId))
    def validateProviderC(provider: DataStore): Consequence[Unit] = for {
      _ <- host.ensureLiveC()
      _ <- if (provider eq host.provider) Consequence.unit else Consequence.configurationInvalid("Retained provider differs")
      _ <- host.policy.validateRetainedC(transition)
      _ <- local.fold(Consequence.unit)(host.validateLocalC)
      _ <- preparation.validateHostC(host)
      classified <- preparation.classificationC
      _ <- if (classified.newDurableCount == 0) Consequence.unit
        else Consequence.configurationInvalid("Retained route cannot publish new durable facts")
    } yield ()
    def validationRecordsC: Consequence[Vector[DataStoreMultiGuardRecord]] = preparation.recordsC.map(_.collect {
      case (target, Some(record)) => DataStoreMultiGuardRecord(target.collection, target.entryId, record)
    })
    def targetsC: Consequence[Vector[(DataStore.CollectionId, DataStore.EntryId)]] = validationRecordsC.map { rows =>
      writeTargets ++ rows.map(r => r.collection -> r.entryId)
    }
    def validateReadbackC(records: Vector[DataStoreMultiGuardRecord]): Consequence[Unit] = for {
      expected <- validationRecordsC
      targets <- targetsC
      _ <- if (records != null && records.forall(r => r != null && r.record != null) &&
        records.map(r => r.collection -> r.entryId) == targets) Consequence.unit
        else Consequence.configurationInvalid("Retained completion readback coverage differs")
      _ <- local.fold(DataStoreMultiGuardReadback.validateTransitionC(transition, records.take(2)))(
        _.validateReadbackC(records.take(writeTargets.size)))
      _ <- if (records.drop(writeTargets.size).zip(expected).forall { case (actual, frozen) =>
        actual.record.fields.map(_.key).distinct.size == actual.record.fields.size &&
          actual.record.fields.sortBy(_.key) == frozen.record.fields.sortBy(_.key)
      }) Consequence.unit else Consequence.stateConflict("Retained completion readback differs")
    } yield ()
  }
  private[datastore] final class ObservationInvocation private[DataStoreSpace] (
    val targets: Vector[DataStoreObservationTarget],
    private[datastore] val policy: DataStoreObservationPolicy,
    private val host: MultiGuardHostAdmission,
    val eventRequest: Option[org.goldenport.cncf.event.ManagedEventObservationRequest],
    private val selectedFrom: Option[org.goldenport.cncf.event.EventStore]
  ) {
    def validateCapturedSelectionC: Consequence[Unit] = selectedFrom match {
      case Some(service) => validateHostSelectionC(service)
      case None => Consequence.configurationInvalid("Observation has no captured host selection")
    }
    def validateHostC(expected: MultiGuardHostAdmission): Consequence[Unit] =
      validateCapturedSelectionC.flatMap { _ =>
        if (host eq expected) Consequence.unit else Consequence.configurationInvalid("Preparation belongs to another host view")
      }
    def validateHostSelectionC(expected: org.goldenport.cncf.event.EventStore): Consequence[Unit] =
      host.ensureLiveC().flatMap { _ =>
        if (expected != null && eventRequest.nonEmpty && selectedFrom.exists(_ eq expected) &&
            host.eventStore.exists(_ eq expected)) Consequence.unit
        else Consequence.configurationInvalid("Observation was not selected by this installed EventStore")
      }
    def validateTargetsC(expanded: Vector[DataStoreObservationTarget]): Consequence[Unit] =
      host.ensureLiveC().flatMap(_ => policy.validateC(expanded))
    def validateProviderC(provider: DataStore): Consequence[Unit] =
      host.ensureLiveC().flatMap { _ =>
        if (provider eq host.provider) policy.validateC(targets)
        else Consequence.configurationInvalid("Observation belongs to another provider")
      }
  }
  /** Opaque call-scoped invocation; neither owner labels nor a plan can
    * construct it outside this DataStoreSpace assembly boundary.
    */
  private[datastore] final class MultiGuardInvocation private[DataStoreSpace] (
    val plan: DataStoreConditionalMultiGuardPlan,
    private val host: MultiGuardHostAdmission,
    val preparation: Option[ManagedEventPreparation] = None
  ) {
    def validateProviderC(provider: DataStore): Consequence[Unit] =
      host.ensureLiveC().flatMap { _ =>
        if (provider eq host.provider) host.policy.validate(plan).flatMap(_ =>
          preparation.fold(Consequence.unit)(_.validateHostC(host)))
        else Consequence.configurationInvalid("Multi-guard invocation belongs to another provider")
      }
    def retainedC: Consequence[Vector[DataStoreMultiGuardRecord]] =
      preparation.fold(Consequence.success(Vector.empty[DataStoreMultiGuardRecord]))(_.retainedRecordsC)
    def readbackTargetsC: Consequence[Vector[(DataStore.CollectionId, DataStore.EntryId)]] =
      retainedC.map(rows => DataStoreMultiGuardReadback.targets(plan) ++ rows.map(r => r.collection -> r.entryId))
    def validateReadbackC(records: Vector[DataStoreMultiGuardRecord]): Consequence[Unit] = for {
      retained <- retainedC
      targets <- readbackTargetsC
      _ <- if (records != null && records.map(r => r.collection -> r.entryId) == targets) Consequence.unit
        else Consequence.configurationInvalid("Prepared complete readback coverage differs")
      _ <- DataStoreMultiGuardReadback.validateC(plan, records.take(targets.size - retained.size))
      _ <- if (records.takeRight(retained.size).zip(retained).forall { case (actual, expected) =>
        actual.record != null && actual.record.fields.map(_.key).distinct.size == actual.record.fields.size &&
          actual.record.fields.sortBy(_.key) == expected.record.fields.sortBy(_.key)
      }) Consequence.unit else Consequence.stateConflict("Retained event readback differs")
    } yield ()
  }

  private[datastore] final class MultiGuardHostAdmission(
    val policy: DataStoreConditionalMultiGuardHostPolicy,
    val provider: DataStore,
    lease: SystemNodeResourceLease,
    thread: Thread,
    val observationPolicy: Option[DataStoreObservationPolicy],
    val eventStore: Option[org.goldenport.cncf.event.EventStore]
  ) {
    def validateLocalC(plan: DataStoreLocalCompletionPlan): Consequence[Unit] = ensureLiveC().flatMap { _ =>
      observationPolicy match {
        case Some(read) => plan.validatePolicyC(policy, Vector(read.cursor.collection, read.eventCollection, read.indexCollection))
        case None => Consequence.configurationInvalid("Local completion requires captured EventStore domain boundaries")
      }
    }
    def ensureLiveC(): Consequence[Unit] =
      if (lease.isReleased || lease.isRevoked || (Thread.currentThread() ne thread))
        Consequence.stateInvalid("Multi-guard host view requires its live lease and issuing thread")
      else Consequence.unit
  }

  /** Host-only assembly: authority is tied to one view, provider and lease. */
  private[cncf] def multiGuardHostViewC(
    policy: DataStoreConditionalMultiGuardHostPolicy,
    provider: DataStore,
    lease: SystemNodeResourceLease,
    observationPolicy: Option[DataStoreObservationPolicy] = None,
    eventStore: Option[org.goldenport.cncf.event.EventStore] = None
  ): Consequence[DataStoreSpace] =
    if (policy == null || provider == null || lease == null)
      Consequence.configurationInvalid("Multi-guard host view is incomplete")
    else {
      val admission = new MultiGuardHostAdmission(policy, provider, lease, Thread.currentThread(), observationPolicy, eventStore)
      admission.ensureLiveC().map(_ =>
        new DataStoreSpace(Some(admission)).useDataStore(provider))
    }

  final case class SeedEntry(
    collection: DataStore.CollectionId,
    record: Record
  )

  final case class Seed(
    entries: Vector[SeedEntry]
  )

  def default(): DataStoreSpace =
    new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())

  def create(conf: ResolvedConfiguration): DataStoreSpace = {
    val dss = new DataStoreSpace()
    val datastorekind = _get_string(
      conf,
      "textus.datastore.kind",
      "cncf.datastore.kind"
    ).map(_.trim.toLowerCase(java.util.Locale.ROOT))
    val sqlitepath = _get_string(
      conf,
      "textus.datastore.path",
      "cncf.datastore.path"
    ).orElse(_get_string(
      conf,
      "textus.datastore.sqlite.path",
      "cncf.datastore.sqlite.path"
    ))
    val sqlnormalizecolumns =
      _get_string(
        conf,
        "textus.datastore.sql.normalize-column-names",
        "cncf.datastore.sql.normalize-column-names"
      ).orElse(_get_string(
        conf,
        "textus.datastore.sqlite.normalize-column-names",
        "cncf.datastore.sqlite.normalize-column-names"
      ))
        .exists(_.trim.equalsIgnoreCase("true"))
    if (datastorekind.exists(k => k == "local" || k == "sqlite") && sqlitepath.isEmpty)
      Consequence.configurationInvalid(
        "textus.datastore.path is required when textus.datastore.kind is local or sqlite"
      ).RAISEC
    dss._configured_default = Some(() => datastorekind match {
      case Some("in-memory" | "inmemory" | "memory") =>
        DataStore.inMemorySearchable()
      case Some("local" | "sqlite") =>
        sqlitepath.map { path =>
          _ensure_parent(path)
          SqlDataStore.sqlite(
            path,
            config = SqlDataStore.Config(
              normalizeColumnNames = sqlnormalizecolumns
            )
          )
        }.getOrElse(
          Consequence.configurationInvalid(
            "textus.datastore.path is required when textus.datastore.kind is local or sqlite"
          ).RAISEC
        )
      case _ =>
        sqlitepath match {
          case Some(path) =>
            _ensure_parent(path)
            SqlDataStore.sqlite(
              path,
              config = SqlDataStore.Config(
                normalizeColumnNames = sqlnormalizecolumns
              )
            )
          case None => DataStore.inMemorySearchable()
        }
    })
    dss
  }

  private def _ensure_parent(path: String): Unit =
    Option(Paths.get(path).toAbsolutePath.normalize.getParent).foreach(Files.createDirectories(_))

  private def _get_string(
    conf: ResolvedConfiguration,
    primary: String,
    compatibility: String
  ): Option[String] =
    ConfigurationAccess.getString(conf, primary).orElse(ConfigurationAccess.getString(conf, compatibility))
}
