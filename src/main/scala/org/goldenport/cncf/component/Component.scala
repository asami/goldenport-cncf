package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.logic.ProtocolLogic
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.protocol.service.{Service => ProtocolService}
// import org.goldenport.cncf.action.ActionLogic
import org.goldenport.cncf.action.ActionEngine
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}

/*
 * @since   Jan.  1, 2026
 *  version Jan.  3, 2026
 * @version Jan.  4, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Component extends Component.Core.Holder {
  lazy val services: ServiceGroup =
    ServiceGroup(protocol.services.services.map(_to_service))

  lazy val receptors: ReceptorGroup = ReceptorGroup.empty // TODO

  lazy val logic: ComponentLogic = ComponentLogic(this)

  private def _to_service(p: ServiceDefinition): Service = {
    p.createService(serviceFactory)
  }

  def service: Service = services.services.head // TODO
}

object Component {
  case class Config()

  case class Core(
    protocol: Protocol,
    protocolLogic: ProtocolLogic,
    actionEngine: ActionEngine,
    jobEngine: JobEngine,
    serviceFactory: ServiceFactory
  )
  object Core {
    trait Holder {
      def core: Core

      def protocol = core.protocol
      def protocolLogic = core.protocolLogic
      def actionEngine = core.actionEngine
      def jobEngine = core.jobEngine
      def serviceFactory = core.serviceFactory
    }
  }

  abstract class ServiceFactory extends ServiceDefinition.Factory[Service] {
    protected var service_ccore: Option[Service.CCore] = None

    def setup(comp: Component): Unit =
      service_ccore = Some(Service.CCore(comp.logic))

    def create(core: ProtocolService.Core): Service = {
      val c = service_ccore getOrElse ???
      create(core, c)
    }

    def create(core: ProtocolService.Core, ccore: Service.CCore): Service
  }
  object ServiceFactory {
    def apply(): ServiceFactory = Instance()

    case class Instance() extends ServiceFactory {
      def create(core: ProtocolService.Core, ccore: Service.CCore): Service =
        Service(core, ccore)
    }
  }

  case class Instance(core: Core) extends Component {
  }

  def create(protocol: Protocol): Component = {
    val servicefactory = ServiceFactory()
    create(protocol, servicefactory)
  }

  def create(
    protocol: Protocol,
    serviceFactory: ServiceFactory
  ): Component = {
    val r = create(
      protocol,
      ProtocolLogic(protocol),
      ActionEngine.create(),
      InMemoryJobEngine.create(),
      serviceFactory
    )
    serviceFactory.setup(r)
    r
  }

  def create(
    protocol: Protocol,
    protocolLogic: ProtocolLogic,
    actionEngine: ActionEngine,
    jobEngine: JobEngine,
    serviceFactory: ServiceFactory
  ): Component = {
    val core = Core(
      protocol,
      protocolLogic,
      actionEngine,
      jobEngine,
      serviceFactory
    )
    Instance(core)
  }
}

// trait ComponentActionEntry {
//   def name: String
//   def opdef: OperationDefinition
//   def logic: ActionLogic
// }

// trait Service {
//   def entries: Seq[ComponentActionEntry]

//   def call(
//     name: String,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse]
// }

// trait Receptor {
//   def entries: Seq[ComponentActionEntry]

//   def receive(
//     name: String,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse]
// }
