package com.example.ug_project.service;

import com.example.ug_project.model.Beam;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;

@Service
public class BeamService {

    private static final String PROJECT_ROOT = "/Users/akshat/IdeaProjects/UG_Project";
    private static final String FREECAD_EXEC = "/Applications/FreeCAD.app/Contents/Resources/bin/freecadcmd";
    private static final String FREECAD_LIB = "/Applications/FreeCAD.app/Contents/Resources/lib";

    public File generateBeam(Beam r) throws Exception {
        String outputDir = PROJECT_ROOT + "/generated_models";
        new File(outputDir).mkdirs();

        String fileName = "beam_" + System.currentTimeMillis() + ".step";
        String outputPath = outputDir + "/" + fileName;

        // 1. Pack the data
        StringJoiner sj = new StringJoiner("|");
        sj.add(r.getSectionType()).add(String.valueOf(r.getLength()));

        if ("RECT".equals(r.getSectionType())) {
            sj.add(String.valueOf(r.getWidth())).add(String.valueOf(r.getHeight()));
        } else if ("I".equals(r.getSectionType()) || "T".equals(r.getSectionType())) {
            sj.add(String.valueOf(r.getFlangeWidth())).add(String.valueOf(r.getFlangeThickness()))
                    .add(String.valueOf(r.getWebThickness() != null ? r.getWebThickness() : r.getStemThickness()))
                    .add(String.valueOf(r.getHeight()));
        } else if ("CIRCULAR".equals(r.getSectionType())) {
            sj.add(String.valueOf(r.getRadius()));
        }

        sj.add(String.valueOf(r.getAx())).add(String.valueOf(r.getAy())).add(String.valueOf(r.getAz()));
        sj.add(String.valueOf(r.getPx())).add(String.valueOf(r.getPy())).add(String.valueOf(r.getPz()));
        sj.add(outputPath);

        // 2. Build the command string for FreeCAD's internal Python engine
        String pythonExecCmd = String.format("exec(open('%s/scripts/generate_beam_with_actuator.py').read())", PROJECT_ROOT);

        ProcessBuilder pb = new ProcessBuilder(FREECAD_EXEC, "-c", pythonExecCmd);

        // 3. Set the Environment (Crucial for Mac)
        Map<String, String> env = pb.environment();
        env.put("BEAM_DATA", sj.toString());
        env.put("PYTHONPATH", FREECAD_LIB);

        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Optional: Stream logs to IntelliJ console so you see the "Write Done" message
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("FreeCAD Output: " + line);
            }
        }

        p.waitFor();

        File result = new File(outputPath);
        if (!result.exists()) {
            throw new RuntimeException("CAD file was not generated. Check console for Python errors.");
        }

        return result;
    }
}