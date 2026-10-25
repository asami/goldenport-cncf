package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.event.{EventPolicyEngine, EventStorePersistenceDeclaration, ManagedEventHistory}

/** Captured admission; Subsystem and SystemNode retain all resource ownership.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
private[cncf] final class EventStoreHistoryBinding(
  declaration: Option[EventStorePersistenceDeclaration],
  activeLease: () => Option[SystemNodeResourceLease],
  inheritLease: SystemNodeResourceLease => Consequence[Unit],
  resolveStore: ComponentDataStore.Request => Consequence[DataStore]
) {
  private val _declaration = declaration
  private val _active_lease = activeLease
  private val _inherit_lease = inheritLease
  private val _resolve_store = resolveStore

  def bindC(context: ExecutionContext): Consequence[ManagedEventHistory] =
    if (context == null) Consequence.stateInvalid("Event history requires an execution context")
    else {
      given ExecutionContext = context
      for {
        _ <- EventPolicyEngine.default.authorizeIntrospection
        declaration <- _declaration.map(Consequence.success).getOrElse(
          Consequence.configurationInvalid("Subsystem has no EventStore persistence declaration"))
        lease <- _active_lease().map(Consequence.success).getOrElse(
          Consequence.stateInvalid("Event history requires an active managed dispatch lease"))
        _ <- _inherit_lease(lease)
        store <- _resolve_store(declaration.serviceRequest)
        _ <- store match {
          case sql: SqlDataStore if sql.managedResourceOption.nonEmpty => Consequence.unit
          case _ => Consequence.configurationInvalid("Event history requires a managed SQL resource")
        }
      } yield {
        val thread = Thread.currentThread()
        new ManagedEventHistory(context, () =>
          if (Thread.currentThread() != thread || !_active_lease().contains(lease))
            Consequence.stateInvalid("Event history capability escaped its issuing dispatch lease")
          else _inherit_lease(lease), query => store match {
            case provider: ManagedEventHistory.Provider =>
              provider.readCommittedEventHistoryC(declaration.eventCollection, query)
            case _ => Consequence.configurationInvalid(
              "Managed event history requires a non-mutating bounded SQL provider")
          })
      }
    }
}
