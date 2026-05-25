package cl.casesim.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagingSecurityGuardTest {

    @Test
    void shouldFailInStagingWhenSecretsAreInsecure() {
        StagingSecurityGuard guard = new StagingSecurityGuard(
                "staging",
                "dev-secret-change-me",
                "casesim-default-llm-key",
                "http://localhost:4200",
                "jdbc:postgresql://staging-host:5432/casesim",
                "staging_user",
                "staging_password",
                "DISABLED",
                "",
                "",
                ""
        );

        assertThatThrownBy(guard::validateStagingSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shouldPassInStagingWithStrongSecretsAndRestrictedCors() {
        StagingSecurityGuard guard = new StagingSecurityGuard(
                "staging",
                "jwt-super-secreto-largo-para-staging-12345",
                "llm-cipher-key-super-larga-para-staging-12345",
                "https://staging.casesim.cl,http://localhost:4200",
                "jdbc:postgresql://staging-db.internal:5432/casesim_staging",
                "casesim_staging",
                "strong-password-value",
                "MANUAL",
                "operations-token-very-strong",
                "https://staging.casesim.cl",
                "staging"
        );

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowDefaultsInDev() {
        StagingSecurityGuard guard = new StagingSecurityGuard(
                "dev",
                "dev-secret-change-me",
                "casesim-default-llm-key",
                "*",
                "jdbc:postgresql://localhost:5433/casesim_db",
                "casesim_user",
                "casesim_pass",
                "DISABLED",
                "",
                "",
                ""
        );

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldFailInProdWhenCorsIsEmpty() {
        StagingSecurityGuard guard = new StagingSecurityGuard(
                "prod",
                "jwt-super-secreto-largo-para-prod-123456",
                "llm-cipher-key-super-larga-para-prod-123456",
                "",
                "jdbc:postgresql://prod-db.internal:5432/casesim",
                "prod_user",
                "prod_password",
                "DISABLED",
                "",
                "",
                "prod"
        );

        assertThatThrownBy(guard::validateStagingSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }

    @Test
    void shouldFailInStagingWhenCorsContainsWildcard() {
        StagingSecurityGuard guard = new StagingSecurityGuard(
                "staging",
                "jwt-super-secreto-largo-para-staging-12345",
                "llm-cipher-key-super-larga-para-staging-12345",
                "*",
                "jdbc:postgresql://staging-db.internal:5432/casesim_staging",
                "staging_user",
                "staging_password",
                "DISABLED",
                "",
                "",
                ""
        );

        assertThatThrownBy(guard::validateStagingSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no puede contener '*'");
    }

    @Test
    void shouldAllowDevWithoutActiveProfile() {
        StagingSecurityGuard guard = createGuardForEnvCheck("dev", "");

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowDevWithDefaultProfile() {
        StagingSecurityGuard guard = createGuardForEnvCheck("dev", "default");

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowStagingWhenProfileMatches() {
        StagingSecurityGuard guard = createGuardForEnvCheck("staging", "staging");

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowProdWhenProfileMatches() {
        StagingSecurityGuard guard = createGuardForEnvCheck("prod", "prod");

        assertThatCode(guard::validateStagingSecurity).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenAppEnvMissingAndProdProfileActive() {
        StagingSecurityGuard guard = createGuardForEnvCheck("", "prod");

        assertProfileMismatch(guard);
    }

    @Test
    void shouldFailWhenAppEnvDevAndProdProfileActive() {
        StagingSecurityGuard guard = createGuardForEnvCheck("dev", "prod");

        assertProfileMismatch(guard);
    }

    @Test
    void shouldFailWhenAppEnvStagingAndProdProfileActive() {
        StagingSecurityGuard guard = createGuardForEnvCheck("staging", "prod");

        assertProfileMismatch(guard);
    }

    @Test
    void shouldFailWhenAppEnvProdAndStagingProfileActive() {
        StagingSecurityGuard guard = createGuardForEnvCheck("prod", "staging");

        assertProfileMismatch(guard);
    }

    private StagingSecurityGuard createGuardForEnvCheck(String appEnv, String springProfilesActive) {
        return new StagingSecurityGuard(
                appEnv,
                "jwt-super-secreto-largo-para-entorno-123456",
                "llm-cipher-key-super-larga-para-entorno-123456",
                "https://casesim.example",
                "jdbc:postgresql://db.internal:5432/casesim",
                "secure_user",
                "strong-password-value-2",
                "DISABLED",
                "",
                "",
                springProfilesActive
        );
    }

    private void assertProfileMismatch(StagingSecurityGuard guard) {
        assertThatThrownBy(guard::validateStagingSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENV and SPRING_PROFILES_ACTIVE are inconsistent. Set both to staging/prod.");
    }
}
