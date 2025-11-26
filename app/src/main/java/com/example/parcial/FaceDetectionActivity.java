package com.example.parcial;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FaceDetectionActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "FaceDetectionActivity";
    private static final Scalar FACE_COLOR = new Scalar(0, 255, 0, 255);

    private static final int CAMERA_PERMISSION = 200;

    private CameraBridgeViewBase cameraView;
    private TextView tvFaceInfo;
    private Mat rgba;
    private Mat gray;
    private Mat display;
    private CascadeClassifier faceClassifier;
    private boolean hasCameraPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_face_detection);

        tvFaceInfo = findViewById(R.id.tvFaceInfo);
        cameraView = findViewById(R.id.faceCameraView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        cameraView.setCvCameraViewListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION);
        } else {
            hasCameraPermission = true;
        }

        tvFaceInfo.setText("Cargando clasificador de rostro...");
        loadCascadeAsync();
    }

    private void loadCascadeAsync() {
        new Thread(() -> {
            try {
                File cascadeDir = getDir("cascade", MODE_PRIVATE);
                File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");

                if (!cascadeFile.exists()) {
                    downloadCascade(cascadeFile);
                }

                CascadeClassifier classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
                if (!classifier.empty()) {
                    faceClassifier = classifier;
                    runOnUiThread(() -> tvFaceInfo.setText("Clasificador cargado. Rostros: 0"));
                } else {
                    Log.e(TAG, "El clasificador está vacío tras la carga");
                }
            } catch (IOException e) {
                Log.e(TAG, "No se pudo cargar el clasificador de rostros", e);
                runOnUiThread(() -> tvFaceInfo.setText("Error al cargar clasificador"));
            }
        }).start();
    }

    private void downloadCascade(File cascadeFile) throws IOException {
        String url = "https://raw.githubusercontent.com/opencv/opencv/4.x/data/haarcascades/haarcascade_frontalface_default.xml";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Respuesta HTTP " + connection.getResponseCode());
        }

        try (InputStream is = connection.getInputStream();
             FileOutputStream os = new FileOutputStream(cascadeFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission && OpenCVLoader.initDebug()) {
            cameraView.setCameraPermissionGranted();
            cameraView.enableView();
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
        if (rgba != null) rgba.release();
        if (gray != null) gray.release();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            hasCameraPermission = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (hasCameraPermission && OpenCVLoader.initDebug()) {
                cameraView.setCameraPermissionGranted();
                cameraView.enableView();
            } else if (!hasCameraPermission) {
                finish();
            }
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        rgba = new Mat(height, width, CvType.CV_8UC4);
        gray = new Mat(height, width, CvType.CV_8UC1);
        display = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        if (rgba != null) rgba.release();
        if (gray != null) gray.release();
        if (display != null) display.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        rgba = inputFrame.rgba();
        if (display == null || display.empty() || display.rows() != rgba.cols() || display.cols() != rgba.rows()) {
            if (display != null) display.release();
            display = new Mat(rgba.cols(), rgba.rows(), CvType.CV_8UC4);
        }

        // Orientar para vertical y espejar la cámara frontal
        Core.rotate(rgba, display, Core.ROTATE_90_CLOCKWISE);
        // Corregir inversión vertical y mantener efecto espejo horizontal
        Core.flip(display, display, 0);
        Core.flip(display, display, 1);

        if (gray == null || gray.rows() != display.rows() || gray.cols() != display.cols()) {
            if (gray != null) gray.release();
            gray = new Mat(display.size(), CvType.CV_8UC1);
        }

        Imgproc.cvtColor(display, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.equalizeHist(gray, gray);

        if (faceClassifier == null) {
            runOnUiThread(() -> tvFaceInfo.setText("Cargando clasificador..."));
            return display;
        }

        MatOfRect faces = new MatOfRect();
        faceClassifier.detectMultiScale(
                gray,
                faces,
                1.1,
                3,
                0,
                new org.opencv.core.Size(80, 80),
                new org.opencv.core.Size()
        );

        Rect[] faceArray = faces.toArray();
        for (Rect face : faceArray) {
            Imgproc.rectangle(display, face.tl(), face.br(), FACE_COLOR, 3);
        }

        final int faceCount = faceArray.length;
        runOnUiThread(() -> tvFaceInfo.setText("Rostros: " + faceCount));

        return display;
    }
}
