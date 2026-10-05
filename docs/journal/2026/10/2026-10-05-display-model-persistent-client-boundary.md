# Display Model and Persistent Client Presentation Boundary

Date: 2026-10-05
Status: design clarification
Related: Phase 96 Display Model Protocol
Driver: Textus Control Center Flutter dashboard

A presentation client may change visual behavior according to local runtime conditions such as usable display size, orientation, charging state and inactivity. A persistent dashboard may also enter an Ambient presentation that reduces visible information and applies client-side burn-in mitigation.

These conditions do not belong to the CNCF Display Model semantic contract.

CNCF remains responsible for target-neutral Display Projection/Display Model semantics, including enough semantic role/priority information for a client to form Detail, Compact, Glance or other constrained realizations. The client/framework owns the mapping from those semantics plus local device environment into a concrete presentation mode.

Therefore Phase 96 must not introduce Flutter-specific, charging-state, wake-lock, inactivity-timer or burn-in fields into the Display Model protocol. A client may select a smaller subset of display information for Ambient/Glance realization without changing the authoritative View or domain state.

This preserves the existing boundary:

Semantic View -> Display Projection -> Display Model Protocol -> presentation client -> local presentation policy/realization.
