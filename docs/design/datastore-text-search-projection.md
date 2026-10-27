# Declared DataStore text-search projection

The common DataStore boundary owns literal SQL predicate escaping and the
explicit `DataStoreTextSearchField` projection. This decision supports complete
JVM String matching without redefining native text storage or SQL constraints.
The public behavior is specified in
[DataStore text search](../spec/datastore-text-search.md).

UTF16 code units are transported as lowercase hexadecimal ASCII. Each unit has
an `x` marker that cannot occur in a hexadecimal digit, preventing an arbitrary
needle from matching a partial unit or an odd boundary. The `u16x1:` prefix
identifies the representation and anchors prefix queries, including empty
sources. There is no charset roundtrip that could replace an unpaired
surrogate or expose NUL to a provider that does not admit NUL in native text.

Optional Java `Locale.ROOT` folding happens explicitly before projection for
both source and needle. The database receives case-sensitive existing Query
predicates over ASCII, so database Unicode folding and collation do not choose
the consumer's String policy. The helper does not normalize combining marks
or code points, and supplementary characters retain their two code units.

The projection is additional derived data in an explicitly declared column.
It is not the original storage codec, a new Core datatype, a new query engine,
a component-specific workaround or a second database/index/cache service.
Consumers use the normal native Entity row and atomically maintain original
and projection under existing authorization, revision and transaction stages.
They retain original documents and metadata as authoritative. No decoder or
original reconstruction promise is exposed, particularly after folding.

Declaration owners pair the policy and representation version with their
physical column. A future version requires an explicit consumer migration
decision; this implementation does not rewrite live data or infer encodings
from String shape. Projection contents are payload, not log-safe identity or
access authority. Future TKL integration must consume this common API and
preserve its existing valid inputs and native persistence responsibilities.
