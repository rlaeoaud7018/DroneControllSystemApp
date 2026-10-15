package com.dronecontrol.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ZoneStat {

	public final double countAvg;
	public final double density;
	public final int dwellAvgSec;

	public ZoneStat(JSONObject json) throws JSONException {
		this.countAvg = json.getDouble("countAvg");
		this.density = json.getDouble("density");
		this.dwellAvgSec = json.getInt("dwellAvgSec");
	}

}
