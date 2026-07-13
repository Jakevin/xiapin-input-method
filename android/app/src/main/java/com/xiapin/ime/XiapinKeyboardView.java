package com.xiapin.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;

/**
 * 軟鍵盤：
 *  - 中文：無 Shift
 *  - 英文：⇧ 三態
 *      0 全小寫 — ⇧（一般鍵色）
 *      1 首字大寫 — ⇪（打一個字母後回 0）
 *      2 全大寫 — ⇧ + 藍色填滿（畫在 super 之後，避免被 key_bg 蓋住）
 *
 * codes：-1 中/英、-2 123、-3 Shift、-4 Enter、-5 Backspace
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
    private final RectF shiftFillRect = new RectF();

    private static final int SHIFT_CAPS_FILL = 0xFF5B9BFF; // 全大寫填滿藍
    private static final int SHIFT_ONCE_FILL = 0xFF5A6270; // 首字略亮灰
    private static final int SHIFT_TEXT_ON_FILL = 0xFFFFFFFF;

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
        setOnKeyboardActionListener(this);
        setPreviewEnabled(false);
        setProximityCorrectionEnabled(true);
        shiftFillPaint.setStyle(Paint.Style.FILL);
        shiftTextPaint.setColor(SHIFT_TEXT_ON_FILL);
        shiftTextPaint.setTextAlign(Paint.Align.CENTER);
        shiftTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        shiftTextPaint.setFakeBoldText(true);
    }

    public void setService(XiapinIME svc) { this.service = svc; }

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
        updateLangButton(englishMode);
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
        invalidateAllKeys();
        invalidate();
    }

    public void updateLangButton(boolean english) {
        this.englishMode = english;
        Keyboard kb = getKeyboard();
        if (kb == null) return;
        for (Keyboard.Key key : kb.getKeys()) {
            if (key.codes != null && key.codes[0] == -1) {
                key.label = english ? "英" : "中";
                key.icon = null;
                break;
            }
        }
        invalidateAllKeys();
    }

    private boolean isUpper() {
        return englishMode && (shiftState == 1 || shiftState == 2);
    }

    /** 0 → 1 → 2 → 0 */
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
                // 0: ⇧  1: ⇪ 首字大寫  2: ⇧（填滿在 onDraw 後畫）
                if (shiftState == 0) {
                    key.label = "⇧";
                } else if (shiftState == 1) {
                    key.label = "⇪";
                } else {
                    // 全大寫：label 先清空，onDraw 自己畫白字 ⇧ 在藍底上，避免疊兩層
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

    /**
     * 必須在 super.onDraw 之後畫填滿色，否則會被 key_bg 蓋掉。
     * 全大寫：藍底 + 白 ⇧；首字大寫：略亮灰底（⇪ 仍由 super 畫）。
     */
    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!englishMode || layer != 0 || shiftState == 0) return;
        if (lettersEnKb == null || getKeyboard() != lettersEnKb) return;

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

            if (shiftState == 2) {
                // 全大寫：藍色填滿 + 白 ⇧
                shiftFillPaint.setColor(SHIFT_CAPS_FILL);
                canvas.drawRoundRect(shiftFillRect, radius, radius, shiftFillPaint);
                shiftTextPaint.setTextSize(22f * dens);
                shiftTextPaint.setColor(SHIFT_TEXT_ON_FILL);
                Paint.FontMetrics fm = shiftTextPaint.getFontMetrics();
                float cx = (l + r) / 2f;
                float cy = (t + b) / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText("⇧", cx, cy, shiftTextPaint);
            }
            // 首字大寫 (state 1)：只用 ⇪ 標籤，不塗藍
            break;
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        if (service == null) return;
        switch (primaryCode) {
            case -1:
                service.toggleAscii();
                return;
            case -2:
                service.toggleKeyboardLayer();
                return;
            case -3:
                cycleShift();
                return;
            case -5: {
                boolean handled = service.sendKey(0xff08, 0);
                if (!handled) service.sendDownUpKey(android.view.KeyEvent.KEYCODE_DEL);
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
                        service.commitRaw(new String(Character.toChars(primaryCode)));
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
}
