package com.crypto.collector.controller;

import com.crypto.collector.service.DataCollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据采集器健康检查控制器
 * <p>
 * 提供采集器状态监控和健康检查接口
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/collector")
@CrossOrigin(origins = "*")
public class CollectorHealthController {

    private final DataCollectorService dataCollectorService;

    public CollectorHealthController(DataCollectorService dataCollectorService) {
        this.dataCollectorService = dataCollectorService;
    }

    /**
     * 健康检查端点
     * GET /collector/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> status = dataCollectorService.getConnectionStatus();
        
        int totalConnections = (Integer) status.getOrDefault("totalConnections", 0);
        long messagesProcessed = ((Number) status.getOrDefault("messagesProcessed", 0L)).longValue();
        
        health.put("status", totalConnections > 0 ? "UP" : "DOWN");
        health.put("connections", totalConnections);
        health.put("messagesProcessed", messagesProcessed);
        health.put("timestamp", System.currentTimeMillis());
        
        return health;
    }

    /**
     * 获取连接状态详情
     * GET /collector/status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return dataCollectorService.getConnectionStatus();
    }
}

