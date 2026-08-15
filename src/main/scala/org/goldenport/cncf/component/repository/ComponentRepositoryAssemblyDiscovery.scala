package org.goldenport.cncf.component.repository

import scala.util.control.NonFatal
import scala.collection.mutable

import org.goldenport.Consequence
import org.goldenport.cncf.component.{AssemblyApiClassLoader, AssemblyApiMetadata, Component}

/*
 * Assembly API metadata belongs to the full admitted assembly, while component
 * discovery remains partitioned by descriptor binding.  Keep those two scopes
 * together here so all partitions share one API class identity.
 *
 * @since   Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[repository] object ComponentRepositoryAssemblyDiscovery {
  def required[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => Consequence.Failure[A](conclusion).RAISE
    }

  def discoverC(
    assemblyRepositories: Vector[ComponentRepository],
    discoveryPartitions: Vector[Vector[ComponentRepository]],
    runtimeParent: ClassLoader
  ): Consequence[Vector[Vector[Component]]] =
    try {
      val metadatac = prepareMetadataC(assemblyRepositories)
      metadatac.flatMap { metadata =>
        AssemblyApiClassLoader.create(runtimeParent, metadata).map { loader =>
          assemblyRepositories.foreach(_.installAssemblyApiClassLoader(loader))
          val discovered = mutable.ArrayBuffer.empty[(ComponentRepository, Vector[Component])]
          def _discover_repository_(repository: ComponentRepository): Vector[Component] =
            discovered.collectFirst { case (candidate, components) if candidate eq repository => components }
              .getOrElse {
                val components = repository.discover().toVector
                discovered += repository -> components
                components
              }
          discoveryPartitions.map(_.flatMap(_discover_repository_))
        }
      }
    } catch {
      case e: ComponentRepository.ComponentRepositoryDiscoveryFailure => Consequence.Failure(e.conclusion)
      case NonFatal(e) => Consequence.componentInvalid(e)
    }

  def prepareMetadataC(repositories: Vector[ComponentRepository]): Consequence[AssemblyApiMetadata] =
    repositories.foldLeft(Consequence.success(AssemblyApiMetadata())) { (z, repository) =>
      for {
        acc <- z
        metadata <- repository.prepareAssemblyApi()
      } yield acc ++ metadata
    }
}
