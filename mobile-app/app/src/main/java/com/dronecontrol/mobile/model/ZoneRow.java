package com.dronecontrol.mobile.model;

/** 목록 화면에서 구역 한 줄 + (있다면) 대표 캠 하나를 같이 보여주기 위한 묶음. */
public class ZoneRow {

	public final Zone zone;
	public final Camera camera; // 없으면 null

	public ZoneRow(Zone zone, Camera camera) {
		this.zone = zone;
		this.camera = camera;
	}

}
