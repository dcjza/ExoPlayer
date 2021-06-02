/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dc.common.Logger;

/**
 * Interface for audio processors, which take audio data as input and transform it, potentially
 * modifying its channel count, encoding and/or sample rate.
 *
 * <p>In addition to being able to modify the format of audio, implementations may allow parameters
 * to be set that affect the output audio and whether the processor is active/inactive.
 * 音频处理器的接口，它将音频数据作为输入并进行转换，从而有可能修改其通道数，编码和/或采样率。
 * <p>除了能够修改音频的格式外，实现还可以设置影响输出音频以及处理器是否处于活动状态的参数。
 */
public interface AudioProcessor {

  /** PCM audio format that may be handled by an audio processor.音频处理器可以处理的PCM音频格式。 */
  final class AudioFormat {
    public static final AudioFormat NOT_SET = //没有设置，就都是-1
        new AudioFormat(
            /* sampleRate= */ Format.NO_VALUE,
            /* channelCount= */ Format.NO_VALUE,
            /* encoding= */ Format.NO_VALUE);

    /** The sample rate in Hertz赫兹采样率. */
    public final int sampleRate;
    /** The number of interleaved channels. */
    public final int channelCount;
    /** The type of linear PCM encoding. */
    @C.PcmEncoding public final int encoding;
    /** The number of bytes used to represent one audio frame用来表示一个音频帧的字节数. */
    public final int bytesPerFrame;

    public AudioFormat(int sampleRate, int channelCount, @C.PcmEncoding int encoding) {
      this.sampleRate = sampleRate;
      this.channelCount = channelCount;
      this.encoding = encoding;
      bytesPerFrame =
          Util.isEncodingLinearPcm(encoding)
              ? Util.getPcmFrameSize(encoding, channelCount)
              : Format.NO_VALUE;
      Logger.w("AudioProcessor",sampleRate,channelCount,encoding,bytesPerFrame);
    }

    @Override
    public String toString() {
      return "AudioProcessor 音频处理 AudioFormat["
          + "sampleRate="
          + sampleRate
          + ", channelCount="
          + channelCount
          + ", encoding="
          + encoding
          + ']';
    }
  }

  /** Exception thrown when a processor can't be configured for a given input audio format
   * 无法为给定的输入音频格式配置处理器时抛出异常. */
  final class UnhandledAudioFormatException extends Exception {

    public UnhandledAudioFormatException(AudioFormat inputAudioFormat) {
      super("Unhandled format: " + inputAudioFormat);
    }

  }

  /** An empty, direct {@link ByteBuffer}. */
  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  /**
   * Configures the processor to process input audio with the specified format. After calling this
   * method, call {@link #isActive()} to determine whether the audio processor is active. Returns
   * the configured output audio format if this instance is active.
   *
   * <p>After calling this method, it is necessary to {@link #flush()} the processor to apply the
   * new configuration. Before applying the new configuration, it is safe to queue input and get
   * output in the old input/output formats. Call {@link #queueEndOfStream()} when no more input
   * will be supplied in the old input format.
   * 将处理器配置为处理指定格式的输入音频。 调用此方法后，调用isActive（）以确定音频处理器是否处于活动状态。 如果此实例处于活动状态，则返回配置的输出音频格式。
   * 调用此方法后，有必要flush（）处理器以应用新配置。 在应用新配置之前，可以安全地将输入排队并以旧的输入/输出格式获取输出。 当不再以旧的输入格式提供输入时，请调用queueEndOfStream（）。
   *
   * @param inputAudioFormat The format of audio that will be queued after the next call to {@link
   *     #flush()}.
   * @return The configured output audio format if this instance is {@link #isActive() active}.
   * @throws UnhandledAudioFormatException Thrown if the specified format can't be handled as input.
   */
  AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException;

  /** Returns whether the processor is configured and will process input buffers返回是否配置了处理器并将处理输入缓冲区. */
  boolean isActive();

  /**
   * Queues audio data between the position and limit of the input {@code buffer} for processing.
   * {@code buffer} must be a direct byte buffer with native byte order. Its contents are treated as
   * read-only. Its position will be advanced by the number of bytes consumed (which may be zero).
   * The caller retains ownership of the provided buffer. Calling this method invalidates any
   * previous buffer returned by {@link #getOutput()}.
   * 在输入缓冲区的位置和限制之间排队音频数据以进行处理。
   * 缓冲区必须是具有本地字节顺序的直接字节缓冲区。 其内容被视为只读。 它的位置将增加所消耗的字节数（可能为零）。
   * 调用方保留提供的缓冲区的所有权。 调用此方法会使getOutput（）返回的所有先前缓冲区无效。
   *
   * @param buffer The input buffer to process.
   */
  void queueInput(ByteBuffer buffer);

  /**
   * Queues an end of stream signal. After this method has been called,
   * {@link #queueInput(ByteBuffer)} may not be called until after the next call to
   * {@link #flush()}. Calling {@link #getOutput()} will return any remaining output data. Multiple
   * calls may be required to read all of the remaining output data. {@link #isEnded()} will return
   * {@code true} once all remaining output data has been read.
   * 将流信号的末尾排队。
   * 调用此方法后，直到下一次调用flush（）之后，才能调用queueInput（ByteBuffer）。
   * 调用getOutput（）将返回所有剩余的输出数据。 可能需要多次调用才能读取所有剩余的输出数据。 一旦读取了所有剩余的输出数据，isEnded（）将返回true。
   */
  void queueEndOfStream();

  /**
   * Returns a buffer containing processed output data between its position and limit. The buffer
   * will always be a direct byte buffer with native byte order. Calling this method invalidates any
   * previously returned buffer. The buffer will be empty if no output is available.
   * 返回一个缓冲区，该缓冲区包含在其位置和限制之间的已处理输出数据。 缓冲区将始终是具有本地字节顺序的直接字节缓冲区。
   * 调用此方法会使以前返回的所有缓冲区无效。 如果没有可用的输出，缓冲区将为空。
   *
   * 返回值：
   * 包含在其位置和极限之间的已处理输出数据的缓冲区。
   *
   * @return A buffer containing processed output data between its position and limit.
   */
  ByteBuffer getOutput();

  /**
   * Returns whether this processor will return no more output from {@link #getOutput()} until it
   * has been {@link #flush()}ed and more input has been queued.
   */
  boolean isEnded();

  /**
   * Clears any buffered data and pending output. If the audio processor is active, also prepares
   * the audio processor to receive a new stream of input in the last configured (pending) format.
   * 清除所有缓冲的数据和挂起的输出。 如果音频处理器处于活动状态，则还要准备音频处理器以接收最后配置的（待定）格式的新输入流。
   */
  void flush();

  /** Resets the processor to its unconfigured state, releasing any resources. */
  void reset();

}
