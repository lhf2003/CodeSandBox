package com.lhf.codesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ExecuteProcessMessage {
    /**
     * 进程执行状态
     */
    private Integer exitValue;

    /**
     * 正确信息
     */
    private String successMessage;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行时间
     */
    private Long time;
    /**
     * 占用的内存
     */
    private Long memory;

}