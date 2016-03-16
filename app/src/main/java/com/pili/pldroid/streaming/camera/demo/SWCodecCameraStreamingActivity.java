package com.pili.pldroid.streaming.camera.demo;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pili.pldroid.player.AVOptions;
import com.pili.pldroid.player.PlayerCode;
import com.pili.pldroid.player.widget.VideoView;
import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.CameraStreamingManager.EncodingType;
import com.pili.pldroid.streaming.CameraStreamingSetting;
import com.pili.pldroid.streaming.StreamStatusCallback;
import com.pili.pldroid.streaming.StreamingPreviewCallback;
import com.pili.pldroid.streaming.StreamingProfile;
import com.pili.pldroid.streaming.StreamingProfile.StreamStatus;
import com.pili.pldroid.streaming.StreamingProfile.StreamStatusConfig;
import com.pili.pldroid.streaming.SurfaceTextureCallback;
import com.pili.pldroid.streaming.widget.AspectFrameLayout;
import com.qiniu.android.dns.DnsManager;
import com.qiniu.android.dns.IResolver;
import com.qiniu.android.dns.NetworkInfo;
import com.qiniu.android.dns.http.DnspodFree;
import com.qiniu.android.dns.local.AndroidDnsServer;
import com.qiniu.android.dns.local.Resolver;

import java.io.IOException;
import java.net.InetAddress;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by jerikc on 15/10/29.
 */
public class SWCodecCameraStreamingActivity extends StreamingBaseActivity
        implements View.OnLayoutChangeListener,
        StreamStatusCallback,
        StreamingPreviewCallback,
        SurfaceTextureCallback,

        IjkMediaPlayer.OnInfoListener,
        IjkMediaPlayer.OnErrorListener,
        IjkMediaPlayer.OnPreparedListener {
    private static final String TAG = "SWCodecCameraStreaming";
    private StreamingProfile mProfile;
    private Context mContext;
    private View mRootView;

    private static final int REQ_DELAY_MILLS = 3000;

    private ViewGroup.LayoutParams mLayoutParams;
    private Pair<Integer, Integer> mScreenSize;

    private String mVideoPath = "rtmp://live.hkstv.hk.lxdns.com/live/hks";
    private boolean mIsLiveStream = false;
    private long mLastPosition = 0;

    private int mReqDelayMills = REQ_DELAY_MILLS;
    private boolean mIsCompleted = false;
    private Runnable mVideoReconnect;

    private VideoView mVideoView;
    private RelativeLayout rl_child;
    private RelativeLayout rl_parent;

    public static DnsManager getMyDnsManager() {
        IResolver r0 = new DnspodFree();
        IResolver r1 = AndroidDnsServer.defaultResolver();
        IResolver r2 = null;
        try {
            r2 = new Resolver(InetAddress.getByName("119.29.29.29"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return new DnsManager(NetworkInfo.normal, new IResolver[]{r0, r1, r2});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        super.onCreate(savedInstanceState);
        mContext = this;
        rl_parent = (RelativeLayout) findViewById(R.id.rl_parent);
        rl_child = (RelativeLayout) findViewById(R.id.rl_child);

        mRootView = findViewById(R.id.content);
        mRootView.addOnLayoutChangeListener(this);
        // player
        mVideoView = (VideoView) findViewById(R.id.video_view);

        AVOptions options = new AVOptions();
        options.setInteger(AVOptions.KEY_MEDIACODEC, 0); // 1 -> enable, 0 -> disable

        Log.i(TAG, "mIsLiveStream:" + mIsLiveStream);
        if (mIsLiveStream) {
            options.setInteger(AVOptions.KEY_BUFFER_TIME, 1000); // the unit of buffer time is ms
            options.setInteger(AVOptions.KEY_GET_AV_FRAME_TIMEOUT, 10 * 1000); // the unit of
            // timeout is ms
            options.setString(AVOptions.KEY_FFLAGS, AVOptions.VALUE_FFLAGS_NOBUFFER); // "nobuffer"
            options.setInteger(AVOptions.KEY_LIVE_STREAMING, 1);
        }

        mVideoView.setAVOptions(options);

        mVideoView.setVideoPath(mVideoPath);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnInfoListener(this);
        mVideoView.setOnPreparedListener(this);

        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        CameraPreviewFrameView cameraPreviewFrameView = (CameraPreviewFrameView) findViewById(R
                .id.cameraPreview_surfaceView);
        cameraPreviewFrameView.setListener(this);

        mShutterButton = (Button) findViewById(R.id.toggleRecording_button);

        mSatusTextView = (TextView) findViewById(R.id.streamingStatus);

        StreamingProfile.AudioProfile aProfile = new StreamingProfile.AudioProfile(44100, 96 *
                1024);
        StreamingProfile.VideoProfile vProfile = new StreamingProfile.VideoProfile(30, 1000 *
                1024, 48);
        StreamingProfile.AVProfile avProfile = new StreamingProfile.AVProfile(vProfile, aProfile);

        StreamingProfile.Stream stream = new StreamingProfile.Stream(mJSONObject);
        mProfile = new StreamingProfile();
        mProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_HIGH3)
                .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM2)
//                .setPreferredVideoEncodingSize(960, 544)
                .setEncodingSizeLevel(Config.ENCODING_LEVEL)
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.QUALITY_PRIORITY)
                .setStream(stream)
                .setAVProfile(avProfile)
                .setDnsManager(getMyDnsManager())
                .setStreamStatusConfig(new StreamStatusConfig(3))
                .setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT)
                .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000));

        CameraStreamingSetting setting = new CameraStreamingSetting();
        setting.setCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setResetTouchFocusDelayInMs(3000)
                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_4_3);

        mCameraStreamingManager = new CameraStreamingManager(this, afl, cameraPreviewFrameView,
                EncodingType.SW_VIDEO_WITH_SW_AUDIO_CODEC);  // soft codec
        mCameraStreamingManager.prepare(setting, mProfile);

        setFocusAreaIndicator();

        // update the StreamingProfile
//        mProfile.setStream(new Stream(mJSONObject1));
//        mCameraStreamingManager.setStreamingProfile(mProfile);
        mCameraStreamingManager.setStreamingStateListener(this);
        mCameraStreamingManager.setStreamingPreviewCallback(this);
        mCameraStreamingManager.setSurfaceTextureCallback(this);
        mCameraStreamingManager.setStreamingSessionListener(this);
        mCameraStreamingManager.setStreamStatusCallback(this);
//        mCameraStreamingManager.setNativeLoggingEnabled(false);


        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mShutterButtonPressed) {
                    stopStreaming();
                } else {
                    startStreaming();
                }
            }
        });

        EditText edittext = (EditText) findViewById(R.id.edittext);
        edittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {

                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        mHandler.removeCallbacksAndMessages(null);
        if (mVideoView != null) {
            mLastPosition = mVideoView.getCurrentPosition();
            mVideoView.pause();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int
            oldTop, int oldRight, int oldBottom) {
        Log.i(TAG, "view!!!!:" + v);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

    }

    @Override
    public boolean onPreviewFrame(byte[] bytes, int width, int height) {
//        deal with the yuv data.
//        long start = System.currentTimeMillis();
//        for (int i = 0; i < bytes.length; i++) {
//            bytes[i] = 0x00;
//        }
//        Log.i(TAG, "old onPreviewFrame cost :" + (System.currentTimeMillis() - start));
        return true;
    }

    @Override
    public void onSurfaceCreated() {
        Log.i(TAG, "onSurfaceCreated");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged width:" + width + ",height:" + height);
    }

    @Override
    public void onSurfaceDestroyed() {
        Log.i(TAG, "onSurfaceDestroyed");
    }

    @Override
    public int onDrawFrame(int texId, int texWidth, int texHeight) {
        // newTexId should not equal with texId. texId is from the SurfaceTexture.
        // Otherwise, there is no filter effect.
        int newTexId = texId;
//        Log.i(TAG, "onDrawFrame texId:" + texId + ",newTexId:" + newTexId + ",texWidth:" +
// texWidth + ",texHeight:" + texHeight);
        return newTexId;
    }

    @Override
    public void notifyStreamStatusChanged(final StreamStatus streamStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // TODO: 2016/3/16
            }
        });
    }


    @Override
    public void onStateChanged(final int state, Object extra) {
        super.onStateChanged(state, extra);
        switch (state) {
            case CameraStreamingManager.STATE.CAMERA_SWITCHED:
//                mShutterButtonPressed = false;
                if (extra != null) {
                    Log.i(TAG, "current camera id:" + (Integer) extra);
                }
                Log.i(TAG, "camera switched");
                break;
        }
    }

    @Override
    public boolean onStateHandled(final int state, Object extra) {
        super.onStateHandled(state, extra);
        switch (state) {
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_FEW_ITEMS:
                return false;
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_MANY_ITEMS:
                return false;
        }
        return false;
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onError what=" + what + ", extra=" + extra);
        if (what == -10000) {
            if (extra == PlayerCode.EXTRA_CODE_INVALID_URI || extra == PlayerCode.EXTRA_CODE_EOF) {
                return true;
            }
            if (mIsCompleted && extra == PlayerCode.EXTRA_CODE_EMPTY_PLAYLIST) {
                Log.d(TAG, "mVideoView reconnect!!!");
                mVideoView.removeCallbacks(mVideoReconnect);
                mVideoReconnect = new Runnable() {
                    @Override
                    public void run() {
                        mVideoView.setVideoPath(mVideoPath);
                    }
                };
                mVideoView.postDelayed(mVideoReconnect, mReqDelayMills);
                mReqDelayMills += 200;
            } else if (extra == PlayerCode.EXTRA_CODE_404_NOT_FOUND) {
                // NO ts exist
            } else if (extra == PlayerCode.EXTRA_CODE_IO_ERROR) {
                // NO rtmp stream exist
            }
        }
        // return true means you handle the onError, hence System wouldn't handle it again(popup
        // a dialog).
        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onInfo what=" + what + ", extra=" + extra);


        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            Log.i(TAG, "onInfo: (MEDIA_INFO_BUFFERING_START)");
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            Log.i(TAG, "onInfo: (MEDIA_INFO_BUFFERING_END)");
        } else if (what == IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START) {
            Toast.makeText(this, "Audio Start", Toast.LENGTH_LONG).show();
            Log.i(TAG, "duration:" + mVideoView.getDuration());
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            Toast.makeText(this, "Video Start", Toast.LENGTH_LONG).show();
            Log.i(TAG, "duration:" + mVideoView.getDuration());
        }
        rl_parent.bringChildToFront(rl_child);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        Log.d(TAG, "onPrepared");
        mReqDelayMills = REQ_DELAY_MILLS;
    }

    @Override
    public void onResume() {
        super.onResume();
        mReqDelayMills = REQ_DELAY_MILLS;
        if (mVideoView != null && !mIsLiveStream && mLastPosition != 0) {
            mVideoView.seekTo(mLastPosition);
            mVideoView.start();
        }
    }
}
