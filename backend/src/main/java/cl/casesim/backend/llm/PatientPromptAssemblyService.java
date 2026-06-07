package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;

import java.util.List;

/**
 * Servicio dedicado al ensamblaje de mensajes del prompt para la simulación de paciente.
 * <p>
 * Coordina el uso de {@link PromptBuilderService} para construir los mensajes finales
 * que se entregan al provider gateway, y calcula métricas asociadas al prompt
 * (tokens estimados, caracteres).
 * </p>
 * 
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Construir la lista de mensajes {@link LlmMessage} usando {@link PromptBuilderService}</li>
 *   <li>Calcular {@code estimatedPromptTokens} y {@code promptChars} a partir de los mensajes</li>
 *   <li>Retornar un {@link PromptAssemblyResult} con los mensajes y sus métricas</li>
 * </ul>
 * 
 * <p>NO modifica el contenido del prompt, el orden de mensajes ni la lógica clínica.</p>
 */
public class PatientPromptAssemblyService {

    private final PromptBuilderService promptBuilderService;
    private final LlmInteractionMetricsService llmInteractionMetricsService;

    public PatientPromptAssemblyService(
            PromptBuilderService promptBuilderService,
            LlmInteractionMetricsService llmInteractionMetricsService
    ) {
        this.promptBuilderService = promptBuilderService;
        this.llmInteractionMetricsService = llmInteractionMetricsService;
    }

    /**
     * Ensambla los mensajes del prompt a partir del contexto clínico, historial,
     * mensaje del usuario y configuración de comportamiento.
     *
     * @param context       contexto clínico armado previamente
     * @param history       historial conversacional reciente
     * @param userMessage   mensaje actual del estudiante
     * @param behaviorConfig configuración de comportamiento del paciente simulado
     * @return resultado del ensamblaje con mensajes + tokens estimados + caracteres
     */
    public PromptAssemblyResult assemblePrompt(
            PromptBuilderService.ClinicalPromptContext context,
            List<ChatMessage> history,
            String userMessage,
            PromptBuilderService.PatientBehaviorConfig behaviorConfig
    ) {
        List<LlmMessage> promptMessages = promptBuilderService.buildMessages(
                context, history, userMessage, behaviorConfig
        );

        int estimatedPromptTokens = promptMessages.stream()
                .map(LlmMessage::content)
                .mapToInt(llmInteractionMetricsService::estimateTokens)
                .sum();
        int promptChars = promptMessages.stream()
                .map(LlmMessage::content)
                .mapToInt(content -> content == null ? 0 : content.length())
                .sum();

        return new PromptAssemblyResult(promptMessages, estimatedPromptTokens, promptChars);
    }

    /**
     * Resultado del ensamblaje de un prompt.
     *
     * @param promptMessages         lista de mensajes {@link LlmMessage} para enviar al LLM
     * @param estimatedPromptTokens  suma de tokens estimados para todos los mensajes
     * @param promptChars            suma de caracteres de todos los mensajes
     */
    public record PromptAssemblyResult(
            List<LlmMessage> promptMessages,
            int estimatedPromptTokens,
            int promptChars
    ) {
    }
}
