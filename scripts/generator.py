import FreeCAD
import Part
import os

# Create output folder
os.makedirs('output', exist_ok=True)
doc = FreeCAD.newDocument()

# Profile points from JSON payload
raw_vertices = [
    [-0.5, -1], [8.5, -1], [18.5, -1], [27.5, -1], [36.5, -1], [47.5, -1],
    [56.5, -1], [65.5, 2], [73.5, 6], [80.5, 11], [86.5, 18], [91.5, 25],
    [94.5, 33], [97.5, 41], [100.5, 50], [102.5, 58], [102.5, 67], [103.5, 75],
    [104.5, 83], [105.5, 92], [105.5, 101], [105.5, 110], [105.5, 119], [108.5, 127],
    [114.5, 134], [122.5, 139], [130.5, 143], [138.5, 147], [147.5, 147], [156.5, 147]
]

# Ensure points do not cross the Y-axis (X >= 0) to avoid self-intersecting solid geometry
poles = [FreeCAD.Vector(max(0.0, float(pt[0])), float(pt[1]), 0.0) for pt in raw_vertices]

# Build smooth BSpline curve from profile control points
bspline = Part.BSplineCurve()
bspline.buildFromPoles(poles)
curve_edge = bspline.toShape()

# Retrieve exact curve endpoints
v1 = curve_edge.Vertex1.Point
v2 = curve_edge.Vertex2.Point

# Close the profile back to the Y-axis (X=0)
raw_closing_pts = [v2, FreeCAD.Vector(0, v2.y, 0), FreeCAD.Vector(0, v1.y, 0), v1]
closing_pts = []
for pt in raw_closing_pts:
    if not closing_pts or (pt - closing_pts[-1]).Length > 1e-4:
        closing_pts.append(pt)

closing_wire = Part.makePolygon(closing_pts)

# Combine BSpline edge and closing edges into a closed wire
wire = Part.Wire([curve_edge] + closing_wire.Edges)

# Create 2D face and revolve around the Y-axis to form a 3D solid
face = Part.Face(wire)
axis_vector = FreeCAD.Vector(0, 1, 0)
solid = face.revolve(FreeCAD.Vector(0, 0, 0), axis_vector, 360)

# Export solid to STEP
obj = doc.addObject('Part::Feature', 'Model')
obj.Shape = solid
doc.recompute()
Part.export([obj], 'output/model.step')
FreeCAD.closeDocument(doc.Name)