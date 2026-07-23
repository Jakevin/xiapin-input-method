package com.xiapin.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.SparseArray;

/**
 * 軟鍵盤（分開、不混打）：
 *  - 拼：蝦拼字根
 *  - 注：注音大千（鍵帽注音標）
 *  - 英：英文 + ⇧
 *  - 符：符號層
 *
 * codes：-1/-2 模式輪流、-3 Shift、-4 Enter、-5 Backspace
 */
public class XiapinKeyboardView extends KeyboardView implements KeyboardView.OnKeyboardActionListener {

    private XiapinIME service;
    private Keyboard lettersRootKb;
    private Keyboard lettersZhuyinKb;
    private Keyboard lettersEnKb;
    private Keyboard symbolsKb;
    private Keyboard symbols2Kb;
    /** 符號層頁：0=半形常用 1=中文/更多 */
    private int symbolPage = 0;

    /** 拼=0 注=1 英=2 符=3 */
    public static final int MODE_ROOT = 0;
    public static final int MODE_ZHUYIN = 1;
    public static final int MODE_ENGLISH = 2;
    public static final int MODE_SYMBOLS = 3;
    private int inputMode = MODE_ROOT;
    /** 進符號前的字母模式，返回時還原 */
    private int lastLettersMode = MODE_ROOT;

    /** 0 全小寫 / 1 首字大寫 / 2 全大寫 */
    private int shiftState = 0;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private int pressCode = 0;
    private boolean longPressFired = false;
    private static final long LONG_PRESS_MS = 420L;
    private final Runnable longPressRunnable = this::onZhuyinLongPress;

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
        lettersRootKb = new Keyboard(getContext(), R.xml.qwerty_root);
        lettersZhuyinKb = new Keyboard(getContext(), R.xml.qwerty_zh);
        lettersEnKb = new Keyboard(getContext(), R.xml.qwerty);
        symbolsKb = new Keyboard(getContext(), R.xml.symbols);
        symbols2Kb = new Keyboard(getContext(), R.xml.symbols2);
        setKeyboard(lettersRootKb);
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

    public int getInputMode() { return inputMode; }
    public int getLastLettersMode() { return lastLettersMode; }

    public boolean isEnglishMode() { return inputMode == MODE_ENGLISH; }
    public boolean isZhuyinMode() { return inputMode == MODE_ZHUYIN; }
    public boolean isRootMode() { return inputMode == MODE_ROOT; }
    public boolean isSymbolsMode() { return inputMode == MODE_SYMBOLS; }

    /** 套用輸入模式並切鍵盤 */
    public void setInputMode(int mode) {
        if (mode == MODE_SYMBOLS && inputMode != MODE_SYMBOLS) {
            lastLettersMode = inputMode;
            if (lastLettersMode == MODE_SYMBOLS) lastLettersMode = MODE_ROOT;
        }
        this.inputMode = mode;
        if (mode != MODE_ENGLISH) {
            shiftState = 0;
            setShifted(false);
        }
        switch (mode) {
            case MODE_SYMBOLS:
                setKeyboard(symbolPage == 1 ? symbols2Kb : symbolsKb);
                break;
            case MODE_ENGLISH:
                setKeyboard(lettersEnKb);
                applyShiftVisual();
                break;
            case MODE_ZHUYIN:
                setKeyboard(lettersZhuyinKb);
                prepareZhuyinKeyLabels();
                break;
            case MODE_ROOT:
            default:
                setKeyboard(lettersRootKb);
                break;
        }
        updateModeButton();
        invalidateAllKeys();
        invalidate();
    }

    /** 相容舊 API */
    public void setLayer(int layer) {
        if (layer != 0) setInputMode(MODE_SYMBOLS);
        else if (inputMode == MODE_SYMBOLS) setInputMode(MODE_ROOT);
    }

    public void setEnglishMode(boolean english) {
        if (english) setInputMode(MODE_ENGLISH);
        else if (inputMode == MODE_ENGLISH) setInputMode(MODE_ROOT);
    }

    /** 符號層兩頁切換 */
    public void toggleSymbolPage() {
        if (inputMode != MODE_SYMBOLS) return;
        symbolPage = (symbolPage == 0) ? 1 : 0;
        setKeyboard(symbolPage == 1 ? symbols2Kb : symbolsKb);
        invalidateAllKeys();
        invalidate();
    }

    public void updateLangButton(boolean english) {
        updateModeButton();
    }

    /** 模式鍵：拼 / 注 / 英 / 符 */
    public void updateModeButton() {
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        String label;
        switch (inputMode) {
            case MODE_ZHUYIN: label = "注"; break;
            case MODE_ENGLISH: label = "英"; break;
            case MODE_SYMBOLS:
                // 符號層用「返回」，不更新模式鍵
                return;
            case MODE_ROOT:
            default: label = "蝦"; break;
        }
        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes != null && key.codes[0] == -1) {
                key.label = label;
                key.icon = null;
                break;
            }
        }
        invalidateAllKeys();
    }

    /**
     * 注音鍵：清空系統 label，改由 onDraw 自繪
     * 主標=大注音、副標=小英文/數字（長按上屏副標）
     */
    private void prepareZhuyinKeyLabels() {
        if (inputMode != MODE_ZHUYIN) return;
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            if (ZHUYIN.get(code) == null) continue;
            key.label = " "; // 空白，避免 super 畫英文蓋過注音
            key.icon = null;
        }
    }


    private boolean isUpper() {
        return inputMode == MODE_ENGLISH && (shiftState == 1 || shiftState == 2);
    }

    private void cycleShift() {
        if (inputMode != MODE_ENGLISH) return;
        shiftState = (shiftState + 1) % 3;
        applyShiftVisual();
        invalidateAllKeys();
        invalidate();
    }

    private void applyShiftVisual() {
        if (inputMode != MODE_ENGLISH || lettersEnKb == null) return;
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
        if (inputMode == MODE_ENGLISH && shiftState == 2
                && lettersEnKb != null && getKeyboard() == lettersEnKb) {
            drawShiftCaps(canvas);
        }

        // 中文字母層才畫注音；符號層不要出現注音
        if (inputMode == MODE_ZHUYIN) {
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
     * 注音大（置中）+ 英文/數字小（右上角）。
     * 短按 → 注音進 Rime；長按 → 上屏小標字元。
     */
    private void drawZhuyinHints(Canvas canvas) {
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        float dens = getResources().getDisplayMetrics().density;
        int padL = getPaddingLeft();
        int padT = getPaddingTop();

        // 大注音
        zhuyinPaint.setTextSize(22f * dens);
        zhuyinPaint.setColor(0xFFE8EAED);
        zhuyinPaint.setTextAlign(Paint.Align.CENTER);
        zhuyinPaint.setFakeBoldText(true);
        if (zhuyinTypeface != null) zhuyinPaint.setTypeface(zhuyinTypeface);
        Paint.FontMetrics zfm = zhuyinPaint.getFontMetrics();

        // 小英文
        letterPaint.setTextSize(11f * dens);
        letterPaint.setColor(0xFF9AA0A6);
        letterPaint.setTextAlign(Paint.Align.RIGHT);
        letterPaint.setFakeBoldText(false);
        Paint.FontMetrics lfm = letterPaint.getFontMetrics();
        float inset = 5f * dens;

        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            String zy = ZHUYIN.get(code);
            if (zy == null) continue;
            float left = key.x + padL;
            float top = key.y + padT;
            float cx = left + key.width / 2f;
            float cy = top + key.height / 2f - (zfm.ascent + zfm.descent) / 2f;
            // 略偏下，給右上小標留空
            cy += 2f * dens;
            canvas.drawText(zy, cx, cy, zhuyinPaint);

            String sec = secondaryLabel(code);
            if (sec != null) {
                float sx = left + key.width - inset;
                float sy = top + 12f * dens - (lfm.ascent + lfm.descent) / 2f;
                canvas.drawText(sec, sx, sy, letterPaint);
            }
        }
        letterPaint.setTextAlign(Paint.Align.CENTER);
        zhuyinPaint.setFakeBoldText(false);
    }

    /** 長按上屏的第二字元（英/數/標點） */
    private static String secondaryLabel(int code) {
        if (code >= 'a' && code <= 'z') return String.valueOf((char) code);
        if (code >= '0' && code <= '9') return String.valueOf((char) code);
        if (code == ',' || code == '.' || code == ';' || code == '/' || code == '-') {
            return String.valueOf((char) code);
        }
        return null;
    }

    private void onZhuyinLongPress() {
        if (inputMode != MODE_ZHUYIN || service == null) return;
        int code = pressCode;
        String sec = secondaryLabel(code);
        if (sec == null) return;
        longPressFired = true;
        // 輕微震動回饋（若裝置支援）
        try {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {}
        service.commitRaw(sec);
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        if (service == null) return;
        switch (primaryCode) {
            case -1:
                // 拼 → 注 → 英（不含符）
                service.cycleInputMode();
                return;
            case -2:
                // 符 / 返回
                service.toggleSymbolsLayer();
                return;
            case -6:
                // 符號頁 更多 ↔ 半形
                toggleSymbolPage();
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
                // 長按已上屏第二字 → 略過短按
                if (longPressFired) {
                    longPressFired = false;
                    return;
                }
                if (inputMode == MODE_SYMBOLS) {
                    if (primaryCode > 0) {
                        // 符號層：直送；注音模式殘留時不送 Rime 聲調（符層無注音）
                        service.commitRaw(new String(Character.toChars(primaryCode)));
                    }
                    return;
                }
                if (inputMode == MODE_ENGLISH && primaryCode >= 'a' && primaryCode <= 'z' && isUpper()) {
                    service.sendKey(Character.toUpperCase(primaryCode), 0);
                    consumeOneShotShift();
                    return;
                }
                // 拼/英：數字直接上屏（不進 Rime 字根/英文碼）
                if ((inputMode == MODE_ROOT || inputMode == MODE_ENGLISH)
                        && primaryCode >= '0' && primaryCode <= '9') {
                    service.commitRaw(String.valueOf((char) primaryCode));
                    return;
                }
                service.sendKey(primaryCode, 0);
        }
    }

    @Override
    public void onPress(int primaryCode) {
        pressCode = primaryCode;
        longPressFired = false;
        longPressHandler.removeCallbacks(longPressRunnable);
        // 僅注音模式、有第二字元的鍵才長按
        if (inputMode == MODE_ZHUYIN && secondaryLabel(primaryCode) != null) {
            longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_MS);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        longPressHandler.removeCallbacks(longPressRunnable);
    }
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
