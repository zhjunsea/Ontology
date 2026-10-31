package com.ocean.ontologyframework.latticetower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LatticeTowerGeoDriver {
    public static void main(String[] args) {
        double bw = 25000, top = 9800, H = 115253, dev = 500, beta = 47.7, highTruss = 116734.266;
        List<Double> ecc = new ArrayList<>(Arrays.asList(
                713.85, 25159.031, 46307.256, 64603.19, 80431.525, 94125.07, 105971.746, 116220.645));
        LatticeTowerGeometryModel.SectionSpecs sec = new LatticeTowerGeometryModel.SectionSpecs(
                "950x7.5", "1000x30", "200x7", "150x4", "1300x10", Arrays.asList("300x10"));
        LatticeTowerGeometryModel m = new LatticeTowerGeometryModel(bw, top, H, dev, beta, ecc, highTruss, sec);
        m.build();
        for (Map.Entry<Integer, double[]> e : m.nodes().entrySet()) {
            double[] p = e.getValue();
            System.out.printf("%d,%.4f,%.4f,%.4f%n", e.getKey(), p[0], p[1], p[2]);
        }
        System.err.println("alpha=" + m.alphaDegrees());
    }
}
