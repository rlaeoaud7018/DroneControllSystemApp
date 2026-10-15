package com.dronecontrol.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Zone {

	public final int id;
	public final String name;
	public final String geometry;

	public Zone(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.name = json.getString("name");
		this.geometry = json.optString("geometry", null);
	}

}
