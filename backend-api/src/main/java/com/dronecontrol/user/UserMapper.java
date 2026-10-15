package com.dronecontrol.user;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

	@Insert("INSERT INTO users(login_id, password_encoded, email, phone, name, role) "
			+ "VALUES(#{loginId}, #{passwordEncoded}, #{email}, #{phone}, #{name}, #{role})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertUser(User user);

	@Select("SELECT * FROM users WHERE login_id = #{loginId}")
	User selectByLoginId(String loginId);

	@Select("SELECT * FROM users WHERE id = #{id}")
	User selectById(int id);

	@Select("SELECT * FROM users WHERE role = #{role} ORDER BY id")
	List<User> selectByRole(String role);

	@Select("SELECT COUNT(*) FROM users WHERE role = 'SUPER_ADMIN'")
	int countSuperAdmins();

	@Update("UPDATE users SET email = #{email}, phone = #{phone}, name = #{name} WHERE id = #{id}")
	int updateProfile(User user);

	@Update("UPDATE users SET role = #{role} WHERE id = #{id}")
	int updateRole(@Param("id") int id, @Param("role") String role);

	@Update("UPDATE users SET password_encoded = #{passwordEncoded} WHERE id = #{id}")
	int updatePassword(@Param("id") int id, @Param("passwordEncoded") String passwordEncoded);

	@org.apache.ibatis.annotations.Delete("DELETE FROM users WHERE id = #{id}")
	int deleteUser(int id);

}
