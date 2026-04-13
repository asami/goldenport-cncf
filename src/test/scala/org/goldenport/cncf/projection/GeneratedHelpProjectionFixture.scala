package org.goldenport.cncf.projection

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec.*
import org.goldenport.schema.DataType
import org.goldenport.cncf.action.{Action, ActionCall, QueryAction}
import org.goldenport.cncf.component.*
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.value.BaseContent

/*
 * @since   Mar. 25, 2026
 *  version Mar. 29, 2026
 *  version Apr.  6, 2026
 * @version Apr. 14, 2026
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
      Vector(new Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(
            CmlOperationDefinition(
              name = "lookupAddress",
              kind = "QUERY",
              summary = Some("Look up an address by postal code."),
              inputType = "LookupAddressQuery",
              inputSummary = Some("Postal code lookup request."),
              outputType = "LookupAddressResult",
              outputSummary = Some("Normalized address representation."),
              inputValueKind = "QUERY_VALUE",
              parameters = Vector.empty
            )
          )

        override def componentDefinitionRecords: Vector[org.goldenport.record.Record] =
          Vector(
            org.goldenport.record.Record.data(
              "name" -> "domain",
              "use_cases" -> Vector(
                org.goldenport.record.Record.data(
                  "name" -> "component_postal_support",
                  "summary" -> "Provide postal-address lookup capabilities as a component.",
                  "primary_actor" -> "EndUser",
                  "goal" -> "Expose reusable postal lookup behavior through the component boundary.",
                  "precondition" -> "The address service is configured in the component.",
                  "postcondition" -> "The component can answer postal lookup requests through its public service."
                )
              )
            )
          )

        override def subsystemDefinitionRecords: Vector[org.goldenport.record.Record] =
          Vector(
            org.goldenport.record.Record.data(
              "name" -> "domain",
              "domain_visions" -> Vector(
                org.goldenport.record.Record.data(
                  "name" -> "trusted_postal_foundation",
                  "summary" -> "Provide a trusted postal information foundation.",
                  "goal" -> "Establish a subsystem-level postal information baseline."
                )
              ),
              "domain_use_cases" -> Vector(
                org.goldenport.record.Record.data(
                  "name" -> "postal_address_lifecycle",
                  "summary" -> "Support subsystem-level postal address lookup and reuse.",
                  "primary_actor" -> "EndUser",
                  "goal" -> "Expose postal capabilities at subsystem scope.",
                  "precondition" -> "The subsystem hosts the domain component.",
                  "postcondition" -> "Subsystem-level postal requirements are available through subsystem help."
                )
              )
            )
          )
      }.initialize(
        ComponentInit(
          subsystem = params.subsystem,
          core = core,
          origin = params.origin
        )
      ))

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
    def useCaseRecords: Vector[org.goldenport.record.Record] =
      Vector(
        org.goldenport.record.Record.data(
          "name" -> "postal_lookup",
          "summary" -> "Look up an address from a postal code.",
          "primary_actor" -> "EndUser",
          "goal" -> "Resolve a postal code into a normalized address representation.",
          "precondition" -> "A resolvable postal code is provided.",
          "postcondition" -> "A normalized address projection is returned."
        )
      )

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
          description("Look up an address by postal code.Returns a normalized address representation."),
        response = ResponseDefinition(result = List(DataType.Named("LookupAddressResult")))
      )
      .build()

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.notImplemented("not used")
  }
}
