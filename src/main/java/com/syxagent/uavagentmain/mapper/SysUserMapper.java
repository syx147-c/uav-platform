package com.syxagent.uavagentmain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syxagent.uavagentmain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);
}
