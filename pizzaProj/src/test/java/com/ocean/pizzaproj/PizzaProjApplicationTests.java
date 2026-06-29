package com.ocean.pizzaproj;

import com.pizza.worker.CommandExecutorWorker;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.mockito.Mockito.*;

@SpringBootTest
class PizzaProjApplicationTests {

    // 注入待测试的命令执行Worker
    @Autowired
    private CommandExecutorWorker commandExecutorWorker;

    // 模拟Zeebe客户端，避免连接真实网关
    @MockBean
    private ZeebeClient zeebeClient;

    // 原有默认测试，保留不删除
    @Test
    void contextLoads() {
    }

    /**
     * 测试正常执行命令场景：echo输出变量
     */
    @Test
    void testWorkerNormalCommand() {
        // 构造模拟任务
        ActivatedJob mockJob = mock(ActivatedJob.class);
        long testJobKey = 10001L;

        // 自定义header：命令模板、结果存储变量名
        Map<String, String> headers = Map.of(
                "command", "echo 披萨饼底：${crustName}",
                "resultVariable", "cmdOutput"
        );
        // 流程变量
        Map<String, Object> variables = Map.of("crustName", "那不勒斯薄饼底");

        // 模拟job返回值
        when(mockJob.getCustomHeaders()).thenReturn(headers);
        when(mockJob.getVariablesAsMap()).thenReturn(variables);
        when(mockJob.getKey()).thenReturn(testJobKey);

        // 执行worker核心处理方法
        commandExecutorWorker.handle(mockJob);

        // 校验：成功分支会调用completeCommand
        verify(zeebeClient).newCompleteCommand(testJobKey);
    }

    /**
     * 测试错误命令场景：访问不存在目录，命令执行失败
     */
    @Test
    void testWorkerFailedCommand() {
        ActivatedJob mockJob = mock(ActivatedJob.class);
        long testJobKey = 10002L;

        Map<String, String> headers = Map.of("command", "dir non_exist_folder_123456");
        Map<String, Object> variables = Map.of();

        when(mockJob.getCustomHeaders()).thenReturn(headers);
        when(mockJob.getVariablesAsMap()).thenReturn(variables);
        when(mockJob.getKey()).thenReturn(testJobKey);

        commandExecutorWorker.handle(mockJob);

        // 校验：失败分支调用failCommand
        verify(zeebeClient).newFailCommand(testJobKey);
    }

    /**
     * 测试缺少command头部的异常分支
     */
    @Test
    void testWorkerMissingCommandHeader() {
        ActivatedJob mockJob = mock(ActivatedJob.class);
        long testJobKey = 10003L;

        // header为空，缺少command
        Map<String, String> headers = Map.of();
        Map<String, Object> variables = Map.of();

        when(mockJob.getCustomHeaders()).thenReturn(headers);
        when(mockJob.getVariablesAsMap()).thenReturn(variables);
        when(mockJob.getKey()).thenReturn(testJobKey);

        commandExecutorWorker.handle(mockJob);

        verify(zeebeClient).newFailCommand(testJobKey);
    }
}