package com.xiapin.ime;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android 版嘸蝦米字根提示：替代桌面版的 boshiamy_comment.lua。
 * 讀 xiapin_liur / openxiami（安裝時已隨 assets 部署），
 * 對候選文字回傳對應字根碼；並提供 code→字根字 查表，
 * 讓「單碼／多碼字根」在候選列一律置頂（e→一、a→對、ma→買…）。
 */
public final class BoshiamyComment {
    private static final String TAG = "BoshiamyComment";
    private static final int MAX_PHRASE_CHARS = 4;
    private static final int MAX_CODE_LEN = 4;

    // 單字 -> 字根碼清單（可能多碼）
    private final Map<String, List<String>> roots = new HashMap<>();
    // 字根碼 (1–4 碼 a-z) -> 權重最高的字根字
    private final Map<String, String> codeToRoot = new HashMap<>();
    private final Map<String, Integer> codeToRootWeight = new HashMap<>();

    public BoshiamyComment(FileResolver resolver) {
        // 只吃字根表，不吃拼音表（避免 我\te 蓋掉 一\te）
        readLookup(resolver.open("xiapin_liur.dict.yaml"));
        readLookup(resolver.open("openxiami_TCJP.dict.yaml"));
        readLookup(resolver.open("openxiami_TradExt.dict.yaml"));
        Log.i(TAG, "codeToRoot size=" + codeToRoot.size()
                + " single=" + countSingleLetterRoots());
    }

    /** 由外部提供開檔方式（shared 目錄或 assets 皆可）。 */
    public interface FileResolver {
        InputStream open(String name);
    }

    private int countSingleLetterRoots() {
        int n = 0;
        for (String k : codeToRoot.keySet()) {
            if (k.length() == 1) n++;
        }
        return n;
    }

    private void readLookup(InputStream in) {
        if (in == null) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            boolean inData = false;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.equals("...")) { inData = true; continue; }
                if (!inData || line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String text = line.substring(0, tab).trim();
                String rest = line.substring(tab + 1);
                // 字典格式: 字\t碼\tweight
                int tab2 = rest.indexOf('\t');
                String code = (tab2 < 0 ? rest : rest.substring(0, tab2)).trim();
                int weight = 0;
                if (tab2 >= 0) {
                    String w = rest.substring(tab2 + 1).trim();
                    int tab3 = w.indexOf('\t');
                    if (tab3 >= 0) w = w.substring(0, tab3).trim();
                    try {
                        weight = Integer.parseInt(w);
                    } catch (NumberFormatException ignored) {
                        weight = 0;
                    }
                }
                if (text.length() != 1) continue;          // 只收單字
                if (code.isEmpty() || code.indexOf(',') >= 0 || code.indexOf('.') >= 0) continue;
                int cp = text.codePointAt(0);
                if (cp < 0x3400 || cp > 0x9FFF) continue;   // 只收基本 CJK

                // 全 a-z、長度 1–4 → 字根碼；同碼取 weight 最高
                if (isAzCode(code) && code.length() <= MAX_CODE_LEN) {
                    Integer prev = codeToRootWeight.get(code);
                    if (prev == null || weight > prev) {
                        codeToRoot.put(code, text);
                        codeToRootWeight.put(code, weight);
                    }
                }

                roots.computeIfAbsent(text, k -> new ArrayList<>())
                     .removeIf(c -> c.equals(code));
                // 短碼、高權重的碼排前面（註解顯示用）
                List<String> list = roots.get(text);
                int insertAt = list.size();
                for (int i = 0; i < list.size(); i++) {
                    // 粗略：較短碼優先
                    if (code.length() < list.get(i).length()) {
                        insertAt = i;
                        break;
                    }
                }
                list.add(insertAt, code);
            }
        } catch (IOException e) {
            Log.e(TAG, "read lookup failed", e);
        }
    }

    private static boolean isAzCode(String code) {
        if (code == null || code.isEmpty()) return false;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch < 'a' || ch > 'z') return false;
        }
        return true;
    }

    /** 對候選文字產生字根註解；非 CJK / 超長則回 null。 */
    public String commentFor(String text) {
        if (text == null || text.isEmpty()) return null;
        boolean allAscii = true;
        for (int i = 0; i < text.length(); i++) {
            if (text.codePointAt(i) > 0x7F) { allAscii = false; break; }
        }
        if (allAscii) return null;

        int charCount = text.codePointCount(0, text.length());
        if (charCount > MAX_PHRASE_CHARS) return null;

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cp < 0x3400 || cp > 0x9FFF) return null;
            List<String> codes = roots.get(ch);
            if (codes == null || codes.isEmpty()) return null;
            parts.add(codes.get(0));
            i += Character.charCount(cp);
        }
        if (charCount == 1) {
            List<String> s = roots.get(text);
            if (s == null || s.isEmpty()) return null;
            if (s.size() == 1) return s.get(0);
            return String.join(" / ", s.subList(0, Math.min(s.size(), 3)));
        }
        return String.join("·", parts);
    }

    /**
     * 查 1–4 碼 a-z 字根碼對應的字根字（同碼取字典 weight 最高者）。
     * 用於候選置頂 + 空白上屏：e→一、a→對、ma→買、ay→成…
     */
    public String rootForCode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_CODE_LEN) return null;
        if (!isAzCode(code)) return null;
        return codeToRoot.get(code);
    }

    /** 是否為已知字根碼（有對應字）。 */
    public boolean hasRootCode(String code) {
        return rootForCode(code) != null;
    }
}
