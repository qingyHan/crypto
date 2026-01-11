package com.crypto.flink.schema;

import com.crypto.common.entity.TradeEvent;
import com.crypto.common.utils.JsonUtil;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 交易事件反序列化Schema
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class TradeEventDeserializationSchema implements DeserializationSchema<TradeEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public TradeEvent deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }

        String json = new String(message, StandardCharsets.UTF_8);
        return JsonUtil.fromJson(json, TradeEvent.class);
    }

    @Override
    public boolean isEndOfStream(TradeEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<TradeEvent> getProducedType() {
        return TypeInformation.of(TradeEvent.class);
    }
}
