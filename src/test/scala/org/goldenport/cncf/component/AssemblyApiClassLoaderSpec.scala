/*
 * @since   Jul. 12, 2026
 *  version Jul. 29, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
package org.goldenport.cncf.component

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.util.Using

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class AssemblyApiClassLoaderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Assembly API classloader" should {
    "validate compatibility and assembly API admission" which {
    "retain the former descriptor-path symbol as a compatibility alias" in {
      Given("the canonical component API descriptor path constant")

      When("a source client resolves the former public symbol")
      val path = AssemblyApiClassLoader.DescriptorPath

      Then("it receives the canonical descriptor path")
      path shouldBe AssemblyApiClassLoader.DESCRIPTOR_PATH
    }

    "give consumer and provider component loaders one descriptor-declared API class identity" in {
      Given("one component API artifact and two component-local copies of the same class")
      val classname = classOf[AssemblyApiFixture].getName
      val classbytes = _class_bytes(classname)
      val artifact = _artifact("provider", "1.0.0", "sha256:one", classname, classbytes)
      val parent = AssemblyApiClassLoader.create(getClass.getClassLoader, Vector(artifact)).toOption.get
      val localjar = _temporary_jar(classname, classbytes)

      When("consumer and provider loaders resolve the API class")
      Using.resources(
        ComponentLocalFirstClassLoader(Vector(localjar), parent),
        ComponentLocalFirstClassLoader(Vector(localjar), parent)
      ) { (consumer, provider) =>
        val consumerclass = consumer.loadClass(classname)
        val providerclass = provider.loadClass(classname)

        Then("both components receive the class from the assembly API loader")
        consumerclass should be theSameInstanceAs providerclass
        consumerclass.getClassLoader should be theSameInstanceAs parent
      }
    }

    "reject conflicting contract version and ABI identities before component loading" in {
      Given("two different provider identities for the same API class")
      val classname = classOf[AssemblyApiFixture].getName
      val bytes = _class_bytes(classname)
      val first = _artifact("provider", "1.0.0", "sha256:one", classname, bytes)
      val second = _artifact("provider", "2.0.0", "sha256:two", classname, bytes)

      When("the assembly API contracts are validated")
      val result = AssemblyApiClassLoader.create(getClass.getClassLoader, Vector(first, second))

      Then("startup fails with the conflicting API identity")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.display should include (classname)
          conclusion.display should include ("sha256:one")
          conclusion.display should include ("sha256:two")
        case Consequence.Success(value) =>
          fail(s"expected component API conflict but got $value")
      }
    }

    "reject a required API without a provider before any component is instantiated" in {
      Given("a consumer descriptor requiring an API absent from an assembly with one different provided API")
      val providedclass = classOf[AssemblyApiFixture].getName
      val providedbytes = _class_bytes(providedclass)
      val provided = _artifact("provider", "1.0.0", "sha256:one", providedclass, providedbytes)
      val requirement = AssemblyApiRequirement(
        componentName = "consumer",
        componentVersion = "1.0.0",
        apiClass = "example.api.MissingApi",
        required = true
      )

      When("the assembly API metadata is validated")
      val result = AssemblyApiClassLoader.create(
        getClass.getClassLoader,
        AssemblyApiMetadata(artifacts = Vector(provided), requirements = Vector(requirement))
      )

      Then("startup fails with the consumer, missing API identity, and provided API inventory")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.display should include ("consumer@1.0.0")
          conclusion.display should include ("example.api.MissingApi")
          conclusion.display should include ("provided APIs")
          conclusion.display should include (providedclass)
        case Consequence.Success(value) =>
          fail(s"expected missing component API failure but got $value")
      }
    }

    "preserve the runtime parent when the assembly declares no component API" in {
      Given("an assembly without API artifacts")
      val runtimeparent = getClass.getClassLoader

      When("the assembly classloader is created")
      val loader = AssemblyApiClassLoader.create(runtimeparent, Vector.empty).toOption.get

      Then("no additional classloader layer is introduced")
      loader should be theSameInstanceAs runtimeparent
    }

    }

    "load descriptor-declared APIs from CAR and SAR archives" which {
    "load only the descriptor-declared nested API JAR from a CAR" in {
      Given("a CAR with a component API descriptor and nested API JAR")
      val classname = classOf[AssemblyApiFixture].getName
      val bytes = _class_bytes(classname)
      val car = Files.createTempFile("assembly-api-", ".car")
      val apijar = _jar_bytes(classname, bytes)
      val descriptor =
        s"""{"schemaVersion":"cncf.component-api.v1","component":{"name":"provider","version":"1.0.0"},"provided":[{"apiClass":"$classname","packages":["org.goldenport.cncf.component"],"abiHash":"sha256:one","artifactPath":"spi/provider-api.jar"}],"required":[]}"""
      _zip(car, Vector(
        AssemblyApiClassLoader.DESCRIPTOR_PATH -> descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "spi/provider-api.jar" -> apijar
      ))

      When("the CAR API metadata is preflighted")
      val metadata = AssemblyApiClassLoader.loadCar(car).toOption.get

      Then("the declared API class is available without scanning component main")
      metadata.artifacts.map(_.contract.apiClass) shouldBe Vector(classname)
      metadata.artifacts.head.classes.keySet should contain (classname)
    }

    "load a canonical v2 component API descriptor" in {
      Given("a CAR whose component API descriptor carries canonical namespace and ID")
      val classname = classOf[AssemblyApiFixture].getName
      val bytes = _class_bytes(classname)
      val car = Files.createTempFile("assembly-api-canonical-", ".car")
      val descriptor =
        s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.goldenport.fixture","id":"Provider","version":"1.0.0"},"provided":[{"apiClass":"$classname","packages":["org.goldenport.cncf.component"],"abiHash":"sha256:one","artifactPath":"spi/provider-api.jar"}],"required":[]}"""
      _zip(car, Vector(
        AssemblyApiClassLoader.DESCRIPTOR_PATH -> descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "spi/provider-api.jar" -> _jar_bytes(classname, bytes)
      ))

      When("the canonical API metadata is preflighted")
      val metadata = AssemblyApiClassLoader.loadCar(car).toOption.get

      Then("the qualified component identity is retained")
      metadata.artifacts.map(_.contract.componentName) shouldBe Vector("org.goldenport.fixture.Provider")
    }

    "preflight component APIs from CARs nested in a SAR" in {
      Given("a SAR containing one provider CAR with a component API")
      val classname = classOf[AssemblyApiFixture].getName
      val classbytes = _class_bytes(classname)
      val descriptor =
        s"""{"schemaVersion":"cncf.component-api.v1","component":{"name":"provider","version":"1.0.0"},"provided":[{"apiClass":"$classname","packages":["org.goldenport.cncf.component"],"abiHash":"sha256:one","artifactPath":"spi/provider-api.jar"}],"required":[]}"""
      val carbytes = _zip_bytes(Vector(
        AssemblyApiClassLoader.DESCRIPTOR_PATH -> descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "spi/provider-api.jar" -> _jar_bytes(classname, classbytes)
      ))
      val sar = Files.createTempFile("assembly-api-", ".sar")
      _zip(sar, Vector("component/provider.car" -> carbytes))

      When("the SAR assembly is preflighted")
      val metadata = AssemblyApiClassLoader.loadSar(sar).toOption.get

      Then("the nested provider API participates in the assembly classloader")
      metadata.artifacts.map(_.contract.apiClass) shouldBe Vector(classname)
    }
    }
  }

  private def _artifact(
    component: String,
    version: String,
    hash: String,
    classname: String,
    bytes: Array[Byte]
  ): AssemblyApiArtifact =
    AssemblyApiArtifact(
      AssemblyApiContract(
        componentName = component,
        componentVersion = version,
        apiClass = classname,
        packages = Vector("org.goldenport.cncf.component"),
        abiHash = hash,
        artifactPath = "spi/provider-api.jar"
      ),
      Map(classname -> bytes)
    )

  private def _class_bytes(classname: String): Array[Byte] = {
    val resource = classname.replace('.', '/') + ".class"
    Using.resource(getClass.getClassLoader.getResourceAsStream(resource)) { in =>
      Option(in).getOrElse(fail(s"class resource is missing: $resource")).readAllBytes()
    }
  }

  private def _temporary_jar(classname: String, bytes: Array[Byte]): Path = {
    val jar = Files.createTempFile("component-local-api-", ".jar")
    _zip(jar, Vector(classname.replace('.', '/') + ".class" -> bytes))
    jar
  }

  private def _jar_bytes(classname: String, bytes: Array[Byte]): Array[Byte] = {
    _zip_bytes(Vector(classname.replace('.', '/') + ".class" -> bytes))
  }

  private def _zip_bytes(entries: Vector[(String, Array[Byte])]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    Using.resource(new ZipOutputStream(out)) { zip =>
      entries.foreach { case (name, bytes) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    out.toByteArray
  }

  private def _zip(path: Path, entries: Vector[(String, Array[Byte])]): Unit =
    Using.resource(new ZipOutputStream(Files.newOutputStream(path))) { zip =>
      entries.foreach { case (name, bytes) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
}

final class AssemblyApiFixture
