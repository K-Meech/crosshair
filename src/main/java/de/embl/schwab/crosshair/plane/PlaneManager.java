package de.embl.schwab.crosshair.plane;

import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import customnode.CustomTriangleMesh;
import de.embl.schwab.crosshair.Crosshair;
import de.embl.schwab.crosshair.bdv.PointsOverlaySizeChange;
import de.embl.schwab.crosshair.utils.BdvUtils;
import de.embl.schwab.crosshair.utils.GeometryUtils;
import ij.IJ;
import ij3d.Content;
import ij3d.Image3DUniverse;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.java3d.Transform3D;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Point3f;
import org.scijava.vecmath.Vector3d;
import vib.BenesNamedPoint;
import vib.PointList;

import java.awt.Color;
import java.util.*;

import static de.embl.cba.bdv.utils.BdvUtils.getBdvWindowCentre;
import static de.embl.cba.bdv.utils.BdvUtils.moveToPosition;

public class PlaneManager {

    private boolean isTrackingPlane = false;
    private String trackedPlaneName;

    private boolean isInPointMode = false;
    private boolean isInVertexMode = false;

    private PlaneCreator planeCreator;

    private double distanceBetweenPlanesThreshold;

    private final Map<String, Plane> planeNameToPlane;

    private final Map<String, RealPoint> namedVertices;
    private final Map<String, RealPoint> selectedVertex;
    private final ArrayList<RealPoint> pointsToFitPlane;
    private final ArrayList<RealPoint> blockVertices;

    private final BdvHandle bdvHandle;
    private final BdvStackSource bdvStackSource;
    private final Image3DUniverse universe;
    private final Content imageContent;
    private PointsOverlaySizeChange pointOverlay;

    private final Color3f alignedPlaneColour = new Color3f(1, 0, 0);



    // Given image content is used to define the extent of planes (only shown within bounds of that image)
    // and where points are shown (again attached to that image)
    public PlaneManager(BdvStackSource bdvStackSource, Image3DUniverse universe, Content imageContent) {
        planeNameToPlane = new HashMap<>();
        selectedVertex = new HashMap<>();

        namedVertices = new HashMap<>();
        pointsToFitPlane = new ArrayList<>();
        blockVertices = new ArrayList<>();

        this.planeCreator = new PlaneCreator( universe, imageContent );

        this.bdvStackSource = bdvStackSource;
        this.bdvHandle = bdvStackSource.getBdvHandle();
        this.universe = universe;
        this.imageContent = imageContent;

        // TODO - make this threshold user definable - makes sense for microns, but possibly not for other units
        distanceBetweenPlanesThreshold = 1E-10;
    }

    public Map<String, RealPoint>getSelectedVertex() {
        return selectedVertex;
    }

    public Plane getPlane( String planeName ) {
        return planeNameToPlane.get( planeName );
    }

    public Map<String, RealPoint> getNamedVertices() {
        return namedVertices;
    }

    public ArrayList<RealPoint> getPointsToFitPlane() {return pointsToFitPlane;}

    public ArrayList<RealPoint> getBlockVertices() {return blockVertices;}

    public boolean isTrackingPlane() { return isTrackingPlane; }

    public void setTrackingPlane( boolean tracking ) { isTrackingPlane = tracking; }

    public void setTrackedPlaneName(String trackedPlaneName) {
        this.trackedPlaneName = trackedPlaneName;
    }

    public String getTrackedPlaneName() {
        return trackedPlaneName;
    }

    public void setPointOverlay (PointsOverlaySizeChange pointOverlay) {
        this.pointOverlay = pointOverlay;
    }

    public boolean isInPointMode() { return isInPointMode; }

    public void setPointMode( boolean isInPointMode ) {
        this.isInPointMode = isInPointMode;
        pointOverlay.setPointMode( isInPointMode );
    }

    public boolean isInVertexMode() {
        return isInVertexMode;
    }

    public void setVertexMode( boolean isInVertexMode ) {
        this.isInVertexMode = isInVertexMode;
        pointOverlay.setVertexMode( isInVertexMode );
    }

    public void setPlaneColour ( String planeName, Color colour) {
        planeNameToPlane.get( planeName ).setColor( colour );

        if (checkNamedPlaneExists( planeName )) {
            universe.getContent( planeName ).setColor( new Color3f( colour ) );
        }
    }

    public void setPlaneColourToAligned( String planeName ) {
        Color3f currentColour = universe.getContent( planeName ).getColor();
        Color3f alignedColour = new Color3f( alignedPlaneColour );
        if ( currentColour != alignedColour ) {
            universe.getContent( planeName ).setColor( alignedColour );
        }
    }

    public void setPlaneColourToUnaligned( String planeName ) {
        Color3f currentColour = universe.getContent( planeName ).getColor();
        Color3f notAlignedColour = new Color3f( planeNameToPlane.get(planeName).getColor() );
        if (currentColour != notAlignedColour) {
            universe.getContent( planeName ).setColor( notAlignedColour );
        }
    }

    public void setPlaneTransparency( String planeName, float transparency ) {
        planeNameToPlane.get( planeName ).setTransparency( transparency );

        if ( checkNamedPlaneExists( planeName ) ) {
            universe.getContent( planeName ).setTransparency( transparency );
        }
    }

    public void nameSelectedVertex(String name) {
        if (!selectedVertex.containsKey("selected")) {
            IJ.log("No vertex selected");
        } else {

            RealPoint selectedPointCopy = new RealPoint(selectedVertex.get("selected"));
            nameVertex(name, selectedPointCopy);
        }
    }

    public void nameVertex (String name, RealPoint vertex) {
        RealPoint vertexCopy = new RealPoint(vertex);
        if (name.equals("Top Left")) {
            renamePoint3D(imageContent, vertexCopy, "TL");
            addNamedVertexBdv(name, vertexCopy);
        } else if (name.equals("Top Right")) {
            renamePoint3D(imageContent, vertexCopy, "TR");
            addNamedVertexBdv(name, vertexCopy);
        } else if (name.equals("Bottom Left")) {
            renamePoint3D(imageContent, vertexCopy, "BL");
            addNamedVertexBdv(name, vertexCopy);
        } else if (name.equals("Bottom Right")) {
            renamePoint3D(imageContent, vertexCopy, "BR");
            addNamedVertexBdv(name, vertexCopy);
        }
    }

    private void addNamedVertexBdv (String vertexName, RealPoint point) {
        removeMatchingNamedVertices(point);
        namedVertices.put(vertexName, point);
        bdvHandle.getViewerPanel().requestRepaint();
    }

    private void removeMatchingNamedVertices (RealPoint point) {
        ArrayList<String> keysToRemove = new ArrayList<>();
        for (String key : namedVertices.keySet()) {
            if ( GeometryUtils.checkTwoRealPointsSameLocation(namedVertices.get(key), point)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            namedVertices.remove(key);
        }
    }

    private void removeMatchingSelectdVertices (RealPoint point) {
        if (selectedVertex.containsKey("selected")) {
            if (GeometryUtils.checkTwoRealPointsSameLocation(selectedVertex.get("selected"), point)) {
                selectedVertex.remove("selected");
            }
        }
    }

    private void renamePoint3D(Content content, RealPoint point, String name) {
        // rename any points with that name to "" to enforce only one point with each name
        BenesNamedPoint existingPointWithName = content.getPointList().get(name);
        if (existingPointWithName != null) {
            content.getPointList().rename(existingPointWithName, "");
        }

        double[] pointCoord = new double[3];
        point.localize(pointCoord);
        int pointIndex = content.getPointList().indexOfPointAt(pointCoord[0], pointCoord[1], pointCoord[2], content.getLandmarkPointSize());
        content.getPointList().rename(content.getPointList().get(pointIndex), name);
    }

    public boolean checkNamedPlaneExists(String name) {
        return planeNameToPlane.containsKey( name );
    }

    public void updatePlaneOnTransformChange(AffineTransform3D affineTransform3D, String planeName) {
        ArrayList<Vector3d> planeDefinition = getPlaneDefinitionFromViewTransform(affineTransform3D);
        updatePlane(planeDefinition.get(0), planeDefinition.get(1), planeName);
    }

    public void updatePlaneCurrentView (String planeName) {
        ArrayList<Vector3d> planeDefinition = getPlaneDefinitionOfCurrentView();
        updatePlane(planeDefinition.get(0), planeDefinition.get(1), planeName);
    }

    public void redrawCurrentPlanes () {
        for ( String planeName: planeNameToPlane.keySet() ) {
            Plane plane = planeNameToPlane.get( planeName );
            updatePlane( plane.getNormal(), plane.getPoint(), planeName );
        }
    }

    public ArrayList<Vector3d> getPlaneDefinitionOfCurrentView () {
        final AffineTransform3D transform = new AffineTransform3D();
        bdvHandle.getViewerPanel().state().getViewerTransform( transform );

        ArrayList<Vector3d> planeDefinition = getPlaneDefinitionFromViewTransform(transform);

        return planeDefinition;
    }

    private ArrayList<Vector3d> getPlaneDefinitionFromViewTransform(AffineTransform3D affineTransform3D) {
        final ArrayList< double[] > viewerPoints = new ArrayList<>();

        viewerPoints.add( new double[]{ 0, 0, 0 });
        viewerPoints.add( new double[]{ 0, 100, 0 });
        viewerPoints.add( new double[]{ 100, 0, 0 });

        final ArrayList< double[] > globalPoints = new ArrayList<>();
        for ( int i = 0; i < 3; i++ )
        {
            globalPoints.add( new double[ 3 ] );
        }

        for ( int i = 0; i < 3; i++ )
        {
            affineTransform3D.inverse().apply( viewerPoints.get( i ), globalPoints.get( i ) );
        }

        ArrayList<Vector3d> planeDefinition = new ArrayList<>();

        Vector3d planeNormal = GeometryUtils.calculateNormalFromPoints(globalPoints);
        Vector3d planePoint = new Vector3d(globalPoints.get(0)[0], globalPoints.get(0)[1], globalPoints.get(0)[2]);
        planeDefinition.add(planeNormal);
        planeDefinition.add(planePoint);

        return planeDefinition;
    }

    public double[] getGlobalViewCentre () {
        final AffineTransform3D transform = new AffineTransform3D();
        bdvHandle.getViewerPanel().state().getViewerTransform( transform );
        double[] centrePointView = getBdvWindowCentre(bdvStackSource);
        double[] centrePointGlobal = new double[3];
        transform.inverse().apply(centrePointView, centrePointGlobal);

        return centrePointGlobal;
    }

    public void updatePlane(Vector3d planeNormal, Vector3d planePoint, String planeName) {

        if ( checkNamedPlaneExists( planeName ) ) {
            planeCreator.updatePlane( getPlane( planeName ), planeNormal, planePoint );
        } else {
            Plane plane = planeCreator.createPlane( planeNormal, planePoint, planeName );
            planeNameToPlane.put(planeName, plane);
        }
    }

        public void moveViewToNamedPlane (String name) {
            // check if you're already at the plane
            ArrayList<Vector3d> planeDefinition = getPlaneDefinitionOfCurrentView();
            Vector3d currentPlaneNormal = planeDefinition.get(0);
            Vector3d currentPlanePoint = planeDefinition.get(1);

            Plane plane = planeNameToPlane.get( name );
            boolean normalsParallel = GeometryUtils.checkVectorsParallel( plane.getNormal(), currentPlaneNormal );
            double distanceToPlane = GeometryUtils.distanceFromPointToPlane( currentPlanePoint,
                    plane.getNormal(), plane.getPoint() );

            // units may want to be more or less strict
            // necessary due to double precision, will very rarely get exactly the same value
            boolean pointInPlane = distanceToPlane < distanceBetweenPlanesThreshold;

            if (normalsParallel & pointInPlane) {
                IJ.log("Already at that plane");
            } else {
                double[] targetNormal = new double[3];
                plane.getNormal().get( targetNormal );

                double[] targetCentroid = new double[3];
                plane.getCentroid().get( targetCentroid );
                moveToPosition(bdvStackSource, targetCentroid, 0, 0);
                if (!normalsParallel) {
                    BdvUtils.levelCurrentView(bdvStackSource, targetNormal);
                }
            }
        }

        public void addRemoveCurrentPositionPointsToFitPlane() {
            RealPoint point = getCurrentPosition();
            addRemovePointFromPointList(pointsToFitPlane, point);
        }

        public void addRemoveCurrentPositionBlockVertices () {
            // Check if on the current block plane
            RealPoint point = getCurrentPosition();
            double[] position = new double[3];
            point.localize(position);

            Plane blockPlane = planeNameToPlane.get( Crosshair.block );
            double distanceToPlane = GeometryUtils.distanceFromPointToPlane(new Vector3d(position),
                    blockPlane.getNormal(), blockPlane.getPoint() );

            // units may want to be more or less strict
            if (distanceToPlane < distanceBetweenPlanesThreshold) {
                addRemovePointFromPointList(blockVertices, point);
            } else {
                IJ.log("Vertex points must lie on the block plane");
            }
        }

        public void addRemovePointFromPointList(ArrayList<RealPoint> points, RealPoint point) {
        // remove point if already present, otherwise add point
            double[] pointViewerCoords = convertToViewerCoordinates(point);

            boolean distanceMatch = false;
            for ( int i = 0; i < points.size(); i++ )
            {
                RealPoint currentPoint = points.get(i);
                double[] currentPointViewerCoords = convertToViewerCoordinates(currentPoint);
                double distance = GeometryUtils.distanceBetweenPoints(pointViewerCoords, currentPointViewerCoords);
                if (distance < 5) {
                    removePointFrom3DViewer(currentPoint);
                    // remove matching points from named vertices
                    removeMatchingNamedVertices(currentPoint);
                    // remove matching points from selected vertices
                    removeMatchingSelectdVertices(currentPoint);
                    points.remove(i);
                    bdvHandle.getViewerPanel().requestRepaint();

                    distanceMatch = true;
                    break;
                }

            }

            if (!distanceMatch) {
                points.add(point);
                bdvHandle.getViewerPanel().requestRepaint();

                double[] position = new double[3];
                point.localize(position);
                imageContent.getPointList().add("", position[0], position[1], position[2]);
            }

        }
    public void removeAllBlockVertices() {
        for (RealPoint point : blockVertices) {
            removePointFrom3DViewer(point);
        }
        namedVertices.clear();
        blockVertices.clear();
        selectedVertex.clear();
        bdvHandle.getViewerPanel().requestRepaint();
    }

    public void removeAllPointsToFitPlane() {
        for (RealPoint point : pointsToFitPlane) {
            removePointFrom3DViewer(point);
        }
        pointsToFitPlane.clear();
        bdvHandle.getViewerPanel().requestRepaint();
    }

    public void removeNamedPlane (String name) {
        if (checkNamedPlaneExists(name)) {
        //        remove block vertices as these are tied to a particular plane (unlike the points)
            if (name.equals( Crosshair.block )) {
                removeAllBlockVertices();
            }

            planeNameToPlane.remove( name );
            universe.removeContent(name);
        }

    }

    private void removePointFrom3DViewer (RealPoint point) {
        // remove from 3D view and bdv
        double[] chosenPointCoord = new double[3];
        point.localize(chosenPointCoord);

        int pointIndex = imageContent.getPointList().indexOfPointAt(chosenPointCoord[0], chosenPointCoord[1], chosenPointCoord[2], imageContent.getLandmarkPointSize());
        imageContent.getPointList().remove(pointIndex);

        //		There's a bug in how the 3D viewer displays points after one is removed. Currently, it just stops
        //		displaying the first point added (rather than the one you actually removed).
        //		Therefore here I remove all points and re-add them, to get the viewer to reset how it draws
        //		the points. Perhaps there's a more efficient way to get around this?
        PointList currentPointList = imageContent.getPointList().duplicate();
        imageContent.getPointList().clear();
        for (Iterator<BenesNamedPoint> it = currentPointList.iterator(); it.hasNext(); ) {
            BenesNamedPoint p = it.next();
            imageContent.getPointList().add(p);
        }
    }

    private RealPoint getCurrentPosition () {
        RealPoint point = new RealPoint(3);
        bdvHandle.getViewerPanel().getGlobalMouseCoordinates(point);
        return point;
    }

    private double[] getCurrentPositionViewerCoordinates () {
        RealPoint point = getCurrentPosition();
        double[] pointViewerCoords = convertToViewerCoordinates(point);
        return pointViewerCoords;
    }


    private double[] convertToViewerCoordinates (RealPoint point) {
        final AffineTransform3D transform = new AffineTransform3D();
        bdvHandle.getViewerPanel().state().getViewerTransform( transform );

        final double[] lPos = new double[ 3 ];
        final double[] gPos = new double[ 3 ];
        // get point position (in microns etc)
        point.localize(lPos);
        // get point position in viewer (I guess in pixel units?), so gpos[2] is the distance in pixels
        // from the current view plane
        transform.apply(lPos, gPos);

        return gPos;
    }

    public void toggleSelectedVertexCurrentPosition () {
        double[] pointViewerCoords = getCurrentPositionViewerCoordinates();
        for ( int i = 0; i < blockVertices.size(); i++ ) {
            double[] currentPointViewerCoords = convertToViewerCoordinates(blockVertices.get(i));
            double distance = GeometryUtils.distanceBetweenPoints(pointViewerCoords, currentPointViewerCoords);
            if (distance < 5) {
                RealPoint selection = new RealPoint(3);
                selection.setPosition(blockVertices.get(i));

                // if point already selected, deselect it, otherwise add
                if (selectedVertex.containsKey("selected")) {
                    if ( GeometryUtils.checkTwoRealPointsSameLocation(selectedVertex.get("selected"), selection)) {
                        selectedVertex.clear();
                        bdvHandle.getViewerPanel().requestRepaint();
                        break;
                    } else {
                        selectedVertex.put("selected", selection);
                        bdvHandle.getViewerPanel().requestRepaint();
                        break;
                    }
                } else {
                    selectedVertex.put("selected", selection);
                    bdvHandle.getViewerPanel().requestRepaint();
                    break;
                }
            }
        }
    }

    public void togglePlaneVisbility( String planeName ) {
        if ( checkNamedPlaneExists(planeName) ) {
            Plane plane = planeNameToPlane.get( planeName );
            boolean isVisibile = plane.isVisible();

            universe.getContent( planeName ).setVisible( !isVisibile );
            plane.setVisible( !isVisibile );
        }
    }

    public Boolean getVisiblityNamedPlane ( String name ) {
        if ( checkNamedPlaneExists( name ) ) {
            return planeNameToPlane.get( name ).isVisible();
        } else {
            return null;
        }
    }

    public boolean checkAllCrosshairPlanesPointsDefined() {
        boolean targetExists = checkNamedPlaneExists( Crosshair.target );
        boolean blockExists = checkNamedPlaneExists( Crosshair.block );

        boolean allVerticesExist = true;
        String[] vertexPoints = {"Top Left", "Top Right", "Bottom Left", "Bottom Right"};
        for (String vertexName: vertexPoints) {
            if (!namedVertices.containsKey(vertexName)) {
                allVerticesExist = false;
                break;
            }
        }

        if (targetExists & blockExists & allVerticesExist) {
            return true;
        } else {
            return false;
        }

    }
}
