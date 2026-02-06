package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.FileContent
import org.goldenport.process.ShellCommandExecutor
import org.goldenport.tree.{Tree, TreeDir, TreeEntry, TreeLeaf, TreeMeta, TreeNodeMeta}
import org.goldenport.vfs.DirectoryFileSystemView
import org.goldenport.cncf.component.protocol.{DirectoryMode, DockerCommandSpecification, DockerDirectoryOutput, DockerFileOutput}
import org.goldenport.protocol.spec.OperationDefinition

/*
 * @since   Feb.  6, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class DockerCommandComponent(
  mapping: CommandParameterMappingRule =
    CommandParameterMappingRule.Default
) extends ShellCommandComponent(mapping) {

  // Executor for Docker execution (can be a local shell executor or a dedicated executor)
  protected def dockerExecutor: ShellCommandExecutor

  // Docker execution specification for each operation
  protected def dockerSpecification(operation: OperationDefinition): DockerCommandSpecification

  // For example, a container command prefix that should always be included,
  // e.g., Vector("command") → `docker run ... <image> command <op> ...`
  protected def dockerCommandPrefix(operation: OperationDefinition): Vector[String] =
    Vector.empty

  override protected final def shell_Executor: ShellCommandExecutor =
    dockerExecutor

  override protected final def base_Command(operation: OperationDefinition): Vector[String] = {
    val spec = dockerSpecification(operation)
    spec.baseCommand ++
      dockerCommandPrefix(operation)
  }

  protected final def collect_artifacts(
    spec: DockerCommandSpecification
  ): Consequence[(Map[String, FileContent], Map[String, DirectoryFileSystemView], Map[String, Tree[Bag]])] = {
    for {
      files <- collect_file_outputs(spec.outputs.files)
      directories <- collect_directory_views(spec.outputs.directories)
      trees <- collect_materialized_trees(spec.outputs.directories)
    } yield (files, directories, trees)
  }

  protected final def collect_file_outputs(
    outputs: Vector[DockerFileOutput]
  ): Consequence[Map[String, FileContent]] = {
    _sequence(outputs.map { output =>
      val resolved = _safe(s"resolving file ${output.path}") { Path.of(output.path) }
      resolved.flatMap { path =>
        _safe(s"reading file ${path}") { FileContent.create(path) }
          .map(content => output.name -> content)
      }
    }).map(_.toMap)
  }

  protected final def collect_directory_views(
    outputs: Vector[DockerDirectoryOutput]
  ): Consequence[Map[String, DirectoryFileSystemView]] = {
    _sequence(outputs.map { output =>
      _safe(s"resolving directory ${output.path}") { Path.of(output.path) }
        .flatMap { path =>
          _safe(s"opening view for ${path}") { DirectoryFileSystemView(path) }
            .map(view => output.name -> view)
        }
    }).map(_.toMap)
  }

  protected final def collect_materialized_trees(
    outputs: Vector[DockerDirectoryOutput]
  ): Consequence[Map[String, Tree[Bag]]] = {
    val materialized = outputs.filter(_.mode == DirectoryMode.Materialized)
    _sequence(materialized.map { output =>
      _safe(s"resolving directory ${output.path}") { Path.of(output.path) }
        .flatMap(path => _read_tree(path).map(tree => output.name -> tree))
    }).map(_.toMap)
  }

  private def _read_tree(base: Path): Consequence[Tree[Bag]] =
    _read_dir(base).map(dir => Tree(dir, TreeMeta.empty))

  private def _read_dir(base: Path): Consequence[TreeDir[Bag]] = {
    _safe(s"listing directory ${base}") {
      Using.resource(Files.list(base)) { stream =>
        stream.iterator().asScala.toVector.sortBy(_.getFileName.toString)
      }
    }.flatMap { paths =>
      val children = paths.map { path =>
        val name = path.getFileName.toString
        if (Files.isDirectory(path)) {
          _read_dir(path).map(dir => TreeEntry(name, dir))
        } else {
          _read_file(path).map(leaf => TreeEntry(name, leaf))
        }
      }
      _sequence(children).map(TreeDir.apply)
    }
  }

  private def _read_file(path: Path): Consequence[TreeLeaf[Bag]] =
    _safe(s"reading file ${path}") {
      TreeLeaf(Bag.file(path), TreeNodeMeta.empty)
    }
  private def _sequence[A](items: Vector[Consequence[A]]): Consequence[Vector[A]] =
    items.foldLeft(Consequence.success(Vector.empty[A])) { (acc, current) =>
      acc.flatMap(vector => current.map(vector :+ _))
    }

  private def _safe[A](description: String)(body: => A): Consequence[A] =
    try {
      Consequence.success(body)
    } catch {
      case NonFatal(exception) =>
        Consequence.failure(s"${description}: ${exception.getMessage}")
    }

}
