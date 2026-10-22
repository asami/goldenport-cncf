package org.goldenport.cncf.oauth

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInit}
import org.goldenport.cncf.config.{ComponentParameterKey, SecretReference}

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
/** Optional component capability bootstrapped from an explicitly declared opaque reference. */
trait OAuthConnectionRuntime extends Component {
  private var _oauth_connection: Option[OAuthConnection] = None

  private[cncf] final def oauthConnection: Option[OAuthConnection] = _oauth_connection

  override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
    super.initialize_component_c(params).flatMap { _ =>
      initializationParameters.resolve(OAuthConnectionRuntime.configurationKey).flatMap { resolution =>
        resolution.value match {
          case None =>
            _oauth_connection = None
            Consequence.unit
          case Some(reference) =>
            OAuthConnectionConfiguration.loadC(reference, componentId, instanceId)
              .flatMap(OAuthConnection.createC).map { connection =>
                _oauth_connection = Some(connection)
              }
        }
      }
    }
}

object OAuthConnectionRuntime {
  /** Factories must declare this exact key object, retaining its identity in the snapshot. */
  val configurationKey: ComponentParameterKey[SecretReference] =
    ComponentParameterKey.optionalSecretReference("oauth.connection.configuration")
}
