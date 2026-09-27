package com.kubaenavi.prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureStreamService extends Service {
    public static final int PORT = 8989;

    public static final String ACTION_START = "com.kubaenavi.prototype.START";
    public static final String ACTION_STOP = "com.kubaenavi.prototype.STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final int NOTIFICATION_ID = 91;
    private static final String CHANNEL_ID = "kubae_capture";

    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile Socket clientSocket;
    private volatile DataOutputStream clientOutput;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private long lastFrameAt;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("쿠배내비 화면 송출 중")
                .setContentText("보조폰 연결 포트 " + PORT)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (projection == null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent data;
            if (Build.VERSION.SDK_INT >= 33) {
                data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
            } else {
                data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            }
            if (resultCode == -1 || data == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            startProjection(resultCode, data);
        }

        if (!running) {
            running = true;
            startServer();
        }
        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            stopSelf();
            return;
        }

        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) {
            stopSelf();
            return;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, new Handler(getMainLooper()));

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                metrics.widthPixels = bounds.width();
                metrics.heightPixels = bounds.height();
                metrics.densityDpi = getResources().getDisplayMetrics().densityDpi;
            } else {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            }
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }

        int width = Math.max(360, metrics.widthPixels);
        int height = Math.max(640, metrics.heightPixels);
        int density = Math.max(DisplayMetrics.DENSITY_MEDIUM, metrics.densityDpi);

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        captureThread = new HandlerThread("KubaeCaptureThread");
        captureThread.start();

        imageReader.setOnImageAvailableListener(reader -> {
            long now = System.currentTimeMillis();
            if (now - lastFrameAt < 180) {
                Image drop = null;
                try {
                    drop = reader.acquireLatestImage();
                } catch (Throwable ignored) {
                } finally {
                    if (drop != null) drop.close();
                }
                return;
            }
            lastFrameAt = now;
            sendLatestFrame(reader, width, height);
        }, new Handler(captureThread.getLooper()));

        virtualDisplay = projection.createVirtualDisplay(
                "KubaeNaviCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    private void startServer() {
        serverExecutor.execute(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                server.setReuseAddress(true);
                while (running) {
                    Socket socket = server.accept();
                    socket.setTcpNoDelay(true);
                    closeClient();
                    clientSocket = socket;
                    clientOutput = new DataOutputStream(socket.getOutputStream());
                }
            } catch (IOException ignored) {
            } finally {
                closeClient();
            }
        });
    }

    private void sendLatestFrame(ImageReader reader, int width, int height) {
        Image image = null;
        Bitmap padded = null;
        Bitmap cropped = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            if (clientOutput == null) return;

            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) return;

            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            int paddedWidth = width + rowPadding / pixelStride;

            padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);

            cropped = Bitmap.createBitmap(padded, 0, 0, width, height);

            ByteArrayOutputStream jpeg = new ByteArrayOutputStream(256 * 1024);
            cropped.compress(Bitmap.CompressFormat.JPEG, 55, jpeg);
            byte[] bytes = jpeg.toByteArray();

            DataOutputStream out = clientOutput;
            if (out != null && bytes.length > 0 && bytes.length < 5_000_000) {
                synchronized (this) {
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();
                }
            }
        } catch (Throwable e) {
            closeClient();
        } finally {
            if (cropped != null) cropped.recycle();
            if (padded != null) padded.recycle();
            if (image != null) image.close();
        }
    }

    private synchronized void closeClient() {
        try {
            if (clientOutput != null) clientOutput.close();
        } catch (Exception ignored) {
        }
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (Exception ignored) {
        }
        clientOutput = null;
        clientSocket = null;
    }

    @Override
    public void onDestroy() {
        running = false;
        closeClient();

        try {
            if (virtualDisplay != null) virtualDisplay.release();
        } catch (Exception ignored) {
        }
        try {
            if (imageReader != null) imageReader.close();
        } catch (Exception ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        try {
            if (captureThread != null) captureThread.quitSafely();
        } catch (Exception ignored) {
        }

        serverExecutor.shutdownNow();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "쿠배내비 화면 송출",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
