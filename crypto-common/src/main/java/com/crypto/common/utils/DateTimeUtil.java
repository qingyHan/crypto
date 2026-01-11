package com.crypto.common.utils;

import com.crypto.common.constants.SystemConstants;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 * <p>
 * 封装基于 {@link java.time} 包的常用操作，统一项目的时间格式化标准。
 * 所有时间均使用上海时区 (Asia/Shanghai, UTC+8)
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public final class DateTimeUtil {

    /**
     * 统一使用上海时区 (UTC+8)
     */
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter
            .ofPattern(SystemConstants.DATETIME_FORMAT);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(SystemConstants.DATE_FORMAT);

    private DateTimeUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 毫秒时间戳转LocalDateTime（使用上海时区）
     *
     * @param timestamp 毫秒时间戳
     * @return LocalDateTime（上海时区）
     */
    public static LocalDateTime timestampToLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), SHANGHAI_ZONE);
    }

    /**
     * LocalDateTime转毫秒时间戳（使用上海时区）
     *
     * @param localDateTime LocalDateTime
     * @return 毫秒时间戳
     */
    public static long localDateTimeToTimestamp(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return 0L;
        }
        return localDateTime.atZone(SHANGHAI_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 格式化LocalDateTime
     *
     * @param localDateTime LocalDateTime
     * @return 格式化字符串
     */
    public static String format(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(DATETIME_FORMATTER);
    }

    /**
     * 格式化LocalDate
     *
     * @param localDate LocalDate
     * @return 格式化字符串
     */
    public static String format(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.format(DATE_FORMATTER);
    }

    /**
     * 解析日期时间字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER);
    }

    /**
     * 解析日期字符串
     *
     * @param dateStr 日期字符串
     * @return LocalDate
     */
    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }

    /**
     * 获取当前时间戳(毫秒)
     *
     * @return 毫秒时间戳
     */
    public static long currentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前LocalDateTime
     *
     * @return LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Date转LocalDateTime
     *
     * @param date Date
     * @return LocalDateTime
     */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime转Date
     *
     * @param localDateTime LocalDateTime
     * @return Date
     */
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 计算两个时间之间的秒数
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 秒数
     */
    public static long secondsBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).getSeconds();
    }

    /**
     * 计算两个时间之间的分钟数
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 分钟数
     */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).toMinutes();
    }

    /**
     * 获取分钟开始时间(秒和毫秒归零)
     *
     * @param timestamp 时间戳
     * @return 分钟开始时间
     */
    public static LocalDateTime getMinuteStart(long timestamp) {
        LocalDateTime dateTime = timestampToLocalDateTime(timestamp);
        return dateTime.withSecond(0).withNano(0);
    }

    /**
     * 获取小时开始时间
     *
     * @param timestamp 时间戳
     * @return 小时开始时间
     */
    public static LocalDateTime getHourStart(long timestamp) {
        LocalDateTime dateTime = timestampToLocalDateTime(timestamp);
        return dateTime.withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * 获取天开始时间
     *
     * @param timestamp 时间戳
     * @return 天开始时间
     */
    public static LocalDateTime getDayStart(long timestamp) {
        LocalDateTime dateTime = timestampToLocalDateTime(timestamp);
        return dateTime.toLocalDate().atStartOfDay();
    }

    /**
     * 毫秒时间戳转格式化字符串
     *
     * @param timestamp 毫秒时间戳
     * @return 格式化字符串
     */
    public static String timestampToString(long timestamp) {
        LocalDateTime localDateTime = timestampToLocalDateTime(timestamp);
        return format(localDateTime);
    }
}
