package com.lhf.codesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ExecuteProcessMessage {
    private Integer exitValue;
    /**
     * 正确信息
     */
    private String successMessage;

    /**
     * 错误信息
     */
    private String errorMessage;

}