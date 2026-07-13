package com.xiapin.ime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 * 鍵盤內翻譯：來源語言固定 auto（自動判斷），
 * 目標僅 英 / 日 / 簡中。
 */
public final class TranslateHelper {
    private static final String TAG = "TranslateHelper";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public interface Callback {
        /** options：可選譯文列表（第 1 個為主譯文） */
        void onResult(String source, List<String> options, String error);
    }

    /** 目標語言（來源永遠 auto） */
    public static final class TargetLang {
        public final String label; // 顯示：英 / 日 / 简
        public final String tl;    // Google code

        public TargetLang(String label, String tl) {
            this.label = label;
            this.tl = tl;
        }
    }

    public static final TargetLang[] TARGETS = {
            new TargetLang("英", "en"),
            new TargetLang("日", "ja"),
            new TargetLang("简", "zh-CN"),
    };

    public static void translate(String text, String tl, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            MAIN.post(() -> cb.onResult(text, new ArrayList<String>(), null));
            return;
        }
        final String src = text.trim();
        final int mySeq = SEQ.incrementAndGet();
        EXEC.execute(() -> {
            List<String> options = new ArrayList<>();
            String err = null;
            try {
                options = translateGoogle(src, "auto", tl);
                if (options.isEmpty()) {
                    String one = translateMyMemory(src, "autodetect", tl);
                    if (one != null && !one.isEmpty()) options.add(one);
                }
                if (options.isEmpty()) err = "翻譯失敗";
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

    /** 回傳多個可選譯文（主譯文 + 可能的替代表述） */
    private static List<String> translateGoogle(String text, String sl, String tl) throws Exception {
        String q = URLEncoder.encode(text, "UTF-8");
        // dt=t 句子；dt=at 替代；dt=rm 可省略
        String urlStr = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=" + sl + "&tl=" + tl + "&dt=t&dt=at&q=" + q;
        String body = httpGet(urlStr, 8000);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) return new ArrayList<>();

        JSONArray root = new JSONArray(body);
        // [0] = [ [translated, original, ...], ... ]
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
        // [5] 有時含 alternative translations: [ [word, null, [[alt, score],...]], ...]
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
        // MyMemory 不支援 autodetect 很好，用 zh-TW|en 等 fallback
        String src = "autodetect".equals(sl) ? "zh-TW" : sl;
        if ("zh-CN".equals(tl)) {
            // MyMemory 用 zh-CN
        }
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
        if (code != 200) {
            conn.disconnect();
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        conn.disconnect();
        return sb.toString();
    }
}
