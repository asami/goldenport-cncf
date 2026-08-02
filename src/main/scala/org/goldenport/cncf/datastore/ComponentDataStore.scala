package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ConfigurationAccess, ResolvedParameter, ResolvedParameters}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqlDataStoreIdentity}
import org.goldenport.cncf.subsystem.{SystemNodeDataStoreBinding, SystemNodeResourceLease}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Jul.  6, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentDataStore {
  sealed trait Policy
  object Policy {
    case object LocalDefault extends Policy
    case object ExternalDefault extends Policy
    case object ExternalRequired extends Policy
    case object LocalOnly extends Policy

    val default: Policy = ExternalDefault

    def parse(value: String): Option[Policy] =
      Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-')).collect {
        case "local-default" | "local" | "local-persistent" => LocalDefault
        case "external-default" | "external" => ExternalDefault
        case "external-required" | "required" => ExternalRequired
        case "local-only" => LocalOnly
      }
  }

  final case class Environment(
    params: ResolvedParameters,
    configuration: Option[ResolvedConfiguration] = None
  )

  final case class Request(
    componentName: String,
    name: String = "application"
  ) {
    val normalizedComponentName: String = normalize_component(componentName)
    val normalizedName: String = normalize_name(name)
  }

  def resolve(
    params: ResolvedParameters,
    request: Request
  ): DataStore =
    resolve(Environment(params), request)

  def resolve(
    environment: Environment,
    request: Request
  ): DataStore = {
    val policy = _policy(environment, request)
    policy match {
      case Policy.LocalOnly =>
        _local(environment, request)
      case Policy.LocalDefault =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
          .getOrElse(_local(environment, request))
      case Policy.ExternalDefault =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
          .getOrElse(DataStore.inMemorySearchable())
      case Policy.ExternalRequired =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
          .getOrElse(throw new IllegalArgumentException(
            s"Persistent datastore is required for component ${request.normalizedComponentName}/${request.normalizedName}"
          ))
    }
  }

  def resolveForDataStoreSpace(
    environment: Environment,
    request: Request
  ): Option[DataStore] = {
    val policy = _policy(environment, request)
    policy match {
      case Policy.LocalOnly =>
        Some(_local(environment, request))
      case Policy.LocalDefault =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
          .orElse(Some(_local(environment, request)))
      case Policy.ExternalDefault =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
      case Policy.ExternalRequired =>
        _dedicated(environment, request)
          .orElse(_basic(environment))
          .orElse(throw new IllegalArgumentException(
            s"Persistent datastore is required for component ${request.normalizedComponentName}/${request.normalizedName}"
          ))
    }
  }

  /**
   * The managed counterpart to `resolveForDataStoreSpace`.  It preserves the
   * existing selection policy but routes only SQL resources through the
   * owning SystemNode binding.  The public resolve methods above intentionally
   * remain direct caller-owned construction surfaces.
   */
  private[cncf] def resolveManagedForDataStoreSpaceC(
    environment: Environment,
    request: Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey
  ): Consequence[Option[DataStore]] = {
    val prefixes = _component_datastore_prefixes(request)
    val managedlocal = () => _managed_local_c(environment, prefixes, request, binding, lease, key)
    val manageddedicated = () => _managed_sql_c(environment, prefixes, request, binding, lease, key)
    val managedbasic = () => _managed_sql_c(environment, Vector("textus.datastore", "cncf.datastore"), request, binding, lease, key)
    _policy(environment, request) match {
      case Policy.LocalOnly => managedlocal()
      case Policy.LocalDefault => _or_else_c(manageddedicated())(_or_else_c(managedbasic())(managedlocal()))
      case Policy.ExternalDefault => _or_else_c(manageddedicated())(managedbasic())
      case Policy.ExternalRequired =>
        _or_else_c(manageddedicated())(managedbasic()).flatMap {
          case some @ Some(_) => Consequence.success(some)
          case None => Consequence { throw new IllegalArgumentException(
            s"Persistent datastore is required for component ${request.normalizedComponentName}/${request.normalizedName}"
          ) }
        }
    }
  }

  private def _managed_local_c(
    environment: Environment,
    prefixes: Vector[String],
    request: Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey
  ): Consequence[Option[DataStore]] =
    Consequence(_local_path(environment, request).toString).flatMap { path =>
      Consequence(_ensure_parent(path)).flatMap { _ =>
        _managed_sqlite_c(path, environment, prefixes ++ Vector("textus.datastore", "cncf.datastore"), request, binding, lease, key, "local")
          .map(Some(_))
      }
    }

  private def _managed_sql_c(
    environment: Environment,
    prefixes: Vector[String],
    request: Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey
  ): Consequence[Option[DataStore]] =
    Consequence {
      _first(environment, prefixes.map(_ + ".kind"))
        .orElse(_first(environment, prefixes.map(_ + ".type")))
        .map(_.trim.toLowerCase(java.util.Locale.ROOT))
    }.flatMap { kind =>
      kind match {
      case Some("in-memory" | "inmemory" | "memory") => Consequence.success(None)
      case Some("local" | "sqlite") =>
        _first(environment, prefixes.flatMap(p => Vector(p + ".path", p + ".sqlite.path"))) match {
          case Some(path) => Consequence(_ensure_parent(path)).flatMap(_ => _managed_sqlite_c(path, environment, prefixes, request, binding, lease, key, prefixes.head).map(Some(_)))
          case None => Consequence { throw new IllegalArgumentException(s"${prefixes.head}.path is required when datastore kind is local or sqlite") }
        }
      case Some("mysql") => _managed_jdbc_c(environment, prefixes, request, binding, lease, key, SqlDataStore.Mysql, Some("com.mysql.cj.jdbc.Driver"))
      case Some("jdbc") => _managed_jdbc_c(environment, prefixes, request, binding, lease, key, _jdbc_dialect(environment, prefixes), None)
      case Some(_) => Consequence.success(None)
      case None =>
        _first(environment, prefixes.map(_ + ".sqlite.path")) match {
          case Some(path) => Consequence(_ensure_parent(path)).flatMap(_ => _managed_sqlite_c(path, environment, prefixes, request, binding, lease, key, prefixes.head).map(Some(_)))
          case None => _managed_jdbc_c(environment, prefixes, request, binding, lease, key, _jdbc_dialect(environment, prefixes), None)
        }
      }
    }

  private def _managed_sqlite_c(
    path: String,
    environment: Environment,
    prefixes: Vector[String],
    request: Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey,
    source: String
  ): Consequence[DataStore] = {
    val config = _sql_config(environment, prefixes)
    SqlDataStoreIdentity.sqliteC(
      path,
      SqlDataStoreIdentity.Credential.reference("sqlite", "no-credential"),
      key,
      properties = _identity_properties(config),
      provenance = SqlDataStoreIdentity.Provenance(Some(_logical_name(request)), Some(source))
    ).flatMap { identity =>
      binding.resolveC(lease, _logical_name(request), identity) {
        SqlDataStore.managedSqliteResourceC(path)
      }.map(resource => SqlDataStore.managedView(
        resource,
        s"jdbc:sqlite:$path",
        SqlDataStore.Sqlite,
        () => binding.inheritLeaseC(lease).map(_ => ()),
        config = config
      ))
    }
  }

  private def _managed_jdbc_c(
    environment: Environment,
    prefixes: Vector[String],
    request: Request,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey,
    dialect: SqlDataStore.DialectSelection,
    defaultdriver: Option[String]
  ): Consequence[Option[DataStore]] =
    _first(environment, prefixes.map(_ + ".jdbc.url")) match {
      case None => Consequence.success(None)
      case Some(url) =>
        val username = _first(environment, prefixes.map(_ + ".jdbc.user"))
        val password = _first(environment, prefixes.map(_ + ".jdbc.password"))
        val driver = _first(environment, prefixes.map(_ + ".jdbc.driver")).orElse(defaultdriver)
        val config = _sql_config(environment, prefixes)
        val identityc = password match {
          case Some(raw) => SqlDataStoreIdentity.jdbcRawC(url, raw, key, principal = username, properties = _identity_properties(config) ++ driver.map("driver" -> _).toMap, provenance = SqlDataStoreIdentity.Provenance(Some(_logical_name(request)), Some(prefixes.head)))
          case None => SqlDataStoreIdentity.jdbcC(url, SqlDataStoreIdentity.Credential.reference("jdbc", "no-password"), key, principal = username, properties = _identity_properties(config) ++ driver.map("driver" -> _).toMap, provenance = SqlDataStoreIdentity.Provenance(Some(_logical_name(request)), Some(prefixes.head)))
        }
        identityc.flatMap { identity =>
          binding.resolveC(lease, _logical_name(request), identity) {
            SqlDataStore.managedJdbcResourceC(url, dialect, username, password, driver)
          }.map(resource => Some(SqlDataStore.managedView(
            resource,
            url,
            dialect,
            () => binding.inheritLeaseC(lease).map(_ => ()),
            config = config
          )))
        }
    }

  private def _or_else_c[A](
    first: Consequence[Option[A]]
  )(
    second: => Consequence[Option[A]]
  ): Consequence[Option[A]] =
    first.flatMap(_.map(value => Consequence.success(Some(value))).getOrElse(second))

  private def _logical_name(request: Request): String =
    s"${request.normalizedComponentName}/${request.normalizedName}"

  private def _identity_properties(config: SqlDataStore.Config): Map[String, String] =
    Map("normalize-column-names" -> config.normalizeColumnNames.toString)

  private def _dedicated(
    environment: Environment,
    request: Request
  ): Option[DataStore] = {
    _sql(environment, _component_datastore_prefixes(request))
  }

  private def _basic(
    environment: Environment
  ): Option[DataStore] =
    _sql(environment, Vector("textus.datastore", "cncf.datastore"))

  private def _local(
    environment: Environment,
    request: Request
  ): DataStore = {
    val path = _local_path(environment, request)
    Files.createDirectories(path.getParent)
    SqlDataStore.sqlite(path.toString, config = _sql_config(environment, _component_datastore_prefixes(request) ++ Vector(
      "textus.datastore",
      "cncf.datastore"
    )))
  }

  private def _local_path(
    environment: Environment,
    request: Request
  ): Path =
    _first(environment, Vector(
      s"textus.local-data.${request.normalizedComponentName}.${request.normalizedName}.path",
      s"cncf.local-data.${request.normalizedComponentName}.${request.normalizedName}.path"
    )).map(Paths.get(_)).getOrElse {
      val dir =
        _first(environment, Vector(
          s"textus.local-data.${request.normalizedComponentName}.dir",
          s"cncf.local-data.${request.normalizedComponentName}.dir"
        )).map(Paths.get(_)).getOrElse {
          val root =
            _first(environment, Vector("textus.local-data.root", "cncf.local-data.root"))
              .map(Paths.get(_))
              .getOrElse(Paths.get(System.getProperty("user.home"), ".cncf"))
          root.resolve(request.normalizedComponentName)
        }
      dir.resolve(s"${request.normalizedName}.db")
    }.toAbsolutePath.normalize

  private def _sql(
    environment: Environment,
    prefixes: Vector[String]
  ): Option[DataStore] = {
    val kind = _first(environment, prefixes.map(_ + ".kind"))
      .orElse(_first(environment, prefixes.map(_ + ".type")))
      .map(_.trim.toLowerCase(java.util.Locale.ROOT))
    kind match {
      case Some("in-memory" | "inmemory" | "memory") =>
        None
      case Some("local" | "sqlite") =>
        Some(_local_sql(environment, prefixes).getOrElse(throw new IllegalArgumentException(
          s"${prefixes.headOption.getOrElse("textus.datastore")}.path is required when datastore kind is local or sqlite"
        )))
      case Some("mysql") =>
        _jdbc(environment, prefixes, SqlDataStore.Mysql, Some("com.mysql.cj.jdbc.Driver"))
      case Some("jdbc") =>
        _jdbc(environment, prefixes, _jdbc_dialect(environment, prefixes), None)
      case Some(_) =>
        None
      case None =>
        _sqlite(environment, prefixes).orElse(_jdbc_auto(environment, prefixes))
    }
  }

  private def _sqlite(
    environment: Environment,
    prefixes: Vector[String]
  ): Option[DataStore] =
    _first(environment, prefixes.map(_ + ".sqlite.path")).map { path =>
      _ensure_parent(path)
      SqlDataStore.sqlite(path, config = _sql_config(environment, prefixes))
    }

  private def _local_sql(
    environment: Environment,
    prefixes: Vector[String]
  ): Option[DataStore] =
    _first(environment, prefixes.flatMap(p => Vector(p + ".path", p + ".sqlite.path"))).map { path =>
      _ensure_parent(path)
      SqlDataStore.sqlite(path, config = _sql_config(environment, prefixes))
    }

  private def _ensure_parent(path: String): Unit =
    Option(Paths.get(path).toAbsolutePath.normalize.getParent).foreach(Files.createDirectories(_))

  private def _jdbc_auto(
    environment: Environment,
    prefixes: Vector[String]
  ): Option[DataStore] =
    _first(environment, prefixes.map(_ + ".jdbc.url")).map { url =>
      val dialect = _jdbc_dialect(environment, prefixes) match {
        case SqlDataStore.Auto => SqlDataStore.dialectFromJdbcUrl(url)
        case other => other
      }
      SqlDataStore.jdbc(
        jdbcUrl = url,
        dialect = dialect,
        username = _first(environment, prefixes.map(_ + ".jdbc.user")),
        password = _first(environment, prefixes.map(_ + ".jdbc.password")),
        driverClassName = _first(environment, prefixes.map(_ + ".jdbc.driver")),
        config = _sql_config(environment, prefixes)
      )
    }

  private def _jdbc(
    environment: Environment,
    prefixes: Vector[String],
    dialect: SqlDataStore.DialectSelection,
    defaultDriver: Option[String]
  ): Option[DataStore] =
    _first(environment, prefixes.map(_ + ".jdbc.url")).map { url =>
      SqlDataStore.jdbc(
        jdbcUrl = url,
        dialect = dialect,
        username = _first(environment, prefixes.map(_ + ".jdbc.user")),
        password = _first(environment, prefixes.map(_ + ".jdbc.password")),
        driverClassName = _first(environment, prefixes.map(_ + ".jdbc.driver")).orElse(defaultDriver),
        config = _sql_config(environment, prefixes)
      )
    }

  private def _jdbc_dialect(
    environment: Environment,
    prefixes: Vector[String]
  ): SqlDataStore.DialectSelection =
    _first(environment, prefixes.map(_ + ".sql.dialect")).map(_.trim.toLowerCase(java.util.Locale.ROOT)) match {
      case Some("mysql" | "mariadb") => SqlDataStore.Mysql
      case _ => SqlDataStore.Auto
    }

  private def _sql_config(
    environment: Environment,
    prefixes: Vector[String]
  ): SqlDataStore.Config =
    SqlDataStore.Config(
      normalizeColumnNames = _truthy(_first(environment, prefixes.flatMap(p => Vector(
        p + ".sql.normalize-column-names",
        p + ".sqlite.normalize-column-names"
      ))))
    )

  private def _policy(
    environment: Environment,
    request: Request
  ): Policy =
    _first(environment, _policy_keys(request))
      .flatMap(Policy.parse)
      .getOrElse(Policy.default)

  private def _policy_keys(request: Request): Vector[String] =
    Vector(
      s"textus.component.${request.normalizedComponentName}.datastores.${request.normalizedName}.policy",
      s"cncf.component.${request.normalizedComponentName}.datastores.${request.normalizedName}.policy",
      s"textus.component.datastores.${request.normalizedName}.policy",
      s"cncf.component.datastores.${request.normalizedName}.policy"
    )

  private def _component_datastore_prefixes(request: Request): Vector[String] = {
    val named = Vector(
      s"textus.component.${request.normalizedComponentName}.datastores.${request.normalizedName}",
      s"cncf.component.${request.normalizedComponentName}.datastores.${request.normalizedName}"
    )
    val legacy =
      if (request.normalizedName == "application")
        Vector(
          s"textus.component.${request.normalizedComponentName}.datastore",
          s"cncf.component.${request.normalizedComponentName}.datastore"
        )
      else
        Vector.empty
    named ++ legacy
  }

  private def _first(
    environment: Environment,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator.flatMap(key => _runtime_value(environment.params, key)).toSeq.headOption
      .orElse(
        keys.iterator.flatMap(key => environment.configuration.flatMap(_configuration_value(_, key))).toSeq.headOption
      )

  private def _runtime_value(
    params: ResolvedParameters,
    key: String
  ): Option[String] =
    params.get(key).map(p => ResolvedParameter.format_value(p.value).trim).filter(_.nonEmpty)

  private def _configuration_value(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] =
    ConfigurationAccess.getString(conf, key)
      .orElse(_configuration_object_value(conf.configuration.values, key.split('.').toList.filter(_.nonEmpty)).map(_.trim).filter(_.nonEmpty))

  private def _configuration_object_value(
    values: Map[String, ConfigurationValue],
    segments: List[String]
  ): Option[String] =
    segments match {
      case Nil => None
      case head :: Nil =>
        values.get(head).flatMap(_configuration_value_string)
      case head :: tail =>
        values.get(head) match {
          case Some(ConfigurationValue.ObjectValue(vs)) =>
            _configuration_object_value(vs, tail)
          case _ =>
            None
        }
    }

  private def _configuration_value_string(value: ConfigurationValue): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }

  private def _truthy(value: Option[String]): Boolean =
    value.exists { v =>
      v.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "true" | "1" | "yes" | "on" => true
        case _ => false
      }
    }

  def normalize_component(value: String): String = {
    val source = Option(value)
      .getOrElse("")
      .trim
      .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
    val normalized = source.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '-' || c == '_' => c
      case _ => '-'
    }.mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-")
    if (normalized.nonEmpty) normalized else "component"
  }

  def normalize_name(value: String): String = {
    val normalized = Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '-' || c == '_' => c
      case _ => '-'
    }.mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-")
    if (normalized.nonEmpty) normalized else "application"
  }
}
