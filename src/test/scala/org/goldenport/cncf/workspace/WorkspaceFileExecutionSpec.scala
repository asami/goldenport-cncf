package org.goldenport.cncf.workspace

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFileAttributeView
import java.util.Comparator
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.zip.{CRC32, GZIPOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.effect.ExternalEffectKind
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkspaceFileExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _id = WorkspaceId.parseC("project").toOption.get
  private val _target = FileTarget(_id)
  private val _owner = ComponentId("org.goldenport.spec.FileOwner")

  "Managed directory preparation" should {
    "prepare nested literal directories and report actual ordinary directory and file outcomes" in {
      Given("an admitted existing root and an ordinary file occupying another directory intent")
      _with_root { root =>
        val context = _context(root)
        val path = _path("archive space/日本\n\\/leaf")
        Files.write(root.resolve("occupied"), Array[Byte](1, 2))
        When("Manage prepares a missing nested directory twice and encounters ordinary file occupants")
        val created = WorkspaceFileExecution.prepareDirectoryC(context, _target, path)
        val present = WorkspaceFileExecution.prepareDirectoryC(context, _target, path)
        val conflicts = Vector("occupied", "occupied/child").map(name =>
          WorkspaceFileExecution.prepareDirectoryC(context, _target, _path(name)))
        Then("only actual creation is Created, existing directories are AlreadyPresent and files are Conflict")
        created.toOption.get.outcome shouldBe FilePrepareDirectoryOutcome.Created
        present.toOption.get.outcome shouldBe FilePrepareDirectoryOutcome.AlreadyPresent
        conflicts.foreach(_.toOption.get.outcome shouldBe FilePrepareDirectoryOutcome.Conflict)
        Files.isDirectory(root.resolve(path.value)) shouldBe true
        Files.readAllBytes(root.resolve("occupied")).toVector shouldBe Vector[Byte](1, 2)
        created.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        created.toString should not include root.toString
        created.toString should not include "archive space"
      }
    }

    "retain actual created ancestors when a later literal segment cannot be created" in {
      Given("a valid admitted root and a path whose later filename exceeds the local filesystem segment limit")
      _with_root { root =>
        val path = _path("retained/" + "x" * 300)
        When("directory preparation creates its first segment before the later filesystem failure")
        val result = WorkspaceFileExecution.prepareDirectoryC(_context(root), _target, path)
        Then("the failure is reported without rolling back or retrying the already created directory")
        result.isFaillure shouldBe true
        _names(root) shouldBe Set("retained")
        _names(root.resolve("retained")) shouldBe empty
      }
    }
  }

  "Bounded binary File representations" should {
    "round trip generated arbitrary byte vectors through each explicit representation" in {
      Given("bounded generated binary payloads and one admitted root with enough room for gzip overhead")
      _with_root { root =>
        val context = _context(root)
        val payloads = Gen.choose(0, 64).flatMap(size => Gen.listOfN(size, Gen.choose(-128, 127).map(_.toByte))).map(_.toVector)
        var ordinal = 0
        When("each generated payload is installed once as Identity and once as Gzip")
        val property = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), Prop.forAll(payloads) { bytes =>
          ordinal += 1
          Vector(FileRepresentation.Identity, FileRepresentation.Gzip).forall { representation =>
            val path = _path(s"payload-$ordinal-$representation")
            val written = WorkspaceFileExecution.writeBytesC(context, _target, path, bytes, representation)
            val read = WorkspaceFileExecution.readBytesC(context, _target, path, representation)
            written.toOption.exists(_.outcome == FileWriteOutcome.Created) && read.toOption.exists(_.bytes.contains(bytes))
          }
        })
        Then("both explicit representations preserve every generated complete byte vector")
        property.passed shouldBe true
        _names(root).exists(_.startsWith(".cncf-file-")) shouldBe false
      }
    }

    "store arbitrary bytes as standard gzip at the exact selected name and preserve the original source" in {
      Given("an admitted archive directory and arbitrary binary source bytes including invalid UTF-8")
      _with_root { root =>
        val bytes = Vector[Byte](0, -1, -128, 127, 13, 10, 0, 42)
        val source = Files.write(root.resolve("original"), bytes.toArray)
        val context = _context(root)
        val path = _path("archive/exact.gz")
        WorkspaceFileExecution.prepareDirectoryC(context, _target, _path("archive"))
        When("CreateNew gzip writes and reads the exact name then competes with an occupied leaf")
        val written = WorkspaceFileExecution.writeBytesC(context, _target, path, bytes, FileRepresentation.Gzip)
        val stored = Files.readAllBytes(root.resolve(path.value)).toVector
        val read = WorkspaceFileExecution.readBytesC(context, _target, path, FileRepresentation.Gzip)
        val conflict = WorkspaceFileExecution.writeBytesC(context, _target, path, Vector[Byte](9), FileRepresentation.Identity)
        Then("the JVM gzip representation round trips, an occupied destination remains unchanged and the source stays intact")
        written.toOption.get.outcome shouldBe FileWriteOutcome.Created
        read.toOption.get.bytes shouldBe Some(bytes)
        stored.take(2) shouldBe Vector(0x1f.toByte, 0x8b.toByte)
        stored shouldBe _gzip(bytes)
        conflict.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists)
        Files.readAllBytes(root.resolve(path.value)).toVector shouldBe stored
        Files.readAllBytes(source).toVector shouldBe bytes
        _names(root.resolve("archive")) shouldBe Set("exact.gz")
        written.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        read.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
        Vector(written.toString, read.toString).foreach { display =>
          display should not include root.toString
          display should not include "exact.gz"
          display should not include bytes.toString
        }
      }
    }

    "distinguish missing and empty bytes and honor explicit representation independently of the filename" in {
      Given("an admitted existing parent and empty Identity and Gzip payloads")
      _with_root { root =>
        val context = _context(root)
        When("explicit representations are stored under names which do not imply their encoding")
        val identity = WorkspaceFileExecution.writeBytesC(context, _target, _path("identity.gz"), Vector.empty, FileRepresentation.Identity)
        val gzip = WorkspaceFileExecution.writeBytesC(context, _target, _path("compressed.bin"), Vector.empty, FileRepresentation.Gzip)
        val missing = WorkspaceFileExecution.readBytesC(context, _target, _path("missing"), FileRepresentation.Gzip)
        val absentparent = WorkspaceFileExecution.readBytesC(context, _target, _path("absent/leaf"), FileRepresentation.Identity)
        val identityread = WorkspaceFileExecution.readBytesC(context, _target, _path("identity.gz"), FileRepresentation.Identity)
        val gzipread = WorkspaceFileExecution.readBytesC(context, _target, _path("compressed.bin"), FileRepresentation.Gzip)
        val mismatch = WorkspaceFileExecution.readBytesC(context, _target, _path("identity.gz"), FileRepresentation.Gzip)
        Then("empty data remains Some(empty), only a missing final leaf is None and no filename inference occurs")
        Vector(identity, gzip).foreach(_.toOption.get.outcome shouldBe FileWriteOutcome.Created)
        identityread.toOption.get.bytes shouldBe Some(Vector.empty)
        gzipread.toOption.get.bytes shouldBe Some(Vector.empty)
        mismatch.isFaillure shouldBe true
        missing.toOption.get.bytes shouldBe None
        absentparent.isFaillure shouldBe true
        _names(root) shouldBe Set("identity.gz", "compressed.bin")
      }
    }

    "bound input and compressed output independently before an oversized file can be installed" in {
      Given("a 32-byte current cap and incompressible input which fits that cap but whose gzip representation exceeds it")
      _with_root { root =>
        val bytes = (0 until 32).map(_.toByte).toVector
        val context = _context(root, cap = 32)
        When("Identity equality, input overflow, compressed output overflow and invalid inputs are submitted")
        val equal = WorkspaceFileExecution.writeBytesC(context, _target, _path("equal.gz"), bytes, FileRepresentation.Identity)
        val rejected = Vector(
          WorkspaceFileExecution.writeBytesC(context, _target, _path("input"), bytes :+ 0.toByte, FileRepresentation.Identity),
          WorkspaceFileExecution.writeBytesC(context, _target, _path("stored"), bytes, FileRepresentation.Gzip),
          WorkspaceFileExecution.writeBytesC(context, _target, _path("null"), null, FileRepresentation.Identity),
          WorkspaceFileExecution.writeBytesC(context, _target, _path("invalid"), bytes, null),
          WorkspaceFileExecution.writeBytesC(_context(root, cap = 1), _target, _path("header"), Vector.empty, FileRepresentation.Gzip))
        val read = WorkspaceFileExecution.readBytesC(context, _target, _path("equal.gz"), FileRepresentation.Identity)
        Then("only exact bounded Identity bytes install and every failed write removes its staged file")
        _gzip(bytes).size should be > 32
        equal.toOption.get.outcome shouldBe FileWriteOutcome.Created
        rejected.foreach(_.isFaillure shouldBe true)
        read.toOption.get.bytes shouldBe Some(bytes)
        _names(root) shouldBe Set("equal.gz")
      }
    }

    "accept exact stored and expanded limits while refusing compressed acquisition and expansion overflow" in {
      Given("gzip fixtures at the exact stored cap and exact expanded cap plus distinct overflow fixtures")
      _with_root { root =>
        val binary = (0 until 32).map(_.toByte).toVector
        val storedcap = _gzip(binary).size
        val expanded = Vector.fill[Byte](64)(7)
        Files.write(root.resolve("expanded"), _gzip(expanded).toArray)
        Files.write(root.resolve("bomb"), _gzip(Vector.fill[Byte](512)(7)).toArray)
        Files.write(root.resolve("acquisition"), _gzip((0 until 256).map(_.toByte).toVector).toArray)
        val context = _context(root, cap = 64)
        When("bounded gzip writing, acquisition and expansion use the current policy independently")
        val written = WorkspaceFileExecution.writeBytesC(_context(root, cap = storedcap), _target, _path("stored"), binary, FileRepresentation.Gzip)
        val exactstored = WorkspaceFileExecution.readBytesC(_context(root, cap = storedcap), _target, _path("stored"), FileRepresentation.Gzip)
        val exactexpanded = WorkspaceFileExecution.readBytesC(context, _target, _path("expanded"), FileRepresentation.Gzip)
        val rejected = Vector("bomb", "acquisition").map(name => WorkspaceFileExecution.readBytesC(context, _target, _path(name), FileRepresentation.Gzip))
        Then("equal caps return complete data and either overflow returns failure without a partial payload")
        written.toOption.get.outcome shouldBe FileWriteOutcome.Created
        Files.size(root.resolve("stored")) shouldBe storedcap.toLong
        exactstored.toOption.get.bytes shouldBe Some(binary)
        exactexpanded.toOption.get.bytes shouldBe Some(expanded)
        rejected.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
        }
      }
    }

    "reject malformed gzip headers, truncated trailers and corrupt CRC without partial success" in {
      Given("three bounded malformed gzip representations with private binary payloads")
      _with_root { root =>
        val bytes = _gzip(Vector[Byte](1, 2, 3, -1))
        val malformed = Vector(Vector[Byte](1, 2, 3), bytes.dropRight(2), bytes.updated(bytes.size - 8, (bytes(bytes.size - 8) ^ 1).toByte))
        malformed.zipWithIndex.foreach { case (value, index) => Files.write(root.resolve(index.toString), value.toArray) }
        When("each bounded stored representation is acquired and strictly decompressed")
        val results = malformed.indices.map(index => WorkspaceFileExecution.readBytesC(_context(root), _target, _path(index.toString), FileRepresentation.Gzip))
        Then("every malformed stream fails privately instead of returning a decoded prefix")
        results.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
        }
      }
    }

    "return the exact combined payload of two complete concatenated gzip members" in {
      Given("two standard gzip members containing distinct arbitrary binary payloads")
      _with_root { root =>
        val first = Vector[Byte](0, -1, 42)
        val second = Vector[Byte](-128, 13, 10, 7)
        Files.write(root.resolve("members"), (_gzip(first) ++ _gzip(second)).toArray)
        When("the complete concatenated representation is read as Gzip")
        val result = WorkspaceFileExecution.readBytesC(_context(root), _target, _path("members"), FileRepresentation.Gzip)
        Then("both complete members contribute their bytes in stored order")
        result.toOption.get.bytes shouldBe Some(first ++ second)
      }
    }

    "reject a valid gzip member followed by a truncated header, trailing garbage or a corrupt second trailer" in {
      Given("one valid member and three representations whose following bytes are incomplete or malformed")
      _with_root { root =>
        val payload = Vector[Byte](1, 2, 3, -1)
        val first = _gzip(payload)
        val second = _gzip(Vector[Byte](42, 0, -128))
        val malformed = Vector(
          first ++ second.take(9),
          first ++ Vector[Byte](1, 2, 3),
          first ++ second.updated(second.size - 8, (second(second.size - 8) ^ 1).toByte))
        malformed.zipWithIndex.foreach { case (bytes, index) => Files.write(root.resolve(index.toString), bytes.toArray) }
        When("each complete stored representation is read as Gzip")
        val results = malformed.indices.map(index =>
          WorkspaceFileExecution.readBytesC(_context(root), _target, _path(index.toString), FileRepresentation.Gzip))
        Then("no malformed suffix returns the valid first member as a successful prefix")
        results.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
          result.toString should not include payload.toString
        }
      }
    }

    "apply one expanded limit across concatenated members while accepting combined equality" in {
      Given("two gzip members whose individual 80-byte payloads fit a 128-byte cap and whose stored concatenation also fits")
      _with_root { root =>
        val payload = Vector.fill[Byte](80)(7)
        val member = _gzip(payload)
        val stored = member ++ member
        Files.write(root.resolve("single"), member.toArray)
        Files.write(root.resolve("combined"), stored.toArray)
        When("one member and both concatenated members are read under the smaller cap and the combined exact cap")
        val single = WorkspaceFileExecution.readBytesC(_context(root), _target, _path("single"), FileRepresentation.Gzip)
        val overflow = WorkspaceFileExecution.readBytesC(_context(root), _target, _path("combined"), FileRepresentation.Gzip)
        val equal = WorkspaceFileExecution.readBytesC(_context(root, cap = 160), _target, _path("combined"), FileRepresentation.Gzip)
        Then("only total expanded overflow fails and combined equality returns all bytes")
        stored.size should be <= 128
        single.toOption.get.bytes shouldBe Some(payload)
        overflow.isFaillure shouldBe true
        overflow.toOption shouldBe None
        overflow.toString should not include root.toString
        equal.toOption.get.bytes shouldBe Some(payload ++ payload)
      }
    }

    "validate optional gzip header fields and their checksum before accepting the payload" in {
      Given("a gzip member with extra bytes, a filename, a comment and a valid header CRC plus malformed variants")
      _with_root { root =>
        val payload = Vector[Byte](0, -1, 42)
        val member = _gzip(payload)
        val header = member.take(10).updated(3, 30.toByte) ++ Vector[Byte](2, 0, -1, 1) ++
          "private-name".getBytes(StandardCharsets.US_ASCII).toVector ++ Vector[Byte](0) ++
          "private-comment".getBytes(StandardCharsets.US_ASCII).toVector ++ Vector[Byte](0)
        val crc = new CRC32()
        crc.update(header.toArray)
        val checksum = Vector((crc.getValue() & 0xff).toByte, ((crc.getValue() >> 8) & 0xff).toByte)
        val valid = header ++ checksum ++ member.drop(10)
        Files.write(root.resolve("valid"), valid.toArray)
        val malformed = Vector(
          valid.updated(header.size, (valid(header.size) ^ 1).toByte),
          member.updated(3, 32.toByte),
          member.updated(2, 0.toByte),
          header.take(12),
          member.take(10).updated(3, 8.toByte) ++ Vector[Byte](1, 2, 3),
          member.take(10).updated(3, 16.toByte) ++ Vector[Byte](1, 2, 3))
        malformed.zipWithIndex.foreach { case (bytes, index) => Files.write(root.resolve(index.toString), bytes.toArray) }
        When("the valid and malformed headers are read through the same bounded gzip adapter")
        val accepted = WorkspaceFileExecution.readBytesC(_context(root), _target, _path("valid"), FileRepresentation.Gzip)
        val rejected = malformed.indices.map(index =>
          WorkspaceFileExecution.readBytesC(_context(root), _target, _path(index.toString), FileRepresentation.Gzip))
        Then("the valid optional fields preserve the payload and malformed headers fail privately")
        accepted.toOption.get.bytes shouldBe Some(payload)
        rejected.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
          result.toString should not include "private-name"
          result.toString should not include "private-comment"
        }
      }
    }
  }

  "Directory and binary workspace authority" should {
    "permit ReadOnly binary reads and deny directory and byte writes or missing actual ownership" in {
      Given("a readable gzip leaf, a ReadOnly grant and a different actual Component owner")
      _with_root { root =>
        val bytes = Vector[Byte](0, -1, 42)
        Files.write(root.resolve("read.gz"), _gzip(bytes).toArray)
        val readonly = _context(root, permission = WorkspacePermission.ReadOnly)
        val wrongowner = _context(root, owner = ComponentId("org.goldenport.spec.OtherFileOwner"))
        When("the current context reads, prepares a directory and attempts a CreateNew byte write")
        val read = WorkspaceFileExecution.readBytesC(readonly, _target, _path("read.gz"), FileRepresentation.Gzip)
        val denied = Vector(readonly, wrongowner, ExecutionContext.create(), _context(root, global = false)).flatMap { context => Vector(
          WorkspaceFileExecution.prepareDirectoryC(context, _target, _path("new/nested")).isFaillure,
          WorkspaceFileExecution.writeBytesC(context, _target, _path("new.gz"), bytes, FileRepresentation.Gzip).isFaillure)
        }
        Then("only the granted read succeeds and denied operations create no directories or files")
        read.toOption.get.bytes shouldBe Some(bytes)
        denied shouldBe Vector.fill(8)(true)
        _names(root) shouldBe Set("read.gz")
      }
    }

    "deny links, copied traversal, nonregular leaves and retargeted roots for all new effects" in {
      Given("admitted roots containing linked paths, a directory leaf and a configured root alias")
      _with_root { root =>
        val first = Files.createDirectory(root.resolve("first"))
        val second = Files.createDirectory(root.resolve("second"))
        Files.createDirectory(first.resolve("directory"))
        Files.write(second.resolve("source"), Array[Byte](0, -1))
        Files.createSymbolicLink(first.resolve("link"), second.resolve("source"))
        Files.createSymbolicLink(first.resolve("ancestor"), second)
        val alias = Files.createSymbolicLink(root.resolve("alias"), first)
        val context = _context(alias)
        val paths = Vector(_path("link"), _path("ancestor/child"), _forged_path("../escape"))
        When("each new effect selects disallowed paths before the configured root is retargeted")
        val denied = paths.flatMap { path => Vector(
          WorkspaceFileExecution.prepareDirectoryC(context, _target, path).isFaillure,
          WorkspaceFileExecution.writeBytesC(context, _target, path, Vector.empty, FileRepresentation.Identity).isFaillure,
          WorkspaceFileExecution.readBytesC(context, _target, path, FileRepresentation.Identity).isFaillure)
        }
        val nonregular = Vector(
          WorkspaceFileExecution.writeBytesC(context, _target, _path("directory"), Vector.empty, FileRepresentation.Identity).isFaillure,
          WorkspaceFileExecution.readBytesC(context, _target, _path("directory"), FileRepresentation.Identity).isFaillure)
        Files.delete(alias)
        Files.createSymbolicLink(alias, second)
        val retargeted = Vector(
          WorkspaceFileExecution.prepareDirectoryC(context, _target, _path("new/nested")).isFaillure,
          WorkspaceFileExecution.writeBytesC(context, _target, _path("new.gz"), Vector.empty, FileRepresentation.Gzip).isFaillure,
          WorkspaceFileExecution.readBytesC(context, _target, _path("source"), FileRepresentation.Identity).isFaillure)
        Then("all effects fail without following or modifying the replacement or linked source")
        denied shouldBe Vector.fill(9)(true)
        nonregular shouldBe Vector(true, true)
        retargeted shouldBe Vector(true, true, true)
        _names(first) shouldBe Set("directory", "link", "ancestor")
        _names(second) shouldBe Set("source")
        Files.readAllBytes(second.resolve("source")).toVector shouldBe Vector[Byte](0, -1)
      }
    }
  }

  "Literal workspace filenames" should {
    "retain literal control characters and Unicode while refusing absolute and traversal forms" in {
      Given("literal POSIX names and explicit host, empty-segment and traversal inputs")
      val names = Vector(" space ", "line\nname", "tab\tname", "back\\slash", "C:literal", "日本/語.txt")
      val invalid = Vector(null, "", "/host", "C:/host", "C:\\host", "\\\\server\\file", "a//b", "a/", ".", "..", "a/../b", "a/./b", "a\u0000b")
      val generated = Gen.listOfN(8, Gen.oneOf("a", "日本", "\n", "\t", "\\", ":", " ")).map(_.mkString)
      When("the literal File path parser consumes each input without normalization")
      val accepted = names.map(WorkspaceFilePath.parseC)
      val rejected = invalid.map(WorkspaceFilePath.parseC)
      val property = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), Prop.forAll(generated) { name =>
        WorkspaceFilePath.parseC("prefix" + name).toOption.exists(_.value == "prefix" + name)
      })
      Then("exact names round trip while every unsafe shape fails privately")
      accepted.flatMap(_.toOption.map(_.value)) shouldBe names
      rejected.foreach(_.isFaillure shouldBe true)
      property.passed shouldBe true
      rejected.mkString should not include "server"
    }
  }

  "Complete bounded File reads" should {
    "distinguish absence from empty text and preserve literal filenames, Unicode and final newline without side effects" in {
      Given("an admitted canonical directory with empty and literal-named UTF-8 files")
      _with_root { root =>
        val text = "日本語—🚀\n"
        val names = Vector("space name", "line\nname", "tab\tname", "back\\slash", "colon:name")
        names.foreach(name => Files.writeString(root.resolve(name), text, StandardCharsets.UTF_8))
        Files.createFile(root.resolve("empty"))
        val context = _context(root)
        val before = _names(root)
        When("the current adapter reads absent, empty and literal-named files")
        val absent = WorkspaceFileExecution.readTextC(context, _target, _path("absent"))
        val empty = WorkspaceFileExecution.readTextC(context, _target, _path("empty"))
        val results = names.map(name => WorkspaceFileExecution.readTextC(context, _target, _path(name)))
        Then("absence and empty remain distinct and read creates no lock or staging file")
        absent.toOption.get.text shouldBe None
        empty.toOption.get.text shouldBe Some("")
        results.foreach { result =>
          result.toOption.get.text shouldBe Some(text)
          result.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
          result.toString should not include text
          result.toString should not include root.toString
        }
        names.foreach(name => Files.readString(root.resolve(name), StandardCharsets.UTF_8) shouldBe text)
        _names(root) shouldBe before
      }
    }

    "accept exact encoded byte caps and refuse overflow and malformed UTF-8 without partial text" in {
      Given("complete files at and above four bytes plus malformed private UTF-8 bytes")
      _with_root { root =>
        Files.writeString(root.resolve("equal"), "éé", StandardCharsets.UTF_8)
        val excess = Files.writeString(root.resolve("excess"), "éé\n", StandardCharsets.UTF_8)
        val malformed = Files.write(root.resolve("malformed"), Array(0xc3.toByte, 0x28.toByte))
        val context = _context(root, cap = 4)
        When("all three sources are read under the current four-byte runtime limit")
        val equal = WorkspaceFileExecution.readTextC(context, _target, _path("equal"))
        val refused = Vector("excess", "malformed").map(name => WorkspaceFileExecution.readTextC(context, _target, _path(name)))
        Then("equality is complete and failures expose neither partial text nor physical locations")
        equal.toOption.get.text shouldBe Some("éé")
        refused.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
          result.toString should not include "éé"
        }
        Files.readAllBytes(excess).toVector shouldBe "éé\n".getBytes(StandardCharsets.UTF_8).toVector
        Files.readAllBytes(malformed).toVector shouldBe Vector(0xc3.toByte, 0x28.toByte)
      }
    }
  }

  "Current workspace authority" should {
    "allow a ReadOnly read and deny writes, unknown targets, wrong owners and missing services before mutation" in {
      Given("a private file, a ReadOnly owner and contexts missing actual ownership, service or global limits")
      _with_root { root =>
        val file = Files.writeString(root.resolve("private"), "secret\n", StandardCharsets.UTF_8)
        val readonly = _context(root, permission = WorkspacePermission.ReadOnly)
        val wrong = _context(root, owner = ComponentId("org.goldenport.spec.OtherFileOwner"))
        val missingservice = ExecutionContext.create()
        val missingglobal = _context(root, global = false)
        val allowed = _context(root)
        val global = allowed.cncfCore.scope.parent.get
        val impostor = allowed.withScope(global.createChildScope(ScopeKind.Component, _owner.name))
        val before = _names(root)
        When("each current context attempts its admitted or denied File operation")
        val read = WorkspaceFileExecution.readTextC(readonly, _target, _path("private"))
        val denied = Vector(readonly, wrong, missingservice, missingglobal, impostor).map(context =>
          WorkspaceFileExecution.writeTextC(context, _target, _path("new"), "replacement", FileWriteMode.CreateNew))
        val unknown = WorkspaceFileExecution.readTextC(allowed, FileTarget(WorkspaceId.parseC("unknown").toOption.get), _path("private"))
        Then("only the granted read succeeds and every denied operation leaves the directory unchanged")
        read.toOption.get.text shouldBe Some("secret\n")
        denied.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include root.toString
          result.toString should not include "replacement"
        }
        unknown.isFaillure shouldBe true
        _names(root) shouldBe before
        Files.readString(file, StandardCharsets.UTF_8) shouldBe "secret\n"
      }
    }

    "refuse final and ancestor links, nonregular files, missing parents and forged traversal values" in {
      Given("an admitted directory with links, a directory leaf and a regular-file parent")
      _with_root { root =>
        val nested = Files.createDirectory(root.resolve("nested"))
        val source = Files.writeString(nested.resolve("private"), "private content", StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("link"), source)
        Files.createSymbolicLink(root.resolve("ancestor"), nested)
        Files.writeString(root.resolve("plain"), "parent file", StandardCharsets.UTF_8)
        val context = _context(root)
        val paths = Vector("link", "ancestor/private", "nested", "missing/leaf", "plain/leaf").map(_path) :+
          _forged_path("../escape")
        val before = _names(root)
        When("reads and writes select each disallowed path")
        val reads = paths.map(WorkspaceFileExecution.readTextC(context, _target, _))
        val writes = paths.map(WorkspaceFileExecution.writeTextC(context, _target, _, "new", FileWriteMode.CreateNew))
        Then("policy failures return no file payload or mutations, including reflected constructor bypasses")
        reads.foreach(_.isFaillure shouldBe true)
        writes.foreach(_.isFaillure shouldBe true)
        (reads ++ writes).foreach(_.toString should not include root.toString)
        Files.readString(source, StandardCharsets.UTF_8) shouldBe "private content"
        _names(root) shouldBe before
      }
    }

    "reject a missing or retargeted admitted root without following the replacement" in {
      Given("a binding canonicalized through a configured root alias and another ordinary directory")
      _with_root { root =>
        val first = Files.createDirectory(root.resolve("first"))
        val second = Files.createDirectory(root.resolve("second"))
        Files.writeString(first.resolve("file"), "original", StandardCharsets.UTF_8)
        Files.writeString(second.resolve("file"), "replacement", StandardCharsets.UTF_8)
        val alias = Files.createSymbolicLink(root.resolve("configured"), first)
        val context = _context(alias)
        val missingcontext = _context(first)
        When("the configured alias is retargeted and the other admitted directory is removed")
        Files.delete(alias)
        Files.createSymbolicLink(alias, second)
        val retargeted = WorkspaceFileExecution.readTextC(context, _target, _path("file"))
        Files.delete(first.resolve("file"))
        Files.delete(first)
        val missing = WorkspaceFileExecution.writeTextC(missingcontext, _target, _path("new"), "new", FileWriteMode.CreateNew)
        Then("both operations fail without selecting or modifying the replacement root")
        retargeted.isFaillure shouldBe true
        missing.isFaillure shouldBe true
        Files.readString(second.resolve("file"), StandardCharsets.UTF_8) shouldBe "replacement"
        _names(second) shouldBe Set("file")
      }
    }
  }

  "Atomic File write modes" should {
    "create only absent ordinary leaves and preserve occupied files even when their text is equal" in {
      Given("an admitted existing parent, an unselected file and a Unicode replacement with final newline")
      _with_root { root =>
        val context = _context(root)
        val text = "日本—🚀\n"
        val untouched = Files.writeString(root.resolve("unselected"), "keep", StandardCharsets.UTF_8)
        When("CreateNew installs a new file and subsequently competes with equal and unequal existing text")
        val created = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), text, FileWriteMode.CreateNew)
        val equal = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), text, FileWriteMode.CreateNew)
        val unequal = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), "other", FileWriteMode.CreateNew)
        Then("only the absent leaf is installed, later attempts report AlreadyExists and staging is removed")
        created.toOption.get.outcome shouldBe FileWriteOutcome.Created
        created.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        Vector(equal, unequal).foreach(_.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
        Files.readAllBytes(root.resolve("new")).toVector shouldBe text.getBytes(StandardCharsets.UTF_8).toVector
        Files.readString(untouched, StandardCharsets.UTF_8) shouldBe "keep"
        _names(root) shouldBe Set("new", "unselected")
      }
    }

    "update only exact expected text while preserving bytes, final newline and supported POSIX permissions" in {
      Given("an existing ordinary file with known expected text and its supported POSIX mode")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "before\n", StandardCharsets.UTF_8)
        val view = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])
        val permissions = Option(view).map(_.readAttributes().permissions())
        val context = _context(root)
        val text = "更新🚀\n"
        When("UpdateExisting receives missing, changed and exactly matching preconditions")
        val missing = WorkspaceFileExecution.writeTextC(context, _target, _path("missing"), text, FileWriteMode.UpdateExisting("before\n"))
        val changed = WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, FileWriteMode.UpdateExisting("wrong"))
        val unchanged = Files.readString(path, StandardCharsets.UTF_8)
        val updated = WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, FileWriteMode.UpdateExisting("before\n"))
        Then("only equality installs the replacement and every temporary file is reclaimed")
        missing.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.Missing)
        changed.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.Changed)
        unchanged shouldBe "before\n"
        updated.toOption.get.outcome shouldBe FileWriteOutcome.Updated
        Files.readAllBytes(path).toVector shouldBe text.getBytes(StandardCharsets.UTF_8).toVector
        permissions.foreach(value => Files.getPosixFilePermissions(path) shouldBe value)
        _names(root) shouldBe Set("edit")
      }
    }

    "refuse malformed, null or over-cap expected and replacement text before staging" in {
      Given("a current four-byte cap and an unchanged ordinary file")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "old\n", StandardCharsets.UTF_8)
        val context = _context(root, cap = 4)
        val cases = Vector(
          ("large", FileWriteMode.CreateNew), ("\uD800", FileWriteMode.CreateNew),
          (null, FileWriteMode.CreateNew), ("new", FileWriteMode.UpdateExisting("large")),
          ("new", FileWriteMode.UpdateExisting("\uDC00")), ("new", FileWriteMode.UpdateExisting(null)),
          ("new", null)
        )
        When("the adapter validates each invalid typed write under that runtime policy")
        val results = cases.map { case (text, mode) => WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, mode) }
        Then("no invalid write or expected value reaches staging and diagnostics omit private payloads")
        results.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include root.toString
          result.toString should not include "large"
        }
        Files.readString(path, StandardCharsets.UTF_8) shouldBe "old\n"
        _names(root) shouldBe Set("edit")
      }
    }

    "serialize cooperating updates on the same binding so one expected-text contender wins" in {
      Given("one binding, a common expected text and two bounded thread contenders")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "before", StandardCharsets.UTF_8)
        val context = _context(root)
        val executor = Executors.newFixedThreadPool(2)
        val ready = new CountDownLatch(2)
        val start = new CountDownLatch(1)
        try {
          When("both contenders request an update using that same current binding")
          val futures = Vector("first\n", "second\n").map { text => executor.submit(new Callable[(String, FileWriteOutcome)] {
            def call(): (String, FileWriteOutcome) = {
              ready.countDown()
              if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("bounded start did not arrive")
              text -> WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text,
                FileWriteMode.UpdateExisting("before")).toOption.get.outcome
            }
          }) }
          val prepared = ready.await(5, TimeUnit.SECONDS)
          start.countDown()
          val outcomes = futures.map(_.get(10, TimeUnit.SECONDS))
          Then("exactly one update succeeds and the other observes Changed, with only the winner's bytes installed")
          prepared shouldBe true
          outcomes.map(_._2).count(_ == FileWriteOutcome.Updated) shouldBe 1
          outcomes.map(_._2).count(_ == FileWriteOutcome.Conflict(FileWriteConflict.Changed)) shouldBe 1
          Files.readString(path, StandardCharsets.UTF_8) shouldBe outcomes.find(_._2 == FileWriteOutcome.Updated).get._1
          _names(root) shouldBe Set("edit")
        } finally {
          start.countDown()
          executor.shutdownNow()
          executor.awaitTermination(5, TimeUnit.SECONDS)
        }
      }
    }
  }

  private def _path(value: String): WorkspaceFilePath = WorkspaceFilePath.parseC(value).toOption.get

  private def _gzip(bytes: Vector[Byte]): Vector[Byte] = {
    val output = new ByteArrayOutputStream()
    Using.resource(new GZIPOutputStream(output))(_.write(bytes.toArray))
    output.toByteArray.toVector
  }

  private def _forged_path(value: String): WorkspaceFilePath = {
    val constructor = classOf[WorkspaceFilePath].getDeclaredConstructor(classOf[String])
    constructor.setAccessible(true)
    constructor.newInstance(value)
  }

  private def _context(
    root: Path,
    permission: WorkspacePermission = WorkspacePermission.Manage,
    owner: ComponentId = _owner,
    cap: Int = 128,
    global: Boolean = true
  ): ExecutionContext = {
    val execution = ExecutionContext.create()
    val admission = WorkspaceExecutionAdmission.createC(Vector(_id -> root), Vector(WorkspaceGrant(_owner, _id, permission))).toOption.get
    val runtime = if (global) GlobalRuntimeContext.create("file-spec", RuntimeConfig.default.copy(resourceReadLimits = ResourceReadLimits(cap)),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty), execution.observability, AliasResolver.empty,
      workspaceAdmission = Some(admission))
    else ScopeContext(ScopeKind.Runtime, "no-global", None, execution.observability, workspaceExecutionAdmissionOption = Some(admission))
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    execution.withScope(Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown))
  }

  private def _with_root(body: Path => Unit): Unit = {
    val base = Files.createDirectories(Paths.get("target", "workspace-file-execution-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-").toRealPath()
    try body(root) finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }

  private def _names(root: Path): Set[String] =
    Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toSet)
}
