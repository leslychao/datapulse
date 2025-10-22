package ru.vkim.datapulse.etl.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.vkim.datapulse.common.JsonUtils;
import ru.vkim.datapulse.domain.marketplace.SaleEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GsonRecordParser {

    public List<SaleEvent> parseWbSales(String json, String shopId) {
        var gson = JsonUtils.gson();
        JsonElement root = gson.fromJson(json, JsonElement.class);
        List<SaleEvent> out = new ArrayList<>();

        if (root != null && root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String sku = str(o, "supplierArticle");
                int qty = i(o, "quantity");
                BigDecimal revenue = new BigDecimal(strOr(o, "totalPrice", "0"));
                OffsetDateTime time = OffsetDateTime.parse(str(o, "date"));
                out.add(new SaleEvent("wb", shopId, sku, qty, revenue, time));
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
    private static String strOr(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
    private static int i(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
    }
}
