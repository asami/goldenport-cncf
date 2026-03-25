# CNCF HelpProjection Generated Component Handoff

Date: 2026-03-25
Owner: cross-repo cozy/simple-modeler -> cncf
Status: ready for CNCF-side end-to-end verification

## Purpose

Record the current cross-repo closure point for help metadata propagation from
CML/cozy generation into CNCF `meta.help`, and define the next CNCF-side task:
verify `HelpProjection.projectModel` against a generated component, not only
against handwritten or builtin components.

## What Is Already Closed

The generator side now emits CNCF help source metadata into generated Scala
code.

Confirmed path:

1. CML/DOX `SERVICE` and `OPERATION` descriptions are parsed by `kaleidox`
2. `cozy` modeler passes those descriptions into `MService` and `MOperation`
3. `simple-modeler` generator emits:
   - `ServiceDefinition.Specification.Builder(...).summary(...).description(...)`
   - `OperationDefinition.Specification.Builder(...).copy(content = BaseContent.Builder(...).summary(...).description(...))`
4. These are the source fields CNCF `HelpProjection` is expected to read

Cross-repo files updated on the producer side:

- `/Users/asami/src/dev2025/simple-modeler/src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/ComponentPart.scala`
- `/Users/asami/src/dev2025/cozy/src/test/resources/modeler/service-help-metadata.dox`
- `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`

Producer-side verification already green:

```sh
cd /Users/asami/src/dev2025/simple-modeler
sbt --no-server --batch publishLocal

cd /Users/asami/src/dev2025/cozy
sbt --no-server --batch "testOnly cozy.modeler.ModelerGenerationSpec"
```

The `cozy` regression now verifies that generated `DomainComponent.scala`
contains CNCF help source metadata for a described service and operation.

## CNCF Task

Add an end-to-end CNCF test that loads or instantiates a generated component
and verifies `HelpProjection.projectModel` output.

This is the missing final check.

The assertion target is not generator text anymore. The assertion target is
the CNCF `HelpModel` produced from a generated component.

## Required CNCF Assertions

At minimum, verify:

1. Component-level help resolves successfully
2. Service-level help uses generated `summary` and `description`
3. Operation-level help uses generated `summary`
4. Operation-level help includes generated `description` in `details`

Expected semantics:

- service summary:
  `Address service for postal address support.`
- service description:
  `Address service for postal address support.Provides help-visible metadata for CNCF projections.`
- operation summary:
  `Look up an address by postal code.`
- operation description:
  `Look up an address by postal code.Returns a normalized address representation.`

The current producer side does not preserve paragraph/newline boundaries in the
generated description string. CNCF should not block on newline formatting in
this phase. The semantic content is the required assertion.

## Recommended Test Shape

Preferred shape:

1. Generate a component from the `cozy` fixture
2. Compile/load the generated component
3. Call `HelpProjection.projectModel(component, Some(selector))`
4. Assert on returned `HelpModel`

Selectors to cover:

- `<component>`
- `<component>.<service>`
- `<component>.<service>.<operation>`

For the current fixture, the main useful selectors are:

- `domain`
- `domain.address`
- `domain.address.lookupAddress`

## Candidate Locations

Likely CNCF-side test locations:

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection`
- or a new generated-component integration spec under:
  `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component`

Existing reference implementation:

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/model/HelpModel.scala`

## Suggested Execution Plan

1. Reuse the generated fixture produced by cozy if practical
2. If dynamic loading is awkward, add a minimal checked-in generated component
   fixture only for projection testing
3. Add one spec that covers service and operation selectors
4. Keep assertions on `HelpModel`, not rendered YAML/JSON strings

## Non-Goals For This Step

- Do not redesign `HelpProjection`
- Do not change selector semantics
- Do not require newline-preserving description rendering yet
- Do not add a separate cozy-side help abstraction as the source of truth

The canonical consumer remains CNCF `HelpProjection`.

## Acceptance Condition

This handoff is complete when CNCF has a green regression showing that a
generated component from `cozy` yields the expected `HelpModel` through
`HelpProjection.projectModel`.

## CNCF Result

The CNCF-side regression is now in place and green.

Implemented files:

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection/GeneratedHelpProjectionFixture.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection/GeneratedHelpProjectionSpec.scala`

Verified behavior:

1. `domain` resolves as a component target
2. `domain.address` resolves as a service target
3. `domain.address.lookupAddress` resolves as an operation target
4. Service summary comes from generated `summary`
5. Service description comes from generated `description`
6. Operation summary comes from generated `summary`
7. Operation description is exposed in `details`

Verification command:

```sh
cd /Users/asami/src/dev2025/cloud-native-component-framework
sbt --no-server --batch "testOnly org.goldenport.cncf.projection.GeneratedHelpProjectionSpec"
```

Result: passed.
