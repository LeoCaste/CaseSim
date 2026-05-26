package cl.casesim.backend.llm;

import cl.casesim.backend.llm.provider.gemini.*;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.PatientResponseService;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    @Bean
    public PromptBuilderService promptBuilderService() {
        return new PromptBuilderService();
    }

    @Bean
    public ResponseSafetyFilter responseSafetyFilter() {
        return new ResponseSafetyFilter();
    }

    @Bean
    public LlmProviderUrlResolver llmProviderUrlResolver() {
        return new LlmProviderUrlResolver();
    }

    @Bean
    public LlmProviderErrorMapper llmProviderErrorMapper() {
        return new LlmProviderErrorMapper();
    }

    @Bean
    public OpenAiLlmClient openAiLlmClient(
            LlmProperties llmProperties,
            LlmProviderUrlResolver urlResolver,
            LlmProviderErrorMapper errorMapper
    ) {
        return new OpenAiLlmClient(llmProperties, urlResolver, errorMapper);
    }

    @Bean
    public GroqLlmClient groqLlmClient(
            LlmProperties llmProperties,
            LlmProviderUrlResolver urlResolver,
            LlmProviderErrorMapper errorMapper
    ) {
        return new GroqLlmClient(llmProperties, urlResolver, errorMapper);
    }

    @Bean
    public GeminiRequestMapper geminiRequestMapper() {
        return new GeminiRequestMapper();
    }

    @Bean
    public GeminiResponseMapper geminiResponseMapper() {
        return new GeminiResponseMapper();
    }

    @Bean
    public GeminiErrorMapper geminiErrorMapper(LlmProviderErrorMapper errorMapper) {
        return new GeminiErrorMapper(errorMapper);
    }

    @Bean
    public GeminiLlmClient geminiLlmClient(
            LlmProperties llmProperties,
            LlmProviderUrlResolver urlResolver,
            GeminiRequestMapper requestMapper,
            GeminiResponseMapper responseMapper,
            GeminiErrorMapper errorMapper
    ) {
        return new GeminiLlmClient(llmProperties, urlResolver, requestMapper, responseMapper, errorMapper);
    }

    @Bean
    public AnthropicLlmClient anthropicLlmClient(
            LlmProperties llmProperties,
            LlmProviderUrlResolver urlResolver,
            LlmProviderErrorMapper errorMapper
    ) {
        return new AnthropicLlmClient(llmProperties, urlResolver, errorMapper);
    }

    @Bean
    public LlmClient llmClient(
            LlmProperties llmProperties,
            OpenAiLlmClient openAiLlmClient,
            GroqLlmClient groqLlmClient,
            GeminiLlmClient geminiLlmClient,
            AnthropicLlmClient anthropicLlmClient
    ) {
        return new LlmClientRouter(llmProperties, java.util.List.of(openAiLlmClient, groqLlmClient, geminiLlmClient, anthropicLlmClient));
    }

    @Bean
    public LlmPatientResponseService llmPatientResponseService(
            LlmProperties llmProperties,
            LlmClient llmClient,
            PromptBuilderService promptBuilderService,
            ResponseSafetyFilter responseSafetyFilter,
            ChatMessageRepository chatMessageRepository,
            LlmUsageService llmUsageService,
            SimulationActivityRepository simulationActivityRepository,
            SimulationSessionRepository simulationSessionRepository,
            ClinicalCaseRepository clinicalCaseRepository,
            ClinicalCaseFactRepository clinicalCaseFactRepository,
            ClinicalCasePersonalityRepository clinicalCasePersonalityRepository,
            SessionRevealedFactRepository sessionRevealedFactRepository
    ) {
        return new LlmPatientResponseService(
                llmProperties,
                llmClient,
                promptBuilderService,
                responseSafetyFilter,
                chatMessageRepository,
                llmUsageService,
                simulationActivityRepository,
                simulationSessionRepository,
                clinicalCaseRepository,
                clinicalCaseFactRepository,
                clinicalCasePersonalityRepository,
                sessionRevealedFactRepository
        );
    }

    @Bean
    public PatientResponseService patientResponseService(
            LlmPatientResponseService llmPatientResponseService
    ) {
        return llmPatientResponseService;
    }
}
