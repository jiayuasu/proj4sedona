package org.apache.sedona.proj.projjson;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a PROJJSON definition for coordinate reference systems.
 * Based on the PROJJSON specification and proj4js implementation.
 */
public class ProjJsonDefinition {
    
    @JsonProperty("$schema")
    private String schema;
    
    private String type;
    private String name;
    private Id id;
    private String scope;
    private String area;
    private BoundingBox bbox;
    private List<ProjJsonDefinition> components;
    private Datum datum;
    private DatumEnsemble datumEnsemble;
    
    @JsonProperty("coordinate_system")
    private CoordinateSystem coordinateSystem;
    
    private Conversion conversion;
    private Transformation transformation;
    
    @JsonProperty("base_crs")
    private BaseCrs baseCrs;
    
    // Getters and setters
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Id getId() { return id; }
    public void setId(Id id) { this.id = id; }
    
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    
    public BoundingBox getBbox() { return bbox; }
    public void setBbox(BoundingBox bbox) { this.bbox = bbox; }
    
    public List<ProjJsonDefinition> getComponents() { return components; }
    public void setComponents(List<ProjJsonDefinition> components) { this.components = components; }
    
    public Datum getDatum() { return datum; }
    public void setDatum(Datum datum) { this.datum = datum; }
    
    public DatumEnsemble getDatumEnsemble() { return datumEnsemble; }
    public void setDatumEnsemble(DatumEnsemble datumEnsemble) { this.datumEnsemble = datumEnsemble; }
    
    public CoordinateSystem getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(CoordinateSystem coordinateSystem) { this.coordinateSystem = coordinateSystem; }
    
    public Conversion getConversion() { return conversion; }
    public void setConversion(Conversion conversion) { this.conversion = conversion; }
    
    public Transformation getTransformation() { return transformation; }
    public void setTransformation(Transformation transformation) { this.transformation = transformation; }
    
    public BaseCrs getBaseCrs() { return baseCrs; }
    public void setBaseCrs(BaseCrs baseCrs) { this.baseCrs = baseCrs; }
    
    /**
     * Represents an authority ID (e.g., EPSG:4326)
     */
    public static class Id {
        private String authority;
        private int code;
        
        public String getAuthority() { return authority; }
        public void setAuthority(String authority) { this.authority = authority; }
        
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
    }
    
    /**
     * Represents a bounding box
     */
    public static class BoundingBox {
        @JsonProperty("south_latitude")
        private double southLatitude;
        
        @JsonProperty("west_longitude")
        private double westLongitude;
        
        @JsonProperty("north_latitude")
        private double northLatitude;
        
        @JsonProperty("east_longitude")
        private double eastLongitude;
        
        public double getSouthLatitude() { return southLatitude; }
        public void setSouthLatitude(double southLatitude) { this.southLatitude = southLatitude; }
        
        public double getWestLongitude() { return westLongitude; }
        public void setWestLongitude(double westLongitude) { this.westLongitude = westLongitude; }
        
        public double getNorthLatitude() { return northLatitude; }
        public void setNorthLatitude(double northLatitude) { this.northLatitude = northLatitude; }
        
        public double getEastLongitude() { return eastLongitude; }
        public void setEastLongitude(double eastLongitude) { this.eastLongitude = eastLongitude; }
    }
    
    /**
     * Represents a datum definition
     */
    public static class Datum {
        private String type;
        private String name;
        private Ellipsoid ellipsoid;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Ellipsoid getEllipsoid() { return ellipsoid; }
        public void setEllipsoid(Ellipsoid ellipsoid) { this.ellipsoid = ellipsoid; }
    }
    
    /**
     * Represents an ellipsoid definition
     */
    public static class Ellipsoid {
        private String name;
        private double radius;
        
        @JsonProperty("semi_major_axis")
        private Double semiMajorAxis;
        
        @JsonProperty("inverse_flattening")
        private Double inverseFlattening;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public double getRadius() { return radius; }
        public void setRadius(double radius) { this.radius = radius; }
        
        public Double getSemiMajorAxis() { return semiMajorAxis; }
        public void setSemiMajorAxis(Double semiMajorAxis) { this.semiMajorAxis = semiMajorAxis; }
        
        public Double getInverseFlattening() { return inverseFlattening; }
        public void setInverseFlattening(Double inverseFlattening) { this.inverseFlattening = inverseFlattening; }
    }
    
    /**
     * Represents a datum ensemble
     */
    public static class DatumEnsemble {
        private String name;
        private List<Member> members;
        private Ellipsoid ellipsoid;
        private String accuracy;
        private Id id;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<Member> getMembers() { return members; }
        public void setMembers(List<Member> members) { this.members = members; }
        
        public Ellipsoid getEllipsoid() { return ellipsoid; }
        public void setEllipsoid(Ellipsoid ellipsoid) { this.ellipsoid = ellipsoid; }
        
        public String getAccuracy() { return accuracy; }
        public void setAccuracy(String accuracy) { this.accuracy = accuracy; }
        
        public Id getId() { return id; }
        public void setId(Id id) { this.id = id; }
        
        public static class Member {
            private String name;
            private Id id;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public Id getId() { return id; }
            public void setId(Id id) { this.id = id; }
        }
    }
    
    /**
     * Represents a coordinate system
     */
    public static class CoordinateSystem {
        private String subtype;
        private List<Axis> axis;
        
        public String getSubtype() { return subtype; }
        public void setSubtype(String subtype) { this.subtype = subtype; }
        
        public List<Axis> getAxis() { return axis; }
        public void setAxis(List<Axis> axis) { this.axis = axis; }
        
        public static class Axis {
            private String name;
            private String abbreviation;
            private String direction;
            private Object unit; // Can be string or Unit object
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public String getAbbreviation() { return abbreviation; }
            public void setAbbreviation(String abbreviation) { this.abbreviation = abbreviation; }
            
            public String getDirection() { return direction; }
            public void setDirection(String direction) { this.direction = direction; }
            
            public Object getUnit() { return unit; }
            public void setUnit(Object unit) { this.unit = unit; }
        }
    }
    
    /**
     * Represents a conversion
     */
    public static class Conversion {
        private String name;
        private Method method;
        private List<Parameter> parameters;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Method getMethod() { return method; }
        public void setMethod(Method method) { this.method = method; }
        
        public List<Parameter> getParameters() { return parameters; }
        public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }
        
        public static class Method {
            private String name;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
        
        public static class Parameter {
            private String name;
            private double value;
            private Object unit; // Can be string or Unit object
            private Id id;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public double getValue() { return value; }
            public void setValue(double value) { this.value = value; }
            
            public Object getUnit() { return unit; }
            public void setUnit(Object unit) { this.unit = unit; }
            
            public Id getId() { return id; }
            public void setId(Id id) { this.id = id; }
        }
    }
    
    /**
     * Represents a transformation
     */
    public static class Transformation {
        private String name;
        private Method method;
        private List<Parameter> parameters;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Method getMethod() { return method; }
        public void setMethod(Method method) { this.method = method; }
        
        public List<Parameter> getParameters() { return parameters; }
        public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }
        
        public static class Method {
            private String name;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
        
        public static class Parameter {
            private String name;
            private double value;
            private Object unit; // Can be string or Unit object
            private String type;
            
            @JsonProperty("file_name")
            private String fileName;
            
            private Id id;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public double getValue() { return value; }
            public void setValue(double value) { this.value = value; }
            
            public Object getUnit() { return unit; }
            public void setUnit(Object unit) { this.unit = unit; }
            
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            
            public String getFileName() { return fileName; }
            public void setFileName(String fileName) { this.fileName = fileName; }
            
            public Id getId() { return id; }
            public void setId(Id id) { this.id = id; }
        }
    }
    
    /**
     * Represents a base CRS
     */
    public static class BaseCrs {
        private String type;
        private String name;
        private Datum datum;
        private CoordinateSystem coordinateSystem;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Datum getDatum() { return datum; }
        public void setDatum(Datum datum) { this.datum = datum; }
        
        public CoordinateSystem getCoordinateSystem() { return coordinateSystem; }
        public void setCoordinateSystem(CoordinateSystem coordinateSystem) { this.coordinateSystem = coordinateSystem; }
    }
}
