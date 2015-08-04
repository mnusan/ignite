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

package org.apache.ignite.internal.util.nio;

import org.apache.ignite.*;
import org.apache.ignite.internal.util.lang.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.plugin.extensions.communication.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.*;
import java.util.*;

import static org.apache.ignite.internal.util.nio.GridNioSessionMetaKey.*;

/**
 * Grid client for NIO server.
 */
public class GridTcpNioCommunicationClient extends GridAbstractCommunicationClient {
    /** Session. */
    private final GridNioSession ses;

    /** Logger. */
    private final IgniteLogger log;

    /**
     * @param ses Session.
     * @param log Logger.
     */
    public GridTcpNioCommunicationClient(GridNioSession ses, IgniteLogger log) {
        super(null);

        assert ses != null;
        assert log != null;

        this.ses = ses;
        this.log = log;
    }

    /**
     * @return Gets underlying session.
     */
    public GridNioSession session() {
        return ses;
    }

    /** {@inheritDoc} */
    @Override public void doHandshake(IgniteInClosure2X<InputStream, OutputStream> handshakeC) throws IgniteCheckedException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public boolean close() {
        boolean res = super.close();

        if (res)
            ses.close();

        return res;
    }

    /** {@inheritDoc} */
    @Override public void forceClose() {
        super.forceClose();

        ses.close();
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(byte[] data, int len) throws IgniteCheckedException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(ByteBuffer data) throws IgniteCheckedException {
        if (closed())
            throw new IgniteCheckedException("Client was closed: " + this);

        GridNioFuture<?> fut = ses.send(data);

        if (fut.isDone())
            fut.get();
    }

    /** {@inheritDoc} */
    @Override public boolean sendMessage(@Nullable UUID nodeId, Message msg, IgniteInClosure<IgniteException> closure)
        throws IgniteCheckedException {
        // Node ID is never provided in asynchronous send mode.
        assert nodeId == null;

        if (closure != null)
            ses.addMeta(ACK_CLOSURE.ordinal(), closure);

        GridNioFuture<?> fut = ses.send(msg);

        if (fut.isDone()) {
            try {
                fut.get();
            }
            catch (IgniteCheckedException e) {
                if (closure != null)
                    ses.removeMeta(ACK_CLOSURE.ordinal());

                if (log.isDebugEnabled())
                    log.debug("Failed to send message [client=" + this + ", err=" + e + ']');

                if (e.getCause() instanceof IOException)
                    return true;
                else
                    throw new IgniteCheckedException("Failed to send message [client=" + this + ']', e);
            }
        }

        if (closure != null)
            ses.removeMeta(ACK_CLOSURE.ordinal());

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean async() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public long getIdleTime() {
        long now = U.currentTimeMillis();

        // Session can be used for receiving and sending.
        return Math.min(Math.min(now - ses.lastReceiveTime(), now - ses.lastSendScheduleTime()),
            now - ses.lastSendTime());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpNioCommunicationClient.class, this, super.toString());
    }
}
