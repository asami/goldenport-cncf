package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorageDeclaration}
import org.goldenport.cncf.event.{EventCursorRecordV1, EventPersistencePolicy, EventStorePersistenceDeclaration}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.ResolvedConfiguration

/** Trusted host construction, not a Workflow Operation or sender override.
  * The returned host owns one fixed EventStore policy and storage declaration.
  * Neither raw datastore views nor claim tokens escape this boundary.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class SubsystemEventStoreBootstrap private (
  val subsystem: Subsystem,
  ownsNode: Boolean
) {
  /** Explicit first-install operation. It never resets an existing cursor;
    * restart opens the existing host without invoking initialization again.
    */
  def initializeCursorC()(using context: ExecutionContext): Consequence[EventCursorRecordV1] =
    subsystem.initializeEventCursorC(ComponentDataStore.Environment(
      context.runtime.resolvedParameters, Some(subsystem.configuration)))

  /** Logical cleanup always runs; only a node created by this host is closed.
    * A node supplied by the enclosing runtime retains its original owner.
    */
  def shutdownC(): Consequence[Unit] =
    if (ownsNode) Consequence(Subsystem.shutdownOwned(subsystem))
    else subsystem.shutdownC().map(_ => ())
}

object SubsystemEventStoreBootstrap {
  /** Explicit host-owned record authority; preserves the original createC ABI.
    * Capture is pure and never performs first installation or actor admission.
    */
  def createC(
    policy: EventPersistencePolicy, declaration: EventStorePersistenceDeclaration,
    scope: ScopeContext, configuration: ResolvedConfiguration,
    mode: Option[RunMode], aliases: AliasResolver,
    recordStorage: ComponentRecordStorageDeclaration
  ): Consequence[SubsystemEventStoreBootstrap] =
    _createC(policy, declaration, scope, configuration, mode, aliases, Some(recordStorage))

  /** Reuses canonical descriptor/component selection and node ownership.
    * Configuration is host-owned; semantic workers never call this factory.
    * Creating a declaration alone still grants no runtime storage access.
    */
  def createC(
    policy: EventPersistencePolicy,
    declaration: EventStorePersistenceDeclaration,
    scope: ScopeContext,
    configuration: ResolvedConfiguration,
    mode: Option[RunMode] = None,
    aliases: AliasResolver = AliasResolver.empty
  ): Consequence[SubsystemEventStoreBootstrap] =
    _createC(policy, declaration, scope, configuration, mode, aliases, None)

  private def _createC(
    policy: EventPersistencePolicy, declaration: EventStorePersistenceDeclaration,
    scope: ScopeContext, configuration: ResolvedConfiguration,
    mode: Option[RunMode], aliases: AliasResolver,
    records: Option[ComponentRecordStorageDeclaration]
  ): Consequence[SubsystemEventStoreBootstrap] =
    if (policy == null || declaration == null || scope == null || configuration == null ||
        mode == null || aliases == null)
      Consequence.configurationInvalid("EventStore host bootstrap requires policy, declaration and canonical host inputs")
    else SubsystemStoragePlan.createC(policy, declaration, records).flatMap { plan =>
      Consequence {
        val ownsNode = SystemNodeConstruction.current.isEmpty
        plan.withConstruction {
          new SubsystemEventStoreBootstrap(DefaultSubsystemFactory.defaultWithScope(
            scope, mode, configuration, aliases), ownsNode)
        }
      }
    }
}
