package com.lhf.codesandbox.utils;

import com.lhf.codesandbox.model.ExecuteProcessMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtil {
    public static ExecuteProcessMessage getProcessMessage(Process process, String option) {
        ExecuteProcessMessage executeProcessMessage = new ExecuteProcessMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 等待进程执行并获取错误码
            int exitValue = process.waitFor();
            executeProcessMessage.setExitValue(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(option + "成功");
                //逐行输出成功信息
                StringBuilder compileString = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileString.append(compileOutputLine);
                }
                executeProcessMessage.setSuccessMessage(compileString.toString());

            } else {
                System.out.println(option + "失败");
                // 输出成功信息
                StringBuilder allSuccessMessage = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    allSuccessMessage.append(compileOutputLine);
                }
                executeProcessMessage.setSuccessMessage(allSuccessMessage.toString());

                // 输出错误信息
                StringBuilder allErrorMessage = new StringBuilder();
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorOutputLine;
                while ((errorOutputLine = errorBufferedReader.readLine()) != null) {
                    allErrorMessage.append(errorOutputLine);
                }
                executeProcessMessage.setErrorMessage(allErrorMessage.toString());
            }
            stopWatch.stop();
            long taskTimeMillis = stopWatch.getLastTaskTimeMillis();
            executeProcessMessage.setTime(taskTimeMillis);
            // 本地调用没有实现获取内存
//            executeProcessMessage.setMemory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeProcessMessage;
    }
}