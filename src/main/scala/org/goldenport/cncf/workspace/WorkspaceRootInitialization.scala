package org.goldenport.cncf.workspace

import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.BasicFileAttributes
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId

/*
 * @since   Nov. 16, 2026
 * @version Nov. 16, 2026
 */
/** Explicit host policy; an anchor is not a general workspace grant. */
private[cncf] final case class WorkspaceRootInitialization(
  componentId: ComponentId,
  anchor: Path,
  sources: Vector[WorkspaceId],
  parent: WorkspaceId,
  alias: WorkspaceId,
  parentPath: WorkspaceRelativePath,
  aliasPath: WorkspaceRelativePath
)

private[workspace] final case class WorkspaceDirectoryIdentity(path: Path, filekey: AnyRef)

private[cncf] final class PreparedWorkspaceRootInitialization private[workspace] (
  private val _identities: Vector[WorkspaceDirectoryIdentity],
  private val _missing: Vector[Path],
  private val _aliases: Vector[Path]
) {
  def verifyExistingC: Consequence[Unit] =
    if (_missing.nonEmpty) WorkspaceRootInitialization.invalidC("Declared roots must already exist during direct decoding")
    else WorkspaceRootInitialization.verifyC(_identities, _missing, _aliases)

  /** No rollback: a failed later effect leaves the prefixes that were actually created. */
  def initializeC: Consequence[Unit] = {
    def _create_(identities: Vector[WorkspaceDirectoryIdentity], missing: Vector[Path]): Consequence[Unit] =
      WorkspaceRootInitialization.verifyC(identities, missing, _aliases).flatMap { _ =>
        missing.headOption match {
          case None => Consequence.unit
          case Some(path) =>
            val created = try {
              Files.createDirectory(path)
              WorkspaceRootInitialization.identityC(path)
            } catch {
              case NonFatal(_) => WorkspaceRootInitialization.invalidC[WorkspaceDirectoryIdentity](
                "Root initialization failed; any created prefixes are retained")
            }
            created.flatMap(identity => _create_(identities :+ identity, missing.tail))
        }
      }
    _create_(_identities, _missing)
  }
}

private[cncf] object WorkspaceRootInitialization {
  def prepareC(
    roots: Vector[(WorkspaceId, Path)],
    grants: Vector[WorkspaceGrant],
    candidates: Vector[SessionCandidateWorkspacePolicy],
    policies: Vector[WorkspaceRootInitialization]
  ): Consequence[PreparedWorkspaceRootInitialization] = {
    val initialized = policies.flatMap(x => Vector(x.parent, x.alias))
    val sources = roots.filterNot(x => initialized.contains(x._1))
    val declared = roots.toMap
    val targets = policies.flatMap(x => Vector(x.anchor.resolve(x.parentPath.value).normalize(),
      x.anchor.resolve(x.aliasPath.value).normalize()))
    def _overlap_(left: Path, right: Path): Boolean = left.startsWith(right) || right.startsWith(left)
    for {
      _ <- WorkspaceExecutionAdmission.validateDeclarationsC(roots, grants, candidates)
      _ <- if (policies.size <= 16 && initialized.distinct.size == initialized.size &&
          roots.forall { case (_, path) => path != null && path.isAbsolute && path == path.normalize() && !path.toString.exists(_.isControl) } &&
          policies.forall { policy =>
            policy.anchor != null && policy.anchor.isAbsolute && policy.anchor == policy.anchor.normalize() &&
            !policy.anchor.toString.exists(_.isControl) &&
            WorkspaceRelativePath.parseC(policy.parentPath.value).isSuccess &&
            WorkspaceRelativePath.parseC(policy.aliasPath.value).isSuccess &&
            policy.sources.nonEmpty && policy.sources.distinct.size == policy.sources.size &&
            policy.sources.toSet == sources.map(_._1).toSet &&
            candidates.count(_ == SessionCandidateWorkspacePolicy(policy.componentId, policy.parent, policy.alias)) == 1 &&
            declared.get(policy.parent).contains(policy.anchor.resolve(policy.parentPath.value).normalize()) &&
            declared.get(policy.alias).contains(policy.anchor.resolve(policy.aliasPath.value).normalize()) &&
            (policy.sources ++ Vector(policy.parent, policy.alias)).forall(id =>
              grants.contains(WorkspaceGrant(policy.componentId, id, WorkspacePermission.Manage)))
          } && targets.indices.forall(i => targets.drop(i + 1).forall(other => !_overlap_(targets(i), other))) &&
          targets.forall(target => sources.forall { case (_, source) => !_overlap_(target, source) }))
        Consequence.unit else invalidC("Invalid initialization authority, source separation or root overlap")
      _ <- _sequence_c(policies.map(x => identityC(x.anchor)))
      _ <- _sequence_c(sources.map { case (id, path) => WorkspaceBinding.createC(id, path) })
      // Inspect every prefix and every noninitialized root before the first effect.
      paths = (policies.flatMap(x => _prefixes(x.anchor)) ++ sources.flatMap(x => _prefixes(x._2)) ++
        targets.flatMap(_prefixes)).distinct.sortBy(_.getNameCount)
      identities <- _sequence_c(paths.filter(Files.exists(_, LinkOption.NOFOLLOW_LINKS)).map(identityC))
      missing = paths.filterNot(path => identities.exists(_.path == path))
      aliases = policies.map(x => declared(x.alias))
      _ <- verifyC(identities, missing, aliases)
    } yield new PreparedWorkspaceRootInitialization(identities, missing, aliases)
  }

  private def _prefixes(path: Path): Vector[Path] =
    Vector(path.getRoot) ++ (1 to path.getNameCount).map(n => path.getRoot.resolve(path.subpath(0, n)))

  private[workspace] def identityC(path: Path): Consequence[WorkspaceDirectoryIdentity] =
    try {
      val attributes = Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
      if (attributes.isDirectory && !attributes.isSymbolicLink && attributes.fileKey() != null && path.toRealPath() == path)
        Consequence.success(WorkspaceDirectoryIdentity(path, attributes.fileKey()))
      else invalidC("Initialization requires unchanged ordinary directories without symlink components")
    } catch { case NonFatal(_) => invalidC("Initialization directory is unavailable") }

  private[workspace] def verifyC(identities: Vector[WorkspaceDirectoryIdentity], missing: Vector[Path],
    aliases: Vector[Path]): Consequence[Unit] = for {
    _ <- identities.foldLeft(Consequence.unit) { (prior, expected) => prior.flatMap { _ =>
      identityC(expected.path).flatMap(actual => if (actual == expected) Consequence.unit
        else invalidC("Initialization directory was rebound; created prefixes are retained"))
    } }
    _ <- if (missing.forall(Files.notExists(_, LinkOption.NOFOLLOW_LINKS))) Consequence.unit
      else invalidC("Initialization path changed; created prefixes are retained")
    _ <- aliases.foldLeft(Consequence.unit) { (prior, alias) => prior.flatMap { _ =>
      if (Files.notExists(alias, LinkOption.NOFOLLOW_LINKS)) Consequence.unit
      else identityC(alias).flatMap { _ =>
        try {
          val empty = Using.resource(Files.list(alias))(stream => !stream.findAny().isPresent)
          if (empty) Consequence.unit else invalidC("Opted-in dormant alias must be empty")
        } catch { case NonFatal(_) => invalidC("Dormant alias is unavailable") }
      }
    } }
  } yield ()

  private def _sequence_c[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (prior, next) => for {
      previous <- prior
      value <- next
    } yield previous :+ value }

  private[workspace] def invalidC[A](message: String): Consequence[A] =
    Consequence.configurationInvalid(s"Workspace root initialization: $message")
}
