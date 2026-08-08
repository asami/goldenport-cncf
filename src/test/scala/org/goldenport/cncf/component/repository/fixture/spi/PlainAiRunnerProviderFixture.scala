package org.goldenport.cncf.component.repository.fixture.spi

import java.util.concurrent.atomic.AtomicInteger

import org.goldenport.Consequence
import org.goldenport.cncf.component.*
import org.goldenport.cncf.config.ComponentParameterKey
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiMessage, AiRecordRequest, AiRecordResponse, AiRunner}
import org.goldenport.protocol.Protocol

/*
 * @since   Jul.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class PlainAiRunnerProviderComponent extends Component {
  override def displayName: String = "plain-ai-runner-provider"
}

final class ArtSceneComponent extends Component {
  override def displayName: String = "component-file-app"
}

final class PlainAiRunner extends AiRunner {
  def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
    Consequence.success(AiGenerateResponse(s"car:${req.prompt}"))

  def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
    Consequence.serviceUnavailable("generateRecord is not used by this fixture.")

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
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.PlainAiRunnerProvider")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class ArtSceneComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new ArtSceneComponent()

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.ComponentFileApp")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class ActiveAssemblyApiComponent extends Component {
  override def displayName: String = "active-component"
}

final class ActiveAssemblyApiComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new ActiveAssemblyApiComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.ActiveAssemblyApi")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class SearchAssemblyApiComponent extends Component {
  override def displayName: String = "search-component"
}

final class SearchAssemblyApiComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new SearchAssemblyApiComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.SearchAssemblyApi")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class PlainFactoryPrimaryComponent extends Component {
  override def displayName: String = "plain-factory-primary"
}

final class PlainFactoryPrimaryComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new PlainFactoryPrimaryComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.PlainFactoryPrimary")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class CanonicalRepositoryParameterProbeComponent extends Component

final class CanonicalRepositoryParameterProbeFactory extends Component.PrimaryComponentFactory {
  override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
    Vector(CanonicalRepositoryParameterProbeFactory.limitKey)

  protected def create_Component(params: ComponentCreate): Component = {
    CanonicalRepositoryParameterProbeFactory.recordCreation()
    new CanonicalRepositoryParameterProbeComponent
  }

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.RepositoryParameterProbe")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

object CanonicalRepositoryParameterProbeFactory {
  private val _creation_count = new AtomicInteger(0)

  val limitKey: ComponentParameterKey[Int] = ComponentParameterKey.requiredInt("provider.limit")

  def resetCreationCount(): Unit =
    _creation_count.set(0)

  def creationCount: Int =
    _creation_count.get()

  private[spi] def recordCreation(): Unit =
    _creation_count.incrementAndGet()
}
