package com.crypto.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON处理工具类
 * <p>
 * 基于 Jackson 封装，提供线程安全的单例 {@link ObjectMapper}。
 * 默认配置了对 Java 8 时间类型的支持及容错策略。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public final class JsonUtil {
    // ObjectMapper单例，线程安全
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 静态代码块，初始化ObjectMapper
    static {
        // 注册Java 8时间模块（支持 LocalDateTime 等新时间类）
        OBJECT_MAPPER.registerModule(new JavaTimeModule());

        // 配置序列化特性
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 禁用日期时间序列化为时间戳
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS); // 禁用空Bean序列化，遇到空对象不报错

        // 配置反序列化特性
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 禁用未知属性反序列化，遇到未知字段不报错
        OBJECT_MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT); // 空字符串反序列化为空对象
    }

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 对象转JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串,失败返回null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Object to JSON failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 对象转格式化的JSON字符串
     *
     * @param obj 对象
     * @return 格式化的JSON字符串
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Object to pretty JSON failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON字符串转对象
     *
     * @param json  JSON字符串
     * @param clazz 目标类
     * @param <T>   泛型类型
     * @return 对象,失败返回null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON to Object failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON字符串转复杂对象(泛型)
     *
     * @param json         JSON字符串
     * @param valueTypeRef 类型引用
     * @param <T>          泛型类型
     * @return 对象,失败返回null
     */
    public static <T> T fromJson(String json, TypeReference<T> valueTypeRef) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, valueTypeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON to Object failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON字符串转List
     *
     * @param json  JSON字符串
     * @param clazz 元素类型
     * @param <T>   泛型类型
     * @return List对象,失败返回空列表
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("JSON to List failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * JSON字符串转Map
     *
     * @param json JSON字符串
     * @return Map对象,失败返回空Map
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("JSON to Map failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 对象转目标对象(通过JSON中转)
     *
     * @param obj   源对象
     * @param clazz 目标类
     * @param <T>   泛型类型
     * @return 目标对象
     */
    public static <T> T convert(Object obj, Class<T> clazz) {
        if (obj == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(obj, clazz);
    }

    /**
     * 获取ObjectMapper实例
     *
     * @return ObjectMapper
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
