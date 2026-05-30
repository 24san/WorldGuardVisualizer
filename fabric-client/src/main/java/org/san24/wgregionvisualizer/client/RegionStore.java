package org.san24.wgregionvisualizer.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RegionStore {
    private static final Map<String, VisualizedRegion> REGIONS = Collections.synchronizedMap(new LinkedHashMap<>());

    private RegionStore() {
    }

    public static void put(VisualizedRegion region) {
        REGIONS.put(region.key(), region);
    }

    public static void clear() {
        REGIONS.clear();
    }

    public static Map<String, VisualizedRegion> regions() {
        synchronized (REGIONS) {
            return Map.copyOf(REGIONS);
        }
    }
}
