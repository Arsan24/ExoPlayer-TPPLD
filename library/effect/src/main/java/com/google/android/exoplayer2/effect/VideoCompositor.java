/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.effect;

import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * Interface for a video compositor that combines frames from mutliple input sources to produce
 * output frames.
 *
 * <p>Input and output are provided via OpenGL textures.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface VideoCompositor {

  /** Listener for errors. */
  interface Listener {
    /**
     * Called when an exception occurs during asynchronous frame compositing.
     *
     * <p>Using {@link VideoCompositor} after an error happens is undefined behavior.
     */
    void onError(VideoFrameProcessingException exception);

    /** Called after {@link VideoCompositor} has output its final output frame. */
    void onEnded();
  }

  /**
   * Registers a new input source, and returns a unique {@code inputId} corresponding to this
   * source, to be used in {@link #queueInputTexture}.
   */
  int registerInputSource();

  /**
   * Signals that no more frames will come from the upstream {@link
   * DefaultVideoFrameProcessor.TextureOutputListener}.
   *
   * <p>Each input source must have a unique {@code inputId} returned from {@link
   * #registerInputSource}.
   */
  void signalEndOfInputSource(int inputId);

  /**
   * Queues an input texture to be composited, for example from an upstream {@link
   * DefaultVideoFrameProcessor.TextureOutputListener}.
   *
   * <p>Each input source must have a unique {@code inputId} returned from {@link
   * #registerInputSource}.
   */
  void queueInputTexture(
      int inputId,
      GlTextureInfo inputTexture,
      long presentationTimeUs,
      DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseTextureCallback);

  /** Releases all resources. */
  void release();
}
