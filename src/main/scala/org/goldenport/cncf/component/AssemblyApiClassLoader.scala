package org.goldenport.cncf.component

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.jar.JarInputStream
import java.util.zip.ZipFile

import scala.util.Using

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence

/*
 * Assembly-owned component API class identity. API JARs are metadata inputs,
 * not component discovery inputs, so their classes are never factory-scanned.
 *
 * @since   Jul. 12, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AssemblyApiContract(
  componentName: String,
  componentVersion: String,
  apiClass: String,
  packages: Vector[String],
  abiHash: String,
  artifactPath: String
)

final case class AssemblyApiArtifact(
  contract: AssemblyApiContract,
  classes: Map[String, Array[Byte]]
)

final case class AssemblyApiRequirement(
  componentName: String,
  componentVersion: String,
  apiClass: String,
  required: Boolean
)

final case class AssemblyApiMetadata(
  artifacts: Vector[AssemblyApiArtifact] = Vector.empty,
  requirements: Vector[AssemblyApiRequirement] = Vector.empty
) {
  def ++(rhs: AssemblyApiMetadata): AssemblyApiMetadata =
    AssemblyApiMetadata(artifacts ++ rhs.artifacts, requirements ++ rhs.requirements)
}

trait AssemblyApiClassIdentity {
  def isComponentApiClass(className: String): Boolean
}

final class AssemblyApiClassLoader private (
  parent: ClassLoader,
  artifacts: Vector[AssemblyApiArtifact]
) extends ClassLoader(parent) with AssemblyApiClassIdentity {
  private val _packages = artifacts.flatMap(_.contract.packages).distinct.sorted
  private val _classes = artifacts.flatMap(_.classes).toMap

  override def isComponentApiClass(className: String): Boolean =
    _packages.exists(p => className == p || className.startsWith(s"$p."))

  override protected def loadClass(name: String, resolve: Boolean): Class[?] = synchronized {
    Option(findLoadedClass(name)).getOrElse {
      val loaded =
        if (isComponentApiClass(name) && _classes.contains(name))
          findClass(name)
        else
          super.loadClass(name, false)
      if (resolve) resolveClass(loaded)
      loaded
    }
  }

  override protected def findClass(name: String): Class[?] =
    _classes.get(name) match {
      case Some(bytes) => defineClass(name, bytes, 0, bytes.length)
      case None => throw new ClassNotFoundException(name)
    }
}

object AssemblyApiClassLoader {
  val DESCRIPTOR_PATH = "component-api-descriptor.json"

  @deprecated("Use DESCRIPTOR_PATH.", "0.5.2")
  val DescriptorPath = DESCRIPTOR_PATH

  def create(
    parent: ClassLoader,
    artifacts: Vector[AssemblyApiArtifact]
  ): Consequence[ClassLoader] =
    create(parent, AssemblyApiMetadata(artifacts = artifacts))

  def create(
    parent: ClassLoader,
    metadata: AssemblyApiMetadata
  ): Consequence[ClassLoader] =
    validate(metadata).map { validated =>
      if (validated.isEmpty) parent
      else new AssemblyApiClassLoader(parent, validated)
    }

  def validate(
    artifacts: Vector[AssemblyApiArtifact]
  ): Consequence[Vector[AssemblyApiArtifact]] =
    validate(AssemblyApiMetadata(artifacts = artifacts))

  def validate(
    metadata: AssemblyApiMetadata
  ): Consequence[Vector[AssemblyApiArtifact]] = {
    val artifacts = metadata.artifacts
    val contractconflicts = artifacts.groupBy(_.contract.apiClass).toVector.flatMap {
      case (api, xs) =>
        val identities = xs.map(x => (
          x.contract.componentName,
          x.contract.componentVersion,
          x.contract.abiHash
        )).distinct
        if (identities.size <= 1) Vector.empty
        else Vector(s"$api=${identities.map { case (n, v, h) => s"$n@$v#$h" }.mkString(",")}")
    }
    val classconflicts = artifacts.flatMap(_.classes.toVector).groupBy(_._1).toVector.flatMap {
      case (name, values) =>
        val contents = values.map(_._2.toVector).distinct
        if (contents.size <= 1) Vector.empty else Vector(name)
    }
    val providedclasses = artifacts.map(_.contract.apiClass).toSet
    val missingrequirements = metadata.requirements
      .filter(_.required)
      .filterNot(x => providedclasses.contains(x.apiClass))
      .map(x => s"${x.componentName}@${x.componentVersion}->${x.apiClass}")
      .distinct
      .sorted
    if (contractconflicts.nonEmpty || classconflicts.nonEmpty || missingrequirements.nonEmpty) {
      Consequence.configurationInvalid(
        Vector(
          Option.when(contractconflicts.nonEmpty)(s"component API contract conflicts: ${contractconflicts.sorted.mkString("; ")}"),
          Option.when(classconflicts.nonEmpty)(s"component API class conflicts: ${classconflicts.sorted.mkString(",")}"),
          Option.when(missingrequirements.nonEmpty)(
            s"required component APIs are missing: ${missingrequirements.mkString(",")}; " +
              s"provided APIs: ${providedclasses.toVector.sorted.mkString(",")}"
          )
        ).flatten.mkString("; ")
      )
    } else {
      Consequence.success(artifacts.distinctBy(x => (x.contract.apiClass, x.contract.abiHash)))
    }
  }

  def loadCar(path: Path): Consequence[AssemblyApiMetadata] = Consequence {
    Using.resource(new ZipFile(path.toFile)) { zip =>
      Option(zip.getEntry(DESCRIPTOR_PATH)).map { entry =>
        val descriptortext = Using.resource(zip.getInputStream(entry)) { in =>
          new String(in.readAllBytes(), StandardCharsets.UTF_8)
        }
        val descriptor = _parse_descriptor(descriptortext)
        val artifacts = descriptor.contracts.map { contract =>
          val jarentry = Option(zip.getEntry(contract.artifactPath)).getOrElse {
            throw new IllegalArgumentException(
              s"declared component API artifact is missing: car=$path artifact=${contract.artifactPath}"
            )
          }
          val jarbytes = Using.resource(zip.getInputStream(jarentry))(_.readAllBytes())
          AssemblyApiArtifact(contract, _classes(jarbytes))
        }
        AssemblyApiMetadata(artifacts, descriptor.requirements)
      }.getOrElse(AssemblyApiMetadata())
    }
  }

  def loadDirectory(path: Path): Consequence[AssemblyApiMetadata] = Consequence {
    val descriptor = path.resolve(DESCRIPTOR_PATH)
    if (!Files.isRegularFile(descriptor)) {
      AssemblyApiMetadata()
    } else {
      val parsed = _parse_descriptor(Files.readString(descriptor))
      val artifacts = parsed.contracts.map { contract =>
        val jar = path.resolve(contract.artifactPath).normalize
        if (!jar.startsWith(path.normalize) || !Files.isRegularFile(jar))
          throw new IllegalArgumentException(
            s"declared component API artifact is missing: car=$path artifact=${contract.artifactPath}"
          )
        AssemblyApiArtifact(contract, _classes(Files.readAllBytes(jar)))
      }
      AssemblyApiMetadata(artifacts, parsed.requirements)
    }
  }

  def loadSar(path: Path): Consequence[AssemblyApiMetadata] = Consequence {
    Using.resource(new ZipFile(path.toFile)) { zip =>
      val carentries = zip.entries()
      var metadata = AssemblyApiMetadata()
      while (carentries.hasMoreElements) {
        val entry = carentries.nextElement()
        if (!entry.isDirectory && entry.getName.endsWith(".car")) {
          val bytes = Using.resource(zip.getInputStream(entry))(_.readAllBytes())
          metadata = metadata ++ _load_car_bytes(bytes, s"$path!/${entry.getName}")
        }
      }
      metadata
    }
  }

  def loadSarDirectory(path: Path): Consequence[AssemblyApiMetadata] =
    SarExtractor.resolveDirectory(path).flatMap { extracted =>
      val carfiles = extracted.carArtifacts.foldLeft(Consequence.success(AssemblyApiMetadata())) { (z, car) =>
        for {
          acc <- z
          metadata <- loadCar(car)
        } yield acc ++ metadata
      }
      extracted.carDirectories.foldLeft(carfiles) { (z, car) =>
        for {
          acc <- z
          metadata <- loadDirectory(car)
        } yield acc ++ metadata
      }
    }

  private final case class ParsedDescriptor(
    contracts: Vector[AssemblyApiContract],
    requirements: Vector[AssemblyApiRequirement]
  )

  private def _parse_descriptor(text: String): ParsedDescriptor = {
    val json = parse(text).fold(throw _, identity)
    val component = json.hcursor.downField("component")
    val componentname = component.get[String]("name").fold(throw _, identity)
    val componentversion = component.get[String]("version").fold(throw _, identity)
    val contracts = json.hcursor.get[Vector[Json]]("provided").getOrElse(Vector.empty).map { value =>
      val c = value.hcursor
      AssemblyApiContract(
        componentName = componentname,
        componentVersion = componentversion,
        apiClass = c.get[String]("apiClass").fold(throw _, identity),
        packages = c.get[Vector[String]]("packages").getOrElse(Vector.empty),
        abiHash = c.get[String]("abiHash").fold(throw _, identity),
        artifactPath = c.get[String]("artifactPath").fold(throw _, identity)
      )
    }
    val requirements = json.hcursor.get[Vector[Json]]("required").getOrElse(Vector.empty).map { value =>
      val c = value.hcursor
      AssemblyApiRequirement(
        componentName = componentname,
        componentVersion = componentversion,
        apiClass = c.get[String]("apiClass").fold(throw _, identity),
        required = c.get[Boolean]("required").getOrElse(true)
      )
    }
    ParsedDescriptor(contracts, requirements)
  }

  private def _load_car_bytes(bytes: Array[Byte], source: String): AssemblyApiMetadata = {
    var descriptortext: Option[String] = None
    val jars = Map.newBuilder[String, Array[Byte]]
    Using.resource(new JarInputStream(new ByteArrayInputStream(bytes))) { car =>
      var entry = car.getNextJarEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.getName == DESCRIPTOR_PATH)
          descriptortext = Some(new String(car.readAllBytes(), StandardCharsets.UTF_8))
        else if (!entry.isDirectory && entry.getName.startsWith("spi/") && entry.getName.endsWith(".jar"))
          jars += entry.getName -> car.readAllBytes()
        car.closeEntry()
        entry = car.getNextJarEntry
      }
    }
    descriptortext.map { text =>
      val descriptor = _parse_descriptor(text)
      val jarentries = jars.result()
      val artifacts = descriptor.contracts.map { contract =>
        val jarbytes = jarentries.getOrElse(
          contract.artifactPath,
          throw new IllegalArgumentException(
            s"declared component API artifact is missing: car=$source artifact=${contract.artifactPath}"
          )
        )
        AssemblyApiArtifact(contract, _classes(jarbytes))
      }
      AssemblyApiMetadata(artifacts, descriptor.requirements)
    }.getOrElse(AssemblyApiMetadata())
  }

  private def _classes(jarbytes: Array[Byte]): Map[String, Array[Byte]] = {
    val builder = Map.newBuilder[String, Array[Byte]]
    Using.resource(new JarInputStream(new ByteArrayInputStream(jarbytes))) { jar =>
      var entry = jar.getNextJarEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.getName.endsWith(".class")) {
          val name = entry.getName.stripSuffix(".class").replace('/', '.')
          val out = new ByteArrayOutputStream()
          jar.transferTo(out)
          builder += name -> out.toByteArray
        }
        jar.closeEntry()
        entry = jar.getNextJarEntry
      }
    }
    builder.result()
  }
}
