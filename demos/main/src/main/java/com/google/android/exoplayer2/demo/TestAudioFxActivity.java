package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * @author senrsl
 * @ClassName: TestAudioFxActivity
 * @Package: com.google.android.exoplayer2.demo
 * @CreateTime: 2021/5/26 5:22 下午
 */
public class TestAudioFxActivity extends Activity {
  private static final String TAG = "AudioFxDemo";
  private static final float VISUALIZER_HEIGHT_DIP = 50f;
  private MediaPlayer mMediaPlayer;
  private Visualizer mVisualizer;
  private Equalizer mEqualizer;
  private LinearLayout mLinearLayout;
  private VisualizerView mVisualizerView;
  private TextView mStatusTextView;

  public static void start(Context context) {
      Intent starter = new Intent(context, TestAudioFxActivity.class);
      context.startActivity(starter);
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    mStatusTextView = new TextView(this);
    mLinearLayout = new LinearLayout(this);
    mLinearLayout.setOrientation(LinearLayout.VERTICAL);
    mLinearLayout.addView(mStatusTextView);
    setContentView(mLinearLayout);
    // Create the MediaPlayer
    mMediaPlayer = MediaPlayer.create(this, R.raw.test_cbr);
    Log.d(TAG, "MediaPlayer audio session ID: " + mMediaPlayer.getAudioSessionId());
    setupVisualizerFxAndUI();
    setupEqualizerFxAndUI();
    // Make sure the visualizer is enabled only when you actually want to receive data, and
    // when it makes sense to receive data.
    mVisualizer.setEnabled(true);
    // When the stream ends, we don't need to collect any more data. We don't do this in
    // setupVisualizerFxAndUI because we likely want to have more, non-Visualizer related code
    // in this callback.
    mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      public void onCompletion(MediaPlayer mediaPlayer) {
        mVisualizer.setEnabled(false);
      }
    });
    mMediaPlayer.start();
    mStatusTextView.setText("Playing audio...");
  }
  private void setupEqualizerFxAndUI() {
    // Create the Equalizer object (an AudioEffect subclass) and attach it to our media player,
    // with a default priority (0).
    mEqualizer = new Equalizer(0, mMediaPlayer.getAudioSessionId());
    mEqualizer.setEnabled(true);
    TextView eqTextView = new TextView(this);
    eqTextView.setText("Equalizer:");
    mLinearLayout.addView(eqTextView);
    short bands = mEqualizer.getNumberOfBands();
    final short minEQLevel = mEqualizer.getBandLevelRange()[0];
    final short maxEQLevel = mEqualizer.getBandLevelRange()[1];
    for (short i = 0; i < bands; i++) {
      final short band = i;
      TextView freqTextView = new TextView(this);
      freqTextView.setLayoutParams(new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.FILL_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      freqTextView.setGravity(Gravity.CENTER_HORIZONTAL);
      freqTextView.setText((mEqualizer.getCenterFreq(band) / 1000) + " Hz");
      mLinearLayout.addView(freqTextView);
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      TextView minDbTextView = new TextView(this);
      minDbTextView.setLayoutParams(new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      minDbTextView.setText((minEQLevel / 100) + " dB");
      TextView maxDbTextView = new TextView(this);
      maxDbTextView.setLayoutParams(new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      maxDbTextView.setText((maxEQLevel / 100) + " dB");
      LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.FILL_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      layoutParams.weight = 1;
      SeekBar bar = new SeekBar(this);
      bar.setLayoutParams(layoutParams);
      bar.setMax(maxEQLevel - minEQLevel);
      bar.setProgress(mEqualizer.getBandLevel(band));
      bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
          mEqualizer.setBandLevel(band, (short) (progress + minEQLevel));
        }
        public void onStartTrackingTouch(SeekBar seekBar) {}
        public void onStopTrackingTouch(SeekBar seekBar) {}
      });
      row.addView(minDbTextView);
      row.addView(bar);
      row.addView(maxDbTextView);
      mLinearLayout.addView(row);
    }
  }
  private void setupVisualizerFxAndUI() {
    // Create a VisualizerView (defined below), which will render the simplified audio
    // wave form to a Canvas.
    mVisualizerView = new VisualizerView(this);
    mVisualizerView.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.FILL_PARENT,
        (int)(VISUALIZER_HEIGHT_DIP * getResources().getDisplayMetrics().density)));
    mLinearLayout.addView(mVisualizerView);
    // Create the Visualizer object and attach it to our media player.
    mVisualizer = new Visualizer(mMediaPlayer.getAudioSessionId());
    mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
    mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
      public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
          int samplingRate) {
        mVisualizerView.updateVisualizer(bytes);
      }
      public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {}
    }, Visualizer.getMaxCaptureRate() / 2, true, false);
  }
  @Override
  protected void onPause() {
    super.onPause();
    if (isFinishing() && mMediaPlayer != null) {
      mVisualizer.release();
      mEqualizer.release();
      mMediaPlayer.release();
      mMediaPlayer = null;
    }
  }
}
/**
 * A simple class that draws waveform data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
 */
class VisualizerView extends View {
  private byte[] mBytes;
  private float[] mPoints;
  private Rect mRect = new Rect();
  private Paint mForePaint = new Paint();
  public VisualizerView(Context context) {
    super(context);
    init();
  }
  private void init() {
    mBytes = null;
    mForePaint.setStrokeWidth(1f);
    mForePaint.setAntiAlias(true);
    mForePaint.setColor(Color.rgb(0, 128, 255));
  }
  public void updateVisualizer(byte[] bytes) {
    mBytes = bytes;
    invalidate();
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mBytes == null) {
      return;
    }
    if (mPoints == null || mPoints.length < mBytes.length * 4) {
      mPoints = new float[mBytes.length * 4];
    }
    mRect.set(0, 0, getWidth(), getHeight());
    for (int i = 0; i < mBytes.length - 1; i++) {
      mPoints[i * 4] = mRect.width() * i / (mBytes.length - 1);
      mPoints[i * 4 + 1] = mRect.height() / 2
          + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2) / 128;
      mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
      mPoints[i * 4 + 3] = mRect.height() / 2
          + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2) / 128;
    }
    canvas.drawLines(mPoints, mForePaint);
  }
}
