package io.datapulse.pricing.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.PricingRunEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(config = BaseMapperConfig.class)
public abstract class PricingRunMapper {

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(source = "errorDetails", target = "errorDetails", qualifiedByName = "jsonToObject")
    @Mapping(target = "sourcePlatform", ignore = true)
    @Mapping(target = "connectionName", ignore = true)
    @Mapping(target = "simulatedDecisionCount", constant = "0")
    public abstract PricingRunResponse toResponse(PricingRunEntity entity);

    @Named("jsonToObject")
    Object jsonToObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
