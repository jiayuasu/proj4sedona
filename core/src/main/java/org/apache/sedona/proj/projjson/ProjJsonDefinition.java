/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sedona.proj.projjson;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a PROJJSON definition for coordinate reference systems. Based on the PROJJSON
 * specification and proj4js implementation.
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
  /**
   * Gets the schema of the projection definition.
   *
   * @return the projection schema
   */
  public String getSchema() {
    return schema;
  }

  /**
   * Sets the schema of the projection definition.
   *
   * @param schema the projection schema
   */
  public void setSchema(String schema) {
    this.schema = schema;
  }

  /**
   * Gets the type of the projection definition.
   *
   * @return the projection type
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the type of the projection definition.
   *
   * @param type the projection type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Gets the name of the projection.
   *
   * @return the projection name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the projection.
   *
   * @param name the projection name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the ID of the projection.
   *
   * @return the projection ID
   */
  public Id getId() {
    return id;
  }

  /**
   * Sets the ID of the projection.
   *
   * @param id the projection ID
   */
  public void setId(Id id) {
    this.id = id;
  }

  /**
   * Gets the scope of the projection.
   *
   * @return the projection scope
   */
  public String getScope() {
    return scope;
  }

  /**
   * Sets the scope of the projection.
   *
   * @param scope the projection scope
   */
  public void setScope(String scope) {
    this.scope = scope;
  }

  /**
   * Gets the area of the projection.
   *
   * @return the projection area
   */
  public String getArea() {
    return area;
  }

  /**
   * Sets the area of the projection.
   *
   * @param area the projection area
   */
  public void setArea(String area) {
    this.area = area;
  }

  /**
   * Gets the bounding box of the projection.
   *
   * @return the projection bounding box
   */
  public BoundingBox getBbox() {
    return bbox;
  }

  /**
   * Sets the bounding box of the projection.
   *
   * @param bbox the projection bounding box
   */
  public void setBbox(BoundingBox bbox) {
    this.bbox = bbox;
  }

  /**
   * Gets the components of the projection.
   *
   * @return the projection components
   */
  public List<ProjJsonDefinition> getComponents() {
    return components;
  }

  /**
   * Sets the components of the projection.
   *
   * @param components the projection components
   */
  public void setComponents(List<ProjJsonDefinition> components) {
    this.components = components;
  }

  /**
   * Gets the datum of the projection.
   *
   * @return the projection datum
   */
  public Datum getDatum() {
    return datum;
  }

  /**
   * Sets the datum of the projection.
   *
   * @param datum the projection datum
   */
  public void setDatum(Datum datum) {
    this.datum = datum;
  }

  /**
   * Gets the datum ensemble of the projection.
   *
   * @return the projection datum ensemble
   */
  public DatumEnsemble getDatumEnsemble() {
    return datumEnsemble;
  }

  /**
   * Sets the datum ensemble of the projection.
   *
   * @param datumEnsemble the projection datum ensemble
   */
  public void setDatumEnsemble(DatumEnsemble datumEnsemble) {
    this.datumEnsemble = datumEnsemble;
  }

  /**
   * Gets the coordinate system of the projection.
   *
   * @return the projection coordinate system
   */
  public CoordinateSystem getCoordinateSystem() {
    return coordinateSystem;
  }

  /**
   * Sets the coordinate system of the projection.
   *
   * @param coordinateSystem the projection coordinate system
   */
  public void setCoordinateSystem(CoordinateSystem coordinateSystem) {
    this.coordinateSystem = coordinateSystem;
  }

  /**
   * Gets the conversion of the projection.
   *
   * @return the projection conversion
   */
  public Conversion getConversion() {
    return conversion;
  }

  /**
   * Sets the conversion of the projection.
   *
   * @param conversion the projection conversion
   */
  public void setConversion(Conversion conversion) {
    this.conversion = conversion;
  }

  /**
   * Gets the transformation of the projection.
   *
   * @return the projection transformation
   */
  public Transformation getTransformation() {
    return transformation;
  }

  /**
   * Sets the transformation of the projection.
   *
   * @param transformation the projection transformation
   */
  public void setTransformation(Transformation transformation) {
    this.transformation = transformation;
  }

  /**
   * Gets the base CRS of the projection.
   *
   * @return the projection base CRS
   */
  public BaseCrs getBaseCrs() {
    return baseCrs;
  }

  /**
   * Sets the base CRS of the projection.
   *
   * @param baseCrs the projection base CRS
   */
  public void setBaseCrs(BaseCrs baseCrs) {
    this.baseCrs = baseCrs;
  }

  /** Represents an authority ID (e.g., EPSG:4326) */
  public static class Id {
    private String authority;
    private int code;

    /**
     * Gets the authority of the ID.
     *
     * @return the authority
     */
    public String getAuthority() {
      return authority;
    }

    /**
     * Sets the authority of the ID.
     *
     * @param authority the authority
     */
    public void setAuthority(String authority) {
      this.authority = authority;
    }

    /**
     * Gets the code of the ID.
     *
     * @return the code
     */
    public int getCode() {
      return code;
    }

    /**
     * Sets the code of the ID.
     *
     * @param code the code
     */
    public void setCode(int code) {
      this.code = code;
    }
  }

  /** Represents a bounding box */
  public static class BoundingBox {
    @JsonProperty("south_latitude")
    private double southLatitude;

    @JsonProperty("west_longitude")
    private double westLongitude;

    @JsonProperty("north_latitude")
    private double northLatitude;

    @JsonProperty("east_longitude")
    private double eastLongitude;

    /**
     * Gets the south latitude of the bounding box.
     *
     * @return the south latitude
     */
    public double getSouthLatitude() {
      return southLatitude;
    }

    /**
     * Sets the south latitude of the bounding box.
     *
     * @param southLatitude the south latitude
     */
    public void setSouthLatitude(double southLatitude) {
      this.southLatitude = southLatitude;
    }

    /**
     * Gets the west longitude of the bounding box.
     *
     * @return the west longitude
     */
    public double getWestLongitude() {
      return westLongitude;
    }

    /**
     * Sets the west longitude of the bounding box.
     *
     * @param westLongitude the west longitude
     */
    public void setWestLongitude(double westLongitude) {
      this.westLongitude = westLongitude;
    }

    /**
     * Gets the north latitude of the bounding box.
     *
     * @return the north latitude
     */
    public double getNorthLatitude() {
      return northLatitude;
    }

    /**
     * Sets the north latitude of the bounding box.
     *
     * @param northLatitude the north latitude
     */
    public void setNorthLatitude(double northLatitude) {
      this.northLatitude = northLatitude;
    }

    /**
     * Gets the east longitude of the bounding box.
     *
     * @return the east longitude
     */
    public double getEastLongitude() {
      return eastLongitude;
    }

    /**
     * Sets the east longitude of the bounding box.
     *
     * @param eastLongitude the east longitude
     */
    public void setEastLongitude(double eastLongitude) {
      this.eastLongitude = eastLongitude;
    }
  }

  /** Represents a datum definition */
  public static class Datum {
    private String type;
    private String name;
    private Ellipsoid ellipsoid;

    /**
     * Gets the type of the datum.
     *
     * @return the datum type
     */
    public String getType() {
      return type;
    }

    /**
     * Sets the type of the datum.
     *
     * @param type the datum type
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Gets the name of the datum.
     *
     * @return the datum name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the datum.
     *
     * @param name the datum name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the ellipsoid of the datum.
     *
     * @return the datum ellipsoid
     */
    public Ellipsoid getEllipsoid() {
      return ellipsoid;
    }

    /**
     * Sets the ellipsoid of the datum.
     *
     * @param ellipsoid the datum ellipsoid
     */
    public void setEllipsoid(Ellipsoid ellipsoid) {
      this.ellipsoid = ellipsoid;
    }
  }

  /** Represents an ellipsoid definition */
  public static class Ellipsoid {
    private String name;
    private double radius;

    @JsonProperty("semi_major_axis")
    private Double semiMajorAxis;

    @JsonProperty("inverse_flattening")
    private Double inverseFlattening;

    /**
     * Gets the name of the ellipsoid.
     *
     * @return the ellipsoid name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the ellipsoid.
     *
     * @param name the ellipsoid name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the radius of the ellipsoid.
     *
     * @return the ellipsoid radius
     */
    public double getRadius() {
      return radius;
    }

    /**
     * Sets the radius of the ellipsoid.
     *
     * @param radius the ellipsoid radius
     */
    public void setRadius(double radius) {
      this.radius = radius;
    }

    /**
     * Gets the semi-major axis of the ellipsoid.
     *
     * @return the ellipsoid semi-major axis
     */
    public Double getSemiMajorAxis() {
      return semiMajorAxis;
    }

    /**
     * Sets the semi-major axis of the ellipsoid.
     *
     * @param semiMajorAxis the ellipsoid semi-major axis
     */
    public void setSemiMajorAxis(Double semiMajorAxis) {
      this.semiMajorAxis = semiMajorAxis;
    }

    /**
     * Gets the inverse flattening of the ellipsoid.
     *
     * @return the ellipsoid inverse flattening
     */
    public Double getInverseFlattening() {
      return inverseFlattening;
    }

    /**
     * Sets the inverse flattening of the ellipsoid.
     *
     * @param inverseFlattening the ellipsoid inverse flattening
     */
    public void setInverseFlattening(Double inverseFlattening) {
      this.inverseFlattening = inverseFlattening;
    }
  }

  /** Represents a datum ensemble */
  public static class DatumEnsemble {
    private String name;
    private List<Member> members;
    private Ellipsoid ellipsoid;
    private String accuracy;
    private Id id;

    /**
     * Gets the name of the datum ensemble.
     *
     * @return the datum ensemble name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the datum ensemble.
     *
     * @param name the datum ensemble name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the members of the datum ensemble.
     *
     * @return the datum ensemble members
     */
    public List<Member> getMembers() {
      return members;
    }

    /**
     * Sets the members of the datum ensemble.
     *
     * @param members the datum ensemble members
     */
    public void setMembers(List<Member> members) {
      this.members = members;
    }

    /**
     * Gets the ellipsoid of the datum ensemble.
     *
     * @return the datum ensemble ellipsoid
     */
    public Ellipsoid getEllipsoid() {
      return ellipsoid;
    }

    /**
     * Sets the ellipsoid of the datum ensemble.
     *
     * @param ellipsoid the datum ensemble ellipsoid
     */
    public void setEllipsoid(Ellipsoid ellipsoid) {
      this.ellipsoid = ellipsoid;
    }

    /**
     * Gets the accuracy of the datum ensemble.
     *
     * @return the datum ensemble accuracy
     */
    public String getAccuracy() {
      return accuracy;
    }

    /**
     * Sets the accuracy of the datum ensemble.
     *
     * @param accuracy the datum ensemble accuracy
     */
    public void setAccuracy(String accuracy) {
      this.accuracy = accuracy;
    }

    /**
     * Gets the ID of the datum ensemble.
     *
     * @return the datum ensemble ID
     */
    public Id getId() {
      return id;
    }

    /**
     * Sets the ID of the datum ensemble.
     *
     * @param id the datum ensemble ID
     */
    public void setId(Id id) {
      this.id = id;
    }

    /** Represents a member in a datum ensemble */
    public static class Member {
      private String name;
      private Id id;

      /**
       * Gets the name of the member.
       *
       * @return the member name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the member.
       *
       * @param name the member name
       */
      public void setName(String name) {
        this.name = name;
      }

      /**
       * Gets the ID of the member.
       *
       * @return the member ID
       */
      public Id getId() {
        return id;
      }

      /**
       * Sets the ID of the member.
       *
       * @param id the member ID
       */
      public void setId(Id id) {
        this.id = id;
      }
    }
  }

  /** Represents a coordinate system */
  public static class CoordinateSystem {
    private String subtype;
    private List<Axis> axis;

    /**
     * Gets the subtype of the coordinate system.
     *
     * @return the coordinate system subtype
     */
    public String getSubtype() {
      return subtype;
    }

    /**
     * Sets the subtype of the coordinate system.
     *
     * @param subtype the coordinate system subtype
     */
    public void setSubtype(String subtype) {
      this.subtype = subtype;
    }

    /**
     * Gets the axis of the coordinate system.
     *
     * @return the coordinate system axis
     */
    public List<Axis> getAxis() {
      return axis;
    }

    /**
     * Sets the axis of the coordinate system.
     *
     * @param axis the coordinate system axis
     */
    public void setAxis(List<Axis> axis) {
      this.axis = axis;
    }

    /** Represents an axis in a coordinate system */
    public static class Axis {
      private String name;
      private String abbreviation;
      private String direction;
      private Object unit; // Can be string or Unit object

      /**
       * Gets the name of the axis.
       *
       * @return the axis name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the axis.
       *
       * @param name the axis name
       */
      public void setName(String name) {
        this.name = name;
      }

      /**
       * Gets the abbreviation of the axis.
       *
       * @return the axis abbreviation
       */
      public String getAbbreviation() {
        return abbreviation;
      }

      /**
       * Sets the abbreviation of the axis.
       *
       * @param abbreviation the axis abbreviation
       */
      public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
      }

      /**
       * Gets the direction of the axis.
       *
       * @return the axis direction
       */
      public String getDirection() {
        return direction;
      }

      /**
       * Sets the direction of the axis.
       *
       * @param direction the axis direction
       */
      public void setDirection(String direction) {
        this.direction = direction;
      }

      /**
       * Gets the unit of the axis.
       *
       * @return the axis unit
       */
      public Object getUnit() {
        return unit;
      }

      /**
       * Sets the unit of the axis.
       *
       * @param unit the axis unit
       */
      public void setUnit(Object unit) {
        this.unit = unit;
      }
    }
  }

  /** Represents a conversion */
  public static class Conversion {
    private String name;
    private Method method;
    private List<Parameter> parameters;

    /**
     * Gets the name of the conversion.
     *
     * @return the conversion name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the conversion.
     *
     * @param name the conversion name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the method of the conversion.
     *
     * @return the conversion method
     */
    public Method getMethod() {
      return method;
    }

    /**
     * Sets the method of the conversion.
     *
     * @param method the conversion method
     */
    public void setMethod(Method method) {
      this.method = method;
    }

    /**
     * Gets the parameters of the conversion.
     *
     * @return the conversion parameters
     */
    public List<Parameter> getParameters() {
      return parameters;
    }

    /**
     * Sets the parameters of the conversion.
     *
     * @param parameters the conversion parameters
     */
    public void setParameters(List<Parameter> parameters) {
      this.parameters = parameters;
    }

    /** Represents a method in a conversion */
    public static class Method {
      private String name;

      /**
       * Gets the name of the method.
       *
       * @return the method name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the method.
       *
       * @param name the method name
       */
      public void setName(String name) {
        this.name = name;
      }
    }

    /** Represents a parameter in a conversion */
    public static class Parameter {
      private String name;
      private double value;
      private Object unit; // Can be string or Unit object
      private Id id;

      /**
       * Gets the name of the parameter.
       *
       * @return the parameter name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the parameter.
       *
       * @param name the parameter name
       */
      public void setName(String name) {
        this.name = name;
      }

      /**
       * Gets the value of the parameter.
       *
       * @return the parameter value
       */
      public double getValue() {
        return value;
      }

      /**
       * Sets the value of the parameter.
       *
       * @param value the parameter value
       */
      public void setValue(double value) {
        this.value = value;
      }

      /**
       * Gets the unit of the parameter.
       *
       * @return the parameter unit
       */
      public Object getUnit() {
        return unit;
      }

      /**
       * Sets the unit of the parameter.
       *
       * @param unit the parameter unit
       */
      public void setUnit(Object unit) {
        this.unit = unit;
      }

      /**
       * Gets the ID of the parameter.
       *
       * @return the parameter ID
       */
      public Id getId() {
        return id;
      }

      /**
       * Sets the ID of the parameter.
       *
       * @param id the parameter ID
       */
      public void setId(Id id) {
        this.id = id;
      }
    }
  }

  /** Represents a transformation */
  public static class Transformation {
    private String name;
    private Method method;
    private List<Parameter> parameters;

    /**
     * Gets the name of the transformation.
     *
     * @return the transformation name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the transformation.
     *
     * @param name the transformation name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the method of the transformation.
     *
     * @return the transformation method
     */
    public Method getMethod() {
      return method;
    }

    /**
     * Sets the method of the transformation.
     *
     * @param method the transformation method
     */
    public void setMethod(Method method) {
      this.method = method;
    }

    /**
     * Gets the parameters of the transformation.
     *
     * @return the transformation parameters
     */
    public List<Parameter> getParameters() {
      return parameters;
    }

    /**
     * Sets the parameters of the transformation.
     *
     * @param parameters the transformation parameters
     */
    public void setParameters(List<Parameter> parameters) {
      this.parameters = parameters;
    }

    /** Represents a method in a transformation */
    public static class Method {
      private String name;

      /**
       * Gets the name of the method.
       *
       * @return the method name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the method.
       *
       * @param name the method name
       */
      public void setName(String name) {
        this.name = name;
      }
    }

    /** Represents a parameter in a transformation */
    public static class Parameter {
      private String name;
      private double value;
      private Object unit; // Can be string or Unit object
      private String type;

      @JsonProperty("file_name")
      private String fileName;

      private Id id;

      /**
       * Gets the name of the parameter.
       *
       * @return the parameter name
       */
      public String getName() {
        return name;
      }

      /**
       * Sets the name of the parameter.
       *
       * @param name the parameter name
       */
      public void setName(String name) {
        this.name = name;
      }

      /**
       * Gets the value of the parameter.
       *
       * @return the parameter value
       */
      public double getValue() {
        return value;
      }

      /**
       * Sets the value of the parameter.
       *
       * @param value the parameter value
       */
      public void setValue(double value) {
        this.value = value;
      }

      /**
       * Gets the unit of the parameter.
       *
       * @return the parameter unit
       */
      public Object getUnit() {
        return unit;
      }

      /**
       * Sets the unit of the parameter.
       *
       * @param unit the parameter unit
       */
      public void setUnit(Object unit) {
        this.unit = unit;
      }

      /**
       * Gets the type of the parameter.
       *
       * @return the parameter type
       */
      public String getType() {
        return type;
      }

      /**
       * Sets the type of the parameter.
       *
       * @param type the parameter type
       */
      public void setType(String type) {
        this.type = type;
      }

      /**
       * Gets the file name of the parameter.
       *
       * @return the parameter file name
       */
      public String getFileName() {
        return fileName;
      }

      /**
       * Sets the file name of the parameter.
       *
       * @param fileName the parameter file name
       */
      public void setFileName(String fileName) {
        this.fileName = fileName;
      }

      /**
       * Gets the ID of the parameter.
       *
       * @return the parameter ID
       */
      public Id getId() {
        return id;
      }

      /**
       * Sets the ID of the parameter.
       *
       * @param id the parameter ID
       */
      public void setId(Id id) {
        this.id = id;
      }
    }
  }

  /** Represents a base CRS */
  public static class BaseCrs {
    private String type;
    private String name;
    private Datum datum;
    private CoordinateSystem coordinateSystem;

    /**
     * Gets the type of the base CRS.
     *
     * @return the base CRS type
     */
    public String getType() {
      return type;
    }

    /**
     * Sets the type of the base CRS.
     *
     * @param type the base CRS type
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Gets the name of the base CRS.
     *
     * @return the base CRS name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the base CRS.
     *
     * @param name the base CRS name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the datum of the base CRS.
     *
     * @return the base CRS datum
     */
    public Datum getDatum() {
      return datum;
    }

    /**
     * Sets the datum of the base CRS.
     *
     * @param datum the base CRS datum
     */
    public void setDatum(Datum datum) {
      this.datum = datum;
    }

    /**
     * Gets the coordinate system of the base CRS.
     *
     * @return the base CRS coordinate system
     */
    public CoordinateSystem getCoordinateSystem() {
      return coordinateSystem;
    }

    /**
     * Sets the coordinate system of the base CRS.
     *
     * @param coordinateSystem the base CRS coordinate system
     */
    public void setCoordinateSystem(CoordinateSystem coordinateSystem) {
      this.coordinateSystem = coordinateSystem;
    }
  }
}
