package cl.casesim.backend.auth;

public enum UserRole {
    ESTUDIANTE,
    PROFESOR,
    ADMIN;

    public static UserRole fromDbValue(String value) {
        return UserRole.valueOf(value.toUpperCase());
    }
}
