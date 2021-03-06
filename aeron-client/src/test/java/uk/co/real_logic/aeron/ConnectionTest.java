/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.co.real_logic.aeron.common.TermHelper;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.aeron.common.concurrent.logbuffer.*;
import uk.co.real_logic.aeron.common.protocol.DataHeaderFlyweight;
import uk.co.real_logic.aeron.common.protocol.HeaderFlyweight;
import uk.co.real_logic.agrona.status.PositionReporter;

import java.nio.ByteBuffer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.co.real_logic.aeron.common.concurrent.logbuffer.LogBufferDescriptor.TERM_META_DATA_LENGTH;
import static uk.co.real_logic.agrona.BitUtil.align;

public class ConnectionTest
{
    private static final int LOG_BUFFER_SIZE = LogBufferDescriptor.TERM_MIN_LENGTH;
    private static final int POSITION_BITS_TO_SHIFT = Integer.numberOfTrailingZeros(LOG_BUFFER_SIZE);
    private static final byte[] DATA = new byte[36];

    static
    {
        for (int i = 0; i < DATA.length; i++)
        {
            DATA[i] = (byte)i;
        }
    }

    private static final int MESSAGE_LENGTH = DataHeaderFlyweight.HEADER_LENGTH + DATA.length;
    private static final int ALIGNED_FRAME_LENGTH = align(MESSAGE_LENGTH, FrameDescriptor.FRAME_ALIGNMENT);
    private static final long CORRELATION_ID = 0xC044E1AL;
    private static final int SESSION_ID = 0x5E55101D;
    private static final int STREAM_ID = 0xC400E;
    private static final int INITIAL_TERM_ID = 0xEE81D;
    private static final long ZERO_INITIAL_POSITION =
        TermHelper.calculatePosition(INITIAL_TERM_ID, 0, POSITION_BITS_TO_SHIFT, INITIAL_TERM_ID);

    private final UnsafeBuffer rcvBuffer = new UnsafeBuffer(new byte[ALIGNED_FRAME_LENGTH]);
    private final DataHeaderFlyweight dataHeader = new DataHeaderFlyweight();
    private final DataHandler mockDataHandler = mock(DataHandler.class);
    private final PositionReporter mockPositionReporter = mock(PositionReporter.class);

    private LogRebuilder[] rebuilders = new LogRebuilder[TermHelper.BUFFER_COUNT];
    private LogReader[] readers = new LogReader[TermHelper.BUFFER_COUNT];
    private ManagedBuffer[] managedBuffers = new ManagedBuffer[TermHelper.BUFFER_COUNT * 2];
    private Connection connection;
    private int activeIndex;

    @Before
    public void setUp()
    {
        for (int i = 0; i < TermHelper.BUFFER_COUNT; i++)
        {
            final UnsafeBuffer logBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(LOG_BUFFER_SIZE));
            final UnsafeBuffer metaDataBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(TERM_META_DATA_LENGTH));

            rebuilders[i] = new LogRebuilder(logBuffer, metaDataBuffer);
            readers[i] = new LogReader(logBuffer, metaDataBuffer);
        }

        for (int i = 0; i < TermHelper.BUFFER_COUNT * 2; i++)
        {
            managedBuffers[i] = mock(ManagedBuffer.class);
        }

        activeIndex = TermHelper.bufferIndex(INITIAL_TERM_ID, INITIAL_TERM_ID);
        dataHeader.wrap(rcvBuffer, 0);
    }

    @Test
    public void shouldReportCorrectPositionOnReception()
    {
        connection = createConnection(ZERO_INITIAL_POSITION);

        insertDataFrame(offsetOfFrame(0));

        final int messages = connection.poll(Integer.MAX_VALUE);
        assertThat(messages, is(1));

        verify(mockDataHandler).onData(
            any(UnsafeBuffer.class),
            eq(DataHeaderFlyweight.HEADER_LENGTH),
            eq(DATA.length),
            any(Header.class));

        final InOrder inOrder = Mockito.inOrder(mockPositionReporter);
        inOrder.verify(mockPositionReporter).position(ZERO_INITIAL_POSITION);
        inOrder.verify(mockPositionReporter).position(ZERO_INITIAL_POSITION + ALIGNED_FRAME_LENGTH);
    }

    @Test
    public void shouldReportCorrectPositionOnReceptionWithNonZeroPositionInInitialTermId()
    {
        final int initialMessageIndex = 5;
        final int initialTermOffset = offsetOfFrame(initialMessageIndex);
        final long initialPosition =
            TermHelper.calculatePosition(INITIAL_TERM_ID, initialTermOffset, POSITION_BITS_TO_SHIFT, INITIAL_TERM_ID);

        rebuilders[activeIndex].tail(initialTermOffset);

        connection = createConnection(initialPosition);

        insertDataFrame(offsetOfFrame(initialMessageIndex));

        final int messages = connection.poll(Integer.MAX_VALUE);
        assertThat(messages, is(1));

        verify(mockDataHandler).onData(
            any(UnsafeBuffer.class),
            eq(initialTermOffset + DataHeaderFlyweight.HEADER_LENGTH),
            eq(DATA.length),
            any(Header.class));

        final InOrder inOrder = Mockito.inOrder(mockPositionReporter);
        inOrder.verify(mockPositionReporter).position(initialPosition);
        inOrder.verify(mockPositionReporter).position(initialPosition + ALIGNED_FRAME_LENGTH);
    }

    @Test
    public void shouldReportCorrectPositionOnReceptionWithNonZeroPositionInNonInitialTermId()
    {
        final int activeTermId = INITIAL_TERM_ID + 1;
        final int initialMessageIndex = 5;
        final int initialTermOffset = offsetOfFrame(initialMessageIndex);
        final long initialPosition =
            TermHelper.calculatePosition(activeTermId, initialTermOffset, POSITION_BITS_TO_SHIFT, INITIAL_TERM_ID);

        activeIndex = TermHelper.bufferIndex(INITIAL_TERM_ID, activeTermId);
        rebuilders[activeIndex].tail(initialTermOffset);

        connection = createConnection(initialPosition);

        insertDataFrame(offsetOfFrame(initialMessageIndex));

        final int messages = connection.poll(Integer.MAX_VALUE);
        assertThat(messages, is(1));

        verify(mockDataHandler).onData(
            any(UnsafeBuffer.class),
            eq(initialTermOffset + DataHeaderFlyweight.HEADER_LENGTH),
            eq(DATA.length),
            any(Header.class));

        final InOrder inOrder = Mockito.inOrder(mockPositionReporter);
        inOrder.verify(mockPositionReporter).position(initialPosition);
        inOrder.verify(mockPositionReporter).position(initialPosition + ALIGNED_FRAME_LENGTH);
    }

    public Connection createConnection(final long initialPosition)
    {
        return new Connection(
            readers, SESSION_ID, INITIAL_TERM_ID, initialPosition,
            CORRELATION_ID, mockDataHandler, mockPositionReporter, managedBuffers);
    }

    private void insertDataFrame(final int offset)
    {
        dataHeader.termId(INITIAL_TERM_ID)
                  .streamId(STREAM_ID)
                  .sessionId(SESSION_ID)
                  .termOffset(offset)
                  .frameLength(DATA.length + DataHeaderFlyweight.HEADER_LENGTH)
                  .headerType(HeaderFlyweight.HDR_TYPE_DATA)
                  .flags(DataHeaderFlyweight.BEGIN_AND_END_FLAGS)
                  .version(HeaderFlyweight.CURRENT_VERSION);

        dataHeader.buffer().putBytes(dataHeader.dataOffset(), DATA);

        rebuilders[activeIndex].insert(rcvBuffer, 0, ALIGNED_FRAME_LENGTH);
    }

    private int offsetOfFrame(final int index)
    {
        return index * ALIGNED_FRAME_LENGTH;
    }
}
