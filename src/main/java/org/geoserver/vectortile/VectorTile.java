package org.geoserver.vectortile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import no.ecc.vectortile.VectorTileEncoder;

import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.platform.ServiceException;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class VectorTile {
	 /**
     * package file extension
     */
    public static final String EXTENSION = "pbf";

    /**
     * format mime type
     */
    public static final String MIME_TYPE = "application/x-vtile";

    /**
     * names/aliases for the format
     */
    public static final Collection<String> NAMES = Lists.newArrayList("vectortile", "vtile", "pbf");
    
    public File file;
    public VectorTile() throws IOException{
    	file = File.createTempFile("vectortile", "pbf");
    }
	public File getFile() {
		return file;
	}
	public void setFile(File file) {
		this.file = file;
	}
	public void add(byte[] encoded)  {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(encoded);	
			fos.flush();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			try {
				fos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	public void add(FeatureCollection featureCollection,CoordinateReferenceSystem sourceCRS, String layerName, int x, int y, int z){
		
		VectorTileEncoder encoder = new VectorTileEncoder();
		
		
		if (! (featureCollection instanceof SimpleFeatureCollection)) {
            throw new ServiceException("VectorTile OutputFormat does not support Complex Features.");
        }

        SimpleFeatureCollection features = (SimpleFeatureCollection)  featureCollection;

	// Add one or more features with a layer name, a Map with attributes and a JTS Geometry. 
	// The Geometry uses (0,0) in lower left and (256,256) in upper right.
        SimpleFeatureIterator it = features.features();
        while (it.hasNext()){
        	SimpleFeature sf = it.next();
        	Collection<Property>properties = sf.getProperties();
        	Map<String,Object>attributes = propertiesToAttributes(properties);
        	Geometry geometry = (Geometry) sf.getDefaultGeometry();
        	Geometry tilegeometry = convertMapCoordsToTileCoords(geometry,z,sourceCRS);
        	encoder.addFeature(layerName, attributes, tilegeometry); 
        }
			
		

		// Finally, get the byte array
		byte[] encoded = encoder.encode();
		add(encoded);
		

	}
    

	public static int tilesize = 256;
   public static double originShift = 2 * Math.PI * 6378137 / 2.0;
    public static double initialResolution = 2 * Math.PI * 6378137 /tilesize;
    public static double resolution(int z){
    	return initialResolution/(Math.pow(2, z));
    }
    public static Coordinate convertTileCoordsToMapCoords(int z,double x, double y){
    	 /*def PixelsToMeters(self, px, py, zoom):
 	        "Converts pixel coordinates in given zoom level of pyramid to EPSG:900913"

 	        res = self.Resolution( zoom )
 	        mx = px * res - self.originShift
 	        my = py * res - self.originShift
 	        return mx, my*/
    	double res = resolution(z);
    	double mx = x * res - originShift;
    	double my = y * res - originShift;
    	return new Coordinate(mx,my);
    	
    }
    public static Geometry convertMapCoordsToTileCoords(Geometry geometry, 
			int z,CoordinateReferenceSystem sourceCRS)  {
    	//need to make sure these are 900913
    	Geometry targetGeometry = null;

    	try {
	    	CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:900913");
	    	MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);
			targetGeometry = JTS.transform( geometry, transform);
		} catch (MismatchedDimensionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAuthorityCodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FactoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	//self.originShift = 2 * math.pi * 6378137 / 2.0
    	//self.initialResolution = 2 * math.pi * 6378137 / self.tileSize
    	 //  def Resolution(self, zoom ):
    	 //       "Resolution (meters/pixel) for given zoom level (measured at Equator)"
    	        
    	 //       # return (2 * math.pi * 6378137) / (self.tileSize * 2**zoom)
    	 //       return self.initialResolution / (2**zoom)
       // res = self.Resolution( zoom )
       // px = (mx + self.originShift) / res
       // py = (my + self.originShift) / res
    	
    	if(targetGeometry!=null){
    	
	    	double res = resolution(z);
	    	Coordinate[]coords = geometry.getCoordinates();
	    	Coordinate[]coordsout = new Coordinate[coords.length];
	    	for(int i=0;i<coords.length;i++){
	    		Coordinate coord = coords[i];
	    		double px = coord.x;
	    		double py = coord.y;
	    		double pxout = (px + originShift)/res;
	    		double pyout = (py+originShift)/res;
	    		coordsout[i] = new Coordinate(pxout,pyout);
	    	}
	    	GeometryFactory fact = new GeometryFactory();
	    	if(geometry instanceof Point && coordsout.length==1){
	    		targetGeometry = fact.createPoint(coordsout[0]);
	    	}else if(geometry instanceof LineString){
	    		targetGeometry = fact.createLineString(coordsout);
	    	}else if (geometry instanceof Polygon){
	    		 LinearRing linear = new GeometryFactory().createLinearRing(coordsout);
	    		 targetGeometry =  new Polygon(linear, null, fact);
	    	}
    	}
    	return targetGeometry;
    	
	}
	Map<String,Object>propertiesToAttributes(Collection<Property>properties){
    	Map<String,Object>attributes=new HashMap<String,Object>();
    	Iterator<Property>it = properties.iterator();
    	while(it.hasNext()){
    		Property property = it.next();
    		attributes.put(property.getName().getLocalPart(), property.getValue());
    	}
    	return attributes;
    	
    }
	
	public static ReferencedEnvelope tileAddressToBBox(int z, int x, int y, CoordinateReferenceSystem targetCRS) throws NoSuchAuthorityCodeException, FactoryException, TransformException {
		Coordinate min = convertTileCoordsToMapCoords(z, x*tilesize, y*tilesize);
		Coordinate max = convertTileCoordsToMapCoords(z, (x+1)*tilesize, (y+1)*tilesize);
		/* From http://www.maptiler.org/google-maps-coordinates-tile-bounds-projection/
		 * minx, miny = self.PixelsToMeters( tx*self.tileSize, ty*self.tileSize, zoom )
		        maxx, maxy = self.PixelsToMeters( (tx+1)*self.tileSize, (ty+1)*self.tileSize, zoom )
		        return ( minx, miny, maxx, maxy ) 
		        
		 
	        
	               "Initialize the TMS Global Mercator pyramid"
	        self.tileSize = 256
	        self.initialResolution = 2 * math.pi * 6378137 / self.tileSize
	        # 156543.03392804062 for tileSize 256 pixels
	        self.originShift = 2 * math.pi * 6378137 / 2.0
	        # 20037508.342789244
		        */
		CoordinateReferenceSystem googleCRS = CRS.decode("EPSG:900913");
		ReferencedEnvelope googleenv = new ReferencedEnvelope(min.x, max.x, min.y, max.y, googleCRS);
	    return googleenv.transform(targetCRS, true);

	}
}
