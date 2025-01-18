/*
 * [y] hybris Platform
 *
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package de.hybris.platform.cloud.azure.hotfolder.spring.impl;

import de.hybris.platform.cloud.azure.hotfolder.constants.AzurecloudhotfolderConstants;

import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.integration.config.SourcePollingChannelAdapterFactoryBean;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.util.Assert;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * A {@link BeanPostProcessor} implementation to limit polling period set in {@link SourcePollingChannelAdapterFactoryBean}
 * for Hot Folders
 */
public class SourcePollingChannelAdapterFactoryBeanPostProcessor
        implements InitializingBean, BeanPostProcessor, BeanFactoryAware
{
    private static final Logger LOG = getLogger(SourcePollingChannelAdapterFactoryBeanPostProcessor.class);
    private BeanFactory beanFactory;
    private long triggerPeriod = AzurecloudhotfolderConstants.TRIGGER_PERIOD;
    private PeriodicTrigger trigger = new PeriodicTrigger(triggerPeriod);

    @Override
    public void afterPropertiesSet()
    {
        Assert.notNull(this.beanFactory, "BeanFactory must not be null");
    }

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException
    {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException
    {
        if (bean instanceof SourcePollingChannelAdapterFactoryBean)
        {
            LOG.debug("Processing bean with name [{}] ", beanName);
            PollerMetadata pollerMetadata = PollerMetadata.getDefaultPollerMetadata(beanFactory);
            pollerMetadata.setTrigger(trigger);

            ((SourcePollingChannelAdapterFactoryBean) bean).setAutoStartup(false);
            ((SourcePollingChannelAdapterFactoryBean) bean).setPollerMetadata(pollerMetadata);
        }
        return bean;
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory)
    {
        this.beanFactory = beanFactory;
    }
}
