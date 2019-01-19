package com.libvlc.sample;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VideoActivity extends Activity {
    public final static String TAG = "LibVLC.VideoActivity";

    public final static String LOCATION = "com.libvlc.sample.VideoActivity.location";
    public final static String DISPLAY = "com.libvlc.sample.VideoActivity.display";

    private String mFilePath;

    private boolean useOpenGL = true;

    // display surface
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private GLSurfaceView mGLSurfaceView;
    private SurfaceHolder mGLSurfaceHolder;

    // media player
    private LibVLC libvlc;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private final static int VideoSizeChanged = -1;

    private int[] mVideoTextures = new int[1];
    private SurfaceTexture mVideoTexture;
    private boolean needUpdateSurface = false;
    private Surface mVideoSurface;

    /*************
     * Activity
     *************/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        // Receive path to play from intent
        Intent intent = getIntent();
        mFilePath = intent.getExtras().getString(LOCATION);
        useOpenGL = intent.getExtras().getBoolean(DISPLAY);

        Log.e(TAG, "Playing back " + mFilePath);

        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.glsurfaceview);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceview);

        if (useOpenGL) {
            mSurfaceView.setVisibility(View.GONE);
            mGLSurfaceView.setEGLContextClientVersion(2);
            mGLSurfaceView.setRenderer(new videoGLRenderer());

            mGLSurfaceHolder = mGLSurfaceView.getHolder();
            mGLSurfaceHolder.setKeepScreenOn(true);
        }
        else {
            mGLSurfaceView.setVisibility(View.GONE);
            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceView.setKeepScreenOn(true);
            mSurfaceHolder.addCallback(new videoRenderer());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    /*************
     * Surface
     *************/
    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        if(mSurfaceHolder == null || mSurfaceView == null)
            return;

        // get screen size
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        mSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = mSurfaceView.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurfaceView.setLayoutParams(lp);
        mSurfaceView.invalidate();
    }

    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create LibVLC
            // TODO: make this more robust, and sync with audio demo
            ArrayList<String> options = new ArrayList<String>();
            //options.add("--subsdec-encoding <encoding>");
            options.add("--audio-time-stretch"); // time stretching
            //options.add("--vout=android_display,none");
            options.add("-vvv"); // verbosity
            libvlc = new LibVLC(this, options);
            //libvlc.setOnHardwareAccelerationError(this);

            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);

            // Set up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            if (useOpenGL) {
                //vout.setVideoSurface(mVideoSurface, null);
                vout.setVideoSurface(mVideoTexture);
            }
            else {
                vout.setVideoView(mSurfaceView);
                //vout.setVideoSurface(mSurfaceView.getHolder().getSurface(), holder);
            }
            //vout.setSubtitlesView(mSurfaceSubtitles);
            //vout.addCallback(this);
        /*
            vout.attachViews(new IVLCVout.OnNewVideoLayoutListener() {
                @Override
                public void onNewVideoLayout(IVLCVout ivlcVout, int i, int i1, int i2, int i3, int i4, int i5) {
                    int sw, sh;
                    if (useOpenGL) {
                        sw = mGLSurfaceView.getWidth();
                        sh = mGLSurfaceView.getHeight();
                    }
                    else {
                        sw = mSurfaceView.getWidth();
                        sh = mSurfaceView.getHeight();
                    }
                    vout.setWindowSize(sw,sh);
                    mMediaPlayer.setAspectRatio(sw+":"+sh);
                    mMediaPlayer.setScale(0);
                }
            });
        */
            vout.attachViews();

            Media m = new Media(libvlc, media);
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(VideoActivity.this, "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    // TODO: handle this cleaner
    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        //vout.removeCallback(this);
        vout.detachViews();
        mGLSurfaceHolder = null;
        libvlc.release();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    /*************
     * Events
     *************/

    private MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);

    public class videoRenderer implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            VideoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    createPlayer(mFilePath);
                }
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }

    public class videoGLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
        public int mProgramId;
        public int mSTMatrixHandle;
        public int mMVPMatrixHandle;
        public int mTexureCoordHandle;
        public int mPositionHandle;
        private FloatBuffer mTexCoorBuffer;
        private FloatBuffer mVertexBuffer;

        private String vertexShaderCode =
                "uniform mat4 uSTMatrix;\n" +
                "uniform mat4 uMVPMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "    vTexCoord = (uSTMatrix * aTexCoord).xy;\n" +
                "    gl_Position = uMVPMatrix * aPosition;\n" +
                "}";

        private String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision highp float;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                    "gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
                "}";

        private final float mSTMatrix[] = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f,-1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
        };

        private final float mMVPMatrix[] = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f,
        };

        // s t 0 1
        private final float[] mTextureData = {
                0.0f, 0.0f, 0.0f, 1.0f, // left top
                0.0f, 1.0f, 0.0f, 1.0f, // left bottom
                1.0f, 1.0f, 0.0f, 1.0f, // right bottom
                1.0f, 0.0f, 0.0f, 1.0f, // right top
        };

        private final float[] mVerticesData = {
                -1.0f, -1.0f, 0.0f, 1.0f, // left top
                -1.0f,  1.0f, 0.0f, 1.0f, // left bottom
                1.0f,  1.0f, 0.0f, 1.0f, // right bottom
                1.0f, -1.0f, 0.0f, 1.0f, // right top
        };

        private float[] mSTMatrixTrans;

        private void initProgram () {
            mProgramId = OpenGLUtil.createProgram(vertexShaderCode, fragmentShaderCode);
            if (mProgramId == 0) {
                Log.i(TAG, "createProgram error!");
            }

            mSTMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uSTMatrix");
            mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix");

            mPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition");
            mTexureCoordHandle = GLES20.glGetAttribLocation(mProgramId, "aTexCoord");

            mTexCoorBuffer = ByteBuffer.allocateDirect(mTextureData.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTexCoorBuffer.put(mTextureData).position(0);

            mVertexBuffer = ByteBuffer.allocateDirect(mVerticesData.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVertexBuffer.put(mVerticesData).position(0);

            mSTMatrixTrans = new float[16];
        }

        @Override
        public void onFrameAvailable (SurfaceTexture surfaceTexture) {
            synchronized (this) {
                mGLSurfaceView.requestRender();
                needUpdateSurface = true;
            }
        }

        @Override
        public void onSurfaceCreated (GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

            GLES20.glDeleteTextures(1, mVideoTextures, 0);
            OpenGLUtil.genTexturesOES(mVideoTextures, 1);

            mVideoTexture = new SurfaceTexture(mVideoTextures[0]);
            mVideoTexture.setOnFrameAvailableListener(this);
            mVideoSurface = new Surface(mVideoTexture);

            synchronized (this) {
                needUpdateSurface = false;
            }

            initProgram();

            VideoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    createPlayer(mFilePath);
                }
            });
        }

        @Override
        public void onSurfaceChanged (GL10 gl,int width, int height){
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame (GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            synchronized (this) {
                if (needUpdateSurface) {
                    mVideoTexture.updateTexImage();
                    //mVideoTexture.getTransformMatrix(mSTMatrixTrans);
                    //Log.e(TAG, "mSTMatrixTrans:" + mSTMatrixTrans[0] + ", " + mSTMatrixTrans[1] + ", " + mSTMatrixTrans[2] + ", " + mSTMatrixTrans[3]);
                    //Log.e(TAG, "mSTMatrixTrans:" + mSTMatrixTrans[4] + ", " + mSTMatrixTrans[5] + ", " + mSTMatrixTrans[6] + ", " + mSTMatrixTrans[7]);
                    //Log.e(TAG, "mSTMatrixTrans:" + mSTMatrixTrans[8] + ", " + mSTMatrixTrans[9] + ", " + mSTMatrixTrans[10] + ", " + mSTMatrixTrans[11]);
                    //Log.e(TAG, "mSTMatrixTrans:" + mSTMatrixTrans[12] + ", " + mSTMatrixTrans[13] + ", " + mSTMatrixTrans[14] + ", " + mSTMatrixTrans[15]);
                    needUpdateSurface = false;
                }
            }

            GLES20.glUseProgram(mProgramId);

            //Matrix.setIdentityM(mSTMatrix, 0);
            GLES20.glUniformMatrix4fv(mSTMatrixHandle, 1, false, mSTMatrix, 0);
            //Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

            GLES20.glEnableVertexAttribArray(mTexureCoordHandle);
            GLES20.glVertexAttribPointer(mTexureCoordHandle, 4, GLES20.GL_FLOAT,
                    false, 0, mTexCoorBuffer);
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            GLES20.glVertexAttribPointer(mPositionHandle, 4, GLES20.GL_FLOAT,
                    false, 0, mVertexBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mVideoTextures[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
        }
    }

    private static class MyPlayerListener implements MediaPlayer.EventListener {
        private WeakReference<VideoActivity> mOwner;

        public MyPlayerListener(VideoActivity owner) {
            mOwner = new WeakReference<VideoActivity>(owner);
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            VideoActivity player = mOwner.get();

            switch(event.type) {
                case MediaPlayer.Event.EndReached:
                    Log.d(TAG, "MediaPlayerEndReached");
                    player.releasePlayer();
                    break;
                case MediaPlayer.Event.Playing:
                case MediaPlayer.Event.Paused:
                case MediaPlayer.Event.Stopped:
                default:
                    break;
            }
        }
    }

    //@Override
    public void eventHardwareAccelerationError() {
        // Handle errors with hardware acceleration
        Log.e(TAG, "Error with hardware acceleration");
        this.releasePlayer();
        Toast.makeText(this, "Error with hardware acceleration", Toast.LENGTH_LONG).show();
    }
}
