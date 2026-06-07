package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.sessions.SessionRevealedFact;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RevealableFactSelectorTest {

    private final ClinicalCaseFactRepository clinicalCaseFactRepository = mock(ClinicalCaseFactRepository.class);
    private final SessionRevealedFactRepository sessionRevealedFactRepository = mock(SessionRevealedFactRepository.class);
    private final SimulationSessionRepository simulationSessionRepository = mock(SimulationSessionRepository.class);
    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);
    private final LlmProperties llmProperties = new LlmProperties();

    private RevealableFactSelector selector;
    private UUID sessionId;
    private UUID activityId;
    private UUID caseId;
    private ClinicalCaseFact level1Fact;
    private ClinicalCaseFact level2Fact;
    private ClinicalCaseFact level3Fact;
    private ClinicalCaseFact temporalFact;
    private ClinicalCaseFact medicationFact;
    private ClinicalCaseFact triggerFact;

    @BeforeEach
    void setUp() {
        llmProperties.setRevealStrategy(RevealStrategy.PROGRESSIVE);
        selector = new RevealableFactSelector(
                clinicalCaseFactRepository,
                sessionRevealedFactRepository,
                simulationSessionRepository,
                simulationActivityRepository,
                llmProperties
        );

        sessionId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        caseId = UUID.randomUUID();

        level1Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "motivo", "Dolor abdominal", 1, null, false, 0);
        level2Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "fiebre", "Tengo fiebre desde ayer", 2, null, false, 1);
        level3Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "antecedente", "Tuve cirugía hace 2 años", 3, null, false, 2);
        temporalFact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "inicio_sintomas", "Comencé con tos seca hace 10 días", 2, null, false, 3);
        medicationFact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "MEDICAMENTOS", "uso_actual", "Tomo losartán todos los días", 2, null, false, 4);
        triggerFact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "habito", "Solo tomo agua ocasionalmente", 2, "[\"sed\",\"hidratacion\"]", false, 5);
    }

    // ========================================================================
    // Tests para selectFactsForPrompt(UUID, String, List<ClinicalCaseFact>)
    // ========================================================================

    @Test
    void initialIncluidoEnSaludo() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "Hola", List.of(level1Fact, level2Fact, level3Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse en saludo");
        assertFalse(result.contains(level2Fact), "Fact nivel 2 no debe incluirse sin match");
        assertFalse(result.contains(level3Fact), "Fact nivel 3 no debe incluirse sin match");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void onQuestionNoIncluidoSinMatch() {
        ClinicalCaseFact onlyOnQuestion = new ClinicalCaseFact(
                UUID.randomUUID(),
                caseId,
                "ANTECEDENTES",
                "alergias",
                "Soy alérgica a la penicilina",
                2,
                "[\"alergia\",\"penicilina\"]",
                false,
                0
        );
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "Hola", List.of(level1Fact, onlyOnQuestion));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse");
        assertFalse(result.contains(onlyOnQuestion), "Fact nivel 2 no debe incluirse sin keyword match en saludo");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void preguntaPorCategoriaRevelaFact() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, medicationFact.getId())).thenReturn(false);

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Qué medicamentos usa?", List.of(level1Fact, medicationFact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse");
        assertTrue(result.contains(medicationFact), "Fact MEDICAMENTOS debe revelarse al preguntar por medicamentos");
        assertTrue(result.indexOf(medicationFact) >= 0, "Medication fact debe estar en la selección");
        verify(sessionRevealedFactRepository).save(any(SessionRevealedFact.class));
    }

    @Test
    void triggerRevelaFactAunqueNoAparezcaEnContenido() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, triggerFact.getId())).thenReturn(false);

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Tiene sed?", List.of(level1Fact, triggerFact));

        assertTrue(result.contains(triggerFact), "Fact con trigger 'sed' debe revelarse al preguntar '¿Tiene sed?'");
        verify(sessionRevealedFactRepository).save(any(SessionRevealedFact.class));
    }

    @Test
    void intencionTemporalRevelaFactTemporal() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, temporalFact.getId())).thenReturn(false);

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Desde cuándo?", List.of(level1Fact, temporalFact));

        assertTrue(result.contains(temporalFact), "Fact temporal debe revelarse al preguntar '¿Desde cuándo?'");
        verify(sessionRevealedFactRepository).save(any(SessionRevealedFact.class));
    }

    @Test
    void intencionTemporalOrdenaFactTemporalPrimero() {
        // Usar un fact NO temporal ya revelado para verificar el ordenamiento
        ClinicalCaseFact nonTemporalFact = new ClinicalCaseFact(
                UUID.randomUUID(), caseId, "GENERAL", "peso", "Mi peso es 70 kilos", 2, null, false, 10
        );
        // nonTemporalFact ya está revelado, así que se incluirá siempre
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of(nonTemporalFact.getId()));
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, temporalFact.getId())).thenReturn(false);

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Desde cuándo?", List.of(level1Fact, nonTemporalFact, temporalFact));

        // Verificar que level1 está primero, y temporalFact antes que nonTemporalFact
        int level1Idx = result.indexOf(level1Fact);
        int temporalIdx = result.indexOf(temporalFact);
        int nonTemporalIdx = result.indexOf(nonTemporalFact);
        assertTrue(level1Idx >= 0, "level1 debe estar presente");
        assertTrue(temporalIdx >= 0, "temporalFact debe estar presente");
        assertTrue(nonTemporalIdx >= 0, "nonTemporalFact debe estar presente");
        // Los facts temporales deben aparecer antes que los no temporales
        assertTrue(temporalIdx < nonTemporalIdx, "Fact temporal debe aparecer antes que fact no-temporal en ordenamiento");
    }

    @Test
    void preguntaIrrelevanteNoRevelaFactsNuevos() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Cómo está el clima?", List.of(level1Fact, level2Fact, level3Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse siempre");
        assertFalse(result.contains(level2Fact), "Fact nivel 2 no debe revelarse con pregunta irrelevante");
        assertFalse(result.contains(level3Fact), "Fact nivel 3 no debe revelarse con pregunta irrelevante");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void factYaReveladoSeMantieneDisponible() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of(level2Fact.getId()));

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Cómo está el clima?", List.of(level1Fact, level2Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse");
        assertTrue(result.contains(level2Fact), "Fact ya revelado debe incluirse aunque la pregunta sea irrelevante");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void directSubeNivelPermitido() {
        llmProperties.setRevealStrategy(RevealStrategy.DIRECT);
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, level3Fact.getId())).thenReturn(false);

        // Con DIRECT, "¿Tuvo cirugía?" permite nivel 3 (nivel base 2 por ? + hints nivel 3 con "cirugia" + 1 por DIRECT = 3)
        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Tuvo cirugía?", List.of(level1Fact, level2Fact, level3Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse");
        assertTrue(result.contains(level3Fact), "Con DIRECT, fact nivel 3 debe revelarse con pregunta que contiene 'cirugia'");
    }

    @Test
    void restrictiveBajaNivelPermitido() {
        llmProperties.setRevealStrategy(RevealStrategy.RESTRICTIVE);
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        // Con RESTRICTIVE, "¿Tiene fiebre?" baja un nivel. Nivel base = 2 (por ?), lvl2 hints "fiebre" -> 2, restrictive -> max(1, 2-1) = 1
        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Tiene fiebre?", List.of(level1Fact, level2Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse");
        assertFalse(result.contains(level2Fact), "Con RESTRICTIVE, fact nivel 2 no debe revelarse incluso con match de keyword");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void persistenciaNoDuplicaRegistros() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        // Simular que el fact ya fue persistido por otro hilo/request
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, level2Fact.getId())).thenReturn(true);

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Tiene fiebre?", List.of(level1Fact, level2Fact));

        assertTrue(result.contains(level2Fact), "Fact nivel 2 debe incluirse por keyword match");
        // existsBySessionIdAndFactId retorna true, entonces NO debe llamar a save
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void persistenciaManejaDataIntegrityViolation() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, level2Fact.getId())).thenReturn(false);
        when(sessionRevealedFactRepository.save(any(SessionRevealedFact.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        // No debe lanzar excepción; debe atrapar DataIntegrityViolationException
        assertDoesNotThrow(() -> {
            List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "¿Tiene fiebre?", List.of(level1Fact, level2Fact));
            assertTrue(result.contains(level2Fact), "Fact debe incluirse aunque la persistencia falle");
        });
        verify(sessionRevealedFactRepository).save(any(SessionRevealedFact.class));
    }

    @Test
    void mensajeVacioONullNoRompeSeleccion() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        // Mensaje null
        List<ClinicalCaseFact> resultNull = selector.selectFactsForPrompt(sessionId, null, List.of(level1Fact, level2Fact));
        assertTrue(resultNull.contains(level1Fact), "Con mensaje null, fact nivel 1 debe incluirse");
        assertFalse(resultNull.contains(level2Fact), "Con mensaje null, fact nivel 2 no debe incluirse");

        // Mensaje vacío
        List<ClinicalCaseFact> resultEmpty = selector.selectFactsForPrompt(sessionId, "", List.of(level1Fact, level2Fact));
        assertTrue(resultEmpty.contains(level1Fact), "Con mensaje vacío, fact nivel 1 debe incluirse");
        assertFalse(resultEmpty.contains(level2Fact), "Con mensaje vacío, fact nivel 2 no debe incluirse");
    }

    @Test
    void factsNulosOVaciosRetornaListaVacia() {
        List<ClinicalCaseFact> resultNull = selector.selectFactsForPrompt(sessionId, "Hola", null);
        assertTrue(resultNull.isEmpty(), "Con facts null debe retornar lista vacía");

        List<ClinicalCaseFact> resultEmpty = selector.selectFactsForPrompt(sessionId, "Hola", List.of());
        assertTrue(resultEmpty.isEmpty(), "Con facts vacío debe retornar lista vacía");
    }

    // ========================================================================
    // Tests para métodos de apoyo internos (package-private)
    // ========================================================================

    @Test
    void resolveAllowedRevealLevelConSaludoDevuelveUno() {
        assertEquals(1, selector.resolveAllowedRevealLevel("Hola", RevealStrategy.PROGRESSIVE));
    }

    @Test
    void resolveAllowedRevealLevelConPreguntaDevuelveDos() {
        assertEquals(2, selector.resolveAllowedRevealLevel("¿Tiene fiebre?", RevealStrategy.PROGRESSIVE));
    }

    @Test
    void resolveAllowedRevealLevelConHintNivel3DevuelveTres() {
        assertEquals(3, selector.resolveAllowedRevealLevel("¿Tiene antecedentes?", RevealStrategy.PROGRESSIVE));
    }

    @Test
    void resolveAllowedRevealLevelDirectSumaUno() {
        assertEquals(2, selector.resolveAllowedRevealLevel("Hola", RevealStrategy.DIRECT));
    }

    @Test
    void resolveAllowedRevealLevelRestrictiveRestaUno() {
        assertEquals(1, selector.resolveAllowedRevealLevel("¿Tiene fiebre?", RevealStrategy.RESTRICTIVE));
    }

    @Test
    void extractKeywordsFiltraTokensCortos() {
        Set<String> keywords = selector.extractKeywords("¿Cómo está el clima hoy?");
        assertTrue(keywords.contains("como"), "'como' debe ser keyword (>=3 chars)");
        assertTrue(keywords.contains("esta"), "'esta' debe ser keyword (>=3 chars)");
        assertTrue(keywords.contains("clima"), "'clima' debe ser keyword (>=3 chars)");
        assertTrue(keywords.contains("hoy"), "'hoy' debe ser keyword (==3 chars)");
        assertFalse(keywords.contains("el"), "'el' no debe ser keyword (<3 chars)");
    }

    @Test
    void extractKeywordsConMensajeVacioRetornaVacio() {
        assertTrue(selector.extractKeywords("").isEmpty());
        assertTrue(selector.extractKeywords(null).isEmpty());
    }

    @Test
    void matchesFactByKeywordBuscaEnNombreCategoriaContenidoYTriggers() {
        Set<String> keywords = Set.of("sed", "fiebre");
        assertTrue(selector.matchesFactByKeyword(triggerFact, keywords),
                "Debe matchear por trigger 'sed'");
        assertTrue(selector.matchesFactByKeyword(level2Fact, keywords),
                "Debe matchear por contenido 'fiebre'");
    }

    @Test
    void isTemporalIntentDetectaPreguntaTemporal() {
        assertTrue(selector.isTemporalIntent("¿Desde cuándo?"));
        assertTrue(selector.isTemporalIntent("¿Hace cuánto?"));
        assertTrue(selector.isTemporalIntent("¿Cuánto tiempo?"));
        assertFalse(selector.isTemporalIntent("¿Cómo está?"));
        assertFalse(selector.isTemporalIntent(""));
        assertFalse(selector.isTemporalIntent(null));
    }

    @Test
    void isTemporalFactDetectaFactConInformacionTemporal() {
        assertTrue(selector.isTemporalFact(temporalFact), "Fact con 'inicio' en nombre debe ser temporal");
        assertTrue(selector.isTemporalFact(level2Fact), "Fact con 'desde' en contenido debe ser temporal");
        assertFalse(selector.isTemporalFact(level1Fact), "Fact sin contenido temporal no debe marcarse como temporal");
    }

    @Test
    void preguntaAmpliaCuentameTodoNoRevelaNivel2SinMatch() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "cuéntame todo", List.of(level1Fact, level2Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse siempre");
        assertFalse(result.contains(level2Fact), "Fact nivel 2 no debe revelarse con 'cuéntame todo' sin keyword match");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void preguntaAmpliaExplícameTodoNoRevelaNivel2() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId)).thenReturn(Set.of());

        List<ClinicalCaseFact> result = selector.selectFactsForPrompt(sessionId, "explícame todo", List.of(level1Fact, level2Fact));

        assertTrue(result.contains(level1Fact), "Fact nivel 1 debe incluirse siempre");
        assertFalse(result.contains(level2Fact), "Fact nivel 2 no debe revelarse con 'explícame todo' sin keyword match");
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void containsAnyTokenDetectaHintEnTextoNormalizado() {
        assertTrue(selector.containsAnyToken("tengo fiebre", List.of("fiebre", "tos")));
        assertTrue(selector.containsAnyToken("dolor de cabeza", List.of("dolor")));
        assertFalse(selector.containsAnyToken("bien gracias", List.of("fiebre", "tos")));
    }
}
