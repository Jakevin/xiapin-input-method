package com.xiapin.ime;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 候選使用頻率（拼音 / 字根通用）：
 * - 全域：字/詞用越多越前
 * - 依輸入碼：同一碼（如 guan）常選的字更前（拼音體驗關鍵）
 *
 * 檔案 filesDir/char_user.tsv：
 *   text\tcount              全域
 *   @code\ttext\tcount       碼表層（code 正規化小寫、無空格）
 */
public final class CharUsageFreq {
    private static final String TAG = "CharUsageFreq";
    /** 碼表層權重大於全域，一次選取就夠壓過預設序 */
    private static final int CODE_BOOST = 1_000_000;

    private final Map<String, Integer> global = new HashMap<>();
    /** key = code + "\t" + text */
    private final Map<String, Integer> byCode = new HashMap<>();
    private File file;

    public void setFile(File f) {
        this.file = f;
        load();
    }

    public void reload() { load(); }

    /** 正規化輸入碼：小寫、去空白與 ' */
    public static String normalizeCode(String preedit) {
        if (preedit == null) return "";
        String s = preedit.replace(" ", "").replace("'", "").trim();
        if (s.isEmpty()) return "";
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 上屏時記錄：整詞 + 單字（全域）+ 若有 code 則記錄 (code, text)。
     */
    public void recordSelection(String code, String text) {
        if (text == null || text.isEmpty()) return;
        boolean any = false;
        String c = normalizeCode(code);

        if (text.length() >= 2 && isAllCjk(text)) {
            bumpGlobal(text, 1);
            if (!c.isEmpty()) bumpCode(c, text, 1);
            any = true;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            if (isCjk(cp)) {
                String ch = new String(Character.toChars(cp));
                bumpGlobal(ch, 1);
                // 單字也記在 code 下（拼音單字最常見）
                if (!c.isEmpty() && text.length() == n) {
                    bumpCode(c, ch, 1);
                }
                any = true;
            }
            i += n;
        }
        // 多字詞：code 對整詞再記一次（上面 length>=2 已記）
        if (any) save();
    }

    /** 相容舊呼叫 */
    public void recordCommitted(String text) {
        recordSelection(null, text);
    }

    public int getCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        synchronized (global) {
            Integer c = global.get(text);
            return c == null ? 0 : c;
        }
    }

    public int getCodeCount(String code, String text) {
        if (text == null || text.isEmpty()) return 0;
        String c = normalizeCode(code);
        if (c.isEmpty()) return 0;
        String key = c + "\t" + text;
        synchronized (byCode) {
            Integer n = byCode.get(key);
            return n == null ? 0 : n;
        }
    }

    /**
     * 排序分數（越大越前）：
     * 1) 該輸入碼下選過幾次 × CODE_BOOST
     * 2) 全域整詞/字次數
     */
    public int scoreForCandidate(String text, String code) {
        if (text == null || text.isEmpty()) return 0;
        int codeHits = getCodeCount(code, text);
        int g = getCount(text);
        if (g == 0 && text.length() > 0) {
            // 退回首字全域
            int cp = text.codePointAt(0);
            if (isCjk(cp)) g = getCount(new String(Character.toChars(cp)));
        }
        return codeHits * CODE_BOOST + g * 1000 + Math.min(text.length(), 9);
    }

    /** 無 code 時 */
    public int scoreForCandidate(String text) {
        return scoreForCandidate(text, null);
    }

    private void bumpGlobal(String key, int delta) {
        synchronized (global) {
            Integer c = global.get(key);
            int n = (c == null ? 0 : c) + delta;
            if (n <= 0) global.remove(key);
            else global.put(key, n);
        }
    }

    private void bumpCode(String code, String text, int delta) {
        String key = code + "\t" + text;
        synchronized (byCode) {
            Integer c = byCode.get(key);
            int n = (c == null ? 0 : c) + delta;
            if (n <= 0) byCode.remove(key);
            else byCode.put(key, n);
        }
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x3400 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2FA1F);
    }

    private static boolean isAllCjk(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!isCjk(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            synchronized (global) {
                global.clear();
            }
            synchronized (byCode) {
                byCode.clear();
            }
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                // @code\ttext\tcount
                if (line.startsWith("@")) {
                    String rest = line.substring(1);
                    String[] p = rest.split("\t");
                    if (p.length >= 3) {
                        try {
                            int c = Integer.parseInt(p[2].trim());
                            if (c > 0) {
                                String code = normalizeCode(p[0]);
                                if (!code.isEmpty() && p[1] != null && !p[1].isEmpty()) {
                                    synchronized (byCode) {
                                        byCode.put(code + "\t" + p[1], c);
                                    }
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    continue;
                }
                int tab = line.lastIndexOf('\t');
                if (tab <= 0) continue;
                String text = line.substring(0, tab);
                try {
                    int c = Integer.parseInt(line.substring(tab + 1).trim());
                    if (c > 0) {
                        synchronized (global) {
                            global.put(text, c);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            Log.i(TAG, "loaded global=" + global.size() + " byCode=" + byCode.size());
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
        }
    }

    private void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Integer> gSnap;
            Map<String, Integer> cSnap;
            synchronized (global) {
                gSnap = new HashMap<>(global);
            }
            synchronized (byCode) {
                cSnap = new HashMap<>(byCode);
            }
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                w.write("# text\\tcount  OR  @code\\ttext\\tcount\n");
                for (Map.Entry<String, Integer> e : gSnap.entrySet()) {
                    w.write(e.getKey());
                    w.write('\t');
                    w.write(String.valueOf(e.getValue()));
                    w.write('\n');
                }
                for (Map.Entry<String, Integer> e : cSnap.entrySet()) {
                    // key = code\ttext
                    w.write('@');
                    w.write(e.getKey());
                    w.write('\t');
                    w.write(String.valueOf(e.getValue()));
                    w.write('\n');
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }
}
