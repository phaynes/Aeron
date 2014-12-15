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

#ifndef INCLUDED_AERON_CONCURRENT_BROADCAST_BUFFER_DESCRIPTOR__
#define INCLUDED_AERON_CONCURRENT_BROADCAST_BUFFER_DESCRIPTOR__

#include <util/Index.h>
#include <concurrent/AtomicBuffer.h>
#include <util/BitUtil.h>

namespace aeron { namespace common { namespace concurrent { namespace broadcast {

namespace BroadcastBufferDescriptor {

static const util::index_t TAIL_COUNTER_OFFSET = util::BitUtil::CACHE_LINE_SIZE;
static const util::index_t LATEST_COUNTER_OFFSET = TAIL_COUNTER_OFFSET + sizeof(std::int64_t);
static const util::index_t TRAILER_LENGTH = util::BitUtil::CACHE_LINE_SIZE * 2;

inline static void checkCapacity(util::index_t capacity)
{
    if (!util::BitUtil::isPowerOfTwo(capacity))
    {
        throw util::IllegalStateException(
            util::strPrintf("Capacity must be a positive power of 2 + TRAILER_LENGTH: capacity=%d", capacity), SOURCEINFO);
    }
}

}}}}}

#endif