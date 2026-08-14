package org.goldenport.cncf.http

import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object WebApplicationEntryPolicy {
  sealed trait Resolution

  case object NoApplication extends Resolution

  final case class Selected(
    app: WebDescriptor.App,
    component: String,
    publishedPath: String
  ) extends Resolution

  final case class Error(message: String) extends Resolution

  def resolve(
    webDescriptor: WebDescriptor,
    subsystemDescriptor: Option[GenericSubsystemDescriptor],
    fallbackComponentName: Option[String] = None
  ): Resolution =
    _select_app(webDescriptor) match {
      case Left(message) => Error(message)
      case Right(None) => NoApplication
      case Right(Some(app)) =>
        _validated_default_route(webDescriptor, app) match {
          case Left(message) => Error(message)
          case Right(Some(route)) =>
            Selected(app, NamingConventions.toNormalizedSegment(route.target.component), route.normalizedPathText)
          case Right(None) =>
            _implicit_component(subsystemDescriptor).orElse(fallbackComponentName.map(NamingConventions.toNormalizedSegment)) match {
              case Some(component) => Selected(app, component, "/web")
              case None => Error(s"Web application '${app.normalizedName}' has no component owner")
            }
        }
    }

  private def _select_app(
    webdescriptor: WebDescriptor
  ): Either[String, Option[WebDescriptor.App]] =
    webdescriptor.apps match {
      case Vector() => Right(None)
      case Vector(app) => Right(Some(app))
      case _ =>
        webdescriptor.componentEntryApps match {
          case Vector(app) => Right(Some(app))
          case Vector() => Left("No Web entry app configured for multiple Web applications")
          case apps => Left(s"Multiple Web entry apps configured: ${apps.map(_.normalizedName).mkString(", ")}")
        }
    }

  private def _validated_default_route(
    webdescriptor: WebDescriptor,
    app: WebDescriptor.App
  ): Either[String, Option[WebDescriptor.Route]] = {
    val routes = webdescriptor.routes.filter(_.kind == WebDescriptor.RouteKind.Default)
    routes match {
      case Vector() => Right(None)
      case Vector(route) =>
        _validate_default_route(route).flatMap { _ =>
          if (route.target.normalizedApp == app.normalizedName)
            Right(Some(route))
          else
            Left(s"Default Web route targets '${route.target.normalizedApp}', not selected application '${app.normalizedName}'")
        }
      case values =>
        Left(s"Multiple default Web routes configured: ${values.map(_.normalizedPathText).mkString(", ")}")
    }
  }

  private def _validate_default_route(route: WebDescriptor.Route): Either[String, Unit] = {
    val rawpath = Option(route.path).getOrElse("")
    val component = Option(route.target.component).map(_.trim).getOrElse("")
    val normalizedcomponent = route.target.normalizedComponent
    val segments = rawpath.split("/", -1).toVector.drop(1)
    if (component.isEmpty || normalizedcomponent.isEmpty)
      Left(s"Default Web route has an invalid component owner: '${route.target.component}'")
    else if (
      rawpath != route.normalizedPathText ||
        !rawpath.startsWith("/") ||
        rawpath.contains("//") ||
        rawpath.contains("?") ||
        rawpath.contains("#") ||
        rawpath.contains("@") ||
        segments.exists(segment => segment.isEmpty || segment == "." || segment == "..") ||
        !route.normalizedPath.headOption.contains("web")
    )
      Left(s"Default Web route path is not a canonical /web path: '$rawpath'")
    else if (route.normalizedPath.take(2) == Vector("web", "system"))
      Left(s"Default Web route path is reserved for the Dashboard: '$rawpath'")
    else
      Right(())
  }

  private def _implicit_component(
    subsystemdescriptor: Option[GenericSubsystemDescriptor]
  ): Option[String] =
    subsystemdescriptor
      .flatMap(descriptor => descriptor.implicitRootComponentName.orElse(descriptor.componentBindings.headOption.map(_.runtimeComponentName)))
      .map(NamingConventions.toNormalizedSegment)
}
