CNCF CML → Wireframe Generation Specification (Draft)
=====================================================

status=draft
category=web / wireframe / generation

Overview
--------

This specification defines how a Wireframe DSL is generated from CML.

The goal is to:

- automatically produce usable UI structures
- minimize manual UI design effort
- support iterative refinement with human input


1. Input Model
--------------

Input:

- CML operations
- input types (Value Objects)
- field definitions
- validation constraints
- optional descriptions


2. Output Model
---------------

Output:

- Wireframe DSL pages
- form bindings
- field groups
- navigation flow


3. Basic Mapping Rule
---------------------

For each operation:

- generate one page

Example:

operation createUser → page CreateUser


4. Page Naming
--------------

Rule:

- PascalCase(operation name)

Examples:

- createUser → CreateUser
- updateProfile → UpdateProfile


5. Form Binding
----------------

Each page binds to its operation:

form <component.service.operation>


6. Field Extraction
--------------------

Fields are derived from:

- input value object
- flattened paths

Example:

UserInput:
  username
  email

↓

fields:
  - username
  - email


7. Grouping Rules
------------------

Fields are grouped heuristically.

### 7.1 Basic Rule

- required fields → Basic group
- optional fields → Optional group

Example:

group Basic:
  - username
  - email

group Optional:
  - memo


### 7.2 Nested Structures

Nested objects become sub-groups:

address.city → group Address

---

8. Label Generation
--------------------

Priority:

1. explicit label (if present in CML)
2. field name split (username → "User Name")

---

9. Action Generation
---------------------

Default:

actions:
  - submit

Optional:

- cancel (if navigation exists)


10. Navigation Generation
-------------------------

### 10.1 Default Success Flow

onSuccess:
  -> <ListPage> or <ParentPage>

If no context:

- generate Result page

---

### 10.2 Default Error Flow

onError:
  -> same page (inline)

---

11. Multi-Page Generation
--------------------------

For CRUD-like operations:

- create → CreatePage
- list → ListPage
- update → UpdatePage

Flow:

List → Create → List

---

12. Static Form App Mapping
----------------------------

Generated pages map to:

CreateUser.html
CreateUser__success.html
CreateUser__error.html

---

13. Override Model
-------------------

Generated wireframe is marked:

generated: true

User modifications:

- preserved
- override generated parts

---

14. Regeneration Strategy
-------------------------

Rules:

- do not overwrite user edits
- merge by section
- preserve manual groups

---

15. AI Enhancement Layer
------------------------

AI may enhance:

- grouping
- labels
- layout
- flow suggestions

---

16. Minimal Example
-------------------

Input:

operation createUser

Output:

page CreateUser

  form userAccount.user.create

  group Basic:
    - username
    - email

  actions:
    - submit

  onSuccess:
    -> UserList


17. Future Extensions
---------------------

- role-based UI filtering
- dynamic forms
- conditional fields
- i18n support


Conclusion
----------

This generation model enables:

- instant UI scaffolding
- consistent UX baseline
- human-in-the-loop refinement
