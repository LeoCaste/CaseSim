package cl.casesim.backend.professor;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.professor.dto.ProfessorSessionListItemResponse;
import cl.casesim.backend.professor.dto.ProfessorSessionReviewResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/professor/sessions")
public class ProfessorSessionsController {

    private final ProfessorSessionsService professorSessionsService;

    public ProfessorSessionsController(ProfessorSessionsService professorSessionsService) {
        this.professorSessionsService = professorSessionsService;
    }

    @GetMapping
    public List<ProfessorSessionListItemResponse> getSessions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return professorSessionsService.getSessions(requireUserId(userPrincipal), limit);
    }

    @GetMapping("/{sessionId}")
    public ProfessorSessionReviewResponse getSessionReview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID sessionId
    ) {
        return professorSessionsService.getSessionReview(requireUserId(userPrincipal), sessionId);
    }

    private UUID requireUserId(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado.");
        }
        return userPrincipal.getId();
    }
}
