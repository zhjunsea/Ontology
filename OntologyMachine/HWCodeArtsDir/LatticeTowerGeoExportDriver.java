package com.ocean.ontologyframework.latticetower;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LatticeTowerGeoExportDriver {
    public static void main(String[] args) throws Exception {
        double bw = 25000, top = 9800, H = 115253, dev = 500, beta = 47.7, highTruss = 116734.266;
        List<Double> ecc = new ArrayList<>(Arrays.asList(
                713.85, 25159.031, 46307.256, 64603.19, 80431.525, 94125.07, 105971.746, 116220.645));
        LatticeTowerGeometryModel.SectionSpecs sec = new LatticeTowerGeometryModel.SectionSpecs(
                "950x7.5", "1000x30", "200x7", "150x4", "1300x10", Arrays.asList("300x10"));
        LatticeTowerGeometryModel m = new LatticeTowerGeometryModel(bw, top, H, dev, beta, ecc, highTruss, sec);
        m.build();
        Path out = Paths.get(args[0]);
        LatticeTowerXlsxExporter.export(out, m);
        System.out.println("wrote " + out + " nodes=" + m.nodes().size() + " elems=" + m.elements().size());
    }
}
