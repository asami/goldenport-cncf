package org.goldenport.cncf.workflow

import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
trait WorkflowEngineTestFixture {
  protected def withWorkflowSubsystem[A](
    name: String
  )(body: Subsystem => A): A =
    TestComponentFactory.withEmptySubsystem(name)(body)
}
