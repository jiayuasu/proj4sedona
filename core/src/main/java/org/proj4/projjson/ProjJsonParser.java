package org.proj4.projjson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.proj4.core.Projection;
import org.proj4.constants.Values;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for PROJJSON format coordinate reference system definitions.
 * Converts PROJJSON definitions to PROJ strings and Projection objects.
 */
public class ProjJsonParser {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Parse a PROJJSON string and return a ProjJsonDefinition object.
     * @param projJsonString The PROJJSON string
     * @return A ProjJsonDefinition object
     * @throws IOException if parsing fails
     */
    public static ProjJsonDefinition parseDefinition(String projJsonString) throws IOException {
        return objectMapper.readValue(projJsonString, ProjJsonDefinition.class);
    }
    
    /**
     * Parse a PROJJSON string and return a Projection object.
     * @param projJsonString The PROJJSON string
     * @return A Projection object
     * @throws IOException if parsing fails
     */
    public static Projection parse(String projJsonString) throws IOException {
        ProjJsonDefinition definition = parseDefinition(projJsonString);
        return parse(definition);
    }
    
    /**
     * Parse a PROJJSON definition and return a Projection object.
     * @param definition The PROJJSON definition
     * @return A Projection object
     */
    public static Projection parse(ProjJsonDefinition definition) {
        String projString = toProjString(definition);
        return new Projection(projString);
    }
    
    /**
     * Convert a PROJJSON definition to a PROJ string.
     * @param definition The PROJJSON definition
     * @return A PROJ string
     */
    public static String toProjString(ProjJsonDefinition definition) {
        StringBuilder proj = new StringBuilder();
        
        // Determine projection type and add basic projection
        String projectionType = determineProjectionType(definition);
        proj.append("+proj=").append(projectionType);
        
        // Add ellipsoid/datum information
        addEllipsoidInfo(proj, definition);
        
        // Add conversion parameters
        addConversionParameters(proj, definition);
        
        // Add coordinate system information
        addCoordinateSystemInfo(proj, definition);
        
        // Add units
        addUnits(proj, definition);
        
        return proj.toString();
    }
    
    /**
     * Determine the projection type from the PROJJSON definition.
     */
    private static String determineProjectionType(ProjJsonDefinition definition) {
        if (definition.getType() == null) {
            return "longlat"; // Default to geographic
        }
        
        switch (definition.getType()) {
            case "GeographicCRS":
                return "longlat";
            case "ProjectedCRS":
                return determineProjectedProjectionType(definition);
            case "GeocentricCRS":
                return "geocent";
            default:
                return "longlat";
        }
    }
    
    /**
     * Determine the specific projection type for projected CRS.
     */
    private static String determineProjectedProjectionType(ProjJsonDefinition definition) {
        if (definition.getConversion() == null || definition.getConversion().getMethod() == null) {
            return "longlat";
        }
        
        String methodName = definition.getConversion().getMethod().getName();
        
        // Map common method names to PROJ projection types
        Map<String, String> methodMap = new HashMap<>();
        methodMap.put("Transverse Mercator", "tmerc");
        methodMap.put("Lambert Conformal Conic", "lcc");
        methodMap.put("Lambert Conformal Conic 1SP", "lcc");
        methodMap.put("Lambert Conformal Conic 2SP", "lcc");
        methodMap.put("Mercator", "merc");
        methodMap.put("Mercator 1SP", "merc");
        methodMap.put("Mercator 2SP", "merc");
        methodMap.put("Albers Equal Area", "aea");
        methodMap.put("Miller Cylindrical", "mill");
        methodMap.put("Stereographic", "stere");
        methodMap.put("Stereographic North Pole", "stere");
        methodMap.put("Stereographic South Pole", "stere");
        methodMap.put("Universal Transverse Mercator", "utm");
        methodMap.put("UTM", "utm");
        
        return methodMap.getOrDefault(methodName, "longlat");
    }
    
    /**
     * Add ellipsoid and datum information to the PROJ string.
     */
    private static void addEllipsoidInfo(StringBuilder proj, ProjJsonDefinition definition) {
        // Check for datum information
        if (definition.getDatum() != null && definition.getDatum().getEllipsoid() != null) {
            ProjJsonDefinition.Ellipsoid ellipsoid = definition.getDatum().getEllipsoid();
            addEllipsoidParameters(proj, ellipsoid);
        } else if (definition.getBaseCrs() != null && 
                   definition.getBaseCrs().getDatum() != null && 
                   definition.getBaseCrs().getDatum().getEllipsoid() != null) {
            ProjJsonDefinition.Ellipsoid ellipsoid = definition.getBaseCrs().getDatum().getEllipsoid();
            addEllipsoidParameters(proj, ellipsoid);
        } else {
            // Default to WGS84
            proj.append(" +ellps=WGS84 +datum=WGS84");
        }
    }
    
    /**
     * Add ellipsoid parameters to the PROJ string.
     */
    private static void addEllipsoidParameters(StringBuilder proj, ProjJsonDefinition.Ellipsoid ellipsoid) {
        if (ellipsoid.getSemiMajorAxis() != null) {
            proj.append(" +a=").append(ellipsoid.getSemiMajorAxis());
        }
        
        if (ellipsoid.getInverseFlattening() != null) {
            proj.append(" +rf=").append(ellipsoid.getInverseFlattening());
        } else if (ellipsoid.getRadius() > 0) {
            // For sphere
            proj.append(" +a=").append(ellipsoid.getRadius());
            proj.append(" +b=").append(ellipsoid.getRadius());
        }
        
        // Add datum if available
        if (ellipsoid.getName() != null) {
            String datumName = mapEllipsoidToDatum(ellipsoid.getName());
            if (datumName != null) {
                proj.append(" +datum=").append(datumName);
            }
        } else {
            // Default to WGS84 if no ellipsoid name
            proj.append(" +datum=WGS84");
        }
    }
    
    /**
     * Map ellipsoid names to datum names.
     */
    private static String mapEllipsoidToDatum(String ellipsoidName) {
        if (ellipsoidName == null) return null;
        
        switch (ellipsoidName.toLowerCase()) {
            case "wgs 84":
            case "wgs84":
                return "WGS84";
            case "grs 1980":
            case "grs80":
                return "NAD83";
            case "clarke 1866":
                return "NAD27";
            default:
                return null;
        }
    }
    
    /**
     * Add conversion parameters to the PROJ string.
     */
    private static void addConversionParameters(StringBuilder proj, ProjJsonDefinition definition) {
        if (definition.getConversion() == null || definition.getConversion().getParameters() == null) {
            return;
        }
        
        for (ProjJsonDefinition.Conversion.Parameter param : definition.getConversion().getParameters()) {
            String paramName = param.getName();
            double value = param.getValue();
            
            // Convert parameter names to PROJ parameter names
            String projParamName = mapParameterName(paramName);
            if (projParamName != null) {
                proj.append(" +").append(projParamName).append("=").append(value);
            }
        }
    }
    
    /**
     * Map PROJJSON parameter names to PROJ parameter names.
     */
    private static String mapParameterName(String paramName) {
        if (paramName == null) return null;
        
        switch (paramName.toLowerCase()) {
            case "longitude of natural origin":
            case "central meridian":
                return "lon_0";
            case "latitude of natural origin":
            case "latitude of origin":
                return "lat_0";
            case "standard parallel 1":
            case "latitude of 1st standard parallel":
                return "lat_1";
            case "standard parallel 2":
            case "latitude of 2nd standard parallel":
                return "lat_2";
            case "false easting":
                return "x_0";
            case "false northing":
                return "y_0";
            case "scale factor at natural origin":
            case "scale factor":
                return "k_0";
            case "longitude of center":
                return "lon_0";
            case "latitude of center":
                return "lat_0";
            default:
                return null;
        }
    }
    
    /**
     * Add coordinate system information to the PROJ string.
     */
    private static void addCoordinateSystemInfo(StringBuilder proj, ProjJsonDefinition definition) {
        if (definition.getCoordinateSystem() == null) {
            return;
        }
        
        String subtype = definition.getCoordinateSystem().getSubtype();
        if ("ellipsoidal".equals(subtype)) {
            proj.append(" +axis=enu");
        } else if ("cartesian".equals(subtype)) {
            proj.append(" +axis=enu");
        }
    }
    
    /**
     * Add units information to the PROJ string.
     */
    private static void addUnits(StringBuilder proj, ProjJsonDefinition definition) {
        // Check coordinate system for units
        if (definition.getCoordinateSystem() != null && 
            definition.getCoordinateSystem().getAxis() != null &&
            !definition.getCoordinateSystem().getAxis().isEmpty()) {
            
            Object unit = definition.getCoordinateSystem().getAxis().get(0).getUnit();
            if (unit instanceof String) {
                String unitStr = (String) unit;
                if ("metre".equals(unitStr) || "meter".equals(unitStr)) {
                    proj.append(" +units=m");
                } else if ("degree".equals(unitStr)) {
                    proj.append(" +units=degrees");
                }
            } else if (unit instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> unitMap = (Map<String, Object>) unit;
                String unitName = (String) unitMap.get("name");
                if ("metre".equals(unitName) || "meter".equals(unitName)) {
                    proj.append(" +units=m");
                } else if ("degree".equals(unitName)) {
                    proj.append(" +units=degrees");
                }
            }
        }
    }
    
    /**
     * Create a PROJJSON definition from a PROJ string.
     * This is a basic implementation that creates a simple PROJJSON structure.
     * @param projString The PROJ string
     * @return A PROJJSON definition
     */
    public static ProjJsonDefinition fromProjString(String projString) {
        ProjJsonDefinition definition = new ProjJsonDefinition();
        definition.setSchema("https://proj.org/schemas/v0.7/projjson.schema.json");
        
        // Parse basic PROJ string parameters
        Map<String, String> params = parseProjString(projString);
        
        // Determine type
        String proj = params.get("proj");
        if ("longlat".equals(proj)) {
            definition.setType("GeographicCRS");
        } else if ("geocent".equals(proj)) {
            definition.setType("GeocentricCRS");
        } else {
            definition.setType("ProjectedCRS");
        }
        
        // Set name
        String title = params.get("title");
        if (title != null) {
            definition.setName(title);
        } else {
            definition.setName("Custom CRS");
        }
        
        // Create basic coordinate system
        ProjJsonDefinition.CoordinateSystem coordSys = new ProjJsonDefinition.CoordinateSystem();
        if ("longlat".equals(proj)) {
            coordSys.setSubtype("ellipsoidal");
        } else {
            coordSys.setSubtype("cartesian");
        }
        definition.setCoordinateSystem(coordSys);
        
        return definition;
    }
    
    /**
     * Parse a PROJ string into a map of parameters.
     */
    private static Map<String, String> parseProjString(String projString) {
        Map<String, String> params = new HashMap<>();
        
        if (projString == null || projString.trim().isEmpty()) {
            return params;
        }
        
        String[] parts = projString.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("+")) {
                String[] keyValue = part.substring(1).split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                } else {
                    params.put(keyValue[0], "");
                }
            }
        }
        
        return params;
    }
}
