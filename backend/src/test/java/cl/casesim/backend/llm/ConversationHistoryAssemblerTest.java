package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHistoryAssemblerTest {

    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private LlmProperties properties;
    private ConversationHistoryAssembler assembler;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setMaxHistoryMessages(3);
        assembler = new ConversationHistoryAssembler(chatMessageRepository, properties);
    }

    @Test
    void retornaHistorialRecienteEnOrdenAscendentePorNumeroTurno() {
        UUID sessionId = UUID.randomUUID();
        ChatMessage assistant = message(sessionId, "ASSISTANT", "Me duele", 4);
        ChatMessage user = message(sessionId, "USER", "Hola", 3);
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(assistant, user));

        List<ChatMessage> history = assembler.loadRecentHistory(sessionId);

        assertEquals(List.of(user, assistant), history);
    }

    @Test
    void respetaLimiteConfiguradoYUsaPageRequestEsperado() {
        UUID sessionId = UUID.randomUUID();
        properties.setMaxHistoryMessages(5);
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        assembler.loadRecentHistory(sessionId);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository).findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    @Test
    void conservaRolesContenidosYMismosObjetos() {
        UUID sessionId = UUID.randomUUID();
        ChatMessage user = message(sessionId, "USER", "¿Qué siente?", 1);
        ChatMessage assistant = message(sessionId, "ASSISTANT", "Dolor abdominal", 2);
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(assistant, user));

        List<ChatMessage> history = assembler.loadRecentHistory(sessionId);

        assertSame(user, history.get(0));
        assertSame(assistant, history.get(1));
        assertEquals("USER", history.get(0).getRol());
        assertEquals("¿Qué siente?", history.get(0).getContenido());
        assertEquals("ASSISTANT", history.get(1).getRol());
        assertEquals("Dolor abdominal", history.get(1).getContenido());
    }

    @Test
    void manejaSesionSinMensajes() {
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        List<ChatMessage> history = assembler.loadRecentHistory(sessionId);

        assertEquals(List.of(), history);
    }

    @Test
    void llamaRepositorioConSessionIdExacto() {
        UUID sessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        assembler.loadRecentHistory(sessionId);

        verify(chatMessageRepository).findBySesionIdOrderByNumeroTurnoDesc(eq(sessionId), org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(chatMessageRepository, never()).findBySesionIdOrderByNumeroTurnoDesc(eq(otherSessionId), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void maxHistoryMessagesNoPositivoRetornaListaVaciaSinConsultarRepositorio() {
        properties.setMaxHistoryMessages(0);

        List<ChatMessage> history = assembler.loadRecentHistory(UUID.randomUUID());

        assertEquals(List.of(), history);
        verify(chatMessageRepository, never()).findBySesionIdOrderByNumeroTurnoDesc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void maxHistoryMessagesNegativoRetornaListaVaciaSinConsultarRepositorio() {
        properties.setMaxHistoryMessages(-1);

        List<ChatMessage> history = assembler.loadRecentHistory(UUID.randomUUID());

        assertEquals(List.of(), history);
        verify(chatMessageRepository, never()).findBySesionIdOrderByNumeroTurnoDesc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    private ChatMessage message(UUID sessionId, String role, String content, int turn) {
        return new ChatMessage(UUID.randomUUID(), sessionId, role, content, turn, LocalDateTime.now());
    }
}
