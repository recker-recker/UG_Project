package com.example.ug_project.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Beam {
    public String sectionType;

    public Double length;

    // RECT
    public Double width;
    public Double height;

    // CIRCULAR
    public Double radius;

    // I-section
    public Double flangeWidth;
    public Double flangeThickness;
    public Double webThickness;

    // T-section
    public Double stemThickness;

    // Actuator
    public Double ax, ay, az;
    public Double px, py, pz;
}
