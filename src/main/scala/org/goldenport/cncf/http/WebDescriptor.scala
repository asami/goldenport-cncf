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
 *  version Jun. 19, 2026
 *  version Jul.  7, 2026
 *  version Aug. 28, 2026
 * @version Sep. 28, 2026
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
  profile: Option[WebUxProfile] = None,
  profileRaw: Option[String] = None,
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
      profile =
        if (rhs.profileRaw.nonEmpty || rhs.profile.nonEmpty) rhs.profile
        else profile,
      profileRaw = rhs.profileRaw.orElse(profileRaw),
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

  def routeAppsForComponent(componentname: String): Vector[String] = {
    val normalized = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentname)
    routes
      .filter(_.target.normalizedComponent == normalized)
      .map(_.target.normalizedApp)
      .distinct
  }

  def routeAppForComponent(componentname: String): Option[String] =
    routeAppsForComponent(componentname) match {
      case Vector(app) => Some(app)
      case _ => None
    }

  def componentEntryApps: Vector[WebDescriptor.App] =
    apps.filter(_.entry)

  def routeAppForComponentPage(
    componentname: String,
    page: Vector[String]
  ): Option[String] = {
    val apps = routeAppsForComponent(componentname)
    val pagename =
      if (page.isEmpty) "index"
      else page.map(_.stripSuffix(".html")).map(WebDescriptor.normalizeSelector).mkString(".")
    val pagespecific = apps.filter { app =>
      pages.contains(s"${WebDescriptor.normalizeSelector(app)}.${pagename}")
    }
    pagespecific match {
      case Vector(app) => Some(app)
      case _ =>
        val composed = apps.filter(appComposition(_) != WebDescriptor.ComponentWebComposition.Disabled)
        composed match {
          case Vector(app) => Some(app)
          case _ => routeAppForComponent(componentname)
        }
    }
  }

  def shellComponentName: Option[String] =
    shell.flatMap(_.componentname)

  def shellAppName: Option[String] =
    shell.map(_.effectiveAppName)

  def shellLayoutName: Option[String] =
    shell.flatMap(_.layoutName)

  def staticPageMode(
    appname: String,
    page: Vector[String]
  ): WebDescriptor.PageMode =
    staticPageCustomization(appname, page)
      .flatMap(_.mode)
      .getOrElse(WebDescriptor.PageMode.Article)

  def staticPageDisplay(
    appname: String,
    page: Vector[String]
  ): WebDescriptor.PageDisplay =
    staticPageCustomization(appname, page).flatMap(_.display)
      .orElse(appFor(appname).flatMap(_.pageDisplay))
      .orElse(componentPage.display)
      .getOrElse(WebDescriptor.PageDisplay.ApplicationShell)

  def staticPageBackButton(
    appname: String,
    page: Vector[String]
  ): Boolean =
    staticPageCustomization(appname, page).flatMap(_.backButton)
      .orElse(appFor(appname).flatMap(_.pageBackButton))
      .orElse(componentPage.backButton)
      .getOrElse(true)

  def appFor(name: String): Option[WebDescriptor.App] =
    apps.find(app =>
      app.matches(name, Vector.empty) ||
        app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
    )

  def effectiveProfile: WebUxProfile =
    profile.getOrElse(WebUxProfile.default)

  def appProfile(name: String): Option[WebUxProfile] =
    appFor(name).flatMap(_.profile)

  def formProfile(
    componentname: String,
    servicename: String,
    operationname: String
  ): Option[WebUxProfile] =
    form.get(WebDescriptor.formSelector(componentname, servicename, operationname)).flatMap(_.profile)

  def operationProfile(
    componentname: String,
    servicename: String,
    operationname: String
  ): WebUxProfile =
    _operation_profile(None, componentname, servicename, operationname)

  def operationProfile(
    appname: Option[String],
    componentname: String,
    servicename: String,
    operationname: String
  ): WebUxProfile =
    _operation_profile(appname, componentname, servicename, operationname)

  private def _operation_profile(
    appname: Option[String],
    componentname: String,
    servicename: String,
    operationname: String
  ): WebUxProfile =
    formProfile(componentname, servicename, operationname)
      .orElse(appname.flatMap(appProfile))
      .orElse(appProfile(componentname))
      .orElse(profile)
      .getOrElse(WebUxProfile.default)

  def staticPageProfile(
    appname: String,
    page: Vector[String]
  ): WebUxProfile =
    staticPageCustomization(appname, page).flatMap(_.profile)
      .orElse(appProfile(appname))
      .orElse(profile)
      .getOrElse(WebUxProfile.default)

  def adminProfile: WebUxProfile =
    profile.getOrElse(WebUxProfile.Admin)

  def themeFor(appname: Option[String] = None): WebDescriptor.Theme =
    appname
      .flatMap(name => apps.find(app =>
        app.matches(name, Vector.empty) ||
          app.normalizedName == org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(name)
      ).map(_.theme))
      .map(theme.merge)
      .getOrElse(theme)

  def pageCustomization(
    componentname: Option[String],
    appname: Option[String]
  ): Option[WebDescriptor.PageCustomization] = {
    val app = appname.map(WebDescriptor.normalizeSelector)
    val component = componentname.map(WebDescriptor.normalizeSelector)
    val candidates =
      (for {
        c <- component.toVector
        a <- app.toVector
      } yield s"${c}.${a}") ++ app.toVector
    candidates.collectFirst(Function.unlift(pages.get))
  }

  def staticPageCustomization(
    appname: String,
    page: Vector[String]
  ): Option[WebDescriptor.PageCustomization] = {
    val app = WebDescriptor.normalizeSelector(appname)
    val pagename =
      if (page.isEmpty) "index"
      else page.map(_.stripSuffix(".html")).map(WebDescriptor.normalizeSelector).mkString(".")
    val candidates =
      if (page.isEmpty)
        Vector(s"${app}.${pagename}", app, pagename)
      else
        Vector(s"${app}.${pagename}", pagename, app)
    candidates.collectFirst(Function.unlift(pages.get))
  }

  def formAssets(
    componentname: String,
    servicename: String,
    operationname: String
  ): WebDescriptor.Assets =
    form.get(WebDescriptor.formSelector(componentname, servicename, operationname))
      .map(_.assets)
      .getOrElse(WebDescriptor.Assets())

  def formIndexAssets(
    componentname: String
  ): WebDescriptor.Assets =
    assets.merge(appAssets(componentname))

  def resultAssets(
    componentname: String,
    servicename: String,
    operationname: String
  ): WebDescriptor.Assets =
    assets
      .merge(appAssets(componentname))
      .merge(formAssets(componentname, servicename, operationname))

  def webRouteFor(path: Vector[String]): Option[WebDescriptor.ResolvedRoute] =
    routes.view
      .flatMap(route => route.resolve(path).map(route.normalizedPath.length -> _))
      .toVector
      .sortBy { case (length, _) => -length }
      .headOption
      .map(_._2)

  def webAppRouteFor(
    componentname: String,
    path: Vector[String]
  ): Option[WebDescriptor.ResolvedRoute] = {
    val component = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentname)
    def normalize(value: String): String =
      org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(value)
    val requestpath = path.map(normalize)
    apps.view
      .flatMap { app =>
        val completed = app.completedFor(Some(component))
        val routepath = completed.route.toVector
          .flatMap(_.split("/").toVector.filter(_.nonEmpty).map(normalize))
        Option.when(
          routepath.nonEmpty &&
            routepath.headOption.contains("web") &&
            routepath.lift(1).contains(component) &&
            requestpath.startsWith(routepath)
        ) {
          routepath.length ->
            WebDescriptor.ResolvedRoute(
              WebDescriptor.RouteTarget(component, app.normalizedName),
              WebDescriptor.RouteKind.Default,
              requestpath.drop(routepath.length)
            )
        }
      }
      .toVector
      .sortBy { case (length, _) => -length }
      .headOption
      .map(_._2)
  }

  def adminTotalCountPolicy(
    componentname: String,
    surface: String,
    collectionname: String
  ): WebDescriptor.TotalCountPolicy =
    adminSurface(componentname, surface, collectionname).map(_.totalCount).getOrElse(WebDescriptor.TotalCountPolicy.Disabled)

  def adminFields(
    componentname: String,
    surface: String,
    collectionname: String
  ): Vector[WebDescriptor.AdminField] =
    adminSurface(componentname, surface, collectionname).toVector.flatMap(_.fields)

  def adminOperationFields(
    componentname: String,
    surface: String,
    collectionname: String,
    operationname: String
  ): Vector[WebDescriptor.AdminField] =
    adminOperationSurface(componentname, surface, collectionname, operationname).toVector.flatMap(_.fields)

  def adminOperationSurface(
    componentname: String,
    surface: String,
    collectionname: String,
    operationname: String
  ): Option[WebDescriptor.AdminSurface] = {
    def normalize(value: String): String =
      value.trim.toLowerCase.replace("_", "-")
    val component = normalize(componentname)
    val s = normalize(surface)
    val collection = normalize(collectionname)
    val operation = normalize(operationname)
    Vector(
      s"${component}.${s}.${collection}.${operation}",
      s"${s}.${collection}.${operation}",
      s"${component}.${s}.${collection}.*",
      s"${s}.${collection}.*"
    ).flatMap(admin.get).headOption.orElse(adminSurface(componentname, surface, collectionname))
  }

  def adminSurface(
    componentname: String,
    surface: String,
    collectionname: String
  ): Option[WebDescriptor.AdminSurface] = {
    def normalize(value: String): String =
      value.trim.toLowerCase.replace("_", "-")
    val component = normalize(componentname)
    val s = normalize(surface)
    val collection = normalize(collectionname)
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
    componentname: String
  ): Vector[WebDescriptor.AdminPage] =
    adminPages.filter(page => page.isCanonicalComponentPage && !WebDescriptor._is_reserved_component_admin_page(page) && page.matchesComponent(componentname))

  def adminPagesForAudience(
    audience: WebDescriptor.AdminAudience
  ): Vector[WebDescriptor.AdminPage] =
    adminPages.filter(page => page.isCanonicalComponentPage && !WebDescriptor._is_reserved_component_admin_page(page) && page.audience == audience)

  def adminPage(
    componentname: String,
    pagename: String
  ): Option[WebDescriptor.AdminPage] = {
    val page = Option(pagename).filter(value => WebDescriptor._canonical_admin_segment(value).contains(value))
    page.flatMap(value => adminPagesFor(componentname).find(_.name == value))
  }
}

object WebDescriptor  extends WebDescriptorAssetPart with WebDescriptorParsingPart {
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
    successMessageKey: Option[String] = None,
    failureMessageKey: Option[String] = None,
    stayOnError: Boolean = false,
    resultTemplate: Option[String] = None,
    layout: Option[String] = None,
    profile: Option[WebUxProfile] = None,
    profileRaw: Option[String] = None,
    assets: Assets = Assets(),
    controls: Map[String, FormControl] = Map.empty
  ) {
    def mergeOverride(rhs: Form): Form =
      Form(
        enabled = rhs.enabled.orElse(enabled),
        access = rhs.access.orElse(access),
        successRedirect = rhs.successRedirect.orElse(successRedirect),
        failureRedirect = rhs.failureRedirect.orElse(failureRedirect),
        successMessageKey = rhs.successMessageKey.orElse(successMessageKey),
        failureMessageKey = rhs.failureMessageKey.orElse(failureMessageKey),
        stayOnError = stayOnError || rhs.stayOnError,
        resultTemplate = rhs.resultTemplate.orElse(resultTemplate),
        layout = rhs.layout.orElse(layout),
        profile =
          if (rhs.profileRaw.nonEmpty || rhs.profile.nonEmpty) rhs.profile
          else profile,
        profileRaw = rhs.profileRaw.orElse(profileRaw),
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
    profile: Option[WebUxProfile] = None,
    profileRaw: Option[String] = None,
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

    /*
     * Component Admin pages are declarations for one existing Web route, not
     * a general-purpose hyperlink facility. Keep the raw href out of all
     * lookup and rendering paths unless it is exactly that canonical route.
     */
    def canonicalHref: Option[String] =
      for {
        componentname <- component.flatMap(_canonical_admin_segment)
        pagename <- _canonical_admin_segment(name)
        route <- Option(href)
        if route == s"/web/${componentname}/admin/${pagename}"
      } yield route

    def isCanonicalComponentPage: Boolean =
      canonicalHref.nonEmpty

    def scopeKey: String =
      Vector(component.map(_normalize_app_segment).orElse(_href_component), Some(audience.name), Some(normalizedName)).flatten.mkString(":")

    def matchesComponent(componentname: String): Boolean = {
      val target = _canonical_admin_segment(componentname)
      target.nonEmpty && component.flatMap(_canonical_admin_segment).contains(target.get)
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
        case Vector("web", componentname, "admin", _*) => Some(_normalize_app_segment(componentname))
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
    profile: Option[WebUxProfile] = None,
    profileRaw: Option[String] = None,
    pageBackButton: Option[Boolean] = None,
    entry: Boolean = false,
    entryRaw: Option[Boolean] = None
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
        profile =
          if (rhs.profileRaw.nonEmpty || rhs.profile.nonEmpty) rhs.profile
          else profile,
        profileRaw = rhs.profileRaw.orElse(profileRaw),
        pageBackButton = rhs.pageBackButton.orElse(pageBackButton),
        entry =
          if (rhs.entry || rhs.entryRaw.contains(false)) rhs.entry
          else entry,
        entryRaw = rhs.entryRaw.orElse(entryRaw)
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

    def completedFor(componentsegment: Option[String]): App = {
      val c = completed
      componentsegment.map(_.trim).filter(_.nonEmpty) match {
        case Some(component) =>
          c.copy(route = c.route.map(_.replace("{component}", component)))
        case None =>
          c
      }
    }

    def matches(requestname: String, requestpath: Vector[String]): Boolean = {
      val normalizedrequestname = _normalize_app_segment(requestname)
      val normalizedrequestpath = requestpath.map(_normalize_app_segment)
      val apppath = effectivePath.split("/").toVector.filter(_.nonEmpty).map(_normalize_app_segment)
      normalizedName == normalizedrequestname ||
        apppath == ("web" +: normalizedrequestname +: normalizedrequestpath) ||
        apppath == ("web" +: normalizedrequestname +: Vector(_normalize_app_segment(effectiveKind)))
    }
  }

  final case class Shell(
    component: Option[String] = None,
    app: Option[String] = None,
    layout: Option[String] = None
  ) {
    def componentname: Option[String] =
      component.map(_.trim).filter(_.nonEmpty).map(_normalize_app_segment)

    def appname: Option[String] =
      app.map(_.trim).filter(_.nonEmpty).map(_normalize_app_segment)

    def effectiveAppName: String =
      appname.orElse(componentname).getOrElse("default")

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

    def resolve(requestpath: Vector[String]): Option[ResolvedRoute] = {
      val routepath = normalizedPath
      val normalizedrequest = requestpath.map(_normalize_app_segment)
      Option.when(
        routepath.nonEmpty &&
          normalizedrequest.startsWith(routepath) &&
          routepath.headOption.contains("web")
      ) {
        ResolvedRoute(
          target,
          kind,
          normalizedrequest.drop(routepath.length)
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
    componentname: String,
    servicename: String,
    operationname: String
  ): String =
    Vector(componentname, servicename, operationname)
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
      profile = _profile(web),
      profileRaw = _profile_raw(web),
      theme = _theme(web),
      assets = _assets(web),
      admin = _admin(web),
      adminPages = _admin_pages(web)
    )
  }

  private[http] val _reserved_component_admin_page_names = Set("descriptor", "entities", "data", "aggregates", "views")

  def normalizeSelector(value: String): String =
    _normalize_selector(value)

}
