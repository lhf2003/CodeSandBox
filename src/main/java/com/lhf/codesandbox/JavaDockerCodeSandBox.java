package com.lhf.codesandbox;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.lhf.codesandbox.model.*;
import com.lhf.codesandbox.utils.ProcessUtil;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaDockerCodeSandBox implements CodeSandBox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaCodeBox = new JavaDockerCodeSandBox();
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

        // 生成用户代码文件路径
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 将代码写入写入用户文件
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

        // 3. 创建docker容器，将用户代码传入容器运行
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
        //拉取镜像
        String image = "java:openjdk-8u111-alpine";
        if (FIRST_INIT) {
            System.out.println("首次拉取镜像......");
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println(item.getStatus());
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("镜像拉取异常");
                throw new RuntimeException(e);
            }
            System.out.println("镜像拉取成功！");
            FIRST_INIT = false;
        }

        //创建容器：
        //设置主机配置
        HostConfig hostConfig = new HostConfig();
        hostConfig.withCpuCount(1L);
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //指定镜像
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //构建交互式操作终端 docker run -v userCodeParentPath:/app --stderr --stdin --stdout --tty /bin/sh
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true) //等同于 -it
                .withCmd("/bin/sh") // 等同于 bash
                .exec();//执行创建容器的命令
        String containerId = createContainerResponse.getId();

        //启动容器 docker start <containerId>
        dockerClient.startContainerCmd(containerId).exec();

        //将输入用例依次执行
        List<ExecuteProcessMessage> executeProcessMessageList = new ArrayList<>();
        StopWatch stopWatch = new StopWatch();
        for (String input : inputList) {
            ExecuteProcessMessage executeProcessMessage = new ExecuteProcessMessage();
            String[] inputArr = input.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArr);
            //创建执行命令：docker exec -it <containerId> <cmdArray 里的具体命令>
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            String execId = execCreateCmdResponse.getId();
            //运行执行命令
            stopWatch.start();
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    executeProcessMessage.setMemory(usageMemory);
                    System.out.println("内存：" + usageMemory);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(execId);
            final String[] successMessage = {null};
            final String[] errorMessage = {null};
            try {
                execStartCmd.exec(new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (streamType.equals(StreamType.STDERR)) {
                            errorMessage[0] = new String(frame.getPayload());
                            executeProcessMessage.setErrorMessage(errorMessage[0]);
                            System.out.println("输出错误结果：" + errorMessage[0]);
                        } else {
                            successMessage[0] = new String(frame.getPayload());
                            executeProcessMessage.setSuccessMessage(successMessage[0]);
                            System.out.println("输出正确结果：" + successMessage[0]);
                        }
                        super.onNext(frame);
                    }
                }).awaitCompletion();
                statsCmd.close();
                stopWatch.stop();
                executeProcessMessage.setTime(stopWatch.getLastTaskTimeMillis());
                executeProcessMessageList.add(executeProcessMessage);
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
        }

        // 4. 封装响应信息
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> successOutputList = new ArrayList<>();
        // TODO 这里取所有执行操作输出用例中的最大耗时，可以优化
        long maxExecuteTime = 0;
        long maxMemory = 0;
        for (ExecuteProcessMessage executeProcessMessage : executeProcessMessageList) {
            if (StrUtil.isNotBlank(executeProcessMessage.getErrorMessage())) {
                // 用户代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            if (executeProcessMessage.getTime() != null) {
                maxExecuteTime = Math.max(maxExecuteTime, executeProcessMessage.getTime());
            }
            if (executeProcessMessage.getMemory() != null) {
                maxMemory = Math.max(maxMemory, executeProcessMessage.getMemory());
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
        judgeInfo.setMemory(maxMemory);
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