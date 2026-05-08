package com.example.ug_project.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Beam {
    // Basic Geometry
    public String sectionType;
    public Double length;

    // RECT Section
    public Double width;
    public Double height;

    // CIRCULAR Section
    public Double radius;

    // I-section
    public Double flangeWidth;
    public Double flangeThickness;
    public Double webThickness;

    // T-section
    public Double stemThickness;

    // --- PHYSICS & FEA PARAMETERS ---
    public Double load;          // Force applied in Newtons (P)
    public Double loadPosition;  // Distance from fixed end to apply load (Lp)
    public Double materialE;     // Young's Modulus in MPa (e.g., Steel = 210000)

    // Piezoelectric Sensor / Actuator Position
    public Double ax, ay, az;

    // Piezoelectric Sensor / Actuator Orientation (Pitch, Yaw, Roll)
    public Double px, py, pz;
}