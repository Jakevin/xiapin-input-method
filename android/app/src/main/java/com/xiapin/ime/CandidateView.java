package com.xiapin.ime;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 候選窗：固定高度，避免有/無候選時畫面跳動。
 * - 有候選：顯示候選字
 * - 無候選但有關聯字：顯示關聯（台→北中灣）
 * - 都無：顯示數字鍵 1–0
 */
public class CandidateView extends FrameLayout {

    private static final int COLOR_BG = 0xFF141414;
    private static final int COLOR_ROOT_BG = 0xFF3D2E00;
    private static final int COLOR_ROOT_TEXT = 0xFFFFCC00;
    private static final int COLOR_CAND_TEXT = 0xFFFFFFFF;
    private static final int COLOR_NUM = 0xFF7A8088;
    private static final int COLOR_DIVIDER = 0xFF2A2A2A;
    private static final int COLOR_DIGIT_BG = 0xFF3C3C3E;
    private static final int COLOR_DIGIT_TEXT = 0xFFF2F2F2;

    private XiapinIME service;
    private BoshiamyComment boshiamy;
    private TextView preeditText;
    private TextView btnClearAll;
    private LinearLayout candidateRow;
    private String extraRootText = null;
    private int displayCount = 0;
    /** true = 目前顯示數字列（無候選） */
    private boolean showingDigits = false;
    /** 翻譯候選模式（可點選譯文） */
    private boolean showingTranslate = false;

    public CandidateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CandidateView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.candidate_view, this, true);
        preeditText = findViewById(R.id.preedit);
        btnClearAll = findViewById(R.id.btn_clear_all);
        candidateRow = findViewById(R.id.candidate_row);
        setBackgroundColor(COLOR_BG);
        setVisibility(View.VISIBLE);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                if (service != null) service.clearAllInput();
            });
        }
        // 初始：無候選 → 數字列
        showDigitRow();
    }

    public void setService(XiapinIME svc) { this.service = svc; }
    public void setBoshiamy(BoshiamyComment b) { this.boshiamy = b; }
    public int getDisplayCount() { return displayCount; }

    public void clear() {
        if (preeditText != null) preeditText.setText("");
        extraRootText = null;
        displayCount = 0;
        showingTranslate = false;
        if (btnClearAll != null) btnClearAll.setVisibility(View.INVISIBLE);
        setVisibility(View.VISIBLE);
        if (service != null) {
            service.setExtraRootCandidate(null);
            service.setDisplayCandidates(null);
        }
        showDigitRow();
    }

    public void update(RimeJNI.Context ctx) {
        if (ctx == null) {
            clear();
            return;
        }

        String preedit = ctx.preedit == null ? "" : ctx.preedit;
        boolean hasPreedit = !preedit.isEmpty();
        preeditText.setText(preedit);
        if (btnClearAll != null) {
            btnClearAll.setVisibility(hasPreedit ? View.VISIBLE : View.INVISIBLE);
        }

        List<RimeJNI.Candidate> cands = ctx.candidates;
        if (cands == null) cands = new ArrayList<>();

        boolean englishMode = service != null && service.isEnglishMode();

        if (englishMode) {
            List<RimeJNI.Candidate> filtered = new ArrayList<>();
            for (RimeJNI.Candidate c : cands) {
                if (c != null && c.text != null && !containsCjk(c.text)) {
                    filtered.add(c);
                }
            }
            cands = filtered;
            extraRootText = null;
            if (service != null) service.setExtraRootCandidate(null);
        } else {
            // 過濾 ExtA/ExtB/符號等亂碼候補，只留常用 CJK BMP（U+4E00–9FFF）
            List<RimeJNI.Candidate> filteredZh = new ArrayList<>();
            for (RimeJNI.Candidate c : cands) {
                if (c != null && isDisplayableCandidate(c.text)) {
                    filteredZh.add(c);
                }
            }
            cands = filteredZh;
            extraRootText = null;
            String norm = preedit.replace(" ", "").replace("'", "");
            // 所有 1–4 碼嘸蝦米字根（一鍵 a–z 與 ma/ay/pns…）一律置頂
            if (boshiamy != null && isRootCode(norm)) {
                String root = boshiamy.rootForCode(norm);
                if (root != null && isDisplayableCandidate(root)) {
                    extraRootText = root;
                }
            }
            if (service != null) service.setExtraRootCandidate(extraRootText);
        }

        boolean rootPinned = extraRootText != null;
        boolean rootAlreadyInList = rootPinned && listContainsText(cands, extraRootText);
        int n = cands.size() + (rootPinned && !rootAlreadyInList ? 1 : 0);
        displayCount = n;
        setVisibility(View.VISIBLE);

        // 無組字候選 → 關聯字 / 數字列（譯文在獨立列）
        if (n == 0) {
            showingTranslate = false;
            if (!englishMode && service != null && !service.isTranslateMode()
                    && service.hasAssociations()) {
                showAssociationRow(service.getAssociations());
            } else {
                showDigitRow();
            }
            return;
        }
        showingTranslate = false;

        showCandidateRow(preedit, cands, englishMode, n);
    }

    /** 關聯字列：送出後提示下一字（灣 北 中…） */
    private void showAssociationRow(java.util.List<String> assoc) {
        showingDigits = false;
        if (candidateRow == null || assoc == null || assoc.isEmpty()) {
            showDigitRow();
            return;
        }
        candidateRow.removeAllViews();
        android.view.ViewGroup.LayoutParams rowLp = candidateRow.getLayoutParams();
        if (rowLp != null) {
            rowLp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            candidateRow.setLayoutParams(rowLp);
        }
        candidateRow.setWeightSum(0f);
        displayCount = assoc.size();

        int padH = dp(14);
        int padV = dp(6);
        for (int i = 0; i < assoc.size(); i++) {
            final String ch = assoc.get(i);
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(padH, padV, padH, padV);
            cell.setClickable(true);
            cell.setFocusable(true);

            // 軟鍵盤無數字選字，不顯示 1. 2. 編號
            TextView body = new TextView(getContext());
            body.setText(ch);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            body.setTextColor(COLOR_CAND_TEXT);
            body.setTypeface(Typeface.DEFAULT_BOLD);
            body.setMaxLines(1);
            body.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1A2A3A);
            bg.setCornerRadius(dp(8));
            cell.setBackground(bg);

            cell.setOnClickListener(v -> {
                if (service != null) service.commitAssociation(ch);
            });

            cell.addView(body);

            if (i > 0) {
                View div = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(22));
                lp.gravity = Gravity.CENTER_VERTICAL;
                div.setLayoutParams(lp);
                div.setBackgroundColor(COLOR_DIVIDER);
                candidateRow.addView(div);
            }
            candidateRow.addView(cell);
        }
    }

    /** 顯示可點選譯文（Google 風格：多個譯文可選） */
    public void showTranslateOptions(java.util.List<String> options) {
        showingDigits = false;
        showingTranslate = true;
        if (candidateRow == null) return;
        if (options == null || options.isEmpty()) {
            showDigitRow();
            return;
        }
        candidateRow.removeAllViews();
        android.view.ViewGroup.LayoutParams rowLp = candidateRow.getLayoutParams();
        if (rowLp != null) {
            rowLp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            candidateRow.setLayoutParams(rowLp);
        }
        candidateRow.setWeightSum(0f);
        displayCount = options.size();
        if (service != null) service.setDisplayCandidates(new ArrayList<>(options));

        int padH = dp(12);
        int padV = dp(6);
        for (int i = 0; i < options.size(); i++) {
            final String text = options.get(i);
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(padH, padV, padH, padV);
            cell.setClickable(true);
            cell.setFocusable(true);

            TextView body = new TextView(getContext());
            body.setText(text);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            body.setTextColor(0xFFE8F0FE);
            body.setTypeface(Typeface.DEFAULT_BOLD);
            body.setMaxLines(2);
            body.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1A3A5C); // 藍色系譯文候選
            bg.setCornerRadius(dp(8));
            cell.setBackground(bg);

            cell.setOnClickListener(v -> {
                if (service != null) service.insertTranslationOption(text);
            });
            cell.addView(body);

            if (i > 0) {
                View div = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(22));
                lp.gravity = Gravity.CENTER_VERTICAL;
                div.setLayoutParams(lp);
                div.setBackgroundColor(COLOR_DIVIDER);
                candidateRow.addView(div);
            }
            candidateRow.addView(cell);
        }
    }

    public void clearTranslateOptions() {
        showingTranslate = false;
        if (service != null && service.isTranslateMode()
                && service.getTranslateOptions() != null
                && !service.getTranslateOptions().isEmpty()) {
            // 仍由 service 持有，refresh 時會再畫
            return;
        }
        if (!showingDigits) showDigitRow();
    }

    /** 開始組下一字時：立刻清掉譯文候選列（保留 service 原文） */
    public void forceClearTranslateDisplay() {
        showingTranslate = false;
        displayCount = 0;
        if (service != null) service.setDisplayCandidates(null);
        showDigitRow();
    }

    /** 無候選時保持空白（數字已在鍵盤上，不再重覆 1–0 列） */
    private void showDigitRow() {
        showingDigits = false;
        showingTranslate = false;
        if (candidateRow == null) return;
        candidateRow.removeAllViews();
        android.view.ViewGroup.LayoutParams rowLp = candidateRow.getLayoutParams();
        if (rowLp != null) {
            rowLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            candidateRow.setLayoutParams(rowLp);
        }
        candidateRow.setWeightSum(0f);
    }

    private void showCandidateRow(String preedit, List<RimeJNI.Candidate> cands,
                                  boolean englishMode, int n) {
        showingDigits = false;
        if (candidateRow == null) return;
        candidateRow.removeAllViews();
        android.view.ViewGroup.LayoutParams rowLp = candidateRow.getLayoutParams();
        if (rowLp != null) {
            rowLp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            candidateRow.setLayoutParams(rowLp);
        }
        candidateRow.setWeightSum(0f);

        // 依個人使用頻率重排（拼音碼層 + 全域）；穩定排序保留原相對序
        List<RimeJNI.Candidate> ordered = new ArrayList<>(cands);
        if (!englishMode && service != null && service.getCharUsage() != null) {
            final CharUsageFreq freq = service.getCharUsage();
            final String code = CharUsageFreq.normalizeCode(preedit);
            Collections.sort(ordered, new Comparator<RimeJNI.Candidate>() {
                @Override
                public int compare(RimeJNI.Candidate a, RimeJNI.Candidate b) {
                    String ta = a != null ? a.text : null;
                    String tb = b != null ? b.text : null;
                    int sa = freq.scoreForCandidate(ta, code);
                    int sb = freq.scoreForCandidate(tb, code);
                    return Integer.compare(sb, sa);
                }
            });
        }

        // 顯示順序（含字根強制項）給空白鍵 / 數字選字
        List<String> display = new ArrayList<>();
        if (extraRootText != null) display.add(extraRootText);
        for (RimeJNI.Candidate c : ordered) {
            if (c != null && c.text != null) {
                if (extraRootText != null && extraRootText.equals(c.text)) continue;
                display.add(c.text);
            }
        }
        if (service != null) service.setDisplayCandidates(display);
        displayCount = display.size();

        int padH = dp(12);
        int padV = dp(6);
        int shown = 0;

        // 字根強制項置頂
        if (extraRootText != null) {
            addCandCell(preedit, extraRootText, true, englishMode, shown > 0);
            shown++;
        }
        for (RimeJNI.Candidate c : ordered) {
            if (c == null || c.text == null) continue;
            if (extraRootText != null && extraRootText.equals(c.text)) continue;
            addCandCell(preedit, c.text, false, englishMode, shown > 0);
            shown++;
        }
    }

    private void addCandCell(String preedit, final String text, boolean isRoot,
                             boolean englishMode, boolean withDivider) {
        int padH = dp(12);
        int padV = dp(6);
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(padH, padV, padH, padV);
        cell.setClickable(true);
        cell.setFocusable(true);

        TextView body = new TextView(getContext());
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        body.setMaxLines(1);

        if (isRoot) {
            String codeHint = preedit == null ? "" : preedit.replace(" ", "").replace("'", "");
            body.setText(text + (codeHint.isEmpty() ? "" : "  " + codeHint));
            body.setTextColor(COLOR_ROOT_TEXT);
            body.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_ROOT_BG);
            bg.setCornerRadius(dp(8));
            cell.setBackground(bg);
        } else {
            String comment = null;
            if (!englishMode && boshiamy != null) {
                comment = boshiamy.commentFor(text);
            }
            if (comment != null && !comment.isEmpty()) {
                body.setText(text + "  " + comment);
            } else {
                body.setText(text);
            }
            body.setTextColor(COLOR_CAND_TEXT);
            body.setTypeface(Typeface.DEFAULT);
            cell.setBackground(null);
        }
        cell.setOnClickListener(v -> {
            if (service != null) service.commitCandidateText(text);
        });
        cell.addView(body);

        if (withDivider) {
            View div = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(22));
            lp.gravity = Gravity.CENTER_VERTICAL;
            div.setLayoutParams(lp);
            div.setBackgroundColor(COLOR_DIVIDER);
            candidateRow.addView(div);
        }
        candidateRow.addView(cell);
    }

    private static boolean isRootCode(String s) {
        if (s == null || s.isEmpty() || s.length() > 4) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    /**
     * 候選顯示過濾：只接受常用漢字 BMP（U+4E00–U+9FFF）與空白。
     * 丟掉 ExtA/ExtB、相容表意、符號、拉丁混雜等易變 □／亂碼的項目。
     */
    static boolean isDisplayableCandidate(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x4E00 && cp <= 0x9FFF) continue;
            if (cp == ' ') continue;
            return false;
        }
        return true;
    }

    private static boolean containsCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if ((cp >= 0x3400 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0x20000 && cp <= 0x2FA1F)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean listContainsText(List<RimeJNI.Candidate> cands, String text) {
        if (text == null) return false;
        for (RimeJNI.Candidate c : cands) {
            if (c != null && text.equals(c.text)) return true;
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
