package com.lhf.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lhf.codesandbox.model.*;
import com.lhf.codesandbox.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JavaCodeSandBoxTemplate implements CodeSandBox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse doExecute(ExecuteCodeRequest ExecuteCodeRequest) {
        List<String> inputList = ExecuteCodeRequest.getInput();
        String language = ExecuteCodeRequest.getLanguage();
        String code = ExecuteCodeRequest.getCode();

        // 1. 保存用户代码文件
        File userCodeFilePath = saveUserCodeFile(code);
        // 2. 编译用户代码
        ExecuteProcessMessage compileMessage = compileCodeFile(userCodeFilePath);
        // 3. 执行用户代码
        List<ExecuteProcessMessage> executeMessageList = executeCodeFile(userCodeFilePath, inputList);

        // 4. 封装响应信息
        ExecuteCodeResponse responseMessage = getResponseMessage(compileMessage, executeMessageList);

        // 5. 删除临时文件（用户已执行的代码文件）
        boolean del = clearCodeFile(userCodeFilePath);
        if (!del) {
            log.error("删除用户代码文件失败，文件名：{}", userCodeFilePath.getName());
        }
        log.info("删除用户代码文件成功");
        return responseMessage;
    }

    /**
     * 1. 保存用户代码文件
     *
     * @param code 用户提交的代码
     * @return
     */
    public File saveUserCodeFile(String code) {
        // 获取用户目录
        String userDir = System.getProperty("user.dir");
        // 设置全局根目录
        String globalCodePath = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，不存在就创建一个
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }

        // 生成用户代码文件路径
        String userCodePath = globalCodePath + File.separator + UUID.randomUUID()
                + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 用户代码文件路径（已写入用户代码）
        File userCodeFilePath = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFilePath;
    }

    /**
     * 2. 编译用户代码
     *
     * @param userCodeFilePath
     * @return
     */
    public ExecuteProcessMessage compileCodeFile(File userCodeFilePath) {
        ExecuteProcessMessage executeProcessMessage = null;
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFilePath.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            executeProcessMessage = ProcessUtil.getProcessMessage(compileProcess, "编译");
            // 代码编译时间
            executeProcessMessage.setTime(executeProcessMessage.getTime());
            System.out.println(executeProcessMessage);
        } catch (IOException e) {
//            return getErrorResponse(e);
            throw new RuntimeException(e);
        }
        return executeProcessMessage;
    }

    /**
     * 3. 执行用户代码
     *
     * @param userCodeFilePath
     * @param inputList
     * @return
     */
    public List<ExecuteProcessMessage> executeCodeFile(File userCodeFilePath, List<String> inputList) {
        List<ExecuteProcessMessage> executeProcessMessageList = new ArrayList<>();
        for (String input : inputList) {
            String executeCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFilePath.getParentFile().getAbsolutePath(), input);
            try {
                Process executeProcess = Runtime.getRuntime().exec(executeCmd);
                ExecuteProcessMessage executeProcessMessage = ProcessUtil.getProcessMessage(executeProcess, "运行");
                System.out.println(executeProcessMessage);
                executeProcessMessageList.add(executeProcessMessage);
            } catch (IOException e) {
//                return getErrorResponse(e);
                throw new RuntimeException(e);
            }
        }
        return executeProcessMessageList;
    }

    /**
     * 4. 封装响应信息
     *
     * @param compileMessage
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getResponseMessage(ExecuteProcessMessage compileMessage, List<ExecuteProcessMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> successOutputList = new ArrayList<>();
        // TODO 这里取所有执行操作输出用例中的最大耗时，可以优化
        long maxExecuteTime = 0;
        for (ExecuteProcessMessage executeProcessMessage : executeMessageList) {
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
        if (successOutputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        // 输出用例集合
        executeCodeResponse.setOutputList(successOutputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        Long compileTime = compileMessage.getTime();
        judgeInfo.setTime(compileTime + maxExecuteTime);
//        judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5. 删除用户代码目录及文件
     *
     * @param userCodeFilePath
     * @return
     */
    public boolean clearCodeFile(File userCodeFilePath) {
        boolean del = true;
        if (userCodeFilePath.getParentFile().exists()) {
            del = FileUtil.del(userCodeFilePath.getParentFile());
        }
        return del;
    }

    /**
     * 6. 处理错误响应
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