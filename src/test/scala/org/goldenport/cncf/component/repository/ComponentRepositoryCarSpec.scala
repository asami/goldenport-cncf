package org.goldenport.cncf.component.repository

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    val workArea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workArea))
  }

  "ComponentDirRepository" should {

    "discover the component wrapped in a car" in {
      val subsystem = new Subsystem(
        name = "test",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val samplejar = Paths.get("component.d").resolve("cncf-helloworld-sbt_3-0.1.0.jar")
        assume(Files.exists(samplejar), "Missing sample component jar for the test")
        val carpath = componentdir.resolve("demo-component.car")
        _create_car(carpath, Seq("component/main.jar" -> samplejar))
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        val components = repository.discover()
        components should not be empty
      }
    }

    "skip an invalid car containing zero component jars" in {
      val subsystem = new Subsystem(
        name = "test-skip",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val dummyjar = Files.createTempFile("dummy", ".jar")
        try {
          val carpath = componentdir.resolve("invalid.car")
          _create_car(carpath, Seq("component/lib/dummy.jar" -> dummyjar))
          val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
          val components = repository.discover()
          components shouldBe empty
        } finally {
          Files.deleteIfExists(dummyjar)
        }
      }
    }
  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit = {
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      entries.foreach { case (name, file) =>
        zos.putNextEntry(new ZipEntry(name))
        Files.copy(file, zos)
        zos.closeEntry()
      }
    }
  }

  private def _with_temp_dir[T](body: Path => T): T = {
    val base = Files.createTempDirectory("component-repo-spec")
    try body(base)
    finally {
      _delete_recursively(base)
    }
  }

  private def _delete_recursively(base: Path): Unit = {
    if (Files.exists(base)) {
      Using.resource(Files.walk(base)) { stream =>
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(p => Files.deleteIfExists(p))
      }
    }
  }
}
