package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.auth.UserRole;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalCaseControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClinicalCaseService clinicalCaseService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private UUID caseId;
    private ClinicalCaseRequest validRequest;
    private String validRequestJson;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        validRequest = new ClinicalCaseRequest(
                "Caso demo",
                "Descripción",
                "Paciente Demo",
                35,
                "F",
                "Dolor torácico",
                "Sin más info",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes", "HTA", List.of("presión"), 1, null)),
                List.of("ansiosa")
        );
        validRequestJson = """
                {
                  "title": "Caso demo",
                  "description": "Descripción",
                  "patientName": "Paciente Demo",
                  "patientAge": 35,
                  "patientSex": "F",
                  "chiefComplaint": "Dolor torácico",
                  "noInformationPhrase": "Sin más info",
                  "active": true,
                  "facts": [
                    {
                      "key": "antecedentes",
                      "content": "HTA",
                      "triggers": ["presión"],
                      "revealLevel": 1
                    }
                  ],
                  "personality": ["ansiosa"]
                }
                """;

        ClinicalCaseResponse response = new ClinicalCaseResponse(
                caseId,
                validRequest.title(),
                validRequest.description(),
                validRequest.patientName(),
                validRequest.patientAge(),
                validRequest.patientSex(),
                validRequest.chiefComplaint(),
                validRequest.noInformationPhrase(),
                true,
                LocalDateTime.now(),
                List.of(new ClinicalCaseResponse.ClinicalCaseFactResponse(
                        "ANTECEDENTES",
                        "antecedentes",
                        "HTA",
                        List.of("presión"),
                        1
                )),
                List.of("ansiosa")
        );

        when(clinicalCaseService.updateClinicalCase(eq(caseId), any(ClinicalCaseRequest.class))).thenReturn(response);
        doNothing().when(clinicalCaseService).deleteClinicalCase(caseId);
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void putClinicalCase_profesorRole_allowed() throws Exception {
        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId.toString()))
                .andExpect(jsonPath("$.facts[0].content").value("HTA"))
                .andExpect(jsonPath("$.facts[0].contenido").value("HTA"))
                .andExpect(jsonPath("$.facts[0].contenido_paciente").value("HTA"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void putClinicalCase_adminRole_allowed() throws Exception {
        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId.toString()));
    }

    @Test
    @WithMockUser(roles = {"ESTUDIANTE"})
    void putClinicalCase_estudianteRole_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.details[0].field").value("auth"));
    }

    @Test
    void putClinicalCase_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("No autenticado."))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void putClinicalCase_withAlteredToken_unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .header("Authorization", "Bearer token-alterado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("No autenticado."))
                .andExpect(jsonPath("$.details[0].field").value("auth"));
    }

    @Test
    void putClinicalCase_withExpiredToken_unauthorized() throws Exception {
        String expiredToken = createExpiredToken("profesor.demo@ufrontera.cl", List.of("PROFESOR"));

        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("No autenticado."))
                .andExpect(jsonPath("$.details[0].field").value("auth"));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void putClinicalCase_invalidRevealLevel_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "content": "HTA",
                      "revealLevel": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("facts[0].revealLevel"))
                .andExpect(jsonPath("$.details[0].message").value("El nivel de revelación debe ser mayor o igual a 1."));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void putClinicalCase_missingFactContent_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "revealLevel": 1
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validación fallida en campos: facts[0].content: El contenido del hecho clínico no puede estar vacío."))
                .andExpect(jsonPath("$.details[0].field").value("facts[0].content"))
                .andExpect(jsonPath("$.details[0].message").value("El contenido del hecho clínico no puede estar vacío."));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void putClinicalCase_withContenidoAlias_shouldBindAndReturnOk() throws Exception {
        String requestJsonWithContenido = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "contenido": "HTA",
                      "revealLevel": 1
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/clinical-cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJsonWithContenido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts[0].content").value("HTA"));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void postClinicalCase_missingFactContent_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "revealLevel": 1
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/clinical-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validación fallida en campos: facts[0].content: El contenido del hecho clínico no puede estar vacío."))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(jsonPath("$.details[0].field").value("facts[0].content"));
    }

    @Test
    void postClinicalCase_whenDataIntegrityViolation_returnsHelpfulFieldMessage() throws Exception {
        String requestJson = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "content": "HTA",
                      "revealLevel": 9
                    }
                  ]
                }
                """;

        doThrow(new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: new row for relation \"caso_hecho_clinico\" violates check constraint \"caso_hecho_clinico_nivel_revelacion_check\" (nivel_revelacion BETWEEN 1 AND 4)")
        )).when(clinicalCaseService).createClinicalCase(any(ClinicalCaseRequest.class), any(UUID.class));

        mockMvc.perform(post("/api/v1/clinical-cases")
                        .with(user(new UserPrincipal(
                                UUID.randomUUID(),
                                "Profesor",
                                "profesor@casesim.cl",
                                "hash",
                                true,
                                java.util.Set.of(UserRole.PROFESOR)
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Los datos enviados no cumplen las restricciones requeridas."))
                .andExpect(jsonPath("$.details[0].field").value("facts[].revealLevel"))
                .andExpect(jsonPath("$.details[0].message").value("El nivel de revelación debe estar entre 1 y 4."));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void postClinicalCase_withVisibilityAndNoRevealLevel_shouldReturnCreated() throws Exception {
        ClinicalCaseResponse response = new ClinicalCaseResponse(
                caseId,
                "Caso demo",
                "Descripción",
                "Paciente Demo",
                35,
                "F",
                "Dolor torácico",
                "Sin más info",
                true,
                LocalDateTime.now(),
                List.of(new ClinicalCaseResponse.ClinicalCaseFactResponse(
                        "ANTECEDENTES",
                        "antecedentes",
                        "HTA",
                        List.of("presión"),
                        1
                )),
                List.of("ansiosa")
        );
        when(clinicalCaseService.createClinicalCase(any(ClinicalCaseRequest.class), any(UUID.class))).thenReturn(response);

        String requestJson = """
                {
                  "title": "Caso demo",
                  "chiefComplaint": "Dolor torácico",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "key": "antecedentes",
                      "content": "HTA",
                      "visibility": "INITIAL"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/clinical-cases")
                        .with(user(new UserPrincipal(
                                UUID.randomUUID(),
                                "Profesor",
                                "profesor@casesim.cl",
                                "hash",
                                true,
                                java.util.Set.of(UserRole.PROFESOR)
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facts[0].revealLevel").value(1));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void postClinicalCase_withFrontendPayloadAliases_shouldBindAndReturnCreated() throws Exception {
        ClinicalCaseResponse response = new ClinicalCaseResponse(
                caseId,
                "Caso demo",
                "Descripción",
                "Paciente Demo",
                30,
                "F",
                "Dolor de cabeza",
                "Sin más info",
                true,
                LocalDateTime.now(),
                List.of(),
                List.of()
        );
        when(clinicalCaseService.createClinicalCase(any(ClinicalCaseRequest.class), any(UUID.class))).thenReturn(response);

        String requestJson = """
                {
                  "title": "Caso demo",
                  "age": 30,
                  "reason": "Dolor de cabeza",
                  "facts": [
                    {
                      "category": "ANTECEDENTES",
                      "title": "antecedentes",
                      "content": "HTA",
                      "trigger": "presión",
                      "visibility": "ON_QUESTION"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/clinical-cases")
                        .with(user(new UserPrincipal(
                                UUID.randomUUID(),
                                "Profesor",
                                "profesor@casesim.cl",
                                "hash",
                                true,
                                java.util.Set.of(UserRole.PROFESOR)
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        verify(clinicalCaseService).createClinicalCase(argThat(request ->
                        request.patientAge() == 30
                                && "Dolor de cabeza".equals(request.chiefComplaint())
                                && request.facts() != null
                                && request.facts().size() == 1
                                && "antecedentes".equals(request.facts().getFirst().key())
                                && "presión".equals(request.facts().getFirst().triggers())
                                && "ON_QUESTION".equals(request.facts().getFirst().visibility())),
                any(UUID.class));
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void deleteClinicalCase_profesorRole_allowed() throws Exception {
        mockMvc.perform(delete("/api/v1/clinical-cases/{id}", caseId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void deleteClinicalCase_adminRole_allowed() throws Exception {
        mockMvc.perform(delete("/api/v1/clinical-cases/{id}", caseId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = {"ESTUDIANTE"})
    void deleteClinicalCase_estudianteRole_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/clinical-cases/{id}", caseId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acceso denegado."));
    }

    @Test
    void deleteClinicalCase_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/clinical-cases/{id}", caseId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autenticado."));
    }

    private String createExpiredToken(String subject, List<String> roles) {
        SecretKey secretKey = Keys.hmacShaKeyFor(hashSecret(jwtSecret));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(java.util.Date.from(now.minusSeconds(3600)))
                .expiration(java.util.Date.from(now.minusSeconds(60)))
                .claim("roles", roles)
                .signWith(secretKey)
                .compact();
    }

    private byte[] hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
