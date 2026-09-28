package org.goldenport.cncf.http

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.cncf.config.OperationMode
import org.goldenport.record.Record

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] trait WebDescriptorAssetPart { self: WebDescriptor.type =>
  import WebDescriptor.*

  private[http] def _load_descriptor_files(
    files: Vector[Path]
  ): Consequence[WebDescriptor] =
    files.foldLeft(Consequence.success(WebDescriptor.empty)) { (r, file) =>
      r.flatMap { descriptor =>
        _load_descriptor_file(file).map(descriptor.mergeOverride)
      }
    }

  private[http] def _load_descriptor_file(
    path: Path
  ): Consequence[WebDescriptor] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption match {
        case Some(record) => _validate(fromRecord(record), path)
        case None => Consequence.resourceInvalid(s"web descriptor is empty: ${path}")
      }
    }

  private[http] def _assets(record: Record): Assets =
    _record_value(record, "assets")
      .orElse(_record_value(record, "asset"))
      .flatMap(_any_to_record)
      .map { r =>
        Assets(
          autoComplete = _boolean(r, "autoComplete")
            .orElse(_boolean(r, "auto-complete"))
            .getOrElse(true),
          favicon = _string(r, "favicon").orElse(_string(r, "icon")),
          css = _string_vector(r, "css") ++ _string_vector(r, "styles"),
          js = _string_vector(r, "js") ++ _string_vector(r, "scripts")
        )
      }.getOrElse(Assets())

  private[http] def _theme(record: Record): Theme =
    _record_value(record, "theme")
      .orElse(_record_value(record, "themes"))
      .map { r =>
        Theme(
          name = _string(r, "name"),
          css = _string_vector(r, "css") ++ _string_vector(r, "styles"),
          variables = _record_value(r, "variables")
            .map(_.asMap.toVector.map { case (key, value) => key -> value.toString }.toMap)
            .getOrElse(Map.empty)
        )
      }.getOrElse(Theme())

  private[http] def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".car") || name.endsWith(".zip")
  }

  private[http] def _descriptor_files(root: Path): Vector[Path] =
    Vector(
      root.resolve("car.d").resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("car.d").resolve("web").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("car").resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("src").resolve("main").resolve("car").resolve("web").resolve("web.yaml"),
      root.resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("web").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("form.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("admin.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("web-descriptor.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("web.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("form.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("admin.yaml")
    )

  private[http] def _load_archive_file(path: Path): Consequence[WebDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      _descriptor_files(fs.getPath("/")).filter(Files.isRegularFile(_)) match {
        case Vector() => Consequence.resourceNotFound(s"web descriptor not found in archive: ${path}")
        case files => _load_descriptor_files(files)
      }
    }
  }
}
