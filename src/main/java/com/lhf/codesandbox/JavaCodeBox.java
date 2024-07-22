package com.lhf.codesandbox;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.lhf.codesandbox.model.*;
import com.lhf.codesandbox.utils.ProcessUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaCodeBox implements CodeSandBox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaCodeBox javaCodeBox = new JavaCodeBox();
        ExecuteCodeRequest ExecuteCodeRequest = new ExecuteCodeRequest();
        String userCode = ResourceUtil.readStr("SimpleComputer/MemoryError.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest.setCode(userCode);
        ExecuteCodeRequest.setLanguage("java");
        ExecuteCodeRequest.setInput(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse Execute = javaCodeBox.doExecute(ExecuteCodeRequest);
        System.out.println(Execute);
    }

    @Override
    public ExecuteCodeResponse doExecute(ExecuteCodeRequest ExecuteCodeRequest) {
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

        // 生成用户代码文件路径
        String userCodePath = globalCodePathName + File.separator + UUID.randomUUID()
                + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 用户代码文件路径（已写入用户代码）
        File userCodeFilePath = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 编译用户代码
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFilePath.getAbsolutePath());
        long compileTime = 0;
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteProcessMessage executeProcessMessage = ProcessUtil.getProcessMessage(compileProcess, "编译");
            compileTime = executeProcessMessage.getTime();
            System.out.println(executeProcessMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        // 3. 执行用户代码
        // 测试用例可能会有多个，用集合封装
        List<ExecuteProcessMessage> executeProcessMessageList = new ArrayList<>();
        for (String input : inputList) {
            String executeCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFilePath.getParentFile().getAbsolutePath(), input);
            try {
                Process executeProcess = Runtime.getRuntime().exec(executeCmd);
                ExecuteProcessMessage executeProcessMessage = ProcessUtil.getProcessMessage(executeProcess, "运行");
                System.out.println(executeProcessMessage);
                executeProcessMessageList.add(executeProcessMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }

        // 4. 封装响应信息
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> successOutputList = new ArrayList<>();
        // TODO 这里取所有执行操作输出用例中的最大耗时，可以优化
        long maxExecuteTime = 0;
        for (ExecuteProcessMessage executeProcessMessage : executeProcessMessageList) {
            if (StrUtil.isNotBlank(executeProcessMessage.getErrorMessage())) {
                // 用户代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            if (executeProcessMessage.getTime() != null) {
                maxExecuteTime = Math.max(maxExecuteTime, executeProcessMessage.getTime());
            }
            successOutputList.add(executeProcessMessage.getSuccessMessage());
        }
        // 所有测试用例正常执行
        if (successOutputList.size() == executeProcessMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        // 输出用例集合
        executeCodeResponse.setOutputList(successOutputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(compileTime + maxExecuteTime);
//        judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 删除临时文件（用户已执行的代码文件）
        if (userCodeFilePath.getParentFile().exists()) {
            FileUtil.del(userCodeFilePath.getParentFile());
            System.out.println("删除临时文件成功");
        }
        return executeCodeResponse;
    }

    /**
     * 错误响应
     *
     * @param e
     * @return ExecuteCodeResponse
     */
    public ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        // 代码沙箱错误
        executeCodeResponse.setStatus(2);
        return executeCodeResponse;
    }
}