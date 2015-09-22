package main.utils;

import java.util.Objects;

public class Point {
	
	public double x;
	public double y;
	
	public Point(double x, double y){
		this.x = x;
		this.y = y; 
	}
	@Override
	public int hashCode() {
		return Objects.hash(x,y);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		return true;
	}

	
	
}
