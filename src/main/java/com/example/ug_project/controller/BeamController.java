package com.example.ug_project.controller;

import com.example.ug_project.model.Beam;
import com.example.ug_project.service.BeamService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;

@RestController
@RequestMapping("/api/cad")
public class BeamController {

    private final BeamService freeCadService;

    public BeamController(BeamService freeCadService) {
        this.freeCadService = freeCadService;
    }

    @PostMapping("/beam")
    public ResponseEntity<Resource> generateBeam(@RequestBody Beam request)
            throws Exception {

        File stepFile = freeCadService.generateBeam(request);

        InputStreamResource resource =
                new InputStreamResource(new FileInputStream(stepFile));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + stepFile.getName() + "\"")
                .contentLength(stepFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}