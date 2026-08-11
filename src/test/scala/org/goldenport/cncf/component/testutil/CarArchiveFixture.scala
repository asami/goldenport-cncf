package org.goldenport.cncf.component.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Using

import io.circe.parser.parse

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{CarRuntimeAdmission, ComponentId}
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate

/*
 * @since   Jul. 28, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object CarArchiveFixture {
  def write(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit = {
    val sourceentries = entries.map { case (name, path) =>
      name -> Files.readAllBytes(path)
    }
    val archiveentries = _with_runtime_evidence(sourceentries)
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      archiveentries.foreach { case (name, bytes) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
  }

  private def _with_runtime_evidence(
    entries: Seq[(String, Array[Byte])]
  ): Seq[(String, Array[Byte])] = {
    val names = entries.map(_._1).toSet
    if (names.contains(CarRuntimeAdmission.MANIFEST_FILE)) {
      entries
    } else {
      _component_coordinate(entries).map { case (name, version, component) =>
        val withabi =
          if (names.contains(CarRuntimeAdmission.ABI_MANIFEST_FILE))
            entries
          else
            entries :+ (
              CarRuntimeAdmission.ABI_MANIFEST_FILE ->
                _abi_manifest(name, version, component).getBytes(StandardCharsets.UTF_8)
            )
        withabi :+ (
          CarRuntimeAdmission.MANIFEST_FILE ->
            _runtime_manifest(name, version, component, withabi).getBytes(StandardCharsets.UTF_8)
        )
      }.getOrElse(entries)
    }
  }

  private def _component_coordinate(
    entries: Seq[(String, Array[Byte])]
  ): Option[(String, String, String)] =
    entries.find(_._1 == "component-descriptor.json").flatMap { case (_, bytes) =>
      parse(new String(bytes, StandardCharsets.UTF_8)).toOption.flatMap { json =>
        val cursor = json.hcursor
        cursor.get[Int]("schemaVersion").toOption match {
          case Some(3) =>
            for {
              namespace <- cursor.downField("component").get[String]("namespace").toOption
                .map(_.trim).filter(_.nonEmpty)
              localid <- cursor.downField("component").get[String]("id").toOption
                .map(_.trim).filter(_.nonEmpty)
              version <- cursor.downField("component").get[String]("version").toOption
                .map(_.trim).filter(_.nonEmpty)
              componentid = s"$namespace.$localid"
              id <- ComponentId.parseC(componentid).toOption
              coordinate <- {
                val result = ComponentReleaseCoordinate.create(id.sharedIdentity, version)
                if (result.isSuccess()) Some(result.value().get()) else None
              }
            } yield (coordinate.mavenArtifactId(), version, id.name)
          case _ =>
            val nestedname = cursor.downField("component").get[String]("name").toOption
            val name = cursor.get[String]("name").toOption.orElse(nestedname)
            val component = cursor.get[String]("component").toOption
              .orElse(cursor.get[String]("componentName").toOption)
              .orElse(nestedname)
              .orElse(name)
            val version = cursor.get[String]("version").toOption
            for {
              v <- version.map(_.trim).filter(_.nonEmpty)
              c <- component.map(_.trim).filter(_.nonEmpty)
              coordinate <- ComponentId.parseC(c).toOption match {
                case Some(componentid) =>
                  val result = ComponentReleaseCoordinate.create(componentid.sharedIdentity, v)
                  if (result.isSuccess())
                    Some((result.value().get().mavenArtifactId(), v, componentid.name))
                  else
                    None
                case None =>
                  name.map(_.trim).filter(_.nonEmpty).map(n => (n, v, c))
              }
            } yield coordinate
        }
      }
    }

  private def _abi_manifest(
    name: String,
    version: String,
    component: String
  ): String =
    ComponentId.parseC(component).toOption match {
      case Some(componentid) =>
        s"""{
           |  "format": "${CarRuntimeAdmission.ABI_MANIFEST_FORMAT}",
           |  "component": {
           |    "namespace": ${_json_string(componentid.namespace.value())},
           |    "id": ${_json_string(componentid.localId.value())},
           |    "version": ${_json_string(version)}
           |  },
           |  "abi": {
           |    "version": 1,
           |    "exports": {"components": [{"namespace": ${_json_string(componentid.namespace.value())}, "id": ${_json_string(componentid.localId.value())}}]},
           |    "dependencies": []
           |  }
           |}
           |""".stripMargin
      case None =>
        s"""{
           |  "format": "cozy.car.abi-manifest.v1",
           |  "car": {"name": ${_json_string(name)}, "version": ${_json_string(version)}},
           |  "abi": {
           |    "version": 1,
           |    "exports": {"components": [{"name": ${_json_string(component)}}]},
           |    "dependencies": []
           |  }
           |}
           |""".stripMargin
    }

  private def _runtime_manifest(
    name: String,
    version: String,
    component: String,
    entries: Seq[(String, Array[Byte])]
  ): String = {
    val integrity = entries.sortBy(_._1).map { case (path, bytes) =>
      s"""{"path":${_json_string(path)},"sha256":${_json_string(_sha256(bytes))}}"""
    }.mkString("[", ",", "]")
    s"""{
       |  "schemaVersion": "${CarRuntimeAdmission.MANIFEST_SCHEMA}",
       |  "car": {
       |    "name": ${_json_string(name)},
       |    "version": ${_json_string(version)},
       |    "component": ${_json_string(component)}
       |  },
       |  "runtime": {
       |    "cncf": {
       |      "minimum": "0.0.0",
       |      "maximum": null,
       |      "excluded": [],
       |      "tested": [${_json_string(CncfVersion.current)}]
       |    }
       |  },
       |  "integrity": {
       |    "algorithm": "SHA-256",
       |    "entries": $integrity
       |  }
       |}
       |""".stripMargin
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x")
      .mkString

  private def _json_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
