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

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * A debugging tracing utility.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DebugTraceUtil {
  /** Events logged by {@link #logEvent}. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    EVENT_VIDEO_INPUT_FORMAT,
    EVENT_DECODER_SIGNAL_EOS,
    EVENT_DECODER_DECODED_FRAME,
    EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO,
    EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO,
    EVENT_DECODER_RECEIVE_EOS,
    EVENT_ENCODER_ENCODED_FRAME,
    EVENT_ENCODER_RECEIVE_EOS,
    EVENT_EXTERNAL_INPUT_MANAGER_SIGNAL_EOS,
    EVENT_MUXER_INPUT_VIDEO,
    EVENT_MUXER_INPUT_AUDIO,
    EVENT_MUXER_TRACK_ENDED_VIDEO,
    EVENT_MUXER_TRACK_ENDED_AUDIO,
    EVENT_VFP_FRAME_DEQUEUED,
    EVENT_VFP_RECEIVE_DECODER_EOS,
    EVENT_VFP_RENDERED_TO_INPUT,
    EVENT_VFP_RENDERED_TO_OUTPUT,
    EVENT_VFP_SIGNAL_EOS,
    EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS,
    EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS
  })
  @Documented
  @Target(TYPE_USE)
  public @interface DebugTraceEvent {}

  public static final String EVENT_VIDEO_INPUT_FORMAT = "VideoInputFormat";
  public static final String EVENT_DECODER_SIGNAL_EOS = "Decoder-SignalEOS";
  public static final String EVENT_DECODER_DECODED_FRAME = "Decoder-DecodedFrame";
  public static final String EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO = "Muxer-CanWriteSample_Video";
  public static final String EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO = "Muxer-CanWriteSample_Audio";
  public static final String EVENT_DECODER_RECEIVE_EOS = "Decoder-ReceiveEOS";
  public static final String EVENT_ENCODER_ENCODED_FRAME = "Encoder-EncodedFrame";
  public static final String EVENT_ENCODER_RECEIVE_EOS = "Encoder-ReceiveEOS";
  public static final String EVENT_EXTERNAL_INPUT_MANAGER_SIGNAL_EOS =
      "ExternalInputManager-SignalEOS";
  public static final String EVENT_MUXER_INPUT_VIDEO = "Muxer-Input_Video";
  public static final String EVENT_MUXER_INPUT_AUDIO = "Muxer-Input_Audio";
  public static final String EVENT_MUXER_TRACK_ENDED_VIDEO = "Muxer-TrackEnded_Video";
  public static final String EVENT_MUXER_TRACK_ENDED_AUDIO = "Muxer-TrackEnded_Audio";
  public static final String EVENT_VFP_FRAME_DEQUEUED = "VFP-FrameDequeued";
  public static final String EVENT_VFP_RECEIVE_DECODER_EOS = "VFP-ReceiveDecoderEOS";
  public static final String EVENT_VFP_RENDERED_TO_INPUT = "VFP-RenderedToInput";
  public static final String EVENT_VFP_RENDERED_TO_OUTPUT = "VFP-RenderedToOutput";
  public static final String EVENT_VFP_SIGNAL_EOS = "VFP-SignalEOS";
  public static final String EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS =
      "BitmapTextureManager-SignalEOS";
  public static final String EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS =
      "TexIdTextureManager-SignalEOS";

  private static final int MAX_FIRST_LAST_LOGS = 10;

  @GuardedBy("DebugTraceUtil.class")
  private static final Map<String, EventLogger> events = new LinkedHashMap<>();

  @GuardedBy("DebugTraceUtil.class")
  private static long startTimeMs = SystemClock.DEFAULT.elapsedRealtime();

  public static synchronized void reset() {
    events.clear();
    startTimeMs = SystemClock.DEFAULT.elapsedRealtime();
  }

  /**
   * Logs a new event.
   *
   * @param eventName The {@linkplain DebugTraceEvent event name} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   * @param extra Optional extra info about the event being logged.
   */
  public static synchronized void logEvent(
      @DebugTraceEvent String eventName, long presentationTimeUs, @Nullable String extra) {
    long eventTimeMs = SystemClock.DEFAULT.elapsedRealtime() - startTimeMs;
    if (!events.containsKey(eventName)) {
      events.put(eventName, new EventLogger());
    }
    EventLogger logger = events.get(eventName);
    logger.addLog(new EventLog(presentationTimeUs, eventTimeMs, extra));
  }

  /**
   * Logs a new event.
   *
   * @param eventName The {@linkplain DebugTraceEvent event name} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   */
  public static synchronized void logEvent(
      @DebugTraceEvent String eventName, long presentationTimeUs) {
    logEvent(eventName, presentationTimeUs, null);
  }

  /**
   * Generate a summary of the traced events, containing the total number of times an event happened
   * and the detailed log on the first and last {@link #MAX_FIRST_LAST_LOGS} times.
   */
  public static synchronized String generateTraceSummary() {
    StringBuilder stringBuilder = new StringBuilder();
    for (Map.Entry<String, EventLogger> entry : events.entrySet()) {
      EventLogger logger = entry.getValue();
      stringBuilder.append(
          Util.formatInvariant("%s[%d]: [", entry.getKey(), logger.getTotalCount()));
      String separator = "";
      ImmutableList<EventLog> eventLogs = logger.getLogs();
      for (int i = 0; i < eventLogs.size(); i++) {
        EventLog eventLog = eventLogs.get(i);
        String logTime =
            Util.formatInvariant(
                "%s@%d",
                presentationTimeToString(eventLog.presentationTimeUs), eventLog.eventTimeMs);
        String extra = eventLog.extra != null ? Util.formatInvariant("(%s)", eventLog.extra) : "";
        stringBuilder.append(separator).append(logTime).append(extra);
        separator = ",";
      }
      stringBuilder.append("]; ");
    }
    return stringBuilder.toString();
  }

  /** Dumps all the stored events to a tsv file. */
  public static synchronized void dumpTsv(Writer writer) throws IOException {
    writer.write("event\ttimestamp\tpresentation\textra\n");
    for (Map.Entry<String, EventLogger> entry : events.entrySet()) {
      ImmutableList<EventLog> eventLogs = entry.getValue().getLogs();
      for (int i = 0; i < eventLogs.size(); i++) {
        EventLog eventLog = eventLogs.get(i);
        writer.write(
            Util.formatInvariant(
                "%s\t%d\t%s\t%s\n",
                entry.getKey(),
                eventLog.eventTimeMs,
                presentationTimeToString(eventLog.presentationTimeUs),
                Strings.nullToEmpty(eventLog.extra)));
      }
    }
  }

  private static String presentationTimeToString(long presentationTimeUs) {
    if (presentationTimeUs == C.TIME_UNSET) {
      return "UNSET";
    } else if (presentationTimeUs == C.TIME_END_OF_SOURCE) {
      return "EOS";
    } else {
      return String.valueOf(presentationTimeUs);
    }
  }

  private static final class EventLog {
    public final long presentationTimeUs;
    public final long eventTimeMs;
    @Nullable public final String extra;

    private EventLog(long presentationTimeUs, long eventTimeMs, @Nullable String extra) {
      this.presentationTimeUs = presentationTimeUs;
      this.eventTimeMs = eventTimeMs;
      this.extra = extra;
    }
  }

  private static final class EventLogger {
    private final List<EventLog> firstLogs;
    private final Queue<EventLog> lastLogs;
    private int totalCount;

    public EventLogger() {
      firstLogs = new ArrayList<>();
      lastLogs = new ArrayDeque<>();
      totalCount = 0;
    }

    public void addLog(EventLog log) {
      if (firstLogs.size() < MAX_FIRST_LAST_LOGS) {
        firstLogs.add(log);
      } else {
        lastLogs.add(log);
        if (lastLogs.size() > MAX_FIRST_LAST_LOGS) {
          lastLogs.remove();
        }
      }
      totalCount++;
    }

    public int getTotalCount() {
      return totalCount;
    }

    public ImmutableList<EventLog> getLogs() {
      return new ImmutableList.Builder<EventLog>().addAll(firstLogs).addAll(lastLogs).build();
    }
  }
}
