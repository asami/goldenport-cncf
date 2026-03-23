CNCF Naming Convention Specification (Component / Service / Operation)
=====================================================================

status=proposed
scope=runtime + DSL alignment

# 1. Purpose

Define a consistent naming convention for:

- Component
- Service
- Operation

across:

- DSL (definition layer / CML)
- Runtime (CLI / HTTP / OpenAPI)

This specification enforces:

- semantic clarity in design
- operational simplicity in execution
- deterministic resolution from flexible input

---

# 2. Core Principle

CNCF MUST treat naming as a dual-representation system:

- Canonical Name = defined by CML (single source of truth)
- Input Name     = flexible user input (multiple formats accepted)

Canonical Name is authoritative.
All runtime resolution MUST map to Canonical Name.

---

# 3. Canonical Naming Rules (CML / DSL)

## 3.1 Component

- Format: UpperCamelCase
- Example:
  - UserAccount
  - OrderManagement

## 3.2 Service

- Format: UpperCamelCase
- Represents:
  - Role (User, Admin)
  - Context (Auth, Profile)

Examples:
- User
- Admin
- Credential

## 3.3 Operation

- Format: lowerCamelCase
- Represents action / behavior

Examples:
- register
- disableUser
- resetPassword

## 3.4 Canonical Selector Format

<component>.<service>.<operation>

Example:

UserAccount.User.register
UserAccount.Admin.disableUser

---

# 4. Runtime Input Policy

CNCF MUST accept multiple input formats:

- CamelCase
- kebab-case
- snake_case

Examples:

UserAccount.User.updateProfile
user-account.user.update-profile
user_account.user.update_profile

---

# 5. Resolution Strategy

## 5.1 Normalization (Input → Comparison Key)

Each identifier MUST be normalized as follows:

- Convert to lowercase
- Remove separators:
  - '-'
  - '_'

Examples:

UserAccount → useraccount
user-account → useraccount
user_account → useraccount

updateProfile → updateprofile
update-profile → updateprofile
update_profile → updateprofile

---

## 5.2 Selector Normalization

Input:

UserAccount.User.updateProfile

Normalized key:

useraccount.user.updateprofile

---

## 5.3 Canonical Resolution

Normalized key MUST be resolved against registered Canonical Names.

Example:

useraccount.user.updateprofile
→ UserAccount.User.updateProfile

---

## 5.4 Matching Rule

- Matching MUST be exact (after normalization)
- Prefix or fuzzy matching MUST NOT be allowed

Invalid:

user → UserAccount   (NOT allowed)

---

## 5.5 Ambiguity Handling

If multiple candidates match:

- MUST raise an error
- MUST NOT guess

---

# 6. Canonical Index (Implementation Requirement)

CNCF MUST build an index for canonical selectors.

## 6.1 Index Structure

- Key: normalized selector key
- Value: canonical selector

Example:

useraccount.user.updateprofile
→ UserAccount.User.updateProfile

## 6.2 Build Timing

This index MUST be built at one of the following times:

- component load time
- system initialization time

## 6.3 Lookup Requirement

Lookup MUST be O(1) or equivalent.

## 6.4 Uniqueness Requirement

If two Canonical Names produce the same normalized selector key:

- the system MUST treat this as a configuration error
- the conflicting selectors MUST be reported
- the system MUST NOT silently choose one

Example:

UserAccount.User.updateProfile
User_Account.User.updateProfile

If both normalize to the same selector key, registration MUST fail.

---

# 7. Internal Representation

CNCF MUST internally use Canonical Name only.

Example:

Selector = UserAccount.User.updateProfile

Normalized form MUST NOT be used as internal identity.

---

# 8. CLI Specification

## 8.1 Command Format

cncf command <selector>

Examples (all valid):

cncf command UserAccount.User.register
cncf command user-account.user.register
cncf command user_account.user.register

## 8.2 Behavior

- Input MUST be normalized and resolved to Canonical Name
- Execution MUST use Canonical Name internally

---

# 9. HTTP Mapping

## 9.1 Path Mapping

<component>/<service>/<operation>

Examples:

POST /user-account/user/register
POST /user-account/admin/disable-user

## 9.2 Rules

- HTTP paths MUST use kebab-case
- HTTP layer MAY internally convert to Canonical Name

---

# 10. OpenAPI Mapping

## 10.1 operationId

Generated from Canonical Name:

UserAccount.User.register → userAccountUserRegister

## 10.2 Rules

- MUST be lowerCamelCase
- MUST be deterministic

---

# 11. Output Policy

## 11.1 Default

System SHOULD display Canonical Name:

UserAccount.User.updateProfile

## 11.2 Error Output

Errors SHOULD include:

- input selector
- resolved canonical (if available)

Example:

Operation not found:
  input: user-account.user.update-profile

Did you mean:
  UserAccount.User.updateProfile

---

# 12. Authorization Compatibility

Authorization MAY use Canonical or normalized forms:

UserAccount.User.*
user-account.user.*

However:

- Internally MUST be resolved to Canonical

---

# 13. Service Design Rules

## 13.1 Service MUST represent:

- Role (User, Admin)
OR
- Functional context (Auth, Profile)

## 13.2 MUST NOT:

- Mix roles in a single Service
- Encode role in Operation name

Invalid:

UserAccount.User.adminDisableUser

---

# 14. Reserved Services

The following services are reserved:

- meta
- system

Examples:

UserAccount.meta.help
UserAccount.system.health

---

# 15. Summary

- Canonical Name (CML) is the single source of truth
- Runtime input is flexible (Camel / kebab / snake)
- Resolution is strict (normalized exact match)
- Internal representation is Canonical only
- Canonical lookup is index-based
- Output is Canonical-centric

---

# 16. Example (Full)

## Definition

UserAccount.User.register
UserAccount.Admin.disableUser

## Accepted Inputs

UserAccount.User.register
user-account.user.register
user_account.user.register

## Internal

UserAccount.User.register

## HTTP

POST /user-account/user/register

---

END
