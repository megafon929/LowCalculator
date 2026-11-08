package com.m929.lowcalculator;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import java.math.BigDecimal;
import java.math.MathContext;

/** LowCalc: калькулятор, весь UI рисуется на Canvas (Holo Dark). API 15-19. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(new CalcView(this));
    }

    //ENGINE-BEGIN
    /** Логика калькулятора без зависимостей от Android. */
    static class Engine {
        String cur = "0";       // вводимое число / результат на экране
        double acc;             // накопленное значение
        char op;                // ожидающая операция или 0
        boolean fresh = true;   // следующая цифра начинает новое число
        boolean opnd;           // после операции уже введён/получен второй операнд
        boolean err;

        void reset() {
            cur = "0"; acc = 0; op = 0; fresh = true; opnd = false; err = false;
        }

        void key(char k) {
            if (k == 'C') { reset(); return; }
            boolean num = (k >= '0' && k <= '9') || k == '.';
            if (err) {
                reset();
                if (!num) return;
            }
            if (num) {
                opnd = true;
                if (k == '.') {
                    if (fresh) { cur = "0."; fresh = false; }
                    else if (cur.indexOf('.') < 0 && cur.length() < 15) cur += '.';
                } else if (fresh) {
                    cur = String.valueOf(k); fresh = false;
                } else if (cur.length() < 15) {
                    if (cur.equals("0")) cur = String.valueOf(k);
                    else if (cur.equals("-0")) cur = "-" + k;
                    else cur += k;
                }
                return;
            }
            if (k == 'R') {          // квадратный корень от текущего числа
                double v = parse(cur);
                if (v < 0) { fail(); return; }
                cur = fmt(Math.sqrt(v));
                fresh = true; opnd = true;
                return;
            }
            if (k == '=') {
                if (op != 0) {
                    acc = apply(acc, parse(cur), op);
                    if (err) return;
                    cur = fmt(acc);
                    op = 0;
                }
                fresh = true;
                return;
            }
            // + - * / ^
            if (op == 0 && fresh && cur.equals("0") && k == '-') {
                cur = "-0"; fresh = false;      // унарный минус в начале
                return;
            }
            if (op != 0 && !opnd) { op = k; return; }  // смена операции
            if (op != 0) {
                acc = apply(acc, parse(cur), op);
                if (err) return;
                cur = fmt(acc);
            } else {
                acc = parse(cur);
            }
            op = k;
            fresh = true;
            opnd = false;
        }

        /** Верхняя маленькая строка: "12 +" */
        String sub() {
            return op == 0 ? "" : fmt(acc) + " " + op;
        }

        double parse(String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
        }

        double apply(double a, double b, char o) {
            double r;
            switch (o) {
                case '+': r = a + b; break;
                case '-': r = a - b; break;
                case '*': r = a * b; break;
                case '^': r = Math.pow(a, b); break;
                default:
                    if (b == 0) { fail(); return 0; }
                    r = a / b;
            }
            if (Double.isNaN(r) || Double.isInfinite(r)) { fail(); return 0; }
            return r;
        }

        void fail() {
            err = true; cur = "Error"; op = 0; acc = 0; fresh = true;
        }

        String fmt(double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) return "Error";
            BigDecimal bd = new BigDecimal(d).round(new MathContext(12)).stripTrailingZeros();
            if (bd.signum() == 0) return "0";
            double ad = Math.abs(d);
            return (ad >= 1e15 || ad < 1e-9) ? bd.toString() : bd.toPlainString();
        }
    }
    //ENGINE-END

    /** Вид: дисплей и сетка кнопок рисуются вручную. */
    static class CalcView extends View {
        // Сетка 4x5:
        //  C  √  ^  ÷
        //  7  8  9  ×
        //  4  5  6  −
        //  1  2  3  +
        //  0  .  [ = ]
        // 'R' = корень (рисуется линиями, без зависимости от шрифта)
        static final String K = "CR^/789*456-123+0.=";
        static final String[] LBL = {"C", "", "^", "\u00F7", "7", "8", "9", "\u00D7",
                "4", "5", "6", "\u2212", "1", "2", "3", "+", "0", ".", "="};
        static final int[] COL = {0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2};
        static final int[] ROW = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4};
        static final int[] CS = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2};
        static final int[] RS = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

        static final int BG = 0xFF1A1A1A;
        static final int BTN = 0xFF333333;
        static final int BTN_OP = 0xFF262626;
        static final int BTN_EQ = 0xFF0B5F80;
        static final int BLUE = 0xFF33B5E5;
        static final int WHITE = 0xFFFFFFFF;
        static final int GRAY = 0xFFAAAAAA;

        final Engine e = new Engine();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final float dp;
        final Typeface light;
        int pressed = -1;
        float dispH, gap, cw, ch;

        CalcView(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            Typeface t = Typeface.create("sans-serif-light", Typeface.NORMAL);
            light = t != null ? t : Typeface.DEFAULT;
        }

        void layout() {
            float w = getWidth(), h = getHeight();
            gap = Math.max(2 * dp, Math.min(w, h) / 70f);
            dispH = h * 0.25f;
            cw = (w - 5 * gap) / 4f;
            ch = (h - dispH - 6 * gap) / 5f;
        }

        void rect(int i, RectF out) {
            out.left = gap + COL[i] * (cw + gap);
            out.top = dispH + gap + ROW[i] * (ch + gap);
            out.right = out.left + CS[i] * cw + (CS[i] - 1) * gap;
            out.bottom = out.top + RS[i] * ch + (RS[i] - 1) * gap;
        }

        int hit(float x, float y) {
            for (int i = K.length() - 1; i >= 0; i--) {
                rect(i, r);
                if (r.contains(x, y)) return i;
            }
            return -1;
        }

        @Override
        protected void onDraw(Canvas c) {
            layout();
            float w = getWidth();
            c.drawColor(BG);

            // --- дисплей ---
            p.setTypeface(light);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(GRAY);
            p.setTextSize(dispH * 0.16f);
            c.drawText(e.sub(), w - 3 * gap, dispH * 0.26f, p);

            String s = e.cur;
            float size = dispH * 0.42f;
            p.setColor(WHITE);
            p.setTextSize(size);
            float maxW = w - 6 * gap;
            while (p.measureText(s) > maxW && size > 8 * dp) {
                size *= 0.92f;
                p.setTextSize(size);
            }
            c.drawText(s, w - 3 * gap, dispH * 0.80f, p);

            // нижняя синяя линия поля ввода (Holo)
            p.setColor(BLUE);
            c.drawRect(gap, dispH - 2 * dp, w - gap, dispH, p);

            // --- кнопки ---
            p.setTextAlign(Paint.Align.CENTER);
            float rad = 2 * dp;
            for (int i = 0; i < K.length(); i++) {
                rect(i, r);
                char k = K.charAt(i);
                boolean eq = k == '=';
                boolean op = !eq && (k == 'C' || k == 'R' || k == '^' || k == '/' || k == '*' || k == '-' || k == '+');
                p.setColor(i == pressed ? BLUE : eq ? BTN_EQ : op ? BTN_OP : BTN);
                c.drawRoundRect(r, rad, rad, p);

                p.setColor(op && i != pressed ? BLUE : WHITE);
                p.setTypeface(light);
                p.setTextSize(Math.min(cw, ch) * 0.42f);
                float cy = (r.top + r.bottom) / 2f - (p.descent() + p.ascent()) / 2f;
                float cx = (r.left + r.right) / 2f;
                if (k == 'R') {
                    float z = Math.min(cw, ch) * 0.2f, my = (r.top + r.bottom) / 2f;
                    p.setStrokeWidth(Math.max(2f, 2 * dp));
                    c.drawLines(new float[] {
                        cx - z, my + 0.1f * z, cx - 0.5f * z, my + z,
                        cx - 0.5f * z, my + z, cx + 0.1f * z, my - z,
                        cx + 0.1f * z, my - z, cx + 1.2f * z, my - z}, p);
                } else {
                    c.drawText(LBL[i], cx, cy, p);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int a = ev.getAction() & MotionEvent.ACTION_MASK;
            int old = pressed;
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                    pressed = hit(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pressed >= 0 && hit(ev.getX(), ev.getY()) != pressed) pressed = -1;
                    break;
                case MotionEvent.ACTION_UP:
                    if (pressed >= 0 && hit(ev.getX(), ev.getY()) == pressed) {
                        playSoundEffect(SoundEffectConstants.CLICK); // системный звук нажатия
                        e.key(K.charAt(pressed));
                    }
                    pressed = -1;
                    invalidate();
                    return true;
                default:
                    pressed = -1;
            }
            if (old != pressed) invalidate();
            return true;
        }
    }
}
