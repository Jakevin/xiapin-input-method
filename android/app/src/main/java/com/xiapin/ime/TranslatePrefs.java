package com.xiapin.ime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 翻譯後端設定（SharedPreferences）。
 * engine: gtx（免費即時） / llm（OpenAI 相容，僅手動觸發）
 */
public final class TranslatePrefs {
    public static final String PREFS = "xiapin_translate";
    public static final String ENGINE_GTX = "gtx";
    public static final String ENGINE_LLM = "llm";

    public static final String PRESET_OPENROUTER = "https://openrouter.ai/api/v1";
    public static final String PRESET_OPENCODE = "https://opencode.ai/zen/go/v1";

    private TranslatePrefs() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getEngine(Context ctx) {
        return prefs(ctx).getString("engine", ENGINE_GTX);
    }

    public static boolean isLlm(Context ctx) {
        return ENGINE_LLM.equals(getEngine(ctx));
    }

    public static void setEngine(Context ctx, String engine) {
        prefs(ctx).edit().putString("engine", engine).apply();
    }

    /** 不含 /chat/completions 的 base，例如 https://openrouter.ai/api/v1 */
    public static String getBaseUrl(Context ctx) {
        return prefs(ctx).getString("base_url", PRESET_OPENROUTER);
    }

    public static void setBaseUrl(Context ctx, String url) {
        prefs(ctx).edit().putString("base_url", url == null ? "" : url.trim()).apply();
    }

    public static String getApiKey(Context ctx) {
        return prefs(ctx).getString("api_key", "");
    }

    public static void setApiKey(Context ctx, String key) {
        prefs(ctx).edit().putString("api_key", key == null ? "" : key.trim()).apply();
    }

    public static String getModel(Context ctx) {
        return prefs(ctx).getString("model", "openai/gpt-4o-mini");
    }

    public static void setModel(Context ctx, String model) {
        prefs(ctx).edit().putString("model", model == null ? "" : model.trim()).apply();
    }

    /** 組出 chat completions URL */
    public static String chatCompletionsUrl(Context ctx) {
        String base = getBaseUrl(ctx);
        if (base == null) base = "";
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        return base + "/chat/completions";
    }
}
