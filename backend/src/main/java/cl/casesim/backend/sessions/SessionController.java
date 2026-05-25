package cl.casesim.backend.sessions;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.CreateSessionRequest;
import cl.casesim.backend.sessions.dto.FinalDiagnosisRequest;
import cl.casesim.backend.sessions.dto.SendMessageRequest;
import cl.casesim.backend.sessions.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        SessionService.CreateSessionResult result = sessionService.createSession(request, requireAuthenticatedStudentId(userPrincipal));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.session());
    }

    @GetMapping("/{id}")
    public SessionResponse getSessionById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return sessionService.getSessionById(id, requireAuthenticatedStudentId(userPrincipal));
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageResponse> getSessionMessages(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return sessionService.getMessagesBySession(id, requireAuthenticatedStudentId(userPrincipal));
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ChatMessageResponse> createSessionMessages(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return sessionService.createMessages(id, request, requireAuthenticatedStudentId(userPrincipal));
    }

    @PostMapping("/{id}/complete")
    public SessionResponse completeSession(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return sessionService.completeSession(id, requireAuthenticatedStudentId(userPrincipal));
    }

    @PostMapping("/{id}/final-diagnosis")
    public SessionResponse registerFinalDiagnosis(
            @PathVariable("id") UUID id,
            @Valid @RequestBody FinalDiagnosisRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return sessionService.registerFinalDiagnosis(id, request, requireAuthenticatedStudentId(userPrincipal));
    }

    private UUID requireAuthenticatedStudentId(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado.");
        }
        return userPrincipal.getId();
    }
}
