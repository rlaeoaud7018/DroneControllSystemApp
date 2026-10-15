package com.dronecontrol.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Event {

	public final int id;
	public final int zoneId;
	public final int levelNo;
	public final String occurredAt;
	public final double densityAtEvent;
	public final double thresholdDensity;
	public final String status; // UNCONFIRMED, CONFIRMED, RESOLVED
	public final String memo;

	public Event(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.zoneId = json.getInt("zoneId");
		this.levelNo = json.getInt("levelNo");
		this.occurredAt = json.getString("occurredAt");
		this.densityAtEvent = json.getDouble("densityAtEvent");
		this.thresholdDensity = json.getDouble("thresholdDensity");
		this.status = json.getString("status");
		this.memo = json.isNull("memo") ? null : json.getString("memo");
	}

}
