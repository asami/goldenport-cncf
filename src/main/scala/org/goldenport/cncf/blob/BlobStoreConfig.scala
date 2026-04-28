package org.goldenport.cncf.blob

import java.nio.file.Path
import java.util.Locale
import scala.collection.concurrent.TrieMap
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.RuntimeConfig

/*
 * Runtime configuration and factory for CNCF BlobStore backends.
 *
 * @since   Apr. 28, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class BlobStoreConfig(
  backend: String = BlobStoreConfig.DefaultBackend,
  name: Option[String] = None,
  container: String = BlobStorageRef.DefaultContainer,
  localRoot: Option[Path] = None,
  publicBasePath: Option[String] = None,
  providerClass: Option[String] = None
) {
  def normalizedBackend: String =
    backend.trim.toLowerCase(Locale.ROOT).replace('-', '_')

  def effectiveName: String =
    name.map(_.trim).filter(_.nonEmpty).getOrElse(normalizedBackend match {
      case BlobStoreConfig.BackendInMemory => "in-memory"
      case BlobStoreConfig.BackendLocal => "local"
      case other => other
    })
}

object BlobStoreConfig {
  val BackendInMemory: String = "in_memory"
  val BackendLocal: String = "local"
  val DefaultBackend: String = BackendInMemory

  def fromConfiguration(
    configuration: ResolvedConfiguration
  ): BlobStoreConfig =
    BlobStoreConfig(
      backend = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStoreBackendKey)
        .getOrElse(DefaultBackend),
      name = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStoreNameKey),
      container = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStoreContainerKey)
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse(BlobStorageRef.DefaultContainer),
      localRoot = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStoreLocalRootKey)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(Path.of(_)),
      publicBasePath = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStorePublicBasePathKey)
        .map(normalizePublicBasePath)
        .filter(_.nonEmpty),
      providerClass = RuntimeConfig.getString(configuration, RuntimeConfig.BlobStoreProviderClassKey)
        .map(_.trim)
        .filter(_.nonEmpty)
    )

  def normalizePublicBasePath(value: String): String = {
    val trimmed = value.trim
    if (trimmed.isEmpty) ""
    else if (hasUriScheme(trimmed)) trimmed
    else if (trimmed.startsWith("/")) trimmed.stripSuffix("/")
    else s"/${trimmed.stripSuffix("/")}"
  }

  def hasUriScheme(value: String): Boolean =
    value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")
}

trait BlobStoreProvider {
  def createBlobStore(config: BlobStoreConfig): Consequence[BlobStore]
}

object BlobStoreFactory {
  private val _providers = TrieMap.empty[String, BlobStoreProvider]

  def register(
    backend: String,
    provider: BlobStoreProvider
  ): Unit =
    _providers.update(_normalize_backend(backend), provider)

  def unregister(
    backend: String
  ): Unit =
    _providers.remove(_normalize_backend(backend))

  def create(config: BlobStoreConfig): Consequence[BlobStore] =
    _validate_config(config).flatMap { _ =>
      config.providerClass match {
        case Some(className) =>
          _create_provider(className).flatMap(_.createBlobStore(config))
        case None =>
          _create_named(config)
      }
    }

  def create(configuration: ResolvedConfiguration): Consequence[BlobStore] =
    create(BlobStoreConfig.fromConfiguration(configuration))

  private def _validate_config(config: BlobStoreConfig): Consequence[Unit] =
    config.publicBasePath match {
      case Some(value) if BlobStoreConfig.hasUriScheme(value) || value.startsWith("//") =>
        Consequence.configurationInvalid("textus.blob.store.public-base-path must be a relative path, not an absolute URL")
      case _ =>
        Consequence.unit
    }

  private def _create_named(config: BlobStoreConfig): Consequence[BlobStore] =
    config.normalizedBackend match {
      case BlobStoreConfig.BackendInMemory =>
        Consequence.success(InMemoryBlobStore(
          name = config.effectiveName,
          container = config.container,
          publicBasePath = config.publicBasePath
        ))
      case BlobStoreConfig.BackendLocal =>
        _create_local(config)
      case other =>
        _providers.get(other) match {
          case Some(provider) =>
            provider.createBlobStore(config)
          case None =>
            Consequence.argumentInvalid(s"unknown blob store backend: $other")
        }
    }

  private def _create_local(config: BlobStoreConfig): Consequence[BlobStore] =
    config.localRoot match {
      case Some(root) =>
        Consequence.success(LocalBlobStore(
          root = root,
          name = config.effectiveName,
          container = config.container,
          publicBasePath = config.publicBasePath
        ))
      case None =>
        Consequence.argumentInvalid("textus.blob.store.local.root is required when textus.blob.store.backend=local")
    }

  private def _create_provider(
    className: String
  ): Consequence[BlobStoreProvider] = {
    try {
      val loader = Option(Thread.currentThread.getContextClassLoader)
        .getOrElse(getClass.getClassLoader)
      val clazz = Class.forName(className, true, loader)
      if (!classOf[BlobStoreProvider].isAssignableFrom(clazz))
        Consequence.configurationInvalid(s"BlobStore provider class does not implement BlobStoreProvider: $className")
      else
        Consequence.success(clazz.getDeclaredConstructor().newInstance().asInstanceOf[BlobStoreProvider])
    } catch {
      case NonFatal(e) =>
        Consequence.configurationInvalid(s"BlobStore provider class cannot be instantiated: $className: ${e.getMessage}")
    }
  }

  private def _normalize_backend(
    backend: String
  ): String =
    backend.trim.toLowerCase(Locale.ROOT).replace('-', '_')
}

final class UnavailableBlobStore(
  val name: String,
  reason: String
) extends BlobStore {
  def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag): Consequence[BlobPutResult] =
    _failure

  def get(ref: BlobStorageRef): Consequence[BlobReadResult] =
    _failure

  def delete(ref: BlobStorageRef): Consequence[Unit] =
    _failure

  def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] =
    _failure

  def status(): Consequence[BlobStoreStatus] =
    Consequence.success(BlobStoreStatus(
      backend = "unavailable",
      available = false,
      message = Some(reason)
    ))

  private def _failure[A]: Consequence[A] =
    Consequence.argumentInvalid(reason)
}
