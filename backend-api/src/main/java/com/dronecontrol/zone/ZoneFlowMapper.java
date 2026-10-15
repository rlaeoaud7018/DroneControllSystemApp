package com.dronecontrol.zone;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ZoneFlowMapper {

	/** 분단위로 현재까지의 누적 outflow 값을 그대로 덮어쓴다(증분 합산 아님 — count = 최신 outflow 값). */
	@Insert("INSERT INTO zone_flows(from_zone_id, to_zone_id, at, count) "
			+ "VALUES(#{fromZoneId}, #{toZoneId}, #{at}, #{count}) "
			+ "ON DUPLICATE KEY UPDATE count = VALUES(count)")
	void upsertFlow(@Param("fromZoneId") int fromZoneId, @Param("toZoneId") int toZoneId,
			@Param("at") String at, @Param("count") int count);

	@Select("SELECT zf.from_zone_id AS fromZoneId, zfrom.name AS fromZoneName, "
			+ "zf.to_zone_id AS toZoneId, zto.name AS toZoneName, SUM(zf.count) AS count "
			+ "FROM zone_flows zf "
			+ "JOIN zones zfrom ON zf.from_zone_id = zfrom.id "
			+ "JOIN zones zto ON zf.to_zone_id = zto.id "
			+ "WHERE zfrom.dong_id = #{dongId} AND zf.at BETWEEN #{from} AND #{to} "
			+ "GROUP BY zf.from_zone_id, zfrom.name, zf.to_zone_id, zto.name "
			+ "ORDER BY count DESC")
	List<ZoneFlow> selectFlowsByDong(@Param("dongId") int dongId, @Param("from") String from, @Param("to") String to);

}
