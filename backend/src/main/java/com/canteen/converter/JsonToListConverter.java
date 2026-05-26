package com.canteen.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JPA 类型转换器
 * 将 List<Integer> 转换为 JSON 字符串存入数据库
 * 从数据库读取时，将 JSON 字符串转换回 List<Integer>
 */
@Converter
public class JsonToListConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(JsonToListConverter.class);
    private static final TypeReference<List<Integer>> LIST_INT_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("转换 List 为 JSON 失败", e);
            throw new IllegalArgumentException("转换 List 为 JSON 失败", e);
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(dbData, LIST_INT_TYPE);
        } catch (IOException e) {
            log.error("转换 JSON 为 List 失败: dbData={}", dbData, e);
            return Collections.emptyList();
        }
    }
}
