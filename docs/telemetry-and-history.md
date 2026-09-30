# Telemetry Workstreams

**Last Updated:** 2025-01-06

## Workstream Snapshot
| Initiative | Objective | Current Status | Upcoming Milestone |
| --- | --- | --- | --- |
| Process Variable (PV) Infrastructure Refactor | Replace legacy event bitmasks and raw maps with typed, thread-safe telemetry primitives | Typed wrappers live; pilot `PvChange` enum ready for wider rollout | Migrate top listeners to enum payloads and deprecate bitmask accessors |
| Analytics & QA Alignment | Keep telemetry/data consumers ready for PV changes | VIN/PID regression matrix distributed, analytics spec drafted | Validate instrumentation in staging and sync sign-off before flag ramp |

## TODO
- [x] Introduce `TypedProcessVar`/`TypedPvList` wrappers to coexist with legacy structures.
- [x] Migrate `ObdDataService`, `ObdProt`, and UI adapters to typed PV access.
- [x] Harden concurrency via `ReentrantReadWriteLock` around PV updates.
- [ ] Replace bitmask-based `PvChangeEvent` with enum payload across listeners.
- [ ] Retire raw-map PV classes after enum rollout verifies parity.

## Process Variable Infrastructure Refactor

### Current State
- `TypedProcessVar` and `TypedPvList` wrappers are merged and exercised by core services (`ObdDataService`, `ObdProt`) and primary UI adapters.
- `PvChange` enum prototype is available and validated in `MainActivity`; compatibility shims still expose legacy bitmask values. VehicleInfoFooter, adapters, and services have not migrated yet.
- Thread-safety tightened via read/write locks around listener dispatch, reducing race conditions during bulk updates.

### Near-Term Deliverables
1. Expand `PvChange` enum adoption to VehicleInfoFooter, data-service listeners, and adapter layers; emit dual payloads during transition.
2. Publish `ProcessVariable<K, V>` interface and adapter so legacy `HashMap` implementations can be phased out without breaking callers.
3. Update unit/integration coverage to assert enum ↔ UI mappings, threading guarantees, and serialization compatibility.

### Dependencies & Coordination
- Align rollout with ECU refactor timelines so PID runtime objects can rely on the new typed interfaces.
- Keep QA in the loop using the VIN/PID regression matrix maintained in TestRail runs TR-1893/TR-1894 to validate telemetry behavior.
- Coordinate with Analytics to ensure enum-backed events land in downstream pipelines before bitmask removal.

### Risks & Mitigations
- **Listener Drift:** Track migration status per listener to avoid inconsistent payload handling; add lint checks once adoption passes 80%.
- **Binary Compatibility:** Ship adapters that expose both legacy and new APIs until downstream modules confirm readiness.
- **Testing Surface:** Expand JVM tests and targeted instrumentation cases to keep parity visible and auditable.

## Analytics & QA Alignment
- Regression assets curated in TestRail runs TR-1893/TR-1894, covering VIN fixtures, PID datasets, and owners for staged testing.
- Staging validation to include logcat checks, Snowplow stream verification, and dashboard smoke tests prior to production rollout.

## Next Actions
1. Migrate high-traffic listeners to the `PvChange` enum and monitor telemetry for regressions before deprecating bitmask accessors.
2. Execute the VIN/PID regression matrix during staging builds and capture QA sign-off prior to increasing feature-flag exposure.
