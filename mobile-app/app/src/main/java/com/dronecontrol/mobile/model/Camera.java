package com.dronecontrol.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Camera {

	public final int id;
	public final int zoneId;
	public final String name;
	public String status;
	public int progress;

	public Camera(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.zoneId = json.getInt("zoneId");
		this.name = json.getString("name");
		this.status = json.optString("status", "STOPPED");
		this.progress = json.optInt("progress", 0);
	}

}
