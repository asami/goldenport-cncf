package domain.statemachine.workflow

object StateMachineWorkflowComponentFactoryBootstrap {
  val schemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v2"
  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector(InvocationControlledWorkflowStateMachineWorkflow1.componentFactoryMetadata)
}
