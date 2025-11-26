package com.example.parcial;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final double TOLERANCE_PERCENTAGE = 0.10;
    private static final Scalar LABEL_COLOR = new Scalar(255, 0, 0, 255);
    private static final Scalar CIRCLE_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar UNKNOWN_CIRCLE_COLOR = new Scalar(0, 0, 255, 255);

    private CameraBridgeViewBase cameraView;
    private Mat gray, circles, rotated;
    private TextView tvInfo;

    private final int CAMERA_PERMISSION = 100;
    private boolean hasCameraPermission = false;
    private List<CoinType> coinTypes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        tvInfo = findViewById(R.id.tvInfo);

        // --- Permiso de cámara ---
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION);
        } else {
            hasCameraPermission = true;
        }

        // --- Vista de la cámara ---
        cameraView = findViewById(R.id.cameraView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);

        coinTypes = initializeCoinTypes();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Cargar OpenCV directamente
        if (hasCameraPermission && OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV cargado");
            cameraView.setCameraPermissionGranted();
            cameraView.enableView();
        } else {
            Log.e(TAG, "OpenCV NO pudo cargarse o falta permiso de cámara");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION) {
            hasCameraPermission = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (hasCameraPermission) {
                cameraView.setCameraPermissionGranted();
                if (OpenCVLoader.initDebug()) {
                    cameraView.enableView();
                }
            } else {
                // Si no se concede el permiso, cerramos la actividad
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null) cameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) cameraView.disableView();
    }

    // === OpenCV Camera Callbacks ===

    @Override
    public void onCameraViewStarted(int width, int height) {
        gray = new Mat();
        circles = new Mat();
        rotated = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        gray.release();
        circles.release();
        rotated.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();

        // Corregir orientación para que el eje X y Y coincidan con el movimiento real de la cámara
        Core.rotate(rgba, rotated, Core.ROTATE_90_CLOCKWISE);

        if (gray.empty() || gray.rows() != rotated.rows() || gray.cols() != rotated.cols()) {
            gray.release();
            gray = new Mat(rotated.size(), CvType.CV_8UC1);
        }

        Imgproc.cvtColor(rotated, gray, Imgproc.COLOR_RGBA2GRAY);

        Imgproc.GaussianBlur(gray, gray, new Size(9, 9), 2, 2);

        // Detectar círculos (monedas)
        Imgproc.HoughCircles(
                gray,
                circles,
                Imgproc.CV_HOUGH_GRADIENT,
                1.5,
                80,
                100,
                40,
                30,   // minRadius
                200   // maxRadius
        );

        double totalAmount = 0.0;
        resetCoinCounts();

        // Dibujar círculos detectados y etiquetar denominaciones
        if (circles.cols() > 0) {
            for (int i = 0; i < circles.cols(); i++) {
                double[] data = circles.get(0, i);
                if (data == null) continue;

                Point center = new Point(data[0], data[1]);
                double radius = data[2];

                CoinType matched = findMatchingCoin(radius);
                if (matched != null) {
                    Imgproc.circle(rotated, center, (int) radius, CIRCLE_COLOR, 4);
                    Imgproc.putText(
                            rotated,
                            matched.name,
                            new Point(center.x - 30, center.y - 10),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.7,
                            LABEL_COLOR,
                            2
                    );

                    matched.count++;
                    totalAmount += matched.value;
                } else {
                    Imgproc.circle(rotated, center, (int) radius, UNKNOWN_CIRCLE_COLOR, 4);
                    Imgproc.putText(
                            rotated,
                            "?",
                            new Point(center.x - 10, center.y - 10),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.7,
                            UNKNOWN_CIRCLE_COLOR,
                            2
                    );
                }
            }
        }

        final double finalTotalAmount = totalAmount;
        runOnUiThread(() -> tvInfo.setText(buildSummary(finalTotalAmount)));

        return rotated;
    }

    private List<CoinType> initializeCoinTypes() {
        List<CoinType> list = new ArrayList<>();
        list.add(new CoinType("50 COP", 50.0, 82.6, 64.4));
        list.add(new CoinType("100 COP", 100.0, 86.2, 75.2));
        list.add(new CoinType("200 COP", 200.0, 91.6, 85.0));
        list.add(new CoinType("500 COP", 500.0, 89.0, 87.4));
        list.add(new CoinType("1000 COP", 1000.0, 100.8, 100.8));
        return list;
    }

    private void resetCoinCounts() {
        for (CoinType coin : coinTypes) {
            coin.count = 0;
        }
    }

    private CoinType findMatchingCoin(double detectedRadius) {
        CoinType bestMatch = null;
        double bestDifference = Double.MAX_VALUE;

        for (CoinType coin : coinTypes) {
            double[] candidateRadii = {coin.radiusOld, coin.radiusNew};
            for (double candidateRadius : candidateRadii) {
                double difference = Math.abs(detectedRadius - candidateRadius);
                double margin = candidateRadius * TOLERANCE_PERCENTAGE;

                if (difference <= margin && difference < bestDifference) {
                    bestDifference = difference;
                    bestMatch = coin;
                }
            }
        }

        return bestMatch;
    }

    private String buildSummary(double totalAmount) {
        StringBuilder builder = new StringBuilder();

        for (CoinType coin : coinTypes) {
            if (coin.count > 0) {
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append(String.format(Locale.getDefault(), "%s = %dx", coin.name, coin.count));
            }
        }

        if (builder.length() > 0) {
            builder.append("  ");
        }
        builder.append(String.format(Locale.getDefault(), "Total: %.0f COP", totalAmount));

        return builder.toString();
    }

    private static class CoinType {
        String name;
        double value;
        double radiusOld;
        double radiusNew;
        int count;

        CoinType(String name, double value, double radiusOld, double radiusNew) {
            this.name = name;
            this.value = value;
            this.radiusOld = radiusOld;
            this.radiusNew = radiusNew;
            this.count = 0;
        }
    }
}
