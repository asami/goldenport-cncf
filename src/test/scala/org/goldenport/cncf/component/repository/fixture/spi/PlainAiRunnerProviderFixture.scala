package org.goldenport.cncf.component.repository.fixture.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiMessage, AiRunner}
import org.goldenport.protocol.Protocol

/*
 * @since   Jul.  8, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class PlainAiRunnerProviderComponent extends Component

final class ArtSceneComponent extends Component

final class PlainAiRunner extends AiRunner {
  def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
    Consequence.success(AiGenerateResponse(s"car:${req.prompt}"))

  def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
    Consequence.success(AiChatResponse(AiMessage("assistant", "car:chat")))
}

final class ComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new PlainAiRunnerProviderComponent()
      .withPort(Component.Port.of(new PlainAiRunner))

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    Component.Core.create(
      "plain-ai-runner-provider",
      ComponentId("plain_ai_runner_provider"),
      ComponentInstanceId.default(ComponentId("plain_ai_runner_provider")),
      Protocol.empty,
      this
    )
}

final class ArtSceneComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new ArtSceneComponent()

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    Component.Core.create(
      "textus-art-scene",
      ComponentId("textus_art_scene"),
      ComponentInstanceId.default(ComponentId("textus_art_scene")),
      Protocol.empty,
      this
    )
}
