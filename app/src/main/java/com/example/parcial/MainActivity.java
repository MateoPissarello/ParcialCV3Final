package com.example.parcial;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";

    private CameraBridgeViewBase cameraView;
    private Mat gray, circles;

    private final int CAMERA_PERMISSION = 100;
    private boolean hasCameraPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

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
        gray = new Mat(height, width, CvType.CV_8UC1);
        circles = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        gray.release();
        circles.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

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

        // Dibujar círculos detectadosssssss
        if (circles.cols() > 0) {
            for (int i = 0; i < circles.cols(); i++) {
                double[] data = circles.get(0, i);
                if (data == null) continue;

                Point center = new Point(data[0], data[1]);
                double radius = data[2];

                Imgproc.circle(rgba, center, (int) radius, new Scalar(0, 255, 0, 255), 4);
                Imgproc.circle(rgba, center, 5, new Scalar(255, 0, 0, 255), 5);
            }
        }

        return rgba;
    }
}
