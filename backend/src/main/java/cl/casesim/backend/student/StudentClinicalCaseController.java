package cl.casesim.backend.student;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.student.dto.StudentClinicalCaseResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/clinical-cases")
public class StudentClinicalCaseController {

    private final StudentClinicalCaseService studentClinicalCaseService;

    public StudentClinicalCaseController(StudentClinicalCaseService studentClinicalCaseService) {
        this.studentClinicalCaseService = studentClinicalCaseService;
    }

    @GetMapping("/{activityId}")
    public StudentClinicalCaseResponse getAssignedClinicalCase(
            @PathVariable UUID activityId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (userPrincipal == null) {
            throw new AccessDeniedException("Acceso denegado.");
        }
        return studentClinicalCaseService.getAssignedClinicalCase(activityId, userPrincipal.getId());
    }
}
