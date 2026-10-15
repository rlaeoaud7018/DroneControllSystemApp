package com.dronecontrol.camera;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** analysis-server(Flask)의 /analyzer/v1/cameras 를 호출하는 클라이언트. */
@Component
public class AnalysisServerClient {

	@Value("${analysis-server.base-url}")
	private String baseUrl;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** @return 분석 시작 요청이 analysis-server에 정상적으로 접수됐는지 (202) */
	public boolean startAnalysis(Camera camera) {
		try {
			StartAnalysisRequest body = new StartAnalysisRequest(
					camera.getId(), camera.getSourceType(), camera.getSourceUri());

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/analyzer/v1/cameras"))
					.header("Content-Type", "application/json; charset=UTF-8")
					.timeout(Duration.ofSeconds(5))
					.POST(HttpRequest.BodyPublishers.ofByteArray(
							objectMapper.writeValueAsBytes(body)))
					.build();

			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			return response.statusCode() == 202;

		} catch (Exception e) {
			System.out.println("[AnalysisServerClient] 분석 시작 요청 실패: " + e.getMessage());
			return false;
		}
	}

	public void stopAnalysis(int cameraId) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/analyzer/v1/cameras/" + cameraId))
					.timeout(Duration.ofSeconds(5))
					.DELETE()
					.build();

			httpClient.send(request, HttpResponse.BodyHandlers.discarding());

		} catch (Exception e) {
			System.out.println("[AnalysisServerClient] 분석 정지 요청 실패: " + e.getMessage());
		}
	}

}
