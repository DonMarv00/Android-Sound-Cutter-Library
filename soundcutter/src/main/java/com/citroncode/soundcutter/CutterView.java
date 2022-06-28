package com.citroncode.soundcutter;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import com.citroncode.soundcutter.soundfile.SoundFile;
import com.citroncode.soundcutter.utils.SamplePlayer;
import com.citroncode.soundcutter.utils.SingleMediaScanner;
import com.citroncode.soundcutter.utils.Utility;
import com.citroncode.soundcutter.views.MarkerView;
import com.citroncode.soundcutter.views.WaveformView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hbisoft.pickit.PickiT;
import com.hbisoft.pickit.PickiTCallbacks;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.util.ArrayList;

public class CutterView extends FrameLayout implements  MarkerView.MarkerListener,
        WaveformView.WaveformListener, PickiTCallbacks {

    private long mLoadingLastUpdateTime;
    private boolean mLoadingKeepGoing;
    private SoundFile mSoundFile;
    private File mFile;
    String extension;
    private WaveformView mWaveformView;
    private MarkerView mStartMarker;
    boolean savedInternal;
    private MarkerView mEndMarker;
    private FloatingActionButton mPlayButton;
    private FloatingActionButton mRewindButton;
    private FloatingActionButton mFfwdButton;
    private boolean mKeyDown;
    Activity activityGlobal;
    private int mWidth;
    private Thread mSaveSoundFileThread;
    private int mMaxPos;
    private int mStartPos;
    private int mEndPos;
    private boolean mStartVisible;
    private boolean mEndVisible;
    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayStartMsec;
    private int mPlayEndMsec;
    private Handler mHandler;
    public boolean isLoading;
    private boolean mIsPlaying;
    private SamplePlayer mPlayer;
    private boolean mTouchDragging;
    private float mTouchStart;
    private int mTouchInitialOffset;
    private int mTouchInitialStartPos;
    private int mTouchInitialEndPos;
    private long mWaveformTouchStartMsec;
    private float mDensity;
    private int mMarkerLeftInset;
    private int mMarkerRightInset;
    private int mMarkerTopOffset;
    private int mMarkerBottomOffset;
    private Thread mLoadSoundFileThread;
    LinearLayout layoutControls;
    Context ctx;

    boolean saved;
    MarkerView markerLeft, markerRight;
    WaveformView  waveformView;
    ProgressBar pg_loading_progress;
    TextView tv_progress;

    //PickIt is used to get the real file path from the provided uri!
    PickiT pickiT;

    public CutterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);


        initView();
    }

    public CutterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public CutterView(Context context) {
        super(context);
        initView();
    }

    public void setSound(Uri soundfile, Context context, Activity activity){
       isLoading = true;
        pickiT = new PickiT(activity, this, activity);
        ctx = context;
        activityGlobal = activity;

        pickiT.getPath(soundfile, Build.VERSION.SDK_INT);


        afterSetSound();
    }
    public void afterSetSound(){
        pg_loading_progress.setVisibility(VISIBLE);
        tv_progress.setVisibility(VISIBLE);


        mPlayer = null;
        mIsPlaying = false;
        mLoadSoundFileThread = null;


        mSoundFile = null;
        mKeyDown = false;
        mHandler = new Handler();

        DisplayMetrics metrics = new DisplayMetrics();
        mDensity = metrics.density;



        mMarkerLeftInset = (int)(46 * mDensity);
        mMarkerRightInset = (int)(48 * mDensity);
        mMarkerTopOffset = (int)(10* mDensity);
        mMarkerBottomOffset = (int)(10 * mDensity);

        mPlayButton = findViewById(R.id.play);
        mPlayButton.setOnClickListener(mPlayListener);
        mRewindButton = findViewById(R.id.rew);
        mRewindButton.setOnClickListener(mRewindListener);
        mFfwdButton = findViewById(R.id.ffwd);
        mFfwdButton.setOnClickListener(mFfwdListener);

        enableDisableButtons();

        mWaveformView = findViewById(R.id.waveform);
        mWaveformView.setListener(this);

        mMaxPos = 0;


        if (mSoundFile != null && !mWaveformView.hasSoundFile()) {
            mWaveformView.setSoundFile(mSoundFile);
            mWaveformView.recomputeHeights(mDensity);
            mMaxPos = mWaveformView.maxPos();
        }

        mStartMarker = findViewById(R.id.startmarker);
        mStartMarker.setListener(this);
        mStartMarker.setAlpha(1f);
        mStartMarker.setFocusable(true);
        mStartMarker.setFocusableInTouchMode(true);
        mStartVisible = true;

        mEndMarker = findViewById(R.id.endmarker);
        mEndMarker.setListener(this);
        mEndMarker.setAlpha(1f);
        mEndMarker.setFocusable(true);
        mEndMarker.setFocusableInTouchMode(true);
        mEndVisible = true;

        updateDisplay();
    }

    private void initView() {
        View v = inflate(getContext(), R.layout.cutterview, this);

        tv_progress = v.findViewById(R.id.tv_progress);
        pg_loading_progress = v.findViewById(R.id.pg_load_sound);
        markerLeft = v.findViewById(R.id.startmarker);
        markerRight = v.findViewById(R.id.endmarker);
        waveformView = v.findViewById(R.id.waveform);
        layoutControls = v.findViewById(R.id.ll_btns);
    }
    private void loadFromFile(Uri uri) {
        mFile = new File(uri.getPath());


        mLoadingLastUpdateTime = getCurrentTime();
        mLoadingKeepGoing = true;

        final SoundFile.ProgressListener listener =
                fractionComplete -> {
                    long now = getCurrentTime();
                    long percentage = now - mLoadingLastUpdateTime;
                    if (percentage > 100) {
                        mLoadingLastUpdateTime = now;
                    }
                    return mLoadingKeepGoing;
                };


        mLoadSoundFileThread = new Thread() {
            public void run() {
                try {
                    mSoundFile = SoundFile.create(mFile.getAbsolutePath(), listener);

                    if (mSoundFile == null) {
                        String name = mFile.getName().toLowerCase();
                        String[] components = name.split("\\.");
                        if (components.length < 2) {
                            toastMsg("read error");
                        } else {
                            toastMsg("read error");
                        }
                        return;
                    }
                    mPlayer = new SamplePlayer(mSoundFile);
                } catch (final Exception e) {

                    e.printStackTrace();

                    Runnable runnable = () -> {
                        toastMsg(e.toString());
                        Log.e("SoundCutter",e.getMessage());
                    };
                    mHandler.post(runnable);

                    return;
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    tv_progress.setVisibility(GONE);
                    pg_loading_progress.setVisibility(GONE);
                    markerLeft.setVisibility(VISIBLE);
                    markerRight.setVisibility(VISIBLE);
                    waveformView.setVisibility(VISIBLE);
                    layoutControls.setVisibility(VISIBLE);

                    isLoading = false;
                });
                if (mLoadingKeepGoing) {
                    Runnable runnable = () -> finishOpeningSoundFile();
                    mHandler.post(runnable);
                }
            }
        };
        mLoadSoundFileThread.start();
    }

    private long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }
    private void toastMsg(String text){
        Toast.makeText(ctx, "Error: " + text, Toast.LENGTH_SHORT).show();
    }
    private void finishOpeningSoundFile() {
        mWaveformView.setSoundFile(mSoundFile);
        mWaveformView.recomputeHeights(mDensity);

        mMaxPos = mWaveformView.maxPos();
        mTouchDragging = false;

        mOffset = 0;
        mOffsetGoal = 0;
        mFlingVelocity = 0;
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;

        updateDisplay();
    }

    private synchronized void updateDisplay() {
        if (mIsPlaying) {
            int now = mPlayer.getCurrentPosition();
            int frames = mWaveformView.millisecsToPixels(now);
            mWaveformView.setPlayback(frames);
            setOffsetGoalNoUpdate(frames - mWidth / 2);
            if (now >= mPlayEndMsec) {
                handlePause();
            }
        }

        if (!mTouchDragging) {
            int offsetDelta;

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30;
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80;
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80;
                } else {
                    mFlingVelocity = 0;
                }

                mOffset += offsetDelta;

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2;
                    mFlingVelocity = 0;
                }
                if (mOffset < 0) {
                    mOffset = 0;
                    mFlingVelocity = 0;
                }
                mOffsetGoal = mOffset;
            } else {
                offsetDelta = mOffsetGoal - mOffset;

                if (offsetDelta > 10) {
                    offsetDelta = offsetDelta / 10;
                } else if (offsetDelta > 0) {
                    offsetDelta = 1;
                } else if (offsetDelta < -10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta < 0)
                    offsetDelta = -1;
                else
                    offsetDelta = 0;

                mOffset += offsetDelta;
            }
        }

        mWaveformView.setParameters(mStartPos, mEndPos, mOffset);
        mWaveformView.invalidate();

        mStartMarker.setContentDescription("StartMarker " +
                formatTime(mStartPos));
        mEndMarker.setContentDescription("Endmarker " +
                formatTime(mEndPos));

        int startX = mStartPos - mOffset - mMarkerLeftInset;
        if (startX + mStartMarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(() -> {
                    mStartVisible = true;
                    mStartMarker.setAlpha(1f);
                }, 0);
            }
        } else {
            if (mStartVisible) {
                mStartMarker.setAlpha(0f);
                mStartVisible = false;
            }
            startX = 0;
        }

        int endX = mEndPos - mOffset - mEndMarker.getWidth() + mMarkerRightInset;
        if (endX + mEndMarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(() -> {
                    mEndVisible = true;
                    mEndMarker.setAlpha(1f);
                }, 0);
            }
        } else {
            if (mEndVisible) {
                mEndMarker.setAlpha(0f);
                mEndVisible = false;
            }
            endX = 0;
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(
                startX,
                mMarkerTopOffset,
                -mStartMarker.getWidth(),
                -mStartMarker.getHeight());
        mStartMarker.setLayoutParams(params);

        params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(
                endX,
                mWaveformView.getMeasuredHeight() - mEndMarker.getHeight() - mMarkerBottomOffset,
                -mStartMarker.getWidth(),
                -mStartMarker.getHeight());
        mEndMarker.setLayoutParams(params);
    }
    private String formatTime(int pixels) {
        if (mWaveformView != null && mWaveformView.isInitialized()) {
            return formatDecimal(mWaveformView.pixelsToSeconds(pixels));
        } else {
            return "";
        }
    }

    private String formatDecimal(double x) {
        int xWhole = (int)x;
        int xFrac = (int)(100 * (x - xWhole) + 0.5);

        if (xFrac >= 100) {
            xWhole++; //Round up
            xFrac -= 100; //Now we need the remainder after the round up
            if (xFrac < 10) {
                xFrac *= 10; //we need a fraction that is 2 digits long
            }
        }

        if (xFrac < 10)
            return xWhole + ".0" + xFrac;
        else
            return xWhole + "." + xFrac;
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(15.0);
    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        return Math.min(pos, mMaxPos);
    }

    private void setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2);
    }

    private void setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2);
    }

    private void setOffsetGoal(int offset) {
        setOffsetGoalNoUpdate(offset);
        updateDisplay();
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }
    private synchronized void handlePause() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        enableDisableButtons();
    }
    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(R.drawable.ic_pause);
            mPlayButton.setContentDescription("stop");
        } else {
            mPlayButton.setImageResource(R.drawable.ic_play);
            mPlayButton.setContentDescription("play");
        }
    }

    public void waveformFling(float vx) {
        mTouchDragging = false;
        mOffsetGoal = mOffset;
        mFlingVelocity = (int)(-vx);
        updateDisplay();
    }

    public void waveformZoomIn() {
        mWaveformView.zoomIn();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }

    public void waveformZoomOut() {
        mWaveformView.zoomOut();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }


    public void markerDraw() {
    }
    public void markerTouchStart(MarkerView marker, float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialStartPos = mStartPos;
        mTouchInitialEndPos = mEndPos;
    }

    public void markerTouchMove(MarkerView marker, float x) {
        float delta = x - mTouchStart;

        if (marker == mStartMarker) {
            mStartPos = trap((int)(mTouchInitialStartPos + delta));
            mEndPos = trap((int)(mTouchInitialEndPos + delta));
        } else {
            mEndPos = trap((int)(mTouchInitialEndPos + delta));
            if (mEndPos < mStartPos)
                mEndPos = mStartPos;
        }

        updateDisplay();
    }

    public void markerTouchEnd(MarkerView marker) {
        mTouchDragging = false;
        if (marker == mStartMarker) {
            setOffsetGoalStart();
        } else {
            setOffsetGoalEnd();
        }
    }

    public void markerLeft(MarkerView marker, int velocity) {
        mKeyDown = true;

        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos = trap(mStartPos - velocity);
            mEndPos = trap(mEndPos - (saveStart - mStartPos));
            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            if (mEndPos == mStartPos) {
                mStartPos = trap(mStartPos - velocity);
                mEndPos = mStartPos;
            } else {
                mEndPos = trap(mEndPos - velocity);
            }

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerRight(MarkerView marker, int velocity) {
        mKeyDown = true;

        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos += velocity;
            if (mStartPos > mMaxPos)
                mStartPos = mMaxPos;
            mEndPos += (mStartPos - saveStart);
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            mEndPos += velocity;
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerEnter(MarkerView marker) {
    }

    public void markerKeyUp() {
        mKeyDown = false;
        updateDisplay();
    }

    public void markerFocus(MarkerView marker) {
        mKeyDown = false;
        if (marker == mStartMarker) {
            setOffsetGoalStartNoUpdate();
        } else {
            setOffsetGoalEndNoUpdate();
        }


        mHandler.postDelayed(new Runnable() {
            public void run() {
                updateDisplay();
            }
        }, 100);
    }
    public void waveformTouchStart(float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialOffset = mOffset;
        mFlingVelocity = 0;
        mWaveformTouchStartMsec = getCurrentTime();
    }

    public void waveformTouchMove(float x) {
        mOffset = trap((int)(mTouchInitialOffset + (mTouchStart - x)));
        updateDisplay();
    }

    public void waveformTouchEnd() {
        mTouchDragging = false;
        mOffsetGoal = mOffset;

        long elapsedMsec = getCurrentTime() - mWaveformTouchStartMsec;
        if (elapsedMsec < 300) {
            if (mIsPlaying) {
                int seekMsec = mWaveformView.pixelsToMillisecs(
                        (int)(mTouchStart + mOffset));
                if (seekMsec >= mPlayStartMsec &&
                        seekMsec < mPlayEndMsec) {
                    mPlayer.seekTo(seekMsec);
                } else {
                    handlePause();
                }
            } else {
                onPlay((int)(mTouchStart + mOffset));
            }
        }
    }

    private synchronized void onPlay(int startPosition) {
        if (mIsPlaying) {
            handlePause();
            return;
        }

        if (mPlayer == null) {
            // Not initialized yet
            return;
        }

        try {
            mPlayStartMsec = mWaveformView.pixelsToMillisecs(startPosition);
            if (startPosition < mStartPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mStartPos);
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mMaxPos);
            } else {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mEndPos);
            }
            mPlayer.setOnCompletionListener(new SamplePlayer.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    handlePause();
                }
            });
            mIsPlaying = true;

            mPlayer.seekTo(mPlayStartMsec);
            mPlayer.start();
            updateDisplay();
            enableDisableButtons();
        } catch (Exception e) {
            toastMsg("Read error");

        }
    }
    public void waveformDraw() {
        mWidth = mWaveformView.getMeasuredWidth();
        if (mOffsetGoal != mOffset && !mKeyDown)
            updateDisplay();
        else if (mIsPlaying) {
            updateDisplay();
        } else if (mFlingVelocity != 0) {
            updateDisplay();
        }
    }
    private OnClickListener mPlayListener = new OnClickListener() {
        public void onClick(View sender) {
            onPlay(mStartPos);
        }
    };

    private OnClickListener mRewindListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = mPlayer.getCurrentPosition() - 5000;
                if (newPos < mPlayStartMsec)
                    newPos = mPlayStartMsec;
                mPlayer.seekTo(newPos);
            } else {
                mStartMarker.requestFocus();
                markerFocus(mStartMarker);
            }
        }
    };

    private OnClickListener mFfwdListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = 5000 + mPlayer.getCurrentPosition();
                if (newPos > mPlayEndMsec)
                    newPos = mPlayEndMsec;
                mPlayer.seekTo(newPos);
            } else {
                mEndMarker.requestFocus();
                markerFocus(mEndMarker);
            }
        }
    };



    public void setSoundCutterStyle(String markerColor, String controlsColor, String controlsIconColors, String waveformViewSelected, String waveformViewUnselected, String waveformUnselectedBkg){
        waveformView.setColors(markerColor,controlsColor,waveformViewSelected,waveformViewUnselected,waveformUnselectedBkg);

        mPlayButton = findViewById(R.id.play);
        mPlayButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(controlsColor)));
        mPlayButton.setImageTintList(ColorStateList.valueOf(Color.parseColor(controlsIconColors)));

        mRewindButton = findViewById(R.id.rew);
        mRewindButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(controlsColor)));
        mRewindButton.setImageTintList(ColorStateList.valueOf(Color.parseColor(controlsIconColors)));

        mFfwdButton = findViewById(R.id.ffwd);
        mFfwdButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(controlsColor)));
        mFfwdButton.setImageTintList(ColorStateList.valueOf(Color.parseColor(controlsIconColors)));


    }
    public boolean saveSound(String soundname) {
        double startTime = mWaveformView.pixelsToSeconds(mStartPos);
        double endTime = mWaveformView.pixelsToSeconds(mEndPos);
        final int startFrame = mWaveformView.secondsToFrames(startTime);
        final int endFrame = mWaveformView.secondsToFrames(endTime);


        mSaveSoundFileThread = new Thread() {
            public void run() {
                // Try AAC first.
                String outPath = makeRingtoneFilename(".mp3");
                final String finalOutPath1 = outPath;

                File outFile = new File(outPath);
                try {
                    // Write the new file
                    mSoundFile.WriteFile(outFile, startFrame, endFrame - startFrame);
                } catch (Exception e) {
                    // log the error and try to create a .wav file instead
                    if (outFile.exists()) {
                        outFile.delete();
                    }
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    Log.e("Ringdroid", "Error: Failed to create " + outPath);
                    Log.e("Ringdroid", writer.toString());
                }


                try {
                    final SoundFile.ProgressListener listener =
                            frac -> true;
                    SoundFile.create(outPath, listener);
                } catch (final Exception e) {
                    e.printStackTrace();

                    Runnable runnable = () -> toastMsg("Error: " + e.getMessage());
                    mHandler.post(runnable);
                    return;
                }

                if(outFile.exists()){
                    extension = ".mp3";
                    String filePath;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        saved = saveAudioQ(soundname);
                    }else{
                        saved = savefilebelowq(soundname);
                    }

                }

            }
        };
        mSaveSoundFileThread.start();

        return saved;
    }
    private String makeRingtoneFilename(String extension){
        SharedPreferences sp_counter = ctx.getSharedPreferences("cutter",0);
        int counter = sp_counter.getInt("counter",0) + 1;
        String filePath;

        File storage = ctx.getFilesDir();
        File directory = new File(storage.getAbsolutePath());
        filePath = directory + "/temp_sound_" + counter + extension;

        SharedPreferences.Editor editor = sp_counter.edit();
        editor.putInt("counter",counter);
        editor.apply();

        return filePath;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public boolean saveAudioQ(String soundname){
        String filePath;
        File storage = ctx.getFilesDir();
        File directory = new File(storage.getAbsolutePath());

        SharedPreferences sp_counter = ctx.getSharedPreferences("cutter",0);
        int counter = sp_counter.getInt("counter",0);
        filePath = directory + "/temp_sound_" + counter + ".mp3";
        Uri uri3 = Uri.fromFile(new File(filePath));


        String videoFileName = soundname +".mp3";

        ContentValues valuesvideos;
        valuesvideos = new ContentValues();
        valuesvideos.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/");
        valuesvideos.put(MediaStore.Audio.Media.TITLE, videoFileName);
        valuesvideos.put(MediaStore.Audio.Media.DISPLAY_NAME, videoFileName);
        valuesvideos.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        valuesvideos.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        valuesvideos.put(MediaStore.Audio.Media.DATE_TAKEN, System.currentTimeMillis());
        valuesvideos.put(MediaStore.Audio.Media.IS_PENDING, 1);
        ContentResolver resolver = ctx.getContentResolver();
        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY); //all video files on primary external storage
        Uri uriSavedVideo = resolver.insert(collection, valuesvideos);

        ParcelFileDescriptor pfd;

        try {
            pfd = ctx.getContentResolver().openFileDescriptor(uriSavedVideo,"w");

            assert pfd != null;
            FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor());

            // Get the already saved video as fileinputstream from here
            InputStream in = ctx.getContentResolver().openInputStream(uri3);


            byte[] buf = new byte[8192];

            int len;
            int progress = 0;
            while ((len = in.read(buf)) > 0) {
                progress = progress + len;

                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            pfd.close();
            valuesvideos.clear();
            valuesvideos.put(MediaStore.Video.Media.IS_PENDING, 0);
            valuesvideos.put(MediaStore.Video.Media.IS_PENDING, 0); //only your app can see the files until pending is turned into 0

            ctx.getContentResolver().update(uriSavedVideo, valuesvideos, null, null);

            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, "error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();

            return false;
        }
    }

    @Override
    public void PickiTonUriReturned() {

    }

    @Override
    public void PickiTonStartListener() {

    }

    @Override
    public void PickiTonProgressUpdate(int progress) {

    }

    @Override
    public void PickiTonCompleteListener(String path, boolean wasDriveFile, boolean wasUnknownProvider, boolean wasSuccessful, String Reason) {
        savefile(new File(path));
    }

    @Override
    public void PickiTonMultipleCompleteListener(ArrayList<String> paths, boolean wasSuccessful, String Reason) {

    }
    public void savefile(File inputfile){
        File file;
        File storage = ctx.getFilesDir();
        File directory = new File(storage.getAbsolutePath());
        file = new File(directory, "temp.mp3");


        try {
            InputStream in = new FileInputStream(inputfile);
            OutputStream out = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer, 0, buffer.length)) != -1){
                out.write(buffer, 0 , len);
            }
            in.close();
            out.close();

            Uri uriConOPUS = Uri.parse(ctx.getFilesDir().toString() + "/temp.mp3");
            loadFromFile(uriConOPUS);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(ctx, getResources().getString(R.string.error_fnf), Toast.LENGTH_SHORT).show();
        }catch (IOException e){
            e.printStackTrace();
            Toast.makeText(ctx, getResources().getString(R.string.error_io), Toast.LENGTH_SHORT).show();
        }

    }
    public boolean savefilebelowq(String name){
        String filePath;
        File storage = ctx.getFilesDir();
        File directory = new File(storage.getAbsolutePath());

        SharedPreferences sp_counter = ctx.getSharedPreferences("cutter",0);
        int counter = sp_counter.getInt("counter",0);
        filePath = directory + "/temp_sound_" + counter + ".mp3";
        File inputfile = new File(filePath);

        File destFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + File.separator + name + ".mp3");

        try {

            org.apache.commons.io.FileUtils.copyFile(inputfile, destFile);
            new SingleMediaScanner(ctx, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
            //TODO Notify
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(ctx, "error:  " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
        //TODO Shows ads
    }
    public String saveInternal(){
        double startTime = mWaveformView.pixelsToSeconds(mStartPos);
        double endTime = mWaveformView.pixelsToSeconds(mEndPos);
        final int startFrame = mWaveformView.secondsToFrames(startTime);
        final int endFrame = mWaveformView.secondsToFrames(endTime);


        mSaveSoundFileThread = new Thread() {
            public void run() {
                // Try AAC first.
                String outPath = makeRingtoneFilename(".mp3");
                final String finalOutPath1 = outPath;

                File outFile = new File(outPath);
                try {
                    // Write the new file
                    mSoundFile.WriteFile(outFile, startFrame, endFrame - startFrame);
                } catch (Exception e) {
                    // log the error and try to create a .wav file instead
                    if (outFile.exists()) {
                        outFile.delete();
                    }
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    Log.e("Ringdroid", "Error: Failed to create " + outPath);
                    Log.e("Ringdroid", writer.toString());
                }


                try {
                    final SoundFile.ProgressListener listener =
                            frac -> true;
                    SoundFile.create(outPath, listener);
                } catch (final Exception e) {
                    e.printStackTrace();

                    Runnable runnable = () -> toastMsg("Error: " + e.getMessage());
                    mHandler.post(runnable);
                    return;
                }

                if(outFile.exists()){
                    extension = ".mp3";
                    savedInternal = true;
                }else{
                    savedInternal = false;
                }

            }
        };
        mSaveSoundFileThread.start();
        File storage = ctx.getFilesDir();
        File directory = new File(storage.getAbsolutePath());
        SharedPreferences sp_counter = ctx.getSharedPreferences("cutter",0);
        int counter = sp_counter.getInt("counter",0);
        counter = counter + 1;
        return directory + "/temp_sound_" + counter + ".mp3";

    }
    public boolean isLoading(){
        return isLoading;
    }
}