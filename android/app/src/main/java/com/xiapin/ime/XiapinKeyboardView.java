package com.xiapin.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.util.SparseArray;

/**
 * 軟鍵盤：
 *  - 中文：鍵帽英文 + 大千注音（自繪）
 *  - 英文：⇧ 三態
 *
 * codes：-1/-2 中↔英↔符、-3 Shift、-4 Enter、-5 Backspace
 */
public class XiapinKeyboardView extends KeyboardView implements KeyboardView.OnKeyboardActionListener {

    private XiapinIME service;
    private Keyboard lettersZhKb;
    private Keyboard lettersEnKb;
    private Keyboard symbolsKb;
    private int layer = 0;

    /** 0 全小寫 / 1 首字大寫 / 2 全大寫 */
    private int shiftState = 0;
    private boolean englishMode = false;

    private final Paint shiftFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shiftTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zhuyinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Typeface zhuyinTypeface;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shiftFillRect = new RectF();

    private static final int SHIFT_CAPS_FILL = 0xFF5B9BFF;
    private static final int SHIFT_TEXT_ON_FILL = 0xFFFFFFFF;
    private static final int ZHUYIN_COLOR = 0xFF9AA0A6;

    /** 大千注音副標（依 key code） */
    private static final SparseArray<String> ZHUYIN = new SparseArray<>();
    static {
        // 字母列
        ZHUYIN.put('q', "ㄆ"); ZHUYIN.put('w', "ㄊ"); ZHUYIN.put('e', "ㄍ");
        ZHUYIN.put('r', "ㄐ"); ZHUYIN.put('t', "ㄔ"); ZHUYIN.put('y', "ㄗ");
        ZHUYIN.put('u', "ㄧ"); ZHUYIN.put('i', "ㄛ"); ZHUYIN.put('o', "ㄟ");
        ZHUYIN.put('p', "ㄣ");
        ZHUYIN.put('a', "ㄇ"); ZHUYIN.put('s', "ㄋ"); ZHUYIN.put('d', "ㄎ");
        ZHUYIN.put('f', "ㄑ"); ZHUYIN.put('g', "ㄕ"); ZHUYIN.put('h', "ㄘ");
        ZHUYIN.put('j', "ㄨ"); ZHUYIN.put('k', "ㄜ"); ZHUYIN.put('l', "ㄠ");
        ZHUYIN.put('z', "ㄈ"); ZHUYIN.put('x', "ㄌ"); ZHUYIN.put('c', "ㄏ");
        ZHUYIN.put('v', "ㄒ"); ZHUYIN.put('b', "ㄖ"); ZHUYIN.put('n', "ㄙ");
        ZHUYIN.put('m', "ㄩ");
        ZHUYIN.put(',', "ㄝ"); ZHUYIN.put('.', "ㄡ");
        // 數字／聲調（中文主鍵盤數字列）
        ZHUYIN.put('1', "ㄅ"); ZHUYIN.put('2', "ㄉ"); ZHUYIN.put('3', "ˇ");
        ZHUYIN.put('4', "ˋ"); ZHUYIN.put('5', "ㄓ"); ZHUYIN.put('6', "ˊ");
        ZHUYIN.put('7', "˙"); ZHUYIN.put('8', "ㄚ"); ZHUYIN.put('9', "ㄞ");
        ZHUYIN.put('0', "ㄢ");
        ZHUYIN.put('-', "ㄦ"); ZHUYIN.put(';', "ㄤ"); ZHUYIN.put('/', "ㄥ");
    }

    public XiapinKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public XiapinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        lettersZhKb = new Keyboard(getContext(), R.xml.qwerty_zh);
        lettersEnKb = new Keyboard(getContext(), R.xml.qwerty);
        symbolsKb = new Keyboard(getContext(), R.xml.symbols);
        setKeyboard(lettersZhKb);
        prepareZhuyinKeyLabels();
        setOnKeyboardActionListener(this);
        setPreviewEnabled(false);
        setProximityCorrectionEnabled(true);
        shiftFillPaint.setStyle(Paint.Style.FILL);
        shiftTextPaint.setColor(SHIFT_TEXT_ON_FILL);
        shiftTextPaint.setTextAlign(Paint.Align.CENTER);
        shiftTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        shiftTextPaint.setFakeBoldText(true);
        zhuyinPaint.setColor(ZHUYIN_COLOR);
        zhuyinPaint.setTextAlign(Paint.Align.CENTER);
        zhuyinPaint.setAntiAlias(true);
        zhuyinTypeface = loadZhuyinTypeface();
        if (zhuyinTypeface != null) {
            zhuyinPaint.setTypeface(zhuyinTypeface);
        } else {
            zhuyinPaint.setTypeface(Typeface.DEFAULT);
        }
        letterPaint.setColor(0xFFE8EAED);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTypeface(Typeface.DEFAULT);
        letterPaint.setAntiAlias(true);
    }

    public void setService(XiapinIME svc) { this.service = svc; }

    /** 確保 5 列（含數字）完整量測，不被系統裁掉頂列 */
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Keyboard kb = getKeyboard();
        if (kb == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = android.view.View.MeasureSpec.getSize(widthMeasureSpec);
        int height = kb.getHeight() + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, Math.max(height, getSuggestedMinimumHeight()));
    }

    private Keyboard currentLettersKb() {
        return englishMode ? lettersEnKb : lettersZhKb;
    }

    public void setLayer(int layer) {
        this.layer = layer;
        if (layer != 0) {
            setKeyboard(symbolsKb);
        } else {
            setKeyboard(currentLettersKb());
            if (englishMode) applyShiftVisual();
        }
        updateModeButton();
        prepareZhuyinKeyLabels();
        invalidateAllKeys();
    }

    public void setEnglishMode(boolean english) {
        this.englishMode = english;
        if (!english) {
            shiftState = 0;
            setShifted(false);
        }
        if (layer == 0) {
            setKeyboard(currentLettersKb());
            if (english) applyShiftVisual();
        }
        updateLangButton(english);
        prepareZhuyinKeyLabels();
        invalidateAllKeys();
        invalidate();
    }

    public void updateLangButton(boolean english) {
        this.englishMode = english;
        updateModeButton();
    }

    /** 模式鍵標籤：中 / 英 / 符 */
    public void updateModeButton() {
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        String label;
        if (layer != 0) label = "符";
        else if (englishMode) label = "英";
        else label = "中";
        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes != null && key.codes[0] == -1) {
                key.label = label;
                key.icon = null;
                break;
            }
        }
        invalidateAllKeys();
    }


    /** 中文鍵：label 只放主字（英/數），注音由 onDraw 右上角小標 */
    private void prepareZhuyinKeyLabels() {
        if (englishMode) return;
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            if (ZHUYIN.get(code) == null) continue;
            if (code >= 'a' && code <= 'z') {
                key.label = String.valueOf((char) code);
            } else if (code >= '0' && code <= '9') {
                key.label = String.valueOf((char) code);
            } else if (code == ',') {
                key.label = ",";
            } else if (code == '.') {
                key.label = ".";
            } else if (code == ';') {
                key.label = ";";
            } else if (code == '/') {
                key.label = "/";
            } else if (code == '-') {
                key.label = "-";
            } else {
                continue;
            }
            key.icon = null;
        }
    }


    private boolean isUpper() {
        return englishMode && (shiftState == 1 || shiftState == 2);
    }

    private void cycleShift() {
        if (!englishMode) return;
        shiftState = (shiftState + 1) % 3;
        applyShiftVisual();
        invalidateAllKeys();
        invalidate();
    }

    private void applyShiftVisual() {
        if (!englishMode || lettersEnKb == null) return;
        boolean upper = isUpper();
        setShifted(upper);
        for (Keyboard.Key key : lettersEnKb.getKeys()) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            if (code == -3) {
                if (shiftState == 0) {
                    key.label = "⇧";
                } else if (shiftState == 1) {
                    key.label = "⇪";
                } else {
                    key.label = " ";
                }
                key.icon = null;
                continue;
            }
            if (code >= 'a' && code <= 'z') {
                char ch = upper ? Character.toUpperCase((char) code) : (char) code;
                key.label = String.valueOf(ch);
            }
        }
    }

    private void consumeOneShotShift() {
        if (shiftState == 1) {
            shiftState = 0;
            applyShiftVisual();
            invalidateAllKeys();
            invalidate();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 英文 Shift 藍底
        if (englishMode && layer == 0 && shiftState == 2
                && lettersEnKb != null && getKeyboard() == lettersEnKb) {
            drawShiftCaps(canvas);
        }

        // 中文字母層才畫注音；符號層不要出現注音
        if (!englishMode && layer == 0) {
            drawZhuyinHints(canvas);
        }
    }

    private void drawShiftCaps(Canvas canvas) {
        float dens = getResources().getDisplayMetrics().density;
        float inset = 3f * dens;
        float radius = 8f * dens;
        int padL = getPaddingLeft();
        int padT = getPaddingTop();
        for (Keyboard.Key key : lettersEnKb.getKeys()) {
            if (key.codes == null || key.codes.length == 0 || key.codes[0] != -3) continue;
            float l = key.x + padL + inset;
            float t = key.y + padT + inset;
            float r = key.x + padL + key.width - inset;
            float b = key.y + padT + key.height - inset;
            shiftFillRect.set(l, t, r, b);
            shiftFillPaint.setColor(SHIFT_CAPS_FILL);
            canvas.drawRoundRect(shiftFillRect, radius, radius, shiftFillPaint);
            shiftTextPaint.setTextSize(22f * dens);
            shiftTextPaint.setColor(SHIFT_TEXT_ON_FILL);
            Paint.FontMetrics fm = shiftTextPaint.getFontMetrics();
            float cx = (l + r) / 2f;
            float cy = (t + b) / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("⇧", cx, cy, shiftTextPaint);
            break;
        }
    }

    /**
     * 只畫注音小標（右上角）；主字交給 super 的 key.label，避免雙重繪製裁切。
     */
    private void drawZhuyinHints(Canvas canvas) {
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        float dens = getResources().getDisplayMetrics().density;
        zhuyinPaint.setTextSize(13f * dens);
        zhuyinPaint.setColor(0xFFB8C0C8);
        zhuyinPaint.setTextAlign(Paint.Align.RIGHT);
        zhuyinPaint.setFakeBoldText(false);
        if (zhuyinTypeface != null) zhuyinPaint.setTypeface(zhuyinTypeface);
        int padL = getPaddingLeft();
        int padT = getPaddingTop();
        Paint.FontMetrics zfm = zhuyinPaint.getFontMetrics();
        float inset = 6f * dens;

        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            String zy = ZHUYIN.get(code);
            if (zy == null) continue;
            float left = key.x + padL;
            float top = key.y + padT;
            float zx = left + key.width - inset;
            // 右上角：baseline 在鍵頂下約 14dp
            float zyBase = top + 14f * dens;
            // 安全：不超出鍵底
            if (zyBase + zfm.descent > top + key.height - 2f * dens) {
                zyBase = top + key.height - 2f * dens - zfm.descent;
            }
            canvas.drawText(zy, zx, zyBase, zhuyinPaint);
        }
        zhuyinPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        if (service == null) return;
        switch (primaryCode) {
            case -1:
            case -2:
                // 中 → 英 → 符 → 中
                service.cycleInputMode();
                return;
            case -3:
                cycleShift();
                return;
            case -5: {
                service.sendKey(0xff08, 0);
                return;
            }
            case -4: {
                boolean handled = service.sendKey(0xff0d, 0);
                if (!handled) service.sendDownUpKey(android.view.KeyEvent.KEYCODE_ENTER);
                return;
            }
            default:
                if (layer == 1) {
                    if (primaryCode > 0) {
                        if (!englishMode && isZhuyinCodeKey(primaryCode)) {
                            service.sendKey(primaryCode, 0);
                        } else {
                            service.commitRaw(new String(Character.toChars(primaryCode)));
                        }
                    }
                    return;
                }
                if (englishMode && primaryCode >= 'a' && primaryCode <= 'z' && isUpper()) {
                    service.sendKey(Character.toUpperCase(primaryCode), 0);
                    consumeOneShotShift();
                    return;
                }
                service.sendKey(primaryCode, 0);
        }
    }

    @Override public void onPress(int primaryCode) {}
    @Override public void onRelease(int primaryCode) {}
    @Override public void onText(CharSequence text) {
        if (service != null && text != null) service.commitRaw(text.toString());
    }
    @Override public void swipeLeft() {
        if (service != null) service.page(true);
    }
    @Override public void swipeRight() {
        if (service != null) service.page(false);
    }
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}

    /** Noto CJK 才有完整注音字形 */
    private static Typeface loadZhuyinTypeface() {
        String[] paths = {
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/system_ext/fonts/NotoSansCJK-Regular.ttc",
        };
        for (String path : paths) {
            try {
                java.io.File f = new java.io.File(path);
                if (!f.exists()) continue;
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    for (int i = 0; i < 5; i++) {
                        try {
                            Typeface.Builder b = new Typeface.Builder(path);
                            b.setTtcIndex(i);
                            Typeface tf = b.build();
                            if (tf != null) return tf;
                        } catch (Throwable ignored) {}
                    }
                }
                Typeface tf = Typeface.createFromFile(f);
                if (tf != null) return tf;
            } catch (Throwable ignored) {}
        }
        return Typeface.DEFAULT;
    }

    private static boolean isZhuyinCodeKey(int code) {
        return (code >= '0' && code <= '9')
                || code == ',' || code == '.' || code == ';'
                || code == '/' || code == '-';
    }
}
