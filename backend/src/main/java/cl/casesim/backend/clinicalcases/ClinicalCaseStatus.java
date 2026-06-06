package cl.casesim.backend.clinicalcases;

public enum ClinicalCaseStatus {
    DRAFT,
    READY,
    ARCHIVED;

    public boolean isAssignable() {
        return this == READY;
    }

    public boolean isLegacyActive() {
        return this != ARCHIVED;
    }

    public static ClinicalCaseStatus fromLegacyActive(boolean active) {
        return active ? READY : ARCHIVED;
    }
}
