package com.lhf.codesandbox.utils;

import com.lhf.codesandbox.model.ExecuteProcessMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtil {
    public static ExecuteProcessMessage getProcessMessage(Process process,String option) {
        ExecuteProcessMessage ExecuteProcessMessage = new ExecuteProcessMessage();

        try {
            // 等待进程执行并获取错误码
            int exitValue = process.waitFor();
            ExecuteProcessMessage.setExitValue(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(option+"成功");
                //逐行输出成功信息
                StringBuilder compileString = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileString.append(compileOutputLine);
                }
                ExecuteProcessMessage.setSuccessMessage(compileString.toString());

            } else {
                System.out.println(option+"失败");
                //逐行输出成功信息
                StringBuilder compileString = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileString.append(compileOutputLine);
                }
                ExecuteProcessMessage.setSuccessMessage(compileString.toString());

                //逐行输出错误信息
                StringBuilder errorString = new StringBuilder();
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorOutputLine;
                while ((errorOutputLine = errorBufferedReader.readLine()) != null) {
                    errorString.append(errorOutputLine);
                }
                ExecuteProcessMessage.setErrorMessage(errorString.toString());

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ExecuteProcessMessage;
    }
}