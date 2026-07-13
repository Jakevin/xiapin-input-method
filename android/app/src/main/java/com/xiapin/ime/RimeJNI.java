package com.xiapin.ime;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * JNI 橋接到 librime（rime_api.h C API）。
 * 原生實作在 src/main/cpp/rime_jni.cc。
 */
public final class RimeJNI {
    private static final String TAG = "RimeJNI";

    static {
        try {
            System.loadLibrary("xiapin_rime");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "failed to load xiapin_rime native lib", e);
        }
    }

    // ---- 單例 ----
    private static RimeJNI instance;

    public static synchronized RimeJNI getInstance() {
        if (instance == null) instance = new RimeJNI();
        return instance;
    }

    private RimeJNI() {}

    /** 啟動 librime。sharedDir = assets 解出的 schema 目錄，userDir = 可寫快取目錄。 */
    public native void startup(String sharedDir, String userDir);

    /** 處理一個按鍵。keycode 用 Android KeyEvent 的 keycode，mask 用 Rime_MOD_* 位元。 */
    public native boolean processKey(int keycode, int mask);

    /** 直接餵一段按鍵序列（除錯用）。 */
    public native boolean simulateKeySequence(String seq);

    /** 提交目前組字。 */
    public native boolean commitComposition();

    /** 清空組字。 */
    public native void clearComposition();

    /** 切換選項（如 ascii_mode）。 */
    public native void setOption(String option, boolean value);

    public native boolean getOption(String option);

    /** 取得目前已提交的文字（若有）。回傳 null 表示無提交。 */
    public native String getCommitText();

    /** 候選項：文字 + 註解（字根提示）。 */
    public static class Candidate {
        public final String text;
        public final String comment;
        public Candidate(String text, String comment) {
            this.text = text;
            this.comment = comment;
        }
        @Override public String toString() { return text + (comment != null ? " (" + comment + ")" : ""); }
    }

    /** 目前組字上下文。 */
    public static class Context {
        public String preedit = "";       // 組字區（使用者正在輸入的拼音/字根）
        public int caretPos = 0;          // 游標位置
        public final List<Candidate> candidates = new ArrayList<>();
        public boolean composing = false; // 是否正在組字
    }

    public native Context getContext();

    /** 選字（page 內 index，0-based）。 */
    public native boolean selectCandidate(int index);

    /** 翻頁。forward=true 下一頁，false 上一頁。 */
    public native boolean pageCandidate(boolean forward);

    /** 切換到指定 schema（如 "xiapin" 中文 / "xiapin_english" 英文候選）。 */
    public native boolean selectSchema(String schemaId);

    // Rime 修飾鍵 mask（與 rime_api.h RimeModifier 對齊）
    public static final int MOD_SHIFT = 1 << 0;
    public static final int MOD_CONTROL = 1 << 2;
    public static final int MOD_ALT = 1 << 3;
}
