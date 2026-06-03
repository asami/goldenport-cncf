# Book, Edition, Volume, and Publication Modeling Notes

Date: 2026-06-01
Status: design note

## Purpose

This note examines the role of Book, Edition, Volume, and Publication in the Knowledge Hub Information Model.

The current model is:

```text
Work
  ↓
Edition
  ↓
Book
```

where Book represents a concrete published book and carries identifiers such as ISBN.

The goal is to clarify:

- whether Volume should be an independent Information object
- how paper books and electronic books should be modeled
- how Publication should be represented

## Current Interpretation

The current model assumes:

```text
Work
  ↓
Edition
  ↓
Book
```

### Work

Represents the intellectual work itself.

Examples:

- Genji Monogatari
- The Lord of the Rings
- Effective Scala

A Work exists independently from any particular publication.

### Edition

Represents a particular editorial realization of a Work.

Examples:

- Shincho Classical Japanese Series edition
- Penguin Classics edition
- Revised Second Edition

An Edition defines:

- editor
- translation
- textual revisions
- annotations
- publication policy

### Book

Represents a concrete publication unit.

Examples:

- ISBN 978-4-10-620801-0
- Kindle ASIN B012345678

A Book is the unit typically obtained from external sources such as:

- OpenBD
- OpenLibrary
- Google Books
- Amazon

Book therefore corresponds to a concrete publication record.

## Volume Analysis

The meaning of Volume depends on context.

### Case 1: Volume and Book Are Identical

Example:

```text
Work
  Genji Monogatari

Edition
  Shincho Edition

Book
  Volume 1

Book
  Volume 2

Book
  Volume 3
```

Each volume has its own ISBN.

In this case:

```text
Volume ≈ Book
```

A separate Volume Information object provides little value.

The volume number can simply be stored as:

```text
Book.volume_number
Book.volume_title
```

### Case 2: Volume Groups Multiple Formats

Example:

```text
Volume 1
 ├─ Hardcover
 ├─ Paperback
 ├─ EPUB
 └─ Kindle
```

Here Volume becomes a meaningful conceptual unit.

A possible structure is:

```text
Work
  ↓
Edition
  ↓
Volume
  ↓
Book
```

In this case:

- Volume represents the logical volume
- Book represents a concrete publication format

Volume may become an independent Information object.

## Paper Books and Electronic Books

The most natural interpretation is:

```text
Work
  ↓
Edition
  ↓
Book
```

with Book representing publication formats.

Example:

```text
Work
  Genji Monogatari

Edition
  Shincho Edition

Book
  format = hardcover
  isbn = 1111

Book
  format = paperback
  isbn = 2222

Book
  format = epub
  identifier = EPUB-123

Book
  format = kindle
  asin = ABCDEF
```

This avoids duplicating Editions merely because publication media differ.

### Why Not Create Separate Editions?

An alternative would be:

```text
Edition
  Paper Edition

Edition
  Electronic Edition
```

However:

- content remains identical
- editor remains identical
- translator remains identical

Only the delivery medium changes.

Therefore media differences belong more naturally at the Book level.

## Publication Analysis

Publication has two distinct meanings.

### Publication Metadata

Examples:

```text
publisher = Shinchosha
publication_date = 2025-01-01
```

These are attributes of a Book.

No independent Information object is required.

### Publication Event

Example:

```text
On 2025-01-01
Shinchosha published the book
```

This is not Information.

This is an Event.

Possible representation:

```text
PublicationEvent
```

with relationships to:

- Book
- Publisher
- Date

## Adopted KE-13 Design

KE-13 uses the following design boundary:

```text
Textual Work
  ↓
Textual Edition
  ↓
Textual Volume (optional)
  ↓
Book
```

`Book` is the concrete textual publication layer in TKE v1. It represents an
ISBN-bearing or otherwise publication-format-specific Information object. A
separate `Textual Publication` Information domain is not introduced in this
slice.

`Textual Volume` is available as a first-class Information domain, but it is
not mandatory for every book. It should be used when there is a meaningful
logical volume between an edition and one or more concrete publications. If
the volume and the concrete book publication are effectively identical, the
volume number/title may remain on the Book publication Information.

Textual works may also have internal parts that are not publications. For
example, `花散里` and `須磨` in Genji monogatari are chapters/parts of the
Textual Work, not Book volumes. They should be modeled as a future
`Textual Part` / `Chapter` concept under `Textual Work`.

```text
Textual Work
  ↓ has-part
Textual Part / Chapter
```

Editions and volumes can then record which textual parts they contain:

```text
Textual Edition / Textual Volume
  ↓ includes
Textual Part / Chapter
```

This separates work-internal structure from publication structure. A volume is
an edition/publication-side grouping; a chapter/part is a work-side structure.

This keeps the common model ready for multi-format or multi-volume structures
without forcing unnecessary intermediate nodes into simple book records.

## Recommended KE-13 Model

General recommendation for the book/text domain:

```text
Textual Work
  ↓
Textual Edition
  ↓
Textual Volume (optional)
  ↓
Book
```

Book remains the concrete textual publication Information object in this
slice. It contains publication-format and identifier values:

```text
identifier
format
publisher
publication_year
publication_month
publication_day
volume_number
volume_title
```

Textual Volume should not be required as a separate Information object for
every book. It is used only when a logical volume has independent meaning
between a Textual Edition and one or more concrete Book publications.

There is no separate `Textual Publication` Information domain in KE-13.
Book, Magazine, issue, or other textual publication profiles can later be
organized under a broader Textual Publication concept if the product needs it.

## Information Objects

Current recommendation for Information-level entities in this slice:

```text
Textual Work
Textual Edition
Textual Volume (optional)
Book
Publisher / Organization
Person
Place
Subject
Classification
```

Textual Volume is optional and should be used only when it represents a
distinct conceptual entity rather than a simple property of Book.

Publication as an event is a future event-model concern. It is not the primary
representation of the concrete ISBN-bearing Book Information in KE-13.

## Implications for SIE Knowledge Materialization

For SIE-based book knowledge materialization:

- Textual Work should receive a stable KnowledgeNode when present.
- Textual Edition should receive a stable KnowledgeNode when present.
- Textual Volume should receive a stable KnowledgeNode only when it is modeled
  explicitly.
- Book should materialize as the concrete publication layer.
- Publisher / Organization should receive a stable KnowledgeNode.
- CulturalResource attributes should mark the book-domain layers without
  collapsing non-book cultural resources into Textual Work.
- Publication events may be added later when event-level knowledge is needed.

This keeps the Information model simple while allowing future expansion when
richer bibliographic relationships become necessary.
