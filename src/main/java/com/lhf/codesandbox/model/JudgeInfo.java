package com.lhf.codesandbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 判题信息
 * @author ${USER}
 * @date ${DATE} ${TIME}
 */

@NoArgsConstructor
@Data
public class JudgeInfo {

    @JsonProperty("message")
    private String message;
    @JsonProperty("time")
    private Long time;
    @JsonProperty("memory")
    private Long memory;
}