package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.MockPatientResponseService;
import cl.casesim.backend.sessions.PatientResponseService;
import cl.casesim.backend.sessions.SimulationSession;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.List;

public class LlmPatientResponseService implements PatientResponseService {

    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final PromptBuilderService promptBuilderService;
    private final ResponseSafetyFilter responseSafetyFilter;
    private final ChatMessageRepository chatMessageRepository;
    private final MockPatientResponseService mockPatientResponseService;
    private final LlmUsageService llmUsageService;

    public LlmPatientResponseService(
            LlmProperties llmProperties,
            LlmClient llmClient,
            PromptBuilderService promptBuilderService,
            ResponseSafetyFilter responseSafetyFilter,
            ChatMessageRepository chatMessageRepository,
            MockPatientResponseService mockPatientResponseService,
            LlmUsageService llmUsageService
    ) {
        this.llmProperties = llmProperties;
        this.llmClient = llmClient;
        this.promptBuilderService = promptBuilderService;
        this.responseSafetyFilter = responseSafetyFilter;
        this.chatMessageRepository = chatMessageRepository;
        this.mockPatientResponseService = mockPatientResponseService;
        this.llmUsageService = llmUsageService;
    }

    @Override
    public String generateResponse(SimulationSession session, String userMessage) {
        if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
            llmUsageService.markFallbackCall();
            return mockPatientResponseService.generateResponse(session, userMessage);
        }

        try {
            List<ChatMessage> history = loadRecentHistory(session.getId());

            List<LlmClient.ChatPromptMessage> promptMessages = promptBuilderService.buildMessages(
                    session,
                    history,
                    userMessage
            );

            llmUsageService.markLlmCall();
            String llmResponse = llmClient.generateChatCompletion(promptMessages);
            String safeResponse = responseSafetyFilter.applyOrFallback(llmResponse);

            if (ResponseSafetyFilter.SAFE_FALLBACK.equals(safeResponse)) {
                llmUsageService.markFallbackCall();
            }

            return safeResponse;
        } catch (RuntimeException ex) {
            llmUsageService.markFallbackCall();
            return mockPatientResponseService.generateResponse(session, userMessage);
        }
    }

    private List<ChatMessage> loadRecentHistory(java.util.UUID sessionId) {
        int historyTurns = Math.max(0, llmProperties.getHistoryTurns());
        if (historyTurns == 0) {
            return List.of();
        }

        return chatMessageRepository
                .findBySesionIdOrderByNumeroTurnoDesc(sessionId, PageRequest.of(0, historyTurns))
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getNumeroTurno))
                .toList();
    }
}
