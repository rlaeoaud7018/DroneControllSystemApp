package com.dronecontrol.history;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkHistoryMapper {

	@Insert("INSERT INTO work_history(admin_id, action_type, target, detail) "
			+ "VALUES(#{adminId}, #{actionType}, #{target}, #{detail})")
	int insert(WorkHistory history);

	@Select("<script>"
			+ "SELECT id, admin_id AS adminId, action_type AS actionType, target, detail, created_at AS createdAt "
			+ "FROM work_history WHERE 1=1 "
			+ "<if test='adminId != null'>AND admin_id = #{adminId}</if> "
			+ "<if test='actionType != null'>AND action_type = #{actionType}</if> "
			+ "ORDER BY created_at DESC"
			+ "</script>")
	List<WorkHistory> select(@Param("adminId") Integer adminId, @Param("actionType") String actionType);

}
