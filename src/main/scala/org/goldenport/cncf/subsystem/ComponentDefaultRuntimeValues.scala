package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal

/** Environment data for explicitly templated, selected CAR defaults only.
  * Reads ordinary Git metadata/configuration and locates the existing tool;
  * it never launches a process or creates a repository, identity or grant.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
private[cncf] object ComponentDefaultRuntimeValues {
  private val _section = """\[\s*([A-Za-z0-9.-]+)(?:\s+"(.*)")?\s*\]""".r
  private val _token = """\$\{(runtime\.root|repository\.root|git\.branch|git\.remote|git\.remote-branch|git\.endpoint|git\.user-name|git\.user-email|executable\.sbt|environment\.path|environment\.java-home|environment\.ssh-auth-sock)\}""".r
  private type Config = Map[(String, String, String), String]

  def values(root: Path, home: Path = Paths.get(System.getProperty("user.home")),
    executablePath: Vector[Path] = sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator)
      .filter(_.nonEmpty).toVector.map(Paths.get(_))): Map[String, String] = {
    val base = Map("runtime.root" -> root.toAbsolutePath.normalize.toString,
      "environment.path" -> executablePath.map(_.toString).mkString(java.io.File.pathSeparator),
      "environment.java-home" -> sys.env.getOrElse("JAVA_HOME", ""),
      "environment.ssh-auth-sock" -> sys.env.getOrElse("SSH_AUTH_SOCK", ""))
    try {
      val executable = executablePath.map(_.resolve("sbt")).find(p => Files.isRegularFile(p) && Files.isExecutable(p))
        .map(p => "executable.sbt" -> p.toAbsolutePath.normalize.toString).toMap
      val repository = _repository(root).flatMap { case (worktree, gitdir, common) =>
        val head = _read(gitdir.resolve("HEAD")).map(_.trim).filter(_.startsWith("ref: refs/heads/"))
          .map(_.stripPrefix("ref: refs/heads/"))
        head.map { branch =>
          val ordinary = _config(home.resolve(".config/git/config"), home) ++ _config(home.resolve(".gitconfig"), home) ++
            _config(common.resolve("config"), home)
          val worktreeConfig = ordinary.get(("extensions", "", "worktreeconfig"))
            .exists(value => Set("true", "yes", "on", "1")(value.toLowerCase(java.util.Locale.ROOT)))
          val config = ordinary ++ (if (worktreeConfig) _config(gitdir.resolve("config.worktree"), home) else Map.empty)
          def get(section: String, subsection: String, key: String) = config.get((section, subsection, key)).filter(_.nonEmpty)
          val remotes = config.keys.collect { case ("remote", name, "url") => name }.toVector.distinct
          val remote = get("branch", branch, "remote").orElse(if (remotes.contains("origin")) Some("origin")
            else if (remotes.size == 1) remotes.headOption else None)
          val remoteBranch = get("branch", branch, "merge").filter(_.startsWith("refs/heads/"))
            .map(_.stripPrefix("refs/heads/")).getOrElse(branch)
          Map("repository.root" -> worktree.toString, "git.branch" -> branch, "git.remote-branch" -> remoteBranch) ++
            remote.map("git.remote" -> _) ++ remote.flatMap(get("remote", _, "url")).map("git.endpoint" -> _) ++
            get("user", "", "name").map("git.user-name" -> _) ++ get("user", "", "email").map("git.user-email" -> _)
        }
      }.getOrElse(Map.empty)
      base ++ executable ++ repository
    } catch { case NonFatal(_) => base }
  }

  def expand(value: String, values: Map[String, String]): String =
    _token.replaceAllIn(value, found => java.util.regex.Matcher.quoteReplacement(values.getOrElse(found.group(1), found.matched)))

  def missing(value: String, values: Map[String, String]): Boolean =
    _token.findAllMatchIn(value).exists(found => !values.contains(found.group(1)))

  private def _repository(start: Path): Option[(Path, Path, Path)] = {
    var current = Option(start.toAbsolutePath.normalize)
    while (current.nonEmpty) {
      val worktree = current.get
      val dotgit = worktree.resolve(".git")
      val gitdir = if (Files.isDirectory(dotgit)) Some(dotgit)
        else _read(dotgit).map(_.trim).filter(_.startsWith("gitdir:"))
          .map(value => worktree.resolve(value.stripPrefix("gitdir:").trim).normalize)
      if (gitdir.nonEmpty) {
        val dir = gitdir.get
        val common = _read(dir.resolve("commondir")).map(value => dir.resolve(value.trim).normalize).getOrElse(dir)
        return Some((worktree, dir, common))
      }
      current = Option(worktree.getParent)
    }
    None
  }

  private def _read(path: Path): Option[String] =
    if (Files.isRegularFile(path)) Some(Files.readString(path)) else None

  /** The ordinary INI-shaped Git configuration, including unconditional
    * include files, quoted values, escapes and comments. Unknown sections are
    * inert; absent author or tracking data is never replaced with a fake value.
    */
  private def _config(path: Path, home: Path, seen: Set[Path] = Set.empty): Config = {
    val canonical = path.toAbsolutePath.normalize
    if (seen(canonical)) Map.empty
    else _read(canonical).map { source =>
      var section = ""
      var subsection = ""
      var result = Map.empty[(String, String, String), String]
      source.replace("\\\r\n", "").replace("\\\n", "").linesIterator.foreach { line =>
        line.trim match {
          case _section(name, sub) => section = name.toLowerCase(java.util.Locale.ROOT); subsection = Option(sub).map(_value).getOrElse("")
          case text if text.nonEmpty && !text.startsWith("#") && !text.startsWith(";") =>
            val parts = text.split("=", 2)
            val key = parts(0).trim.toLowerCase(java.util.Locale.ROOT)
            val value = if (parts.length == 2) _value(parts(1).trim) else "true"
            if (section == "include" && key == "path") {
              val include = if (value.startsWith("~/")) home.resolve(value.drop(2))
                else canonical.getParent.resolve(value).normalize
              result ++= _config(include, home, seen + canonical)
            } else result += ((section, subsection, key) -> value)
          case _ => ()
        }
      }
      result
    }.getOrElse(Map.empty)
  }

  private def _value(raw: String): String = {
    val result = new StringBuilder
    var quoted = false
    var escaped = false
    var finished = false
    raw.foreach { char =>
      if (!finished) {
        if (escaped) {
          result += (char match { case 'n' => '\n'; case 't' => '\t'; case 'b' => '\b'; case other => other })
          escaped = false
        } else char match {
          case '\\' => escaped = true
          case '"' => quoted = !quoted
          case '#' | ';' if !quoted => finished = true
          case other => result += other
        }
      }
    }
    result.toString.trim
  }
}
