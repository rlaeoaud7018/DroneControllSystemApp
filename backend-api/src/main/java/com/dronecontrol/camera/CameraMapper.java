package com.dronecontrol.camera;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CameraMapper {

	@Insert("INSERT INTO cameras(zone_id, name, source_type, source_uri, status) "
			+ "VALUES(#{zoneId}, #{name}, #{sourceType}, #{sourceUri}, 'STOPPED')")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertCamera(Camera camera);

	@Select("SELECT * FROM cameras WHERE zone_id = #{zoneId}")
	List<Camera> selectCamerasByZoneId(int zoneId);

	@Select("SELECT * FROM cameras WHERE id = #{id}")
	Camera selectCameraById(int id);

	@Update("UPDATE cameras SET name = #{name}, source_type = #{sourceType}, source_uri = #{sourceUri} WHERE id = #{id}")
	int updateCamera(Camera camera);

	@Update("UPDATE cameras SET status = #{status} WHERE id = #{id}")
	int updateStatus(@Param("id") int id, @Param("status") String status);

	@Update("UPDATE cameras SET status = #{status}, progress = #{progress}, error_message = #{errorMessage} WHERE id = #{id}")
	int updateStatusDetail(@Param("id") int id, @Param("status") String status,
			@Param("progress") int progress, @Param("errorMessage") String errorMessage);

}
