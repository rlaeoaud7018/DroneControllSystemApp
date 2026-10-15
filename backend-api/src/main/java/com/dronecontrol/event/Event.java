package com.dronecontrol.event;

import java.math.BigDecimal;

public class Event {

	private int id;
	private int zoneId;
	private Integer cameraId;
	private int levelNo;
	private String occurredAt;
	private BigDecimal densityAtEvent;
	private BigDecimal thresholdDensity;
	private String status; // UNCONFIRMED, CONFIRMED, RESOLVED
	private Integer handledBy;
	private String handledAt;
	private String memo;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getZoneId() {
		return zoneId;
	}

	public void setZoneId(int zoneId) {
		this.zoneId = zoneId;
	}

	public Integer getCameraId() {
		return cameraId;
	}

	public void setCameraId(Integer cameraId) {
		this.cameraId = cameraId;
	}

	public int getLevelNo() {
		return levelNo;
	}

	public void setLevelNo(int levelNo) {
		this.levelNo = levelNo;
	}

	public String getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(String occurredAt) {
		this.occurredAt = occurredAt;
	}

	public BigDecimal getDensityAtEvent() {
		return densityAtEvent;
	}

	public void setDensityAtEvent(BigDecimal densityAtEvent) {
		this.densityAtEvent = densityAtEvent;
	}

	public BigDecimal getThresholdDensity() {
		return thresholdDensity;
	}

	public void setThresholdDensity(BigDecimal thresholdDensity) {
		this.thresholdDensity = thresholdDensity;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getHandledBy() {
		return handledBy;
	}

	public void setHandledBy(Integer handledBy) {
		this.handledBy = handledBy;
	}

	public String getHandledAt() {
		return handledAt;
	}

	public void setHandledAt(String handledAt) {
		this.handledAt = handledAt;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

}
