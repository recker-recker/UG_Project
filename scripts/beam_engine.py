import FreeCAD
import Part
import os

# --- PROFILE FUNCTIONS ---
def make_i_profile(w, h, tf, tw):
    pts = [
        FreeCAD.Vector(-w/2, -h/2, 0), FreeCAD.Vector(w/2, -h/2, 0),
        FreeCAD.Vector(w/2, -h/2 + tf, 0), FreeCAD.Vector(tw/2, -h/2 + tf, 0),
        FreeCAD.Vector(tw/2, h/2 - tf, 0), FreeCAD.Vector(w/2, h/2 - tf, 0),
        FreeCAD.Vector(w/2, h/2, 0), FreeCAD.Vector(-w/2, h/2, 0),
        FreeCAD.Vector(-w/2, h/2 - tf, 0), FreeCAD.Vector(-tw/2, h/2 - tf, 0),
        FreeCAD.Vector(-tw/2, -h/2 + tf, 0), FreeCAD.Vector(-w/2, -h/2 + tf, 0),
        FreeCAD.Vector(-w/2, -h/2, 0)
    ]
    return Part.Face(Part.makePolygon(pts))

def make_t_profile(w, h, tf, tw):
    pts = [
        FreeCAD.Vector(-w/2, h/2, 0), FreeCAD.Vector(w/2, h/2, 0),
        FreeCAD.Vector(w/2, h/2 - tf, 0), FreeCAD.Vector(tw/2, h/2 - tf, 0),
        FreeCAD.Vector(tw/2, -h/2, 0), FreeCAD.Vector(-tw/2, -h/2, 0),
        FreeCAD.Vector(-tw/2, h/2 - tf, 0), FreeCAD.Vector(-w/2, h/2 - tf, 0),
        FreeCAD.Vector(-w/2, h/2, 0)
    ]
    return Part.Face(Part.makePolygon(pts))

# --- MAIN ENGINE ---
raw_data = os.getenv("BEAM_DATA")

if raw_data:
    # 1. Parse exactly 7 parameters
    data = raw_data.split('|')
    b_type = data[0]
    L, D, W, H, TF, TW = map(float, data[1:7])
    out_path = data[7]

    # 2. Setup Document
    doc = FreeCAD.newDocument("BeamGen")

    # 3. Generate Shape
    if b_type == "CIRCULAR":
        final_shape = Part.makeCylinder(D/2, L)
    elif b_type == "I_BEAM":
        final_shape = make_i_profile(W, H, TF, TW).extrude(FreeCAD.Vector(0, 0, L))
    elif b_type == "T_BEAM":
        final_shape = make_t_profile(W, H, TF, TW).extrude(FreeCAD.Vector(0, 0, L))
    else: # RECT
        final_shape = Part.makeBox(W, H, L)
        final_shape.translate(FreeCAD.Vector(-W/2, -H/2, 0))

    # 4. Wrap and Export
    feature = doc.addObject("Part::Feature", "BeamResult")
    feature.Shape = final_shape

    Part.export([feature], out_path)
    print("Export Success: " + out_path)
else:
    print("Error: BEAM_DATA not found in environment.")