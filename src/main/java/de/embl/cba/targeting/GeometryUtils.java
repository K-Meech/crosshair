package de.embl.cba.targeting;

import bdv.util.Affine3DHelpers;
import bdv.util.Bdv;
import bdv.viewer.animate.SimilarityTransformAnimator;
import de.embl.cba.bdv.utils.BdvUtils;
import ij3d.Content;
import ij3d.Image3DUniverse;
import net.imglib2.RealPoint;
import net.imglib2.ops.parse.token.Real;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.apache.commons.math3.geometry.Vector;
import org.apache.commons.math3.linear.*;
import org.scijava.java3d.Transform3D;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Point3f;
import org.scijava.vecmath.Vector3d;

import java.util.*;
import java.util.stream.DoubleStream;

import static de.embl.cba.bdv.utils.BdvUtils.*;

public final class GeometryUtils {

    // from MOBIE
    public static void moveToPosition(Bdv bdv, double[] xyz, long durationMillis )
    {

        final AffineTransform3D currentViewerTransform = new AffineTransform3D();
        bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

        AffineTransform3D newViewerTransform = currentViewerTransform.copy();

        // ViewerTransform
        // applyInverse: coordinates in viewer => coordinates in image
        // apply: coordinates in image => coordinates in viewer

        final double[] locationOfTargetCoordinatesInCurrentViewer = new double[ 3 ];
        currentViewerTransform.apply( xyz, locationOfTargetCoordinatesInCurrentViewer );

        for ( int d = 0; d < 3; d++ )
        {
            locationOfTargetCoordinatesInCurrentViewer[ d ] *= -1;
        }

        newViewerTransform.translate( locationOfTargetCoordinatesInCurrentViewer );

        newViewerTransform.translate( getBdvWindowCenter( bdv ) );

        if ( durationMillis <= 0 )
        {
            bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( newViewerTransform );
            return;
        }
        else
        {
            final SimilarityTransformAnimator similarityTransformAnimator =
                    new SimilarityTransformAnimator(
                            currentViewerTransform,
                            newViewerTransform,
                            0,
                            0,
                            durationMillis );

            bdv.getBdvHandle().getViewerPanel().setTransformAnimator( similarityTransformAnimator );
            bdv.getBdvHandle().getViewerPanel().transformChanged( currentViewerTransform );
        }
    }


    // from MOBIE
    public static void levelCurrentView( Bdv bdv, double[] targetNormalVector )
    {

        double[] currentNormalVector = BdvUtils.getCurrentViewNormalVector( bdv );

        AffineTransform3D currentViewerTransform = new AffineTransform3D();
        bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

        LinAlgHelpers.normalize( targetNormalVector ); // just to be sure.

        // determine rotation axis
        double[] rotationAxis = new double[ 3 ];
        LinAlgHelpers.cross( currentNormalVector, targetNormalVector, rotationAxis );
        if ( LinAlgHelpers.length( rotationAxis ) > 0 ) LinAlgHelpers.normalize( rotationAxis );

        // The rotation axis is in the coordinate system of the original data set => transform to viewer coordinate system
        double[] qCurrentRotation = new double[ 4 ];
        Affine3DHelpers.extractRotation( currentViewerTransform, qCurrentRotation );
        final AffineTransform3D currentRotation = quaternionToAffineTransform3D( qCurrentRotation );

        double[] rotationAxisInViewerSystem = new double[ 3 ];
        currentRotation.apply( rotationAxis, rotationAxisInViewerSystem );

        // determine rotation angle
        double angle = - Math.acos( LinAlgHelpers.dot( currentNormalVector, targetNormalVector ) );

        // construct rotation of angle around axis
        double[] rotationQuaternion = new double[ 4 ];
        LinAlgHelpers.quaternionFromAngleAxis( rotationAxisInViewerSystem, angle, rotationQuaternion );
        final AffineTransform3D rotation = quaternionToAffineTransform3D( rotationQuaternion );

        // apply transformation (rotating around current viewer centre position)
        final AffineTransform3D translateCenterToOrigin = new AffineTransform3D();
        translateCenterToOrigin.translate( DoubleStream.of( getBdvWindowCenter( bdv )).map(x -> -x ).toArray() );

        final AffineTransform3D translateCenterBack = new AffineTransform3D();
        translateCenterBack.translate( getBdvWindowCenter( bdv ) );

        ArrayList< AffineTransform3D > viewerTransforms = new ArrayList<>(  );

        viewerTransforms.add( currentViewerTransform.copy()
                .preConcatenate( translateCenterToOrigin )
                .preConcatenate( rotation )
                .preConcatenate( translateCenterBack ) );

        changeBdvViewerTransform( bdv, viewerTransforms, 500 );

    }



    public static ArrayList<Vector3d> fit_plane_to_points (ArrayList<RealPoint> points) {
        // Solution as here: https://math.stackexchange.com/questions/99299/best-fitting-plane-given-a-set-of-points
        // good explanation of svds: https://en.wikipedia.org/wiki/Singular_value_decomposition
//        convert to a real matrix
        double [] [] point_array = new double [points.size()][3];
        for (int i=0; i<points.size(); i++) {
            double[] position = new double[3];
            points.get(i).localize(position);

            point_array[i] = position;
        }
        System.out.println(point_array.toString());



//        Convert to real matrix as here: http://commons.apache.org/proper/commons-math/userguide/linear.html
        RealMatrix point_matrix = MatrixUtils.createRealMatrix(point_array);

        //        Calculate centroid
        RealVector centroid = new ArrayRealVector(new double[] {0,0,0});
        for (int i=0; i<points.size(); i++) {
            centroid = centroid.add(point_matrix.getRowVector(i));
        }
        centroid.mapDivideToSelf(points.size());

        //subtract centroid from every row
        for (int i=0; i<points.size(); i++) {
            RealVector row = point_matrix.getRowVector(i);
            point_matrix.setRowVector(i, row.subtract(centroid));
        }

        RealMatrix transposed_matrix = point_matrix.transpose();
        SingularValueDecomposition svd = new SingularValueDecomposition(transposed_matrix);
        double[] singular_values = svd.getSingularValues();

        // get index of minimum singular value
        Double min_value = null;
        int index = 0;
        for (int i=0; i<singular_values.length; i++) {
            if (min_value == null) {
                min_value = singular_values[i];
                index = i;
            } else {
                if (singular_values[i] < min_value) {
                    min_value = singular_values[i];
                    index = i;
                }
            }
        }

        // get corresponding left singular vector
        RealVector plane_normal = svd.getU().getColumnVector(index);

        // return plane normal and centroid as vector 3d
        Vector3d final_plane_normal = new Vector3d(plane_normal.toArray());
        // normalie just in case
        final_plane_normal.normalize();
        Vector3d final_plane_point = new Vector3d(centroid.toArray());
        ArrayList<Vector3d> result = new ArrayList<>();
        result.add(final_plane_normal);
        result.add(final_plane_point);

        return result;

    }

    public static Vector3d get_centroid (ArrayList<Vector3d> points) {
        Vector3d centroid = new Vector3d(new double[] {0,0,0});
        for (Vector3d v : points) {
            centroid.add(v);
        }
        centroid.setX(centroid.getX()/points.size());
        centroid.setY(centroid.getY()/points.size());
        centroid.setZ(centroid.getZ()/points.size());
        return centroid;
    }

    public static ArrayList<Point3f> calculate_triangles_from_points (ArrayList<Vector3d> intersections, Vector3d plane_normal) {
        // TODO -maybe calculate plane normal directly from points? Avoids any issues with transformations...
        Vector3d centroid = get_centroid(intersections);
        Vector3d centroid_to_point = new Vector3d();
        centroid_to_point.sub(intersections.get(0), centroid);

        Double[] signed_angles = new Double[intersections.size()];
//		angle of point to itself is zero
        signed_angles[0] = 0.0;
        for (int i=1; i<intersections.size(); i++) {
            Vector3d centroid_to_current_point = new Vector3d();
            centroid_to_current_point.sub(intersections.get(i), centroid);
            signed_angles[i] = calculate_signed_angle(centroid_to_point, centroid_to_current_point, plane_normal);
        }

        // convert all intersections to point3f
        ArrayList<Point3f> intersections_3f = new ArrayList<>();
        for (Vector3d d : intersections) {
            intersections_3f.add(vector3d_to_point3f(d));
        }

        // order intersections_without_root with respect ot the signed angles
        ArrayList<point_angle> points_and_angles = new ArrayList<>();
        for (int i = 0; i<intersections_3f.size(); i++) {
            points_and_angles.add(new point_angle(intersections_3f.get(i), signed_angles[i]));
        }

        Collections.sort(points_and_angles, (p1, p2) -> p1.getAngle().compareTo(p2.getAngle()));

        ArrayList<Point3f> triangles = new ArrayList<>();
        for (int i = 1; i<points_and_angles.size() - 1; i++) {
            triangles.add(points_and_angles.get(0).getPoint());
            triangles.add(points_and_angles.get(i).getPoint());
            triangles.add(points_and_angles.get(i + 1).getPoint());
        }

        return triangles;
    }

    public static Point3f vector3d_to_point3f (Vector3d vector) {
        Point3f new_point = new Point3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
        return new_point;
    }

    public static double calculate_signed_angle (Vector3d vector1, Vector3d vector2, Vector3d plane_normal) {
        double unsigned_angle = vector1.angle(vector2);
        Vector3d cross_vector1_vector2 = new Vector3d();
        cross_vector1_vector2.cross(vector1, vector2);

        double sign = plane_normal.dot(cross_vector1_vector2);
        if (sign < 0) {
            return -unsigned_angle;
        } else {
            return unsigned_angle;
        }
    }

    public static Vector3d calculate_normal_from_points (ArrayList<double[]> points) {
        double[] point_a = points.get(0);
        double[] point_b = points.get(1);
        double[] point_c = points.get(2);

        double[] vector_1 = new double[3];
        double[] vector_2 = new double[3];

        for ( int i = 0; i < 3; i++ ) {
            vector_1[i] = point_a[i] - point_b[i];
            vector_2[i] = point_c[i] - point_b[i];
        }

        Vector3d normal = new Vector3d();
        normal.cross(new Vector3d(vector_1), new Vector3d(vector_2));
        normal.normalize();

        return normal;
    }

    public static ArrayList<Vector3d> calculate_intersections (double[] global_min, double[] global_max, Vector3d plane_normal, Vector3d plane_point, Content imageContent, Image3DUniverse universe) {
        ArrayList<Vector3d> bounding_box_points = new ArrayList<>();
        bounding_box_points.add(new Vector3d (global_min[0], global_min[1], global_min[2]));
        bounding_box_points.add(new Vector3d (global_min[0], global_min[1], global_max[2]));
        bounding_box_points.add(new Vector3d (global_min[0], global_max[1], global_min[2]));
        bounding_box_points.add(new Vector3d (global_min[0], global_max[1], global_max[2]));
        bounding_box_points.add(new Vector3d (global_max[0], global_min[1], global_min[2]));
        bounding_box_points.add(new Vector3d (global_max[0], global_min[1], global_max[2]));
        bounding_box_points.add(new Vector3d (global_max[0], global_max[1], global_min[2]));
        bounding_box_points.add(new Vector3d (global_max[0], global_max[1], global_max[2]));

//        //TODO -remove - checks where bounding box is predicted to be
//        // plot these for testing
//        List<Point3f> transformed_points = new ArrayList<>();
//        Transform3D translate = new Transform3D();
//        Transform3D rotate = new Transform3D();
//        imageContent.getLocalTranslate(translate);
//        imageContent.getLocalRotate(rotate);
//        for (Vector3d b_point : bounding_box_points) {
//            Point3d pp = new Point3d();
//            b_point.get(pp);
////            Vector3d b_point_copy = new Vector3d(b_point.getX(), b_point.getY(), b_point.getZ());
//            rotate.transform(pp);
//            translate.transform(pp);
//            Point3f poi = new Point3f((float) pp.getX(), (float) pp.getY(), (float) pp.getZ());
//            transformed_points.add(poi);
//        }
//        universe.removeContent("yo");
//        universe.addPointMesh(transformed_points, new Color3f(0, 1, 0), "yo");

        //enumerate all combos of two points on edges
        ArrayList<Vector3d[]> bounding_box_edges = new ArrayList<>();
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(0), bounding_box_points.get(1)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(0), bounding_box_points.get(4)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(1), bounding_box_points.get(5)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(4), bounding_box_points.get(5)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(7), bounding_box_points.get(5)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(3), bounding_box_points.get(7)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(7), bounding_box_points.get(6)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(6), bounding_box_points.get(4)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(2), bounding_box_points.get(0)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(2), bounding_box_points.get(6)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(2), bounding_box_points.get(3)});
        bounding_box_edges.add(new Vector3d[] {bounding_box_points.get(1), bounding_box_points.get(3)});

        ArrayList<Vector3d> intersection_points = new ArrayList<>();

        // check for intersection of plane with all points - if four intersect, return these as teh four points
        // deals with case where plane is on the bounding box edges
        ArrayList<Boolean> intersects = new ArrayList<>();
        for (Vector3d[] v: bounding_box_edges) {
            if (check_vector_lies_in_plane(v[0], v[1], plane_normal, plane_point)) {
                intersection_points.add(v[0]);
                intersection_points.add(v[1]);
                continue;
                // parallel but doesn't lie in plane so no intersections
            } else if (check_vector_plane_parallel(v[0], v[1], plane_normal)) {
                continue;
            } else {
                Vector3d intersection = calculate_vector_plane_intersection(v[0], v[1], plane_normal, plane_point);
                if (intersection.length() > 0) {
                    intersection_points.add(intersection);
                }
            }
        }

        // get rid of any repeat points
        Set<Vector3d> set = new HashSet<>(intersection_points);
        intersection_points.clear();
        intersection_points.addAll(set);

        return intersection_points;

    }
    //	https://stackoverflow.com/questions/5666222/3d-line-plane-intersection
    public static Vector3d calculate_vector_plane_intersection (Vector3d point1, Vector3d point2, Vector3d plane_normal, Vector3d plane_point) {
        Vector3d point_to_point = new Vector3d();
        point_to_point.sub(point2, point1);
        double dot_product_vector_plane_normal = point_to_point.dot(plane_normal);

        Vector3d plane_to_point_vector = new Vector3d();
        plane_to_point_vector.sub(point1, plane_point);
        double dot_product_plane_to_point_plane_normal = plane_to_point_vector.dot(plane_normal);
        double factor = -dot_product_plane_to_point_plane_normal / dot_product_vector_plane_normal;

        Vector3d result = new Vector3d();

        if (factor < 0 || factor > 1) {
            return result;
        }

        point_to_point.setX(point_to_point.getX()*factor);
        point_to_point.setY(point_to_point.getY()*factor);
        point_to_point.setZ(point_to_point.getZ()*factor);
        result.add(point1, point_to_point);
        return result;
    }

    public static boolean check_vector_lies_in_plane (Vector3d point1, Vector3d point2, Vector3d plane_normal, Vector3d plane_point) {
        // vector between provided points
        boolean vector_plane_parallel = check_vector_plane_parallel (point1, point2, plane_normal);
        boolean point_plane_intersect = check_point_plane_intersection(point1, plane_normal, plane_point);

        return vector_plane_parallel && point_plane_intersect;
    }

    public static boolean check_vector_plane_parallel (Vector3d point1, Vector3d point2, Vector3d plane_normal) {
        // vector between provided points
        Vector3d point_to_point = new Vector3d();
        point_to_point.sub(point1, point2);

        double dot_product = point_to_point.dot(plane_normal);
        return dot_product == 0;
    }

    public static boolean check_point_plane_intersection (Vector3d point, Vector3d plane_normal, Vector3d plane_point) {
        Vector3d point_to_plane_vector = new Vector3d();
        point_to_plane_vector.sub(plane_point, point);

        double dot_product = point_to_plane_vector.dot(plane_normal);
        return dot_product == 0;
    }

}