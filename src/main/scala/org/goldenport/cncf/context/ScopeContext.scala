package org.goldenport.cncf.context

import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.context.DataStoreContext
import org.goldenport.cncf.context.EntityStoreContext
import org.goldenport.cncf.context.EntitySpaceContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.admission.ScopedConcurrencyAdmission
import org.goldenport.cncf.processexecution.{ProcessExecutionAdmission, ProcessExecutionDriver}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.operation.evaluation.OperationEvaluationResolver
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSink, CorpusEvaluationSinkSocket, ExperimentEvaluationSink, ExperimentEvaluationSinkSocket}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 20, 2026
 *  version Feb. 25, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
enum ScopeKind {
  case Runtime
  case Subsystem
  case Component
  case Service
  case Action
}

abstract class ScopeContext() extends ObservationDsl with ScopeContext.Core.Holder {

  def core: ScopeContext.Core

  def workAreaSpace: WorkAreaSpace = parent match {
    case Some(s) => s.workAreaSpace
    case None => GlobalContext.globalContext.workAreaSpace
  }

  def httpDriver: HttpDriver = {
    core.httpDriverOption match {
      case Some(driver) =>
        driver
      case None =>
        parent match {
          case Some(p) => p.httpDriver
          case None => throw new IllegalStateException("ScopeContext has no HttpDriver")
        }
    }
  }

  /**
   * Runtime-owned Process Execution driver. A missing driver deliberately has
   * no ambient-host fallback; the UnitOfWork interpreter returns its
   * structured unavailable-service failure.
   */
  def processExecutionDriverOption: Option[ProcessExecutionDriver] =
    core.processExecutionDriverOption orElse parent.flatMap(_.processExecutionDriverOption)

  /** Runtime-owned capability/program admission for Process Execution. */
  def processExecutionAdmissionOption: Option[ProcessExecutionAdmission] =
    core.processExecutionAdmissionOption orElse parent.flatMap(_.processExecutionAdmissionOption)

  /** Runtime-owned, per-logical-scope concurrency admission. */
  def scopedConcurrencyAdmissionOption: Option[ScopedConcurrencyAdmission] =
    core.scopedConcurrencyAdmissionOption orElse parent.flatMap(_.scopedConcurrencyAdmissionOption)

  def operationEvaluationResolverOption: Option[OperationEvaluationResolver] =
    core.operationEvaluationResolverOption orElse parent.flatMap(_.operationEvaluationResolverOption)

  def operationEvaluationResolver: OperationEvaluationResolver =
    operationEvaluationResolverOption.getOrElse(OperationEvaluationResolver.disabled)

  def corpusEvaluationSinkOption: Option[CorpusEvaluationSink] =
    _local_corpus_evaluation_sink orElse parent.flatMap(_.corpusEvaluationSinkOption)

  def corpusEvaluationSink: CorpusEvaluationSink =
    corpusEvaluationSinkOption.getOrElse(CorpusEvaluationSink.disabled)

  def experimentEvaluationSinkOption: Option[ExperimentEvaluationSink] =
    _local_experiment_evaluation_sink orElse parent.flatMap(_.experimentEvaluationSinkOption)

  def experimentEvaluationSink: ExperimentEvaluationSink =
    experimentEvaluationSinkOption.getOrElse(ExperimentEvaluationSink.disabled)

  def formatPing: String =
    parent match {
      case Some(p) => p.formatPing
      case None => GlobalRuntimeContext.defaultPing
    }

  def createChildScope(kind: ScopeKind, name: String): ScopeContext =
    ScopeContext(
      kind = kind,
      name = name,
      parent = Some(this),
      observabilityContext = observabilityContext.createChild(this, kind, name)
    )

  protected def observability_Context: ObservabilityContext =
    observabilityContext

  override protected def scope_context: Option[ScopeContext] =
    Some(this)

  private def _local_corpus_evaluation_sink: Option[CorpusEvaluationSink] =
    this match {
      case context: Component.Context =>
        context.component match {
          case socket: CorpusEvaluationSinkSocket if socket.isSpiInstalled => Some(socket.corpusEvaluationSink)
          case _ => None
        }
      case _ => None
    }

  private def _local_experiment_evaluation_sink: Option[ExperimentEvaluationSink] =
    this match {
      case context: Component.Context =>
        context.component match {
          case socket: ExperimentEvaluationSinkSocket if socket.isSpiInstalled => Some(socket.experimentEvaluationSink)
          case _ => None
        }
      case _ => None
    }
}

object ScopeContext {
  final case class Core(
    kind: ScopeKind,
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver],
    datastore: Option[DataStoreContext] = None,
    entitystore: Option[EntityStoreContext] = None,
    entityspace: Option[EntitySpaceContext] = None,
    aggregateInternalRead: Boolean = false,
    processExecutionDriverOption: Option[ProcessExecutionDriver] = None,
    processExecutionAdmissionOption: Option[ProcessExecutionAdmission] = None,
    scopedConcurrencyAdmissionOption: Option[ScopedConcurrencyAdmission] = None,
    operationEvaluationResolverOption: Option[OperationEvaluationResolver] = None
  )
  object Core {
    trait Holder {
      def core: Core

      def kind: ScopeKind = core.kind
      def name: String = core.name
      def parent: Option[ScopeContext] = core.parent
      def observabilityContext: ObservabilityContext = core.observabilityContext
      def isAggregateInternalRead: Boolean =
        core.aggregateInternalRead || parent.exists(_.isAggregateInternalRead)

      def dataStoreSpace: DataStoreSpace =
        core.datastore.map(_.dataStoreSpace) orElse
          parent.map(_.dataStoreSpace) getOrElse
          ScopeContext._default_data_store_space
      def entityStoreSpace: EntityStoreSpace =
        core.entitystore.map(_.entityStoreSpace) orElse
          parent.map(_.entityStoreSpace) getOrElse
          ScopeContext._default_entity_store_space
      def entitySpace: EntitySpace =
        core.entityspace.map(_.entitySpace) orElse
          parent.map(_.entitySpace) getOrElse
          ScopeContext._default_entity_space
    }
  }

  final case class Instance(core: ScopeContext.Core) extends ScopeContext() {
  }

  def withAggregateInternalRead(
    scope: ScopeContext,
    enabled: Boolean
  ): ScopeContext =
    scope match {
      case Instance(core) => Instance(core.copy(aggregateInternalRead = enabled))
      case other => Instance(other.core.copy(aggregateInternalRead = enabled))
    }

  def apply(
    kind: ScopeKind,
    name: String,
    parent: Option[ScopeContext],
    @deprecatedName("observabilityContext", "0.5.1") observabilitycontext: ObservabilityContext,
    @deprecatedName("httpDriverOption", "0.5.1") httpdriveroption: Option[HttpDriver] = None,
    @deprecatedName("processExecutionDriverOption", "0.5.1")
    processexecutiondriveroption: Option[ProcessExecutionDriver] = None,
    @deprecatedName("processExecutionAdmissionOption", "0.5.1")
    processexecutionadmissionoption: Option[ProcessExecutionAdmission] = None,
    @deprecatedName("scopedConcurrencyAdmissionOption", "0.5.1")
    scopedconcurrencyadmissionoption: Option[ScopedConcurrencyAdmission] = None,
    @deprecatedName("operationEvaluationResolverOption", "0.5.1")
    operationevaluationresolveroption: Option[OperationEvaluationResolver] = None
  ): ScopeContext = {
    Instance(
      ScopeContext.Core(
        kind = kind,
        name = name,
        parent = parent,
        observabilityContext = observabilitycontext,
        httpDriverOption = httpdriveroption,
        processExecutionDriverOption = processexecutiondriveroption,
        processExecutionAdmissionOption = processexecutionadmissionoption,
        scopedConcurrencyAdmissionOption = scopedconcurrencyadmissionoption,
        operationEvaluationResolverOption = operationevaluationresolveroption
      )
    )
  }

  def unapply(x: ScopeContext): Option[ScopeContext.Core] = {
    Some(x.core)
  }

  private lazy val _default_data_store_space: DataStoreSpace =
    DataStoreSpace.default()

  private lazy val _default_entity_store_space: EntityStoreSpace =
    EntityStoreSpace.create(
      org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    )

  private lazy val _default_entity_space: EntitySpace =
    new EntitySpace()
}
