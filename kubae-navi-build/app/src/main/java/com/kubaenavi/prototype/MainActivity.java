package com.kubaenavi.prototype;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_NOTIFICATION = 1002;

    private static final int BLUE = 0xff1677ff;
    private static final int BLUE_DARK = 0xff0b5ed7;
    private static final int TEXT = 0xff17202a;
    private static final int SUB = 0xff66727e;
    private static final int BG = 0xfff4f7fb;
    private static final int SOFT = 0xfff7f9fc;

    private MediaProjectionManager projectionManager;
    private TextView status;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(40));
        root.setGravity(Gravity.TOP);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // HERO
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(8), dp(12), dp(8), dp(20));
        root.addView(hero, matchWrap());

        TextView badge = text("독립 프로토타입", 13, true);
        badge.setTextColor(BLUE_DARK);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(round(0xffe8f1ff, 999, 0, 0));
        hero.addView(badge);

        TextView title = text("쿠배내비 V0.1.1", 31, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(8));
        hero.addView(title);

        TextView subtitle = text(
                "쿠팡·배민 지도 화면을 보조폰에 실시간으로 보여주는\n1차 독립 테스트 앱",
                16, false);
        subtitle.setTextColor(SUB);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0f, 1.35f);
        hero.addView(subtitle);

        TextView separated = text("라이더짝꿍 · 짝꿍내비 · 퀵짝꿍과 분리", 13, false);
        separated.setTextColor(0xff7b8794);
        separated.setGravity(Gravity.CENTER);
        separated.setPadding(0, dp(10), 0, 0);
        hero.addView(separated);

        // STATUS CARD
        LinearLayout statusCard = card();
        root.addView(statusCard, cardParams());

        TextView cardTitle = text("메인폰 화면 송출", 20, true);
        statusCard.addView(cardTitle);

        status = text("대기 중", 15, true);
        status.setTextColor(BLUE_DARK);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(round(0xffedf5ff, 12, 0, 0));
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(12);
        statusCard.addView(status, statusLp);

        Button mainButton = primaryButton("쿠배 화면 송출 시작");
        mainButton.setOnClickListener(v -> beginCapture());
        statusCard.addView(mainButton);

        Button stopButton = secondaryButton("송출 중지");
        stopButton.setOnClickListener(v -> {
            Intent i = new Intent(this, CaptureStreamService.class);
            i.setAction(CaptureStreamService.ACTION_STOP);
            startService(i);
            status.setText("송출 중지됨");
        });
        statusCard.addView(stopButton);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams infoRowLp = matchWrap();
        infoRowLp.topMargin = dp(12);
        statusCard.addView(infoRow, infoRowLp);

        TextView ipBox = smallBox("메인폰 주소", localIpv4() + ":" + CaptureStreamService.PORT);
        TextView modeBox = smallBox("테스트 방식", "같은 Wi‑Fi / 핫스팟");
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(86), 1f);
        half1.rightMargin = dp(6);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(86), 1f);
        half2.leftMargin = dp(6);
        infoRow.addView(ipBox, half1);
        infoRow.addView(modeBox, half2);

        // RECEIVER CARD
        LinearLayout receiverCard = card();
        root.addView(receiverCard, cardParams());

        TextView receiverTitle = text("보조폰 화면 수신", 20, true);
        receiverCard.addView(receiverTitle);

        TextView receiverDesc = text("메인폰에 표시된 IP 주소를 입력한 뒤 연결하세요.", 14, false);
        receiverDesc.setTextColor(SUB);
        receiverDesc.setPadding(0, dp(8), 0, dp(8));
        receiverCard.addView(receiverDesc);

        ipInput = new EditText(this);
        ipInput.setHint("예: 192.168.0.12");
        ipInput.setHintTextColor(0xff9aa5b1);
        ipInput.setTextColor(TEXT);
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        ipInput.setTextSize(16);
        ipInput.setPadding(dp(14), 0, dp(14), 0);
        ipInput.setBackground(round(0xfff7f9fc, 14, 1, 0xffdfe5ec));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        inputLp.topMargin = dp(8);
        receiverCard.addView(ipInput, inputLp);

        Button receiveButton = primaryButton("보조폰에서 쿠배 화면 보기");
        receiveButton.setOnClickListener(v -> openReceiver());
        receiverCard.addView(receiveButton);

        // TEST GUIDE CARD
        LinearLayout guideCard = card();
        root.addView(guideCard, cardParams());

        TextView guideTitle = text("이번 버전에서 확인할 것", 20, true);
        guideCard.addView(guideTitle);

        TextView guide = text(
                "1. 메인폰에서 화면 캡처 허용\n" +
                "2. 쿠팡·배민 화면으로 이동\n" +
                "3. 보조폰에서 메인폰 IP 입력 후 연결\n" +
                "4. 메인폰 화면이 보조폰에 실시간 표시되는지 확인\n" +
                "5. 화면이 바뀌면 보조폰도 따라 변경되는지 확인",
                15, false);
        guide.setTextColor(SUB);
        guide.setLineSpacing(0f, 1.45f);
        guide.setPadding(0, dp(12), 0, 0);
        guideCard.addView(guide);

        TextView footer = text(
                "이 버전은 캡처·표시 가능성 검증용입니다.\n" +
                "쿠배 지도 자동 추적·확대와 기존 짝꿍내비 연결 연동은 다음 단계에서 추가합니다.",
                12, false);
        footer.setTextColor(0xff7b8794);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(18), dp(10), 0);
        root.addView(footer);

        setContentView(scroll);
        askNotificationPermissionIfNeeded();
    }

    private void beginCapture() {
        if (projectionManager == null) {
            toast("화면 캡처 기능을 사용할 수 없습니다.");
            return;
        }
        status.setText("화면 캡처 권한 요청 중");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;

        if (resultCode != RESULT_OK || data == null) {
            status.setText("화면 캡처 허용 취소됨");
            return;
        }

        Intent service = new Intent(this, CaptureStreamService.class);
        service.setAction(CaptureStreamService.ACTION_START);
        service.putExtra(CaptureStreamService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureStreamService.EXTRA_RESULT_DATA, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        status.setText("송출 중 — 쿠팡·배민 화면으로 이동하세요");
        toast("쿠배 화면 송출을 시작했습니다.");
    }

    private void openReceiver() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            toast("메인폰 IP를 입력하세요.");
            return;
        }
        Intent i = new Intent(this, ReceiverActivity.class);
        i.putExtra(ReceiverActivity.EXTRA_HOST, ip);
        startActivity(i);
    }

    private void askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(20), dp(20), dp(20));
        c.setBackground(round(Color.WHITE, 24, 1, 0xffe5eaf0));
        c.setElevation(dp(4));
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(14);
        return lp;
    }

    private TextView smallBox(String label, String value) {
        TextView t = text(label + "\n" + value, 13, false);
        t.setTextColor(SUB);
        t.setLineSpacing(0f, 1.25f);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(round(SOFT, 14, 0, 0));
        return t;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(BLUE, 16, 0, 0));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(BLUE_DARK);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(Color.WHITE, 14, 1, 0xffb9d4f5));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static String localIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String host = address.getHostAddress();
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "IP 확인 필요";
    }
}
