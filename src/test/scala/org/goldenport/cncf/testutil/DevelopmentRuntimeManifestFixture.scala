package org.goldenport.cncf.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

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
    val entries = Vector(
      "target/cncf.d/runtime-classpath.txt",
      "target/cncf.d/component-descriptor.json",
      "src/main/car/abi-manifest.json"
    )
    val jsonentries = entries.map(path => s"{\"path\":\"$path\"}").mkString("[", ",", "]")
    Files.writeString(
      evidence.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v2","sourceKind":"development-directory","car":{"name":"${coordinate.mavenArtifactId()}","version":"$version","component":"${componentid.localId.value()}"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$jsonentries}""",
      StandardCharsets.UTF_8
    )
  }

  private def _canonical_component_id(component: String): String = {
    val local = component.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map(_.capitalize).mkString
    s"org.goldenport.cncf.test.$local"
  }

}
