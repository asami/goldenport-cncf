package org.goldenport.cncf.filestore

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInit
import org.goldenport.cncf.config.{ComponentParameterKey, SecretReference}
import org.goldenport.cncf.oauth.OAuthConnectionRuntime

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 */
/** Explicit component-owned selection, retaining the existing OAuth management surface. */
trait GoogleDriveConnectionRuntime extends OAuthConnectionRuntime {
  private var _service_account: Option[GoogleDriveServiceAccount] = None
  private[cncf] final def googleDriveServiceAccount: Option[GoogleDriveServiceAccount] = _service_account

  /** Configuration presence only: never proof of Google access or Material permission. */
  final def hasGoogleDriveConfiguration: Boolean = _service_account.isDefined || oauthConnection.isDefined

  override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
    super.initialize_component_c(params).flatMap { _ =>
      initializationParameters.resolve(GoogleDriveConnectionRuntime.configurationKey).flatMap { selected =>
        if (selected.value.isDefined && oauthConnection.isDefined)
          Consequence.configurationInvalid("Google Drive authentication strategies are mutually exclusive")
        else selected.value match {
          case None => _service_account = None; Consequence.unit
          case Some(reference) => GoogleDriveServiceAccount.loadC(reference, componentId, instanceId).map { account =>
            _service_account = Some(account)
          }
        }
      }
    }
}

object GoogleDriveConnectionRuntime {
  val configurationKey: ComponentParameterKey[SecretReference] =
    ComponentParameterKey.optionalSecretReference("google-drive.service-account.configuration")
}
