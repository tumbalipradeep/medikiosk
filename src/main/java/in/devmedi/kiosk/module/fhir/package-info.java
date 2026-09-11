/**
 * FHIR R4 interoperability foundation for MediKiosk.
 *
 * <p>This module projects persisted MediKiosk clinical data into standard,
 * deterministic FHIR R4 resources ({@code Patient}, {@code Encounter},
 * {@code Observation}, {@code DocumentReference}, {@code Consent}) grouped
 * into {@code Bundle} instances (type {@code collection}) per completed case.
 * It is a read-only, one-way projection: existing domain entities are never
 * modified, no FHIR code leaks into other modules, and no clinical data is
 * invented. An observation is only emitted for data that is already persisted
 * by M1/M2/M3.</p>
 *
 * <p>Nothing here performs network I/O. ABDM/HIP/HEX connectivity is out of
 * scope for this foundation and will build on these resources in a later
 * checkpoint.</p>
 *
 * <p>Resource identifiers are deterministic (RFC 4122 UUID version 5 over a
 * documented business key) so the same domain row always yields the same
 * logical FHIR id and bundles are reproducible. Shared internal JSON uses
 * {@code tools.jackson} (Jackson 3), matching the rest of the application.</p>
 */
package in.devmedi.kiosk.module.fhir;
