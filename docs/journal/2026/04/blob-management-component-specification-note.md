# Blob Management Component Specification Note

Date: 2026-04-12
Status: draft
Scope: CNCF builtin Blob management component

## Context

CNCF needs a builtin component for managing Blob-like assets such as images,
videos, and attachments.

This is independent of the SimpleEntity DB storage-shape work. Blob payloads are
large external objects and should not be treated as ordinary SimpleEntity scalar
columns or embedded value-object fields.

## Goal

Provide a builtin Blob management component that supports:

- Image, Video, and Attachment registration;
- Blob metadata management as an Entity;
- Blob payload storage through an S3-like object storage backend abstraction;
- association between Blob objects and arbitrary domain entities;
- Aggregate/View use of associated media without embedding payloads in parent
  entity storage records.

## Non-Goals

- Do not store Blob payloads directly in ordinary Entity records.
- Do not make Blob management part of the SimpleEntity storage-shape policy.
- Do not flatten repeated media references into parent entity columns.
- Do not require a specific cloud provider in the initial abstraction.

## Builtin Component Direction

The builtin Blob component should own:

- Blob registration;
- Blob metadata lookup;
- Blob lifecycle management;
- Blob deletion semantics;
- payload storage backend access;
- media/attachment type classification;
- optional checksum/content-type/size metadata.

Blob metadata is represented as an Entity.

Blob payload is stored in a separate object storage abstraction, initially
modeled as S3-like storage:

- bucket/container;
- key/object name;
- content type;
- content length;
- checksum or digest;
- optional version or ETag;
- object storage URI/reference.

## Entity Association Direction

Domain entities, such as product-like entities, should associate with Blob
entities rather than embedding Blob payloads.

Examples:

- product -> main image;
- product -> image gallery;
- article -> attached PDF;
- profile -> avatar image;
- video entry -> preview image and video object.

The association mechanism is still open and needs explicit design.

Candidate approaches:

- association records owned by the Blob component;
- association records owned by each domain entity component;
- a generic association component that can connect arbitrary entity pairs;
- typed relation fields on domain entities that reference Blob entity ids.

The preferred approach is not decided yet.

## Aggregate/View Usage

Aggregate/View projections should be able to gather associated Blob metadata for
read/display use.

Important boundary:

- Aggregate/View may include Blob metadata or references;
- Aggregate/View should not inline Blob payloads;
- payload retrieval should go through Blob component operations or generated
  access URLs;
- domain entity storage remains independent from Blob payload storage.

## Open Questions

1. What is the canonical Blob metadata entity model?
2. What is the minimal S3-like storage abstraction required by CNCF?
3. How should Blob object lifecycle and deletion interact with entity
   associations?
4. Which component owns Blob-object association records?
5. Should association support be generic across entity pairs or Blob-specific?
6. How should access control be applied to Blob metadata and payload retrieval?
7. Should Blob payload retrieval return bytes, streams, signed URLs, or
   operation responses?
8. How should checksum/content-type/size validation be specified?

## Initial Recommendation

Start with a builtin Blob management component that owns Blob metadata and
payload storage access, while keeping entity association as a separate design
item.

Do not embed Blob payloads in parent entity records. Domain entities should
associate with Blob entities through an explicit association mechanism that can
later support Aggregate/View projection.
