package com.lhf.codesandbox;

import cn.hutool.core.io.FileUtil;
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

    }

}
