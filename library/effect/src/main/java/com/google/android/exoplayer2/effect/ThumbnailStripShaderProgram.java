/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import android.content.Context;
import android.opengl.GLES20;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.io.IOException;

/**
 * Draws the target input frame at a given horizontal position of the output texture to generate an
 * horizontal tiling effect.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class ThumbnailStripShaderProgram extends SingleFrameGlShaderProgram {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_thumbnail_strip_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_copy_es2.glsl";

  private final GlProgram glProgram;
  private final ThumbnailStripEffect thumbnailStripEffect;

  public ThumbnailStripShaderProgram(
      Context context, boolean useHdr, ThumbnailStripEffect thumbnailStripEffect)
      throws VideoFrameProcessingException {
    super(useHdr);
    this.thumbnailStripEffect = thumbnailStripEffect;

    try {
      this.glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }

    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public boolean shouldClearTextureBuffer() {
    // The output texture buffer is never cleared in order to keep the previously drawn frames and
    // generate an horizontal tiling effect.
    return false;
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    return new Size(thumbnailStripEffect.stripWidth, thumbnailStripEffect.stripHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    long targetPresentationTimeUs = Util.msToUs(thumbnailStripEffect.getNextTimestampMs());
    // Ignore the frame if there are no more thumbnails to draw or if it's earlier than the target.
    if (thumbnailStripEffect.isDone() || presentationTimeUs < targetPresentationTimeUs) {
      return;
    }
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setIntUniform("uIndex", thumbnailStripEffect.getNextThumbnailIndex());
      glProgram.setIntUniform("uCount", thumbnailStripEffect.getNumberOfThumbnails());
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      thumbnailStripEffect.onThumbnailDrawn();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e, presentationTimeUs);
    }
  }
}