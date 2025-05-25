package de.hinundhergestellt.jhuh.vendors.ready2order;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

public class ApiUtils {

    public static String withDefault(@Nullable String value, String defaultValue) {
        return StringUtils.hasLength(value) ? value : defaultValue;
    }

    public static <T> T withDefault(@Nullable T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private ApiUtils() {}
}
