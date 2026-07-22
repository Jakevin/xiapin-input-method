package com.xiapin.ime;

import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.File;

/**
 * 蝦拼輸入法主服務。
 * 負責：啟動 Rime、顯示軟鍵盤 + 候選窗、把按鍵送進 RimeJNI、把提交文字送給編輯框。
 */
public class XiapinIME extends InputMethodService {

    private RimeJNI rime;
    private XiapinKeyboardView keyboardView;
    private CandidateView candidateView;
    private boolean deployed = false;
    private BoshiamyComment boshiamy;
    private String currentSchema = "xiapin";
    /** App 層強制插入的字根候選（顯示第 1 位）；null 表示無 */
    private String extraRootCandidate = null;
    /** 鍵盤層：0=字母 1=符號數字 */
    private int keyboardLayer = 0;
    /** 關聯字字典（essay） */
    private AssociationDict associationDict;
    /** 最近上屏的中文 context（最多 2 字，供連續聯想） */
    private String commitContext = "";
    /** 目前關聯候選 */
    private java.util.List<String> associations = new java.util.ArrayList<>();
    /** 候選字個人使用頻率 */
    private CharUsageFreq charUsage;
    /** 目前畫面上候選順序（重排後），空白上屏用第 0 個 */
    private java.util.List<String> displayCandidates = new java.util.ArrayList<>();
    /** 最近一次組字碼（空白/上屏前），供頻率學習 */
    private String lastCompositionCode = "";

    // ---- 翻譯（Gboard 風格）----
    private boolean translateMode = false;
    private int langPairIndex = 0; // TranslateHelper.TARGETS 英/日/简
    private String translateSource = "";
    private String translateResult = "";
    private java.util.List<String> translateOptions = new java.util.ArrayList<>();
    private android.widget.TextView btnTranslate;
    private android.widget.TextView btnLangPair;
    private android.widget.TextView txtTranslateHint;
    private android.widget.EditText txtTranslateSource;
    private boolean suppressSourceWatch = false;
    private android.view.View translatePanel;
    private android.view.View translateSourceRow;
    private android.widget.LinearLayout translateResultRow;
    private android.widget.TextView btnClearSource;
    private android.widget.TextView btnSendSource;
    private android.widget.TextView btnRunTranslate;
    private android.widget.TextView btnTranslateSettings;
    private final android.os.Handler translateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingTranslate;

    @Override
    public void onCreate() {
        super.onCreate();
        rime = RimeJNI.getInstance();
    }

    @Override
    public View onCreateInputView() {
        View root = getLayoutInflater().inflate(R.layout.input_view, null);
        keyboardView = root.findViewById(R.id.keyboard);
        candidateView = root.findViewById(R.id.candidates);
        keyboardView.setService(this);
        candidateView.setService(this);
        if (boshiamy != null) candidateView.setBoshiamy(boshiamy);
        keyboardLayer = 0;
        keyboardView.setLayer(0);
        bindTranslateUi(root);
        updateLang();
        updateTranslateUi();
        return root;
    }

    private void bindTranslateUi(View root) {
        btnTranslate = root.findViewById(R.id.btn_translate);
        btnLangPair = root.findViewById(R.id.btn_lang_pair);
        txtTranslateHint = root.findViewById(R.id.txt_translate_hint);
        txtTranslateSource = root.findViewById(R.id.txt_translate_source);
        translatePanel = root.findViewById(R.id.translate_panel);
        translateSourceRow = root.findViewById(R.id.translate_source_row);
        translateResultRow = root.findViewById(R.id.translate_result_row);
        btnClearSource = root.findViewById(R.id.btn_clear_source);
        btnRunTranslate = root.findViewById(R.id.btn_run_translate);
        btnTranslateSettings = root.findViewById(R.id.btn_translate_settings);
        btnSendSource = root.findViewById(R.id.btn_send_source);
        if (btnTranslate != null) {
            btnTranslate.setOnClickListener(v -> toggleTranslateMode());
        }
        if (btnLangPair != null) {
            btnLangPair.setOnClickListener(v -> cycleLangPair());
        }
        if (btnClearSource != null) {
            btnClearSource.setOnClickListener(v -> clearTranslateSource());
        }
        if (btnRunTranslate != null) {
            btnRunTranslate.setOnClickListener(v -> manualTranslate());
        }
        if (btnTranslateSettings != null) {
            btnTranslateSettings.setOnClickListener(v -> openTranslateSettings());
        }
        if (btnSendSource != null) {
            btnSendSource.setOnClickListener(v -> sendOriginalText());
        }
        setupTranslateEditText();
    }

    /** 原文用 EditText：可編輯；不彈系統鍵盤（由我們鍵盤輸入） */
    private void setupTranslateEditText() {
        if (txtTranslateSource == null) return;
        // 避免點 EditText 又跳出另一個鍵盤
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            txtTranslateSource.setShowSoftInputOnFocus(false);
        }
        txtTranslateSource.setOnClickListener(v -> {
            // 點一下把游標放到最後，方便繼續打
            if (translateMode) {
                txtTranslateSource.requestFocus();
                txtTranslateSource.setSelection(txtTranslateSource.getText().length());
            }
        });
        txtTranslateSource.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (suppressSourceWatch || !translateMode) return;
                String now = s == null ? "" : s.toString();
                if (now.equals(translateSource == null ? "" : translateSource)) return;
                translateSource = now;
                if (now.trim().isEmpty()) {
                    translateOptions = new java.util.ArrayList<>();
                    translateResult = "";
                    cancelPendingTranslate();
                    renderTranslateResults();
                    updateTranslateUi();
                } else {
                    // gtx 短延遲；LLM 停頓 3 秒後自動翻（可取消重計時）
                    scheduleTranslate(now);
                    updateTranslateUi();
                }
            }
        });
    }

    /** 程式寫入 EditText，不觸發 TextWatcher 重入 */
    private void setSourceEditText(String text, boolean moveCursorEnd) {
        if (txtTranslateSource == null) return;
        suppressSourceWatch = true;
        try {
            String cur = txtTranslateSource.getText() != null
                    ? txtTranslateSource.getText().toString() : "";
            String next = text == null ? "" : text;
            if (!cur.equals(next)) {
                txtTranslateSource.setText(next);
            }
            if (moveCursorEnd) {
                int len = txtTranslateSource.getText() != null
                        ? txtTranslateSource.getText().length() : 0;
                txtTranslateSource.setSelection(len);
            }
        } finally {
            suppressSourceWatch = false;
        }
    }

    private void clearTranslateSource() {
        translateSource = "";
        translateResult = "";
        translateOptions = new java.util.ArrayList<>();
        cancelPendingTranslate();
        setSourceEditText("", true);
        if (candidateView != null) candidateView.forceClearTranslateDisplay();
        renderTranslateResults();
        updateTranslateUi();
        refresh();
    }

    private void toggleTranslateMode() {
        if (translateMode) {
            // ★ 關掉翻譯：先把原文 EditText 內容送進 App，避免文字消失
            flushTranslateSourceToApp();
            translateMode = false;
            translateSource = "";
            translateResult = "";
            translateOptions = new java.util.ArrayList<>();
            cancelPendingTranslate();
            setSourceEditText("", false);
            if (candidateView != null) candidateView.forceClearTranslateDisplay();
            renderTranslateResults();
            if (rime != null) {
                rime.clearComposition();
                extraRootCandidate = null;
            }
            updateTranslateUi();
            refresh();
            return;
        }
        // 開啟翻譯
        translateMode = true;
        translateSource = "";
        translateResult = "";
        translateOptions = new java.util.ArrayList<>();
        cancelPendingTranslate();
        setSourceEditText("", true);
        updateTranslateUi();
        if (rime != null) {
            rime.clearComposition();
            extraRootCandidate = null;
            if (candidateView != null) candidateView.update(rime.getContext());
        }
        if (txtTranslateSource != null) {
            txtTranslateSource.requestFocus();
            txtTranslateSource.setSelection(0);
        }
        renderTranslateResults();
    }

    /**
     * 把原文 EditText 寫入 App（不關閉翻譯模式）。
     * 用於：送原文按鈕、Enter、關閉翻譯前 flush。
     * @param clearAfter 送出後是否清空 EditText（送原文/Enter=true；關翻譯=true）
     */
    private void flushTranslateSourceToApp(boolean clearAfter) {
        String src = "";
        if (txtTranslateSource != null && txtTranslateSource.getText() != null) {
            src = txtTranslateSource.getText().toString();
        }
        if ((src == null || src.isEmpty()) && translateSource != null) {
            src = translateSource;
        }
        if (src == null || src.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(src, 1);
            android.util.Log.i("XiapinIME", "TR send original: [" + src + "]");
        }
        if (clearAfter) {
            translateSource = "";
            translateResult = "";
            translateOptions = new java.util.ArrayList<>();
            cancelPendingTranslate();
            setSourceEditText("", true);
            renderTranslateResults();
            updateTranslateUi();
        }
    }

    private void flushTranslateSourceToApp() {
        flushTranslateSourceToApp(true);
    }

    /** 不送譯文，直接把原文中文送進 App */
    private void sendOriginalText() {
        if (!translateMode) return;
        String src = translateSource == null ? "" : translateSource.trim();
        if (src.isEmpty() && txtTranslateSource != null && txtTranslateSource.getText() != null) {
            src = txtTranslateSource.getText().toString().trim();
        }
        if (src.isEmpty()) return;
        flushTranslateSourceToApp(true);
        // 清 Rime 組字殘留
        if (rime != null) {
            rime.clearComposition();
            extraRootCandidate = null;
            drainCommitText();
        }
        if (candidateView != null) candidateView.update(rime != null ? rime.getContext() : null);
    }

    private void cycleLangPair() {
        langPairIndex = (langPairIndex + 1) % TranslateHelper.TARGETS.length;
        updateTranslateUi();
        if (translateMode && translateSource != null && !translateSource.isEmpty()) {
            translateOptions = new java.util.ArrayList<>();
            translateResult = "";
            renderTranslateResults();
            scheduleTranslate(translateSource);
        }
    }

    private void updateTranslateUi() {
        TranslateHelper.TargetLang target = TranslateHelper.TARGETS[langPairIndex];
        if (btnTranslate != null) {
            btnTranslate.setText(translateMode ? "自動" : "譯");
            btnTranslate.setBackgroundResource(translateMode ? R.drawable.feature_btn_on : R.drawable.feature_btn_bg);
            btnTranslate.setTextColor(translateMode ? 0xFFFFFFFF : 0xFFE8EAED);
        }
        if (btnLangPair != null) {
            btnLangPair.setVisibility(translateMode ? View.VISIBLE : View.GONE);
            btnLangPair.setText("→" + target.label);
        }
        if (btnClearSource != null) {
            btnClearSource.setVisibility(translateMode ? View.VISIBLE : View.GONE);
        }
        boolean llm = TranslatePrefs.isLlm(this);
        if (btnRunTranslate != null) {
            // LLM：一定顯示手動翻譯；gtx 也可手動補翻
            btnRunTranslate.setVisibility(translateMode ? View.VISIBLE : View.GONE);
            boolean hasSrc = translateSource != null && !translateSource.trim().isEmpty();
            btnRunTranslate.setAlpha(hasSrc ? 1f : 0.45f);
            btnRunTranslate.setEnabled(hasSrc);
            btnRunTranslate.setText(llm ? "翻譯" : "重翻");
        }
        if (btnTranslateSettings != null) {
            btnTranslateSettings.setVisibility(translateMode ? View.VISIBLE : View.GONE);
        }
        if (btnSendSource != null) {
            boolean hasSrc = translateMode && translateSource != null && !translateSource.trim().isEmpty();
            btnSendSource.setVisibility(translateMode ? View.VISIBLE : View.GONE);
            // 有原文時較亮
            btnSendSource.setAlpha(hasSrc ? 1f : 0.45f);
            btnSendSource.setEnabled(hasSrc);
        }
        if (translatePanel != null) {
            translatePanel.setVisibility(translateMode ? View.VISIBLE : View.GONE);
        }
        // 原文 EditText：顯示 buffer（組字中用 hint 旁的 preedit 由候選列顯示）
        if (txtTranslateSource != null) {
            String src = translateSource == null ? "" : translateSource;
            // 不把 preedit 混進 EditText，避免和 Rime 組字搶
            String shown = txtTranslateSource.getText() != null
                    ? txtTranslateSource.getText().toString() : "";
            if (!shown.equals(src)) {
                setSourceEditText(src, false);
            }
            txtTranslateSource.setTextColor(0xFFFFCC00);
            txtTranslateSource.setHint(translateMode ? "在此輸入原文…" : "");
        }
        if (txtTranslateHint != null) {
            txtTranslateHint.setVisibility(translateMode ? View.VISIBLE : View.GONE);
            if (translateMode) {
                if (translateSource != null && !translateSource.isEmpty()
                        && (translateOptions == null || translateOptions.isEmpty())
                        && pendingTranslate != null) {
                    boolean llmPend = TranslatePrefs.isLlm(this);
                    txtTranslateHint.setText(llmPend ? "停頓中，3 秒後翻譯…" : "翻譯中…");
                    txtTranslateHint.setTextColor(llmPend ? 0xFFFBBF24 : 0xFF9AA0A6);
                } else if (translateOptions != null && !translateOptions.isEmpty()) {
                    txtTranslateHint.setText("點譯文上屏");
                    txtTranslateHint.setTextColor(0xFF8AB4F8);
                } else if (TranslatePrefs.isLlm(this)) {
                    if (pendingTranslate != null) {
                        txtTranslateHint.setText("LLM→" + target.label + " · 停 3 秒後自動翻");
                    } else {
                        txtTranslateHint.setText("LLM→" + target.label + " · 停 3 秒自動 / 可按翻譯");
                    }
                    txtTranslateHint.setTextColor(0xFFFBBF24);
                } else {
                    txtTranslateHint.setText("自動→" + target.label + " · 點譯文才送出");
                    txtTranslateHint.setTextColor(0xFF9AA0A6);
                }
            }
        }
    }

    private String currentPreeditNorm() {
        try {
            RimeJNI.Context ctx = rime != null ? rime.getContext() : null;
            if (ctx != null && ctx.preedit != null && !ctx.preedit.isEmpty()) {
                return ctx.preedit.replace(" ", "").replace("'", "");
            }
        } catch (Exception ignored) {}
        return "";
    }


    private void openTranslateSettings() {
        try {
            android.content.Intent i = new android.content.Intent(this, TranslateSettingsActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            android.util.Log.w("XiapinIME", "open settings failed", e);
        }
    }

    /** 手動翻譯（LLM 唯一觸發點；gtx 也可按重翻） */
    private void manualTranslate() {
        if (!translateMode) return;
        String src = translateSource == null ? "" : translateSource.trim();
        if (src.isEmpty() && txtTranslateSource != null && txtTranslateSource.getText() != null) {
            src = txtTranslateSource.getText().toString().trim();
        }
        if (src.isEmpty()) return;
        if (TranslatePrefs.isLlm(this)) {
            String key = TranslatePrefs.getApiKey(this);
            if (key == null || key.isEmpty()) {
                if (txtTranslateHint != null) {
                    txtTranslateHint.setText("請先在設定填 API Key");
                    txtTranslateHint.setTextColor(0xFFF28B82);
                }
                showTranslatePlaceholder("無 Key");
                openTranslateSettings();
                return;
            }
        }
        cancelPendingTranslate();
        if (txtTranslateHint != null) {
            txtTranslateHint.setText(TranslatePrefs.isLlm(this) ? "LLM 翻譯中…" : "翻譯中…");
            txtTranslateHint.setTextColor(0xFF9AA0A6);
        }
        showTranslatePlaceholder("…");
        requestTranslateNow(src);
    }

    private void cancelPendingTranslate() {
        if (pendingTranslate != null) {
            translateHandler.removeCallbacks(pendingTranslate);
            pendingTranslate = null;
        }
    }

    /** debounce 280ms，避免每個字狂打 API；結果只進譯文列 */
    private void scheduleTranslate(final String source) {
        if (!translateMode || source == null || source.trim().isEmpty()) return;
        cancelPendingTranslate();
        final String src = source.trim();
        final boolean llm = TranslatePrefs.isLlm(this);
        // gtx：280ms；LLM：停頓 3 秒才打 API（避免每字燒錢）
        final long delayMs = llm ? 3000L : 280L;
        pendingTranslate = () -> {
            pendingTranslate = null;
            // 再次確認原文沒變、模式仍開
            if (!translateMode) return;
            String cur = translateSource == null ? "" : translateSource.trim();
            if (!src.equals(cur)) return;
            requestTranslateNow(src);
        };
        translateHandler.postDelayed(pendingTranslate, delayMs);
        updateTranslateUi();
        if (translateResultRow != null && (translateOptions == null || translateOptions.isEmpty())) {
            showTranslatePlaceholder(llm ? "3秒後翻譯…" : "…");
        }
    }

    private void requestTranslateNow(String source) {
        if (!translateMode || source == null || source.isEmpty()) return;
        TranslateHelper.TargetLang target = TranslateHelper.TARGETS[langPairIndex];
        TranslateHelper.translate(getApplicationContext(), source, target.tl, (src, options, error) -> {
            if (!translateMode) return;
            // 若原文已變，忽略過期結果
            if (translateSource == null || !source.equals(translateSource.trim())) return;
            if (error != null) {
                translateResult = "";
                translateOptions = new java.util.ArrayList<>();
                showTranslatePlaceholder("失敗");
                if (txtTranslateHint != null) {
                    txtTranslateHint.setText(error);
                    txtTranslateHint.setTextColor(0xFFF28B82);
                }
                return;
            }
            translateOptions = sanitizeTranslateOptions(options);
            translateResult = translateOptions.isEmpty() ? "" : translateOptions.get(0);
            updateTranslateUi();
            renderTranslateResults();
        });
    }


    /** 丟掉 null / "null" / 空白的假譯文 */
    private static java.util.List<String> sanitizeTranslateOptions(java.util.List<String> options) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (options == null) return out;
        for (String s : options) {
            if (s == null) continue;
            String x = s.trim();
            if (x.isEmpty()) continue;
            if ("null".equalsIgnoreCase(x) || "undefined".equalsIgnoreCase(x)) continue;
            out.add(x);
        }
        return out;
    }

    private void showTranslatePlaceholder(String text) {
        if (translateResultRow == null) return;
        translateResultRow.removeAllViews();
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF9AA0A6);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        translateResultRow.addView(tv);
    }

    /** 譯文 chip：只有點它才上屏 */
    private void renderTranslateResults() {
        if (translateResultRow == null) return;
        translateResultRow.removeAllViews();
        if (!translateMode) return;
        if (translateOptions == null || translateOptions.isEmpty()) {
            showTranslatePlaceholder(
                    (translateSource != null && !translateSource.isEmpty()) ? "…" : "譯文會出現在這裡");
            return;
        }
        float dens = getResources().getDisplayMetrics().density;
        for (int i = 0; i < translateOptions.size(); i++) {
            final String opt = translateOptions.get(i);
            if (opt == null || opt.isEmpty()) continue;
            if ("null".equalsIgnoreCase(opt.trim())) continue;
            android.widget.TextView chip = new android.widget.TextView(this);
            chip.setText(opt);
            chip.setTextSize(15);
            chip.setTextColor(0xFFE8F0FE);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setMaxLines(2);
            chip.setPadding((int) (12 * dens), (int) (6 * dens), (int) (12 * dens), (int) (6 * dens));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1A3A5C);
            bg.setCornerRadius(8 * dens);
            chip.setBackground(bg);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> insertTranslationOption(opt));
            if (i > 0) {
                View gap = new View(this);
                android.widget.LinearLayout.LayoutParams glp =
                        new android.widget.LinearLayout.LayoutParams((int) (6 * dens), 1);
                gap.setLayoutParams(glp);
                translateResultRow.addView(gap);
            }
            translateResultRow.addView(chip);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * 點選譯文：把譯文送進 App（原文只在 EditText，不必刪 App 內容）。
     */
    public void insertTranslationOption(String text) {
        if (text == null || text.isEmpty() || "…".equals(text) || "失敗".equals(text)) return;
        if ("譯文會出現在這裡".equals(text)) return;
        if ("null".equalsIgnoreCase(text.trim())) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
        translateSource = "";
        translateResult = "";
        translateOptions = new java.util.ArrayList<>();
        cancelPendingTranslate();
        setSourceEditText("", true);
        if (candidateView != null) {
            candidateView.forceClearTranslateDisplay();
            if (rime != null) {
                rime.clearComposition();
                candidateView.update(rime.getContext());
            }
        }
        renderTranslateResults();
        updateTranslateUi();
    }

    /**
     * 翻譯模式：
     * - 原文照常寫入 App（使用者看得到、送得出）
     * - 同時累加 buffer 並請求翻譯
     * - 譯文只在點選後才替換上屏
     * @return true = 已處理（含寫入 App），呼叫端勿再 commit
     */
    /**
     * 翻譯模式：原文只寫入鍵盤內 EditText（不寫 App）。
     * 插入位置尊重 EditText 目前的游標，不強制跳到最後。
     */

    /** 翻譯原文框是否為空（無任何字元） */
    private boolean isTranslateSourceEmpty() {
        if (txtTranslateSource != null && txtTranslateSource.getText() != null) {
            return txtTranslateSource.getText().length() == 0;
        }
        return translateSource == null || translateSource.isEmpty();
    }

    /**
     * 翻譯模式且原文空時：符號 / 數字 / 空白 / 控制字元應直送 App，
     * 中文與英文字母仍進原文 EditText。
     */
    private boolean shouldBypassTranslateToApp(String text) {
        if (!translateMode || !isTranslateSourceEmpty()) return false;
        if (text == null || text.isEmpty()) return true;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            // 中文 → 進原文框
            if ((cp >= 0x3400 && cp <= 0x9FFF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0x20000 && cp <= 0x2FA1F)) {
                return false;
            }
            // 英文字母 → 進原文框（可翻）
            if (Character.isLetter(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        // 純符號 / 數字 / 空白 / 標點 → 直送 App
        return true;
    }

    private boolean handleTranslateCommit(String text) {
        if (!translateMode || text == null || text.isEmpty()) return false;
        // 原文空 + 符號/數字/空白 → 不攔截，讓呼叫端直送 App
        if (shouldBypassTranslateToApp(text)) return false;

        // 空白：直接加在 EditText 游標位置
        if (" ".equals(text) || "\n".equals(text)) {
            if (txtTranslateSource != null) {
                int pos = txtTranslateSource.getSelectionStart();
                if (pos < 0) pos = txtTranslateSource.getText() != null ? txtTranslateSource.getText().length() : 0;
                String cur = txtTranslateSource.getText() != null ? txtTranslateSource.getText().toString() : "";
                String next = cur.substring(0, pos) + text + cur.substring(pos);
                translateSource = next;
                suppressSourceWatch = true;
                txtTranslateSource.setText(next);
                txtTranslateSource.setSelection(pos + text.length());
                suppressSourceWatch = false;
            } else {
                if (translateSource == null) translateSource = "";
                translateSource = translateSource + text;
            }
            updateTranslateUi();
            if (translateSource != null && !translateSource.trim().isEmpty()) {
                scheduleTranslate(translateSource.trim());
            }
            return true;
        }

        // 中文上屏：插在游標位置
        int insertPos = 0;
        if (txtTranslateSource != null) {
            insertPos = txtTranslateSource.getSelectionStart();
            if (insertPos < 0) insertPos = txtTranslateSource.getText() != null ? txtTranslateSource.getText().length() : 0;
        }
        String cur = (txtTranslateSource != null && txtTranslateSource.getText() != null)
                ? txtTranslateSource.getText().toString() : (translateSource == null ? "" : translateSource);
        String next = cur.substring(0, insertPos) + text + cur.substring(insertPos);
        translateSource = next;

        suppressSourceWatch = true;
        if (txtTranslateSource != null) {
            txtTranslateSource.setText(next);
            txtTranslateSource.setSelection(insertPos + text.length());
        }
        suppressSourceWatch = false;

        android.util.Log.i("XiapinIME", "TR edit [" + text + "] pos=" + insertPos + " => [" + translateSource + "]");
        updateTranslateUi();
        scheduleTranslate(translateSource);
        return true;
    }

    private static boolean containsCjkChar(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if ((cp >= 0x4E00 && cp <= 0x9FFF)
                    || (cp >= 0x3400 && cp <= 0x4DBF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    /** 開始組下一字：候選列讓給拼音/字根；譯文列仍保留上次結果 */
    private void pauseTranslateOptionsForComposition() {
        if (!translateMode) return;
        // 不再清掉 translateOptions，譯文列獨立，不搶候選列
        updateTranslateUi();
    }

    @Override
    public View onCreateCandidatesView() {
        // 候選窗已內嵌在 input_view
        return null;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        ensureDeployed();
        // 編輯器可能改過 assoc_user.tsv，開鍵盤時重載
        if (associationDict != null) {
            associationDict.reloadUserFreq();
        }
        if (charUsage != null) {
            charUsage.reload();
        }
        // 每次開鍵盤強制對齊 schema，避免中英狀態漂移
        rime.selectSchema(currentSchema);
        if (keyboardView != null) {
            keyboardLayer = 0;
            keyboardView.setLayer(0);
        }
        updateLang();
        updateTranslateUi();
        if (translateMode && txtTranslateSource != null) {
            txtTranslateSource.requestFocus();
        }
        refresh();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        // 收鍵盤時清組字，避免殘留
        if (rime != null) {
            rime.clearComposition();
            extraRootCandidate = null;
            clearAssociations();
        }
        super.onFinishInputView(finishingInput);
    }

    private void ensureDeployed() {
        if (deployed) return;
        try {
            File shared = new File(getCacheDir(), "rime_shared");
            File user = new File(getFilesDir(), "rime_user");
            if (!shared.exists()) shared.mkdirs();
            if (!user.exists()) user.mkdirs();
            boolean redeployed = AssetDeployer.deploy(this, shared);
            // assets 變動時清掉 user/build，避免殘留壞掉的 prism/table 快取
            if (redeployed) {
                AssetDeployer.deleteRecursive(new File(user, "build"));
                android.util.Log.i("xiapin_ime", "cleared rime_user/build for rebuild");
            }
            rime.startup(shared.getAbsolutePath(), user.getAbsolutePath());
            deployed = true;
            boshiamy = new BoshiamyComment(name -> {
                try {
                    return new java.io.FileInputStream(new java.io.File(shared, name));
                } catch (java.io.FileNotFoundException e) {
                    return null;
                }
            });
            if (candidateView != null) candidateView.setBoshiamy(boshiamy);
            // 關聯字：背景載入 essay + 使用者頻率檔
            associationDict = new AssociationDict();
            associationDict.setUserFile(new java.io.File(getFilesDir(), "assoc_user.tsv"));
            try {
                java.io.InputStream essay = new java.io.FileInputStream(new java.io.File(shared, "essay.txt"));
                associationDict.loadAsync(essay);
            } catch (java.io.FileNotFoundException e) {
                android.util.Log.w("xiapin_ime", "essay.txt missing, no associations");
            }
            charUsage = new CharUsageFreq();
            charUsage.setFile(new java.io.File(getFilesDir(), "char_user.tsv"));
        } catch (Throwable t) {
            android.util.Log.e("xiapin_ime", "ensureDeployed failed", t);
            android.widget.Toast.makeText(
                    this,
                    "蝦拼引擎啟動失敗：" + t.getClass().getSimpleName(),
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    public void setExtraRootCandidate(String text) {
        this.extraRootCandidate = text;
    }

    public String getExtraRootCandidate() {
        return extraRootCandidate;
    }

    /** 字母 ↔ 符號數字層 */
    public void toggleKeyboardLayer() {
        keyboardLayer = (keyboardLayer == 0) ? 1 : 0;
        if (keyboardView != null) {
            keyboardView.setLayer(keyboardLayer);
            updateLang();
        }
    }

    /**
     * 鍵盤把一個字母/符號送進 Rime。
     * @return Rime 是否處理了這個鍵
     */

    /**
     * 翻譯原文 EditText 刪一個字（或選取區間）。
     * @return true 若有刪到內容；false 表示原文為空
     */
    private boolean deleteTranslateSourceChar() {
        if (txtTranslateSource == null) {
            if (translateSource == null || translateSource.isEmpty()) return false;
            int endLen = translateSource.length();
            int cp = Character.codePointBefore(translateSource, endLen);
            int cut = endLen - Character.charCount(cp);
            translateSource = translateSource.substring(0, Math.max(0, cut));
            afterTranslateSourceDeleted();
            return true;
        }
        android.text.Editable e = txtTranslateSource.getText();
        if (e == null || e.length() == 0) {
            translateSource = "";
            return false;
        }
        int start = txtTranslateSource.getSelectionStart();
        int end = txtTranslateSource.getSelectionEnd();
        if (start < 0) start = e.length();
        if (end < 0) end = start;
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        suppressSourceWatch = true;
        try {
            if (start != end) {
                e.delete(start, end);
                txtTranslateSource.setSelection(start);
            } else if (start > 0) {
                int cp = Character.codePointBefore(e, start);
                int cut = Character.charCount(cp);
                e.delete(start - cut, start);
                txtTranslateSource.setSelection(start - cut);
            } else {
                // 游標在開頭且無選取
                return false;
            }
            translateSource = e.toString();
        } finally {
            suppressSourceWatch = false;
        }
        afterTranslateSourceDeleted();
        return true;
    }

    private void afterTranslateSourceDeleted() {
        if (translateSource == null) translateSource = "";
        if (translateSource.isEmpty()) {
            translateResult = "";
            translateOptions = new java.util.ArrayList<>();
            cancelPendingTranslate();
            renderTranslateResults();
            updateTranslateUi();
            if (candidateView != null && rime != null) candidateView.update(rime.getContext());
        } else {
            updateTranslateUi();
            scheduleTranslate(translateSource);
        }
    }

    /** 刪 App 輸入框前一個字元；長按可連續觸發 */
    private boolean deleteAppChar() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        try {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() > 0) {
                int n = Character.charCount(Character.codePointBefore(before, before.length()));
                ic.deleteSurroundingText(n, 0);
                return true;
            }
            sendDownUpKey(android.view.KeyEvent.KEYCODE_DEL);
            return true;
        } catch (Exception e) {
            try {
                sendDownUpKey(android.view.KeyEvent.KEYCODE_DEL);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }


    /**
     * 組字中按 Enter：把目前 preedit 原文（拉丁碼）上屏，並徹底清狀態。
     * 例：打 meta + Enter → 送出 "meta"，不會留下中文候選讓下一個空白誤選。
     */
    private void commitRawCompositionAsLatin() {
        String raw = "";
        try {
            RimeJNI.Context ctx = rime != null ? rime.getContext() : null;
            if (ctx != null && ctx.preedit != null) {
                raw = ctx.preedit.replace(" ", "").replace("'", "");
            }
        } catch (Exception ignored) {}
        if (raw.isEmpty()) {
            raw = currentInputCode();
        }
        hardClearComposition();
        if (!raw.isEmpty()) {
            if (handleTranslateCommit(raw)) {
                // 翻譯模式已寫入 EditText
            } else {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(raw, 1);
            }
            clearAssociations();
        }
        updateLang();
        if (candidateView != null && rime != null) {
            candidateView.update(rime.getContext());
        }
        if (translateMode) updateTranslateUi();
    }

    /** 徹底清空 Rime 組字、殘留 commit、App 層候選/字根 */
    private void hardClearComposition() {
        extraRootCandidate = null;
        displayCandidates = new java.util.ArrayList<>();
        lastCompositionCode = "";
        if (rime != null) {
            try {
                rime.clearComposition();
            } catch (Exception ignored) {}
            drainCommitText();
            try {
                rime.clearComposition();
            } catch (Exception ignored) {}
            drainCommitText();
        }
        clearAssociations();
    }

    public boolean sendKey(int keycode, int mask) {
        // 退格 ⌫
        if (keycode == 0xff08) {
            // 1) 組字中：交給 Rime
            if (hasPreedit()) {
                lastCompositionCode = currentInputCode();
                boolean handled = rime.processKey(0xff08, mask);
                refresh();
                return handled;
            }
            // 2) 翻譯模式：有原文 → 刪 EditText（尊重游標）；無原文 → 刪 App
            if (translateMode) {
                if (deleteTranslateSourceChar()) {
                    return true;
                }
                // 原文空：直接刪 App 輸入框（可連按／長按）
                return deleteAppChar();
            }
            // 3) 一般模式無組字：刪 App（可連按／長按）
            clearAssociations();
            return deleteAppChar();
        }
        // 開始打字母 → 清關聯；翻譯模式先讓出候選列給組字
        if (keycode >= 'a' && keycode <= 'z') {
            clearAssociations();
            pauseTranslateOptionsForComposition();
        }
        // 空白鍵：只走「選候選」或「輸出空格」其中一條，絕不雙重上屏
        if (keycode == ' ') {
            // 注意：無 preedit 時不可只靠 displayCandidates（Enter 送出英文後可能殘留）
            boolean composing = hasPreedit()
                    || (extraRootCandidate != null && !isEnglishMode() && hasPreedit());

            // 翻譯模式 + 完全沒在組字
            if (translateMode && !composing) {
                if (isTranslateSourceEmpty()) {
                    // 原文空：空白直送 App
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(" ", 1);
                } else {
                    handleTranslateCommit(" ");
                }
                return true;
            }

            // 組字中：只上屏「一個」候選，然後 return（不再 processKey，避免 Rime 再送一次）
            if (composing) {
                String pick = null;
                if (extraRootCandidate != null && !isEnglishMode()) {
                    pick = extraRootCandidate;
                } else if (displayCandidates != null && !displayCandidates.isEmpty()) {
                    // 譯文 chip 不應被空白選中
                    if (!(translateMode && translateOptions != null
                            && translateOptions.contains(displayCandidates.get(0)))) {
                        pick = displayCandidates.get(0);
                    }
                }
                if (pick != null) {
                    commitCandidateText(pick);
                    return true;
                }
                // 有 preedit 但我們列表空：讓 Rime 選一次，refresh 後立刻排空殘留 commit
                boolean handled = rime.processKey(' ', mask);
                refresh();
                // 再排一次，避免殘留 commit 被下一個鍵帶出
                drainCommitText();
                return true; // 組字中空白一律吞掉，不額外輸出空白字元
            }

            // 完全閒置：輸出空白字元（不選關聯字）
            clearAssociations();
            commitText(" ");
            if (candidateView != null) candidateView.update(rime.getContext());
            return true;
        }
        // Enter
        if (keycode == 0xff0d || keycode == '\n') {
            // 翻譯模式（無組字）
            if (translateMode && !hasPreedit()) {
                if (!isTranslateSourceEmpty()) {
                    // 有原文：Enter = 送原文
                    sendOriginalText();
                    return true;
                }
                // 原文空：Enter 直送 App
                clearAssociations();
                sendDownUpKey(android.view.KeyEvent.KEYCODE_ENTER);
                return true;
            }
            if (isShowingTranslateOptions() && !hasPreedit()) {
                return true; // 不自動送譯文
            }
            // 組字中按 Enter：上屏「原文碼」（如 meta），並徹底清空 Rime/候選，
            // 避免之後按空白又選到殘留中文候選
            if (hasPreedit() || (extraRootCandidate != null && !isEnglishMode())
                    || (displayCandidates != null && !displayCandidates.isEmpty() && hasPreedit())) {
                commitRawCompositionAsLatin();
                return true;
            }
            // 無組字：把 Enter 送給 App
            clearAssociations();
            sendDownUpKey(android.view.KeyEvent.KEYCODE_ENTER);
            return true;
        }
        // 上屏前保留組字碼（processKey 後 preedit 可能已清空）
        if (hasPreedit()) {
            lastCompositionCode = currentInputCode();
        }
        boolean handled = rime.processKey(keycode, mask);
        refresh();
        return handled;
    }

    /** 中/英：xiapin ↔ xiapin_english */
    public void toggleAscii() {
        boolean toEnglish = "xiapin".equals(currentSchema);
        String target = toEnglish ? "xiapin_english" : "xiapin";
        // 先清組字，再切 schema，避免舊中文候選殘留
        rime.clearComposition();
        extraRootCandidate = null;
        clearAssociations();
        boolean ok = rime.selectSchema(target);
        if (ok) {
            currentSchema = target;
        }
        // 再 select 一次提高成功率（部分 session 首次切換會失敗）
        if (ok) rime.selectSchema(currentSchema);
        if (candidateView != null) candidateView.clear();
        updateLang();
        refresh();
    }

    public boolean isTranslateMode() { return translateMode; }

    public java.util.List<String> getTranslateOptions() { return translateOptions; }

    /** 目前候選列是否正在顯示「譯文」（非組字候選） */
    private boolean isShowingTranslateOptions() {
        if (!translateMode || translateOptions == null || translateOptions.isEmpty()) return false;
        if (displayCandidates == null || displayCandidates.isEmpty()) return false;
        for (String d : displayCandidates) {
            if (d != null && translateOptions.contains(d)) return true;
        }
        return false;
    }

    public boolean isEnglishMode() {
        return "xiapin_english".equals(currentSchema);
    }

    private void updateLang() {
        if (keyboardView != null) {
            // 同步中/英：中文關 Shift，英文才可用大寫
            keyboardView.setEnglishMode(isEnglishMode());
        }
    }

    /**
     * 依「顯示用」index 選字（0 = 字根強制項或 Rime 第 0）。
     */
    public void pickDisplayCandidate(int displayIndex) {
        if (displayIndex < 0) return;
        // 譯文候選：不走數字鍵/空白自動選，只接受候選列 onClick → insertTranslationOption
        if (isShowingTranslateOptions()) {
            return;
        }
        // 關聯字模式（無組字時）
        if (!associations.isEmpty() && !hasPreedit()) {
            if (displayIndex < associations.size()) {
                commitAssociation(associations.get(displayIndex));
            }
            return;
        }
        // 重排後的候選：用文字上屏（避免 Rime index 對不上）
        if (displayCandidates != null && displayIndex < displayCandidates.size()) {
            commitCandidateText(displayCandidates.get(displayIndex));
            return;
        }
        if (extraRootCandidate != null) {
            if (displayIndex == 0) {
                commitRoot(extraRootCandidate);
                return;
            }
            pickCandidate(displayIndex - 1);
            return;
        }
        pickCandidate(displayIndex);
    }

    public void pickCandidate(int index) {
        if (index < 0) return;
        rime.selectCandidate(index);
        extraRootCandidate = null;
        refresh();
    }

    /** 強制上屏字根字並清空 Rime 組字 */
    public void commitRoot(String text) {
        commitCandidateText(text);
    }

    /**
     * 上屏候選文字（重排後用文字提交，不依賴 Rime page index）。
     */
    public void commitCandidateText(String text) {
        if (text == null || text.isEmpty()) return;
        // 先記下輸入碼（清組字前），供拼音頻率學習
        String code = currentInputCode();
        if (charUsage != null) {
            charUsage.recordSelection(code, text);
        }
        boolean inTranslate = handleTranslateCommit(text);
        if (!inTranslate) {
            // 非翻譯：正常上屏
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(text, 1);
            onChineseCommitted(text);
        } else {
            // 翻譯模式：原文進 EditText；頻率已記
        }
        // 清組字 + 排空 Rime 殘留 commit，避免空白後又吐出下一個候選
        if (rime != null) {
            rime.clearComposition();
            drainCommitText();
        }
        extraRootCandidate = null;
        displayCandidates = new java.util.ArrayList<>();
        // 空白選字後先清關聯，避免「再按空白又選到關聯字」的錯覺
        // （關聯會在非翻譯模式由 onChineseCommitted 重新填入）
        if (inTranslate) {
            clearAssociations();
        }
        updateLang();
        if (candidateView != null) candidateView.update(rime.getContext());
        if (inTranslate) updateTranslateUi();
    }

    /** 丟掉 Rime 緩衝的 commit，避免雙重上屏 */
    private void drainCommitText() {
        if (rime == null) return;
        try {
            for (int i = 0; i < 3; i++) {
                String leftover = rime.getCommitText();
                if (leftover == null || leftover.isEmpty()) break;
                android.util.Log.i("XiapinIME", "drain discard: [" + leftover + "]");
            }
        } catch (Exception ignored) {}
    }

    public void setDisplayCandidates(java.util.List<String> ordered) {
        displayCandidates = ordered != null
                ? new java.util.ArrayList<>(ordered)
                : new java.util.ArrayList<>();
    }

    public CharUsageFreq getCharUsage() { return charUsage; }

    /** 清空全部組字（黃色 preedit 右側 ✕） */
    public void clearAllInput() {
        extraRootCandidate = null;
        clearAssociations();
        if (rime != null) rime.clearComposition();
        if (translateMode) {
            translateSource = "";
            translateResult = "";
            translateOptions = new java.util.ArrayList<>();
            cancelPendingTranslate();
            renderTranslateResults();
            updateTranslateUi();
        }
        if (candidateView != null) candidateView.clear();
        refresh();
    }

    public void page(boolean forward) {
        rime.pageCandidate(forward);
        refresh();
    }

    private void commitText(String text) {
        if (TextUtils.isEmpty(text)) return;
        // 翻譯模式：累積原文並翻譯，原文會進 App；譯文需點選
        if (handleTranslateCommit(text)) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(text, 1);
    }

    public void sendDownUpKey(int keycode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
    }

    /** 直接送字元（符號層用，不經 Rime） */
    public void commitRaw(String text) {
        if (text == null || text.isEmpty()) return;
        clearAssociations();
        // 翻譯原文空時：符號/數字會 bypass 進 App；有原文則進 EditText
        commitText(text);
        if (candidateView != null && rime != null) candidateView.update(rime.getContext());
    }

    /** 點選關聯字 */
    public void commitAssociation(String ch) {
        if (ch == null || ch.isEmpty()) return;
        // 先記使用頻率（用點選前的 prefix），再上屏
        String prefix = commitContext == null ? "" : commitContext;
        if (associationDict != null) {
            associationDict.recordUse(prefix, ch);
        }
        commitText(ch);
        onChineseCommitted(ch);
        if (candidateView != null) candidateView.update(rime.getContext());
        updateLang();
    }

    public java.util.List<String> getAssociations() {
        return associations;
    }

    public boolean hasAssociations() {
        return associations != null && !associations.isEmpty();
    }

    private void clearAssociations() {
        associations = new java.util.ArrayList<>();
        commitContext = "";
    }

    /** 上屏中文後更新關聯候選 */
    private void onChineseCommitted(String text) {
        if (text == null || text.isEmpty() || isEnglishMode()) {
            clearAssociations();
            return;
        }
        // 頻率已在 commitCandidateText 用輸入碼記錄；此處僅補「Rime 直接 commit」路徑
        // （無 preedit code 時仍記全域）
        // 抽出 CJK 接到 context
        StringBuilder sb = new StringBuilder(commitContext == null ? "" : commitContext);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            if ((cp >= 0x3400 && cp <= 0x9FFF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0x20000 && cp <= 0x2FA1F)) {
                sb.appendCodePoint(cp);
            } else {
                // 非中文打斷
                sb.setLength(0);
            }
            i += n;
        }
        // 只留尾 2 字
        String ctx = sb.toString();
        if (ctx.length() > 2) ctx = ctx.substring(ctx.length() - 2);
        commitContext = ctx;
        if (associationDict != null && associationDict.isReady()) {
            associations = associationDict.lookup(commitContext);
        } else {
            associations = new java.util.ArrayList<>();
        }
    }

    private boolean hasPreedit() {
        RimeJNI.Context ctx = rime != null ? rime.getContext() : null;
        return ctx != null && ctx.preedit != null && !ctx.preedit.isEmpty();
    }

    /** 目前組字碼（小寫、無空格），供頻率學習 / 排序 */
    private String currentInputCode() {
        try {
            RimeJNI.Context ctx = rime != null ? rime.getContext() : null;
            if (ctx != null && ctx.preedit != null) {
                return CharUsageFreq.normalizeCode(ctx.preedit);
            }
        } catch (Exception ignored) {}
        return "";
    }

    void refresh() {
        String committed = rime.getCommitText();
        if (committed != null && !committed.isEmpty()) {
            // Rime 直接上屏：用上屏前記住的碼做頻率
            if (charUsage != null) {
                charUsage.recordSelection(lastCompositionCode, committed);
            }
            commitText(committed);
            // 翻譯模式不跑關聯字，避免蓋掉譯文候選
            if (!translateMode) {
                onChineseCommitted(committed);
            }
            lastCompositionCode = "";
        }
        RimeJNI.Context ctx = rime.getContext();
        if (candidateView != null) {
            candidateView.update(ctx);
        }
        updateLang();
        if (translateMode) updateTranslateUi();
    }

    public RimeJNI getRime() { return rime; }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 數字鍵 1–9：選顯示第 N 個候選
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            int n = keyCode - KeyEvent.KEYCODE_1; // 0-based
            if (hasAssociations() || extraRootCandidate != null || hasCandidates()) {
                pickDisplayCandidate(n);
                return true;
            }
        }
        int rimeKey = 0;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            rimeKey = 'a' + (keyCode - KeyEvent.KEYCODE_A);
        } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
            rimeKey = ' ';
        } else if (keyCode == KeyEvent.KEYCODE_COMMA) {
            rimeKey = ',';
        } else if (keyCode == KeyEvent.KEYCODE_PERIOD) {
            rimeKey = '.';
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            rimeKey = 0xff08;
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            rimeKey = 0xff0d;
        }
        if (rimeKey != 0) {
            sendKey(rimeKey, 0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean hasCandidates() {
        if (candidateView == null) return false;
        return candidateView.getDisplayCount() > 0;
    }
}
