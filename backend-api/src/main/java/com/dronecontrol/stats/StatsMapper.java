package com.dronecontrol.stats;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StatsMapper {

	@Select("SELECT z.id AS zoneId, z.area_m2 AS areaM2 "
			+ "FROM cameras c JOIN zones z ON c.zone_id = z.id "
			+ "WHERE c.id = #{cameraId}")
	ZoneAreaInfo selectZoneAreaByCameraId(int cameraId);

	@Insert("INSERT INTO zone_stats_minute("
				+ "zone_id, at, count_avg, count_max, count_min, density, "
				+ "inflow_n, inflow_e, inflow_s, inflow_w, "
				+ "outflow_n, outflow_e, outflow_s, outflow_w, "
				+ "dwell_avg_sec, sample_count, avg_speed_px_s, "
				+ "move_e, move_ne, move_n, move_nw, move_w, move_sw, move_s, move_se) "
			+ "VALUES(#{zoneId}, #{minuteAt}, #{personCount}, #{personCount}, #{personCount}, #{density}, "
				+ "#{inN}, #{inE}, #{inS}, #{inW}, #{outN}, #{outE}, #{outS}, #{outW}, #{dwell}, 1, #{avgSpeedPxS}, "
				+ "#{moveE}, #{moveNe}, #{moveN}, #{moveNw}, #{moveW}, #{moveSw}, #{moveS}, #{moveSe}) "
			+ "ON DUPLICATE KEY UPDATE "
				+ "count_avg = (count_avg * sample_count + VALUES(count_avg)) / (sample_count + 1), "
				+ "count_max = GREATEST(count_max, VALUES(count_max)), "
				+ "count_min = LEAST(count_min, VALUES(count_min)), "
				+ "density = (density * sample_count + VALUES(density)) / (sample_count + 1), "
				+ "inflow_n = inflow_n + VALUES(inflow_n), "
				+ "inflow_e = inflow_e + VALUES(inflow_e), "
				+ "inflow_s = inflow_s + VALUES(inflow_s), "
				+ "inflow_w = inflow_w + VALUES(inflow_w), "
				+ "outflow_n = outflow_n + VALUES(outflow_n), "
				+ "outflow_e = outflow_e + VALUES(outflow_e), "
				+ "outflow_s = outflow_s + VALUES(outflow_s), "
				+ "outflow_w = outflow_w + VALUES(outflow_w), "
				+ "dwell_avg_sec = (dwell_avg_sec * sample_count + VALUES(dwell_avg_sec)) / (sample_count + 1), "
				+ "avg_speed_px_s = (avg_speed_px_s * sample_count + VALUES(avg_speed_px_s)) / (sample_count + 1), "
				+ "move_e = move_e + VALUES(move_e), "
				+ "move_ne = move_ne + VALUES(move_ne), "
				+ "move_n = move_n + VALUES(move_n), "
				+ "move_nw = move_nw + VALUES(move_nw), "
				+ "move_w = move_w + VALUES(move_w), "
				+ "move_sw = move_sw + VALUES(move_sw), "
				+ "move_s = move_s + VALUES(move_s), "
				+ "move_se = move_se + VALUES(move_se), "
				+ "sample_count = sample_count + 1")
	void upsertMinuteStat(@Param("zoneId") int zoneId, @Param("minuteAt") String minuteAt,
			@Param("personCount") int personCount, @Param("density") BigDecimal density,
			@Param("inN") int inN, @Param("inE") int inE, @Param("inS") int inS, @Param("inW") int inW,
			@Param("outN") int outN, @Param("outE") int outE, @Param("outS") int outS, @Param("outW") int outW,
			@Param("dwell") int dwell, @Param("avgSpeedPxS") double avgSpeedPxS,
			@Param("moveE") int moveE, @Param("moveNe") int moveNe, @Param("moveN") int moveN, @Param("moveNw") int moveNw,
			@Param("moveW") int moveW, @Param("moveSw") int moveSw, @Param("moveS") int moveS, @Param("moveSe") int moveSe);

	@Select("SELECT zone_id AS zoneId, at, count_avg AS countAvg, count_max AS countMax, count_min AS countMin, "
			+ "density, inflow_n AS inflowN, inflow_e AS inflowE, inflow_s AS inflowS, inflow_w AS inflowW, "
			+ "outflow_n AS outflowN, outflow_e AS outflowE, outflow_s AS outflowS, outflow_w AS outflowW, "
			+ "dwell_avg_sec AS dwellAvgSec, avg_speed_px_s AS avgSpeedPxS, "
			+ "move_e AS moveE, move_ne AS moveNe, move_n AS moveN, move_nw AS moveNw, "
			+ "move_w AS moveW, move_sw AS moveSw, move_s AS moveS, move_se AS moveSe "
			+ "FROM zone_stats_minute WHERE zone_id = #{zoneId} AND at BETWEEN #{from} AND #{to} ORDER BY at")
	List<ZoneMinuteStat> selectMinuteStats(@Param("zoneId") int zoneId, @Param("from") String from, @Param("to") String to);

	@Select("SELECT zone_id AS zoneId, at, count_avg AS countAvg, count_max AS countMax, count_min AS countMin, "
			+ "density, inflow_n AS inflowN, inflow_e AS inflowE, inflow_s AS inflowS, inflow_w AS inflowW, "
			+ "outflow_n AS outflowN, outflow_e AS outflowE, outflow_s AS outflowS, outflow_w AS outflowW, "
			+ "dwell_avg_sec AS dwellAvgSec, avg_speed_px_s AS avgSpeedPxS, "
			+ "move_e AS moveE, move_ne AS moveNe, move_n AS moveN, move_nw AS moveNw, "
			+ "move_w AS moveW, move_sw AS moveSw, move_s AS moveS, move_se AS moveSe "
			+ "FROM zone_stats_minute WHERE zone_id = #{zoneId} AND at = #{minuteAt}")
	ZoneMinuteStat selectMinuteStatAt(@Param("zoneId") int zoneId, @Param("minuteAt") String minuteAt);

	class ZoneAreaInfo {
		private int zoneId;
		private BigDecimal areaM2;

		public int getZoneId() {
			return zoneId;
		}

		public void setZoneId(int zoneId) {
			this.zoneId = zoneId;
		}

		public BigDecimal getAreaM2() {
			return areaM2;
		}

		public void setAreaM2(BigDecimal areaM2) {
			this.areaM2 = areaM2;
		}
	}

}
