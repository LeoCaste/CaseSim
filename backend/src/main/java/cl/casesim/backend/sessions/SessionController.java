package cl.casesim.backend.sessions;

import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.CreateChatMessageRequest;
import cl.casesim.backend.sessions.dto.CreateSessionRequest;
import cl.casesim.backend.sessions.dto.SimulationSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationSessionResponse createSession(@Valid @RequestBody CreateSessionRequest request) {
        return sessionService.createSession(request);
    }

    @GetMapping("/{id}")
    public SimulationSessionResponse getSessionById(@PathVariable("id") UUID id) {
        return sessionService.getSessionById(id);
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageResponse> getSessionMessages(@PathVariable("id") UUID id) {
        return sessionService.getMessagesBySession(id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ChatMessageResponse> createSessionMessages(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return sessionService.createMessages(id, request);
    }
}
