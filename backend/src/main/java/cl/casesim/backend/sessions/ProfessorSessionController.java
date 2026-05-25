package cl.casesim.backend.sessions;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.auth.UserRole;
import cl.casesim.backend.sessions.dto.ProfessorSessionDetailResponse;
import cl.casesim.backend.sessions.dto.ProfessorSessionListItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("legacy-professor-sessions")
@RequestMapping("/api/v1/professor/sessions")
public class ProfessorSessionController {

    private final ProfessorSessionService professorSessionService;

    public ProfessorSessionController(ProfessorSessionService professorSessionService) {
        this.professorSessionService = professorSessionService;
    }

    @GetMapping
    public List<ProfessorSessionListItemResponse> getProfessorSessions(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return professorSessionService.getProfessorSessions(requireProfessorId(userPrincipal));
    }

    @GetMapping("/{id}")
    public ProfessorSessionDetailResponse getProfessorSessionById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return professorSessionService.getProfessorSessionById(requireProfessorId(userPrincipal), id);
    }

    private UUID requireProfessorId(UserPrincipal userPrincipal) {
        if (userPrincipal == null || !userPrincipal.getRoles().contains(UserRole.PROFESOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para profesores.");
        }
        return userPrincipal.getId();
    }
}
