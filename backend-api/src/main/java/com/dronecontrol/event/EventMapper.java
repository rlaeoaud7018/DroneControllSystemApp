package com.dronecontrol.event;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EventMapper {

	@Insert("INSERT INTO events(zone_id, camera_id, level_no, occurred_at, density_at_event, threshold_density, status) "
			+ "VALUES(#{zoneId}, #{cameraId}, #{levelNo}, #{occurredAt}, #{densityAtEvent}, #{thresholdDensity}, 'UNCONFIRMED')")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertEvent(Event event);

	@Select("<script>"
			+ "SELECT id, zone_id AS zoneId, camera_id AS cameraId, level_no AS levelNo, occurred_at AS occurredAt, "
			+ "density_at_event AS densityAtEvent, threshold_density AS thresholdDensity, status, "
			+ "handled_by AS handledBy, handled_at AS handledAt, memo "
			+ "FROM events WHERE 1=1 "
			+ "<if test='zoneId != null'>AND zone_id = #{zoneId}</if> "
			+ "<if test='status != null'>AND status = #{status}</if> "
			+ "ORDER BY occurred_at DESC"
			+ "</script>")
	List<Event> selectEvents(@Param("zoneId") Integer zoneId, @Param("status") String status);

	@Select("SELECT id, zone_id AS zoneId, camera_id AS cameraId, level_no AS levelNo, occurred_at AS occurredAt, "
			+ "density_at_event AS densityAtEvent, threshold_density AS thresholdDensity, status, "
			+ "handled_by AS handledBy, handled_at AS handledAt, memo "
			+ "FROM events WHERE id = #{id}")
	Event selectById(int id);

	@Select("SELECT id, zone_id AS zoneId, camera_id AS cameraId, level_no AS levelNo, occurred_at AS occurredAt, "
			+ "density_at_event AS densityAtEvent, threshold_density AS thresholdDensity, status, "
			+ "handled_by AS handledBy, handled_at AS handledAt, memo "
			+ "FROM events WHERE zone_id = #{zoneId} AND status IN ('UNCONFIRMED','CONFIRMED') LIMIT 1")
	Event selectOpenEventForZone(int zoneId);

	@Update("UPDATE events SET status = #{status}, memo = #{memo}, handled_by = #{handledBy}, handled_at = #{handledAt} WHERE id = #{id}")
	int updateStatus(@Param("id") int id, @Param("status") String status, @Param("memo") String memo,
			@Param("handledBy") Integer handledBy, @Param("handledAt") String handledAt);

	@Select("SELECT zone_id AS zoneId, level_no AS levelNo, density_threshold AS densityThreshold "
			+ "FROM event_thresholds WHERE zone_id = #{zoneId} ORDER BY level_no")
	List<EventThreshold> selectThresholdsByZone(int zoneId);

	@Insert("INSERT INTO event_thresholds(zone_id, level_no, density_threshold) "
			+ "VALUES(#{zoneId}, #{levelNo}, #{densityThreshold}) "
			+ "ON DUPLICATE KEY UPDATE density_threshold = VALUES(density_threshold)")
	int upsertThreshold(EventThreshold threshold);

	@Delete("DELETE FROM event_thresholds WHERE zone_id = #{zoneId}")
	int deleteThresholdsByZone(int zoneId);

}
