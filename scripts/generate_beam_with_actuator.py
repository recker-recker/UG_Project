import os
import sys

# DEBUG: Write a simple check file to see if Python even starts
with open("/Users/akshat/IdeaProjects/UG_Project/generated_models/python_start.txt", "w") as f:
    f.write("Python started successfully")

try:
    import FreeCAD
    import Part
except ImportError as e:
    with open("/Users/akshat/IdeaProjects/UG_Project/generated_models/python_error.txt", "w") as f:
        f.write(f"ImportError: {str(e)}")
    sys.exit(1)

def create_beam():
    data = os.getenv("BEAM_DATA")
    if not data:
        sys.exit(1)

    args = data.split("|")
    section_type = args[0]
    length = float(args[1])
    output_path = args[-1]

    # Actuator coords
    ax, ay, az = float(args[-7]), float(args[-6]), float(args[-5])

    doc = FreeCAD.newDocument("BeamGen")

    if section_type == "RECT":
        w, h = float(args[2]), float(args[3])
        shape = Part.makeBox(w, h, length)
    elif section_type == "CIRCULAR":
        r = float(args[2])
        shape = Part.makeCylinder(r, length)
    elif section_type == "I":
        f_w, f_t, w_t, h = float(args[2]), float(args[3]), float(args[4]), float(args[5])
        b1 = Part.makeBox(f_w, f_t, length)
        web = Part.makeBox(w_t, h - (2*f_t), length)
        web.translate(FreeCAD.Vector((f_w/2)-(w_t/2), f_t, 0))
        b2 = Part.makeBox(f_w, f_t, length)
        b2.translate(FreeCAD.Vector(0, h-f_t, 0))
        shape = b1.fuse(web).fuse(b2)
    elif section_type == "T":
        f_w, f_t, s_t, h = float(args[2]), float(args[3]), float(args[4]), float(args[5])
        flange = Part.makeBox(f_w, f_t, length)
        stem = Part.makeBox(s_t, h - f_t, length)
        stem.translate(FreeCAD.Vector((f_w/2)-(s_t/2), f_t, 0))
        shape = flange.fuse(stem)

    # Add marker
    marker = Part.makeBox(5, 5, 5)
    marker.translate(FreeCAD.Vector(ax, ay, az))
    final_model = shape.fuse(marker)

    obj = doc.addObject("Part::Feature", "Beam")
    obj.Shape = final_model
    Part.export([obj], output_path)

if __name__ == "__main__":
    create_beam()
