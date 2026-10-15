package com.dronecontrol.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

public class User {

	public final int id;
	public final String loginId;
	public final String name;
	public final String role; // PRE_USER, USER, ADMIN, SUPER_ADMIN

	public User(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.loginId = json.getString("loginId");
		this.name = json.getString("name");
		this.role = json.getString("role");
	}

	public boolean isAdmin() {
		return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
	}

}
