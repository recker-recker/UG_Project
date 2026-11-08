package com.example.ug_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class LlmAgentService {

    private static final Logger log = LoggerFactory.getLogger(LlmAgentService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // Fallback model chain: tried in order when a model is busy/unavailable
    private final String[] MODELS = {
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest"
    };

    // Agent 1: The Creator
    public String generateFreeCadScript(String jsonPayload) {
        log.info("[Agent 1 - Creator] Generating FreeCAD script from payload ({} chars)", jsonPayload.length());
        String prompt = "You are an expert FreeCAD Python developer writing HEADLESS scripts (no GUI).\n" +
                "Read the JSON payload and write a Python script to generate a SOLID .step file at 'output/model.step'.\n\n"
                +
                "=== COMPLETE WORKING TEMPLATE - follow this exact pattern ===\n\n" +
                "```python\n" +
                "import FreeCAD, Part, os\n" +
                "os.makedirs('output', exist_ok=True)\n" +
                "doc = FreeCAD.newDocument()\n\n" +
                "# --- OPTION A: Extrude a custom 2D profile (use for custom cross-sections) ---\n" +
                "# To close it, make sure the last point MUST equal the first point.\n" +
                "v1 = FreeCAD.Vector(0, 0, 0)\n" +
                "v2 = FreeCAD.Vector(100, 0, 0)\n" +
                "v3 = FreeCAD.Vector(100, 50, 0)\n" +
                "v4 = FreeCAD.Vector(0, 50, 0)\n" +
                "wire = Part.makePolygon([v1, v2, v3, v4, v1])  # v1 repeated at end = closed!\n" +
                "face = Part.Face(wire)          # 2D filled face\n" +
                "solid = face.extrude(FreeCAD.Vector(0, 0, 200))  # solid extrusion along Z\n" +
                "# solid.ShapeType == 'Solid'  <- this is what we want\n\n" +
                "# --- OPTION B: Primitive solids (use when shape fits) ---\n" +
                "# solid = Part.makeBox(length, width, height)      # rectangular box\n" +
                "# solid = Part.makeCylinder(radius, height)        # cylinder\n" +
                "# solid = Part.makeSphere(radius)                  # sphere\n" +
                "# solid = Part.makeCone(r1, r2, height)           # cone\n\n" +
                "# --- OPTION C: Sweep a profile along a path ---\n" +
                "# CRITICAL: Use BSplineCurve for smooth curvy paths, and makePolygon for sharp shapes (like rectangles).\n"
                +
                "# curve = Part.BSplineCurve(vectors) OR Part.makePolygon(vectors)\n" +
                "# path_wire = Part.Wire([curve.toShape()])\n" +
                "# CRITICAL: Use makePipeShell on the PATH wire, pass PROFILE wire in a list.\n" +
                "# True = makeSolid, True = isFrenet (MUST BE TRUE to prevent twisting!)\n" +
                "# solid = path_wire.makePipeShell([profile_wire], True, True)\n\n" +
                "# --- Boolean operations (optional) ---\n" +
                "# result = solid1.fuse(solid2)     # union\n" +
                "# result = solid1.cut(solid2)      # subtraction\n" +
                "# result = solid1.common(solid2)   # intersection\n\n" +
                "# --- ALWAYS end with these exact lines ---\n" +
                "obj = doc.addObject('Part::Feature', 'Model')\n" +
                "obj.Shape = solid\n" +
                "doc.recompute()\n" +
                "Part.export([obj], 'output/model.step')   # ONLY correct headless export\n" +
                "FreeCAD.closeDocument(doc.Name)\n" +
                "```\n\n" +
                "=== RULES ===\n" +
                "- FORBIDDEN: Part.makeEdge(), doc.exportStep(), doc.export(), ImportGui (none exist headlessly)\n" +
                "- CRITICAL FOR CURVES: NEVER use `bspline.interpolate()`. It causes wavy curves from noisy UI points. ALWAYS use `Part.BSplineCurve(vectors)` or `bspline.buildFromPoles(vectors)` to use the points as control poles for a perfectly smooth curve.\n" +
                "- Use Part.makePolygon for sharp/geometric shapes (like squares). Use Part.BSplineCurve for smooth, curvy, or freehand paths.\n"
                +
                "- For sweep operations, ALWAYS use isFrenet=True in makePipeShell so the profile turns smoothly along the path.\n"
                +
                "- CRITICAL FOR SWEEPS: The user draws massive cross-sections. If the cross-section is larger than the path's turn radius, it will mathematically self-intersect and create a spiderweb mess! ALWAYS scale the profile vertices down by multiplying them by 0.15 (relative to their center) before sweeping!\n"
                +
                "- To close a curve or polygon, ensure the last point in the vectors list is identical to the first point.\n"
                +
                "- The final shape assigned to obj.Shape MUST be a Solid (ShapeType == 'Solid')\n\n" +
                "=== JSON PAYLOAD ===\n" +
                jsonPayload + "\n\n" +
                "Output ONLY executable Python code inside ```python tags.";
        String result = callGemini(prompt);
        log.info("[Agent 1 - Creator] Script generated ({} chars)", result.length());
        return result;
    }

    // Agent 2: The Reviewer / Corrector
    public String correctFreeCadScript(String brokenCode, String errorMessage) {
        log.info("[Agent 2 - Corrector] Sending broken script + error log to Gemini for correction");
        log.debug("[Agent 2 - Corrector] Error log:\n{}", errorMessage);
        String prompt = "You are a FreeCAD debugging expert. The following Python script failed to execute in HEADLESS mode.\n\n"
                +
                "BROKEN CODE:\n" + brokenCode + "\n\n" +
                "ERROR LOG:\n" + errorMessage + "\n\n" +
                "Fix the error. CRITICAL: For STEP export use EXACTLY: Part.export([obj], 'output/model.step')\n" +
                "DO NOT use doc.exportStep(), doc.export(), or ImportGui - they do NOT work headlessly.\n" +
                "Output ONLY the corrected, executable Python code inside ```python tags. Save to 'output/model.step'.";
        String result = callGemini(prompt);
        log.info("[Agent 2 - Corrector] Corrected script received ({} chars)", result.length());
        return result;
    }

    private String callGemini(String prompt) {
        String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]}";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        Exception lastException = null;

        for (String model : MODELS) {
            String url = GEMINI_BASE_URL + model + ":generateContent?key=" + apiKey;
            int maxAttempts = 3;
            long delayMs = 5000;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    log.info("[Gemini API] Calling {} (attempt {}/{})", model, attempt, maxAttempts);
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    log.info("[Gemini API] Success with {} (body: {} chars)",
                            model, response.getBody() != null ? response.getBody().length() : 0);
                    return extractPythonCode(response.getBody());

                } catch (org.springframework.web.client.HttpServerErrorException e) {
                    int status = e.getStatusCode().value();
                    if (status == 503 || status == 500) {
                        log.warn("[Gemini API] {} returned {} on attempt {}/{}",
                                model, status, attempt, maxAttempts);
                        if (attempt < maxAttempts) {
                            log.info("[Gemini API] Retrying {} in {}ms...", model, delayMs);
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            delayMs *= 2;
                        } else {
                            log.warn("[Gemini API] {} exhausted. Trying next model...", model);
                            lastException = e;
                        }
                    } else {
                        throw e; // unknown 5xx
                    }

                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    int status = e.getStatusCode().value();
                    if (status == 429) {
                        log.warn("[Gemini API] {} rate-limited (429) on attempt {}/{}",
                                model, attempt, maxAttempts);
                        if (attempt < maxAttempts) {
                            log.info("[Gemini API] Retrying {} in {}ms...", model, delayMs);
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            delayMs *= 2;
                        } else {
                            log.warn("[Gemini API] {} exhausted. Trying next model...", model);
                            lastException = e;
                        }
                    } else if (status == 404) {
                        // 404 means the model doesn't exist or was deprecated
                        log.warn("[Gemini API] Model {} not found (404). Skipping to next model...", model);
                        lastException = e;
                        break; // break out of retry loop, move to next model
                    } else {
                        throw e; // 400, 401, etc.
                    }
                }
            }
        }

        throw new RuntimeException("All Gemini models failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Escapes a string for safe embedding inside a JSON string value.
     * Handles all special characters per RFC 8259.
     */
    private String escapeJson(String text) {
        if (text == null)
            return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String extractPythonCode(String rawJsonResponse) {
        if (rawJsonResponse == null)
            return "";

        // Extract the "text" field value from Gemini's JSON response using plain string
        // parsing.
        // Gemini returns: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
        // We locate the "text": key and read its string value, handling JSON escape
        // sequences.
        String textValue = extractJsonStringField(rawJsonResponse, "\"text\":");
        String source = (textValue != null) ? textValue : rawJsonResponse;

        // Now extract the Python code block
        int start = source.indexOf("```python");
        int end = source.lastIndexOf("```");
        if (start != -1 && end > start + 9) {
            return source.substring(start + 9, end).trim();
        }
        log.warn("[Gemini API] No ```python block found in response; returning raw text");
        return source.trim();
    }

    /**
     * Extracts and unescapes the value of a JSON string field from a raw JSON
     * string.
     * Finds the first occurrence of fieldKey (e.g. "\"text\":") and reads the
     * quoted value after it.
     */
    private String extractJsonStringField(String json, String fieldKey) {
        int keyIdx = json.indexOf(fieldKey);
        if (keyIdx == -1)
            return null;
        int quoteStart = json.indexOf('"', keyIdx + fieldKey.length());
        if (quoteStart == -1)
            return null;
        StringBuilder sb = new StringBuilder();
        int i = quoteStart + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> {
                        sb.append('\n');
                        i += 2;
                    }
                    case 'r' -> {
                        sb.append('\r');
                        i += 2;
                    }
                    case 't' -> {
                        sb.append('\t');
                        i += 2;
                    }
                    case 'b' -> {
                        sb.append('\b');
                        i += 2;
                    }
                    case 'f' -> {
                        sb.append('\f');
                        i += 2;
                    }
                    case '"' -> {
                        sb.append('"');
                        i += 2;
                    }
                    case '\\' -> {
                        sb.append('\\');
                        i += 2;
                    }
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                        } else {
                            sb.append(c);
                            i++;
                        }
                    }
                    default -> {
                        sb.append(next);
                        i += 2;
                    }
                }
            } else if (c == '"') {
                break; // end of string value
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}