package com.dronecontrol.zone;

public class ZoneFlow {

	private int fromZoneId;
	private String fromZoneName;
	private int toZoneId;
	private String toZoneName;
	private int count;

	public int getFromZoneId() {
		return fromZoneId;
	}

	public void setFromZoneId(int fromZoneId) {
		this.fromZoneId = fromZoneId;
	}

	public String getFromZoneName() {
		return fromZoneName;
	}

	public void setFromZoneName(String fromZoneName) {
		this.fromZoneName = fromZoneName;
	}

	public int getToZoneId() {
		return toZoneId;
	}

	public void setToZoneId(int toZoneId) {
		this.toZoneId = toZoneId;
	}

	public String getToZoneName() {
		return toZoneName;
	}

	public void setToZoneName(String toZoneName) {
		this.toZoneName = toZoneName;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

}
