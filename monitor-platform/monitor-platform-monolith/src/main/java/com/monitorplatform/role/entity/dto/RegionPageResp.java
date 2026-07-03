package com.monitorplatform.role.entity.dto;

import com.monitorplatform.role.entity.Region;
import lombok.Data;

import java.util.List;

/*

分页出参

*/
@Data
public class RegionPageResp {

    private Long total;
    private List<Region> records;

}
