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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {
    private static final Long TIME_LIMIT = 5000L;
    private static Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaCodeBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String userCode = ResourceUtil.readStr("SimpleComputer/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(userCode);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInput(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse Execute = javaCodeBox.doExecute(executeCodeRequest);
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
            log.info("首次拉取镜像......");
            FIRST_INIT = false;
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
                throw new RuntimeException("镜像拉取异常", e);
            }
            log.info("镜像拉取成功！");
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

            try {
                // 创建执行命令
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .exec();
                log.info("创建执行命令：" + execCreateCmdResponse);

                String execId = execCreateCmdResponse.getId();

                // 运行执行命令
                ExecStartCmd execStartCmd = dockerClient.execStartCmd(execId)
                        .withDetach(false) // 确保不使用detach模式
                        .withTty(true); // 确保使用交互式终端

                log.info("开始执行命令：" + execId);

                // 开始计时
                stopWatch.start();

                // 创建一个锁对象
                final Object lock = new Object();

                // 标志位，记录命令是否完成
                final boolean[] outTimeLimit = {true};
                final String[] successMessage = {null};
                final String[] errorMessage = {null};

                // 启动执行命令
                execStartCmd.exec(new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (streamType.equals(StreamType.STDERR)) {
                            errorMessage[0] = new String(frame.getPayload());
                            executeProcessMessage.setErrorMessage(errorMessage[0]);
                            log.info("输出错误结果：" + errorMessage[0]);
                        } else {
                            successMessage[0] = new String(frame.getPayload());
                            executeProcessMessage.setSuccessMessage(successMessage[0]);
                            log.info("输出正确结果：" + successMessage[0]);
                        }
                        super.onNext(frame);
                    }

                    @Override
                    public void onComplete() {
                        outTimeLimit[0] = false;
                        synchronized (lock) {
                            lock.notify();
                        }
                        super.onComplete();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("命令执行出错", throwable);
                        synchronized (lock) {
                            lock.notify();
                        }
                        super.onError(throwable);
                    }
                });

                // 等待命令完成或超时
                synchronized (lock) {
                    if (outTimeLimit[0]) {
                        lock.wait(TIME_LIMIT);
                    }
                }

                // 停止计时
                stopWatch.stop();
                executeProcessMessage.setTime(stopWatch.getLastTaskTimeMillis());

                // 获取一次内存统计信息并关闭 statsCmd
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onStart(Closeable closeable) {
                    }

                    @Override
                    public void onNext(Statistics statistics) {
                        Long usageMemory = statistics.getMemoryStats().getUsage();
                        executeProcessMessage.setMemory(usageMemory);
                        log.info("内存：" + usageMemory);
                        // 立即关闭 statsCmd
                        statsCmd.close();
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

                executeProcessMessageList.add(executeProcessMessage);
            } catch (InterruptedException e) {
                log.error("程序执行异常", e);
                throw new RuntimeException(e);
            }
        }

        return executeProcessMessageList;
    }
}