package org.goldenport.cncf.component.builtin.messagedeliverystub

import java.time.Instant
import java.util.UUID
import scala.collection.concurrent.TrieMap
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.messagedelivery.{DeliveryChannel, UnifiedMessage, MessageDeliveryProvider, MessageDeliveryResult}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.spec.ServiceDefinitionGroup

/*
 * Built-in stub message-delivery provider for local/test use.
 *
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class MessageDeliveryStubComponent() extends Component

object MessageDeliveryStubComponent:
  final case class Delivery(
    channel: DeliveryChannel,
    recipient: String,
    subject: Option[String],
    body: String,
    templateId: Option[String],
    attributes: Map[String, String],
    correlationId: Option[String],
    providerMessageId: String,
    acceptedAt: Instant
  )

  private val _deliveries = TrieMap.empty[String, Delivery]

  val name: String = "MessageDeliveryStub"
  val componentId: ComponentId = ComponentId(name)

  def deliveries: Vector[Delivery] =
    _deliveries.values.toVector.sortBy(_.acceptedAt)

  def clearDeliveries(): Unit =
    _deliveries.clear()

  object Factory extends Component.SinglePrimaryBundleFactory:
    protected def create_Component(params: ComponentCreate): Component =
      new MessageDeliveryStubComponent:
        override def messageDeliveryProviders: Vector[MessageDeliveryProvider] =
          Vector(DefaultMessageDeliveryProvider)

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      Component.Core.create(
        name,
        componentId,
        ComponentInstanceId.default(componentId),
        Protocol(
          services = ServiceDefinitionGroup(services = Vector.empty),
          handler = ProtocolHandler.default
        )
      )

  object DefaultMessageDeliveryProvider extends MessageDeliveryProvider:
    val name: String = "textus-message-delivery-stub"

    def send(message: UnifiedMessage)(using ExecutionContext): Consequence[MessageDeliveryResult] =
      val now = Instant.now
      val providerMessageId = UUID.randomUUID.toString
      val delivery = Delivery(
        channel = message.channel,
        recipient = message.recipient,
        subject = message.subject,
        body = message.body,
        templateId = message.templateId,
        attributes = message.attributes,
        correlationId = message.correlationId,
        providerMessageId = providerMessageId,
        acceptedAt = now
      )
      _deliveries.update(providerMessageId, delivery)
      println(s"[message-delivery-stub] channel=${message.channel.toString.toLowerCase(java.util.Locale.ROOT)} recipient=${message.recipient} subject=${message.subject.getOrElse("")} providerMessageId=$providerMessageId")
      Consequence.success(
        MessageDeliveryResult(
          accepted = true,
          providerMessageId = Some(providerMessageId),
          acceptedAt = now,
          attributes = Map("provider" -> name)
        )
      )
