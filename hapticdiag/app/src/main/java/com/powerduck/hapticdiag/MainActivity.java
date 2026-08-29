package com.powerduck.hapticdiag;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        showDeviceInfo();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(17,17,17));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(box);

        TextView title = new TextView(this);
        title.setText("어서오소 · Android 네이티브 진동 진단");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0,0,0,dp(8));
        box.addView(title);

        TextView guide = new TextView(this);
        guide.setText("브라우저와 WebView를 전혀 쓰지 않습니다. 아래 버튼을 하나씩 눌러 실제로 진동하는 번호만 확인하세요.");
        guide.setTextColor(Color.LTGRAY);
        guide.setTextSize(16);
        guide.setPadding(0,0,0,dp(12));
        box.addView(guide);

        addButton(box, "① Legacy Vibrator · 500ms", v -> testLegacy500());
        addButton(box, "② VibratorManager · 500ms MAX", v -> testManager500());
        addButton(box, "③ 강한 파형 · 3회", v -> testWaveform());
        addButton(box, "④ HEAVY_CLICK · 시스템 효과", v -> testHeavyClick());
        addButton(box, "⑤ 1초 MAX 진동", v -> testOneSecond());
        addButton(box, "⑥ 모든 진동 정지", v -> cancelAll());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(38,38,38));
        status.setTextSize(15);
        status.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin = dp(14);
        box.addView(status, sp);

        setContentView(scroll);
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(62));
        p.topMargin = dp(8);
        parent.addView(b,p);
    }

    private Vibrator legacyVibrator() {
        try {
            return (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable t) {
            return null;
        }
    }

    private Vibrator managerVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm == null ? null : vm.getDefaultVibrator();
            }
            return legacyVibrator();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean fireOneShot(Vibrator v, long ms, int amplitude) {
        if (v == null || !v.hasVibrator()) return false;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
            } else {
                v.vibrate(ms);
            }
            return true;
        } catch (Throwable t) {
            setStatus("오류: " + t.getClass().getSimpleName() + "\n" + String.valueOf(t.getMessage()));
            return false;
        }
    }

    private void testLegacy500() {
        Vibrator v = legacyVibrator();
        boolean ok = fireOneShot(v,500,VibrationEffect.DEFAULT_AMPLITUDE);
        setStatus("① Legacy Vibrator 500ms 호출=" + ok + "\n" + vibratorInfo(v));
    }

    private void testManager500() {
        Vibrator v = managerVibrator();
        boolean ok = fireOneShot(v,500,255);
        setStatus("② VibratorManager/MAX 500ms 호출=" + ok + "\n" + vibratorInfo(v));
    }

    private void testWaveform() {
        Vibrator v = managerVibrator();
        boolean ok = false;
        try {
            if (v != null && v.hasVibrator()) {
                long[] timings = {0,220,120,350,120,550};
                if (Build.VERSION.SDK_INT >= 26) {
                    int[] amps = {0,255,0,255,0,255};
                    v.vibrate(VibrationEffect.createWaveform(timings,amps,-1));
                } else {
                    v.vibrate(timings,-1);
                }
                ok = true;
            }
        } catch (Throwable t) {
            setStatus("③ 오류: " + t.getClass().getSimpleName() + "\n" + String.valueOf(t.getMessage()));
            return;
        }
        setStatus("③ 강한 파형 3회 호출=" + ok + "\n" + vibratorInfo(v));
    }

    private void testHeavyClick() {
        Vibrator v = managerVibrator();
        boolean ok = false;
        try {
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                } else {
                    fireOneShot(v,80,255);
                }
                ok = true;
            }
        } catch (Throwable t) {
            setStatus("④ 오류: " + t.getClass().getSimpleName() + "\n" + String.valueOf(t.getMessage()));
            return;
        }
        setStatus("④ HEAVY_CLICK 호출=" + ok + "\n" + vibratorInfo(v));
    }

    private void testOneSecond() {
        Vibrator v = managerVibrator();
        boolean ok = fireOneShot(v,1000,255);
        setStatus("⑤ 1초 MAX 호출=" + ok + "\n" + vibratorInfo(v));
    }

    private void cancelAll() {
        try {
            Vibrator a = legacyVibrator();
            Vibrator b = managerVibrator();
            if (a != null) a.cancel();
            if (b != null && b != a) b.cancel();
            setStatus("⑥ 진동 정지 요청 완료");
        } catch (Throwable t) {
            setStatus("⑥ 정지 오류: " + t.getClass().getSimpleName());
        }
    }

    private String vibratorInfo(Vibrator v) {
        if (v == null) return "Vibrator=null";
        StringBuilder s = new StringBuilder();
        s.append("hasVibrator=").append(v.hasVibrator());
        if (Build.VERSION.SDK_INT >= 26) s.append("\namplitudeControl=").append(v.hasAmplitudeControl());
        return s.toString();
    }

    private void showDeviceInfo() {
        Vibrator legacy = legacyVibrator();
        Vibrator manager = managerVibrator();
        StringBuilder s = new StringBuilder();
        s.append("Android SDK=").append(Build.VERSION.SDK_INT);
        s.append("\n기기=").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);
        s.append("\nLegacy: ").append(vibratorInfo(legacy));
        s.append("\nManager: ").append(vibratorInfo(manager));
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                int[] ids=vm==null?new int[0]:vm.getVibratorIds();
                s.append("\nVibrator IDs=").append(java.util.Arrays.toString(ids));
            } catch(Throwable ignored) {}
        }
        setStatus(s.toString());
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }
}
