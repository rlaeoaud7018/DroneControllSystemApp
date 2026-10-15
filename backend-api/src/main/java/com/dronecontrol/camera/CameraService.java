package com.dronecontrol.camera;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CameraService {

	@Autowired
	private CameraMapper cameraMapper;

	@Autowired
	private AnalysisServerClient analysisServerClient;

	public Camera createCamera(Camera camera) {
		int result = cameraMapper.insertCamera(camera);
		if (result <= 0) {
			return null;
		}
		camera.setStatus("STOPPED");
		camera.setProgress(0);
		return camera;
	}

	public List<Camera> listCamerasByZoneId(int zoneId) {
		return cameraMapper.selectCamerasByZoneId(zoneId);
	}

	public Camera getCamera(int id) {
		return cameraMapper.selectCameraById(id);
	}

	public UpdateResult updateCamera(int id, Camera patch) {
		Camera existing = cameraMapper.selectCameraById(id);
		if (existing == null) {
			return UpdateResult.notFound();
		}

		boolean changingSource = (patch.getSourceType() != null && !patch.getSourceType().equals(existing.getSourceType()))
				|| (patch.getSourceUri() != null && !patch.getSourceUri().equals(existing.getSourceUri()));

		if (changingSource && !"STOPPED".equals(existing.getStatus())) {
			return UpdateResult.runningConflict();
		}

		if (patch.getName() != null) {
			existing.setName(patch.getName());
		}
		if (patch.getSourceType() != null) {
			existing.setSourceType(patch.getSourceType());
		}
		if (patch.getSourceUri() != null) {
			existing.setSourceUri(patch.getSourceUri());
		}

		int result = cameraMapper.updateCamera(existing);
		return (result > 0) ? UpdateResult.ok(existing) : UpdateResult.notFound();
	}

	/**
	 * 캠 가동/정지. state는 "RUNNING" 또는 "STOPPED"만 허용한다.
	 * RUNNING 요청 시 analysis-server에 분석 시작을 지시하고, 실제 PREPARING→RUNNING 전환은
	 * analysis-server가 /internal/v1/cameras/{id}/status 로 보고해오는 값을 따른다 (여기서는 PREPARING까지만 낙관적으로 반영).
	 */
	public UpdateResult changeState(int id, String state) {
		if (!"RUNNING".equals(state) && !"STOPPED".equals(state)) {
			return UpdateResult.badRequest();
		}

		Camera existing = cameraMapper.selectCameraById(id);
		if (existing == null) {
			return UpdateResult.notFound();
		}

		if ("RUNNING".equals(state)) {
			boolean accepted = analysisServerClient.startAnalysis(existing);
			if (!accepted) {
				return UpdateResult.analysisServerUnavailable();
			}
			cameraMapper.updateStatus(id, "PREPARING");
			existing.setStatus("PREPARING");
			return UpdateResult.ok(existing);
		}

		analysisServerClient.stopAnalysis(id);
		cameraMapper.updateStatus(id, "STOPPED");
		existing.setStatus("STOPPED");
		return UpdateResult.ok(existing);
	}

	/** analysis-server가 보고하는 캠 상태/진행률 반영. 없는 캠이면 null. */
	public Camera reportStatus(int id, String status, int progress, String errorMessage) {
		Camera existing = cameraMapper.selectCameraById(id);
		if (existing == null) {
			return null;
		}

		int result = cameraMapper.updateStatusDetail(id, status, progress, errorMessage);
		if (result <= 0) {
			return null;
		}

		existing.setStatus(status);
		existing.setProgress(progress);
		existing.setErrorMessage(errorMessage);
		return existing;
	}

	public static class UpdateResult {
		public enum Code { OK, NOT_FOUND, RUNNING_CONFLICT, BAD_REQUEST, ANALYSIS_SERVER_UNAVAILABLE }

		private Code code;
		private Camera camera;

		private UpdateResult(Code code, Camera camera) {
			this.code = code;
			this.camera = camera;
		}

		public static UpdateResult ok(Camera camera) {
			return new UpdateResult(Code.OK, camera);
		}

		public static UpdateResult notFound() {
			return new UpdateResult(Code.NOT_FOUND, null);
		}

		public static UpdateResult runningConflict() {
			return new UpdateResult(Code.RUNNING_CONFLICT, null);
		}

		public static UpdateResult badRequest() {
			return new UpdateResult(Code.BAD_REQUEST, null);
		}

		public static UpdateResult analysisServerUnavailable() {
			return new UpdateResult(Code.ANALYSIS_SERVER_UNAVAILABLE, null);
		}

		public Code getCode() {
			return code;
		}

		public Camera getCamera() {
			return camera;
		}
	}

}
