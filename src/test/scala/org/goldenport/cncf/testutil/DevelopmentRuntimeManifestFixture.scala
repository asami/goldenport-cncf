package org.goldenport.cncf.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import org.goldenport.cncf.CncfVersion

object DevelopmentRuntimeManifestFixture {
  def write(root: Path, classdir: Path, name: String, version: String, component: String): Unit = {
    val cardir = root.resolve("src/main/car")
    val evidence = root.resolve("target/cncf.d")
    val classpath = evidence.resolve("runtime-classpath.txt")
    Files.createDirectories(cardir)
    Files.createDirectories(evidence)
    Files.writeString(classpath, classdir.toString, StandardCharsets.UTF_8)
    val descriptor = cardir.resolve("component-descriptor.json")
    val abi = cardir.resolve("abi-manifest.json")
    Files.writeString(descriptor, s"""{"name":"$name","version":"$version","component":"$component"}""", StandardCharsets.UTF_8)
    Files.writeString(abi, s"""{"format":"cozy.car.abi-manifest.v1","car":{"name":"$name","version":"$version"},"abi":{"exports":{"components":[{"name":"$component"}]}}}""", StandardCharsets.UTF_8)
    val logical = _sha256(s"project:${root.relativize(classdir).toString.replace('\\', '/')}".getBytes(StandardCharsets.UTF_8))
    val entries = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(classpath), Some(logical)),
      ("src/main/car/component-descriptor.json", _sha256(descriptor), None),
      ("src/main/car/abi-manifest.json", _sha256(abi), None)
    )
    val jsonentries = entries.map { case (path, digest, logicaldigest) =>
      val logicalfield = logicaldigest.map(value => s"\"logicalSha256\":\"$value\",").getOrElse("")
      s"{$logicalfield\"path\":\"$path\",\"sha256\":\"$digest\"}"
    }.mkString("[", ",", "]")
    val digest = _sha256(entries.map { case (path, value, logicaldigest) => s"$path\t$value\t${logicaldigest.getOrElse("")}" }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(
      evidence.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v1","sourceKind":"development-directory","car":{"name":"$name","version":"$version","component":"$component"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$jsonentries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$digest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
}
