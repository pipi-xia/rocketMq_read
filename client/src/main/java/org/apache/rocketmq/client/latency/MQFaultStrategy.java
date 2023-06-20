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

package org.apache.rocketmq.client.latency;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.logging.org.slf4j.Logger;
import org.apache.rocketmq.logging.org.slf4j.LoggerFactory;

public class MQFaultStrategy {
    private final static Logger log = LoggerFactory.getLogger(MQFaultStrategy.class);
    private final LatencyFaultTolerance<String> latencyFaultTolerance = new LatencyFaultToleranceImpl();

    private boolean sendLatencyFaultEnable = false; // 是否开启延迟容错策略

    private long[] latencyMax = {50L, 100L, 550L, 1000L, 2000L, 3000L, 15000L}; // 延迟数组
    private long[] notAvailableDuration = {0L, 0L, 30000L, 60000L, 120000L, 180000L, 600000L};  // 不可用时长，配合上面延迟数组使用


    public long[] getNotAvailableDuration() {
        return notAvailableDuration;
    }

    public void setNotAvailableDuration(final long[] notAvailableDuration) {
        this.notAvailableDuration = notAvailableDuration;
    }

    public long[] getLatencyMax() {
        return latencyMax;
    }

    public void setLatencyMax(final long[] latencyMax) {
        this.latencyMax = latencyMax;
    }

    public boolean isSendLatencyFaultEnable() {
        return sendLatencyFaultEnable;
    }

    public void setSendLatencyFaultEnable(final boolean sendLatencyFaultEnable) {
        this.sendLatencyFaultEnable = sendLatencyFaultEnable;
    }

    /**
     * 选择一个messageQueue
     * 这个会判断是否开启了发送延迟容错的开关, 通过延迟容错, 选出一个被认为最靠谱的Broker出来进行发送消息
     * 在client端会维护一个"Broker发送延迟"的关系, 根据这个关系来选择一个延迟比较低的Broker来发送消息
     * 以保证最大限度地利用Broker的能力, 剔除已经宕机, 不可用或者发送延迟级别较高的Broker, 尽量保证消息正常发送
     */
    public MessageQueue selectOneMessageQueue(final TopicPublishInfo tpInfo, final String lastBrokerName) {
        // 发送延迟容错开关, 默认是关闭的
        if (this.sendLatencyFaultEnable) {
            try {
                // 获取一个自增的序号
                int index = tpInfo.getSendWhichQueue().incrementAndGet();
                for (int i = 0; i < tpInfo.getMessageQueueList().size(); i++) {
                    // 根据序号来取模得到一个queue, 所以是轮询着来找的
                    int pos = index++ % tpInfo.getMessageQueueList().size();
                    MessageQueue mq = tpInfo.getMessageQueueList().get(pos);
                    // 如果是第一次发送消息, 或者此次选出来的broker和上次的一样, 才会选择这个broker, 否则就继续往下走
                    if (!StringUtils.equals(lastBrokerName, mq.getBrokerName()) && latencyFaultTolerance.isAvailable(mq.getBrokerName())) {
                        return mq;
                    }
                }
                // 如果上面的步骤没有选出来broker, 就选一个延迟较低的broker出来
                final String notBestBroker = latencyFaultTolerance.pickOneAtLeast();
                int writeQueueNums = tpInfo.getWriteQueueIdByBroker(notBestBroker);
                if (writeQueueNums > 0) {
                    final MessageQueue mq = tpInfo.selectOneMessageQueue();
                    if (notBestBroker != null) {
                        mq.setBrokerName(notBestBroker);
                        mq.setQueueId(tpInfo.getSendWhichQueue().incrementAndGet() % writeQueueNums);
                    }
                    return mq;
                } else {
                    latencyFaultTolerance.remove(notBestBroker);
                }
            } catch (Exception e) {
                log.error("Error occurred when selecting message queue", e);
            }
            // 随机选一个broker
            return tpInfo.selectOneMessageQueue();
        }
        // 随机选一个broker出来, 并不会选择上次发送的broker, 分摊broker压力
        return tpInfo.selectOneMessageQueue(lastBrokerName);
    }

    public void updateFaultItem(final String brokerName, final long currentLatency, boolean isolation) {
        if (this.sendLatencyFaultEnable) {
            long duration = computeNotAvailableDuration(isolation ? 30000 : currentLatency);
            this.latencyFaultTolerance.updateFaultItem(brokerName, currentLatency, duration);
        }
    }

    // 更新broker, 包括延迟信息
    private long computeNotAvailableDuration(final long currentLatency) {
        for (int i = latencyMax.length - 1; i >= 0; i--) {
            if (currentLatency >= latencyMax[i])
                return this.notAvailableDuration[i];
        }

        return 0;
    }
}
