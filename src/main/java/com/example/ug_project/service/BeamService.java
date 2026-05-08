package com.example.ug_project.service;

import com.example.ug_project.model.BeamRequest;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class BeamService {
    private static final String FREECAD_PATH = "/Applications/FreeCAD.app/Contents/Resources/bin/freecadcmd";
    private static final String SCRIPT_PATH = "/Users/akshat/IdeaProjects/UG_Project/scripts/beam_engine.py";

    public File generateStepFile(BeamRequest request) throws Exception {
        String fullPath = request.getOutput().getDirectory() + request.getOutput().getFileName();

        // Ensure directory exists to prevent Python 'FileNotFound' errors
        new File(request.getOutput().getDirectory()).mkdirs();

        // 7 Parameters: TYPE|L|D|W|H|TF|TW|PATH
        String beamData = String.format("%s|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%s",
                request.getBeamConfiguration().getType(),
                request.getBeamConfiguration().getDimensions().getLength(),
                request.getBeamConfiguration().getDimensions().getDiameter(),
                request.getBeamConfiguration().getDimensions().getWidth(),
                request.getBeamConfiguration().getDimensions().getHeight(),
                request.getBeamConfiguration().getDimensions().getFlangeThickness(),
                request.getBeamConfiguration().getDimensions().getWebThickness(),
                fullPath
        );

        String pythonCmd = String.format("exec(open('%s').read())", SCRIPT_PATH);
        ProcessBuilder pb = new ProcessBuilder(FREECAD_PATH, "-c", pythonCmd);
        pb.environment().put("BEAM_DATA", beamData);

        // Redirect errors so they show up in IntelliJ's console
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Log Python output to IntelliJ console for debugging
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) { System.out.println("FreeCAD: " + line); }
        }

        process.waitFor();

        File resultFile = new File(fullPath);
        if (resultFile.exists()) {
            return resultFile;
        } else {
            throw new RuntimeException("CAD Generation Failed - Python script did not create file.");
        }
    }
}