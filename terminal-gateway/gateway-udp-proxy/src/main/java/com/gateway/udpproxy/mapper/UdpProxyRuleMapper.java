package com.gateway.udpproxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateway.udpproxy.entity.UdpProxyRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UdpProxyRuleMapper extends BaseMapper<UdpProxyRule> {
}
