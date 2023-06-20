/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.remoting;

import io.netty.channel.Channel;
import java.util.concurrent.ExecutorService;
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public interface RemotingServer extends RemotingService {

    /**
     *  注册处理器
     * @param requestCode   请求码
     * @param processor     处理器
     * @param executor      线程池
     *    这三者是绑定关系：
     *       根据请求的code  找到处理对应请求的处理器与线程池 并完成业务处理。
     */
    void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
        final ExecutorService executor);

    /**
     *  注册缺省处理器
     * @param processor  缺省处理器
     * @param executor   线程池
     */
    void registerDefaultProcessor(final NettyRequestProcessor processor, final ExecutorService executor);
    // 获取服务端口
    int localListenPort();

    /**
     *  根据 请求码 获取 处理器和线程池
     * @param requestCode  请求码
     * @return 处理器和线程池Pair
     */
    Pair<NettyRequestProcessor, ExecutorService> getProcessorPair(final int requestCode);

    Pair<NettyRequestProcessor, ExecutorService> getDefaultProcessorPair();

    RemotingServer newRemotingServer(int port);

    void removeRemotingServer(int port);

    /**
     *  同步调用
     * @param channel   通信通道
     * @param request   业务请求对象
     * @param timeoutMillis   超时时间
     * @return  响应结果封装
     */
    RemotingCommand invokeSync(final Channel channel, final RemotingCommand request,
        final long timeoutMillis) throws InterruptedException, RemotingSendRequestException,
        RemotingTimeoutException;

    /**
     *  异步调用
     * @param channel  通信通道
     * @param request  业务请求对象
     * @param timeoutMillis  超时时间
     * @param invokeCallback  响应结果回调对象
     */
    void invokeAsync(final Channel channel, final RemotingCommand request, final long timeoutMillis,
        final InvokeCallback invokeCallback) throws InterruptedException,
        RemotingTooMuchRequestException, RemotingTimeoutException, RemotingSendRequestException;

    /**
     *  单向调用 (不关注返回结果)
     * @param channel   通信通道
     * @param request   业务请求对象
     * @param timeoutMillis  超时时间
     */
    void invokeOneway(final Channel channel, final RemotingCommand request, final long timeoutMillis)
        throws InterruptedException, RemotingTooMuchRequestException, RemotingTimeoutException,
        RemotingSendRequestException;

}
