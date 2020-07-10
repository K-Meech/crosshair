package de.embl.cba.targeting;

import org.scijava.vecmath.Point3f;

public class PointAngle {
    private Point3f point;
    private Double angle;

    PointAngle(Point3f point, Double angle) {
        this.point = point;
        this.angle = angle;
    }

    public Point3f getPoint () {
        return point;
    }

    Double getAngle() {
        return angle;
    }
}