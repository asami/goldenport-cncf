CNCF Web Descriptor Packaging Model (Draft)
===========================================

Overview
--------

Web descriptors are packaged at the subsystem level.

Subsystem is the primary unit of deployment and configuration.

Two forms exist:

- SAR (Subsystem Archive)
- CAR (Component Archive, treated as subsystem when standalone)


1. SAR Structure
----------------

A SAR may contain:

- multiple components (CAR)
- a Web descriptor
- configuration files

Example:

/components/
/web/web.yaml


2. CAR as Subsystem
--------------------

A CAR without SAR is treated as a subsystem.

It may contain:

/web/web.yaml


3. Descriptor Scope
--------------------

Web descriptor applies to all components within the subsystem.

Selectors must be fully qualified.


4. Override Rules
------------------

Priority:

1. SAR descriptor
2. CAR descriptor (fallback)


5. Rationale
------------

- unified deployment model
- scalable from single component to multi-component system
- clean separation between model and configuration
