package com.crypto.collector.client;

import com.crypto.common.entity.TradeEvent;
import com.crypto.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Binance WebSocket客户端
 * 连接Binance交易所实时交易流
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class BinanceWebSocketClient extends WebSocketClient {

    private final String symbol;
    private final Consumer<TradeEvent> messageHandler;
    private volatile boolean isReconnecting = false;

    public BinanceWebSocketClient(URI serverUri, String symbol, Consumer<TradeEvent> messageHandler) {
        super(serverUri);
        this.symbol = symbol;
        this.messageHandler = messageHandler;
        this.setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("WebSocket connected: {} - Status: {}", symbol, handshakedata.getHttpStatus());
        isReconnecting = false;
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        try {
            // 解析交易事件
            TradeEvent tradeEvent = JsonUtil.fromJson(message, TradeEvent.class);

            if (tradeEvent != null && tradeEvent.isValid()) {
                // 处理交易事件
                messageHandler.accept(tradeEvent);
            } else {
                log.warn("Invalid trade event received: {}", message);
            }
        } catch (Exception e) {
            log.error("Error processing message for {}: {}", symbol, e.getMessage(), e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket closed: {} - Code: {}, Reason: {}, Remote: {}",
                symbol, code, reason, remote);

        // 非主动关闭时尝试重连
        if (remote && !isReconnecting) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error for {}: {}", symbol, ex.getMessage(), ex);
    }

    /**
     * 安排重连
     */
    private void scheduleReconnect() {
        if (isReconnecting) {
            return;
        }

        isReconnecting = true;
        log.info("Scheduling reconnect for {} in 5 seconds...", symbol);

        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect: {}", symbol);
                this.reconnect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Reconnect interrupted for {}", symbol);
            } catch (Exception e) {
                log.error("Reconnect failed for {}: {}", symbol, e.getMessage());
                isReconnecting = false;
                // 如果重连失败,再次尝试
                scheduleReconnect();
            }
        }).start();
    }

    public String getSymbol() {
        return symbol;
    }
}
