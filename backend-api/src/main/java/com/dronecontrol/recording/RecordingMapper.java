package com.dronecontrol.recording;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecordingMapper {

	@Insert("INSERT INTO recordings(camera_id, started_at, file_path) VALUES(#{cameraId}, #{startedAt}, #{filePath})")
	int insertRecording(@Param("cameraId") int cameraId, @Param("startedAt") String startedAt,
			@Param("filePath") String filePath);

	/** 구역 소속 카메라들의 녹화 이력 중, 시작시각이 [from, to] 구간에 걸치는 것을 조회 (재생 시점 기준 최신순). */
	@Select("SELECT r.id AS id, r.camera_id AS cameraId, "
			+ "DATE_FORMAT(r.started_at, '%Y-%m-%dT%H:%i:%s') AS startedAt, r.file_path AS filePath "
			+ "FROM recordings r JOIN cameras c ON r.camera_id = c.id "
			+ "WHERE c.zone_id = #{zoneId} AND r.started_at BETWEEN #{from} AND #{to} "
			+ "ORDER BY r.started_at DESC")
	List<Recording> selectByZoneAndRange(@Param("zoneId") int zoneId, @Param("from") String from, @Param("to") String to);

}
