package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonAttributeResponse(
        List<OzonAttributeResult> result,
        @JsonProperty("last_id") String lastId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonAttributeResult(
            long id,
            @JsonProperty("offer_id") String offerId,
            List<OzonAttribute> attributes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonAttribute(
            @JsonProperty("attribute_id") long attributeId,
            List<OzonAttributeValue> values
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonAttributeValue(
            String value,
            @JsonProperty("dictionary_value_id") long dictionaryValueId
    ) {}
}
