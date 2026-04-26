package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClinicalCaseService {

    private final ClinicalCaseRepository clinicalCaseRepository;

    public ClinicalCaseService(ClinicalCaseRepository clinicalCaseRepository) {
        this.clinicalCaseRepository = clinicalCaseRepository;
    }

    public List<ClinicalCaseResponse> getActiveClinicalCases() {
        return clinicalCaseRepository.findByActivoTrueOrderByCreadoEnDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ClinicalCaseResponse getActiveClinicalCaseById(UUID id) {
        ClinicalCase clinicalCase = clinicalCaseRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado con id: " + id));

        return toResponse(clinicalCase);
    }

    private ClinicalCaseResponse toResponse(ClinicalCase clinicalCase) {
        return new ClinicalCaseResponse(
                clinicalCase.getId(),
                clinicalCase.getTitulo(),
                clinicalCase.getDescripcion(),
                clinicalCase.getPacienteNombre(),
                clinicalCase.getPacienteEdad(),
                clinicalCase.getPacienteSexo(),
                clinicalCase.getMotivoConsulta(),
                clinicalCase.getFraseSinInformacion(),
                clinicalCase.isActivo(),
                clinicalCase.getCreadoEn()
        );
    }
}
