package com.crypto.flink.schema;

import com.crypto.common.entity.KlineData;
import com.crypto.common.utils.JsonUtil;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * K线数据反序列化Schema
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class KlineDataDeserializationSchema implements DeserializationSchema<KlineData> {

    private static final long serialVersionUID = 1L;

    @Override
    public KlineData deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }

        String json = new String(message, StandardCharsets.UTF_8);
        return JsonUtil.fromJson(json, KlineData.class);
    }

    @Override
    public boolean isEndOfStream(KlineData nextElement) {
        return false;
    }

    @Override
    public TypeInformation<KlineData> getProducedType() {
        return TypeInformation.of(KlineData.class);
    }
}
