package com.crypto.api.service;

import com.crypto.api.dto.MarketDataDTO;

import java.util.List;
import java.util.Map;

/**
 * 市场数据服务接口
 * 提供市场行情数据查询功能
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public interface MarketDataService {

    /**
     * 获取实时价格
     *
     * @param symbol 交易对符号
     * @return 价格数据
     */
    MarketDataDTO.PriceData getRealtimePrice(String symbol);

    /**
     * 获取K线数据
     *
     * @param request K线查询请求
     * @return K线响应
     */
    MarketDataDTO.KlineResponse getKlineData(MarketDataDTO.KlineQueryRequest request);

    /**
     * 获取所有交易对列表
     *
     * @return 交易对信息列表
     */
    List<MarketDataDTO.SymbolInfo> getAllSymbols();

    /**
     * 获取启用的交易对列表
     *
     * @return 交易对信息列表
     */
    List<MarketDataDTO.SymbolInfo> getEnabledSymbols();

    /**
     * 获取市场统计数据
     *
     * @return 市场统计
     */
    MarketDataDTO.MarketStatistics getMarketStatistics();

    /**
     * 获取24小时行情数据
     *
     * @param symbol 交易对符号
     * @return 市场数据
     */
    MarketDataDTO get24hMarketData(String symbol);

    /**
     * 刷新缓存中的价格数据
     *
     * @param symbol 交易对符号
     */
    void refreshPriceCache(String symbol);

    /**
     * 批量刷新价格缓存
     *
     * @param symbols 交易对符号列表
     */
    void batchRefreshPriceCache(List<String> symbols);

    /**
     * 获取市场行情数据列表(用于前端Market页面)
     * 返回所有启用的交易对的最新行情
     *
     * @return 市场行情列表
     */
    List<MarketDataDTO> getMarketDataList();

    /**
     * 获取市场统计概览(用于前端Dashboard)
     * 返回市场概览统计信息
     *
     * @return 市场统计信息
     */
    Map<String, Object> getMarketOverview();
}
