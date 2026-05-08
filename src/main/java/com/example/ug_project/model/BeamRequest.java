package com.example.ug_project.model;

import lombok.Data;

@Data // Generates getBeamConfiguration() and getOutput()
public class BeamRequest {
    private BeamConfiguration beamConfiguration;
    private OutputConfig output;

    @Data // Generates getType() and getDimensions()
    public static class BeamConfiguration {
        private String type;
        private Dimensions dimensions;
    }

    @Data // Generates getLength(), getWidth(), etc.
    public static class Dimensions {
        private double length, diameter, width, height, flangeThickness, webThickness;
    }

    @Data // Generates getFileName() and getDirectory()
    public static class OutputConfig {
        private String fileName;
        private String directory;
    }
}