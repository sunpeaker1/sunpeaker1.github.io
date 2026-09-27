package com.kubaenavi.route;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements LocationListener {

    private static final int REQ_LOCATION = 201;
    private static final String ROUTE_URL = "https://valhalla1.openstreetmap.de/route";
    private static final int BLUE = 0xff1677ff;
    private static final int TEXT = 0xff17202a;
    private static final int SUB = 0xff65727e;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MapView map;
    private LocationManager locationManager;
    private GeoPoint currentPoint;
    private Marker currentMarker;
    private Marker destinationMarker;
    private Polyline routeLine;

    private EditText destAddress;
    private EditText destLat;
    private EditText destLon;
    private TextView status;
    private TextView summary;
    private Button routeButton;

    private volatile boolean followLocation = true;
    private volatile double pendingDestLat = Double.NaN;
    private volatile double pendingDestLon = Double.NaN;
    private volatile String pendingDestAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);
        map.getController().setCenter(new GeoPoint(37.5665, 126.9780));
        root.addView(map, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackground(round(0xf7ffffff, 20, 1, 0xffe5eaf0));
        top.setElevation(dp(5));

        TextView title = text("쿠배내비 · 짧은길 시험", 20, true, TEXT);
        top.addView(title);

        status = text("현재 위치 확인 중", 13, true, BLUE);
        status.setPadding(0, dp(4), 0, 0);
        top.addView(status);

        summary = text("목적지를 입력하면 이륜차 후보 경로 중 이동거리가 가장 짧은 경로를 표시합니다.", 12, false, SUB);
        summary.setPadding(0, dp(4), 0, 0);
        top.addView(summary);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        topLp.setMargins(dp(10), dp(10), dp(10), 0);
        root.addView(top, topLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));
        panel.setBackground(round(0xfaffffff, 22, 1, 0xffdfe5ec));
        panel.setElevation(dp(8));

        TextView panelTitle = text("목적지 주소 / 좌표", 17, true, TEXT);
        panel.addView(panelTitle);

        destAddress = coordinateInput("목적지 주소 · 기존 짝꿍내비 전송값 테스트");
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        addressLp.topMargin = dp(8);
        panel.addView(destAddress, addressLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        destLat = coordinateInput("위도");
        destLon = coordinateInput("경도");

        LinearLayout.LayoutParams halfA = new LinearLayout.LayoutParams(0, dp(50), 1f);
        halfA.rightMargin = dp(5);
        LinearLayout.LayoutParams halfB = new LinearLayout.LayoutParams(0, dp(50), 1f);
        halfB.leftMargin = dp(5);

        inputRow.addView(destLat, halfA);
        inputRow.addView(destLon, halfB);

        LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputRowLp.topMargin = dp(8);
        panel.addView(inputRow, inputRowLp);

        routeButton = button("쿠배형 짧은길 찾기", true);
        routeButton.setOnClickListener(v -> routeFromInputs());
        panel.addView(routeButton);

        Button followButton = button("현재 위치 자동추적 ON / OFF", false);
        followButton.setOnClickListener(v -> {
            followLocation = !followLocation;
            Toast.makeText(this, followLocation ? "자동추적 ON" : "자동추적 OFF", Toast.LENGTH_SHORT).show();
            if (followLocation && currentPoint != null) {
                map.getController().animateTo(currentPoint);
            }
        });
        panel.addView(followButton);

        TextView warning = text("시험판: 실제 통행 가능 여부와 교통법규를 우선 확인하세요.", 11, false, 0xff7b8794);
        warning.setPadding(0, dp(7), 0, 0);
        panel.addView(warning);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;
        panelLp.setMargins(dp(10), 0, dp(10), dp(12));
        root.addView(panel, panelLp);

        setContentView(root);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestLocationIfNeeded();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        String address = intent.getStringExtra("dest_address");
        double lat = intent.getDoubleExtra("dest_lat", Double.NaN);
        double lon = intent.getDoubleExtra("dest_lon", Double.NaN);

        Uri data = intent.getData();
        if (data != null && "kubaenavi".equalsIgnoreCase(data.getScheme())) {
            if (address == null || address.trim().isEmpty()) {
                address = data.getQueryParameter("address");
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                try {
                    lat = Double.parseDouble(data.getQueryParameter("lat"));
                    lon = Double.parseDouble(data.getQueryParameter("lon"));
                } catch (Exception ignored) {
                }
            }
        }

        if (address != null && !address.trim().isEmpty()) {
            pendingDestAddress = address.trim();
            if (destAddress != null) destAddress.setText(pendingDestAddress);
            maybeAutoRoute();
            return;
        }

        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            pendingDestLat = lat;
            pendingDestLon = lon;
            if (destLat != null && destLon != null) {
                destLat.setText(String.format(Locale.US, "%.6f", lat));
                destLon.setText(String.format(Locale.US, "%.6f", lon));
            }
            maybeAutoRoute();
        }
    }

    private void requestLocationIfNeeded() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
        } catch (Exception ignored) {
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 3f, this);
        } catch (Exception ignored) {
        }

        Location last = null;
        try {
            last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }
        if (last == null) {
            try {
                last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {
            }
        }
        if (last != null) onLocationChanged(last);
    }

    @Override
    public void onLocationChanged(Location location) {
        currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (currentMarker == null) {
            currentMarker = new Marker(map);
            currentMarker.setTitle("현재 위치");
            currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            currentMarker.setIcon(getDrawable(android.R.drawable.ic_menu_mylocation));
            map.getOverlays().add(currentMarker);
        }
        currentMarker.setPosition(currentPoint);

        status.setText(String.format(Locale.KOREA, "GPS 연결 · 정확도 약 %.0fm", location.getAccuracy()));

        if (followLocation) {
            map.getController().animateTo(currentPoint);
        }
        map.invalidate();
        maybeAutoRoute();
    }

    private void maybeAutoRoute() {
        if (currentPoint == null) return;

        if (pendingDestAddress != null && !pendingDestAddress.trim().isEmpty()) {
            String address = pendingDestAddress.trim();
            pendingDestAddress = "";
            geocodeAndRoute(address);
            return;
        }

        if (Double.isNaN(pendingDestLat) || Double.isNaN(pendingDestLon)) return;
        double lat = pendingDestLat;
        double lon = pendingDestLon;
        pendingDestLat = Double.NaN;
        pendingDestLon = Double.NaN;
        calculateRoute(lat, lon);
    }

    private void routeFromInputs() {
        if (currentPoint == null) {
            toast("현재 GPS 위치를 아직 확인하지 못했습니다.");
            return;
        }

        String address = destAddress == null ? "" : destAddress.getText().toString().trim();
        if (!address.isEmpty()) {
            geocodeAndRoute(address);
            return;
        }

        try {
            double lat = Double.parseDouble(destLat.getText().toString().trim());
            double lon = Double.parseDouble(destLon.getText().toString().trim());
            calculateRoute(lat, lon);
        } catch (Exception e) {
            toast("목적지 주소 또는 위도·경도를 확인하세요.");
        }
    }

    private void geocodeAndRoute(String address) {
        if (currentPoint == null || address == null || address.trim().isEmpty()) return;

        routeButton.setEnabled(false);
        status.setText("목적지 주소 좌표 확인 중…");
        summary.setText("기존 짝꿍내비처럼 목적지 주소를 받아 쿠배내비 경로로 변환합니다.");

        final String clean = address.trim();
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.KOREA);
                List<Address> list = geocoder.getFromLocationName(clean, 5);
                if (list == null || list.isEmpty()) {
                    runOnUiThread(() -> {
                        routeButton.setEnabled(true);
                        status.setText("주소 변환 실패");
                        summary.setText("주소를 좌표로 찾지 못했습니다. 좌표 입력으로도 시험할 수 있습니다.");
                    });
                    return;
                }

                Address best = list.get(0);
                double lat = best.getLatitude();
                double lon = best.getLongitude();

                runOnUiThread(() -> {
                    destLat.setText(String.format(Locale.US, "%.6f", lat));
                    destLon.setText(String.format(Locale.US, "%.6f", lon));
                    routeButton.setEnabled(true);
                    calculateRoute(lat, lon);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    routeButton.setEnabled(true);
                    status.setText("주소 변환 오류");
                    summary.setText("휴대폰 주소 검색 서비스 또는 네트워크를 확인하세요.");
                });
            }
        });
    }

    private void calculateRoute(double lat, double lon) {
        if (currentPoint == null) return;

        routeButton.setEnabled(false);
        status.setText("이륜차 후보 경로 3개 계산 중…");
        summary.setText("대도로 선호도를 다르게 계산한 뒤 실제 이동거리가 가장 짧은 경로를 선택합니다.");

        final GeoPoint start = new GeoPoint(currentPoint.getLatitude(), currentPoint.getLongitude());
        final GeoPoint dest = new GeoPoint(lat, lon);

        executor.execute(() -> {
            try {
                double[] usePrimary = {0.00, 0.15, 0.35};
                RouteResult best = null;

                for (double primary : usePrimary) {
                    RouteResult r = requestValhalla(start, dest, primary);
                    if (r != null && !r.points.isEmpty()) {
                        if (best == null || r.lengthKm < best.lengthKm) {
                            best = r;
                        }
                    }
                }

                final RouteResult selected = best;
                runOnUiThread(() -> {
                    routeButton.setEnabled(true);
                    if (selected == null) {
                        status.setText("경로 계산 실패");
                        summary.setText("이륜차 통행 가능한 경로를 찾지 못했습니다. 좌표나 네트워크를 확인하세요.");
                        return;
                    }
                    drawRoute(selected, dest);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    routeButton.setEnabled(true);
                    status.setText("경로 계산 오류");
                    summary.setText("네트워크 또는 경로 서버 연결을 확인하세요.");
                });
            }
        });
    }

    private RouteResult requestValhalla(GeoPoint start, GeoPoint dest, double usePrimary) throws Exception {
        JSONObject request = new JSONObject();

        JSONArray locations = new JSONArray();
        locations.put(new JSONObject().put("lat", start.getLatitude()).put("lon", start.getLongitude()));
        locations.put(new JSONObject().put("lat", dest.getLatitude()).put("lon", dest.getLongitude()));
        request.put("locations", locations);

        request.put("costing", "motor_scooter");

        JSONObject scooter = new JSONObject();
        scooter.put("use_primary", usePrimary);
        scooter.put("use_hills", 1.0);
        scooter.put("top_speed", 60);

        request.put("costing_options", new JSONObject().put("motor_scooter", scooter));
        request.put("directions_options", new JSONObject()
                .put("units", "kilometers")
                .put("language", "ko-KR"));

        HttpURLConnection conn = (HttpURLConnection) new URL(ROUTE_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-Client-Id", "kubae-navi-prototype");

        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(stream);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            return null;
        }

        JSONObject json = new JSONObject(response);
        JSONObject trip = json.getJSONObject("trip");
        JSONObject tripSummary = trip.getJSONObject("summary");
        JSONArray legs = trip.getJSONArray("legs");
        if (legs.length() == 0) return null;

        String shape = legs.getJSONObject(0).getString("shape");
        List<GeoPoint> points = decodePolyline6(shape);

        RouteResult r = new RouteResult();
        r.points = points;
        r.lengthKm = tripSummary.optDouble("length", Double.MAX_VALUE);
        r.timeSeconds = tripSummary.optDouble("time", 0);
        r.usePrimary = usePrimary;
        return r;
    }

    private void drawRoute(RouteResult result, GeoPoint destination) {
        if (routeLine != null) {
            map.getOverlays().remove(routeLine);
        }
        if (destinationMarker != null) {
            map.getOverlays().remove(destinationMarker);
        }

        routeLine = new Polyline(map);
        routeLine.setPoints(result.points);
        routeLine.setColor(BLUE);
        routeLine.setWidth(dp(6));
        map.getOverlays().add(routeLine);

        destinationMarker = new Marker(map);
        destinationMarker.setPosition(destination);
        destinationMarker.setTitle("목적지");
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setIcon(getDrawable(android.R.drawable.ic_menu_mylocation));
        map.getOverlays().add(destinationMarker);

        status.setText("쿠배형 짧은길 표시 완료");
        int mins = (int) Math.max(1, Math.round(result.timeSeconds / 60.0));
        summary.setText(String.format(Locale.KOREA,
                "후보 3개 중 최단 %.2f km · 예상 %d분 · 대도로 선호 %.0f%%",
                result.lengthKm, mins, result.usePrimary * 100.0));

        if (!result.points.isEmpty()) {
            org.osmdroid.util.BoundingBox box = org.osmdroid.util.BoundingBox.fromGeoPoints(result.points);
            map.zoomToBoundingBox(box, true, dp(90));
        }
        map.invalidate();
    }

    private static List<GeoPoint> decodePolyline6(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lon = 0;

        while (index < encoded.length()) {
            int[] a = decodeValue(encoded, index);
            lat += a[0];
            index = a[1];

            int[] b = decodeValue(encoded, index);
            lon += b[0];
            index = b[1];

            points.add(new GeoPoint(lat / 1e6, lon / 1e6));
        }
        return points;
    }

    private static int[] decodeValue(String encoded, int start) {
        int result = 0;
        int shift = 0;
        int b;
        int index = start;

        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);

        int delta = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
        return new int[]{delta, index};
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private EditText coordinateInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(0xfff7f9fc, 13, 1, 0xffdfe5ec));
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(round(primary ? BLUE : Color.WHITE, 14, 1, primary ? BLUE : 0xffb8d4f7));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(primary ? 54 : 48));
        lp.topMargin = dp(primary ? 10 : 6);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else if (requestCode == REQ_LOCATION) {
            status.setText("위치 권한 필요");
            summary.setText("현재 위치를 이용하려면 위치 권한을 허용해야 합니다.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (locationManager != null) locationManager.removeUpdates(this);
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class RouteResult {
        List<GeoPoint> points = new ArrayList<>();
        double lengthKm;
        double timeSeconds;
        double usePrimary;
    }
}
