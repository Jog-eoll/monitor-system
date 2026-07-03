package com.infopublish.client.entity.dto.precheck;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件校验结果汇总
 */
@Data
public class FileCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已校验内部文件数量 */
    private int checkedCount;

    /** 校验通过数量 */
    private int passedCount;

    /** 校验失败数量 */
    private int failedCount;

    /** 校验通过文件信息 */
    private List<PassedFile> passedFiles;

    /** 校验失败文件信息 */
    private List<FailedFile> failedFiles;

    /**
     * 空结果（无文件需要校验）
     */
    public static FileCheckResult empty() {
        FileCheckResult result = new FileCheckResult();
        result.setCheckedCount(0);
        result.setPassedCount(0);
        result.setFailedCount(0);
        result.setPassedFiles(new ArrayList<>());
        result.setFailedFiles(new ArrayList<>());
        return result;
    }

    /**
     * 添加通过文件
     */
    public void addPassedFile(PassedFile file) {
        if (passedFiles == null) {
            passedFiles = new ArrayList<>();
        }
        passedFiles.add(file);
        passedCount = passedFiles.size();
        checkedCount = passedCount + (failedFiles != null ? failedFiles.size() : 0);
    }

    /**
     * 添加失败文件
     */
    public void addFailedFile(FailedFile file) {
        if (failedFiles == null) {
            failedFiles = new ArrayList<>();
        }
        failedFiles.add(file);
        failedCount = failedFiles.size();
        checkedCount = (passedFiles != null ? passedFiles.size() : 0) + failedCount;
    }

    /**
     * 是否存在失败文件
     */
    public boolean hasFailure() {
        return failedFiles != null && !failedFiles.isEmpty();
    }
}
