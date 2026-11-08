package com.example.ug_project.controller;

import com.example.ug_project.service.LlmAgentService;
import com.example.ug_project.service.FreeCadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class CadController {

    private static final Logger log = LoggerFactory.getLogger(CadController.class);

    private final LlmAgentService llmAgentService;
    private final FreeCadService freeCadService;

    public CadController(LlmAgentService llmAgentService, FreeCadService freeCadService) {
        this.llmAgentService = llmAgentService;
        this.freeCadService = freeCadService;
    }

    @PostMapping("/generate-step")
    public ResponseEntity<Resource> generateModel(@RequestBody String jsonPayload) {
        log.info(">>> POST /api/generate-step received (payload: {} chars)", jsonPayload.length());

        // 1. Generate Python code via AI
        log.info("Step 1: Calling LLM to generate FreeCAD Python script...");
        String pythonCode = llmAgentService.generateFreeCadScript(jsonPayload);
        log.info("Step 1 complete. Script length: {} chars", pythonCode.length());

        // 2. Execute locally and return the STEP file
        log.info("Step 2: Executing script with FreeCAD CLI...");
        Resource stepFile = freeCadService.executeScriptAndGetResult(pythonCode);
        log.info("Step 2 complete. Returning STEP file to client.");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"model.step\"")
                .body(stepFile);
    }
}