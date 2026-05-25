package cl.casesim.backend.controller;

import cl.casesim.backend.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return new HealthResponse("OK", "casesim-backend");
    }
}
