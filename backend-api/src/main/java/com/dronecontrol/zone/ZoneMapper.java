package com.dronecontrol.zone;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ZoneMapper {

	@Insert("INSERT INTO zones(dong_id, name, area_m2, geometry) VALUES(#{dongId}, #{name}, #{areaM2}, #{geometry})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertZone(Zone zone);

	@Select("SELECT * FROM zones WHERE dong_id = #{dongId}")
	List<Zone> selectZonesByDongId(int dongId);

	@Select("SELECT * FROM zones WHERE id = #{id}")
	Zone selectZoneById(int id);

	@Update("UPDATE zones SET name = #{name}, area_m2 = #{areaM2}, geometry = #{geometry} WHERE id = #{id}")
	int updateZone(Zone zone);

	@Insert("INSERT INTO zone_neighbors(zone_id, direction, neighbor_id) "
			+ "VALUES(#{zoneId}, #{direction}, #{neighborId}) "
			+ "ON DUPLICATE KEY UPDATE neighbor_id = VALUES(neighbor_id)")
	int upsertNeighbor(@Param("zoneId") int zoneId, @Param("direction") String direction, @Param("neighborId") int neighborId);

	@Select("SELECT direction, neighbor_id AS neighborId FROM zone_neighbors WHERE zone_id = #{zoneId}")
	List<ZoneNeighbor> selectNeighbors(int zoneId);

}
