package com.dronecontrol.stats;

import java.util.List;
import java.util.Map;

/** analysis-server가 POST /internal/v1/stats 로 보내는 초 단위 분석 결과 1건. */
public class StatSample {

	private int cameraId;
	private String at;
	private int personCount;
	private Map<String, Integer> in;
	private Map<String, Integer> out;
	private List<Integer> dwellSec;
	private double avgSpeedPxS;
	private Map<String, Integer> moves;

	public int getCameraId() {
		return cameraId;
	}

	public void setCameraId(int cameraId) {
		this.cameraId = cameraId;
	}

	public String getAt() {
		return at;
	}

	public void setAt(String at) {
		this.at = at;
	}

	public int getPersonCount() {
		return personCount;
	}

	public void setPersonCount(int personCount) {
		this.personCount = personCount;
	}

	public Map<String, Integer> getIn() {
		return in;
	}

	public void setIn(Map<String, Integer> in) {
		this.in = in;
	}

	public Map<String, Integer> getOut() {
		return out;
	}

	public void setOut(Map<String, Integer> out) {
		this.out = out;
	}

	public List<Integer> getDwellSec() {
		return dwellSec;
	}

	public void setDwellSec(List<Integer> dwellSec) {
		this.dwellSec = dwellSec;
	}

	public double getAvgSpeedPxS() {
		return avgSpeedPxS;
	}

	public void setAvgSpeedPxS(double avgSpeedPxS) {
		this.avgSpeedPxS = avgSpeedPxS;
	}

	public Map<String, Integer> getMoves() {
		return moves;
	}

	public void setMoves(Map<String, Integer> moves) {
		this.moves = moves;
	}

}
