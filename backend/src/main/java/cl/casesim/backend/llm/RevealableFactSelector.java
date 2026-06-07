package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.sessions.SessionRevealedFact;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cl.casesim.backend.llm.TextNormalizationUtil.hasText;
import static cl.casesim.backend.llm.TextNormalizationUtil.normalize;

/**
 * Servicio responsable de seleccionar qué hechos clínicos (facts) son revelables
 * en función del mensaje del usuario, el nivel de revelación permitido, la estrategia
 * de revelación, los facts previamente revelados y los triggers/configuración de cada fact.
 */
@Service
public class RevealableFactSelector {

    private static final Logger log = LoggerFactory.getLogger(RevealableFactSelector.class);

    private static final List<String> LEVEL_2_HINTS = List.of("como", "donde", "cuando", "cuanto", "tiene", "siente", "dolor", "fiebre", "tos", "sintoma", "vomito", "nausea");
    private static final List<String> LEVEL_3_HINTS = List.of("desde", "antecedente", "alergia", "medicamento", "cirugia", "hospital", "laboratorio", "examen", "resultado", "familiar", "cronico", "tratamiento");
    private static final List<String> TEMPORAL_HINTS = List.of("desde cuando", "hace cuanto", "inicio", "empezo", "comenzo", "cuanto tiempo", "desde");

    private final ClinicalCaseFactRepository clinicalCaseFactRepository;
    private final SessionRevealedFactRepository sessionRevealedFactRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final SimulationActivityRepository simulationActivityRepository;
    private final LlmProperties llmProperties;

    public RevealableFactSelector(
            ClinicalCaseFactRepository clinicalCaseFactRepository,
            SessionRevealedFactRepository sessionRevealedFactRepository,
            SimulationSessionRepository simulationSessionRepository,
            SimulationActivityRepository simulationActivityRepository,
            LlmProperties llmProperties
    ) {
        this.clinicalCaseFactRepository = clinicalCaseFactRepository;
        this.sessionRevealedFactRepository = sessionRevealedFactRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.simulationActivityRepository = simulationActivityRepository;
        this.llmProperties = llmProperties;
    }

    /**
     * Selecciona los facts revelables para una sesión y mensaje de usuario.
     * Carga la sesión, la actividad asociada y los facts del caso clínico,
     * luego delega al método de selección interno.
     *
     * @param sessionId   identificador de la sesión
     * @param userMessage mensaje del usuario
     * @return lista de facts revelables (nunca null)
     */
    public List<ClinicalCaseFact> selectFactsForPrompt(UUID sessionId, String userMessage) {
        SimulationSession session = simulationSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return List.of();
        }

        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId()).orElse(null);
        if (activity == null) {
            return List.of();
        }

        List<ClinicalCaseFact> allFacts = clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(activity.getCasoId());
        return selectFactsForPrompt(sessionId, userMessage, allFacts);
    }

    /**
     * Núcleo de selección de facts. Dado un listado completo de facts del caso,
     * determina cuáles deben incluirse en el prompt según:
     * <ul>
     *   <li>Nivel de revelación del fact vs. nivel permitido</li>
     *   <li>Facts ya revelados en la sesión</li>
     *   <li>Coincidencia por keywords entre el mensaje del usuario y el fact</li>
     *   <li>Intención temporal del mensaje</li>
     * </ul>
     * Además, persiste los facts recién revelados.
     *
     * @param sessionId   identificador de la sesión
     * @param userMessage mensaje del usuario
     * @param allFacts    listado completo de facts del caso clínico
     * @return lista de facts seleccionados para el prompt
     */
    List<ClinicalCaseFact> selectFactsForPrompt(UUID sessionId, String userMessage, List<ClinicalCaseFact> allFacts) {
        if (allFacts == null || allFacts.isEmpty()) {
            return List.of();
        }

        Set<UUID> alreadyRevealedIds = new HashSet<>(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId));
        Set<String> messageKeywords = extractKeywords(userMessage);
        boolean temporalIntent = isTemporalIntent(userMessage);
        RevealStrategy revealStrategy = llmProperties.getRevealStrategy() == null ? RevealStrategy.PROGRESSIVE : llmProperties.getRevealStrategy();
        int allowedRevealLevel = resolveAllowedRevealLevel(userMessage, revealStrategy);

        List<ClinicalCaseFact> selectedFacts = new ArrayList<>();
        List<ClinicalCaseFact> newlyRevealedFacts = new ArrayList<>();

        for (ClinicalCaseFact fact : allFacts) {
            int factRevealLevel = fact.getNivelRevelacion() == null ? 1 : fact.getNivelRevelacion();
            boolean includeByDefault = factRevealLevel == 1;
            boolean includeAsPreviouslyRevealed = alreadyRevealedIds.contains(fact.getId());

            boolean includeAsNewReveal = false;
            if (!includeByDefault && !includeAsPreviouslyRevealed && factRevealLevel <= allowedRevealLevel) {
                includeAsNewReveal = matchesFactByKeyword(fact, messageKeywords);
                if (!includeAsNewReveal && temporalIntent && isTemporalFact(fact)) {
                    includeAsNewReveal = true;
                }
            }

            if (includeByDefault || includeAsPreviouslyRevealed || includeAsNewReveal) {
                selectedFacts.add(fact);
            }

            if (includeAsNewReveal) {
                newlyRevealedFacts.add(fact);
                alreadyRevealedIds.add(fact.getId());
            }
        }

        if (temporalIntent && !selectedFacts.isEmpty()) {
            selectedFacts = selectedFacts.stream()
                    .sorted(Comparator.comparing((ClinicalCaseFact fact) -> !isTemporalFact(fact)))
                    .collect(Collectors.toList());
        }

        persistNewlyRevealedFacts(sessionId, newlyRevealedFacts);
        return selectedFacts;
    }

    /**
     * Persiste los facts recién revelados en la sesión, evitando duplicados
     * de forma defensiva ante concurrencia.
     */
    void persistNewlyRevealedFacts(UUID sessionId, List<ClinicalCaseFact> newlyRevealedFacts) {
        if (newlyRevealedFacts == null || newlyRevealedFacts.isEmpty()) {
            return;
        }

        for (ClinicalCaseFact fact : newlyRevealedFacts) {
            if (sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, fact.getId())) {
                continue;
            }

            try {
                sessionRevealedFactRepository.save(new SessionRevealedFact(
                        UUID.randomUUID(),
                        sessionId,
                        fact.getId(),
                        LocalDateTime.now()
                ));
            } catch (DataIntegrityViolationException ignored) {
                // Protección defensiva ante concurrencia o duplicados.
            }
        }
    }

    /**
     * Resuelve el nivel máximo de revelación permitido según el mensaje del usuario
     * y la estrategia de revelación configurada.
     */
    int resolveAllowedRevealLevel(String userMessage, RevealStrategy revealStrategy) {
        String normalized = normalize(userMessage);
        if (normalized.isEmpty()) {
            return revealStrategy == RevealStrategy.DIRECT ? 2 : 1;
        }

        int allowedLevel = userMessage != null && userMessage.contains("?") ? 2 : 1;
        if (containsAnyToken(normalized, LEVEL_2_HINTS)) {
            allowedLevel = Math.max(allowedLevel, 2);
        }

        if (containsAnyToken(normalized, LEVEL_3_HINTS)) {
            allowedLevel = 3;
        }

        if (revealStrategy == RevealStrategy.DIRECT) {
            allowedLevel = Math.min(3, allowedLevel + 1);
        } else if (revealStrategy == RevealStrategy.RESTRICTIVE) {
            allowedLevel = Math.max(1, allowedLevel - 1);
        }

        return allowedLevel;
    }

    /**
     * Determina si un fact coincide con las keywords extraídas del mensaje del usuario.
     * Busca en nombre, categoría, contenido paciente y triggers del fact.
     */
    boolean matchesFactByKeyword(ClinicalCaseFact fact, Set<String> messageKeywords) {
        if (messageKeywords == null || messageKeywords.isEmpty()) {
            return false;
        }

        String normalizedFactText = normalize((fact.getNombre() == null ? "" : fact.getNombre()) + " " +
                (fact.getCategoria() == null ? "" : fact.getCategoria()) + " " +
                (fact.getContenidoPaciente() == null ? "" : fact.getContenidoPaciente()) + " " +
                (fact.getTriggers() == null ? "" : fact.getTriggers()));

        if (normalizedFactText.isEmpty()) {
            return false;
        }

        for (String keyword : messageKeywords) {
            if (normalizedFactText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extrae keywords relevantes del mensaje del usuario (tokens de 3+ caracteres).
     */
    Set<String> extractKeywords(String userMessage) {
        String normalized = normalize(userMessage);
        if (normalized.isEmpty()) {
            return Set.of();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Verifica si el mensaje normalizado contiene algún token de la lista de pistas.
     */
    boolean containsAnyToken(String normalizedMessage, List<String> hints) {
        return hints.stream().anyMatch(normalizedMessage::contains);
    }

    /**
     * Detecta si el mensaje del usuario expresa intención temporal
     * (preguntas sobre cuándo, desde cuándo, duración, etc.).
     */
    boolean isTemporalIntent(String userMessage) {
        String normalized = normalize(userMessage);
        if (!hasText(normalized)) {
            return false;
        }
        return TEMPORAL_HINTS.stream().anyMatch(normalized::contains);
    }

    /**
     * Determina si un fact contiene información de tipo temporal
     * (inicio, duración, tiempo, etc.) en su nombre, contenido o triggers.
     */
    boolean isTemporalFact(ClinicalCaseFact fact) {
        if (fact == null) {
            return false;
        }
        String factText = normalize((fact.getNombre() == null ? "" : fact.getNombre()) + " "
                + (fact.getContenidoPaciente() == null ? "" : fact.getContenidoPaciente()) + " "
                + (fact.getTriggers() == null ? "" : fact.getTriggers()));
        if (!hasText(factText)) {
            return false;
        }
        return factText.contains("inicio")
                || factText.contains("desde")
                || factText.contains("hace")
                || factText.contains("duracion")
                || factText.contains("tiempo");
    }
}
