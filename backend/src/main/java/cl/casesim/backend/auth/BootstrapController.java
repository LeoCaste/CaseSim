package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import cl.casesim.backend.auth.dto.BootstrapAdminStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

    private final AuthBootstrapService authBootstrapService;

    public BootstrapController(AuthBootstrapService authBootstrapService) {
        this.authBootstrapService = authBootstrapService;
    }

    @GetMapping("/admin/status")
    public BootstrapAdminStatusResponse adminStatus() {
        return new BootstrapAdminStatusResponse(authBootstrapService.adminExists());
    }

    @PostMapping("/admin")
    public ResponseEntity<Void> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        authBootstrapService.bootstrapAdmin(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
