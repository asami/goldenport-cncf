# Phase 42 - Static Web Server Rendering Contract Checklist

This checklist is the authoritative Phase 42 state ledger.

## SW-01: Typed Page View

Status: DONE

- [x] Carry the component page model as `WebPageContext.view: Record`.
- [x] Resolve nested `pageContext.view.*` sources in server-side widgets.
- [x] Publish the same typed model in the safe page-context script block.
- [x] Keep the framework execution projection authoritative during provider
  merges.

## SW-02: Locale-Correct First Render

Status: ACTIVE

- [ ] Resolve template message keys from `ExecutionContext.locale`.
- [ ] Derive `html[lang]`, `Content-Language`, and timezone from the same
  resolved context.
- [ ] Verify Japanese and English first paint without a translation pass.

## SW-03: Aggregate Forms

Status: OPEN

- [ ] Bind normal HTML forms to aggregate commands.
- [ ] Provide validation, authorization, flash state, and PRG.
- [ ] Keep JavaScript-disabled form operation complete.

## SW-04: Privacy and Acceptance

Status: OPEN

- [ ] Define public, standalone, and authenticated page cache policy.
- [ ] Verify subject-specific data cannot enter shared responses.
- [ ] Verify no browser REST bootstrap for primary content or execution
  context.
