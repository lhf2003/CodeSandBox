package com.lhf.codesandbox.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeResponse {
    /**
     * 输出数据
     */
   private List<String> outputList;

    /**
     * 题目信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 状态码
     */
    private Integer status;
}