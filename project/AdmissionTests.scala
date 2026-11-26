import java.net.URLClassLoader
import sbt._

/** Scala 2 build transport to the canonical Scala 3 metadata selector. */
object AdmissionTests {
  private def _quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  final case class Selection(arguments: Seq[String], marker: String)

  def arguments(classpath: Seq[File], classes: File, revision: String,
    definitions: Seq[TestDefinition]): Selection = {
    val loader = new URLClassLoader(classpath.map(_.toURI.toURL).toArray,
      ClassLoader.getPlatformClassLoader)
    try {
      val bridge = loader.loadClass("org.goldenport.cncf.validation.RepositoryAdmissionSelection$")
      // exportJars may place test classes in a JAR rather than classDirectory.
      val artifact = new File(bridge.getProtectionDomain.getCodeSource.getLocation.toURI)
      val rows = try bridge.getMethod("select", classOf[String], classOf[String], classOf[Array[String]])
        .invoke(bridge.getField("MODULE$").get(null), artifact.getCanonicalPath, revision,
          definitions.map(_.name).toArray).asInstanceOf[Array[Array[String]]].toVector
      catch { case error: java.lang.reflect.InvocationTargetException => throw error.getCause }
      def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      val marker = "CNCF-ADMISSION-V1 selected " + rows.map(row => token(row(0)) + ":" + token(row(1))).mkString(",")
      val arguments = rows.groupBy(_(0)).toVector.sortBy(_._1).map { case (suite, operations) =>
        " " + suite + " -- -C org.goldenport.cncf.validation.AdmissionSuccessReporter " + operations.map(_(1)).distinct.sorted
          .map(name => "-t " + _quoted(name)).mkString(" ")
      }
      Selection(arguments, marker)
    } finally loader.close()
  }
}
