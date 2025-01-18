/*
 * [y] hybris Platform
 *
 * Copyright (c) 2024 SAP SE or an SAP affiliate company. All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package de.hybris.platform.cloud.azure.hotfolder.remote.file.filters;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.integration.metadata.ConcurrentMetadataStore;

import com.azure.storage.blob.BlobClient;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Extending {@link AzureBlobPersistentAcceptOnceListFilter}.
 * {@link AzureBlobAcceptUnprocessedFilesListFilter} filters for {@link com.azure.storage.blob.BlobClient} which are files and
 * have not already been accepted for processing but are present in processing folder.
 */
public class AzureBlobAcceptUnprocessedFilesListFilter extends AzureBlobPersistentAcceptOnceListFilter {
    private static final Logger LOG = getLogger(AzureBlobAcceptUnprocessedFilesListFilter.class);

    private ConcurrentMetadataStore inprocessingFilesMetadataStore;
    private String azureHotfolderProcessingRemotePath;
    private String azureHotfolderRemotePath;
    private int maxUnprocessedFileCheckCount;

    public AzureBlobAcceptUnprocessedFilesListFilter(ConcurrentMetadataStore store, String prefix, ConcurrentMetadataStore inprocessingFilesMetadataStore, String azureHotfolderProcessingRemotePath, String azureHotfolderRemotePath, int maxUnprocessedFileCheckCount) {
        super(store, prefix);
        this.inprocessingFilesMetadataStore = inprocessingFilesMetadataStore;
        this.azureHotfolderProcessingRemotePath = azureHotfolderProcessingRemotePath;
        this.azureHotfolderRemotePath = azureHotfolderRemotePath;
        this.maxUnprocessedFileCheckCount = maxUnprocessedFileCheckCount;
    }

    @Override
    public boolean accept(final BlobClient file) {
        try {
            return file != null && this.isFileEligibleForReprocessing(file);
        } catch (final RuntimeException ex) {
            LOG.error("Exception whilst filtering for duplicate file.", ex);
            return false;
        }
    }

    private boolean isFileEligibleForReprocessing(BlobClient file) {
        String processingRemoteFilePathKey = this.buildKey(file);
        LOG.info("Checking if file '{}' is unprocessed.", processingRemoteFilePathKey);
        String equivalentRemoteFilePath = findEquivalentRemoteFilePathKey(processingRemoteFilePathKey);
        LOG.debug("Prepared equivalent remote-path '{}' for processing file '{}", equivalentRemoteFilePath, processingRemoteFilePathKey);
        String valueProcessingFilestore = inprocessingFilesMetadataStore.get(equivalentRemoteFilePath);
        if (StringUtils.isEmpty(valueProcessingFilestore)) {
            LOG.info("File '{}' is UNPROCESSED. File is not being proceessed by hotfolder.", processingRemoteFilePathKey);
            String valueReprocessingFileStoreCounter = store.get(processingRemoteFilePathKey);
            int fileCheckCount = 1;
            if (StringUtils.isEmpty(valueReprocessingFileStoreCounter)) {
                store.put(processingRemoteFilePathKey, String.valueOf(fileCheckCount));
            } else {
                fileCheckCount = Integer.parseInt(valueReprocessingFileStoreCounter) + 1;
                store.put(processingRemoteFilePathKey, String.valueOf(fileCheckCount));
            }
            LOG.debug("Unprocessed file '{}' is checked {} time(s).", processingRemoteFilePathKey, fileCheckCount);
            if (fileCheckCount > maxUnprocessedFileCheckCount) {
                LOG.info("Unprocessed file '{}', exceeding max. check count {}.", processingRemoteFilePathKey, maxUnprocessedFileCheckCount);
                LOG.info("Removing unprocessed file '{}' from Unprocessed Files Metadata Store.", processingRemoteFilePathKey);
                store.remove(processingRemoteFilePathKey);
                return true;
            }
        } else {
            LOG.info("File '{}' is found to be inprocessing.", processingRemoteFilePathKey);
        }
        LOG.info("No action will be taken on Unprocessed file '{}", processingRemoteFilePathKey);
        return false;
    }

    private String findEquivalentRemoteFilePathKey(String processingRemoteFilePathKey) {
        return processingRemoteFilePathKey.replaceFirst(this.prefix + azureHotfolderProcessingRemotePath, this.prefix + azureHotfolderRemotePath);
    }

}
