package org.goldenport.cncf.resource

import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait UrnResourceProvider {
  def nid: String
  def read(reference: ResourceReference.Urn): Consequence[ResourceContent]
}

final case class UrnResourceProviderBinding(
  nid: String,
  providerClass: String
)

final case class UrnResourceProviderConfig(
  providers: Vector[UrnResourceProvider] = Vector.empty
)

object UrnResourceProviderConfig {
  private val _nid_pattern = "^[a-z0-9][a-z0-9-]{0,31}$".r
  private val _configuration_key = "textus.resource.urn.providers"

  def fromValuesC(values: Vector[String]): Consequence[UrnResourceProviderConfig] =
    _bindings_c(values).flatMap { bindings =>
      bindings.foldLeft(Consequence.success(Vector.empty[UrnResourceProvider])) { (z, binding) =>
        for {
          providers <- z
          provider <- _provider_c(binding)
        } yield providers :+ provider
      }.map(UrnResourceProviderConfig(_))
    }

  private def _bindings_c(
    values: Vector[String]
  ): Consequence[Vector[UrnResourceProviderBinding]] =
    values.foldLeft(Consequence.success(Vector.empty[UrnResourceProviderBinding])) { (z, value) =>
      for {
        bindings <- z
        binding <- _binding_c(value)
        _ <- if (bindings.exists(_.nid == binding.nid))
          Consequence.argumentInvalid(_configuration_key, "each NID exactly once", binding.nid)
        else
          Consequence.unit
      } yield bindings :+ binding
    }

  private def _binding_c(value: String): Consequence[UrnResourceProviderBinding] =
    value.trim.split("=", 2).toVector match {
      case Vector(rawnid, rawclass) =>
        val nid = rawnid.trim.toLowerCase(Locale.ROOT)
        val classname = rawclass.trim
        if (!_nid_pattern.matches(nid))
          Consequence.argumentFormatError(_configuration_key, "<nid>=<provider-class>", value)
        else if (nid == "textus")
          Consequence.argumentInvalid(_configuration_key, "a non-Textus NID", nid)
        else if (classname.isEmpty)
          Consequence.argumentFormatError(_configuration_key, "<nid>=<provider-class>", value)
        else
          Consequence.success(UrnResourceProviderBinding(nid, classname))
      case _ =>
        Consequence.argumentFormatError(_configuration_key, "<nid>=<provider-class>", value)
    }

  private def _provider_c(
    binding: UrnResourceProviderBinding
  ): Consequence[UrnResourceProvider] =
    try {
      val loader = Option(Thread.currentThread.getContextClassLoader)
        .getOrElse(getClass.getClassLoader)
      val clazz = Class.forName(binding.providerClass, true, loader)
      if (!classOf[UrnResourceProvider].isAssignableFrom(clazz))
        Consequence.configurationInvalid(
          s"URN resource provider class does not implement UrnResourceProvider: ${binding.providerClass}"
        )
      else {
        val provider = clazz.getConstructor().newInstance().asInstanceOf[UrnResourceProvider]
        _normalized_nid_c(provider.nid).flatMap { nid =>
          if (nid == binding.nid)
            Consequence.success(provider)
          else
            Consequence.configurationInvalid(
              s"URN resource provider NID does not match configured binding: ${binding.providerClass}"
            )
        }
      }
    } catch {
      case NonFatal(_) =>
        Consequence.configurationInvalid(
          s"URN resource provider class cannot be instantiated: ${binding.providerClass}"
        )
    }

  private def _normalized_nid_c(value: String): Consequence[String] = {
    val nid = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    if (_nid_pattern.matches(nid) && nid != "textus")
      Consequence.success(nid)
    else
      Consequence.configurationInvalid("URN resource provider must declare a non-Textus NID")
  }
}

private final class UrnResourceAccess(
  providers: Vector[UrnResourceProvider]
) extends ResourceAccess {
  private val _providers = providers.flatMap { provider =>
    Option(provider).flatMap { value =>
      val nid = Option(value.nid).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
      if (nid == "textus" || !nid.matches("^[a-z0-9][a-z0-9-]{0,31}$"))
        None
      else
        Some(nid -> value)
    }
  }.toMap

  def read(reference: ResourceReference): Consequence[ResourceContent] =
    reference match {
      case urn: ResourceReference.Urn if urn.nid == "textus" =>
        Consequence.resourceUnsupported("Textus URN resources require the dedicated standard provider")
      case urn: ResourceReference.Urn =>
        _providers.get(urn.nid) match {
          case Some(provider) => provider.read(urn)
          case None => Consequence.resourceUnsupported("URN resource NID is not configured")
        }
      case _ =>
        Consequence.resourceUnsupported("URN resource access requires a URN reference")
    }

  override def providerMetadata(reference: ResourceReference): ResourceProviderMetadata =
    reference match {
      case urn: ResourceReference.Urn if urn.nid == "textus" =>
        ResourceProviderMetadata.unconfigured("textus")
      case urn: ResourceReference.Urn =>
        _providers.get(urn.nid) match {
          case Some(_) => ResourceProviderMetadata("urn", urn.nid, configured = true)
          case None => ResourceProviderMetadata.unconfigured(urn.nid)
        }
      case _ => ResourceProviderMetadata.unconfigured("urn")
    }
}
