package ru.vkim.datapulse.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtils {
    private JsonUtils() {}
    public static Gson gson() {
        return new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                .create();
    }
}
