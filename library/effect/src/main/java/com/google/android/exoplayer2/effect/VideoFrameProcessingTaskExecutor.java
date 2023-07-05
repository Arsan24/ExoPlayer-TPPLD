/*
 * Copyright 2022 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * Wrapper around a single thread {@link ExecutorService} for executing {@link Task} instances.
 *
 * <p>Public methods can be called from any thread.
 *
 * <p>The wrapper handles calling {@link
 * VideoFrameProcessor.Listener#onError(VideoFrameProcessingException)} for errors that occur during
 * these tasks. The listener is invoked from the {@link ExecutorService}. Errors are assumed to be
 * non-recoverable, so the {@code VideoFrameProcessingTaskExecutor} should be released if an error
 * occurs.
 *
 * <p>{@linkplain #submitWithHighPriority(Task) High priority tasks} are always executed before
 * {@linkplain #submit(Task) default priority tasks}. Tasks with equal priority are executed in FIFO
 * order.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class VideoFrameProcessingTaskExecutor {
  /**
   * Interface for tasks that may throw a {@link GlUtil.GlException} or {@link
   * VideoFrameProcessingException}.
   */
  public interface Task {
    /** Runs the task. */
    void run() throws VideoFrameProcessingException, GlUtil.GlException;
  }

  private static final long RELEASE_WAIT_TIME_MS = 500;

  private final boolean shouldShutdownExecutorService;
  private final ExecutorService singleThreadExecutorService;
  private final VideoFrameProcessor.Listener listener;
  private final Object lock;

  @GuardedBy("lock")
  private final Queue<Task> highPriorityTasks;

  @GuardedBy("lock")
  private boolean shouldCancelTasks;

  /** Creates a new instance. */
  public VideoFrameProcessingTaskExecutor(
      ExecutorService singleThreadExecutorService,
      boolean shouldShutdownExecutorService,
      VideoFrameProcessor.Listener listener) {
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.shouldShutdownExecutorService = shouldShutdownExecutorService;
    this.listener = listener;
    lock = new Object();
    highPriorityTasks = new ArrayDeque<>();
  }

  /** Submits the given {@link Task} to be executed after all pending tasks have completed. */
  @SuppressWarnings("FutureReturnValueIgnored")
  public void submit(Task task) {
    @Nullable RejectedExecutionException executionException = null;
    synchronized (lock) {
      if (shouldCancelTasks) {
        return;
      }
      try {
        wrapTaskAndSubmitToExecutorService(task, /* isFlushOrReleaseTask= */ false);
      } catch (RejectedExecutionException e) {
        executionException = e;
      }
    }

    if (executionException != null) {
      handleException(executionException);
    }
  }

  /** Submits the given {@link Task} to execute, and returns after the task is executed. */
  public void submitAndBlock(Task task) {
    synchronized (lock) {
      if (shouldCancelTasks) {
        return;
      }
    }

    Future<?> future = wrapTaskAndSubmitToExecutorService(task, /* isFlushOrReleaseTask= */ false);
    try {
      future.get();
    } catch (ExecutionException e) {
      handleException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handleException(e);
    }
  }

  /**
   * Submits the given {@link Task} to be executed after the currently running task and all
   * previously submitted high-priority tasks have completed.
   *
   * <p>Tasks that were previously {@linkplain #submit(Task) submitted} without high-priority and
   * have not started executing will be executed after this task is complete.
   */
  public void submitWithHighPriority(Task task) {
    synchronized (lock) {
      if (shouldCancelTasks) {
        return;
      }
      highPriorityTasks.add(task);
    }
    // If the ExecutorService has non-started tasks, the first of these non-started tasks will run
    // the task passed to this method. Just in case there are no non-started tasks, submit another
    // task to run high-priority tasks.
    submit(() -> {});
  }

  /**
   * Flushes all scheduled tasks.
   *
   * <p>During flush, the {@code VideoFrameProcessingTaskExecutor} ignores the {@linkplain #submit
   * submission of new tasks}. The tasks that are submitted before flushing are either executed or
   * canceled when this method returns.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  public void flush() throws InterruptedException {
    synchronized (lock) {
      shouldCancelTasks = true;
      highPriorityTasks.clear();
    }

    CountDownLatch latch = new CountDownLatch(1);
    wrapTaskAndSubmitToExecutorService(
        () -> {
          synchronized (lock) {
            shouldCancelTasks = false;
          }
          latch.countDown();
        },
        /* isFlushOrReleaseTask= */ true);
    latch.await();
  }

  /**
   * Cancels remaining tasks, runs the given release task
   *
   * <p>If {@code shouldShutdownExecutorService} is {@code true}, shuts down the {@linkplain
   * ExecutorService background thread}.
   *
   * @param releaseTask A {@link Task} to execute before shutting down the background thread.
   * @throws InterruptedException If interrupted while releasing resources.
   */
  public void release(Task releaseTask) throws InterruptedException {
    synchronized (lock) {
      shouldCancelTasks = true;
      highPriorityTasks.clear();
    }
    Future<?> unused =
        wrapTaskAndSubmitToExecutorService(releaseTask, /* isFlushOrReleaseTask= */ true);
    if (shouldShutdownExecutorService) {
      singleThreadExecutorService.shutdown();
      if (!singleThreadExecutorService.awaitTermination(RELEASE_WAIT_TIME_MS, MILLISECONDS)) {
        listener.onError(
            new VideoFrameProcessingException(
                "Release timed out. OpenGL resources may not be cleaned up properly."));
      }
    }
  }

  private Future<?> wrapTaskAndSubmitToExecutorService(
      Task defaultPriorityTask, boolean isFlushOrReleaseTask) {
    return singleThreadExecutorService.submit(
        () -> {
          try {
            synchronized (lock) {
              if (shouldCancelTasks && !isFlushOrReleaseTask) {
                return;
              }
            }

            @Nullable Task nextHighPriorityTask;
            while (true) {
              synchronized (lock) {
                // Lock only polling to prevent blocking the public method calls.
                nextHighPriorityTask = highPriorityTasks.poll();
              }
              if (nextHighPriorityTask == null) {
                break;
              }
              nextHighPriorityTask.run();
            }
            defaultPriorityTask.run();
          } catch (Exception e) {
            handleException(e);
          }
        });
  }

  private void handleException(Exception exception) {
    synchronized (lock) {
      if (shouldCancelTasks) {
        // Ignore exception after cancelation as it can be caused by a previously reported exception
        // that is the reason for the cancelation.
        return;
      }
      shouldCancelTasks = true;
    }
    listener.onError(VideoFrameProcessingException.from(exception));
  }
}
