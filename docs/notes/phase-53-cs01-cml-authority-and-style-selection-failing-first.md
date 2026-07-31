# Phase 53 CS-01 - CML Authority and ComponentStyle Failing-First Contract

Status: CS-01A in progress; no normative design or specification is changed.

## Authority inventory

The current typed CML owner of an explicit `COMPONENT` declaration is
Kaleidox's `ComponentSubsystemModel.ComponentDefinition`.  Cozy consumes that
model through `Modeler`; its `CmlModelMetadata` projection is a consumer
surface and must not become a parallel ComponentStyle parser.

`simplemodeling` and `simplemodeling-lib` do not own this semantic and are not
admitted by CS-01A.  Any production addition of the typed style field requires
a recorded Phase 53 scope reset that admits the Kaleidox repository.

## Failing-first evidence

Two component-only CML fixtures declare the same `artscene` component and
select distinct identifiers only through `STYLE`:
`full-fledged-with-standalone` and `domain-only`.  `KaleidoxCmlParsingSpec`
first proves that both fixtures parse and expose their components, then records
a `pendingUntilFixed` expectation that each selected value remains observable
in the typed component model.  The two values must retain their distinct CML
selections, which rejects one default or one hardcoded selection.  The exact
ComponentStyle identity API remains an explicit CS-01 freeze decision rather
than an assumption in this first preservation contract.

The pending state is intentional.  It is not a grammar, fixture, catalog,
descriptor, capability, operating-mode, user-context, or datastore result.
It records only the current loss between the explicit CML selection and the
typed Kaleidox component model.

## Boundary

This CS-01A slice neither implements a CNCF catalog nor changes CML grammar,
descriptor projection, `cozyPrepareRuntime`, capability resolution,
ExecutionContext, launchers, ArtScene, or normative documentation.  The next
production slice must admit Kaleidox before changing the typed authority.
