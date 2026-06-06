package cl.casesim.backend.student;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.auth.UserRole;
import cl.casesim.backend.student.dto.StudentClinicalCaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentClinicalCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentClinicalCaseService studentClinicalCaseService;

    @Test
    void getAssignedClinicalCase_returnsSafeDtoOnly() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID clinicalCaseId = UUID.randomUUID();
        when(studentClinicalCaseService.getAssignedClinicalCase(activityId, studentId))
                .thenReturn(new StudentClinicalCaseResponse(
                        activityId,
                        clinicalCaseId,
                        "Caso clínico asignado",
                        "Paciente Demo",
                        35,
                        "F",
                        "Dolor torácico"
                ));

        mockMvc.perform(get("/api/v1/student/clinical-cases/{activityId}", activityId)
                        .with(user(new UserPrincipal(
                                studentId,
                                "Estudiante",
                                "estudiante@casesim.cl",
                                "hash",
                                true,
                                Set.of(UserRole.ESTUDIANTE)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()))
                .andExpect(jsonPath("$.clinicalCaseId").value(clinicalCaseId.toString()))
                .andExpect(jsonPath("$.title").value("Caso clínico asignado"))
                .andExpect(jsonPath("$.chiefComplaint").value("Dolor torácico"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.facts").doesNotExist())
                .andExpect(jsonPath("$.personality").doesNotExist())
                .andExpect(jsonPath("$.metadata").doesNotExist())
                .andExpect(jsonPath("$.noInformationPhrase").doesNotExist())
                .andExpect(jsonPath("$.expectedDiagnosis").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"PROFESOR"})
    void getAssignedClinicalCase_professorRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/clinical-cases/{activityId}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
}
