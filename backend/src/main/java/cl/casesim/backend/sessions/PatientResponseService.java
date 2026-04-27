package cl.casesim.backend.sessions;

public interface PatientResponseService {

    String generateResponse(SimulationSession session, String userMessage);
}
