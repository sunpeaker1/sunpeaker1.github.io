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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements LocationListener {

    private static final int REQ_LOCATION = 201;
    private static final String ROUTE_URL = "https://valhalla1.openstreetmap.de/route";

    private static final int BLUE = 0xff1677ff;
    private static final int NAVY = 0xff08275a;
    private static final int TEXT = 0xff17202a;
    private static final int SUB = 0xff65727e;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MapView map;
    private LocationManager locationManager;
    private GeoPoint currentPoint;
    private Marker currentMarker;
    private Marker destinationMarker;
    private Polyline routeLine;

    private LinearLayout preTopBar;
    private LinearLayout preTripPanel;
    private LinearLayout guidanceCard;
    private LinearLayout bottomBar;
    private TextView status;
    private TextView summary;
    private TextView turnArrow;
    private TextView turnDistance;
    private TextView turnInstruction;
    private TextView nextTurn;
    private TextView centerTurn;
    private TextView etaText;
    private TextView remainText;
    private TextView progressText;

    private EditText destAddress;
    private EditText destLat;
    private EditText destLon;
    private Button routeButton;
    private Button locateButton;

    private Location lastNavLocation;
    private float lastCourseDegrees = Float.NaN;

    private volatile boolean followLocation = true;
    private volatile boolean navigationActive = false;
    private volatile double pendingDestLat = Double.NaN;
    private volatile double pendingDestLon = Double.NaN;
    private volatile String pendingDestAddress = "";

    private RouteResult activeRoute;
    private GeoPoint activeDestination;
    private int nearestRouteIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);
        map.getController().setCenter(new GeoPoint(37.5665, 126.9780));
        root.addView(map, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 운행 전 상태바
        preTopBar = new LinearLayout(this);
        LinearLayout preTop = preTopBar;
        preTop.setOrientation(LinearLayout.VERTICAL);
        preTop.setPadding(dp(14), dp(10), dp(14), dp(10));
        preTop.setBackground(round(0xeeffffff, 18, 1, 0xffe5eaf0));
        preTop.setElevation(dp(4));

        TextView title = text("쿠배내비 · 짧은길", 19, true, TEXT);
        preTop.addView(title);

        status = text("현재 위치 확인 중", 13, true, BLUE);
        status.setPadding(0, dp(3), 0, 0);
        preTop.addView(status);

        summary = text("목적지를 받으면 골목·이면도로 후보를 포함해 짧은 경로를 계산합니다.", 12, false, SUB);
        summary.setPadding(0, dp(3), 0, 0);
        preTop.addView(summary);

        FrameLayout.LayoutParams preTopLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        preTopLp.gravity = Gravity.TOP;
        preTopLp.setMargins(dp(10), dp(10), dp(10), 0);
        root.addView(preTop, preTopLp);

        // 실제 운행 상단 턴 안내
        guidanceCard = new LinearLayout(this);
        guidanceCard.setOrientation(LinearLayout.VERTICAL);
        guidanceCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        guidanceCard.setBackground(round(0xf808275a, 22, 0, 0));
        guidanceCard.setElevation(dp(8));
        guidanceCard.setVisibility(View.GONE);

        LinearLayout mainTurnRow = new LinearLayout(this);
        mainTurnRow.setOrientation(LinearLayout.HORIZONTAL);
        mainTurnRow.setGravity(Gravity.CENTER_VERTICAL);

        turnArrow = text("↑", 54, true, Color.WHITE);
        turnArrow.setGravity(Gravity.CENTER);
        mainTurnRow.addView(turnArrow, new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout turnTextCol = new LinearLayout(this);
        turnTextCol.setOrientation(LinearLayout.VERTICAL);
        turnTextCol.setGravity(Gravity.CENTER_VERTICAL);

        turnDistance = text("--m", 42, true, Color.WHITE);
        turnInstruction = text("경로 안내 준비", 17, true, 0xffeef4ff);
        turnInstruction.setMaxLines(2);
        turnTextCol.addView(turnDistance);
        turnTextCol.addView(turnInstruction);

        mainTurnRow.addView(turnTextCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        guidanceCard.addView(mainTurnRow);

        nextTurn = text("다음 안내 준비 중", 15, true, Color.WHITE);
        nextTurn.setPadding(dp(12), dp(7), dp(12), dp(7));
        nextTurn.setBackground(round(0x442b5fb4, 12, 0, 0));
        guidanceCard.addView(nextTurn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams guideLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        guideLp.gravity = Gravity.TOP;
        guideLp.setMargins(dp(10), dp(10), dp(10), 0);
        root.addView(guidanceCard, guideLp);

        // 회전 직전 중앙 강조
        centerTurn = text("", 58, true, Color.WHITE);
        centerTurn.setGravity(Gravity.CENTER);
        centerTurn.setBackground(round(0xaa1d2b3a, 24, 0, 0));
        centerTurn.setVisibility(View.GONE);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(180), dp(150));
        centerLp.gravity = Gravity.CENTER;
        root.addView(centerTurn, centerLp);

        // 운행 전 목적지 입력 패널
        preTripPanel = new LinearLayout(this);
        preTripPanel.setOrientation(LinearLayout.VERTICAL);
        preTripPanel.setPadding(dp(16), dp(14), dp(16), dp(16));
        preTripPanel.setBackground(round(0xfaffffff, 22, 1, 0xffdfe5ec));
        preTripPanel.setElevation(dp(8));

        TextView panelTitle = text("목적지 주소 / 좌표", 17, true, TEXT);
        preTripPanel.addView(panelTitle);

        destAddress = coordinateInput("목적지 주소 · 짝꿍내비 전송값 테스트");
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        addressLp.topMargin = dp(8);
        preTripPanel.addView(destAddress, addressLp);

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
        preTripPanel.addView(inputRow, inputRowLp);

        routeButton = button("쿠배형 짧은길 찾기", true);
        routeButton.setOnClickListener(v -> routeFromInputs());
        preTripPanel.addView(routeButton);

        TextView warning = text("시험판 · 실제 통행 가능 여부와 교통법규를 우선 확인하세요.", 11, false, 0xff7b8794);
        warning.setPadding(0, dp(7), 0, 0);
        preTripPanel.addView(warning);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;
        panelLp.setMargins(dp(10), 0, dp(10), dp(12));
        root.addView(preTripPanel, panelLp);

        // 운행 중 하단 ETA 바
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(18), dp(12), dp(18), dp(12));
        bottomBar.setBackground(round(0xf7ffffff, 22, 1, 0xffe5eaf0));
        bottomBar.setElevation(dp(8));
        bottomBar.setVisibility(View.GONE);

        LinearLayout etaRow = new LinearLayout(this);
        etaRow.setOrientation(LinearLayout.HORIZONTAL);
        etaRow.setGravity(Gravity.CENTER_VERTICAL);

        etaText = text("도착 --:--", 22, true, TEXT);
        remainText = text("-- km", 22, true, TEXT);
        etaRow.addView(etaText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        etaRow.addView(remainText);
        bottomBar.addView(etaRow);

        progressText = text("남은 시간 계산 중", 14, true, SUB);
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(4), 0, 0);
        bottomBar.addView(progressText);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;
        bottomLp.setMargins(dp(10), 0, dp(10), dp(12));
        root.addView(bottomBar, bottomLp);

        // 사용자가 지도를 움직였을 때만 나타나는 현재위치 복귀 버튼
        locateButton = new Button(this);
        locateButton.setText("➤  현재위치로");
        locateButton.setTextSize(16);
        locateButton.setTypeface(Typeface.DEFAULT_BOLD);
        locateButton.setTextColor(Color.WHITE);
        locateButton.setAllCaps(false);
        locateButton.setBackground(round(0xee202124, 24, 0, 0));
        locateButton.setStateListAnimator(null);
        locateButton.setVisibility(View.GONE);
        locateButton.setOnClickListener(v -> {
            followLocation = true;
            locateButton.setVisibility(View.GONE);
            if (currentPoint != null) {
                map.getController().animateTo(currentPoint);
            }
            applyHeadingUp(lastNavLocation);
        });
        FrameLayout.LayoutParams locateLp = new FrameLayout.LayoutParams(dp(190), dp(58));
        locateLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        locateLp.setMargins(0, 0, 0, dp(118));
        root.addView(locateButton, locateLp);

        map.setOnTouchListener((v, event) -> {
            if (navigationActive && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                followLocation = false;
                locateButton.setVisibility(View.VISIBLE);
            }
            return false;
        });

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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 1.5f, this);
        } catch (Exception ignored) {
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1300L, 3f, this);
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

        if (!navigationActive) {
            status.setText(String.format(Locale.KOREA, "GPS 연결 · 정확도 약 %.0fm", location.getAccuracy()));
        }

        if (navigationActive) {
            updateNavigation(location);
        } else if (followLocation) {
            map.getController().animateTo(currentPoint);
        }

        map.invalidate();
        maybeAutoRoute();
    }

    private void updateNavigation(Location location) {
        if (activeRoute == null || activeRoute.points.isEmpty()) return;

        nearestRouteIndex = findNearestRouteIndex(currentPoint, activeRoute.points, nearestRouteIndex);

        if (followLocation) {
            locateButton.setVisibility(View.GONE);
            applyHeadingUp(location);
            map.getController().animateTo(currentPoint);
        }
        rememberNavigationLocation(location);

        double remainingMeters = distanceAlong(activeRoute.points, nearestRouteIndex, activeRoute.points.size() - 1);
        Maneuver next = null;
        Maneuver after = null;

        for (Maneuver m : activeRoute.maneuvers) {
            if (m.beginShapeIndex > nearestRouteIndex + 1) {
                if (next == null) next = m;
                else {
                    after = m;
                    break;
                }
            }
        }

        if (next == null) {
            turnArrow.setText("◎");
            turnDistance.setText(formatDistance(remainingMeters));
            turnInstruction.setText("목적지에 접근 중");
            nextTurn.setText("안전하게 목적지를 확인하세요.");
            centerTurn.setVisibility(View.GONE);
        } else {
            double nextMeters = distanceAlong(activeRoute.points,
                    nearestRouteIndex,
                    Math.min(next.beginShapeIndex, activeRoute.points.size() - 1));

            turnArrow.setText(arrowForType(next.type));
            turnDistance.setText(formatDistance(nextMeters));
            turnInstruction.setText(cleanInstruction(next.instruction));
            nextTurn.setText(after == null
                    ? "다음 안내 · 목적지"
                    : "다음 " + arrowForType(after.type) + "  " + cleanInstruction(after.instruction));

            if (nextMeters <= 80.0) {
                centerTurn.setText(arrowForType(next.type) + "\n" + formatDistance(nextMeters));
                centerTurn.setVisibility(View.VISIBLE);
            } else {
                centerTurn.setVisibility(View.GONE);
            }

            if (followLocation) {
                double targetZoom;
                if (nextMeters <= 25) targetZoom = 19.5;
                else if (nextMeters <= 60) targetZoom = 19.0;
                else if (nextMeters <= 120) targetZoom = 18.5;
                else if (nextMeters <= 250) targetZoom = 18.0;
                else if (location.getSpeed() >= 12f) targetZoom = 16.8;
                else if (location.getSpeed() >= 6f) targetZoom = 17.2;
                else targetZoom = 17.7;
                map.getController().setZoom(targetZoom);
            }
        }

        double remainKm = Math.max(0.0, remainingMeters / 1000.0);
        double fraction = activeRoute.lengthKm > 0.01
                ? Math.max(0.0, Math.min(1.0, remainKm / activeRoute.lengthKm))
                : 0.0;
        long remainSeconds = Math.max(20L, Math.round(activeRoute.timeSeconds * fraction));
        long arrivalMillis = System.currentTimeMillis() + remainSeconds * 1000L;
        String arrival = new SimpleDateFormat("a h:mm", Locale.KOREA).format(new Date(arrivalMillis));

        etaText.setText("도착 " + arrival);
        remainText.setText(String.format(Locale.KOREA, "%.1f km", remainKm));
        progressText.setText(Math.max(1, Math.round(remainSeconds / 60.0)) + "분 남음");

        if (remainingMeters < 20.0) {
            turnInstruction.setText("목적지 도착");
            turnDistance.setText("도착");
            centerTurn.setVisibility(View.GONE);
        }
    }

    private void applyHeadingUp(Location location) {
        if (!navigationActive || !followLocation || location == null) return;

        Float course = resolveCourseDegrees(location);
        if (course == null) return;

        float smoothed = smoothCourse(lastCourseDegrees, course);
        lastCourseDegrees = smoothed;

        // osmdroid 공식 heading-up 방식: 진행방향이 화면 위를 향하도록 360 - bearing.
        float orientation = (360f - smoothed) % 360f;
        if (orientation < 0f) orientation += 360f;
        map.setMapOrientation(orientation, true);
    }

    private Float resolveCourseDegrees(Location location) {
        if (location.hasBearing() && location.getSpeed() >= 0.7f) {
            return normalizeDegrees(location.getBearing());
        }

        if (lastNavLocation != null) {
            float moved = lastNavLocation.distanceTo(location);
            if (moved >= 2.5f) {
                return normalizeDegrees(lastNavLocation.bearingTo(location));
            }
        }

        Float routeCourse = routeCourseDegrees();
        if (routeCourse != null) return routeCourse;

        if (!Float.isNaN(lastCourseDegrees)) {
            return lastCourseDegrees;
        }
        return null;
    }

    private Float routeCourseDegrees() {
        if (activeRoute == null || activeRoute.points == null || activeRoute.points.size() < 2) return null;

        int from = Math.max(0, Math.min(nearestRouteIndex, activeRoute.points.size() - 2));
        GeoPoint a = activeRoute.points.get(from);

        for (int i = from + 1; i < Math.min(activeRoute.points.size(), from + 25); i++) {
            GeoPoint b = activeRoute.points.get(i);
            if (straightDistanceMeters(a, b) >= 8.0) {
                float[] out = new float[2];
                Location.distanceBetween(
                        a.getLatitude(), a.getLongitude(),
                        b.getLatitude(), b.getLongitude(), out);
                return normalizeDegrees(out[1]);
            }
        }
        return null;
    }

    private void rememberNavigationLocation(Location location) {
        if (location == null) return;
        if (lastNavLocation == null || lastNavLocation.distanceTo(location) >= 1.5f) {
            lastNavLocation = new Location(location);
        }
    }

    private static float normalizeDegrees(float degrees) {
        float d = degrees % 360f;
        if (d < 0f) d += 360f;
        return d;
    }

    private static float smoothCourse(float previous, float current) {
        if (Float.isNaN(previous)) return normalizeDegrees(current);

        float delta = normalizeDegrees(current - previous);
        if (delta > 180f) delta -= 360f;

        // 급격한 GPS 튐을 완화하되 골목 회전은 따라갈 수 있게 45% 반영.
        return normalizeDegrees(previous + delta * 0.45f);
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
        summary.setText("짝꿍내비 목적지 주소를 쿠배내비 경로로 변환합니다.");

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
        summary.setText("골목·이면도로를 포함한 후보 중 이동거리가 가장 짧은 경로를 고릅니다.");

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

        if (code < 200 || code >= 300) return null;

        JSONObject json = new JSONObject(response);
        JSONObject trip = json.getJSONObject("trip");
        JSONObject tripSummary = trip.getJSONObject("summary");
        JSONArray legs = trip.getJSONArray("legs");
        if (legs.length() == 0) return null;

        JSONObject leg = legs.getJSONObject(0);
        String shape = leg.getString("shape");
        List<GeoPoint> points = decodePolyline6(shape);

        List<Maneuver> maneuvers = new ArrayList<>();
        JSONArray ms = leg.optJSONArray("maneuvers");
        if (ms != null) {
            for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.getJSONObject(i);
                Maneuver man = new Maneuver();
                man.type = m.optInt("type", 0);
                man.instruction = m.optString("instruction", "");
                man.beginShapeIndex = m.optInt("begin_shape_index", 0);
                man.endShapeIndex = m.optInt("end_shape_index", man.beginShapeIndex);
                maneuvers.add(man);
            }
        }

        RouteResult r = new RouteResult();
        r.points = points;
        r.maneuvers = maneuvers;
        r.lengthKm = tripSummary.optDouble("length", Double.MAX_VALUE);
        r.timeSeconds = tripSummary.optDouble("time", 0);
        r.usePrimary = usePrimary;
        return r;
    }

    private void drawRoute(RouteResult result, GeoPoint destination) {
        if (routeLine != null) map.getOverlays().remove(routeLine);
        if (destinationMarker != null) map.getOverlays().remove(destinationMarker);

        routeLine = new Polyline(map);
        routeLine.setPoints(result.points);
        routeLine.setColor(BLUE);
        routeLine.setWidth(dp(8));
        map.getOverlays().add(routeLine);

        destinationMarker = new Marker(map);
        destinationMarker.setPosition(destination);
        destinationMarker.setTitle("목적지");
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setIcon(getDrawable(android.R.drawable.ic_menu_mylocation));
        map.getOverlays().add(destinationMarker);

        activeRoute = result;
        activeDestination = destination;
        nearestRouteIndex = 0;
        navigationActive = true;
        followLocation = true;
        lastNavLocation = null;
        lastCourseDegrees = Float.NaN;
        locateButton.setVisibility(View.GONE);

        preTopBar.setVisibility(View.GONE);
        preTripPanel.setVisibility(View.GONE);
        guidanceCard.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        if (!result.points.isEmpty()) {
            map.getController().setZoom(17.7);
            map.getController().animateTo(currentPoint != null ? currentPoint : result.points.get(0));
            Float initialCourse = routeCourseDegrees();
            if (initialCourse != null) {
                lastCourseDegrees = initialCourse;
                map.setMapOrientation((360f - initialCourse) % 360f, true);
            }
        }

        turnInstruction.setText("출발하세요");
        turnDistance.setText("출발");
        nextTurn.setText("경로 안내를 시작합니다.");
        map.invalidate();
    }

    private int findNearestRouteIndex(GeoPoint p, List<GeoPoint> pts, int hint) {
        if (p == null || pts == null || pts.isEmpty()) return 0;
        int start = Math.max(0, hint - 25);
        int end = Math.min(pts.size() - 1, hint + 160);

        double best = Double.MAX_VALUE;
        int bestIdx = hint;

        for (int i = start; i <= end; i++) {
            double d = straightDistanceMeters(p, pts.get(i));
            if (d < best) {
                best = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private double distanceAlong(List<GeoPoint> pts, int start, int end) {
        if (pts == null || pts.size() < 2) return 0.0;
        int a = Math.max(0, Math.min(start, pts.size() - 1));
        int b = Math.max(0, Math.min(end, pts.size() - 1));
        if (b <= a) return 0.0;

        double total = 0.0;
        for (int i = a; i < b; i++) {
            total += straightDistanceMeters(pts.get(i), pts.get(i + 1));
        }
        return total;
    }

    private static double straightDistanceMeters(GeoPoint a, GeoPoint b) {
        float[] r = new float[1];
        Location.distanceBetween(
                a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude(), r);
        return r[0];
    }

    private String arrowForType(int type) {
        if (type == 9 || type == 10 || type == 11 || type == 18 || type == 20 || type == 23) return "↱";
        if (type == 14 || type == 15 || type == 16 || type == 19 || type == 21 || type == 24) return "↰";
        if (type == 12 || type == 13) return "↶";
        if (type == 26 || type == 27) return "⟳";
        if (type == 4 || type == 5 || type == 6) return "◎";
        return "↑";
    }

    private String formatDistance(double meters) {
        if (meters < 1000.0) return Math.max(1, Math.round(meters)) + "m";
        return String.format(Locale.KOREA, "%.1fkm", meters / 1000.0);
    }

    private String cleanInstruction(String s) {
        if (s == null || s.trim().isEmpty()) return "계속 진행";
        String t = s.trim();
        if (t.length() > 44) t = t.substring(0, 44);
        return t;
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

    private static class Maneuver {
        int type;
        String instruction = "";
        int beginShapeIndex;
        int endShapeIndex;
    }

    private static class RouteResult {
        List<GeoPoint> points = new ArrayList<>();
        List<Maneuver> maneuvers = new ArrayList<>();
        double lengthKm;
        double timeSeconds;
        double usePrimary;
    }
}
