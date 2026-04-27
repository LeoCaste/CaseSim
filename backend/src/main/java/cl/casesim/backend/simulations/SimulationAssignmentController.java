package cl.casesim.backend.simulations;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.simulations.dto.CreateSimulationRequest;
import cl.casesim.backend.simulations.dto.CreateSimulationResponse;
import cl.casesim.backend.simulations.dto.StudentActivityResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SimulationAssignmentController {

    private final SimulationAssignmentService simulationAssignmentService;

    public SimulationAssignmentController(SimulationAssignmentService simulationAssignmentService) {
        this.simulationAssignmentService = simulationAssignmentService;
    }

    @PostMapping("/simulations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSimulationResponse createSimulation(
            @Valid @RequestBody CreateSimulationRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return simulationAssignmentService.createSimulation(request, userPrincipal.getId());
    }

    @GetMapping("/student/activities")
    public List<StudentActivityResponse> getStudentActivities(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return simulationAssignmentService.getStudentActivities(userPrincipal.getId());
    }
}
