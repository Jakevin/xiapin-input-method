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
import java.util.Map;

/**
 * 候選字個人使用頻率：越常上屏的字/詞，排序越前。
 * 檔案：filesDir/char_user.tsv  （text\\tcount）
 */
public final class CharUsageFreq {
    private static final String TAG = "CharUsageFreq";
    private final Map<String, Integer> counts = new HashMap<>();
    private File file;

    public void setFile(File f) {
        this.file = f;
        load();
    }

    public void reload() { load(); }

    /** 上屏一段文字時，為整詞 + 每個單字各 +1 */
    public void recordCommitted(String text) {
        if (text == null || text.isEmpty()) return;
        boolean any = false;
        // 整詞（2+ 字）
        if (text.length() >= 2 && isAllCjk(text)) {
            bump(text, 1);
            any = true;
        }
        // 單字
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            if (isCjk(cp)) {
                bump(new String(Character.toChars(cp)), 1);
                any = true;
            }
            i += n;
        }
        if (any) save();
    }

    public int getCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        synchronized (counts) {
            Integer c = counts.get(text);
            return c == null ? 0 : c;
        }
    }

    /** 候選排序用：整詞次數優先，否則用首字次數 */
    public int scoreForCandidate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int whole = getCount(text);
        if (whole > 0) return whole * 1000 + text.length(); // 整詞略加分
        // 首字
        int cp = text.codePointAt(0);
        if (!isCjk(cp)) return 0;
        return getCount(new String(Character.toChars(cp)));
    }

    private void bump(String key, int delta) {
        synchronized (counts) {
            Integer c = counts.get(key);
            int n = (c == null ? 0 : c) + delta;
            if (n <= 0) counts.remove(key);
            else counts.put(key, n);
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
            synchronized (counts) {
                counts.clear();
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int tab = line.lastIndexOf('\t');
                    if (tab <= 0) continue;
                    String text = line.substring(0, tab);
                    try {
                        int c = Integer.parseInt(line.substring(tab + 1).trim());
                        if (c > 0) counts.put(text, c);
                    } catch (NumberFormatException ignored) {}
                }
            }
            Log.i(TAG, "loaded " + counts.size() + " entries");
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
        }
    }

    private void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Integer> snap;
            synchronized (counts) {
                snap = new HashMap<>(counts);
            }
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                w.write("# text\tcount\n");
                for (Map.Entry<String, Integer> e : snap.entrySet()) {
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
