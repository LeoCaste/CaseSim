package cl.casesim.backend.llm;

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
    public LlmClient llmClient(LlmProperties llmProperties) {
        return new OpenAiLlmClient(llmProperties);
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
