package com.lhf.codesandbox.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeRequest {
    /**
     * 输入数据
     */
    List<String> input;
    /**
     * 编程语言
     */
    String language;
    /**
     * 代码
     */
    String code;
}