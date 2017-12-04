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

package org.apache.ignite.spi.discovery.zk.internal;

import java.util.UUID;
import org.apache.ignite.spi.discovery.DiscoverySpiCustomMessage;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ZkInternalFailNodeMessage implements ZkInternalMessage {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    final UUID nodeId;

    /** */
    final String warning;

    /**
     * @param nodeId Node ID.
     * @param warning Warning to be displayed on all nodes.
     */
    ZkInternalFailNodeMessage(UUID nodeId, String warning) {
        this.nodeId = nodeId;
        this.warning = warning;
    }

    /** {@inheritDoc} */
    @Nullable @Override public DiscoverySpiCustomMessage ackMessage() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public boolean isMutable() {
        return false;
    }
}
