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
package de.hybris.platform.cloud.azure.hotfolder.remote.inbound;

import de.hybris.platform.cloud.azure.hotfolder.remote.file.RemoteFileHeaders;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;
import org.springframework.context.Lifecycle;
import org.springframework.integration.endpoint.AbstractMessageSource;
import org.springframework.integration.file.FileHeaders;
import org.springframework.util.Assert;

import java.io.File;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AzureBlobUnprocessedFileMessageSource extends AbstractMessageSource<File> implements Lifecycle {

    private final BlockingQueue<AzureBlobFileInfo> toBeReceived = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile int maxFetchSize = Integer.MIN_VALUE;

    private AzureBlobUnprocessedFileExecutor unprocessedFileExecutor;

    @Override
    public void start() {
        this.running.set(true);
    }

    @Override
    public void stop() {
        this.running.compareAndSet(true, false);
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    protected Object doReceive() {
        Assert.state(this.running.get(), this.getComponentName() + " is not running");
        final AzureBlobFileInfo file = poll();
        if (file != null) {
            return getMessageBuilderFactory()
                    .withPayload(file.getRemoteDirectory() + file.getFilename())
                    .setHeader(FileHeaders.REMOTE_DIRECTORY, file.getRemoteDirectory())
                    .setHeader(FileHeaders.REMOTE_FILE, file.getFilename())
                    .setHeader(RemoteFileHeaders.REMOTE_FILE_DELETED, false)
                    .build();
        }
        return null;
    }

    @Override
    public String getComponentType() {
        return "azure:inbound-reprocess-channel-adapter";
    }

    protected AzureBlobFileInfo poll() {
        if (this.toBeReceived.isEmpty()) {
            fetchUnprocessedFiles();
        }
        return this.toBeReceived.poll();
    }

    protected void fetchUnprocessedFiles() {
        final List<AzureBlobFileInfo> azureBlobFileInfos
                = getUnprocessedFileExecutor().fetchUnprocessedFilesInfo(getMaxFetchSize());
        this.toBeReceived.addAll(azureBlobFileInfos);
    }

    public void setMaxFetchSize(final int maxFetchSize) {
        this.maxFetchSize = maxFetchSize;
    }

    public int getMaxFetchSize() {
        return this.maxFetchSize;
    }

    public AzureBlobUnprocessedFileExecutor getUnprocessedFileExecutor() {
        return unprocessedFileExecutor;
    }

    public void setUnprocessedFileExecutor(AzureBlobUnprocessedFileExecutor unprocessedFileExecutor) {
        this.unprocessedFileExecutor = unprocessedFileExecutor;
    }
}
