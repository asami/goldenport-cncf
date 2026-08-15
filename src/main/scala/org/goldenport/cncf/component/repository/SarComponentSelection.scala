package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentId}

/*
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[repository] object SarComponentSelection {
  def select(
    discovered: Vector[Component],
    requested: Vector[ComponentDescriptor]
  ): Vector[Component] =
    if (requested.isEmpty)
      discovered.distinctBy(_.name)
    else
      _requested_coordinates(requested).map { coordinates =>
        discovered.filter { component =>
          component.artifactMetadata.exists { metadata =>
            metadata.componentId.exists { componentid =>
              coordinates.contains(componentid -> metadata.version)
            }
          }
        }
      }.getOrElse(Vector.empty)

  private def _requested_coordinates(
    requested: Vector[ComponentDescriptor]
  ): Option[Set[(ComponentId, String)]] = {
    val coordinates = requested.map(_.requireCanonicalIdentityC.toOption)
    if (coordinates.forall(_.nonEmpty)) Some(coordinates.flatten.toSet) else None
  }
}
