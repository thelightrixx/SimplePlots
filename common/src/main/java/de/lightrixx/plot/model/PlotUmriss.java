package de.lightrixx.plot.model;

import java.util.*;

/** Außenkanten des Claims einschließlich übernommener Innenwege; öffentliche Kreuzungen bleiben ausgespart. */
public final class PlotUmriss {
    public record Punkt(double x, double z) {}
    public record Kante(long x1, long z1, long x2, long z2) {}
    private PlotUmriss() {}
    public static List<Kante> kanten(Set<PlotId> zellen, PlotRaster raster) {
        var out = new ArrayList<Kante>();
        for (var cell : zellen) {
            long x = (long) cell.x() * raster.rasterweite(), z = (long) cell.z() * raster.rasterweite();
            long endX = x + raster.groesse(), endZ = z + raster.groesse(), road = raster.wegbreite();
            if (!hat(zellen, cell, -1, 0)) out.add(new Kante(x, z, x, endZ));
            if (!hat(zellen, cell, 0, -1)) out.add(new Kante(x, z, endX, z));
            if (!hat(zellen, cell, 1, 0)) out.add(new Kante(endX, z, endX, endZ));
            else {
                if (!(hat(zellen, cell, 0, -1) && hat(zellen, cell, 1, -1))) out.add(new Kante(endX, z, endX + road, z));
                if (!(hat(zellen, cell, 0, 1) && hat(zellen, cell, 1, 1))) out.add(new Kante(endX, endZ, endX + road, endZ));
            }
            if (!hat(zellen, cell, 0, 1)) out.add(new Kante(x, endZ, endX, endZ));
            else {
                if (!(hat(zellen, cell, -1, 0) && hat(zellen, cell, -1, 1))) out.add(new Kante(x, endZ, x, endZ + road));
                if (!(hat(zellen, cell, 1, 0) && hat(zellen, cell, 1, 1))) out.add(new Kante(endX, endZ, endX, endZ + road));
            }
        }
        return List.copyOf(out);
    }
    private static boolean hat(Set<PlotId> zellen, PlotId id, int dx, int dz) {
        long x = (long) id.x() + dx, z = (long) id.z() + dz;
        return x >= Integer.MIN_VALUE && x <= Integer.MAX_VALUE && z >= Integer.MIN_VALUE && z <= Integer.MAX_VALUE && zellen.contains(new PlotId((int) x, (int) z));
    }
    public static List<Punkt> punkte(List<Kante> kanten, double x, double z, int reichweite, int abstand, int limit) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || reichweite < 1 || reichweite > 64 || abstand < 1 || limit < 1 || limit > 512) throw new IllegalArgumentException("Ungültige Grenzanzeige.");
        var out = new LinkedHashSet<Punkt>();
        for (var edge : kanten) {
            boolean senkrecht = edge.x1 == edge.x2;
            double fest = senkrecht ? edge.x1 : edge.z1, mitte = senkrecht ? x : z;
            if (Math.abs(fest - mitte) > reichweite) continue;
            long start = senkrecht ? edge.z1 : edge.x1, ende = senkrecht ? edge.z2 : edge.x2;
            double um = senkrecht ? z : x;
            long von = Math.max(start, (long) Math.ceil(um - reichweite)), bis = Math.min(ende, (long) Math.floor(um + reichweite));
            if (von > bis) continue;
            long erster = start + Math.max(0, (long) Math.ceil((von - start) / (double) abstand)) * abstand;
            for (long ort = erster; ort <= bis; ort += abstand) {
                double px = senkrecht ? fest : ort, pz = senkrecht ? ort : fest;
                if ((px - x) * (px - x) + (pz - z) * (pz - z) <= (double) reichweite * reichweite) out.add(new Punkt(px, pz));
                if (out.size() == limit) return List.copyOf(out);
            }
        }
        return List.copyOf(out);
    }
}
