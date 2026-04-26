package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
