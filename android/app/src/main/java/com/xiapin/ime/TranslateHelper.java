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

        // 嚴格要求只輸出譯文；免費/推理模型常把 reasoning 塞滿 token
        String system = "You are a translation engine. "
                + "Translate the user message into " + targetName + " only. "
                + "Rules: (1) Output the translation text and nothing else. "
                + "(2) No thinking, no analysis, no quotes, no labels, no romanization. "
                + "(3) Keep emojis if present.";
        String user = "Translate into " + targetName + ":\n\n" + text;

        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", messages);
        body.put("temperature", 0.1);
        // 至少 512：openrouter/free 等推理模型會先吐英文思考，太小會 finish_reason=length 且 content 殘缺/null
        int mt = Math.min(2048, Math.max(1024, text.length() * 8 + 512));
        body.put("max_tokens", mt);

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
        JSONObject msg = choice.optJSONObject("message");
        if (msg != null) {
            String c = jsonValueToText(msg, "content");
            if (c != null && !c.isEmpty()) return c;
            c = jsonValueToText(msg, "text");
            if (c != null && !c.isEmpty()) return c;
            // 部分 free/reasoning 模型 content=null，譯文在 reasoning 末段
            c = jsonValueToText(msg, "reasoning");
            if (c != null && !c.isEmpty()) return c;
        }
        String t = jsonValueToText(choice, "text");
        if (t != null && !t.isEmpty()) return t;
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
        if ((content.startsWith("\"") && content.endsWith("\"") && content.length() >= 2)
                || (content.startsWith("「") && content.endsWith("」") && content.length() >= 2)
                || (content.startsWith("'") && content.endsWith("'") && content.length() >= 2)) {
            content = content.substring(1, content.length() - 1).trim();
        }
        if (content.regionMatches(true, 0, "Translation:", 0, 12)) {
            content = content.substring(12).trim();
        }
        // 推理模型常回一大段英文思考：若有明顯「非英文」行，取最像譯文的一行
        if (content.length() > 80 && looksLikeEnglishReasoning(content)) {
            String picked = pickLikelyTranslationLine(content);
            if (picked != null && !picked.isEmpty()) content = picked;
        }
        if ("null".equalsIgnoreCase(content)) return "";
        return content.trim();
    }

    private static boolean looksLikeEnglishReasoning(String s) {
        String lower = s.toLowerCase();
        return lower.contains("the user") || lower.contains("translate")
                || lower.contains("i need to") || lower.contains("output only")
                || lower.contains("reasoning") || s.contains("\n");
    }

    /** 從多行/長推理文字中挑出較像譯文的一行 */
    private static String pickLikelyTranslationLine(String content) {
        if (content == null || content.isEmpty()) return null;

        // 1) 引號內含日文/中文的片段優先（推理模型常見: Possible translation: "...."）
        java.util.regex.Matcher qm = java.util.regex.Pattern
                .compile("[\"「]([^\"」]{2,200})[\"」]")
                .matcher(content);
        String bestQ = null;
        int bestQScore = -1;
        while (qm.find()) {
            String q = qm.group(1).trim();
            int sc = scriptScore(q);
            if (sc > bestQScore) {
                bestQScore = sc;
                bestQ = q;
            }
        }
        if (bestQScore >= 4) return bestQ;

        // 2) 逐行打分
        String[] lines = content.split("\r?\n");
        String best = null;
        int bestScore = -1;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            // 去掉常見前綴
            line = line.replaceFirst("(?i)^(possible\\s+translation|translation|final|answer|output)\\s*[:：-]\\s*", "");
            line = line.trim();
            if ((line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2)
                    || (line.startsWith("「") && line.endsWith("」") && line.length() >= 2)) {
                line = line.substring(1, line.length() - 1).trim();
            }
            String low = line.toLowerCase();
            if (low.startsWith("the user") || low.startsWith("so we") || low.startsWith("i need")
                    || low.startsWith("probably") || low.startsWith("could be") || low.startsWith("wait")
                    || low.startsWith("next,") || low.startsWith("putting it")
                    || low.startsWith("starting with") || low.startsWith("alternatively")
                    || low.contains("translates to") || low.contains("output only")
                    || low.contains("can be translated")) {
                continue;
            }
            int score = scriptScore(line);
            if (line.length() <= 120) score += 2;
            if (line.length() > 200) score -= 3;
            if (score > bestScore) {
                bestScore = score;
                best = line;
            }
        }
        if (bestScore < 3) return null;
        return best;
    }

    /** CJK/假名越多分越高；純英文很低 */
    private static int scriptScore(String line) {
        if (line == null || line.isEmpty()) return -99;
        int cjk = 0, kana = 0, latin = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF)) cjk++;
            else if ((cp >= 0x3040 && cp <= 0x30FF) || (cp >= 0x31F0 && cp <= 0x31FF)) kana++;
            else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) latin++;
        }
        int score = cjk * 3 + kana * 4;
        if (latin > cjk + kana) score -= 6;
        return score;
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
