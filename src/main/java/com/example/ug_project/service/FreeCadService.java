package com.example.ug_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FreeCadService {

    private static final Logger log = LoggerFactory.getLogger(FreeCadService.class);

    @Value("${freecad.path}")
    private String freecadPath;

    private final LlmAgentService llmAgentService;

    public FreeCadService(LlmAgentService llmAgentService) {
        this.llmAgentService = llmAgentService;
    }

    public Resource executeScriptAndGetResult(String initialPythonCode) {
        log.info("=== Starting Dual-Agent FreeCAD Pipeline ===");
        try {
            Path scriptDir = Paths.get("scripts");
            Path outputDir = Paths.get("output");
            if (!Files.exists(scriptDir)) Files.createDirectories(scriptDir);
            if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

            Path scriptPath = scriptDir.resolve("generator.py");
            Path outputPath = outputDir.resolve("model.step");

            String activeCode = initialPythonCode;
            int maxRetries = 2;
            boolean success = false;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                log.info("--- Attempt {}/{} ---", attempt, maxRetries);
                log.debug("Writing Python script to: {}", scriptPath.toAbsolutePath());
                Files.writeString(scriptPath, activeCode);
                Files.deleteIfExists(outputPath);

                // --console runs FreeCAD in headless CLI mode (no GUI window)
                // Redirect stdin from /dev/null so FreeCAD gets EOF immediately and
                // exits after the script finishes instead of waiting for interactive input.
                log.info("Launching FreeCAD CLI: {} --console {}", freecadPath, scriptPath.toAbsolutePath());
                ProcessBuilder pb = new ProcessBuilder(
                        freecadPath,
                        "--console",
                        scriptPath.toAbsolutePath().toString()
                );
                pb.redirectErrorStream(true);
                pb.redirectInput(new java.io.File("/dev/null")); // send EOF to stdin → no interactive hang
                Process process = pb.start();

                // Stream and capture FreeCAD output, filtering startup noise
                StringBuilder outputLog = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLog.append(line).append("\n");
                        if (isEnvVarLine(line) || isIgnoredStartupLine(line)) {
                            log.trace("[FreeCAD] {}", line); // env vars / startup noise → TRACE only
                        } else {
                            log.info("[FreeCAD] {}", line);
                        }
                    }
                }

                int exitCode = process.waitFor();
                log.info("FreeCAD process exited with code: {}", exitCode);

                if (Files.exists(outputPath) && outputPath.toFile().length() > 0) {
                    log.info("SUCCESS: STEP file generated at {} ({} bytes)",
                            outputPath.toAbsolutePath(), outputPath.toFile().length());
                    success = true;
                    break;
                } else {
                    log.warn("STEP file not produced on attempt {}. FreeCAD output:\n{}", attempt, outputLog);
                    if (attempt < maxRetries) {
                        log.info("Passing FreeCAD error to Correction Agent (Attempt {})...", attempt);
                        activeCode = llmAgentService.correctFreeCadScript(activeCode, outputLog.toString());
                        log.debug("Correction Agent returned new script ({} chars)", activeCode.length());
                    }
                }
            }

            if (!success || !Files.exists(outputPath)) {
                log.error("Both Creator and Correction agents failed to produce a valid STEP file after {} attempts.", maxRetries);
                throw new RuntimeException("Both Creator and Correction agents failed to produce a valid STEP file.");
            }

            log.info("=== Pipeline completed successfully ===");

            // Read file bytes into memory so we can delete the temp file from disk
            byte[] stepBytes = Files.readAllBytes(outputPath);
            Files.deleteIfExists(outputPath);
            log.info("Temp STEP file deleted from disk. Sending {} bytes to client.", stepBytes.length);

            return new ByteArrayResource(stepBytes);

        } catch (Exception e) {
            log.error("Dual-Agent Pipeline Error: {}", e.getMessage(), e);
            throw new RuntimeException("Dual-Agent Pipeline Error: " + e.getMessage());
        }
    }

    /** Returns true for lines that look like environment variable assignments (KEY=value). */
    private boolean isEnvVarLine(String line) {
        if (line == null || line.isBlank()) return true;
        int eq = line.indexOf('=');
        if (eq <= 0) return false;
        String key = line.substring(0, eq);
        return key.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    /** Returns true for known harmless FreeCAD startup noise lines to suppress at INFO. */
    private boolean isIgnoredStartupLine(String line) {
        if (line == null) return false;
        return line.contains("3DconnexionNavlib")
                || line.contains("FreeCAD Console mode")
                || line.startsWith("Running: ")
                || line.startsWith("Recompute")
                || line.contains("(100 %)");
    }
}