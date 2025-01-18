/*
 * [y] hybris Platform
 *
 * Copyright (c) 2023 SAP SE or an SAP affiliate company. All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package de.hybris.platform.cloud.hotfolder.spring.integration.router;

import org.springframework.integration.router.AbstractMessageRouter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class KeyValueMessageRouter extends AbstractMessageRouter {

    private final Map<String, MessageChannel> targetChannelsMap;

    private final String targetMessageChannelKey;


    public KeyValueMessageRouter(Map<String, MessageChannel> targetChannelsMap, String targetMessageChannelKey) {
        Assert.notEmpty(targetChannelsMap, "targetChannelsMap cannot be empty");
        Assert.hasText(targetMessageChannelKey, "targetMessageChannelKey cannot be empty");
        this.targetChannelsMap = targetChannelsMap;
        this.targetMessageChannelKey = targetMessageChannelKey;
    }

    @Override
    protected Collection<MessageChannel> determineTargetChannels(Message<?> message) {
        MessageChannel targetChannel = targetChannelsMap.get(targetMessageChannelKey);
        return Objects.nonNull(targetChannel) ? Collections.singletonList(targetChannel) : Collections.emptyList();
    }
}
