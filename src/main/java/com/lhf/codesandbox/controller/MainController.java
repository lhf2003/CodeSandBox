package com.lhf.codesandbox.controller;

import com.lhf.codesandbox.JavaCodeSandBoxTemplate;
import com.lhf.codesandbox.model.ExecuteCodeRequest;
import com.lhf.codesandbox.model.ExecuteCodeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @Autowired
    private JavaCodeSandBoxTemplate javaCodeSandBoxTemplate;

    @GetMapping("hi")
    public String hello() {
        return "ok";
    }

    @PostMapping("/execute_code")
    public ExecuteCodeResponse execute(ExecuteCodeRequest executeCodeRequest) {
        return javaCodeSandBoxTemplate.doExecute(executeCodeRequest);
    }
}