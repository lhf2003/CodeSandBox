package com.lhf.codesandbox;


import cn.hutool.core.io.resource.ResourceUtil;
import com.lhf.codesandbox.model.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class JavaNativeCodeSandBox extends JavaCodeSandBoxTemplate {

    public static void main(String[] args) {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest ExecuteCodeRequest = new ExecuteCodeRequest();
        String userCode = ResourceUtil.readStr("SimpleComputer/MemoryError.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest.setCode(userCode);
        ExecuteCodeRequest.setLanguage("java");
        ExecuteCodeRequest.setInput(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse Execute = javaNativeCodeSandBox.doExecute(ExecuteCodeRequest);
        System.out.println(Execute);
    }

    @Override
    public ExecuteCodeResponse doExecute(ExecuteCodeRequest executeCodeRequest) {
        return super.doExecute(executeCodeRequest);
    }

}