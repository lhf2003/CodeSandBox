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
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {
    private static final Long TIME_LIMIT = 5000L;
    private static Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaCodeBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest ExecuteCodeRequest = new ExecuteCodeRequest();
        String userCode = ResourceUtil.readStr("SimpleComputer/Main.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest.setCode(userCode);
        ExecuteCodeRequest.setLanguage("java");
        ExecuteCodeRequest.setInput(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse Execute = javaCodeBox.doExecute(ExecuteCodeRequest);
        System.out.println(Execute);
    }

    @Override
    public List<ExecuteProcessMessage> executeCodeFile(File userCodeFilePath, List<String> inputList) {
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
        hostConfig.withMemorySwap(0L);
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.setBinds(new Bind(userCodeFilePath.getParent(), new Volume("/app")));
        //指定镜像
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //构建交互式操作终端 docker run -v userCodeParentPath:/app --stderr --stdin --stdout --tty /bin/sh
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
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
            final boolean[] outTimeLimit = {true};
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

                    //进程执行完毕后会到这里方法
                    @Override
                    public void onComplete() {
                        //执行到这里表示没超时
                        outTimeLimit[0] = false;
                        super.onComplete();
                    }
                }).awaitCompletion(TIME_LIMIT, TimeUnit.MICROSECONDS); //超时控制
                statsCmd.close();
                stopWatch.stop();
                executeProcessMessage.setTime(stopWatch.getLastTaskTimeMillis());
                executeProcessMessageList.add(executeProcessMessage);
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
        }
        return executeProcessMessageList;
    }
}