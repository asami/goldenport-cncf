package org.goldenport.cncf.projection

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec.*
import org.goldenport.cncf.action.{Action, ActionCall, QueryAction}
import org.goldenport.cncf.component.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.value.BaseContent

/*
 * @since   Mar. 25, 2026
 * @version Mar. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[projection] object GeneratedHelpProjectionFixture {
  def component(): Component = {
    val subsystem = TestComponentFactory.emptySubsystem(name)
    val params = ComponentCreate(
      subsystem = subsystem,
      origin = ComponentOrigin.Repository("cozy-generated")
    )
    val component = factory.create(params).head
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private val name = "domain"
  private val componentId = ComponentId(name)

  private lazy val factory = new Component.Factory {
    override protected def create_Components(params: ComponentCreate): Vector[Component] =
      Vector(Component.Instance(core))

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      core
  }

  private lazy val core: Component.Core =
    Component.Core.create(
      name,
      componentId,
      ComponentInstanceId.default(componentId),
      Protocol(
        services = ServiceDefinitionGroup(Vector(AddressService))
      ),
      factory
    )

  object AddressService extends ServiceDefinition {
    val specification = ServiceDefinition.Specification.Builder("address").
      summary("Address service for postal address support.").
      description("Address service for postal address support.Provides help-visible metadata for CNCF projections.").
      operation(LookupAddressOperation).
      build()
  }

  object LookupAddressOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("lookupAddress").
      copy(
        content = BaseContent.Builder("lookupAddress").
          summary("Look up an address by postal code.").
          description("Look up an address by postal code.Returns a normalized address representation.")
      )
      .build()

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.failure("not used")
  }
}
