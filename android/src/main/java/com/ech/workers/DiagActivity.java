package com.ech.workers;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class DiagActivity extends Activity {
    private TextView tv;
    private final StringBuilder report = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(24, 120, 24, 24);

        TextView title = new TextView(this);
        title.setText("ECH 诊断版：先点「打开主程序」，闪退后重新打开本 App，截图这页发给 Hark");
        title.setTextSize(18);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("打开主程序");
        open.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        Button copy = new Button(this);
        copy.setText("复制报告");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ech-diag", report.toString()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        Button clear = new Button(this);
        clear.setText("清除记录");
        clear.setOnClickListener(v -> {
            new File(getFilesDir(), CrashApp.FILE).delete();
            Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
        });
        row.addView(open);
        row.addView(copy);
        row.addView(clear);
        root.addView(row);

        ScrollView sv = new ScrollView(this);
        tv = new TextView(this);
        tv.setTextSize(13);
        tv.setTextColor(Color.BLACK);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        add("机型: " + Build.MANUFACTURER + " " + Build.MODEL + "  Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        add("ABI: " + Build.SUPPORTED_ABIS[0]);
        add("");
        add("===== Java 崩溃记录 =====");
        String crash = readFile(new File(getFilesDir(), CrashApp.FILE), 8000);
        add(crash == null ? "(无)" : crash);
        add("");
        add("===== 系统退出记录 =====");
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(getPackageName(), 0, 4);
                for (ApplicationExitInfo i : list) {
                    add(new java.util.Date(i.getTimestamp()) + " 进程=" + i.getProcessName() + " 原因=" + reason(i.getReason()) + " 状态=" + i.getStatus());
                    if (i.getDescription() != null) add("  描述: " + i.getDescription());
                    if (i.getReason() == ApplicationExitInfo.REASON_ANR) {
                        try {
                            InputStream in = i.getTraceInputStream();
                            if (in != null) add(readStream(in, 3000));
                        } catch (Throwable ignored) { }
                    }
                }
                if (list.isEmpty()) add("(无)");
            }
        } catch (Throwable t) {
            add("读取失败: " + t);
        }
        add("");
        add("===== 日志(警告以上) =====");
        tv.setText(report);
        new Thread(() -> {
            String log;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "400", "*:W"});
                log = readStream(p.getInputStream(), 20000);
            } catch (Throwable t) {
                log = "读取失败: " + t;
            }
            final String l = log;
            runOnUiThread(() -> {
                add(l);
                tv.setText(report);
            });
        }).start();
    }

    private void add(String s) {
        report.append(s).append('\n');
    }

    private static String reason(int r) {
        switch (r) {
            case 4: return "Java崩溃";
            case 5: return "Native崩溃";
            case 6: return "ANR无响应";
            case 3: return "信号";
            case 1: return "自己退出";
            case 10: return "用户请求";
            case 2: return "信号";
            default: return String.valueOf(r);
        }
    }

    private static String readFile(File f, int max) {
        if (!f.exists()) return null;
        try {
            return readStream(new FileInputStream(f), max);
        } catch (Throwable t) {
            return "读取失败: " + t;
        }
    }

    private static String readStream(InputStream in, int max) throws java.io.IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        String s = sb.toString();
        return s.length() > max ? s.substring(s.length() - max) : s;
    }
}
