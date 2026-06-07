package cl.casesim.backend.clinicalcases;

public enum ClinicalCaseStatus {
    DRAFT,
    READY;

    public boolean isAssignable() {
        return this == READY;
    }

    /**
     * Since ARCHIVED was removed, all statuses are considered legacy-active.
     * The {@code activo} boolean field in the entity is the source of truth for deactivation.
     */
    public boolean isLegacyActive() {
        return true;
    }

    /**
     * Maps legacy {@code activo} boolean to status.
     * {@code active=true} -> READY, {@code active=false} -> DRAFT (ARCHIVED no longer exists).
     */
    public static ClinicalCaseStatus fromLegacyActive(boolean active) {
        return active ? READY : DRAFT;
    }
}
