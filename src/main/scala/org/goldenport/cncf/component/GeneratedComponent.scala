package org.goldenport.cncf.component

final class GeneratedComponent(
  override val core: Component.Core,
  definition: ComponentDefinition
) extends Component {
  override def protocol: org.goldenport.protocol.Protocol =
    definition.protocol
}
