package com.google.android.exoplayer2.audio;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author senrsl
 * @ClassName: TeeWavFileSink
 * @Package: com.google.android.exoplayer2.audio
 * @CreateTime: 2021/6/2 6:39 下午
 */
public class TeeWavFileSink implements TeeAudioProcessor.AudioBufferSink, AudioSink {

  private static final String TAG = "WaveFileAudioBufferSink";

  private static final int FILE_SIZE_MINUS_8_OFFSET = 4;
  private static final int FILE_SIZE_MINUS_44_OFFSET = 40;
  private static final int HEADER_LENGTH = 44;

  private final String outputFileNamePrefix;
  private final byte[] scratchBuffer;
  private final ByteBuffer scratchByteBuffer;

  private int sampleRateHz;
  private int channelCount;
  @C.PcmEncoding
  private int encoding;
  @Nullable
  private RandomAccessFile randomAccessFile;
  private int counter;
  private int bytesWritten;

  /**
   * Creates a new audio buffer sink that writes to .wav files with the given prefix.
   *
   * @param outputFileNamePrefix The prefix for output files.
   */
  public TeeWavFileSink(String outputFileNamePrefix) {
    this.outputFileNamePrefix = outputFileNamePrefix;
    scratchBuffer = new byte[1024];
    scratchByteBuffer = ByteBuffer.wrap(scratchBuffer).order(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
    reset();
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    this.encoding = encoding;
  }

  @Override
  public void handleBuffer(ByteBuffer buffer) {
    try {
      maybePrepareFile();
      writeBuffer(buffer);
    } catch (IOException e) {
      Log.e(TAG, "Error writing data", e);
    }
  }

  private void maybePrepareFile() throws IOException {
    if (randomAccessFile != null) {
      return;
    }
    RandomAccessFile randomAccessFile = new RandomAccessFile(getNextOutputFileName(), "rw");
    writeFileHeader(randomAccessFile);
    this.randomAccessFile = randomAccessFile;
    bytesWritten = HEADER_LENGTH;
  }

  private void writeFileHeader(RandomAccessFile randomAccessFile) throws IOException {
    // Write the start of the header as big endian data.
    randomAccessFile.writeInt(WavUtil.RIFF_FOURCC);
    randomAccessFile.writeInt(-1);
    randomAccessFile.writeInt(WavUtil.WAVE_FOURCC);
    randomAccessFile.writeInt(WavUtil.FMT_FOURCC);

    // Write the rest of the header as little endian data.
    scratchByteBuffer.clear();
    scratchByteBuffer.putInt(16);
    scratchByteBuffer.putShort((short) WavUtil.getTypeForPcmEncoding(encoding));
    scratchByteBuffer.putShort((short) channelCount);
    scratchByteBuffer.putInt(sampleRateHz);
    int bytesPerSample = Util.getPcmFrameSize(encoding, channelCount);
    scratchByteBuffer.putInt(bytesPerSample * sampleRateHz);
    scratchByteBuffer.putShort((short) bytesPerSample);
    scratchByteBuffer.putShort((short) (8 * bytesPerSample / channelCount));
    randomAccessFile.write(scratchBuffer, 0, scratchByteBuffer.position());

    // Write the start of the data chunk as big endian data.
    randomAccessFile.writeInt(WavUtil.DATA_FOURCC);
    randomAccessFile.writeInt(-1);
  }

  private void writeBuffer(ByteBuffer buffer) throws IOException {
    RandomAccessFile randomAccessFile = Assertions.checkNotNull(this.randomAccessFile);
    while (buffer.hasRemaining()) {
      int bytesToWrite = min(buffer.remaining(), scratchBuffer.length);
      buffer.get(scratchBuffer, 0, bytesToWrite);
      randomAccessFile.write(scratchBuffer, 0, bytesToWrite);
      bytesWritten += bytesToWrite;
    }
  }

  @Override
  public void setListener(Listener listener) {

  }

  @Override
  public boolean supportsFormat(Format format) {
    return false;
  }

  @Override
  public int getFormatSupport(Format format) {
    return SINK_FORMAT_SUPPORTED_WITH_TRANSCODING;
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    return 0;
  }

  @Override
  public void configure(Format inputFormat, int specifiedBufferSize,@Nullable int[] outputChannels)
      throws ConfigurationException {

  }

  @Override
  public void play() {

  }

  @Override
  public void handleDiscontinuity() {

  }

  @Override
  public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs,
      int encodedAccessUnitCount) throws InitializationException, WriteException {
    return false;
  }

  @Override
  public void playToEndOfStream() throws WriteException {

  }

  @Override
  public boolean isEnded() {
    return false;
  }

  @Override
  public boolean hasPendingData() {
    return false;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {

  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return null;
  }

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {

  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return false;
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {

  }

  @Override
  public void setAudioSessionId(int audioSessionId) {

  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {

  }

  @Override
  public void enableTunnelingV21() {

  }

  @Override
  public void disableTunneling() {

  }

  @Override
  public void setVolume(float volume) {

  }

  @Override
  public void pause() {

  }

  @Override
  public void flush() {

  }

  @Override
  public void experimentalFlushWithoutAudioTrackRelease() {

  }

  public void reset() {
    @Nullable RandomAccessFile randomAccessFile = this.randomAccessFile;
    if (randomAccessFile == null) {
      return;
    }

    try {
      scratchByteBuffer.clear();
      scratchByteBuffer.putInt(bytesWritten - 8);
      randomAccessFile.seek(FILE_SIZE_MINUS_8_OFFSET);
      randomAccessFile.write(scratchBuffer, 0, 4);

      scratchByteBuffer.clear();
      scratchByteBuffer.putInt(bytesWritten - 44);
      randomAccessFile.seek(FILE_SIZE_MINUS_44_OFFSET);
      randomAccessFile.write(scratchBuffer, 0, 4);
    } catch (IOException e) {
      // The file may still be playable, so just log a warning.
      Log.w(TAG, "Error updating file size", e);
    }

    try {
      randomAccessFile.close();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      this.randomAccessFile = null;
    }
  }

  private String getNextOutputFileName() {
    return Util.formatInvariant("%s-%04d.wav", outputFileNamePrefix, counter++);
  }

}
