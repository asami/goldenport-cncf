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
 *  version Apr. 25, 2026
 *  version May. 30, 2026
 * @version Jun.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebDescriptor(
  defaultView: String = WebTableColumnResolver.defaultViewName,
  defaultFormAccess: Option[WebDescriptor.Exposure] = None,
  expose: Map[String, WebDescriptor.Exposure] = Map.empty,
  auth: WebDescriptor.Auth = WebDescriptor.Auth(),
  authorization: Map[String, WebDescriptor.Authorization] = Map.empty,
  form: Map[String, WebDescriptor.Form] = Map.empty,
  apps: Vector[WebDescriptor.App] = Vector.empty,
  routes: Vector[WebDescriptor.Route] = Vector.empty,
  shell: Option[WebDescriptor.Shell] = None,
  componentPage: WebDescriptor.ComponentPage = WebDescriptor.ComponentPage(),
  pages: Map[String, WebDescriptor.PageCustomization] = Map.empty,
  theme: WebDescriptor.Theme = WebDescriptor.Theme(),
  assets: WebDescriptor.Assets = WebDescriptor.Assets(),
  admin: Map[String, WebDescriptor.AdminSurface] = Map.empty,
  adminPages: Vector[WebDescriptor.AdminPage] = Vector.empty
) {
  def mergeOverride(rhs: WebDescriptor): WebDescriptor = {
    def _merge_vector_[A, K](
      lhs: Vector[A],
      rhs: Vector[A]
    )(key: A => K): Vector[A] = {
      val r = rhs.map(x => key(x) -> x).toMap
      val keys = (lhs.map(key) ++ rhs.map(key)).distinct
      val l = lhs.map(x => key(x) -> x).toMap
      keys.flatMap(k => r.get(k).orElse(l.get(k)))
    }
    def _merge_apps_(lhs: Vector[WebDescriptor.App], rhs: Vector[WebDescriptor.App]): Vector[WebDescriptor.App] = {
      val r = rhs.map(x => x.normalizedName -> x).toMap
      val l = lhs.map(x => x.normalizedName -> x).toMap
      val keys = (lhs.map(_.normalizedName) ++ rhs.map(_.normalizedName)).distinct
      keys.flatMap { key =>
        (l.get(key), r.get(key)) match {
          case (Some(left), Some(right)) => Some(left.mergeOverride(right))
          case (Some(left), None) => Some(left)
          case (None, Some(right)) => Some(right)
          case _ => None
        }
      }
    }
    def _merge_form_(
      lhs: Map[String, WebDescriptor.Form],
      rhs: Map[String, WebDescriptor.Form]
    ): Map[String, WebDescriptor.Form] = {
      val keys = (lhs.keys ++ rhs.keys).toVector.distinct
      keys.flatMap { key =>
        (lhs.get(key), rhs.get(key)) match {
          case (Some(left), Some(right)) => Some(key -> left.mergeOverride(right))
          case (Some(left), None) => Some(key -> left)
          case (None, Some(right)) => Some(key -> right)
          case _ => None
        }
      }.toMap
    }

    copy(
      defaultView =
        if (rhs.defaultView == WebTableColumnResolver.defaultViewName) defaultView
        else rhs.defaultView,
      defaultFormAccess = rhs.defaultFormAccess.orElse(defaultFormAccess),
      expose = expose ++ rhs.expose,
      auth =
        if (rhs.auth == WebDescriptor.Auth()) auth
        else rhs.auth,
      authorization = authorization ++ rhs.authorization,
      form = _merge_form_(form, rhs.form),
      apps = _merge_apps_(apps, rhs.apps),
      routes = _merge_vector_(routes, rhs.routes)(_.normalizedPathText),
      shell = rhs.shell.orElse(shell),
      componentPage =
        if (rhs.componentPage == WebDescriptor.ComponentPage()) componentPage
        else rhs.componentPage,
      pages = pages ++ rhs.pages,
      theme = theme.merge(rhs.theme),
      assets = assets.merge(rhs.assets),
      admin = admin ++ rhs.admin,
      adminPages = _merge_vector_(adminPages, rhs.adminPages)(_.scopeKey)
    )
  }

  def hasControls: Boolean =
    expose.nonEmpty ||
      authorization.nonEmpty ||
      form.nonEmpty ||
      apps.nonEmpty ||
      routes.nonEmpty ||
      shell.nonEmpty ||
      componentPage != WebDescriptor.ComponentPage() ||
      theme != WebDescriptor.Theme() ||
      assets != WebDescriptor.Assets() ||
      admin.nonEmpty ||
      adminPages.nonEmpty ||
      defaultView != WebTableColumnResolver.defaultViewName ||
      defaultFormAccess.nonEmpty ||
      auth != WebDescriptor.Auth()

  def exposureOf(selector: String): WebDescriptor.Exposure =
    form.get(selector)
      .filterNot(_.enabled.contains(false))
      .flatMap(_.access)
      .orElse(expose.get(selector))
      .orElse(form.get(selector).filterNot(_.enabled.contains(false)).map(_ => effectiveDefaultFormAccess))
      .getOrElse(WebDescriptor.Exposure.Internal)

  def effectiveDefaultFormAccess: WebDescriptor.Exposure =
    defaultFormAccess.getOrElse {
      if (auth.mode.trim.equalsIgnoreCase("none")) WebDescriptor.Exposure.Public
      else WebDescriptor.Exposure.Protected
    }

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

  def appKind(name: String): Option[String] =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    ).map(_.effectiveKind)

  def appLayout(name: String): Option[String] =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    ).flatMap(_.layoutName)

  def appComposition(name: String): WebDescriptor.ComponentWebComposition =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    ).map(_.composition).getOrElse(WebDescriptor.ComponentWebComposition.Disabled)

  def routeAppsForComponent(componentName: String): Vector[String] = {
    val normalized = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentName)
    routes
      .filter(_.target.normalizedComponent == normalized)
      .map(_.target.normalizedApp)
      .distinct
  }

  def routeAppForComponent(componentName: String): Option[String] =
    routeAppsForComponent(componentName) match {
      case Vector(app) => Some(app)
      case _ => None
    }

  def routeAppForComponentPage(
    componentName: String,
    page: Vector[String]
  ): Option[String] = {
    val apps = routeAppsForComponent(componentName)
    val pageName =
      if (page.isEmpty) "index"
      else page.map(_.stripSuffix(".html")).map(WebDescriptor.normalizeSelector).mkString(".")
    val pageSpecific = apps.filter { app =>
      pages.contains(s"${WebDescriptor.normalizeSelector(app)}.${pageName}")
    }
    pageSpecific match {
      case Vector(app) => Some(app)
      case _ =>
        val composed = apps.filter(appComposition(_) != WebDescriptor.ComponentWebComposition.Disabled)
        composed match {
          case Vector(app) => Some(app)
          case _ => routeAppForComponent(componentName)
        }
    }
  }

  def shellComponentName: Option[String] =
    shell.flatMap(_.componentName)

  def shellAppName: Option[String] =
    shell.map(_.effectiveAppName)

  def shellLayoutName: Option[String] =
    shell.flatMap(_.layoutName)

  def staticPageMode(
    appName: String,
    page: Vector[String]
  ): WebDescriptor.PageMode =
    staticPageCustomization(appName, page)
      .flatMap(_.mode)
      .getOrElse(WebDescriptor.PageMode.Article)

  def staticPageDisplay(
    appName: String,
    page: Vector[String]
  ): WebDescriptor.PageDisplay =
    staticPageCustomization(appName, page).flatMap(_.display)
      .orElse(appFor(appName).flatMap(_.pageDisplay))
      .orElse(componentPage.display)
      .getOrElse(WebDescriptor.PageDisplay.ApplicationShell)

  def staticPageBackButton(
    appName: String,
    page: Vector[String]
  ): Boolean =
    staticPageCustomization(appName, page).flatMap(_.backButton)
      .orElse(appFor(appName).flatMap(_.pageBackButton))
      .orElse(componentPage.backButton)
      .getOrElse(true)

  def appFor(name: String): Option[WebDescriptor.App] =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    )

  def themeFor(appName: Option[String] = None): WebDescriptor.Theme =
    appName
      .flatMap(name => apps.find(app =>
        app.matches(name, Vector.empty) ||
          app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
      ).map(_.theme))
      .map(theme.merge)
      .getOrElse(theme)

  def pageCustomization(
    componentName: Option[String],
    appName: Option[String]
  ): Option[WebDescriptor.PageCustomization] = {
    val app = appName.map(WebDescriptor.normalizeSelector)
    val component = componentName.map(WebDescriptor.normalizeSelector)
    val candidates =
      (for {
        c <- component.toVector
        a <- app.toVector
      } yield s"${c}.${a}") ++ app.toVector
    candidates.collectFirst(Function.unlift(pages.get))
  }

  def staticPageCustomization(
    appName: String,
    page: Vector[String]
  ): Option[WebDescriptor.PageCustomization] = {
    val app = WebDescriptor.normalizeSelector(appName)
    val pageName =
      if (page.isEmpty) "index"
      else page.map(_.stripSuffix(".html")).map(WebDescriptor.normalizeSelector).mkString(".")
    val candidates =
      if (page.isEmpty)
        Vector(s"${app}.${pageName}", app, pageName)
      else
        Vector(s"${app}.${pageName}", pageName, app)
    candidates.collectFirst(Function.unlift(pages.get))
  }

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

  def adminPagesFor(
    componentName: String
  ): Vector[WebDescriptor.AdminPage] =
    adminPages.filter(_.matchesComponent(componentName))

  def adminPagesForAudience(
    audience: WebDescriptor.AdminAudience
  ): Vector[WebDescriptor.AdminPage] =
    adminPages.filter(_.audience == audience)

  def adminPage(
    componentName: String,
    pageName: String
  ): Option[WebDescriptor.AdminPage] = {
    val page = WebDescriptor.normalizeSelector(pageName)
    adminPagesFor(componentName).find(_.normalizedName == page)
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
        case "authenticated" => Some(Protected)
        case "anonymous" => Some(Public)
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
    allowAnonymous: Boolean = false,
    deny: Boolean = false,
    requireAuthenticated: Boolean = false,
    requireProviderAuthentication: Boolean = false,
    minimumPrivilege: Option[String] = None
  )

  final case class Form(
    enabled: Option[Boolean] = None,
    access: Option[Exposure] = None,
    successRedirect: Option[String] = None,
    failureRedirect: Option[String] = None,
    stayOnError: Boolean = false,
    resultTemplate: Option[String] = None,
    layout: Option[String] = None,
    assets: Assets = Assets(),
    controls: Map[String, FormControl] = Map.empty
  ) {
    def mergeOverride(rhs: Form): Form =
      Form(
        enabled = rhs.enabled.orElse(enabled),
        access = rhs.access.orElse(access),
        successRedirect = rhs.successRedirect.orElse(successRedirect),
        failureRedirect = rhs.failureRedirect.orElse(failureRedirect),
        stayOnError = stayOnError || rhs.stayOnError,
        resultTemplate = rhs.resultTemplate.orElse(resultTemplate),
        layout = rhs.layout.orElse(layout),
        assets = assets.merge(rhs.assets),
        controls = controls ++ rhs.controls
      )
  }

  final case class FormControl(
    controlType: Option[String] = None,
    hidden: Boolean = false,
    system: Boolean = false,
    label: Option[String] = None,
    values: Vector[String] = Vector.empty,
    multiple: Boolean = false,
    required: Option[Boolean] = None,
    readonly: Boolean = false,
    placeholder: Option[String] = None,
    help: Option[String] = None,
    defaultValue: Option[String] = None,
    validation: org.goldenport.schema.WebValidationHints = org.goldenport.schema.WebValidationHints.empty
  )

  final case class PageCustomization(
    title: Option[String] = None,
    heading: Option[String] = None,
    subtitle: Option[String] = None,
    layout: Option[String] = None,
    mode: Option[PageMode] = None,
    modeRaw: Option[String] = None,
    display: Option[PageDisplay] = None,
    displayRaw: Option[String] = None,
    backButton: Option[Boolean] = None,
    submitLabel: Option[String] = None,
    fields: Vector[String] = Vector.empty,
    controls: Map[String, FormControl] = Map.empty
  )

  enum PageDisplay {
    case ApplicationShell
    case Standalone

    def name: String =
      this match {
        case ApplicationShell => "shell"
        case Standalone => "standalone"
      }
  }

  object PageDisplay {
    def parse(value: String): Option[PageDisplay] =
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "shell" | "application-shell" | "app-shell" | "embedded" | "embed" => Some(ApplicationShell)
        case "standalone" | "independent" | "page" | "own-page" => Some(Standalone)
        case _ => None
      }
  }

  final case class ComponentPage(
    display: Option[PageDisplay] = None,
    displayRaw: Option[String] = None,
    backButton: Option[Boolean] = None
  )

  enum PageMode {
    case Article
    case Screen

    def name: String =
      this match {
        case Article => "article"
        case Screen => "screen"
      }
  }

  object PageMode {
    def parse(value: String): Option[PageMode] =
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "article" | "embed" | "embedded" => Some(Article)
        case "screen" | "full-screen" | "fullscreen" | "page" => Some(Screen)
        case _ => None
      }
  }

  enum ComponentWebComposition {
    case Disabled
    case Article

    def name: String =
      this match {
        case Disabled => "disabled"
        case Article => "article"
      }

    def isArticle: Boolean =
      this == Article
  }

  object ComponentWebComposition {
    def parse(value: String): Option[ComponentWebComposition] =
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "disabled" | "disable" | "none" | "false" | "off" => Some(Disabled)
        case "article" | "embed" | "embedded" | "subsystem-shell" => Some(Article)
        case _ => None
      }
  }

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

  final case class AdminPage(
    name: String,
    label: String = "",
    href: String = "",
    description: String = "",
    permission: Option[String] = None,
    component: Option[String] = None,
    audience: AdminAudience = AdminAudience.Application,
    audienceRaw: Option[String] = None
  ) {
    def normalizedName: String =
      WebDescriptor.normalizeSelector(name)

    def effectiveLabel: String =
      Option(label).map(_.trim).filter(_.nonEmpty).getOrElse(name)

    def effectivePermission: String =
      permission.map(_.trim).filter(_.nonEmpty).getOrElse("admin.entity.read")

    def isApplicationAudience: Boolean =
      audience == AdminAudience.Application

    def isSystemAudience: Boolean =
      audience == AdminAudience.System

    def scopeKey: String =
      Vector(component.map(_normalize_app_segment).orElse(_href_component), Some(audience.name), Some(normalizedName)).flatten.mkString(":")

    def matchesComponent(componentName: String): Boolean = {
      val target = _normalize_app_segment(componentName)
      component.map(_normalize_app_segment) match {
        case Some(value) => value == target
        case None => _href_component.contains(target)
      }
    }

    def componentHrefMismatch: Option[(String, String)] =
      for {
        declared <- component.map(_normalize_app_segment)
        href <- _href_component
        if declared != href
      } yield declared -> href

    private def _href_component: Option[String] = {
      val parts = Option(href).map(_.trim).filter(_.nonEmpty).getOrElse("").split("/").toVector.filter(_.nonEmpty)
      parts match {
        case Vector("web", componentName, "admin", _*) => Some(_normalize_app_segment(componentName))
        case _ => None
      }
    }
  }

  enum AdminAudience {
    case Application
    case System

    def name: String =
      this match {
        case Application => "application"
        case System => "system"
      }
  }

  object AdminAudience {
    def parse(value: String): Option[AdminAudience] =
      Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).flatMap {
        case "application" | "app" | "operator" => Some(Application)
        case "system" | "runtime" => Some(System)
        case _ => None
      }
  }

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
    theme: Theme = Theme(),
    layout: Option[String] = None,
    composition: ComponentWebComposition = ComponentWebComposition.Disabled,
    compositionRaw: Option[String] = None,
    pageDisplay: Option[PageDisplay] = None,
    pageDisplayRaw: Option[String] = None,
    pageBackButton: Option[Boolean] = None
  ) {
    def normalizedName: String =
      _normalize_app_segment(name)

    def effectiveKind: String =
      Option(kind).map(_.trim).filter(_.nonEmpty).getOrElse("static-form")

    def effectiveRoot: String =
      root.map(_.trim).filter(_.nonEmpty).getOrElse(s"/web/${normalizedName}")

    def effectiveRoute: String =
      route.map(_.trim).filter(_.nonEmpty).getOrElse(s"/web/{component}/${normalizedName}")

    def layoutName: Option[String] =
      layout.map(_.trim).filter(_.nonEmpty)

    def mergeOverride(rhs: App): App =
      copy(
        name = rhs.name,
        path = Option(rhs.path).map(_.trim).filter(_.nonEmpty).getOrElse(path),
        kind =
          if (rhs.kind.trim.nonEmpty && rhs.kind != "static-form") rhs.kind
          else kind,
        root = rhs.root.orElse(root),
        route = rhs.route.orElse(route),
        assets = assets.merge(rhs.assets),
        theme = theme.merge(rhs.theme),
        layout = rhs.layout.orElse(layout),
        composition = rhs.compositionRaw.map(_ => rhs.composition).getOrElse(composition),
        compositionRaw = rhs.compositionRaw.orElse(compositionRaw),
        pageDisplay = rhs.pageDisplayRaw.map(_ => rhs.pageDisplay).getOrElse(pageDisplay),
        pageDisplayRaw = rhs.pageDisplayRaw.orElse(pageDisplayRaw),
        pageBackButton = rhs.pageBackButton.orElse(pageBackButton)
      )

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

  final case class Shell(
    component: Option[String] = None,
    app: Option[String] = None,
    layout: Option[String] = None
  ) {
    def componentName: Option[String] =
      component.map(_.trim).filter(_.nonEmpty).map(_normalize_app_segment)

    def appName: Option[String] =
      app.map(_.trim).filter(_.nonEmpty).map(_normalize_app_segment)

    def effectiveAppName: String =
      appName.orElse(componentName).getOrElse("default")

    def layoutName: Option[String] =
      layout.map(_.trim).filter(_.nonEmpty)
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
    js: Vector[String] = Vector.empty,
    favicon: Option[String] = None
  ) {
    def merge(rhs: Assets): Assets =
      Assets(
        autoComplete && rhs.autoComplete,
        (css ++ rhs.css).distinct,
        (js ++ rhs.js).distinct,
        rhs.favicon.orElse(favicon)
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
      _descriptor_files(path).filter(Files.isRegularFile(_)) match {
        case Vector() => Consequence.resourceNotFound(s"web descriptor not found: ${path.resolve("web")}")
        case files => _load_descriptor_files(files)
      }
    } else if (_is_archive_file(path)) {
      _load_archive_file(path)
    } else {
      _load_descriptor_file(path)
    }

  private def _load_descriptor_files(
    files: Vector[Path]
  ): Consequence[WebDescriptor] =
    files.foldLeft(Consequence.success(WebDescriptor.empty)) { (r, file) =>
      r.flatMap { descriptor =>
        _load_descriptor_file(file).map(descriptor.mergeOverride)
      }
    }

  private def _load_descriptor_file(
    path: Path
  ): Consequence[WebDescriptor] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption match {
        case Some(record) => _validate(fromRecord(record), path)
        case None => Consequence.resourceInvalid(s"web descriptor is empty: ${path}")
      }
    }

  def fromRecord(record: Record): WebDescriptor = {
    val web = _record_value(record, "web").getOrElse(record)
    WebDescriptor(
      defaultView = _string(web, "defaultView")
        .orElse(_string(web, "default-view"))
        .getOrElse(WebTableColumnResolver.defaultViewName),
      defaultFormAccess = _default_form_access(web),
      expose = _expose(web),
      auth = _auth(web),
      authorization = _authorization(web),
      form = _form(web),
      apps = _apps(web),
      routes = _routes(web),
      shell = _shell(web),
      componentPage = _component_page(web),
      pages = _pages(web),
      theme = _theme(web),
      assets = _assets(web),
      admin = _admin(web),
      adminPages = _admin_pages(web)
    )
  }

  private def _validate(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] =
    _validate_web_composition(descriptor, path).flatMap { descriptor =>
      _validate_routes(descriptor.routes, path).map { routes =>
        descriptor.copy(routes = routes)
      }
    }

  private def _validate_web_composition(
    descriptor: WebDescriptor,
    path: Path
  ): Consequence[WebDescriptor] = {
    val invalidApp =
      descriptor.apps.collectFirst {
        case app if app.compositionRaw.exists(raw => ComponentWebComposition.parse(raw).isEmpty) =>
          s"invalid app composition in ${path}: ${app.name}=${app.compositionRaw.get}"
      }
    val invalidPage =
      descriptor.pages.collectFirst {
        case (name, page) if page.modeRaw.exists(raw => PageMode.parse(raw).isEmpty) =>
          s"invalid page mode in ${path}: ${name}=${page.modeRaw.get}"
      }
    val invalidShell =
      descriptor.shell.collect {
        case shell if shell.component.exists(_.trim.isEmpty) =>
          s"invalid web shell in ${path}: component is empty"
      }
    val invalidAdminPage =
      descriptor.adminPages.collectFirst {
        case page if page.name.trim.isEmpty =>
          s"invalid admin page in ${path}: name is required"
        case page if page.audienceRaw.exists(raw => AdminAudience.parse(raw).isEmpty) =>
          s"invalid admin page audience in ${path}: ${page.name}=${page.audienceRaw.get}"
        case page if page.componentHrefMismatch.nonEmpty =>
          val (declared, href) = page.componentHrefMismatch.get
          s"invalid admin page component in ${path}: ${page.name} component=${declared}, href component=${href}"
      }
    invalidApp.orElse(invalidPage).orElse(invalidShell).orElse(invalidAdminPage) match {
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

  private def _default_form_access(record: Record): Option[Exposure] =
    _record_value(record, "default").flatMap { default =>
      val nested = _record_value(default, "form").flatMap { form =>
        _string(form, "access").flatMap(Exposure.parse)
      }
      nested.orElse(_string(default, "form.access").flatMap(Exposure.parse))
    }

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

  private def _form(record: Record): Map[String, Form] =
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
            stayOnError = _boolean(r, "stayOnError").orElse(_boolean(r, "stay-on-error")).getOrElse(false),
            resultTemplate = _string(r, "resultTemplate").orElse(_string(r, "result-template")),
            layout = _string(r, "layout"),
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

  private def _pages(record: Record): Map[String, PageCustomization] =
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
    PageCustomization(
      title = _string(record, "title"),
      heading = _string(record, "heading"),
      subtitle = _string(record, "subtitle").orElse(_string(record, "description")),
      layout = _string(record, "layout"),
      mode = modeRaw.flatMap(PageMode.parse),
      modeRaw = modeRaw,
      display = displayraw.flatMap(PageDisplay.parse),
      displayRaw = displayraw,
      backButton = _boolean(record, "backButton").orElse(_boolean(record, "back-button")).orElse(_boolean(record, "back_button")),
      submitLabel = _string(record, "submitLabel").orElse(_string(record, "submit-label")),
      fields = _string_vector(record, "fields"),
      controls = _form_controls(record)
    )
  }

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
      val compositionRaw =
        _string(record, "composition")
          .orElse(_string(record, "webComposition"))
          .orElse(_string(record, "web-composition"))
      val pagedisplayraw =
        _string(record, "pageDisplay")
          .orElse(_string(record, "page-display"))
          .orElse(_string(record, "componentPageDisplay"))
          .orElse(_string(record, "component-page-display"))
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
        pageBackButton = _boolean(record, "pageBackButton").orElse(_boolean(record, "page-back-button")).orElse(_boolean(record, "page_back_button"))
      )
    }

  private def _routes(record: Record): Vector[Route] =
    record.getAny("routes") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record).flatMap(_route)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record).flatMap(_route)
      case _ => Vector.empty
    }

  private def _shell(record: Record): Option[Shell] =
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

  private def _component_page(record: Record): ComponentPage =
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

  private def _assets(record: Record): Assets =
    _record_value(record, "assets")
      .orElse(_record_value(record, "asset"))
      .flatMap(_any_to_record)
      .map { r =>
        Assets(
          autoComplete = _boolean(r, "autoComplete")
            .orElse(_boolean(r, "auto-complete"))
            .getOrElse(true),
          favicon = _string(r, "favicon").orElse(_string(r, "icon")),
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

  private def _admin_pages(record: Record): Vector[AdminPage] =
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
    nameHint: Option[String]
  ): Option[AdminPage] =
    value match {
      case r: Record =>
        val name = _string(r, "name")
          .orElse(nameHint.map(_.trim).filter(_.nonEmpty))
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
      case m: Map[?, ?] => _admin_page(_map_to_record(m), nameHint)
      case m: java.util.Map[?, ?] => _admin_page(_map_to_record(m.asScala.toMap), nameHint)
      case s: String =>
        val name = nameHint.getOrElse(s).trim
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

  def normalizeSelector(value: String): String =
    _normalize_selector(value)

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
      root.resolve("car.d").resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("car.d").resolve("web").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("car").resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("src").resolve("main").resolve("car").resolve("web").resolve("web.yaml"),
      root.resolve("web").resolve("web-descriptor.yaml"),
      root.resolve("web").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("web.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("form.yaml"),
      root.resolve("src").resolve("main").resolve("web-inf").resolve("admin.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("web-descriptor.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("web.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("form.yaml"),
      root.resolve("web").resolve("WEB-INF").resolve("admin.yaml")
    )

  private def _load_archive_file(path: Path): Consequence[WebDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      _descriptor_files(fs.getPath("/")).filter(Files.isRegularFile(_)) match {
        case Vector() => Consequence.resourceNotFound(s"web descriptor not found in archive: ${path}")
        case files => _load_descriptor_files(files)
      }
    }
  }
}
