package com.kubaenavi.prototype;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverActivity extends Activity {
    public static final String EXTRA_HOST = "host";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private volatile Socket socket;

    private ImageView imageView;
    private TextView status;
    private Bitmap previousBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String host = getIntent().getStringExtra(EXTRA_HOST);
        if (host == null) host = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff111111);

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(0xff111111);
        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        status = new TextView(this);
        status.setText("연결 준비");
        status.setTextColor(0xffffffff);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0xaa000000);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        sp.gravity = Gravity.TOP;
        root.addView(status, sp);

        TextView close = new TextView(this);
        close.setText("  닫기  ");
        close.setTextSize(16);
        close.setTextColor(0xffffffff);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundColor(0xaa000000);
        close.setPadding(dp(8), dp(8), dp(8), dp(8));
        close.setOnClickListener(v -> finish());

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(88), dp(48));
        cp.gravity = Gravity.BOTTOM | Gravity.END;
        cp.setMargins(0, 0, dp(12), dp(16));
        root.addView(close, cp);

        setContentView(root);

        final String finalHost = host;
        executor.execute(() -> receiveLoop(finalHost));
    }

    private void receiveLoop(String host) {
        while (running) {
            try {
                setStatus("메인폰 " + host + ":" + CaptureStreamService.PORT + " 연결 중…");

                Socket s = new Socket();
                socket = s;
                s.connect(new InetSocketAddress(host, CaptureStreamService.PORT), 5000);
                s.setTcpNoDelay(true);

                setStatus("연결됨 — 쿠배 화면 수신 중");
                DataInputStream in = new DataInputStream(s.getInputStream());

                while (running && !s.isClosed()) {
                    int size = in.readInt();
                    if (size <= 0 || size > 5_000_000) {
                        throw new IllegalStateException("invalid frame size " + size);
                    }

                    byte[] bytes = new byte[size];
                    in.readFully(bytes);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) continue;

                    runOnUiThread(() -> {
                        Bitmap old = previousBitmap;
                        previousBitmap = bitmap;
                        imageView.setImageBitmap(bitmap);
                        if (old != null && old != bitmap && !old.isRecycled()) {
                            old.recycle();
                        }
                    });
                }
            } catch (Throwable e) {
                if (!running) break;
                setStatus("연결 끊김 — 2초 후 재연결");
                closeSocket();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    private void setStatus(String value) {
        runOnUiThread(() -> status.setText(value));
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        running = false;
        closeSocket();
        executor.shutdownNow();

        if (previousBitmap != null && !previousBitmap.isRecycled()) {
            previousBitmap.recycle();
            previousBitmap = null;
        }
        super.onDestroy();
    }
}
