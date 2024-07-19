package com.lhf.codesandbox.model;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.lhf.codesandbox.utils.ProcessUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaCodeBox implements CodeSandBox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaCodeBox javaCodeBox = new JavaCodeBox();
        ExecuteCodeRequest ExecuteCodeRequest = new ExecuteCodeRequest();
        String userCode = ResourceUtil.readStr("SimpleComputer/Main.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest.setCode(userCode);
        ExecuteCodeRequest.setLanguage("java");
        ExecuteCodeRequest.setInput(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse Execute = javaCodeBox.Execute(ExecuteCodeRequest);
        System.out.println(Execute);
    }

    @Override
    public ExecuteCodeResponse Execute(ExecuteCodeRequest ExecuteCodeRequest) {
        List<String> inputList = ExecuteCodeRequest.getInput();
        String language = ExecuteCodeRequest.getLanguage();
        String code = ExecuteCodeRequest.getCode();

        // 1. 保存用户代码文件
        // 获取用户目录
        String userDir = System.getProperty("user.dir");
        // 设置全局根目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，不存在就创建一个
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 用户代码文件路径
        String userCodePath = globalCodePathName + File.separator + UUID.randomUUID()
                + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 生成完整路径
        File file = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 编译用户代码
        String compileCmd = String.format("javac -encoding utf-8 %s", file.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteProcessMessage ExecuteProcessMessage = ProcessUtil.getProcessMessage(compileProcess, "编译");
            System.out.println(ExecuteProcessMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 3. 执行用户代码
        for (String input : inputList) {
            String executeCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", file.getParentFile().getAbsolutePath(), input);
            try {
                Process ExecuteProcess = Runtime.getRuntime().exec(executeCmd);
                ExecuteProcessMessage ExecuteProcessMessage = ProcessUtil.getProcessMessage(ExecuteProcess, "运行");
                System.out.println(ExecuteProcessMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }
}