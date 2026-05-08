package com.example.ug_project.controller;

import com.example.ug_project.model.BeamRequest; // Ensure this matches your DTO location
import com.example.ug_project.service.BeamService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/cad")
@CrossOrigin(origins = "http://localhost:3000") // Matches your frontend port
public class BeamController {

    private final BeamService beamService;

    public BeamController(BeamService beamGeneratorService) {
        this.beamService = beamGeneratorService;
    }

    @PostMapping("/generate-fea")
    public ResponseEntity<byte[]> generateFeaBeam(@RequestBody BeamRequest request) {
        try {
            File stepFile = beamService.generateStepFile(request);

            if (stepFile == null || !stepFile.exists()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            byte[] fileContent = Files.readAllBytes(stepFile.toPath());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", request.getOutput().getFileName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .body(fileContent);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}