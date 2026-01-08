package org.goldenport.cncf.http

import org.goldenport.cncf.subsystem.{HelloWorldSubsystemFactory, Subsystem}

/*
 * @since   Jan.  8, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object HelloWorldHttpEngineFactory {
  def helloWorldSubsystem(): Subsystem =
    HelloWorldSubsystemFactory.helloWorld()

  def helloWorldEngine(): HttpExecutionEngine = {
    val subsystem = helloWorldSubsystem()
    val catalog = new HelloWorldOperationCatalog(subsystem)
    val actionEngine =
      subsystem.components.get("admin").map(_.actionEngine)
        .orElse(subsystem.components.headOption.map(_._2.actionEngine))
        .getOrElse {
          throw new IllegalStateException("No component found for action engine")
        }
    DefaultHttpExecutionEngine(catalog, actionEngine)
  }
}
