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

import de.hybris.bootstrap.annotations.UnitTest;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@UnitTest
public class KeyValueMessageRouterUnitTest {
    private Map<String, MessageChannel> targetChannelsMap;
    private String targetMessageChannelKey;
    private KeyValueMessageRouter keyValueMessageRouter;

    @Before
    public void setup() {
        targetMessageChannelKey = "RESUBMIT_FILE";
        targetChannelsMap = new HashMap<>();
        targetChannelsMap.put(targetMessageChannelKey, new DirectChannel());
        targetChannelsMap.put("ERROR_FILE", new DirectChannel());
    }

    @Test
    public void testDetermineTargetChannelFound() {
        keyValueMessageRouter = new KeyValueMessageRouter(targetChannelsMap, targetMessageChannelKey);
        Assert.assertTrue(CollectionUtils.isNotEmpty(keyValueMessageRouter.determineTargetChannels(new GenericMessage("This is generic message."))));
    }

    @Test
    public void testDetermineTargetChannelNotFound() {
        keyValueMessageRouter = new KeyValueMessageRouter(Collections.singletonMap("ERROR_FILE", new DirectChannel()), targetMessageChannelKey);
        Assert.assertTrue(CollectionUtils.isEmpty(keyValueMessageRouter.determineTargetChannels(new GenericMessage("This is generic message."))));
    }

    @After
    public void cleanUp() {
        keyValueMessageRouter = null;
        targetMessageChannelKey = null;
        targetChannelsMap = null;
    }
}
