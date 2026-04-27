package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import cl.casesim.backend.llm.dto.UpdateLlmConfigRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm")
public class LlmAdminController {

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

    @PutMapping("/config")
    public LlmConfigResponse updateConfig(@Valid @RequestBody UpdateLlmConfigRequest request) {
        return llmAdminService.updateConfig(request);
    }

    @PostMapping("/test-connection")
    public TestConnectionResponse testConnection() {
        return llmAdminService.testConnection();
    }

    @GetMapping("/usage")
    public List<LlmUsageDailyResponse> getUsage() {
        return llmUsageService.getDailyUsage();
    }

    @GetMapping("/summary")
    public LlmSummaryResponse getSummary() {
        return llmUsageService.getSummary();
    }
}
