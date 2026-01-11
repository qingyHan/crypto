package com.crypto.collector.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.crypto.common.entity.TradeEvent;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * OKX WebSocket 客户端
 * <p>
 * 负责与 OKX 交易所建立 WebSocket 长连接，订阅并解析实时交易数据。
 * 具备自动重连、心跳维持及异常处理机制。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class OkxWebSocketClient extends WebSocketClient {

    private final String symbol;                        // 交易对，如 BTC-USDT
    private final Consumer<TradeEvent> messageHandler;  // 消息处理回调
    private volatile boolean isReconnecting = false;    // 是否正在重连
    
    public OkxWebSocketClient(URI serverUri, String symbol, Consumer<TradeEvent> messageHandler) {
        super(serverUri);
        this.symbol = symbol;
        this.messageHandler = messageHandler;
        this.setConnectionLostTimeout(60);  // 60秒超时
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("OKX WebSocket connected: {} - Status: {}", symbol, handshakedata.getHttpStatus());
        isReconnecting = false;

        // 订阅交易频道
        subscribeToTrades();
    }

    /**
     * 发送订阅请求
     * <p>
     * 连接建立后，向 OKX 发送 JSON 订阅指令。订阅频道为 {@code trades}。
     * 自动处理 Symbol 格式转换（如 BTCUSDT &rarr; BTC-USDT）。
     * </p>
     */
    private void subscribeToTrades() {
        try {
            // OKX订阅格式: {"op":"subscribe","args":[{"channel":"trades","instId":"BTC-USDT"}]}
            // 如果symbol已经包含连字符（如BTC-USDT），直接使用；否则转换（如BTCUSDT -> BTC-USDT）
            String instId = symbol.contains("-") ? symbol : symbol.replace("USDT", "-USDT");
            String subscribeMsg = String.format(
                "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"trades\",\"instId\":\"%s\"}]}",
                instId
            );
            this.send(subscribeMsg);
            log.info("Subscribed to OKX trades for: {}", instId);
        } catch (Exception e) {
            log.error("Failed to subscribe to trades for {}: {}", symbol, e.getMessage());
        }
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        // 解析并处理消息
        try {
            JSONObject json = JSON.parseObject(message);

            // 处理订阅响应
            if (json.containsKey("event")) {
                String event = json.getString("event");
                if ("subscribe".equals(event)) {
                    log.info("Successfully subscribed: {}", json.getString("arg"));
                } else if ("error".equals(event)) {
                    log.error("Subscription error: {}", json.getString("msg"));
                }
                return;
            }

            // 处理交易数据
            if (json.containsKey("data")) {
                JSONArray dataArray = json.getJSONArray("data");
                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject trade = dataArray.getJSONObject(i);
                    TradeEvent tradeEvent = parseOkxTrade(trade);
                    if (tradeEvent != null && tradeEvent.isValid()) {
                        messageHandler.accept(tradeEvent);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing OKX message for {}: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * 解析交易数据
     * <p>
     * 将 OKX 推送的原始 JSON 数据映射为系统内部的 {@link TradeEvent} 对象。
     * </p>
     * 
     * <h3>字段映射关系：</h3>
     * <ul>
     *   <li>{@code instId} &rarr; symbol</li>
     *   <li>{@code px} &rarr; price</li>
     *   <li>{@code sz} &rarr; quantity</li>
     *   <li>{@code ts} &rarr; tradeTime</li>
     *   <li>{@code side} &rarr; isBuyerMaker (buy=false, sell=true)</li>
     * </ul>
     */
    private TradeEvent parseOkxTrade(JSONObject trade) {
        // OKX 返回的交易数据格式：
        // {
        //   "instId": "BTC-USDT",    ← 交易对
        //   "tradeId": "123456",     ← 交易ID
        //   "px": "50000.5",         ← 价格 (Price)
        //   "sz": "0.01",            ← 数量 (Size)
        //   "side": "buy",           ← 买/卖方向
        //   "ts": "1234567890123"    ← 时间戳 (Timestamp)
        // }
    
        try {
            TradeEvent event = new TradeEvent();
            event.setSymbol(symbol);
            event.setTradeId(Long.parseLong(trade.getString("tradeId")));
            event.setPrice(new BigDecimal(trade.getString("px")));
            event.setQuantity(new BigDecimal(trade.getString("sz")));
            event.setTradeTime(Long.parseLong(trade.getString("ts")));
            event.setEventTime(Instant.now().toEpochMilli());

            // OKX的side: buy/sell → isBuyerMaker
            String side = trade.getString("side");
            event.setIsBuyerMaker("sell".equals(side));

            return event;
        } catch (Exception e) {
            log.error("Failed to parse OKX trade: {}", trade, e);
            return null;
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("OKX WebSocket closed: {} - Code: {}, Reason: {}, Remote: {}",
                symbol, code, reason, remote);

        // 非主动关闭时尝试重连
        if (remote && !isReconnecting) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("OKX WebSocket error for {}: {}", symbol, ex.getMessage(), ex);
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
