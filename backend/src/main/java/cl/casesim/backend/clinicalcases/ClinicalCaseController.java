package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.auth.UserPrincipal;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinical-cases")
public class ClinicalCaseController {

    private final ClinicalCaseService clinicalCaseService;

    public ClinicalCaseController(ClinicalCaseService clinicalCaseService) {
        this.clinicalCaseService = clinicalCaseService;
    }

    @GetMapping
    public List<ClinicalCaseResponse> getClinicalCases() {
        return clinicalCaseService.getActiveClinicalCases();
    }

    @GetMapping("/{id}")
    public ClinicalCaseResponse getClinicalCaseById(@PathVariable UUID id) {
        return clinicalCaseService.getActiveClinicalCaseById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalCaseResponse createClinicalCase(
            @Valid @RequestBody ClinicalCaseRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return clinicalCaseService.createClinicalCase(request, userPrincipal.getId());
    }

    @PutMapping("/{id}")
    public ClinicalCaseResponse updateClinicalCase(
            @PathVariable UUID id,
            @Valid @RequestBody ClinicalCaseRequest request
    ) {
        return clinicalCaseService.updateClinicalCase(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateClinicalCase(@PathVariable UUID id) {
        clinicalCaseService.deactivateClinicalCase(id);
    }
}
