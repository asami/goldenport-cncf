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
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] trait WebDescriptorParsingPart { self: WebDescriptor.type =>
  import WebDescriptor.*

  private[http] def _validate(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] =
    _validate_profiles(descriptor, path).flatMap { descriptor =>
      _validate_web_composition(descriptor, path)
    }.flatMap { descriptor =>
      _validate_routes(descriptor.routes, path).map { routes =>
        descriptor.copy(routes = routes)
      }
    }

  private def _validate_profiles(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] = {
    val invalidglobal =
      descriptor.profileRaw.flatMap(raw => _invalid_profile_message(path, "web.profile", raw))
    val invalidapp =
      descriptor.apps.collectFirst {
        case app if app.profileRaw.exists(raw => WebUxProfile.parse(raw).isEmpty) =>
          s"invalid web UX profile in ${path}: apps.${app.name}.profile=${app.profileRaw.get}"
      }
    val invalidform =
      descriptor.form.collectFirst {
        case (name, form) if form.profileRaw.exists(raw => WebUxProfile.parse(raw).isEmpty) =>
          s"invalid web UX profile in ${path}: form.${name}.profile=${form.profileRaw.get}"
      }
    val invalidpage =
      descriptor.pages.collectFirst {
        case (name, page) if page.profileRaw.exists(raw => WebUxProfile.parse(raw).isEmpty) =>
          s"invalid web UX profile in ${path}: pages.${name}.profile=${page.profileRaw.get}"
      }
    invalidglobal.orElse(invalidapp).orElse(invalidform).orElse(invalidpage) match {
      case Some(message) => Consequence.resourceInvalid(message)
      case None => Consequence.success(descriptor)
    }
  }

  private def _invalid_profile_message(
    path: Path,
    scope: String,
    raw: String
  ): Option[String] =
    Option.when(WebUxProfile.parse(raw).isEmpty)(
      s"invalid web UX profile in ${path}: ${scope}=${raw}"
    )

  private def _validate_web_composition(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] = {
    val invalidapp =
      descriptor.apps.collectFirst {
        case app if app.compositionRaw.exists(raw => ComponentWebComposition.parse(raw).isEmpty) =>
          s"invalid app composition in ${path}: ${app.name}=${app.compositionRaw.get}"
      }
    val invalidpage =
      descriptor.pages.collectFirst {
        case (name, page) if page.modeRaw.exists(raw => PageMode.parse(raw).isEmpty) =>
          s"invalid page mode in ${path}: ${name}=${page.modeRaw.get}"
      }
    val invalidshell =
      descriptor.shell.collect {
        case shell if shell.component.exists(_.trim.isEmpty) =>
          s"invalid web shell in ${path}: component is empty"
      }
    val invalidadminpage =
      descriptor.adminPages.collectFirst {
        case page if page.name.trim.isEmpty =>
          s"invalid admin page in ${path}: name is required"
        case page if page.audienceRaw.exists(raw => AdminAudience.parse(raw).isEmpty) =>
          s"invalid admin page audience in ${path}: ${page.name}=${page.audienceRaw.get}"
        case page if page.componentHrefMismatch.nonEmpty =>
          val (declared, href) = page.componentHrefMismatch.get
          s"invalid admin page component in ${path}: ${page.name} component=${declared}, href component=${href}"
        case page if page.isCanonicalComponentPage && _is_reserved_component_admin_page(page) =>
          s"invalid admin page route in ${path}: ${page.name} is reserved by the component Admin dispatcher"
        case page if !page.isCanonicalComponentPage =>
          s"invalid admin page route in ${path}: ${page.name} must declare one canonical component Admin route"
      }
    invalidapp.orElse(invalidpage).orElse(invalidshell).orElse(invalidadminpage) match {
      case Some(message) => Consequence.resourceInvalid(message)
      case None => Consequence.success(descriptor)
    }
  }

  private def _validate_routes(
    routes: Vector[Route],
    path: Path
  ): Consequence[Vector[Route]] = {
    val conflicts = routes
      .groupBy(_.normalizedPathText)
      .toVector
      .flatMap {
        case (routepath, xs) =>
          val signatures = xs.map(_.conflictSignature).distinct
          Option.when(signatures.size > 1)(routepath -> xs)
      }
    conflicts.headOption match {
      case Some((routepath, xs)) =>
        val targets = xs.map { route =>
          s"${route.kind.name}:${route.target.normalizedComponent}/${route.target.normalizedApp}"
        }.distinct.mkString(", ")
        Consequence.resourceInvalid(s"web route conflict in ${path}: ${routepath} -> ${targets}")
      case None =>
        Consequence.success(routes.distinctBy(route => route.normalizedPathText -> route.conflictSignature))
    }
  }

  private[http] def _expose(record: Record): Map[String, Exposure] =
    _record_value(record, "expose")
      .map(_.asMap.toVector.flatMap {
        case (key, value) => Exposure.parse(value.toString).map(key -> _)
      }.toMap)
      .getOrElse(Map.empty)

  private[http] def _default_form_access(record: Record): Option[Exposure] =
    _record_value(record, "default").flatMap { default =>
      val nested = _record_value(default, "form").flatMap { form =>
        _string(form, "access").flatMap(Exposure.parse)
      }
      nested.orElse(_string(default, "form.access").flatMap(Exposure.parse))
    }

  private[http] def _auth(record: Record): Auth =
    Auth(
      mode = _record_value(record, "auth").flatMap(_.getString("mode")).getOrElse("none")
    )

  private[http] def _authorization(record: Record): Map[String, Authorization] =
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
                .getOrElse(false),
              deny = _boolean(r, "deny").getOrElse(false),
              requireAuthenticated = _boolean(r, "requireAuthenticated")
                .orElse(_boolean(r, "require-authenticated"))
                .orElse(_boolean(r, "require_authenticated"))
                .orElse(_boolean(r, "authenticated"))
                .getOrElse(false),
              requireProviderAuthentication = _boolean(r, "requireProviderAuthentication")
                .orElse(_boolean(r, "require-provider-authentication"))
                .orElse(_boolean(r, "require_provider_authentication"))
                .orElse(_boolean(r, "providerAuthenticated"))
                .orElse(_boolean(r, "provider-authenticated"))
                .orElse(_boolean(r, "provider_authenticated"))
                .getOrElse(false),
              minimumPrivilege = r.getString("minimumPrivilege")
                .orElse(r.getString("minimum-privilege"))
                .orElse(r.getString("minimum_privilege"))
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

  private[http] def _form(record: Record): Map[String, Form] =
    _record_value(record, "form")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _form_value(value).map(key -> _)
      }.toMap)
      .getOrElse(Map.empty)

  private def _form_value(value: Any): Option[Form] =
    value match {
      case null => Some(Form())
      case x: Boolean => Some(Form(enabled = Some(x)))
      case x: java.lang.Boolean => Some(Form(enabled = Some(x.booleanValue)))
      case _ =>
        _any_to_record(value).map { r =>
          Form(
            enabled = _boolean(r, "enabled"),
            access = _string(r, "access").orElse(_string(r, "expose")).flatMap(Exposure.parse),
            successRedirect = _string(r, "successRedirect").orElse(_string(r, "success-redirect")),
            failureRedirect = _string(r, "failureRedirect").orElse(_string(r, "failure-redirect")),
            successMessageKey = _string(r, "successMessageKey")
              .orElse(_string(r, "success-message-key")),
            failureMessageKey = _string(r, "failureMessageKey")
              .orElse(_string(r, "failure-message-key")),
            stayOnError = _boolean(r, "stayOnError").orElse(_boolean(r, "stay-on-error")).getOrElse(false),
            resultTemplate = _string(r, "resultTemplate").orElse(_string(r, "result-template")),
            layout = _string(r, "layout"),
            profile = _profile(r),
            profileRaw = _profile_raw(r),
            assets = _assets(r),
            controls = _form_controls(r)
          )
        }
    }

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
      label = _string(record, "label"),
      values = _string_vector(record, "values"),
      multiple = _boolean(record, "multiple").getOrElse(false),
      required = _boolean(record, "required"),
      readonly = _boolean(record, "readonly").orElse(_boolean(record, "readOnly")).orElse(_boolean(record, "read-only")).getOrElse(false),
      placeholder = _string(record, "placeholder"),
      help = _string(record, "help"),
      defaultValue = _string(record, "defaultValue").orElse(_string(record, "default-value")).orElse(_string(record, "value"))
    )

  private[http] def _pages(record: Record): Map[String, PageCustomization] =
    _record_value(record, "pages")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map(r => _normalize_selector(key) -> _page_customization(r))
      }.toMap)
      .getOrElse(Map.empty)

  private def _page_customization(record: Record): PageCustomization =
  {
    val modeRaw = _string(record, "mode").orElse(_string(record, "pageMode")).orElse(_string(record, "page-mode"))
    val displayraw = _string(record, "display").orElse(_string(record, "pageDisplay")).orElse(_string(record, "page-display"))
    val profileraw = _profile_raw(record)
    PageCustomization(
      title = _string(record, "title"),
      heading = _string(record, "heading"),
      subtitle = _string(record, "subtitle").orElse(_string(record, "description")),
      layout = _string(record, "layout"),
      mode = modeRaw.flatMap(PageMode.parse),
      modeRaw = modeRaw,
      display = displayraw.flatMap(PageDisplay.parse),
      displayRaw = displayraw,
      profile = profileraw.flatMap(WebUxProfile.parse),
      profileRaw = profileraw,
      backButton = _boolean(record, "backButton").orElse(_boolean(record, "back-button")).orElse(_boolean(record, "back_button")),
      submitLabel = _string(record, "submitLabel").orElse(_string(record, "submit-label")),
      fields = _string_vector(record, "fields"),
      controls = _form_controls(record)
    )
  }

  private[http] def _apps(record: Record): Vector[App] =
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
      val compositionRaw =
        _string(record, "composition")
          .orElse(_string(record, "webComposition"))
          .orElse(_string(record, "web-composition"))
      val pagedisplayraw =
        _string(record, "pageDisplay")
          .orElse(_string(record, "page-display"))
          .orElse(_string(record, "componentPageDisplay"))
          .orElse(_string(record, "component-page-display"))
      val profileraw = _profile_raw(record)
      val entryraw = _boolean(record, "entry")
        .orElse(_boolean(record, "componentEntry"))
        .orElse(_boolean(record, "component-entry"))
      App(
        name = name,
        path = record.getString("path").map(_.trim).filter(_.nonEmpty).orElse(root).getOrElse(""),
        kind = record.getString("kind").map(_.trim).filter(_.nonEmpty).getOrElse("static-form"),
        root = root,
        route = record.getString("route").map(_.trim).filter(_.nonEmpty),
        theme = _theme(record),
        assets = _assets(record),
        layout = _string(record, "layout"),
        composition = compositionRaw.flatMap(ComponentWebComposition.parse).getOrElse(ComponentWebComposition.Disabled),
        compositionRaw = compositionRaw,
        pageDisplay = pagedisplayraw.flatMap(PageDisplay.parse),
        pageDisplayRaw = pagedisplayraw,
        profile = profileraw.flatMap(WebUxProfile.parse),
        profileRaw = profileraw,
        pageBackButton = _boolean(record, "pageBackButton").orElse(_boolean(record, "page-back-button")).orElse(_boolean(record, "page_back_button")),
        entry = entryraw.getOrElse(false),
        entryRaw = entryraw
      )
    }

  private[http] def _routes(record: Record): Vector[Route] =
    record.getAny("routes") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record).flatMap(_route)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record).flatMap(_route)
      case _ => Vector.empty
    }

  private[http] def _shell(record: Record): Option[Shell] =
    _record_value(record, "shell")
      .orElse(_record_value(record, "webShell"))
      .orElse(_record_value(record, "web-shell"))
      .map { r =>
        Shell(
          component = _string(r, "component"),
          app = _string(r, "app"),
          layout = _string(r, "layout")
        )
      }

  private[http] def _component_page(record: Record): ComponentPage =
    _record_value(record, "componentPage")
      .orElse(_record_value(record, "component-page"))
      .orElse(_record_value(record, "componentPages"))
      .orElse(_record_value(record, "component-pages"))
      .map { r =>
        val displayraw =
          _string(r, "display")
            .orElse(_string(r, "pageDisplay"))
            .orElse(_string(r, "page-display"))
        ComponentPage(
          display = displayraw.flatMap(PageDisplay.parse),
          displayRaw = displayraw,
          backButton = _boolean(r, "backButton").orElse(_boolean(r, "back-button")).orElse(_boolean(r, "back_button"))
        )
      }
      .getOrElse(ComponentPage())

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

  private[http] def _admin(record: Record): Map[String, AdminSurface] =
    _record_value(record, "admin")
      .map(_.asMap.toVector.flatMap {
        case (key, _) if _normalize_selector(key) == "pages" => None
        case (key, value) =>
          _any_to_record(value).map { r =>
            _normalize_selector(key) -> AdminSurface(
              totalCount = _total_count_policy(r).getOrElse(TotalCountPolicy.Disabled),
              fields = _admin_fields(r)
            )
          }
      }.toMap)
      .getOrElse(Map.empty)

  private[http] def _admin_pages(record: Record): Vector[AdminPage] =
    _record_value(record, "admin")
      .flatMap(_.getAny("pages"))
      .map { value =>
        val pages: Vector[AdminPage] = value match {
        case xs: Seq[?] => xs.toVector.flatMap(_admin_page(_, None))
        case xs: java.util.List[?] => xs.asScala.toVector.flatMap(_admin_page(_, None))
        case r: Record =>
          r.asMap.toVector.flatMap {
            case (name, value) => _admin_page(value, Some(name))
          }
        case m: Map[?, ?] =>
          m.toVector.flatMap {
            case (name, pageValue) => _admin_page(pageValue, Some(name.toString))
          }
        case m: java.util.Map[?, ?] =>
          m.asScala.toVector.flatMap {
            case (name, pageValue) => _admin_page(pageValue, Some(name.toString))
          }
        case other => _admin_page(other, None).toVector
        }
        pages
      }.getOrElse(Vector.empty)

  private def _admin_page(
    value: Any,
    namehint: Option[String]
  ): Option[AdminPage] =
    value match {
      case r: Record =>
        val name = _string(r, "name")
          .orElse(namehint.map(_.trim).filter(_.nonEmpty))
          .getOrElse("")
        Some(AdminPage(
          name = name,
          label = _string(r, "label").getOrElse(""),
          href = _string(r, "href").getOrElse(""),
          description = _string(r, "description").getOrElse(""),
          permission = _string(r, "permission"),
          component = _string(r, "component"),
          audience = _string(r, "audience").flatMap(AdminAudience.parse).getOrElse(AdminAudience.Application),
          audienceRaw = _string(r, "audience")
        ))
      case m: Map[?, ?] => _admin_page(_map_to_record(m), namehint)
      case m: java.util.Map[?, ?] => _admin_page(_map_to_record(m.asScala.toMap), namehint)
      case s: String =>
        val name = namehint.getOrElse(s).trim
        Option.when(name.nonEmpty)(AdminPage(name = name, label = s.trim))
      case _ => None
    }

  private def _admin_fields(record: Record): Vector[AdminField] = {
    val controls = _form_controls(record)
    val fields = record.getAny("fields") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_admin_field(_, controls))
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_admin_field(_, controls))
      case Some(s: String) => s.split("[,|\\s]+").toVector.flatMap(_admin_field(_, controls))
      case Some(other) => _admin_field(other, controls).toVector
      case None => Vector.empty
    }
    val fieldnames = fields.map(_.name).toSet
    fields ++ controls.toVector.sortBy(_._1).collect {
      case (name, control) if !fieldnames.contains(name) => AdminField(name, control)
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

  private[http] def _record_value(record: Record, key: String): Option[Record] =
    record.getAny(key).flatMap(_any_to_record)

  private[http] def _any_to_record(value: Any): Option[Record] =
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

  private[http] def _string_vector(record: Record, key: String): Vector[String] =
    record.getAny(key) match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(s: String) => s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
      case Some(other) => Vector(other.toString.trim).filter(_.nonEmpty)
      case None => Vector.empty
    }

  private[http] def _boolean(record: Record, key: String): Option[Boolean] =
    record.getString(key).flatMap { value =>
      value.trim.toLowerCase match {
        case "true" | "yes" | "on" | "1" => Some(true)
        case "false" | "no" | "off" | "0" => Some(false)
        case _ => None
      }
    }

  private[http] def _profile(record: Record): Option[WebUxProfile] =
    _profile_raw(record).flatMap(WebUxProfile.parse)

  private[http] def _profile_raw(record: Record): Option[String] =
    _string(record, "profile")
      .orElse(_string(record, "uxProfile"))
      .orElse(_string(record, "ux-profile"))

  private[http] def _string(record: Record, key: String): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private[http] def _normalize_app_segment(value: String): String =
    value.trim.toLowerCase.replace("_", "-")

  private[http] def _canonical_admin_segment(value: String): Option[String] =
    Option(value).filter(segment => segment == segment.trim).filter(_.nonEmpty).filter { segment =>
      _normalize_app_segment(segment) == segment &&
        segment.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")
    }

  private[http] def _is_reserved_component_admin_page(page: AdminPage): Boolean =
    page.component.nonEmpty &&
      _canonical_admin_segment(page.name).exists(value => !value.contains(".") && _reserved_component_admin_page_names.contains(value))

  private[http] def _normalize_selector(value: String): String =
    value.split("\\.").toVector.map(_normalize_selector_segment).mkString(".")

  private def _normalize_selector_segment(value: String): String =
    value.trim.toLowerCase.replace("_", "-")

}
