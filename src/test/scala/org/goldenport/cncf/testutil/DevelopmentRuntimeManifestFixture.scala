package org.goldenport.cncf.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate

/*
 * @since   Jul. 29, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object DevelopmentRuntimeManifestFixture {
  def write(root: Path, classDir: Path, name: String, version: String, component: String): Unit = {
    val cardir = root.resolve("src/main/car")
    val evidence = root.resolve("target/cncf.d")
    val classpath = evidence.resolve("runtime-classpath.txt")
    val descriptor = evidence.resolve("component-descriptor.json")
    val sourcedescriptor = cardir.resolve("component-descriptor.json")
    val abi = cardir.resolve("abi-manifest.json")
    val componentid = ComponentId(_canonical_component_id(component))
    val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, version)
    val descriptorjson =
      s"""{"schemaVersion":3,"component":{"namespace":"${componentid.namespace.value()}","id":"${componentid.localId.value()}","version":"$version"}}"""
    Files.createDirectories(cardir)
    Files.createDirectories(evidence)
    Files.writeString(classpath, classDir.toString, StandardCharsets.UTF_8)
    Files.writeString(descriptor, descriptorjson, StandardCharsets.UTF_8)
    Files.writeString(sourcedescriptor, descriptorjson, StandardCharsets.UTF_8)
    Files.writeString(
      abi,
      s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"${componentid.namespace.value()}","id":"${componentid.localId.value()}","version":"$version"},"abi":{"version":1,"exports":{"components":[{"namespace":"${componentid.namespace.value()}","id":"${componentid.localId.value()}"}]},"dependencies":[]}}""",
      StandardCharsets.UTF_8
    )
    val logical = _sha256(s"project:${root.relativize(classDir).toString.replace('\\', '/')}".getBytes(StandardCharsets.UTF_8))
    val entries = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(classpath), Some(logical)),
      ("target/cncf.d/component-descriptor.json", _sha256(descriptor), None),
      ("src/main/car/abi-manifest.json", _sha256(abi), None)
    )
    val jsonentries = entries.map { case (path, digest, logicaldigest) =>
      val logicalfield = logicaldigest.map(value => s"\"logicalSha256\":\"$value\",").getOrElse("")
      s"{$logicalfield\"path\":\"$path\",\"sha256\":\"$digest\"}"
    }.mkString("[", ",", "]")
    val digest = _sha256(entries.map { case (path, value, logicaldigest) => s"$path\t$value\t${logicaldigest.getOrElse("")}" }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(
      evidence.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v2","sourceKind":"development-directory","car":{"name":"${coordinate.mavenArtifactId()}","version":"$version","component":"${componentid.localId.value()}"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$jsonentries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$digest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _canonical_component_id(component: String): String = {
    val local = component.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map(_.capitalize).mkString
    s"org.goldenport.cncf.test.$local"
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
}
