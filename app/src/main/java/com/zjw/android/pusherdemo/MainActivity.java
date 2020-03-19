package com.zjw.android.pusherdemo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;

import org.easydarwin.push.MediaStream;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;

/**
 * 主页面，提供摄像头和屏幕推送功能.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1000;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaStream mediaStream;

    private Single<MediaStream> getMediaStream() {
        Single<MediaStream> single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mediaStream);
        if (mediaStream == null) {
            return single.doOnSuccess(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream ms) throws Exception {
                    mediaStream = ms;
                }
            });
        } else {
            return single;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 启动服务...
        Intent intent = new Intent(this, MediaStream.class);
        startService(intent);

        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(final MediaStream ms) throws Exception {
                ms.observeCameraPreviewResolution(MainActivity.this, new Observer<int[]>() {
                    @Override
                    public void onChanged(@Nullable int[] size) {
                        Toast.makeText(MainActivity.this, "当前摄像头分辨率为:" + size[0] + "*" + size[1], Toast.LENGTH_SHORT).show();
                    }
                });
                final TextView pushingStateText = findViewById(R.id.pushing_state);
                final TextView pushingBtn = findViewById(R.id.pushing);
                ms.observePushingState(MainActivity.this, new Observer<MediaStream.PushingState>() {

                    @Override
                    public void onChanged(@Nullable MediaStream.PushingState pushingState) {
                        if (pushingState.screenPushing) {
                            pushingStateText.setText("屏幕推送");

                            // 更改屏幕推送按钮状态.

                            TextView tview = findViewById(R.id.pushing_desktop);
                            if (pushingState.state > 0) {
                                tview.setText("取消推送");
                            } else {
                                tview.setText("推送屏幕");
                            }
                            findViewById(R.id.pushing_desktop).setEnabled(true);
                        } else {
                            pushingStateText.setText("推送");

                            if (pushingState.state > 0) {
                                pushingBtn.setText("停止");
                            } else {
                                pushingBtn.setText("推送");
                            }

                        }
                        pushingStateText.append(":\t" + pushingState.msg);
                        if (pushingState.state > 0) {
                            pushingStateText.append(pushingState.url);
                        }

                    }
                });
                TextureView textureView = findViewById(R.id.texture_view);
                textureView.setSurfaceTextureListener(new SurfaceTextureListenerWrapper() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                        ms.setSurfaceTexture(surfaceTexture);
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        ms.setSurfaceTexture(null);
                        return true;
                    }
                });


                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION);
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Toast.makeText(MainActivity.this, "创建服务出错!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onPushing(View view) {
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(MediaStream mediaStream) throws Exception {
                MediaStream.PushingState state = mediaStream.getPushingState();
                if (state != null && state.state > 0) { // 终止推送和预览
                    mediaStream.stopStream();
                    mediaStream.closeCameraPreview();
                } else {                                // 启动预览和推送.
                    mediaStream.openCameraPreview();
                    String id = "12345";
                    mediaStream.startStream("cloud.easydarwin.org", "554", id);
                }
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {
                if (grantResults.length > 1
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    getMediaStream().subscribe(new Consumer<MediaStream>() {
                        @Override
                        public void accept(MediaStream mediaStream) throws Exception {
                            mediaStream.notifyPermissionGranted();
                        }
                    });
                } else {
                    finish();
                }
                break;
            }
        }
    }

    // 推送屏幕.
    public void onPushScreen(final View view) {
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(MediaStream mediaStream) throws Exception {
                MediaStream.PushingState state = mediaStream.getScreenPushingState();
                if (state != null && state.state > 0) {
                    // 取消推送。
                    mediaStream.stopPushScreen();
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return;
                    }
                    MediaProjectionManager mMpMngr = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mMpMngr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
                    // 防止点多次.
                    view.setEnabled(false);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            getMediaStream().subscribe(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream mediaStream) throws Exception {
                    mediaStream.pushScreen(resultCode, data, "cloud.easydarwin.org", "554", "screen");
                }
            });
        }
    }

}
