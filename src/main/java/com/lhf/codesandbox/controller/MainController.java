package com.lhf.codesandbox.controller;

import com.lhf.codesandbox.JavaCodeSandBoxTemplate;
import com.lhf.codesandbox.JavaDockerCodeSandBox;
import com.lhf.codesandbox.JavaNativeCodeSandBox;
import com.lhf.codesandbox.model.ExecuteCodeRequest;
import com.lhf.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;

@Slf4j
@RestController
public class MainController {
    @Autowired
    private JavaCodeSandBoxTemplate javaCodeSandBoxTemplate;
    @Autowired
    private JavaNativeCodeSandBox javaNativeCodeSandBox;
    @Autowired
    private JavaDockerCodeSandBox javaDockerCodeSandBox;

    private static String REQUEST_HEAD = "auth";
    private static String REQUEST_HEAD_VALUE = "123456";

    @GetMapping("hi")
    public String hello() {
        return "ok";
    }

    @PostMapping("/execute_code")
    public ExecuteCodeResponse execute(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        log.info("准备执行代码沙箱，请求参数：{}", executeCodeRequest);

        String header = request.getHeader(REQUEST_HEAD);
        if (!REQUEST_HEAD_VALUE.equals(header)) {
            response.setStatus(403);
            log.error("请求头校验失败，header：{}", header);
            return null;
        }
        //调用docker代码沙箱
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandBox.doExecute(executeCodeRequest);
        log.info("代码沙箱执行结束，响应结果：{}", executeCodeResponse);
        return executeCodeResponse;
    }
}