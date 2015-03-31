/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal;

import org.apache.ignite.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.internal.util.typedef.internal.*;

import javax.cache.event.*;
import java.io.*;
import java.nio.channels.*;
import java.util.concurrent.*;

/**
 * Marshaller context implementation.
 */
public class MarshallerContextImpl extends MarshallerContextAdapter {
    /** */
    private final CountDownLatch latch = new CountDownLatch(1);

    /** */
    private final File workDir;

    /** */
    private IgniteLogger log;

    /** */
    private volatile GridCacheAdapter<Integer, String> cache;

    /** Non-volatile on purpose. */
    private int failedCnt;

    /**
     * @throws IgniteCheckedException In case of error.
     */
    public MarshallerContextImpl() throws IgniteCheckedException {
        workDir = U.resolveWorkDirectory("marshaller", false);
    }

    /**
     * @param ctx Kernal context.
     * @throws IgniteCheckedException In case of error.
     */
    public void onMarshallerCacheStarted(GridKernalContext ctx) throws IgniteCheckedException {
        ctx.cache().marshallerCache().context().continuousQueries().executeInternalQuery(
            new ContinuousQueryListener(log, workDir),
            null,
            true,
            true
        );
    }

    /**
     * @param ctx Kernal context.
     * @throws IgniteCheckedException In case of error.
     */
    public void onMarshallerCachePreloaded(GridKernalContext ctx) throws IgniteCheckedException {
        assert ctx != null;

        log = ctx.log(MarshallerContextImpl.class);

        cache = ctx.cache().marshallerCache();

        latch.countDown();
    }

    /** {@inheritDoc} */
    @Override protected boolean registerClassName(int id, String clsName) throws IgniteCheckedException {
        GridCacheAdapter<Integer, String> cache0 = cache;

        if (cache0 == null)
            return false;

        String old;

        try {
            old = cache0.tryPutIfAbsent(id, clsName);

            if (old != null && !old.equals(clsName)) {
                U.quietAndWarn(log, "Type ID collision detected, may affect performance " +
                    "(set idMapper property on marshaller to fix) [id=" + id + ", clsName1=" + clsName +
                    "clsName2=" + old + ']');

                return false;
            }

            failedCnt = 0;

            return true;
        }
        catch (CachePartialUpdateCheckedException | GridCacheTryPutFailedException e) {
            if (++failedCnt > 10) {
                U.quietAndWarn(log, e, "Failed to register marshalled class for more than 10 times in a row " +
                    "(may affect performance)");

                failedCnt = 0;
            }

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override protected String className(int id) throws IgniteCheckedException {
        if (cache == null)
            U.awaitQuiet(latch);

        String clsName = cache.get(id);

        if (clsName == null) {
            File file = new File(workDir, id + ".classname");

            try (
                FileInputStream in = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))
            ) {
                FileLock lock = in.getChannel().lock(0, Long.MAX_VALUE, true);

                try {
                    clsName = reader.readLine();
                }
                finally {
                    lock.release();
                }
            }
            catch (IOException e) {
                throw new IgniteCheckedException("Failed to read class name from file [id=" + id +
                    ", file=" + file.getAbsolutePath() + ']', e);
            }
        }

        return clsName;
    }

    /**
     */
    private static class ContinuousQueryListener implements CacheEntryUpdatedListener<Integer, String> {
        /** */
        private final IgniteLogger log;

        /** */
        private final File workDir;

        /**
         * @param log Logger.
         * @param workDir Work directory.
         */
        private ContinuousQueryListener(IgniteLogger log, File workDir) {
            this.log = log;
            this.workDir = workDir;
        }

        /** {@inheritDoc} */
        @Override public void onUpdated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
            throws CacheEntryListenerException {
            for (CacheEntryEvent<? extends Integer, ? extends String> evt : events) {
                assert evt.getOldValue() == null;

                File file = new File(workDir, evt.getKey() + ".classname");

                try (
                    FileOutputStream out = new FileOutputStream(file);
                    Writer writer = new OutputStreamWriter(out)
                ) {
                    FileLock lock  = out.getChannel().lock();

                    try {
                        writer.write(evt.getValue());

                        writer.flush();
                    }
                    finally {
                        lock.release();
                    }
                }
                catch (IOException e) {
                    U.error(log, "Failed to write class name to file [id=" + evt.getKey() +
                        ", clsName=" + evt.getValue() + ", file=" + file.getAbsolutePath() + ']', e);
                }
            }
        }
    }
}
