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

#include "Publication.h"
#include "ClientConductor.h"

using namespace aeron;

Publication::Publication(
    ClientConductor& conductor,
    const std::string& channel,
    std::int32_t streamId,
    std::int32_t sessionId,
    std::int64_t correlationId) :
    m_conductor(conductor),
    m_channel(channel),
    m_correlationId(correlationId),
    m_streamId(streamId),
    m_sessionId(sessionId)
{

}

Publication::~Publication()
{
    m_conductor.releasePublication(this);
}
