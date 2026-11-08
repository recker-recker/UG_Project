import FreeCAD, Part

path_raw_verts = [[89.5, 147], [130, 200], [200, 240], [300, 245], [370, 212]]
path_pts = [FreeCAD.Vector(pt[0], pt[1], 0) for pt in path_raw_verts]
bspline = Part.BSplineCurve()
bspline.buildFromPoles(path_pts)
path_wire = Part.Wire([bspline.toShape()])

prof_raw = [[-20,-20], [20,-20], [20,20], [-20,20], [-20,-20]]
p0 = path_pts[0]
p1 = path_pts[1]
tangent = (p1 - p0).normalize()
normal = FreeCAD.Vector(-tangent.y, tangent.x, 0)
binormal = FreeCAD.Vector(0, 0, 1)

prof_3d = [p0 + normal * pt[0] + binormal * pt[1] for pt in prof_raw]
prof_wire = Part.makePolygon(prof_3d)

solid = path_wire.makePipeShell([prof_wire], True, True)
print(solid.ShapeType)
