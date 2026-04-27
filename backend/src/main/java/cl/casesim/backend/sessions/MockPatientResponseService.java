package cl.casesim.backend.sessions;

import org.springframework.stereotype.Service;

@Service
public class MockPatientResponseService implements PatientResponseService {

    private static final String DEFAULT_REPLY = "Entiendo. Cuénteme un poco más sobre eso.";

    @Override
    public String generateResponse(SimulationSession session, String userMessage) {
        return DEFAULT_REPLY;
    }
}
