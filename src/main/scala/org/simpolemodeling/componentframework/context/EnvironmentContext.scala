package org.simplemodeling.componentframework.context

import java.nio.file.Path
import java.net.InetAddress

sealed trait EnvironmentContext

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
object EnvironmentContext {
  final case class Local(
    cwd: Path,
    osUser: String,
    host: String,
    os: String
  ) extends EnvironmentContext

  object Local {
    def detect(): Local =
      Local(
        cwd = Path.of(System.getProperty("user.dir")),
        osUser = System.getProperty("user.name"),
        host = InetAddress.getLocalHost.getHostName,
        os = System.getProperty("os.name")
      )
  }
}
