package com.dronecontrol.event;

import java.math.BigDecimal;

public class EventThreshold {

	private int zoneId;
	private int levelNo;
	private BigDecimal densityThreshold;

	public EventThreshold() {
	}

	public EventThreshold(int zoneId, int levelNo, BigDecimal densityThreshold) {
		this.zoneId = zoneId;
		this.levelNo = levelNo;
		this.densityThreshold = densityThreshold;
	}

	public int getZoneId() {
		return zoneId;
	}

	public void setZoneId(int zoneId) {
		this.zoneId = zoneId;
	}

	public int getLevelNo() {
		return levelNo;
	}

	public void setLevelNo(int levelNo) {
		this.levelNo = levelNo;
	}

	public BigDecimal getDensityThreshold() {
		return densityThreshold;
	}

	public void setDensityThreshold(BigDecimal densityThreshold) {
		this.densityThreshold = densityThreshold;
	}

}
