package org.goldenport.cncf.http

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.cncf.config.OperationMode
import org.goldenport.record.Record

/*
 * @since   Apr. 14, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebDescriptor(
  defaultView: String = WebTableColumnResolver.defaultViewName,
  expose: Map[String, WebDescriptor.Exposure] = Map.empty,
  auth: WebDescriptor.Auth = WebDescriptor.Auth(),
  authorization: Map[String, WebDescriptor.Authorization] = Map.empty,
  form: Map[String, WebDescriptor.Form] = Map.empty,
  apps: Vector[WebDescriptor.App] = Vector.empty,
  routes: Vector[WebDescriptor.Route] = Vector.empty,
  theme: WebDescriptor.Theme = WebDescriptor.Theme(),
  assets: WebDescriptor.Assets = WebDescriptor.Assets(),
  admin: Map[String, WebDescriptor.AdminSurface] = Map.empty
) {
  def hasControls: Boolean =
    expose.nonEmpty ||
      authorization.nonEmpty ||
      form.nonEmpty ||
      apps.nonEmpty ||
      routes.nonEmpty ||
      theme != WebDescriptor.Theme() ||
      assets != WebDescriptor.Assets() ||
      admin.nonEmpty ||
      defaultView != WebTableColumnResolver.defaultViewName ||
      auth != WebDescriptor.Auth()

  def exposureOf(selector: String): WebDescriptor.Exposure =
    expose.getOrElse(selector, WebDescriptor.Exposure.Internal)

  def isFormEnabled(selector: String): Boolean =
    form.get(selector).flatMap(_.enabled) match {
      case Some(value) => value
      case None =>
        if (!hasControls)
          true
        else
          exposureOf(selector) != WebDescriptor.Exposure.Internal
    }

  def isAppEnabled(name: String, path: Vector[String] = Vector.empty): Boolean =
    if (apps.isEmpty)
      true
    else
      apps.exists(_.matches(name, path))

  def appAssets(name: String): WebDescriptor.Assets =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    ).map(_.assets).getOrElse(WebDescriptor.Assets())

  def themeFor(appName: Option[String] = None): WebDescriptor.Theme =
    appName
      .flatMap(name => apps.find(app =>
        app.matches(name, Vector.empty) ||
          app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
      ).map(_.theme))
      .map(theme.merge)
      .getOrElse(theme)

  def formAssets(
    componentName: String,
    serviceName: String,
    operationName: String
  ): WebDescriptor.Assets =
    form.get(WebDescriptor.formSelector(componentName, serviceName, operationName))
      .map(_.assets)
      .getOrElse(WebDescriptor.Assets())

  def formIndexAssets(
    componentName: String
  ): WebDescriptor.Assets =
    assets.merge(appAssets(componentName))

  def resultAssets(
    componentName: String,
    serviceName: String,
    operationName: String
  ): WebDescriptor.Assets =
    assets
      .merge(appAssets(componentName))
      .merge(formAssets(componentName, serviceName, operationName))

  def webRouteFor(path: Vector[String]): Option[WebDescriptor.ResolvedRoute] =
    routes.view
      .flatMap(route => route.resolve(path).map(route.normalizedPath.length -> _))
      .toVector
      .sortBy { case (length, _) => -length }
      .headOption
      .map(_._2)

  def withImplicitSarRoutes(componentNames: Vector[String]): WebDescriptor =
    if (routes.nonEmpty || apps.size != 1)
      this
    else {
      val app = apps.head
      _implicit_sar_component_name(app, componentNames) match {
        case Some(componentName) =>
          val target = WebDescriptor.RouteTarget(componentName, app.name)
          copy(routes = Vector(
            WebDescriptor.Route(s"/web/${app.normalizedName}", target, WebDescriptor.RouteKind.Alias),
            WebDescriptor.Route("/web", target, WebDescriptor.RouteKind.Default)
          ))
        case None =>
          this
      }
    }

  private def _implicit_sar_component_name(
    app: WebDescriptor.App,
    componentNames: Vector[String]
  ): Option[String] = {
    def normalize(value: String): String =
      org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(value)
    val matched = componentNames.filter(name => normalize(name) == app.normalizedName)
    matched match {
      case Vector(name) => Some(normalize(name))
      case _ if componentNames.size == 1 => componentNames.headOption.map(normalize)
      case _ => None
    }
  }

  def adminTotalCountPolicy(
    componentName: String,
    surface: String,
    collectionName: String
  ): WebDescriptor.TotalCountPolicy =
    adminSurface(componentName, surface, collectionName).map(_.totalCount).getOrElse(WebDescriptor.TotalCountPolicy.Disabled)

  def adminFields(
    componentName: String,
    surface: String,
    collectionName: String
  ): Vector[WebDescriptor.AdminField] =
    adminSurface(componentName, surface, collectionName).toVector.flatMap(_.fields)

  def adminOperationFields(
    componentName: String,
    surface: String,
    collectionName: String,
    operationName: String
  ): Vector[WebDescriptor.AdminField] =
    adminOperationSurface(componentName, surface, collectionName, operationName).toVector.flatMap(_.fields)

  def adminOperationSurface(
    componentName: String,
    surface: String,
    collectionName: String,
    operationName: String
  ): Option[WebDescriptor.AdminSurface] = {
    def normalize(value: String): String =
      value.trim.toLowerCase.replace("_", "-")
    val component = normalize(componentName)
    val s = normalize(surface)
    val collection = normalize(collectionName)
    val operation = normalize(operationName)
    Vector(
      s"${component}.${s}.${collection}.${operation}",
      s"${s}.${collection}.${operation}",
      s"${component}.${s}.${collection}.*",
      s"${s}.${collection}.*"
    ).flatMap(admin.get).headOption.orElse(adminSurface(componentName, surface, collectionName))
  }

  def adminSurface(
    componentName: String,
    surface: String,
    collectionName: String
  ): Option[WebDescriptor.AdminSurface] = {
    def normalize(value: String): String =
      value.trim.toLowerCase.replace("_", "-")
    val component = normalize(componentName)
    val s = normalize(surface)
    val collection = normalize(collectionName)
    Vector(
      s"${component}.${s}.${collection}",
      s"${s}.${collection}",
      s"${component}.${s}.*",
      s"${s}.*",
      s"${component}.${s}",
      s
    ).flatMap(admin.get).headOption
  }
}

object WebDescriptor {
  enum Exposure {
    case Internal
    case Protected
    case Public

    def name: String =
      this match {
        case Internal => "internal"
        case Protected => "protected"
        case Public => "public"
      }
  }

  object Exposure {
    def parse(value: String): Option[Exposure] =
      value.trim.toLowerCase match {
        case "internal" => Some(Internal)
        case "protected" => Some(Protected)
        case "public" => Some(Public)
        case _ => None
      }
  }

  final case class Auth(
    mode: String = "none"
  )

  final case class Authorization(
    roles: Vector[String] = Vector.empty,
    scopes: Vector[String] = Vector.empty,
    capabilities: Vector[String] = Vector.empty,
    operationModes: Vector[OperationMode] = Vector.empty,
    anonymousOperationModes: Vector[OperationMode] = Vector.empty,
    allowAnonymous: Boolean = false
  )

  final case class Form(
    enabled: Option[Boolean] = None,
    successRedirect: Option[String] = None,
    failureRedirect: Option[String] = None,
    stayOnError: Boolean = false,
    resultTemplate: Option[String] = None,
    assets: Assets = Assets(),
    controls: Map[String, FormControl] = Map.empty
  )

  final case class FormControl(
    controlType: Option[String] = None,
    hidden: Boolean = false,
    system: Boolean = false,
    values: Vector[String] = Vector.empty,
    multiple: Boolean = false,
    required: Option[Boolean] = None,
    readonly: Boolean = false,
    placeholder: Option[String] = None,
    help: Option[String] = None,
    validation: org.goldenport.schema.WebValidationHints = org.goldenport.schema.WebValidationHints.empty
  )

  enum TotalCountPolicy {
    case Disabled
    case Optional
    case Required

    def name: String =
      this match {
        case Disabled => "disabled"
        case Optional => "optional"
        case Required => "required"
      }

    def allowsTotal: Boolean =
      this != Disabled
  }

  object TotalCountPolicy {
    def parse(value: String): Option[TotalCountPolicy] =
      value.trim.toLowerCase match {
        case "disabled" | "none" | "false" | "off" => Some(Disabled)
        case "optional" | "best-effort" | "besteffort" | "true" | "on" => Some(Optional)
        case "required" | "require" => Some(Required)
        case _ => None
      }
  }

  final case class AdminSurface(
    totalCount: TotalCountPolicy = TotalCountPolicy.Disabled,
    fields: Vector[AdminField] = Vector.empty
  )

  final case class AdminField(
    name: String,
    control: FormControl = FormControl()
  )

  final case class App(
    name: String,
    path: String = "",
    kind: String = "static-form",
    root: Option[String] = None,
    route: Option[String] = None,
    assets: Assets = Assets(),
    theme: Theme = Theme()
  ) {
    def normalizedName: String =
      _normalize_app_segment(name)

    def effectiveKind: String =
      Option(kind).map(_.trim).filter(_.nonEmpty).getOrElse("static-form")

    def effectiveRoot: String =
      root.map(_.trim).filter(_.nonEmpty).getOrElse(s"/web/${normalizedName}")

    def effectiveRoute: String =
      route.map(_.trim).filter(_.nonEmpty).getOrElse(s"/web/{component}/${normalizedName}")

    def effectivePath: String =
      Option(path).map(_.trim).filter(_.nonEmpty).getOrElse(effectiveRoot)

    def completed: App =
      copy(
        path = effectivePath,
        kind = effectiveKind,
        root = Some(effectiveRoot),
        route = Some(effectiveRoute)
      )

    def completedFor(componentSegment: Option[String]): App = {
      val c = completed
      componentSegment.map(_.trim).filter(_.nonEmpty) match {
        case Some(component) =>
          c.copy(route = c.route.map(_.replace("{component}", component)))
        case None =>
          c
      }
    }

    def matches(requestName: String, requestPath: Vector[String]): Boolean = {
      val normalizedRequestName = _normalize_app_segment(requestName)
      val normalizedRequestPath = requestPath.map(_normalize_app_segment)
      val appPath = effectivePath.split("/").toVector.filter(_.nonEmpty).map(_normalize_app_segment)
      normalizedName == normalizedRequestName ||
        appPath == ("web" +: normalizedRequestName +: normalizedRequestPath) ||
        appPath == ("web" +: normalizedRequestName +: Vector(_normalize_app_segment(effectiveKind)))
    }
  }

  enum RouteKind {
    case Alias
    case Default

    def name: String =
      this match {
        case Alias => "alias"
        case Default => "default"
      }
  }

  object RouteKind {
    def parse(value: String): Option[RouteKind] =
      value.trim.toLowerCase match {
        case "alias" => Some(RouteKind.Alias)
        case "default" => Some(RouteKind.Default)
        case _ => None
      }
  }

  final case class RouteTarget(
    component: String,
    app: String
  ) {
    def normalizedComponent: String =
      _normalize_app_segment(component)

    def normalizedApp: String =
      _normalize_app_segment(app)
  }

  final case class Route(
    path: String,
    target: RouteTarget,
    kind: RouteKind = RouteKind.Alias
  ) {
    def normalizedPath: Vector[String] =
      path.split("/").toVector.filter(_.nonEmpty).map(_normalize_app_segment)

    def normalizedPathText: String =
      "/" + normalizedPath.mkString("/")

    def conflictSignature: (RouteKind, String, String) =
      (kind, target.normalizedComponent, target.normalizedApp)

    def resolve(requestPath: Vector[String]): Option[ResolvedRoute] = {
      val routePath = normalizedPath
      val normalizedRequest = requestPath.map(_normalize_app_segment)
      Option.when(
        routePath.nonEmpty &&
          normalizedRequest.startsWith(routePath) &&
          routePath.headOption.contains("web")
      ) {
        ResolvedRoute(
          target,
          kind,
          normalizedRequest.drop(routePath.length)
        )
      }
    }
  }

  final case class ResolvedRoute(
    target: RouteTarget,
    kind: RouteKind,
    remainingPath: Vector[String]
  )

  final case class Assets(
    autoComplete: Boolean = true,
    css: Vector[String] = Vector.empty,
    js: Vector[String] = Vector.empty
  ) {
    def merge(rhs: Assets): Assets =
      Assets(
        autoComplete && rhs.autoComplete,
        (css ++ rhs.css).distinct,
        (js ++ rhs.js).distinct
      )
  }

  final case class Theme(
    name: Option[String] = None,
    css: Vector[String] = Vector.empty,
    variables: Map[String, String] = Map.empty
  ) {
    def merge(rhs: Theme): Theme =
      Theme(
        rhs.name.orElse(name),
        (css ++ rhs.css).distinct,
        variables ++ rhs.variables
      )

    def toLayoutOptions: StaticFormAppLayout.ThemeOptions =
      StaticFormAppLayout.ThemeOptions(name, css, variables)
  }

  val empty: WebDescriptor = WebDescriptor()

  def formSelector(
    componentName: String,
    serviceName: String,
    operationName: String
  ): String =
    Vector(componentName, serviceName, operationName)
      .map(org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment)
      .mkString(".")

  def load(path: Path): Consequence[WebDescriptor] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"web descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path)) {
      _descriptor_files(path).find(Files.isRegularFile(_)) match {
        case Some(file) => load(file)
        case None => Consequence.resourceNotFound(s"web descriptor not found: ${path.resolve("web")}")
      }
    } else if (_is_archive_file(path)) {
      _load_archive_file(path)
    } else {
      DescriptorRecordLoader.load(path).flatMap { records =>
        records.headOption match {
          case Some(record) => _validate(fromRecord(record), path)
          case None => Consequence.resourceInvalid(s"web descriptor is empty: ${path}")
        }
      }
    }

  def fromRecord(record: Record): WebDescriptor = {
    val web = _record_value(record, "web").getOrElse(record)
    WebDescriptor(
      defaultView = _string(web, "defaultView")
        .orElse(_string(web, "default-view"))
        .getOrElse(WebTableColumnResolver.defaultViewName),
      expose = _expose(web),
      auth = _auth(web),
      authorization = _authorization(web),
      form = _form(web),
      apps = _apps(web),
      routes = _routes(web),
      theme = _theme(web),
      assets = _assets(web),
      admin = _admin(web)
    )
  }

  private def _validate(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] =
    _validate_routes(descriptor.routes, path).map { routes =>
      descriptor.copy(routes = routes)
    }

  private def _validate_routes(
    routes: Vector[Route],
    path: Path
  ): Consequence[Vector[Route]] = {
    val conflicts = routes
      .groupBy(_.normalizedPathText)
      .toVector
      .flatMap {
        case (routePath, xs) =>
          val signatures = xs.map(_.conflictSignature).distinct
          Option.when(signatures.size > 1)(routePath -> xs)
      }
    conflicts.headOption match {
      case Some((routePath, xs)) =>
        val targets = xs.map { route =>
          s"${route.kind.name}:${route.target.normalizedComponent}/${route.target.normalizedApp}"
        }.distinct.mkString(", ")
        Consequence.resourceInvalid(s"web route conflict in ${path}: ${routePath} -> ${targets}")
      case None =>
        Consequence.success(routes.distinctBy(route => route.normalizedPathText -> route.conflictSignature))
    }
  }

  private def _expose(record: Record): Map[String, Exposure] =
    _record_value(record, "expose")
      .map(_.asMap.toVector.flatMap {
        case (key, value) => Exposure.parse(value.toString).map(key -> _)
      }.toMap)
      .getOrElse(Map.empty)

  private def _auth(record: Record): Auth =
    Auth(
      mode = _record_value(record, "auth").flatMap(_.getString("mode")).getOrElse("none")
    )

  private def _authorization(record: Record): Map[String, Authorization] =
    _record_value(record, "authorization")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map { r =>
            key -> Authorization(
              roles = _string_vector(r, "roles"),
              scopes = _string_vector(r, "scopes"),
              capabilities = _string_vector(r, "capabilities"),
              operationModes = _operation_modes(r),
              anonymousOperationModes = _anonymous_operation_modes(r),
              allowAnonymous = _boolean(r, "allowAnonymous")
                .orElse(_boolean(r, "allow-anonymous"))
                .getOrElse(false)
            )
          }
      }.toMap)
      .getOrElse(Map.empty)

  private def _operation_modes(record: Record): Vector[OperationMode] =
    (_string_vector(record, "operationModes") ++ _string_vector(record, "operation-modes"))
      .flatMap(OperationMode.from)
      .distinct

  private def _anonymous_operation_modes(record: Record): Vector[OperationMode] =
    (
      _string_vector(record, "anonymousOperationModes") ++
        _string_vector(record, "anonymous-operation-modes")
    ).flatMap(OperationMode.from).distinct

  private def _form(record: Record): Map[String, Form] =
    _record_value(record, "form")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map { r =>
            key -> Form(
              enabled = _boolean(r, "enabled"),
              successRedirect = _string(r, "successRedirect").orElse(_string(r, "success-redirect")),
              failureRedirect = _string(r, "failureRedirect").orElse(_string(r, "failure-redirect")),
              stayOnError = _boolean(r, "stayOnError").orElse(_boolean(r, "stay-on-error")).getOrElse(false),
              resultTemplate = _string(r, "resultTemplate").orElse(_string(r, "result-template")),
              assets = _assets(r),
              controls = _form_controls(r)
            )
          }
      }.toMap)
      .getOrElse(Map.empty)

  private def _form_controls(record: Record): Map[String, FormControl] =
    _record_value(record, "controls")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map(r => key -> _form_control(r))
      }.toMap)
      .getOrElse(Map.empty)

  private def _form_control(record: Record): FormControl =
    FormControl(
      controlType = _string(record, "type").orElse(_string(record, "controlType")).orElse(_string(record, "control-type")),
      hidden = _boolean(record, "hidden").getOrElse(false),
      system = _boolean(record, "system").getOrElse(false),
      values = _string_vector(record, "values"),
      multiple = _boolean(record, "multiple").getOrElse(false),
      required = _boolean(record, "required"),
      readonly = _boolean(record, "readonly").orElse(_boolean(record, "readOnly")).orElse(_boolean(record, "read-only")).getOrElse(false),
      placeholder = _string(record, "placeholder"),
      help = _string(record, "help")
    )

  private def _apps(record: Record): Vector[App] =
    record.getAny("apps") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record).flatMap(_app)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record).flatMap(_app)
      case _ => Vector.empty
    }

  private def _app(record: Record): Option[App] =
    for {
      name <- record.getString("name").map(_.trim).filter(_.nonEmpty)
    } yield {
      val root = record.getString("root").map(_.trim).filter(_.nonEmpty)
      App(
        name = name,
        path = record.getString("path").map(_.trim).filter(_.nonEmpty).orElse(root).getOrElse(""),
        kind = record.getString("kind").map(_.trim).filter(_.nonEmpty).getOrElse("static-form"),
        root = root,
        route = record.getString("route").map(_.trim).filter(_.nonEmpty),
        theme = _theme(record),
        assets = _assets(record)
      )
    }

  private def _routes(record: Record): Vector[Route] =
    record.getAny("routes") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record).flatMap(_route)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record).flatMap(_route)
      case _ => Vector.empty
    }

  private def _assets(record: Record): Assets =
    _record_value(record, "assets")
      .orElse(_record_value(record, "asset"))
      .flatMap(_any_to_record)
      .map { r =>
        Assets(
          autoComplete = _boolean(r, "autoComplete")
            .orElse(_boolean(r, "auto-complete"))
            .getOrElse(true),
          css = _string_vector(r, "css") ++ _string_vector(r, "styles"),
          js = _string_vector(r, "js") ++ _string_vector(r, "scripts")
        )
      }.getOrElse(Assets())

  private def _theme(record: Record): Theme =
    _record_value(record, "theme")
      .orElse(_record_value(record, "themes"))
      .map { r =>
        Theme(
          name = _string(r, "name"),
          css = _string_vector(r, "css") ++ _string_vector(r, "styles"),
          variables = _record_value(r, "variables")
            .map(_.asMap.toVector.map { case (key, value) => key -> value.toString }.toMap)
            .getOrElse(Map.empty)
        )
      }.getOrElse(Theme())

  private def _route(record: Record): Option[Route] =
    for {
      path <- record.getString("path").map(_.trim).filter(_.nonEmpty)
      target <- _record_value(record, "target").flatMap(_route_target)
    } yield {
      Route(
        path = path,
        target = target,
        kind = record.getString("kind").flatMap(RouteKind.parse).getOrElse(RouteKind.Alias)
      )
    }

  private def _route_target(record: Record): Option[RouteTarget] =
    for {
      component <- record.getString("component").map(_.trim).filter(_.nonEmpty)
      app <- record.getString("app").map(_.trim).filter(_.nonEmpty)
    } yield RouteTarget(component, app)

  private def _admin(record: Record): Map[String, AdminSurface] =
    _record_value(record, "admin")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map { r =>
            _normalize_selector(key) -> AdminSurface(
              totalCount = _total_count_policy(r).getOrElse(TotalCountPolicy.Disabled),
              fields = _admin_fields(r)
            )
          }
      }.toMap)
      .getOrElse(Map.empty)

  private def _admin_fields(record: Record): Vector[AdminField] = {
    val controls = _form_controls(record)
    val fields = record.getAny("fields") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_admin_field(_, controls))
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_admin_field(_, controls))
      case Some(s: String) => s.split("[,|\\s]+").toVector.flatMap(_admin_field(_, controls))
      case Some(other) => _admin_field(other, controls).toVector
      case None => Vector.empty
    }
    val fieldNames = fields.map(_.name).toSet
    fields ++ controls.toVector.sortBy(_._1).collect {
      case (name, control) if !fieldNames.contains(name) => AdminField(name, control)
    }
  }

  private def _admin_field(
    value: Any,
    controls: Map[String, FormControl]
  ): Option[AdminField] =
    value match {
      case s: String =>
        val name = s.trim
        Option.when(name.nonEmpty)(AdminField(name, controls.getOrElse(name, FormControl())))
      case r: Record =>
        _string(r, "name").map { name =>
          AdminField(name, _form_control(r))
        }
      case m: Map[?, ?] =>
        _admin_field(_map_to_record(m), controls)
      case m: java.util.Map[?, ?] =>
        _admin_field(_map_to_record(m.asScala.toMap), controls)
      case _ =>
        val name = value.toString.trim
        Option.when(name.nonEmpty)(AdminField(name, controls.getOrElse(name, FormControl())))
    }

  private def _total_count_policy(record: Record): Option[TotalCountPolicy] =
    record.getString("totalCount")
      .orElse(record.getString("total-count"))
      .flatMap(TotalCountPolicy.parse)

  private def _record_value(record: Record, key: String): Option[Record] =
    record.getAny(key).flatMap(_any_to_record)

  private def _any_to_record(value: Any): Option[Record] =
    value match {
      case r: Record => Some(r)
      case m: Map[?, ?] => Some(_map_to_record(m))
      case m: java.util.Map[?, ?] => Some(_map_to_record(m.asScala.toMap))
      case _ => None
    }

  private def _map_to_record(m: collection.Map[?, ?]): Record =
    Record.create(m.iterator.map { case (k, v) => k.toString -> _record_value_any(v) }.toSeq)

  private def _record_value_any(value: Any): Any =
    value match {
      case r: Record => r
      case m: Map[?, ?] => _map_to_record(m)
      case m: java.util.Map[?, ?] => _map_to_record(m.asScala.toMap)
      case xs: Seq[?] => xs.toVector.map(_record_value_any)
      case xs: java.util.List[?] => xs.asScala.toVector.map(_record_value_any)
      case other => other
    }

  private def _string_vector(record: Record, key: String): Vector[String] =
    record.getAny(key) match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(s: String) => s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
      case Some(other) => Vector(other.toString.trim).filter(_.nonEmpty)
      case None => Vector.empty
    }

  private def _boolean(record: Record, key: String): Option[Boolean] =
    record.getString(key).flatMap { value =>
      value.trim.toLowerCase match {
        case "true" | "yes" | "on" | "1" => Some(true)
        case "false" | "no" | "off" | "0" => Some(false)
        case _ => None
      }
    }

  private def _string(record: Record, key: String): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private def _normalize_app_segment(value: String): String =
    value.trim.toLowerCase.replace("_", "-")

  private def _normalize_selector(value: String): String =
    value.split("\\.").toVector.map(_normalize_selector_segment).mkString(".")

  private def _normalize_selector_segment(value: String): String =
    value.trim.toLowerCase.replace("_", "-")

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".car") || name.endsWith(".zip")
  }

  private def _descriptor_files(root: Path): Vector[Path] =
    Vector(
      root.resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("web").resolve("web.yaml")
    )

  private def _load_archive_file(path: Path): Consequence[WebDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      _descriptor_files(fs.getPath("/")).find(Files.isRegularFile(_)) match {
        case Some(file) => load(file)
        case None => Consequence.resourceNotFound(s"web descriptor not found in archive: ${path}")
      }
    }
  }
}
