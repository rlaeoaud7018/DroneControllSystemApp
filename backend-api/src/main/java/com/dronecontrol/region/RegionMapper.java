package com.dronecontrol.region;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RegionMapper {

	@Insert("INSERT INTO regions(parent_id, level, code, name) VALUES(#{parentId}, #{level}, #{code}, #{name})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertRegion(Region region);

	@Select("SELECT * FROM regions WHERE parent_id IS NULL")
	List<Region> selectTopLevelRegions();

	@Select("SELECT * FROM regions WHERE parent_id = #{parentId}")
	List<Region> selectRegionsByParentId(int parentId);

	@Select("SELECT * FROM regions WHERE id = #{id}")
	Region selectRegionById(int id);

	@Update("UPDATE regions SET name = #{name} WHERE id = #{id}")
	int updateRegion(Region region);

}
