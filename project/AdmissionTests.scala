import java.net.URLClassLoader
import sbt._

/** Scala 2 build transport to the canonical Scala 3 metadata selector. */
object AdmissionTests {
  private def _quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def arguments(classpath: Seq[File], classes: File, revision: String,
    definitions: Seq[TestDefinition]): Seq[String] = {
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
      rows.groupBy(_(0)).toVector.sortBy(_._1).map { case (suite, operations) =>
        " " + suite + " -- " + operations.map(_(1)).distinct.sorted
          .map(name => "-t " + _quoted(name)).mkString(" ")
      }
    } finally loader.close()
  }
}
