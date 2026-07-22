package com.xiapin.ime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 鍵盤內翻譯：
 * - gtx：Google 非官方 + MyMemory（可 debounce 即時）
 * - llm：OpenAI 相容 chat/completions（僅手動觸發，避免費用爆掉）
 */
public final class TranslateHelper {
    private static final String TAG = "TranslateHelper";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public interface Callback {
        void onResult(String source, List<String> options, String error);
    }

    public static final class TargetLang {
        public final String label;
        public final String tl;
        public final String promptName;

        public TargetLang(String label, String tl, String promptName) {
            this.label = label;
            this.tl = tl;
            this.promptName = promptName;
        }
    }

    public static final TargetLang[] TARGETS = {
            new TargetLang("英", "en", "English"),
            new TargetLang("日", "ja", "Japanese"),
            new TargetLang("简", "zh-CN", "Simplified Chinese"),
    };

    /** 依設定選擇後端；LLM 也走此入口（呼叫端負責不要自動狂打） */
    public static void translate(Context ctx, String text, String tl, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            MAIN.post(() -> cb.onResult(text, new ArrayList<String>(), null));
            return;
        }
        final String src = text.trim();
        final int mySeq = SEQ.incrementAndGet();
        final boolean useLlm = ctx != null && TranslatePrefs.isLlm(ctx);
        final Context app = ctx != null ? ctx.getApplicationContext() : null;

        EXEC.execute(() -> {
            List<String> options = new ArrayList<>();
            String err = null;
            try {
                if (useLlm && app != null) {
                    options = translateLlm(app, src, tl);
                } else {
                    options = translateGoogle(src, "auto", tl);
                    if (options.isEmpty()) {
                        String one = translateMyMemory(src, "autodetect", tl);
                        if (one != null && !one.isEmpty()) options.add(one);
                    }
                }
                if (options.isEmpty()) {
                    err = useLlm
                            ? "LLM 無譯文（檢查模型回傳格式 / Key / 網址）"
                            : "翻譯失敗";
                }
            } catch (Exception e) {
                Log.w(TAG, "translate error", e);
                err = e.getMessage() != null ? e.getMessage() : "網路錯誤";
            }
            final List<String> fOpts = options;
            final String fErr = err;
            MAIN.post(() -> {
                if (mySeq != SEQ.get()) return;
                cb.onResult(src, fOpts, fErr);
            });
        });
    }

    /** 相容舊呼叫（僅 gtx） */
    public static void translate(String text, String tl, Callback cb) {
        translate(null, text, tl, cb);
    }

    private static List<String> translateLlm(Context ctx, String text, String tl) throws Exception {
        String url = TranslatePrefs.chatCompletionsUrl(ctx);
        String key = TranslatePrefs.getApiKey(ctx);
        String model = TranslatePrefs.getModel(ctx);
        if (url.isEmpty()) throw new IllegalStateException("未設定 API 網址");
        if (key.isEmpty()) throw new IllegalStateException("未設定 API Key");
        if (model.isEmpty()) throw new IllegalStateException("未設定模型名稱");

        String targetName = "English";
        for (TargetLang t : TARGETS) {
            if (t.tl.equals(tl)) {
                targetName = t.promptName;
                break;
            }
        }

        String system = "You are a professional translator. "
                + "Translate the user's text into " + targetName + ". "
                + "Output ONLY the translation, no quotes, no explanation, no romanization unless asked.";
        String user = text;

        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", messages);
        body.put("temperature", 0.2);
        // 省 token
        body.put("max_tokens", Math.min(2048, Math.max(64, text.length() * 4)));

        String raw = httpPostJson(url, key, body.toString(), 45000);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();

        JSONObject root = new JSONObject(raw);
        if (root.has("error")) {
            JSONObject e = root.optJSONObject("error");
            String msg = e != null ? e.optString("message", "API error") : "API error";
            throw new IllegalStateException(msg);
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            Log.w(TAG, "LLM no choices, body=" + truncate(raw, 300));
            return new ArrayList<>();
        }
        JSONObject c0 = choices.getJSONObject(0);
        String content = extractMessageText(c0);
        content = normalizeTranslation(content);
        Log.i(TAG, "LLM ok len=" + (content == null ? 0 : content.length())
                + " preview=" + truncate(content, 80));
        List<String> out = new ArrayList<>();
        if (content != null && !content.isEmpty()) out.add(content);
        return out;
    }

    /**
     * 從 choices[0] 取出譯文。
     * 注意：Android JSONObject.optString() 在值為 JSON null 時會回傳字面 "null"！
     */
    private static String extractMessageText(JSONObject choice) {
        if (choice == null) return "";
        // 1) message.content
        JSONObject msg = choice.optJSONObject("message");
        if (msg != null) {
            String c = jsonValueToText(msg, "content");
            if (c != null && !c.isEmpty()) return c;
            // 部分 API: message.reasoning / refusal
            c = jsonValueToText(msg, "text");
            if (c != null && !c.isEmpty()) return c;
        }
        // 2) choice.text (舊 completion 風格)
        String t = jsonValueToText(choice, "text");
        if (t != null && !t.isEmpty()) return t;
        // 3) delta (stream 殘片誤當完整回覆)
        JSONObject delta = choice.optJSONObject("delta");
        if (delta != null) {
            String c = jsonValueToText(delta, "content");
            if (c != null && !c.isEmpty()) return c;
        }
        return "";
    }

    /** 安全取字串：避開 optString 把 JSON null 變成 "null" */
    private static String jsonValueToText(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return "";
        Object v = obj.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof String) {
            return ((String) v).trim();
        }
        // OpenAI 多模態: content 可能是 [{type:text,text:"..."}]
        if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.opt(i);
                if (item instanceof String) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(((String) item).trim());
                } else if (item instanceof JSONObject) {
                    JSONObject o = (JSONObject) item;
                    String tx = o.optString("text", null);
                    if (tx == null || "null".equals(tx)) {
                        // 有的用 content 欄
                        if (!o.isNull("content")) {
                            Object c2 = o.opt("content");
                            if (c2 instanceof String) tx = (String) c2;
                        }
                    }
                    if (tx != null && !"null".equals(tx) && !tx.isEmpty()) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(tx.trim());
                    }
                }
            }
            return sb.toString().trim();
        }
        // 其他型別
        String s = String.valueOf(v).trim();
        if ("null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) return "";
        return s;
    }

    private static String normalizeTranslation(String content) {
        if (content == null) return "";
        content = content.trim();
        if (content.isEmpty()) return "";
        if ("null".equalsIgnoreCase(content) || "undefined".equalsIgnoreCase(content)) return "";
        // 去掉包起來的引號
        if ((content.startsWith("\"") && content.endsWith("\"") && content.length() >= 2)
                || (content.startsWith("「") && content.endsWith("」") && content.length() >= 2)
                || (content.startsWith("'") && content.endsWith("'") && content.length() >= 2)) {
            content = content.substring(1, content.length() - 1).trim();
        }
        // 去掉常見前綴
        if (content.startsWith("Translation:")) {
            content = content.substring("Translation:".length()).trim();
        }
        if ("null".equalsIgnoreCase(content)) return "";
        return content;
    }

    private static List<String> translateGoogle(String text, String sl, String tl) throws Exception {
        String q = URLEncoder.encode(text, "UTF-8");
        String urlStr = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=" + sl + "&tl=" + tl + "&dt=t&dt=at&q=" + q;
        String body = httpGet(urlStr, 8000);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) return new ArrayList<>();

        JSONArray root = new JSONArray(body);
        JSONArray sentences = root.optJSONArray(0);
        if (sentences != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray part = sentences.optJSONArray(i);
                if (part != null && part.length() > 0 && !part.isNull(0)) {
                    sb.append(part.getString(0));
                }
            }
            String main = sb.toString().trim();
            if (!main.isEmpty()) set.add(main);
        }
        JSONArray alts = root.optJSONArray(5);
        if (alts != null) {
            for (int i = 0; i < alts.length(); i++) {
                JSONArray item = alts.optJSONArray(i);
                if (item == null || item.length() < 3) continue;
                JSONArray choices = item.optJSONArray(2);
                if (choices == null) continue;
                for (int j = 0; j < choices.length() && j < 4; j++) {
                    JSONArray c = choices.optJSONArray(j);
                    if (c != null && c.length() > 0 && !c.isNull(0)) {
                        String alt = c.getString(0).trim();
                        if (!alt.isEmpty()) set.add(alt);
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    private static String translateMyMemory(String text, String sl, String tl) throws Exception {
        String src = "autodetect".equals(sl) ? "zh-TW" : sl;
        String pair = src + "|" + tl;
        String q = URLEncoder.encode(text, "UTF-8");
        String urlStr = "https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + pair;
        String body = httpGet(urlStr, 8000);
        if (body == null) return null;
        int i = body.indexOf("\"translatedText\":\"");
        if (i < 0) return null;
        i += "\"translatedText\":\"".length();
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < body.length(); j++) {
            char c = body.charAt(j);
            if (c == '\\' && j + 1 < body.length()) {
                char n = body.charAt(j + 1);
                if (n == '"') { sb.append('"'); j++; }
                else if (n == 'n') { sb.append('\n'); j++; }
                else if (n == 'u' && j + 5 < body.length()) {
                    sb.append((char) Integer.parseInt(body.substring(j + 2, j + 6), 16));
                    j += 5;
                } else sb.append(n);
                continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        String r = sb.toString().trim();
        if (r.contains("MYMEMORY WARNING")) return null;
        return r;
    }

    private static String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "XiapinIME/1.0");
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(in);
        conn.disconnect();
        if (code != 200) return null;
        return body;
    }

    private static String httpPostJson(String urlStr, String bearer, String json, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("User-Agent", "XiapinIME/1.0");
        // OpenRouter 建議（可有可無）
        conn.setRequestProperty("HTTP-Referer", "https://github.com/Jakevin/xiapin-input-method");
        conn.setRequestProperty("X-Title", "Xiapin IME");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(in);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            String msg = body;
            try {
                JSONObject o = new JSONObject(body);
                if (o.has("error")) {
                    Object e = o.get("error");
                    if (e instanceof JSONObject) msg = ((JSONObject) e).optString("message", body);
                    else msg = String.valueOf(e);
                }
            } catch (Exception ignored) {}
            throw new IllegalStateException("HTTP " + code + ": " + truncate(msg, 180));
        }
        return body;
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
