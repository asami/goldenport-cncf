# Phase 39 - Server Port Allocation Checklist

## SP-01: Allocation Contract

Status: DONE

- [x] Define CAR, SAR, additional-instance, and bare-runtime ranges.
- [x] Define explicit override precedence and legacy compatibility.
- [x] Define stable artifact defaults and dynamic additional-instance behavior.

## SP-02: Runtime Policy

Status: DONE

- [x] Add `ServerPortPolicy` as the sole `CncfRuntime` port resolver.
- [x] Classify CAR, SAR, and bare runtime activation.
- [x] Allocate and validate persistent machine-local defaults under a file lock.
- [x] Keep explicit ports authoritative and retain bare-runtime `8080`.

## SP-03: Artifact Metadata and Operations

Status: DONE

- [x] Define `textus.server.default-port` as authored CAR/SAR metadata.
- [x] Document Textus Control Center ownership of the official catalog.
- [x] Report the actual endpoint after successful HTTP binding.
- [x] Publish and clear the bound endpoint for delayed launcher registration.

## SP-04: Executable Evidence and Closure

Status: DONE

- [x] Cover default, override, allocation, persistence, and conflict behavior.
- [x] Cover bound-endpoint publication and shutdown cleanup.
- [x] Record the stable design and developer guidance.
- [x] Validate the CNCF runtime suite before release commit.
