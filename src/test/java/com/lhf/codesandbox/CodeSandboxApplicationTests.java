package com.lhf.codesandbox;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

@SpringBootTest
class CodeSandboxApplicationTests {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Test
    void contextLoads() {
        ArrayList<Object> list = new ArrayList<>();
        // 获取用户目录
        String userDir = System.getProperty("user.dir");
        // 设置全局根目录
        String globalCodePath = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，不存在就创建一个
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        String code = "   ";
        // 生成用户代码文件路径
        String userCodePath = globalCodePath + File.separator + UUID.randomUUID()
                + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 用户代码文件路径（已写入用户代码）
        File userCodeFilePath = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        System.out.println(userCodeFilePath.getParent());
        System.out.println(userCodeFilePath);

    }

    @Test
    public void testDocker() {
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
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        try {
            pullImageCmd.exec(new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("拉取镜像中：" + item);
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("拉取镜像成功");

    }
}
