package org.goldenport.cncf.config

import org.goldenport.Consequence

/*
 * @since   Jul. 17, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecretReference private (
  private val _locator: String
) {
  override def equals(other: Any): Boolean =
    other match {
      case value: SecretReference => _locator == value._locator
      case _ => false
    }

  override def hashCode(): Int =
    0

  override def toString: String =
    "SecretReference(<redacted>)"
}

object SecretReference {
  private[cncf] def fromConfiguration(value: String): Consequence[SecretReference] =
    Option(value).map(_.trim).filter(_.nonEmpty).fold[Consequence[SecretReference]](
      Consequence.configurationInvalid("secret reference is required")
    )(value => Consequence.success(new SecretReference(value)))
}

private[cncf] final class SecretMaterial private[config] (
  private val _bytes: Array[Byte]
) {
  private[cncf] def _copy_bytes: Array[Byte] =
    _bytes.clone()

  override def toString: String =
    "SecretMaterial(<redacted>)"
}

private[cncf] trait RuntimeSecretResolver {
  def resolveSecret(reference: SecretReference): Consequence[SecretMaterial]
}

private[cncf] object RuntimeSecretResolver {
  def inMemory(entries: Iterable[(SecretReference, Array[Byte])]): RuntimeSecretResolver =
    new _InMemorySecretResolver(
      entries.iterator.map { case (reference, value) =>
        reference -> new SecretMaterial(value.clone())
      }.toMap
    )

  private final class _InMemorySecretResolver(
    entries: Map[SecretReference, SecretMaterial]
  ) extends RuntimeSecretResolver {
    def resolveSecret(reference: SecretReference): Consequence[SecretMaterial] =
      entries.get(reference).map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("secret reference cannot be resolved by the runtime")
      )
  }
}
