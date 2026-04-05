CNCF Static Form Application Model (Journal)
============================================

status=journal
published_at=2026-04-06
category=web / application / static

Overview
--------

This note introduces the Static Form Application model in CNCF.

The goal is to enable simple web applications using only:

- static HTML files
- form submission
- CNCF operation APIs

without requiring:

- frontend frameworks
- custom backend code
- server-side rendering

This provides a lightweight application model
for internal tools, admin interfaces, and simple workflows.


1. Concept
-----------

A Static Form Application consists of:

- static HTML pages (input forms)
- CNCF Web Tier
- operation-based execution

Flow:

Browser → HTML Form → Web Tier → Operation → Result Page

No application server is required.


2. Position in CNCF Web Strategy
--------------------------------

CNCF supports three web application models:

1. Static Form Application (this model)
2. SPA (JavaScript-driven)
3. External Web Application (SSR/MVC)

All models share the same backend:

- CNCF operation API
- Web Tier (optional or required depending on access)


3. Core Idea: Convention-Based Routing
--------------------------------------

Instead of defining routing logic in code,
Static Form Applications rely on naming conventions.

Given:

address.html

CNCF resolves result pages based on operation outcome:

Success:

- address__success.html
- address__200.html

Error:

- address__error.html
- address__{status}.html

This removes the need for explicit routing configuration.


4. Execution Flow
-----------------

### Step 1: User Input

User submits form:

<form action="/address" method="POST">

---

### Step 2: Web Tier Processing

- parse form input
- convert to operation input
- invoke operation

---

### Step 3: Result Evaluation

- success → HTTP 200
- validation error → HTTP 400
- auth error → HTTP 401/403
- system error → HTTP 500

---

### Step 4: Page Resolution

Based on base name:

address

Resolve page:

Success:

1. address__success.html
2. address__200.html

Error:

1. address__{status}.html
2. address__error.html

---

### Step 5: Response

- resolved HTML page is returned
- optional query parameters may be appended


5. Parameter Passing
---------------------

Operation results may be passed via:

### 5.1 Query Parameters

Example:

/address__success.html?id=123&name=Alice

---

### 5.2 Form Rehydration (Future)

- repopulate input fields
- display validation messages


6. Minimal Requirements
-----------------------

To build a Static Form App:

- create HTML form page
- deploy under /app/{name}
- define CNCF operation
- optionally create result pages

No JavaScript is required.


7. Benefits
------------

### 7.1 Zero Backend Code

- no controllers
- no routing logic
- no API layer

---

### 7.2 Simplicity

- easy to understand
- easy to debug

---

### 7.3 Fast Development

- rapid prototyping
- internal tools

---

### 7.4 CNCF Integration

- validation via schema
- authentication via Web Tier
- exposure control


8. Limitations
--------------

### 8.1 UI Flexibility

- limited dynamic behavior
- no complex state management

---

### 8.2 UX Constraints

- page reload model
- no reactive UI

---

### 8.3 Data Handling

- limited client-side processing


9. Relationship with Form API
------------------------------

Static Form Application uses Form API concepts:

- schema-driven validation
- consistent error structure

However:

- Form API = data structure + validation
- Static Form App = application pattern

They are related but distinct.


10. Optional Enhancements
-------------------------

### 10.1 Placeholder Rendering

Basic templating:

{{result.name}}

---

### 10.2 Hidden Control Fields

```
<input type="hidden" name="_redirect" value="..." />
```

---

### 10.3 JSON Mode

Return JSON instead of HTML (debug mode)


11. CNCF-Specific Characteristics
---------------------------------

- operation-centric execution
- convention over configuration
- no API stub layer
- unified validation model
- minimal runtime requirements


Conclusion
----------

The Static Form Application model provides
a lightweight and practical way to build web applications in CNCF.

It enables:

- simple application development
- rapid prototyping
- reduced system complexity

while maintaining integration with CNCF's
core execution and security model.
