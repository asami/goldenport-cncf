CNCF Wireframe DSL Specification (Draft v0)
===========================================

status=draft
category=web / wireframe / dsl

Overview
--------

This DSL defines page structure and user interaction for CNCF applications.

It is independent from CML and focuses on:

- page structure
- form binding
- field layout
- navigation flow

It is designed to be:

- human-readable
- AI-generatable
- incrementally refinable


1. Basic Structure
------------------

A wireframe consists of pages.

Example:

page CreateUser
  form userAccount.user.create

---

2. Page Definition
------------------

page <PageName>

Example:

page CreateUser

Page names are identifiers and used for navigation.


3. Form Binding
----------------

Bind page to CNCF operation:

form <component.service.operation>

Example:

form userAccount.user.create


4. Field Definition
-------------------

fields:
  - <fieldName>
  - <fieldName>

Example:

fields:
  - username
  - email

---

4.1 Grouping

group <GroupName>:
  - <fieldName>
  - <fieldName>

Example:

group BasicInfo:
  - username
  - email

group Optional:
  - memo


5. Actions
-----------

actions:
  - submit
  - cancel
  - custom <ActionName>

Example:

actions:
  - submit
  - cancel


6. Navigation Flow
------------------

Define transitions:

onSuccess:
  -> <PageName>

onError:
  -> <PageName>

Example:

onSuccess:
  -> UserList

onError:
  -> CreateUserError


7. Inline Error Handling (Optional)
-----------------------------------

error:
  mode: inline | redirect

Example:

error:
  mode: inline

---

8. Layout Hints (Optional)
--------------------------

layout:
  columns: 1 | 2
  style: simple | compact

Example:

layout:
  columns: 2


9. Example
-----------

page CreateUser

  form userAccount.user.create

  group BasicInfo:
    - username
    - email

  group Optional:
    - memo

  actions:
    - submit
    - cancel

  onSuccess:
    -> UserList

  onError:
    -> CreateUserError


10. Multi-Page Flow Example
---------------------------

page UserList

  actions:
    - custom create

  onAction create:
    -> CreateUser


page CreateUser

  form userAccount.user.create

  fields:
    - username
    - email

  onSuccess:
    -> UserList


11. Mapping to Static Form App
------------------------------

Wireframe maps to static files:

CreateUser.html
CreateUser__success.html
CreateUser__error.html

Navigation:

onSuccess -> redirect to target page


12. Mapping to SPA (Future)
---------------------------

Wireframe may also map to:

- React components
- Vue components

Same DSL can generate different UI targets.


13. AI Generation Loop
-----------------------

CML → cozy → Wireframe (initial)
Human → edit Wireframe
Wireframe → UI generation

This creates a human-in-the-loop design process.


14. Non-Goals
--------------

This DSL does NOT:

- define styling details
- replace frontend frameworks
- define data model (handled by CML)


Conclusion
----------

This DSL provides a lightweight way to define UI structure
while keeping CNCF architecture clean and modular.

It enables:

- rapid UI generation
- static form applications
- AI-assisted UX design
