package org.goldenport.cncf.cli

import java.nio.file.Path
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.cncf.component.cache.{ArchiveKind, ManagedArchiveCache, ManagedCachePolicy, SourceGeneration}

/** Explicit, bounded access to the existing managed-cache owner. */
private[cli] object ManagedCacheOperation {
  private val usage = "cache <inspect|collect|legacy-inspect|legacy-retire> --root PATH " +
    "[--archive PATH] [--kind car|sar] [--key KEY --generation SHA256] [--dry-run|--apply] [--batches N]"

  private[cli] final case class Request(operation: String, root: Path, archive: Option[Path],
    kind: Option[ArchiveKind], key: Option[String], generation: Option[String], applyChanges: Boolean, batches: Int)

  private[cli] def parse(args: Vector[String]): Either[String, Request] = {
    if (args.isEmpty) return Left(usage)
    val operation = args.head
    if (!Set("inspect", "collect", "legacy-inspect", "legacy-retire").contains(operation)) return Left(usage)
    var values = Map.empty[String, String]
    var applyChanges = false
    var dryRunSpecified = false
    var index = 1
    while (index < args.size) {
      val flag = args(index)
      if (flag == "--apply" || flag == "--dry-run") {
        if (applyChanges || dryRunSpecified) return Left("duplicate or conflicting operation mode")
        if (flag == "--apply") applyChanges = true else dryRunSpecified = true
        index += 1
      } else if (Set("--root", "--archive", "--kind", "--key", "--generation", "--batches").contains(flag)) {
        if (values.contains(flag) || index + 1 >= args.size || args(index + 1).startsWith("--")) return Left(usage)
        values += flag -> args(index + 1)
        index += 2
      } else return Left(usage)
    }
    val allowed = operation match {
      case "inspect" => Set("--root")
      case "collect" => Set("--root", "--batches")
      case "legacy-inspect" => Set("--root", "--archive", "--kind")
      case _ => Set("--root", "--kind", "--key", "--generation")
    }
    if (!values.keySet.subsetOf(allowed) || !values.contains("--root") || values.values.exists(_.isEmpty)) return Left(usage)
    if ((applyChanges || dryRunSpecified) && !Set("collect", "legacy-retire").contains(operation)) return Left(usage)
    val kind = values.get("--kind").flatMap(value => ArchiveKind.values.find(_.directory == value))
    if (values.contains("--kind") && kind.isEmpty) return Left("invalid kind")
    if (operation.startsWith("legacy-") && kind.isEmpty) return Left(usage)
    if (operation == "legacy-inspect" && !values.contains("--archive")) return Left(usage)
    if (operation == "legacy-retire" && !Vector("--key", "--generation").forall(key => values.get(key).exists(_.matches("[0-9a-f]{64}")))) return Left(usage)
    val batches = values.get("--batches").map(_.toIntOption).getOrElse(Some(1))
    if (batches.isEmpty || batches.get < 1 || batches.get > 128 || (!applyChanges && batches.get != 1)) return Left("invalid batches")
    try Right(Request(operation, Path.of(values("--root")), values.get("--archive").map(value => Path.of(value)),
      kind, values.get("--key"), values.get("--generation"), applyChanges, batches.get))
    catch { case NonFatal(error) => Left(error.toString) }
  }

  private[cli] def execute(request: Request, output: String => Unit): Int = {
    try {
      Using.resource(new ManagedArchiveCache(ManagedCachePolicy(request.root))) { cache =>
        request.operation match {
          case "inspect" => output(cache.inspectC().TAKE.toString)
          case "collect" =>
            var more = true
            var batch = 0
            while (more && batch < request.batches) {
              val result = cache.collectC(dryRun = !request.applyChanges).TAKE
              output(result.toString)
              more = result.truncated
              batch += 1
            }
          case "legacy-inspect" => output(cache.inspectLegacyCurrentC(request.archive.get, request.kind.get).TAKE.toString)
          case "legacy-retire" =>
            val generation = SourceGeneration(request.kind.get, request.generation.get)
            val accepted = cache.retireLegacyCurrentC(request.key.get, generation, dryRun = !request.applyChanges).TAKE
            output(if (accepted) "matched" else "refused: mismatch or in use; inspect and retry")
            if (!accepted) throw new IllegalStateException("legacy retirement refused")
        }
      }
      0
    } catch { case NonFatal(error) => output(error.toString); 1 }
  }

  def run(args: Array[String]): Int = parse(args.toVector) match {
    case Left(message) => Console.err.println(message); 2
    case Right(request) => execute(request, Console.out.println)
  }
}
