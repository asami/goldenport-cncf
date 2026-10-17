package org.goldenport.cncf.resource

import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 *  version Jul. 16, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResourceProviderMetadata(
  family: String,
  identity: String,
  configured: Boolean
) {
  def safeAttributes: Vector[(String, String)] =
    Vector(
      "resource.provider.family" -> family,
      "resource.provider.identity" -> identity,
      "resource.provider.configured" -> configured.toString
    )
}

object ResourceProviderMetadata {
  def unconfigured(scheme: String): ResourceProviderMetadata =
    ResourceProviderMetadata("unconfigured", scheme, configured = false)
}

/**
 * Test-owned resource provider assembly. It never opens host files or network
 * connections and is installed only by an explicit ExecutionContext test API.
 */
final case class ResourceAccessTestProfile(
  urlPolicy: ResourceUrlPolicy = ResourceUrlPolicy(),
  urlProviders: Vector[UrlResourceProvider] = Vector.empty,
  textusUrnProviders: Vector[TextusUrnResourceProvider] = Vector.empty,
  urnProviders: Vector[UrnResourceProvider] = Vector.empty,
  readLimits: ResourceReadLimits = ResourceReadLimits.default
) {
  def resourceAccess: ResourceAccess = ResourceAccess.testProfile(this)
}

final class InMemoryUrlResourceProvider(
  val scheme: String,
  contents: Map[String, String]
) extends UrlResourceProvider {
  def read(reference: ResourceReference.Url, limits: ResourceReadLimits): Consequence[ResourceContent] =
    contents.get(reference.print) match {
      case Some(value) => InMemoryResourceContent.createC(reference, value, limits)
      case None => Consequence.resourceNotFound("configured in-memory URL resource is not available")
    }
}

final class InMemoryTextusUrnResourceProvider(
  val namespace: String,
  contents: Map[String, String]
) extends TextusUrnResourceProvider {
  def read(reference: TextusUrnReference, limits: ResourceReadLimits): Consequence[ResourceContent] =
    contents.get(reference.resourceId) match {
      case Some(value) => InMemoryResourceContent.createC(reference.reference, value, limits)
      case None => Consequence.resourceNotFound("configured in-memory Textus URN resource is not available")
    }
}

final class InMemoryUrnResourceProvider(
  val nid: String,
  contents: Map[String, String]
) extends UrnResourceProvider {
  def read(reference: ResourceReference.Urn, limits: ResourceReadLimits): Consequence[ResourceContent] =
    contents.get(reference.nss) match {
      case Some(value) => InMemoryResourceContent.createC(reference, value, limits)
      case None => Consequence.resourceNotFound("configured in-memory URN resource is not available")
    }
}

private object InMemoryResourceContent {
  def createC(reference: ResourceReference, value: String, limits: ResourceReadLimits): Consequence[ResourceContent] =
    ResourceReadAcquisition.utf8C(value, limits).map(ResourceContent(reference, _))
}
