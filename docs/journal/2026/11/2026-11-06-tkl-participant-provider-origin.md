# Selected provider attribution for participant trust

The TKL participant integration needs to distinguish the actual selected account
provider from other authenticated providers. CNCF already records admitted
provider versus trusted-local origin privately, but its provider traversal drops
the resolved binding before returning AuthenticationResult.

This bounded source change retains the exact successful binding/provider pair and
adds secret-free AuthenticationProviderIdentity plus SecurityContext's optional
reader. Existing private weak identity provenance owns attribution and inheritance;
no constructor field, serialized authority, new authentication issuer or application
realm/account policy is introduced. TKL remains responsible for its own declared
trust profile and owner decisions. Studio/App lifecycle history is unaffected.

Source implementation and executable specifications are prepared for native
qualification only. No checks, independent review, acceptance, local publication,
installation or consumer outcome is claimed. The parent owns those gates and any
subsequent TKL/Studio integration. No real Drive/device or external effect occurs.
