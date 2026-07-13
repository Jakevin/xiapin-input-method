package com.xiapin.ime;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 關聯字字典：essay 詞頻 + 使用者點選頻率。
 * 排序：使用者次數優先（越常用越前），同分再比 essay 權重。
 */
public final class AssociationDict {
    private static final String TAG = "AssociationDict";
    private static final int MAX_RESULTS = 12;
    private static final int MAX_PREFIX = 2;
    private static final long USER_BOOST = 1_000_000L;

    public static final class UserEntry {
        public final String prefix;
        public final String next;
        public int count;

        public UserEntry(String prefix, String next, int count) {
            this.prefix = prefix;
            this.next = next;
            this.count = count;
        }
    }

    private final Map<String, Map<String, Integer>> table = new HashMap<>();
    private final Map<String, Integer> userCount = new HashMap<>();
    private File userFile;
    private volatile boolean ready = false;

    public boolean isReady() { return ready; }

    public void setUserFile(File file) {
        this.userFile = file;
        loadUserFreq();
    }

    public File getUserFile() { return userFile; }

    public void loadAsync(InputStream in) {
        if (in == null) return;
        new Thread(() -> {
            try {
                load(in);
            } catch (Throwable t) {
                Log.e(TAG, "load failed", t);
            }
        }, "assoc-load").start();
    }

    public void load(InputStream in) throws Exception {
        Map<String, Map<String, Integer>> tmp = new HashMap<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String text = line.substring(0, tab);
                if (text.length() < 2 || text.length() > 8) continue;
                if (!isCjkPhrase(text)) continue;
                int weight = 1;
                try {
                    String w = line.substring(tab + 1).trim();
                    int sp = w.indexOf(' ');
                    if (sp > 0) w = w.substring(0, sp);
                    weight = Integer.parseInt(w);
                } catch (NumberFormatException ignored) {}
                if (weight <= 0) weight = 1;

                add(tmp, text.substring(0, 1), text.substring(1, 2), weight);
                if (text.length() >= 3) {
                    add(tmp, text.substring(0, 2), text.substring(2, 3), weight);
                }
            }
        }
        aliasPrefix(tmp, "台", "臺");
        aliasPrefix(tmp, "臺", "台");

        synchronized (table) {
            table.clear();
            table.putAll(tmp);
        }
        ready = true;
        Log.i(TAG, "loaded prefixes=" + tmp.size());
    }

    private static void add(Map<String, Map<String, Integer>> tmp,
                            String prefix, String next, int weight) {
        if (prefix.isEmpty() || next.isEmpty()) return;
        Map<String, Integer> m = tmp.get(prefix);
        if (m == null) {
            m = new HashMap<>();
            tmp.put(prefix, m);
        }
        Integer old = m.get(next);
        m.put(next, (old == null ? 0 : old) + weight);
    }

    private static void aliasPrefix(Map<String, Map<String, Integer>> tmp,
                                    String dst, String src) {
        Map<String, Integer> srcMap = tmp.get(src);
        if (srcMap == null) return;
        Map<String, Integer> dstMap = tmp.get(dst);
        if (dstMap == null) {
            dstMap = new HashMap<>();
            tmp.put(dst, dstMap);
        }
        for (Map.Entry<String, Integer> e : srcMap.entrySet()) {
            Integer old = dstMap.get(e.getKey());
            dstMap.put(e.getKey(), (old == null ? 0 : old) + e.getValue());
        }
    }

    private static boolean isCjkPhrase(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!((cp >= 0x3400 && cp <= 0x9FFF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0x20000 && cp <= 0x2FA1F))) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    public void recordUse(String prefix, String next) {
        if (prefix == null || prefix.isEmpty() || next == null || next.isEmpty()) return;
        bump(prefix, next, 1);
        if ("台".equals(prefix)) bump("臺", next, 1);
        else if ("臺".equals(prefix)) bump("台", next, 1);
        saveUserFreq();
    }

    private void bump(String prefix, String next, int delta) {
        String key = prefix + "\t" + next;
        synchronized (userCount) {
            Integer c = userCount.get(key);
            int n = (c == null ? 0 : c) + delta;
            if (n <= 0) userCount.remove(key);
            else userCount.put(key, n);
        }
    }

    /** 編輯器：列出全部使用者頻率（次數高→前） */
    public List<UserEntry> getUserEntries() {
        List<UserEntry> out = new ArrayList<>();
        synchronized (userCount) {
            for (Map.Entry<String, Integer> e : userCount.entrySet()) {
                String k = e.getKey();
                int tab = k.indexOf('\t');
                if (tab < 0) continue;
                out.add(new UserEntry(k.substring(0, tab), k.substring(tab + 1), e.getValue()));
            }
        }
        Collections.sort(out, new Comparator<UserEntry>() {
            @Override
            public int compare(UserEntry a, UserEntry b) {
                int c = Integer.compare(b.count, a.count);
                if (c != 0) return c;
                c = a.prefix.compareTo(b.prefix);
                if (c != 0) return c;
                return a.next.compareTo(b.next);
            }
        });
        return out;
    }

    /** 編輯器：設定次數（<=0 刪除） */
    public void setUserCount(String prefix, String next, int count) {
        if (prefix == null || prefix.isEmpty() || next == null || next.isEmpty()) return;
        String key = prefix + "\t" + next;
        synchronized (userCount) {
            if (count <= 0) userCount.remove(key);
            else userCount.put(key, count);
        }
        saveUserFreq();
    }

    public void deleteUser(String prefix, String next) {
        setUserCount(prefix, next, 0);
    }

    public void clearAllUser() {
        synchronized (userCount) {
            userCount.clear();
        }
        saveUserFreq();
    }

    /** 從檔案重載（編輯器存檔後、IME 開鍵盤時） */
    public void reloadUserFreq() {
        loadUserFreq();
    }

    public List<String> lookup(String context) {
        if (!ready || context == null || context.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < context.length(); ) {
            int cp = context.codePointAt(i);
            int n = Character.charCount(cp);
            if ((cp >= 0x3400 && cp <= 0x9FFF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0x20000 && cp <= 0x2FA1F)) {
                sb.appendCodePoint(cp);
            } else {
                sb.setLength(0);
            }
            i += n;
        }
        String cjk = sb.toString();
        if (cjk.isEmpty()) return Collections.emptyList();

        for (int len = Math.min(MAX_PREFIX, cjk.length()); len >= 1; len--) {
            String prefix = cjk.substring(cjk.length() - len);
            List<String> got = lookupExact(prefix);
            if (!got.isEmpty()) return got;
        }
        return Collections.emptyList();
    }

    private List<String> lookupExact(String prefix) {
        // essay 下一字權重
        Map<String, Integer> essay;
        synchronized (table) {
            Map<String, Integer> m = table.get(prefix);
            essay = (m == null) ? new HashMap<String, Integer>() : new HashMap<String, Integer>(m);
        }
        // 合併「只有使用者頻率、essay 沒有」的下一字（如 羅→旭）
        synchronized (userCount) {
            String pfxTab = prefix + "\t";
            for (Map.Entry<String, Integer> e : userCount.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith(pfxTab)) continue;
                String next = k.substring(pfxTab.length());
                if (next.isEmpty()) continue;
                if (!essay.containsKey(next)) {
                    essay.put(next, 0); // essay 權重 0，靠 user 次數排序
                }
            }
        }
        if (essay.isEmpty()) return Collections.emptyList();

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(essay.entrySet());
        final String pfx = prefix;
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                long sa = score(pfx, a.getKey(), a.getValue());
                long sb = score(pfx, b.getKey(), b.getValue());
                return Long.compare(sb, sa);
            }
        });
        List<String> out = new ArrayList<>(Math.min(MAX_RESULTS, entries.size()));
        for (int i = 0; i < entries.size() && out.size() < MAX_RESULTS; i++) {
            out.add(entries.get(i).getKey());
        }
        return out;
    }

    private long score(String prefix, String next, int essayWeight) {
        int u;
        synchronized (userCount) {
            Integer c = userCount.get(prefix + "\t" + next);
            u = c == null ? 0 : c;
        }
        return u * USER_BOOST + (essayWeight > 0 ? essayWeight : 0);
    }

    private void loadUserFreq() {
        if (userFile == null || !userFile.isFile()) return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(userFile), StandardCharsets.UTF_8))) {
            String line;
            synchronized (userCount) {
                userCount.clear();
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\t");
                    if (parts.length < 3) continue;
                    try {
                        int c = Integer.parseInt(parts[2].trim());
                        if (c > 0) userCount.put(parts[0] + "\t" + parts[1], c);
                    } catch (NumberFormatException ignored) {}
                }
            }
            Log.i(TAG, "user freq loaded entries=" + userCount.size());
        } catch (Exception e) {
            Log.w(TAG, "load user freq failed", e);
        }
    }

    private void saveUserFreq() {
        if (userFile == null) return;
        try {
            File parent = userFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Integer> snap;
            synchronized (userCount) {
                snap = new HashMap<>(userCount);
            }
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(userFile), StandardCharsets.UTF_8))) {
                w.write("# prefix\tnext\tcount\n");
                for (Map.Entry<String, Integer> e : snap.entrySet()) {
                    String k = e.getKey();
                    int tab = k.indexOf('\t');
                    if (tab < 0) continue;
                    w.write(k.substring(0, tab));
                    w.write('\t');
                    w.write(k.substring(tab + 1));
                    w.write('\t');
                    w.write(String.valueOf(e.getValue()));
                    w.write('\n');
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "save user freq failed", e);
        }
    }

    /**
     * 編輯器用：不依賴 IME 實例，直接讀寫檔案。
     */
    public static List<UserEntry> readUserFile(File file) {
        List<UserEntry> out = new ArrayList<>();
        if (file == null || !file.isFile()) return out;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length < 3) continue;
                try {
                    int c = Integer.parseInt(parts[2].trim());
                    if (c > 0) out.add(new UserEntry(parts[0], parts[1], c));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "readUserFile failed", e);
        }
        Collections.sort(out, new Comparator<UserEntry>() {
            @Override
            public int compare(UserEntry a, UserEntry b) {
                int c = Integer.compare(b.count, a.count);
                if (c != 0) return c;
                c = a.prefix.compareTo(b.prefix);
                if (c != 0) return c;
                return a.next.compareTo(b.next);
            }
        });
        return out;
    }

    public static void writeUserFile(File file, List<UserEntry> entries) {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                w.write("# prefix\tnext\tcount\n");
                if (entries != null) {
                    for (UserEntry e : entries) {
                        if (e == null || e.prefix == null || e.next == null || e.count <= 0) continue;
                        w.write(e.prefix);
                        w.write('\t');
                        w.write(e.next);
                        w.write('\t');
                        w.write(String.valueOf(e.count));
                        w.write('\n');
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "writeUserFile failed", e);
        }
    }
}
