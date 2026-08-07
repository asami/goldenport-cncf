# Canonical CAR Repository Resolution

## Direct boundary

`CanonicalCarRepositoryResolver.resolve(qualifiedComponentId, release,
repositories, cacheRoot)` is the direct CNCF repository/cache boundary for a
known canonical CAR release. It parses the ID with shared `ComponentId` and
creates one shared `ComponentReleaseCoordinate`. A bare ID is rejected with
the shared `component.identity.id.qualified` diagnostic; null ID and release
retain their shared required diagnostics.

The resolver uses only the coordinate's exact dependency key,
`carRepositoryRelativePath`, and `carCacheRelativePath`. It does not inspect a
CAR, load a descriptor, infer aliases, traverse an index/catalog, resolve
Component.Core, or migrate runtime selectors.

## Resolution

Resolution is cache-first. A regular archive at
`cacheRoot/<carCacheRelativePath>` is returned even for a SNAPSHOT. Otherwise,
configured roots are tried in order:

- a filesystem root or `file:` URI is joined with
  `<carRepositoryRelativePath>`;
- an `http://` or `https://` root is requested at that exact relative path,
  only for non-SNAPSHOT releases.

HTTP retrieval writes a unique sibling temporary file and moves it atomically
to the exact cache destination, with a non-atomic no-replacement fallback.
Failure removes the temporary file and leaves no final archive. A concurrent
or prior cache hit is never replaced. The final miss reports the exact shared
dependency key.

Repository entries and cache roots are explicit required inputs. Null, blank,
malformed, or unsupported entries fail with stable `component.repository.*`
diagnostics rather than throwing.

## Namespace isolation

`org.alpha.textus.Shared:0.6.0` and
`org.beta.textus.Shared:0.6.0` may share the filename
`textus-shared-0.6.0.car`. Their qualified dependency keys, repository request
paths, cache paths, and archive bytes remain distinct. Filename equality is
not repository identity.

The legacy runtime `ComponentRepository` remains in place until CID-05/CID-06;
this direct boundary is not a replacement generic runtime repository API.
