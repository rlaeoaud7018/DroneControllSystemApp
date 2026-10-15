package com.dronecontrol.camera;

/** analysis-server의 POST /analyzer/v1/cameras 로 보내는 요청 바디. */
public class StartAnalysisRequest {

	private int cameraId;
	private String sourceType;
	private String sourceUri;

	public StartAnalysisRequest(int cameraId, String sourceType, String sourceUri) {
		this.cameraId = cameraId;
		this.sourceType = sourceType;
		this.sourceUri = sourceUri;
	}

	public int getCameraId() {
		return cameraId;
	}

	public String getSourceType() {
		return sourceType;
	}

	public String getSourceUri() {
		return sourceUri;
	}

}
