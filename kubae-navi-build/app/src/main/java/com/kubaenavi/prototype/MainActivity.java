package com.kubaenavi.prototype;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private MediaProjectionManager projectionManager;
    private TextView status;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(0xfff5f7fa);

        TextView title = text("쿠배내비 V0.1", 28, true);
        root.addView(title);

        TextView subtitle = text(
                "쿠팡·배민 지도 화면을 보조폰에 그대로 보내는 1차 독립 프로토타입\n" +
                "※ 라이더짝꿍 / 짝꿍내비 / 퀵짝꿍과 분리된 테스트 앱",
                15, false);
        subtitle.setTextColor(0xff51606f);
        subtitle.setPadding(0, dp(10), 0, dp(18));
        root.addView(subtitle);

        status = text("대기 중", 16, true);
        status.setTextColor(0xff0b6bcb);
        root.addView(status);

        Space s1 = new Space(this);
        root.addView(s1, new LinearLayout.LayoutParams(1, dp(18)));

        Button mainButton = button("메인폰 — 쿠배 화면 송출 시작");
        mainButton.setOnClickListener(v -> beginCapture());
        root.addView(mainButton);

        Button stopButton = button("메인폰 송출 중지");
        stopButton.setOnClickListener(v -> {
            Intent i = new Intent(this, CaptureStreamService.class);
            i.setAction(CaptureStreamService.ACTION_STOP);
            startService(i);
            status.setText("송출 중지 요청");
        });
        root.addView(stopButton);

        TextView mainInfo = text(
                "메인폰 주소: " + localIpv4() + ":" + CaptureStreamService.PORT +
                "\n두 폰이 같은 Wi‑Fi/핫스팟에 있을 때 V0.1 전송 테스트가 가능합니다.",
                14, false);
        mainInfo.setTextColor(0xff4a5560);
        mainInfo.setPadding(0, dp(10), 0, dp(20));
        root.addView(mainInfo);

        TextView receiverTitle = text("보조폰", 20, true);
        root.addView(receiverTitle);

        ipInput = new EditText(this);
        ipInput.setHint("메인폰 IP 예: 192.168.0.12");
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        ipInput.setTextSize(16);
        ipInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(ipInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button receiveButton = button("보조폰 — 쿠배 화면 수신");
        receiveButton.setOnClickListener(v -> openReceiver());
        root.addView(receiveButton);

        TextView note = text(
                "V0.1 성공 기준\n" +
                "1. 쿠팡/배민 화면 캡처 허용\n" +
                "2. 메인폰 화면이 보조폰에 실시간 표시\n" +
                "3. 화면이 바뀌면 보조폰도 따라 변경\n\n" +
                "이 버전은 캡처·표시 가능성 검증용입니다. 최종판의 기존 짝꿍내비 연결 엔진 연동은 검증 후 별도로 붙입니다.",
                14, false);
        note.setTextColor(0xff5e6873);
        note.setPadding(0, dp(22), 0, dp(8));
        root.addView(note);

        setContentView(root);
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

        status.setText("송출 시작됨 — 쿠팡/배민 화면으로 이동하세요");
        toast("화면 송출을 시작했습니다.");
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

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(0xff17202a);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
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
