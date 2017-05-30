/*
 * Copyright (C) 2017-2017 DataStax Inc.
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
package com.datastax.oss.driver.internal.core.protocol;

import io.netty.handler.codec.DecoderException;

/**
 * Wraps an error while decoding an incoming protocol frame.
 *
 * <p>This is only used internally, never exposed to the client.
 */
public class FrameDecodingException extends DecoderException {
  public final int streamId;

  public FrameDecodingException(int streamId, Throwable cause) {
    super("Error decoding frame for streamId " + streamId, cause);
    this.streamId = streamId;
  }
}
