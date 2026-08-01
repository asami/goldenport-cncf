package org.goldenport.cncf.config

import java.nio.file.{Path, Paths}

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.{SubsystemCurrentUserEvidence, SubsystemExecutionProfile}
import org.goldenport.configuration.ConfigurationOrigin

/*
 * Admits the two standalone HOME profile layers without merging them. The
 * profile is intentionally unread for authenticated and controlled execution.
 *
 * @since   Jul. 31, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
object StandaloneUserProfileResolver {
  enum Layer {
    case TextusHome
    case CncfHome
  }

  final case class Admitted(
    layer: Layer,
    path: Path,
    origin: ConfigurationOrigin,
    document: StandaloneUserProfile.Document
  )

  private[config] trait Reader {
    def load(path: Path): Consequence[Option[StandaloneUserProfile.Document]]
  }

  private[config] trait HomeLookup {
    def resolve(): Consequence[Path]
  }

  private[config] object Reader {
    val default: Reader = new Reader {
      def load(path: Path): Consequence[Option[StandaloneUserProfile.Document]] =
        StandaloneUserProfile.load(path)
    }
  }

  private[config] object HomeLookup {
    val default: HomeLookup = new HomeLookup {
      def resolve(): Consequence[Path] = _home
    }
  }

  def resolve(profile: SubsystemExecutionProfile): Consequence[Vector[Admitted]] =
    resolve(profile, HomeLookup.default, Reader.default)

  private[config] def resolve(
    profile: SubsystemExecutionProfile,
    home: HomeLookup,
    reader: Reader
  ): Consequence[Vector[Admitted]] =
    profile.currentUserEvidence match {
      case SubsystemCurrentUserEvidence.Fixed =>
        home.resolve().flatMap(path => _resolve(profile, path.toAbsolutePath.normalize, reader))
      case SubsystemCurrentUserEvidence.Authenticated | SubsystemCurrentUserEvidence.ControlledTest =>
        Consequence.success(Vector.empty)
    }

  private[config] def resolve(
    profile: SubsystemExecutionProfile,
    home: Path,
    reader: Reader
  ): Consequence[Vector[Admitted]] =
    _resolve(profile, home.toAbsolutePath.normalize, reader)

  private def _resolve(
    profile: SubsystemExecutionProfile,
    home: Path,
    reader: Reader
  ): Consequence[Vector[Admitted]] =
    profile.currentUserEvidence match {
      case SubsystemCurrentUserEvidence.Fixed =>
        _admit(Layer.TextusHome, home.resolve(".textus").resolve("user-profile.yaml"), ConfigurationOrigin.Home, reader).flatMap { textus =>
          _admit(Layer.CncfHome, home.resolve(".cncf").resolve("user-profile.yaml"), ConfigurationOrigin.Home, reader).map { cncf =>
            textus.toVector ++ cncf.toVector
          }
        }
      case SubsystemCurrentUserEvidence.Authenticated | SubsystemCurrentUserEvidence.ControlledTest =>
        Consequence.success(Vector.empty)
    }

  private def _home: Consequence[Path] =
    sys.props.get("user.home").map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(Paths.get(value).toAbsolutePath.normalize)
      case None => Consequence.configurationInvalid("StandaloneUserProfile admission requires JVM user.home")
    }

  private[config] def admit(
    layer: Layer,
    path: Path,
    origin: ConfigurationOrigin,
    reader: Reader
  ): Consequence[Option[Admitted]] =
    if (origin != ConfigurationOrigin.Home)
      Consequence.configurationInvalid(s"StandaloneUserProfile admission accepts HOME sources only: $path")
    else
      _admit(layer, path, origin, reader)

  private def _admit(
    layer: Layer,
    path: Path,
    origin: ConfigurationOrigin,
    reader: Reader
  ): Consequence[Option[Admitted]] =
    reader.load(path).map(_.map(Admitted(layer, path, origin, _)))
}
