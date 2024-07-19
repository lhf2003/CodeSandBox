package com.lhf.codesandbox.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeResponse {
    /**
     * 输出数据
     */
    List<String> output;

    /**
     * 题目信息
     */
    JudgeInfo judgeInfo;
}