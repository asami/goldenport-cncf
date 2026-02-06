package org.goldenport.cncf.backend.docker

import java.io.IOException
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.tree.{Tree, TreeDir, TreeEntry, TreeLeaf}
import org.goldenport.process.{ShellCommand, ShellCommandExecutor, LocalShellCommandExecutor}

/*
 * @since   Feb.  5, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait DockerAdapter {
  def execute(input: DockerInput): Consequence[DockerOutput]
}

final case class DockerInput(
  image: String,
  files: Tree[Bag],
  command: Vector[String],
  env: Map[String, String] = Map.empty
)

final case class DockerOutput(
  result: org.goldenport.process.ShellCommandResult
)

final class CommandDockerAdapter(
  private val executor: ShellCommandExecutor = new LocalShellCommandExecutor
) extends DockerAdapter {

  override def execute(input: DockerInput): Consequence[DockerOutput] =
    withTempDirectory { workDir =>
      for {
        _ <- writeTree(input.files, workDir)
        result <- if (input.command.isEmpty) {
          // skip docker invocation
          Consequence.success(
            org.goldenport.process.ShellCommandResult(
              exitCode = 0,
              stdout = Bag.empty,
              stderr = Bag.empty,
              files = Map.empty,
              directories = Map.empty
            )
          )
        } else {
          val cmd = ShellCommand(
            command = Vector("docker","run","--rm","-v", s"${workDir.toAbsolutePath}:/work","-w","/work", input.image) ++ input.command,
            workDir = None,
            env = input.env
          )
          executor.execute(cmd)
        }
      } yield DockerOutput(
        result = result.copy(
          files = Map.empty,
          directories = Map(
            "work" -> org.goldenport.vfs.DirectoryFileSystemView(workDir)
          )
        )
      )
    }

  private def writeTree(
    tree: Tree[Bag],
    base: Path
  ): Consequence[Unit] =
    writeDir(tree.root, base)

  private def writeDir(
    dir: TreeDir[Bag],
    base: Path
  ): Consequence[Unit] = {
    dir.children.foldLeft(Consequence.success(())) { (acc, entry) =>
      acc.flatMap { _ =>
        val childPath = base.resolve(entry.name)
        entry.node match {
          case childDir: TreeDir[Bag] =>
            for {
              _ <- safe(s"creating directory $childPath") {
                Files.createDirectories(childPath)
                ()
              }
              _ <- writeDir(childDir, childPath)
            } yield ()
          case TreeLeaf(bag, _) =>
            writeFile(bag, childPath)
        }
      }
    }
  }

  private def writeFile(bag: Bag, path: Path): Consequence[Unit] = {
    for {
      _ <- safe(s"preparing target ${path}") {
        Option(path.getParent).foreach(p => Files.createDirectories(p))
      }
      _ <- safe(s"writing bag to ${path}") {
        Using.resource(Files.newOutputStream(
          path,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING
        )) { out =>
          Using.resource(bag.openInputStream()) { in =>
            in.transferTo(out)
          }
        }
      }
    } yield ()
  }

  private def readTree(base: Path): Consequence[Tree[Bag]] =
    readDir(base).map(Tree(_))

  private def readDir(base: Path): Consequence[TreeDir[Bag]] = {
    val entries =
      safe(s"listing directory $base") {
        Using.resource(Files.list(base)) { stream =>
          stream.iterator().asScala.toVector.sortBy(_.getFileName.toString)
        }
      }

    entries.flatMap { paths =>
      val children = paths.map { path =>
        val name = path.getFileName.toString
        if (Files.isDirectory(path))
          readDir(path).map(node => TreeEntry(name, node))
        else
          readFile(path).map(leaf => TreeEntry(name, leaf))
      }
      sequence(children).map(TreeDir(_))
    }
  }

  private def readFile(path: Path): Consequence[TreeLeaf[Bag]] =
    safe(s"reading file $path") {
      val bytes = Files.readAllBytes(path)
      val bag = Bag.fromBytes(bytes)
      TreeLeaf(bag)
    }

  private def withTempDirectory[A](
    f: Path => Consequence[A]
  ): Consequence[A] = {
    val dir = Files.createTempDirectory("cncf-docker-")
    try f(dir)
    finally deleteRecursively(dir)
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.iterator().asScala.toVector.reverse.foreach(p => Files.deleteIfExists(p))
      catch { case NonFatal(_) => () }
      finally
        stream.close()
    }

  private def sequence[A](xs: Vector[Consequence[A]]): Consequence[Vector[A]] =
    xs.foldLeft(Consequence.success(Vector.empty[A])) { (acc, c) =>
      acc.flatMap(v => c.map(v :+ _))
    }

  private def safe[A](description: String)(body: => A): Consequence[A] =
    try Consequence.success(body)
    catch { case NonFatal(e) => Consequence.failure(s"$description: ${e.getMessage}") }
}

/**
 * A stateful Docker adapter intended for long-lived, stateful Docker images
 * (session-maintained containers). This is intentionally left as a thin shell
 * stub implementation for now.
 */
final class ServerDockerAdapter extends DockerAdapter {
  override def execute(input: DockerInput): Consequence[DockerOutput] =
    Consequence.failure("ServerDockerAdapter is not implemented yet (stateful / session-based adapter)")
}
