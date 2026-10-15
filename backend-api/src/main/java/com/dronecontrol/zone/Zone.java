package com.dronecontrol.zone;

import java.math.BigDecimal;

public class Zone {

	private int id;
	private Integer dongId;
	private String name;
	private BigDecimal areaM2;
	private String geometry;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Integer getDongId() {
		return dongId;
	}

	public void setDongId(Integer dongId) {
		this.dongId = dongId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getAreaM2() {
		return areaM2;
	}

	public void setAreaM2(BigDecimal areaM2) {
		this.areaM2 = areaM2;
	}

	public String getGeometry() {
		return geometry;
	}

	public void setGeometry(String geometry) {
		this.geometry = geometry;
	}

}
