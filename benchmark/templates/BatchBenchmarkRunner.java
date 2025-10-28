import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.apache.sedona.proj.projjson.ProjJsonDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

class {CLASS_NAME} {
    
    // Helper method to initialize test points
    private static Point[] initializeTestPoints(int batchSize) {
        // Base test points
        Point[] basePoints = {
{BASE_POINTS}        };
        
        // Create batch by repeating base points
        Point[] testPoints = new Point[batchSize];
        for (int i = 0; i < batchSize; i++) {
            testPoints[i] = basePoints[i % basePoints.length];
        }
        return testPoints;
    }
    
    public static void main(String[] args) {
        String fromCrs = {FROM_CRS};
        String toCrs = {TO_CRS};
        int batchSize = {BATCH_SIZE};
        int iterations = {ITERATIONS};
        
        // Initialize test points using helper method
        Point[] testPoints = initializeTestPoints(batchSize);
        
        System.out.println("=== Proj4Sedona Batch Benchmark ===");
        System.out.println("Scenario: {SCENARIO_NAME}");
        System.out.println("CRS Format: {CRS_FORMAT}");
        System.out.println("From CRS: " + fromCrs);
        System.out.println("To CRS: " + toCrs);
        System.out.println("Batch Size: " + batchSize);
        System.out.println("Iterations: " + iterations);
        
        // Create projections based on CRS format
        Projection fromProj = null;
        Projection toProj = null;
        
        try {
            if ("{CRS_FORMAT}" == "epsg") {
                fromProj = new Projection(fromCrs);
                toProj = new Projection(toCrs);
            } else if ("{CRS_FORMAT}" == "wkt2") {
                // Map WKT2 keys to filenames
                java.util.Map<String, String> wkt2Files = new java.util.HashMap<>();
                wkt2Files.put("WGS84", "data/wkt2/wgs84.wkt");
                wkt2Files.put("WebMercator", "data/wkt2/webmercator.wkt");
                wkt2Files.put("UTM_19N", "data/wkt2/utm_19n.wkt");
                wkt2Files.put("NAD83_Vermont", "data/wkt2/nad83_vermont.wkt");
                wkt2Files.put("NAD83", "data/wkt2/nad83.wkt");
                
                // Read WKT2 from files
                String fromWkt2 = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(wkt2Files.get(fromCrs))));
                String toWkt2 = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(wkt2Files.get(toCrs))));
                fromProj = new Projection(fromWkt2);
                toProj = new Projection(toWkt2);
            } else if ("{CRS_FORMAT}" == "projjson") {
                // Use PROJJSON strings passed from Python
                ObjectMapper mapper = new ObjectMapper();
                ProjJsonDefinition fromDef = mapper.readValue(fromCrs, ProjJsonDefinition.class);
                ProjJsonDefinition toDef = mapper.readValue(toCrs, ProjJsonDefinition.class);
                fromProj = Proj4Sedona.fromProjJson(fromDef);
                toProj = Proj4Sedona.fromProjJson(toDef);
            }
        } catch (Exception e) {
            System.err.println("Error creating projections: " + e.getMessage());
            System.exit(1);
        }
        
        // Batch transformation test
        System.out.println("\nBatch Performance Test:");
        long startTime = System.nanoTime();
        int successCount = 0;
        
        try {
            for (int i = 0; i < iterations; i++) {
                for (Point point : testPoints) {
                    try {
                        Point result = Proj4Sedona.transform(fromProj, toProj, point, false);
                        successCount++;
                    } catch (Exception e) {
                        // Count failed transformations
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during batch transformation: " + e.getMessage());
        }
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / (iterations * batchSize);
        double tps = 1000.0 / avgTimeMs;
        double successRate = (successCount * 100.0) / (iterations * batchSize);
        
        System.out.println(String.format("Total time: %.2f ms", totalTimeMs));
        System.out.println(String.format("Average per transformation: %.6f ms", avgTimeMs));
        System.out.println(String.format("Transformations per second: %.0f", tps));
        System.out.println(String.format("Success rate: %.2f%%", successRate));
    }
}

