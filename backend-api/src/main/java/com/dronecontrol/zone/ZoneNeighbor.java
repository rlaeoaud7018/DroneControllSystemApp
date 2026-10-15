package com.dronecontrol.zone;

public class ZoneNeighbor {

	private String direction; // N, E, S, W
	private int neighborId;

	public ZoneNeighbor() {
	}

	public ZoneNeighbor(String direction, int neighborId) {
		this.direction = direction;
		this.neighborId = neighborId;
	}

	public String getDirection() {
		return direction;
	}

	public void setDirection(String direction) {
		this.direction = direction;
	}

	public int getNeighborId() {
		return neighborId;
	}

	public void setNeighborId(int neighborId) {
		this.neighborId = neighborId;
	}

}
