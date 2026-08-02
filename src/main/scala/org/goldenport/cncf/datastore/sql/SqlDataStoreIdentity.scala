package org.goldenport.cncf.datastore.sql

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.goldenport.Consequence

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
/** Secret-safe, deterministic identity input for the SystemNode registry. */
private[cncf] final class SqlDataStoreIdentity private (
  private val _serialized: String,
  val provenance: SqlDataStoreIdentity.Provenance
) extends Ordered[SqlDataStoreIdentity] {
  import SqlDataStoreIdentity.*

  val canonicalKey: String = _sha256(_serialized)

  override def compare(that: SqlDataStoreIdentity): Int =
    canonicalKey.compareTo(that.canonicalKey)

  override def equals(other: Any): Boolean = other match {
    case that: SqlDataStoreIdentity => _serialized == that._serialized
    case _ => false
  }

  override def hashCode: Int = _serialized.hashCode

  override def toString: String = s"SqlDataStoreIdentity($canonicalKey)"
}

private[cncf] object SqlDataStoreIdentity {
  enum Ownership {
    case Managed
    case CallerOwned
  }

  sealed trait Credential {
    private[sql] def discriminator: String
  }

  object Credential {
    final class Reference private[sql] (name: String, version: String) extends Credential {
      private[sql] def discriminator: String =
        s"ref:${_field(name)}:${_field(version)}"
      override def toString: String = "Credential.Reference(<redacted>)"
    }

    def reference(name: String, version: String): Credential =
      new Reference(_required(name, "credential reference"), _required(version, "credential version"))

  }

  final class HmacKey private (private val _bytes: Array[Byte]) {
    private[cncf] def fingerprint(value: String): String =
      _hmac(_bytes, value)

    override def toString: String = "HmacKey(<redacted>)"
  }

  object HmacKey {
    def apply(bytes: Array[Byte]): HmacKey = {
      val copied = Option(bytes).map(_.clone()).getOrElse(Array.emptyByteArray)
      if (copied.isEmpty)
        throw new IllegalArgumentException("managed datastore HMAC key is empty")
      new HmacKey(copied)
    }
  }

  final class Provenance private (
    val logicalName: Option[String],
    val selectionSource: Option[String]
  ) {
    override def toString: String = "Provenance(<logical binding>)"
  }

  object Provenance {
    val empty: Provenance = new Provenance(None, None)

    def apply(logicalName: Option[String], selectionSource: Option[String]): Provenance =
      new Provenance(logicalName.map(_.trim).filter(_.nonEmpty), selectionSource.map(_.trim).filter(_.nonEmpty))
  }

  def sqliteC(
    path: String,
    credential: Credential,
    key: HmacKey,
    provider: String = "jdbc",
    principal: Option[String] = None,
    properties: Map[String, String] = Map.empty,
    ownership: Ownership = Ownership.Managed,
    provenance: Provenance = Provenance.empty
  ): Consequence[SqlDataStoreIdentity] =
    Consequence {
      val (target, sqliteproperties) = _sqlite_target(path)
      _identity(
        provider,
        "sqlite",
        target,
        principal,
        credential.discriminator,
        _properties(properties.toVector ++ sqliteproperties),
        ownership,
        provenance
      )
    }

  def sqliteRawC(
    path: String,
    rawCredential: String,
    key: HmacKey,
    provider: String = "jdbc",
    principal: Option[String] = None,
    properties: Map[String, String] = Map.empty,
    ownership: Ownership = Ownership.Managed,
    provenance: Provenance = Provenance.empty
  ): Consequence[SqlDataStoreIdentity] =
    Consequence {
      val (target, sqliteproperties) = _sqlite_target(path)
      _identity(provider, "sqlite", target, principal, s"hmac:${key.fingerprint(_nonblank(rawCredential, "raw credential"))}", _properties(properties.toVector ++ sqliteproperties), ownership, provenance)
    }

  def jdbcC(
    jdbcUrl: String,
    credential: Credential,
    key: HmacKey,
    provider: String = "jdbc",
    principal: Option[String] = None,
    properties: Map[String, String] = Map.empty,
    ownership: Ownership = Ownership.Managed,
    provenance: Provenance = Provenance.empty
  ): Consequence[SqlDataStoreIdentity] =
    Consequence {
      val (dialect, target, urlproperties) = _jdbc_target(jdbcUrl)
      _identity(
        provider,
        dialect,
        target,
        principal,
        credential.discriminator,
        _properties(properties.toVector ++ urlproperties),
        ownership,
        provenance
      )
    }

  def jdbcRawC(
    jdbcUrl: String,
    rawCredential: String,
    key: HmacKey,
    provider: String = "jdbc",
    principal: Option[String] = None,
    properties: Map[String, String] = Map.empty,
    ownership: Ownership = Ownership.Managed,
    provenance: Provenance = Provenance.empty
  ): Consequence[SqlDataStoreIdentity] =
    Consequence {
      val (dialect, target, urlproperties) = _jdbc_target(jdbcUrl)
      _identity(provider, dialect, target, principal, s"hmac:${key.fingerprint(_nonblank(rawCredential, "raw credential"))}", _properties(properties.toVector ++ urlproperties), ownership, provenance)
    }

  private def _identity(
    provider: String,
    dialect: String,
    target: String,
    principal: Option[String],
    credentialdiscriminator: String,
    properties: Vector[(String, String)],
    ownership: Ownership,
    provenance: Provenance
  ): SqlDataStoreIdentity = {
    val serialized = Vector(
      "v1",
      _field(_required(provider, "provider").toLowerCase(java.util.Locale.ROOT)),
      _field(dialect),
      _field(target),
      _field(principal.map(_.trim).filter(_.nonEmpty).getOrElse("")),
      _field(credentialdiscriminator),
      _field(properties.map { case (k, v) => s"${_field(k)}${_field(v)}" }.mkString),
      _field(ownership.toString)
    ).mkString
    new SqlDataStoreIdentity(serialized, provenance)
  }

  private def _sqlite_target(path: String): (String, Vector[(String, String)]) = {
    val text = _required(path, "SQLite path")
    val lower = text.toLowerCase(java.util.Locale.ROOT)
    if (text == ":memory:" || lower == "jdbc:sqlite::memory:")
      throw new IllegalArgumentException("private SQLite in-memory databases cannot have a managed identity")
    if (lower.startsWith("file:")) {
      val (name, query) = _split_query(text.drop("file:".length))
      val sqliteproperties = _properties(_query_properties(query))
      val parameters = sqliteproperties.toMap
      if (parameters.get("mode").exists(_.equalsIgnoreCase("memory"))) {
        if (name.trim.isEmpty || parameters.get("cache").forall(!_.equalsIgnoreCase("shared")))
          throw new IllegalArgumentException("managed shared SQLite memory requires a stable name and cache=shared")
        s"shared-memory:${_field(name)}" -> sqliteproperties
      } else {
        val normalized = Path.of(_required(name, "SQLite URI path")).toAbsolutePath.normalize.toString
        s"file:${_field(normalized)}" -> sqliteproperties
      }
    } else {
      if (text.contains('?'))
        throw new IllegalArgumentException("unsupported SQLite URI form for managed identity")
      val normalized = Path.of(text).toAbsolutePath.normalize.toString
      s"file:${_field(normalized)}" -> Vector.empty
    }
  }

  private def _jdbc_target(jdbcUrl: String): (String, String, Vector[(String, String)]) = {
    val text = _required(jdbcUrl, "JDBC URL")
    val lower = text.toLowerCase(java.util.Locale.ROOT)
    if (lower.startsWith("jdbc:sqlite:"))
      _sqlite_target(text.drop("jdbc:sqlite:".length)) match {
        case (target, sqliteproperties) => ("sqlite", target, sqliteproperties)
      }
    else {
      val pattern = "(?i)^jdbc:(mysql|mariadb|postgresql)://([^/]+)/(.*)$".r
      text match {
        case pattern(rawdialect, authority, databaseandquery) =>
          if (authority.contains('@'))
            throw new IllegalArgumentException("JDBC URL userinfo is not accepted for managed identity")
          val dialect = rawdialect.toLowerCase(java.util.Locale.ROOT)
          val defaultport = if (dialect == "postgresql") 5432 else 3306
          val (database, query) = _split_query(databaseandquery)
          val canonicaldatabase = _required(database, "JDBC database")
          val hosts = authority.split(",", -1).toVector.map(_host(_, defaultport)).mkString(",")
          val queryproperties = _query_properties(query)
          (dialect, s"hosts:${_field(hosts)}database:${_field(canonicaldatabase)}", queryproperties)
        case _ =>
          throw new IllegalArgumentException("unsupported or malformed JDBC URL for managed identity")
      }
    }
  }

  private def _host(value: String, defaultport: Int): String = {
    val text = _required(value, "JDBC host").trim
    val (host, port) =
      if (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end <= 1 || (end + 1 < text.length && text.charAt(end + 1) != ':'))
          throw new IllegalArgumentException("malformed JDBC IPv6 host")
        val hostvalue = text.substring(1, end)
        val portvalue = if (end + 1 == text.length) defaultport else _port(text.substring(end + 2))
        hostvalue -> portvalue
      } else {
        val first = text.indexOf(':')
        if (first >= 0 && first != text.lastIndexOf(':'))
          throw new IllegalArgumentException("JDBC IPv6 hosts must use brackets")
        if (first < 0) text -> defaultport
        else text.substring(0, first) -> _port(text.substring(first + 1))
      }
    s"${_required(host, "JDBC host").toLowerCase(java.util.Locale.ROOT)}:$port"
  }

  private def _port(value: String): Int = {
    val port = try value.toInt catch { case _: NumberFormatException => -1 }
    if (port < 1 || port > 65535)
      throw new IllegalArgumentException("JDBC port is outside 1..65535")
    port
  }

  private def _split_query(value: String): (String, String) = {
    val index = value.indexOf('?')
    if (index < 0) value -> "" else value.substring(0, index) -> value.substring(index + 1)
  }

  private def _query_properties(query: String): Vector[(String, String)] =
    if (query.isEmpty) Vector.empty
    else
      query.split("[&;]", -1).toVector.map { entry =>
        val index = entry.indexOf('=')
        if (index <= 0)
          throw new IllegalArgumentException("JDBC property is malformed")
        entry.substring(0, index) -> entry.substring(index + 1)
      }

  private def _properties(properties: Map[String, String]): Vector[(String, String)] =
    _properties(properties.toVector)

  private def _properties(properties: Vector[(String, String)]): Vector[(String, String)] = {
    val normalized = properties.map { case (key, value) =>
      val canonicalkey = _required(key, "JDBC property name").toLowerCase(java.util.Locale.ROOT)
      if (_is_credential_property(canonicalkey))
        throw new IllegalArgumentException("JDBC credential material must be supplied separately")
      canonicalkey -> _required(value, "JDBC property value")
    }
    if (normalized.map(_._1).distinct.size != normalized.size)
      throw new IllegalArgumentException("JDBC property names must not repeat")
    normalized.sortBy(_._1)
  }

  private def _is_credential_property(name: String): Boolean =
    {
      val compact = name.filter(_.isLetterOrDigit)
      Set("password", "passwd", "pwd", "token", "secret", "credential").contains(compact) ||
        compact.contains("password") ||
        compact.contains("token") ||
        compact.contains("secret") ||
        compact.contains("credential") ||
        compact.contains("accesskey") ||
        compact.contains("privatekey") ||
        compact.contains("sslkey") ||
        compact.contains("keystore") ||
        compact.contains("truststore")
    }

  private def _required(value: String, label: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(throw new IllegalArgumentException(s"$label is empty"))

  private def _nonblank(value: String, label: String): String =
    Option(value).filter(_.trim.nonEmpty).getOrElse(throw new IllegalArgumentException(s"$label is empty"))

  private def _field(value: String): String = s"${value.length}:$value"

  private def _hmac(keybytes: Array[Byte], value: String): String = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(keybytes, "HmacSHA256"))
    _hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)))
  }

  private def _sha256(value: String): String =
    _hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))

  private def _hex(bytes: Array[Byte]): String =
    bytes.iterator.map(byte => f"${byte & 0xff}%02x").mkString
}
