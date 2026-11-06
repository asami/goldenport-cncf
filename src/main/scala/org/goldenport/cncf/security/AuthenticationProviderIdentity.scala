package org.goldenport.cncf.security

/** Nonsecret names of an actually selected deployment binding and provider.
  * Constructing this value supplies no authentication or provenance authority.
  * @since   Nov.  6, 2026
  * @version Nov.  6, 2026
  * @author  ASAMI, Tomoharu
  */
final case class AuthenticationProviderIdentity(
  bindingName: String,
  providerName: String,
  componentName: String
)
