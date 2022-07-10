package com.example.democamera2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;

import com.example.democamera2.utils.PermissionUtils;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class CameraCaptureSessionsAndRequestsActivity extends AppCompatActivity {

    private static final String TAG = CameraCaptureSessionsAndRequestsActivity.class.getSimpleName();

    private static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
    };
    private static final int REQUEST_CODE_REQUEST_PERMISSION = 0;
    private static final int REQUEST_CODE_REQUEST_PERMISSION_RETRY_TIMES_LIMIT = 3;

    private static final int IMAGE_READER_WIDTH = 600;
    private static final int IMAGE_READER_HEIGHT = 400;
    private static final int IMAGE_READER_FORMAT = PixelFormat.JPEG; // ImageFormat.JPEG;
    private static final int IMAGE_READER_MAX_IMAGES = 2;

    private Spinner sCamera;
    private SurfaceView svCamera;
    private ImageReader imageReader;
    private Button btnCreateCaptureRequest;
    private Button btnSetRepeatingRequest;
    private Button btnAbortCaptures;
    private ImageView ivCamera;
    private Button btnCapture;

    private CameraManager cameraManager;
    private String[] cameraIds;
    private String curCameraId;
    private CameraDevice curCameraDevice;
    private CameraCaptureSession curCameraCaptureSession;
    private CameraDevice.StateCallback cameraDeviceStateCallback;
    private CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback;
    private CameraCaptureSession.CaptureCallback cameraCaptureSessionCaptureCallback;
    private ImageReader.OnImageAvailableListener imageReaderOnImageAvailableListener;

    private CameraHandler<Activity> handler;

    private int requestPermissionsRetryTimes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture_sessions_and_requests);

        findViews();
        setViewsListeners();
        initEnvironment();
        PermissionUtils.checkAndRequestPermissions(this, PERMISSIONS, REQUEST_CODE_REQUEST_PERMISSION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        cleanEnvironment();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        requestPermissionsRetryTimes++;
        permissions = PermissionUtils.getDeniedPermissionsFromOnRequestPermissionsResult(permissions, grantResults);
        if (null == permissions || permissions.length == 0) {
            return;
        }
        if (requestPermissionsRetryTimes >= REQUEST_CODE_REQUEST_PERMISSION_RETRY_TIMES_LIMIT) {
            finish();
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_REQUEST_PERMISSION);
        }
    }

    private void findViews() {
        sCamera = findViewById(R.id.s_cameras);
        svCamera = findViewById(R.id.sv_camera);
        imageReader = ImageReader.newInstance(IMAGE_READER_WIDTH, IMAGE_READER_HEIGHT, IMAGE_READER_FORMAT, IMAGE_READER_MAX_IMAGES);
        btnCreateCaptureRequest = findViewById(R.id.btn_create_capture_request);
        btnSetRepeatingRequest = findViewById(R.id.btn_set_repeating_request);
        btnAbortCaptures = findViewById(R.id.btn_abort_captures);
        ivCamera = findViewById(R.id.iv_camera);
        btnCapture = findViewById(R.id.btn_capture);
    }

    private void setViewsListeners() {
        btnCreateCaptureRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createCaptureRequest(curCameraCaptureSession);
            }
        });
        btnSetRepeatingRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRepeatingRequest(curCameraCaptureSession);
            }
        });
        btnAbortCaptures.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abortCaptures(curCameraCaptureSession);
            }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capture(curCameraCaptureSession);
            }
        });
        sCamera.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "onItemSelected: parent = " + parent + ", view = " + view + ", position = " + position + ", id = " + id);
                if (null == cameraIds || position >= cameraIds.length) {
                    return;
                }
                curCameraId = cameraIds[position];
                openCameraDevice(curCameraId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i(TAG, "onNothingSelected: parent = " + parent);
            }
        });
    }

    private void initEnvironment() {
        Log.i(TAG, "initEnvironment: ");
        handler = new CameraHandler<>(this);
        cameraManager = ActivityCompat.getSystemService(this, CameraManager.class);
        getCameraIds();

        cameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.i(TAG, "CameraDevice.StateCallback. onOpened: camera = " + camera);
                curCameraDevice = camera;
                handler.sendEmptyMessage(CameraHandler.MSG_WHAT_CAMERA_DEVICE_OPENED);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Log.i(TAG, "CameraDevice.StateCallback. onDisconnected: camera = " + camera);
                camera.close();
                curCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Log.i(TAG, "CameraDevice.StateCallback. onError: camera = " + camera + ", error = " + error);
                String errorString = null;
                switch (error) {
                    case ERROR_CAMERA_IN_USE:
                        errorString = "ERROR_CAMERA_IN_USE: indicating that the camera device is in use already.";
                        break;
                    case ERROR_MAX_CAMERAS_IN_USE:
                        errorString = "ERROR_MAX_CAMERAS_IN_USE: indicating that the camera device could not be opened because there are too many other open camera devices.";
                        break;
                    case ERROR_CAMERA_DISABLED:
                        errorString = "ERROR_CAMERA_DISABLED: indicating that the camera device could not be opened due to a device policy";
                        break;
                    case ERROR_CAMERA_DEVICE:
                        errorString = "ERROR_CAMERA_DEVICE: indicating that the camera device has encountered a fatal error";
                        break;
                    case ERROR_CAMERA_SERVICE:
                        errorString = "ERROR_CAMERA_SERVICE： indicating that the camera service has encountered a fatal error";
                        break;
                    default:
                        break;
                }
                Log.i(TAG, "CameraDevice.StateCallback. onError: error message is " + errorString);
            }
        };
        cameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.i(TAG, "CameraCaptureSession.StateCallback. onConfigured: session = " + session);
                CameraCaptureSessionsAndRequestsActivity.this.curCameraCaptureSession = session;
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.i(TAG, "CameraCaptureSession.StateCallback. onConfigureFailed: session = " + session);
            }

            @Override
            public void onReady(@NonNull CameraCaptureSession session) {
                super.onReady(session);
                Log.i(TAG, "CameraCaptureSession.StateCallback. onReady: session = " + session);
            }

            @Override
            public void onActive(@NonNull CameraCaptureSession session) {
                super.onActive(session);
                Log.i(TAG, "CameraCaptureSession.StateCallback. onActive: session = " + session);
            }

            @Override
            public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
                super.onCaptureQueueEmpty(session);
                Log.i(TAG, "CameraCaptureSession.StateCallback. onCaptureQueueEmpty: session = " + session);
            }

            @Override
            public void onClosed(@NonNull CameraCaptureSession session) {
                super.onClosed(session);
                Log.i(TAG, "CameraCaptureSession.StateCallback. onClosed: session = " + session);
                session.close();
                curCameraCaptureSession = null;
            }

            @Override
            public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                super.onSurfacePrepared(session, surface);
                Log.i(TAG, "CameraCaptureSession.StateCallback. onSurfacePrepared: session = " + session + ", surface = " + surface);
            }
        };
        cameraCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureStarted: session = " + session + ", request = " + request + ", timestamp = " + timestamp + ", frameNumber = " + frameNumber);
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureProgressed: session = " + session + ", request = " + request + ", partialResult = " + partialResult);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureCompleted: session = " + session + ", request = " + request + ", result = " + result);
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureFailed: session = " + session + ", request = " + request + ", failure = " + failure+". " + failure.getReason());
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureSequenceCompleted: session = " + session + ", sequenceId = " + sequenceId + ", frameNumber = " + frameNumber);
            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                super.onCaptureSequenceAborted(session, sequenceId);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureSequenceAborted: session = " + session + ", sequenceId = " + sequenceId);
            }

            @Override
            public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                super.onCaptureBufferLost(session, request, target, frameNumber);
                Log.i(TAG, "CameraCaptureSession.CaptureCallback. onCaptureBufferLost: session = " + session + ", request = " + request + ", target = " + target + ", frameNumber = " + frameNumber);
            }
        };
        imageReaderOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.i(TAG, "onImageAvailable: reader = " + reader);
                Image image = reader.acquireNextImage();
                if(null==image){
                    return;
                }
                Image.Plane[] planes = image.getPlanes();
                if (null == planes || planes.length == 0) {
                    Log.w(TAG, "onImageAvailable: planes is empty");
                    image.close();
                    return;
                }
                Image.Plane plane = planes[0];
                if (null == plane) {
                    Log.w(TAG, "onImageAvailable: plane is null");
                    image.close();
                    return;
                }
                ByteBuffer buffer = plane.getBuffer();
                if (null == buffer) {
                    Log.w(TAG, "onImageAvailable: buffer is null");
                    image.close();
                    return;
                }
                byte[] byteArr = new byte[buffer.remaining()];
                buffer.get(byteArr);
                if (null == byteArr || byteArr.length == 0) {
                    Log.w(TAG, "onImageAvailable: byte array is empty");
                    image.close();
                    return;
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArr, 0, byteArr.length, null);
                ivCamera.setImageBitmap(bitmap);

                image.close();
            }
        };
    }

    private void cleanEnvironment() {
        Log.i(TAG, "cleanEnvironment: ");

        closeCurrentCamera();
    }

    private void getCameraIds() {
        Log.i(TAG, "getCameraIds: ");
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            Log.i(TAG, "getCameraIds: " + Arrays.toString(cameraIds));
            Message msg = handler.obtainMessage(CameraHandler.MSG_WHAT_CAMERA_IDS_GOT);
            Bundle data = msg.getData();
            data.putStringArray(CameraHandler.EXTRA_CAMERA_IDS, cameraIds);
            handler.sendMessage(msg);
        } catch (CameraAccessException e) {
            Log.e(TAG, "getCameraIds: ", e);
        }
    }

    private void openCameraDevice(String cameraId) {
        Log.i(TAG, "openCameraDevice: cameraId = " + cameraId);
        if (null == cameraId) {
            return;
        }

        closeCurrentCamera();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        try {
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCameraDeviceAndCaptureSession: ", e);
        }
    }


    private void createCaptureSession(CameraDevice cameraDevice) {
        Log.i(TAG, "createCaptureSession: cameraDevice = " + cameraDevice);
        if (null == cameraDevice) {
            return;
        }
        // https://developer.android.google.cn/training/camera2/capture-sessions-requests#create-camera-capture-session

        // Remember to call this only *after* SurfaceHolder.Callback.surfaceCreated()
        Surface previewSurface = svCamera.getHolder().getSurface();
        Surface imageSurface = imageReader.getSurface();
        List<Surface> targets = Arrays.asList(previewSurface, imageSurface);

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        try {
            cameraDevice.createCaptureSession(targets, cameraCaptureSessionStateCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createAndCaptureSession: ", e);
        }
    }


    private void createCaptureRequest(CameraCaptureSession session) {
        Log.i(TAG, "createCaptureRequest: session = " + session);
        if (null == session) {
            return;
        }

        // https://developer.android.google.cn/training/camera2/capture-sessions-requests#single-capture-requests
        try {
            CaptureRequest.Builder captureRequest = session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequest.addTarget(svCamera.getHolder().getSurface());

            // The first null argument corresponds to the capture callback, which you should
            // provide if you want to retrieve frame metadata or keep track of failed
            // capture
            // requests that could indicate dropped frames; the second null argument
            // corresponds to the Handler used by the asynchronous callback, which will fall
            // back to the current thread's looper if null
            int captureSequenceId = session.capture(captureRequest.build(), cameraCaptureSessionCaptureCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureRequest: ", e);
        }
    }

    private void setRepeatingRequest(CameraCaptureSession session) {
        Log.i(TAG, "setRepeatingRequest: session = " + session);
        if (null == session) {
            return;
        }

        try {
            session.stopRepeating();
        } catch (CameraAccessException e) {
            Log.e(TAG, "setRepeatingRequest: ", e);
        }

        try {
            CaptureRequest.Builder captureRequest = session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequest.addTarget(svCamera.getHolder().getSurface());

            // https://developer.android.google.cn/training/camera2/capture-sessions-requests#repeating-capture-requests
            // This will keep sending the capture request as frequently as possible until the
            // session is torn down or session.stopRepeating() is called
            // session.setRepeatingRequest(captureRequest.build(), null, null);
            int captureSequenceId = session.setRepeatingRequest(captureRequest.build(), cameraCaptureSessionCaptureCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setRepeatingRequest: ", e);
        }
    }

    private void capture(CameraCaptureSession session) {
        Log.i(TAG, "capture: session = " + session);
        if (null == session) {
            return;
        }
        imageReader.setOnImageAvailableListener(imageReaderOnImageAvailableListener, handler);
        try {
            // https://developer.android.google.cn/training/camera2/capture-sessions-requests#interleaving-capture-requests
            // Create the single request and dispatch it
            // NOTE: This may disrupt the ongoing repeating request momentarily
            CaptureRequest.Builder singleRequest = session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            singleRequest.addTarget(svCamera.getHolder().getSurface());
            singleRequest.addTarget(imageReader.getSurface());
            int captureSequenceId = session.capture(singleRequest.build(), cameraCaptureSessionCaptureCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "capture: ", e);
        }
    }

    private void abortCaptures(CameraCaptureSession session) {
        Log.i(TAG, "abortCaptures: session = " + session);
        if (null == session) {
            return;
        }
        try {
            session.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e(TAG, "abortCaptures: ", e);
        }
    }

    private void closeCurrentCamera() {
        Log.i(TAG, "closeCurrentCamera: ");
        if (null != curCameraCaptureSession) {
            try {
                curCameraCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                Log.e(TAG, "closeCurrentCamera: ", e);
            }
            curCameraCaptureSession.close();
            curCameraCaptureSession = null;
        }
        if (null != curCameraDevice) {
            curCameraDevice.close();
            curCameraDevice = null;
        }
    }

    private void updateCamerasUI() {
        sCamera.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, cameraIds));
    }

    private class CameraHandler<H> extends Handler {

        private static final int MSG_WHAT_CAMERA_IDS_GOT = 1;
        private static final String EXTRA_CAMERA_IDS = "EXTRA_CAMERA_IDS";

        private static final int MSG_WHAT_CAMERA_DEVICE_OPENED = 2;


        private WeakReference<H> weakReference;

        public CameraHandler(H weakReference) {
            this.weakReference = new WeakReference<H>(weakReference);
        }

        public H getHolder() {
            return this.weakReference.get();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Log.i(TAG, "handleMessage: msg = " + msg);

            int what = msg.what;
            Bundle data = msg.peekData();
            if (what == MSG_WHAT_CAMERA_IDS_GOT) {
                if (null == data) {
                    Log.w(TAG, "handleMessage: null == data");
                    return;
                }
                String[] cameraIds = data.getStringArray(EXTRA_CAMERA_IDS);
                CameraCaptureSessionsAndRequestsActivity.this.cameraIds = cameraIds;
                closeCurrentCamera();
                updateCamerasUI();
            } else if (what == MSG_WHAT_CAMERA_DEVICE_OPENED) {
                createCaptureSession(CameraCaptureSessionsAndRequestsActivity.this.curCameraDevice);
            }
        }
    }
}