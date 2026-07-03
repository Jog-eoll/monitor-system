package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.List;
/*

批量导入出参

*/
@Data
public class RoleBatchImportResp {
    private int total;
    private int success;
    private int skipped;
    private int failed;
    private List<String> errors;
}