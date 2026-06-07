package cl.casesim.backend.clinicalcases;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that maps ClinicalCaseStatus to/from database string values.
 * Handles legacy 'ARCHIVED' values in the database by converting them to DRAFT,
 * since the ARCHIVED enum constant was removed.
 */
@Converter(autoApply = true)
public class ClinicalCaseStatusConverter implements AttributeConverter<ClinicalCaseStatus, String> {

    @Override
    public String convertToDatabaseColumn(ClinicalCaseStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public ClinicalCaseStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return ClinicalCaseStatus.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            // Legacy values (e.g., 'ARCHIVED') map to DRAFT for backward compatibility
            return ClinicalCaseStatus.DRAFT;
        }
    }
}
