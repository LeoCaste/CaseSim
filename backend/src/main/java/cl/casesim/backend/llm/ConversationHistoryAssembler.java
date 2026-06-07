package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ConversationHistoryAssembler {

    private final ChatMessageRepository chatMessageRepository;
    private final LlmProperties llmProperties;

    public ConversationHistoryAssembler(
            ChatMessageRepository chatMessageRepository,
            LlmProperties llmProperties
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.llmProperties = llmProperties;
    }

    public List<ChatMessage> loadRecentHistory(UUID sessionId) {
        int historyTurns = Math.max(0, llmProperties.getMaxHistoryMessages());
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
