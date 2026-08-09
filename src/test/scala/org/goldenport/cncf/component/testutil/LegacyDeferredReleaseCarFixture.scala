package org.goldenport.cncf.component.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.cncf.component.{CarRuntimeAdmission, ComponentDescriptor, ComponentIdentityDeferredReleaseEntry}
import org.simplemodeling.textus.corpus.{CorpusComponent, CorpusComponentFactory, CorpusScalarAction, CorpusScalarActionCall, CorpusScalarOperation}

/*
 * @since   Aug.  8, 2026
 * @version Aug.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object LegacyDeferredReleaseCarFixture {
  val LEGACY_ABI_MANIFEST_FORMAT = "cozy.car.abi-manifest.v1"

  val corpusClasses: Vector[Class[?]] = Vector(
    classOf[CorpusComponent],
    classOf[CorpusComponentFactory],
    classOf[CorpusScalarOperation],
    classOf[CorpusScalarAction],
    classOf[CorpusScalarActionCall]
  )

  def legacyDescriptor(
    entry: ComponentIdentityDeferredReleaseEntry
  ): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(entry.legacyartifact),
      version = Some(entry.release),
      componentName = Some(entry.legacylocalid),
      schemaVersion = Some(2)
    )

  def writeDirectory(
    root: Path,
    entry: ComponentIdentityDeferredReleaseEntry,
    descriptorOverride: Option[ComponentDescriptor] = None,
    classes: Vector[Class[?]] = corpusClasses
  ): Path = {
    Files.createDirectories(root.resolve("component"))
    val descriptor = descriptorOverride.getOrElse(legacyDescriptor(entry))
    Files.writeString(
      root.resolve("component-descriptor.json"),
      _descriptor_json(descriptor),
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve(CarRuntimeAdmission.ABI_MANIFEST_FILE),
      _abi_manifest(entry, descriptor),
      StandardCharsets.UTF_8
    )
    _write_fixture_jar(root.resolve("component").resolve("textus-corpus.jar"), classes)
    root
  }

  private def _descriptor_json(descriptor: ComponentDescriptor): String = {
    val name = descriptor.name.getOrElse("")
    val version = descriptor.version.map(value => s"\"version\":\"$value\",").getOrElse("")
    val component = descriptor.componentName.getOrElse("")
    s"""{"schemaVersion":2,"name":"$name",$version"component":{"name":"$component"},"componentStyle":{"apiVersion":"cncf.textus/v1","provider":"cncf","id":"full-fledged-with-standalone@1","version":1,"parameterSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false},"parameters":{},"provides":{"bundles":["domain.full@1"],"capabilities":["user.fixed-context-compatible@1","user.multi-user@1"],"effective":["domain.aggregate@1","domain.command@1","domain.domain-event@1","domain.entity@1","domain.optimistic-concurrency@1","domain.persistence@1","domain.projection@1","domain.query@1","domain.transaction@1","user.fixed-context-compatible@1","user.multi-user@1"]},"requires":{"subsystemCapabilities":["datastore.optimistic-concurrency@1","datastore.persistent@1","datastore.transactional@1","user-context.current@1"]}}}"""
  }

  def pack(
    source: Path,
    target: Path,
    runtimeManifestDirectory: Boolean = false
  ): Path = {
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      if (runtimeManifestDirectory) {
        zip.putNextEntry(new ZipEntry(s"${CarRuntimeAdmission.MANIFEST_FILE}/"))
        zip.closeEntry()
      }
      Using.resource(Files.walk(source)) { stream =>
        stream.iterator().asScala
          .filter(Files.isRegularFile(_))
          .toVector
          .sortBy(_.toString)
          .foreach { file =>
            val entry = source.relativize(file).toString.replace('\\', '/')
            zip.putNextEntry(new ZipEntry(entry))
            Files.copy(file, zip)
            zip.closeEntry()
          }
      }
    }
    target
  }

  private def _write_fixture_jar(
    target: Path,
    classes: Vector[Class[?]]
  ): Path = {
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      val resources = classes.flatMap { cls =>
        val base = cls.getName.replace('.', '/')
        val loader = cls.getClassLoader
        Vector(s"$base.class", s"$base$$.class")
          .filter(entry => loader.getResource(entry) != null)
          .map(entry => loader -> entry)
      }.distinct
      resources.foreach { case (loader, entry) =>
        val resource = Option(loader.getResourceAsStream(entry))
          .getOrElse(throw new IllegalStateException(s"missing fixture class resource: $entry"))
        zip.putNextEntry(new ZipEntry(entry))
        Using.resource(resource) { in =>
          in.transferTo(zip)
          ()
        }
        zip.closeEntry()
      }
    }
    target
  }

  private def _abi_manifest(
    entry: ComponentIdentityDeferredReleaseEntry,
    descriptor: ComponentDescriptor
  ): String =
    s"""{"format":"${LEGACY_ABI_MANIFEST_FORMAT}","car":{"name":"${descriptor.name.getOrElse(entry.legacyartifact)}","version":"${descriptor.version.getOrElse(entry.release)}"},"abi":{"version":1,"exports":{"components":[{"name":"${descriptor.componentName.getOrElse(entry.legacylocalid)}"}]},"dependencies":[]}}"""
}
