package org.goldenport.cncf.resource

import org.goldenport.Consequence
import org.goldenport.cncf.http.PublicNetworkAdmission

/*
 * Provider-neutral admission for static Web reads.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
object WebTargetAdmission {
  def admitC(reference: ResourceReference): Consequence[ResourceReference.Url] =
    reference match {
      case url: ResourceReference.Url => _admit_url_c(url)
      case _ => Consequence.resourceUnsupported("Web access requires an absolute HTTPS URL")
    }

  private def _admit_url_c(
    reference: ResourceReference.Url
  ): Consequence[ResourceReference.Url] = {
    val uri = reference.uri
    if (reference.scheme != "https")
      Consequence.resourceUnsupported("Web access requires HTTPS")
    else if (uri.getUserInfo != null || uri.getFragment != null)
      Consequence.resourceUnsupported("Web access URL must not contain user-info or a fragment")
    else if (uri.getPort != -1 && uri.getPort != 443)
      Consequence.resourceUnsupported("Web access URL must use the default HTTPS port")
    else
      Option(uri.getHost).filter(_.nonEmpty) match {
        case Some(host) => PublicNetworkAdmission.admitHostC(host).map(_ => reference)
        case None => Consequence.resourceUnsupported("Web access URL requires a host")
      }
  }
}
