package org.goldenport.cncf.http

import com.comcast.ip4s.Host
import org.goldenport.Consequence
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Aug. 10, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object ServerEndpointPolicy {
  final case class Endpoint(host: Host, port: Int)

  val HOST_PROPERTY_KEY = "textus.server.host"
  val DEFAULT_HOST: Host = Host.fromString("127.0.0.1").get

  def resolve(
    subsystem: Subsystem,
    availabilityForHost: Host => ServerPortPolicy.Availability = ServerPortPolicy.Availability.forHost,
    assignmentStore: Option[ServerPortPolicy.AssignmentStore] = None
  ): Consequence[Endpoint] =
    _host(subsystem).flatMap { host =>
      ServerPortPolicy
        .resolve(subsystem, availabilityForHost(host), assignmentStore)
        .map(Endpoint(host, _))
    }

  private def _host(subsystem: Subsystem): Consequence[Host] = {
    val value = ConfigurationAccess
      .getString(subsystem.configuration, HOST_PROPERTY_KEY)
      .orElse(sys.props.get(HOST_PROPERTY_KEY).map(_.trim).filter(_.nonEmpty))
      .getOrElse(DEFAULT_HOST.toString)
    Host.fromString(value) match {
      case Some(host) => Consequence.success(host)
      case None => Consequence.argumentInvalid(s"invalid server host for $HOST_PROPERTY_KEY: $value")
    }
  }
}
