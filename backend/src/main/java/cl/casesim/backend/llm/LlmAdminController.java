package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.LlmProviderModelsResponse;
import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import cl.casesim.backend.llm.dto.UpdateLlmConfigRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm")
public class LlmAdminController {

    private static final Logger log = LoggerFactory.getLogger(LlmAdminController.class);

    private final LlmAdminService llmAdminService;
    private final LlmUsageService llmUsageService;

    public LlmAdminController(LlmAdminService llmAdminService, LlmUsageService llmUsageService) {
        this.llmAdminService = llmAdminService;
        this.llmUsageService = llmUsageService;
    }

    @GetMapping("/config")
    public LlmConfigResponse getConfig() {
        return llmAdminService.getConfig();
    }

    @GetMapping("/models")
    public List<LlmProviderModelsResponse> getAvailableModels(
            @RequestParam(required = false) String provider
    ) {
        return llmAdminService.getAvailableModels(provider);
    }

    @PutMapping("/config")
    public LlmConfigResponse updateConfig(@Valid @RequestBody UpdateLlmConfigRequest request) {
        return llmAdminService.updateConfig(request);
    }

    @DeleteMapping("/config/api-key")
    public LlmConfigResponse deleteApiKey() {
        return llmAdminService.deleteApiKey();
    }

    @PostMapping(path = {"/test-connection", "/test-connection/", "/testConnection", "/testConnection/"})
    public ResponseEntity<TestConnectionResponse> testConnection() {
        log.debug("LLM admin test-connection endpoint called");
        TestConnectionResponse response = llmAdminService.testConnection();
        Integer status = response.httpStatus();
        if (status == null || status < 100 || status > 599) {
            status = response.success() ? 200 : 500;
        }
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/usage")
    public List<LlmUsageDailyResponse> getUsage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status
    ) {
        return llmUsageService.getDailyUsage(from, to, model, status);
    }

    @GetMapping("/summary")
    public LlmSummaryResponse getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status
    ) {
        return llmUsageService.getSummary(from, to, model, status);
    }
}
