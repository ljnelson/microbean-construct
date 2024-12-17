/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.construct;

import java.lang.System.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.ProcessingEnvironment;

import static java.lang.System.getLogger;

import static java.lang.System.Logger.Level.ERROR;

/**
 * A utility class that can {@linkplain #get() supply} a {@link ProcessingEnvironment} suitable for use at runtime.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #get()
 *
 * @see ProcessingEnvironment
 *
 * @see Domain
 */
public final class RuntimeProcessingEnvironment {


  /*
   * Static fields.
   */


  private static final Logger LOGGER = getLogger(RuntimeProcessingEnvironment.class.getName());

  private static final CountDownLatch processorLatch = new CountDownLatch(1);

  private static final AtomicReference<CompletableFuture<ProcessingEnvironment>> r =
    new AtomicReference<>(startBlockingCompilationTask(new CompletableFuture<>()));


  /*
   * Constructors.
   */


  private RuntimeProcessingEnvironment() {
    super();
  }


  /*
   * Static methods.
   */


  /**
   * Returns a non-{@code null} {@link ProcessingEnvironment} suitable for use at runtime.
   *
   * <p>The returned {@link ProcessingEnvironment} and the objects it supplies are not guaranteed to be safe for
   * concurrent use by multiple threads.</p>
   *
   * @return a non-{@code null} {@link ProcessingEnvironment}
   *
   * @exception java.util.concurrent.CancellationException if the task of setting up the {@link ProcessingEnvironment}
   * was cancelled
   *
   * @exception java.util.concurrent.CompletionException if an error occurs
   *
   * @see ProcessingEnvironment
   *
   * @see Domain
   */
  public static final ProcessingEnvironment get() {
    CompletableFuture<ProcessingEnvironment> f = r.get();
    if (f == null) {
      f = new CompletableFuture<>();
      if (r.compareAndSet(null, f)) {
        startBlockingCompilationTask(f);
      } else {
        f = r.get();
      }
    }
    return f.join();
  }

  /**
   * Closes and unblocks the machinery responsible for supplying {@link ProcessingEnvironment} instances.
   */
  public static final void close() {
    final Future<?> f = r.get();
    if (f != null) {
      try {
        f.cancel(true);
      } finally {
        r.set(null);
        processorLatch.countDown();
      }
    }
  }

  private static final CompletableFuture<ProcessingEnvironment> startBlockingCompilationTask(final CompletableFuture<ProcessingEnvironment> f) {
    // Use a virtual thread since the BlockingCompilationTask will spend its entire time blocked/parked once it has
    // completed the CompletableFuture.
    //
    // There may be two (or more) threads involved here, depending on the compiler implementation: this virtual one that
    // offloads the setup of the compiler/annotation processing machinery, and the thread that said machinery may use to
    // run the actual compilation task (which blocks forever) (see CompilationTask#call()).
    Thread.ofVirtual()
      .name(RuntimeProcessingEnvironment.class.getName())
      .uncaughtExceptionHandler((thread, exception) -> {
          f.completeExceptionally(exception);
          if (LOGGER.isLoggable(ERROR)) {
            LOGGER.log(ERROR, exception.getMessage(), exception);
          }
        })
      .start(new BlockingCompilationTask(f, processorLatch));
    return f;
  }

}
