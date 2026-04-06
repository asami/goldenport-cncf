# Operation Access Policy Names

Date: 2026-04-06

## Purpose

This note records the current canonical policy names for operation-level access
metadata in CNCF. The immediate goal is to keep CML declaration, generated
metadata, and runtime authorization aligned while the access model is still
evolving.

## Canonical Policy Names

- `owner_or_manager`
- `manager_only`

These are the canonical public names to be used in generated metadata and CML.

## Accepted Aliases

The runtime currently accepts kebab-case aliases in addition to canonical
snake_case names:

- `owner-or-manager`
- `manager-only`

The canonical form remains snake_case.

## Intended Meaning

### `owner_or_manager`

The operation is allowed for:

- the resource owner
- a management-level actor

This policy is suitable for self-service operations over `SimpleEntity`
resources such as:

- `changePassword`
- `getMyAccount`

### `manager_only`

The operation is allowed only for a management-level actor.

This policy is suitable for governance-oriented service operations such as:

- `createUserAccount`
- `updateUserStatus`
- `deleteUserAccount`
- `listUserAccounts`

## Current Modeling Rule

The current preferred declaration route is:

1. declare target resource via `ENTITY`
2. declare default service-level access policy via `ACCESS` when a service has a
   stable access profile
3. use operation-level `ACCESS` only when an operation needs to override the
   service default

## Current Scope

These names are currently sufficient for the implemented user-account use case.
The policy catalog should remain intentionally small until more operational
experience is accumulated.
