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
package de.hybris.platform.cloud.azure.hotfolder.remote.file.filters;

import com.azure.storage.blob.BlobClient;
import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AzureBlobAcceptUnprocessedFilesListFilterUnitTest {


    private static final String HOTFOLDER_REMOTE_PATH = "/hybris/master";
    private static final String HOTFOLDER_REMOTE_PROCESSING_PATH = "/hybris/master/processing";
    private static final String PROCESSING_FILE_REMOTE_PATH = HOTFOLDER_REMOTE_PROCESSING_PATH + "/testfile.txt";
    private static final String TEST_FILE_2 = "/testfile2.txt";
    private static final String PROCESSING_FILE_2_REMOTE_PATH = HOTFOLDER_REMOTE_PROCESSING_PATH + TEST_FILE_2;
    private static final String PREFIX = "";
    private static int maxUnprocessedFileCheckCount = 10;

    @Mock
    private BlobClient cloudBlob;
    private AzureBlobAcceptUnprocessedFilesListFilter azureBlobAcceptUnprocessedFilesListFilter;

    private ConcurrentMetadataStore inProcessingMetadataStore;

    @Before
    public void setUp() {
        ConcurrentMetadataStore metadataStore = new SimpleMetadataStore();
        inProcessingMetadataStore = new SimpleMetadataStore();
        inProcessingMetadataStore.put(HOTFOLDER_REMOTE_PATH + TEST_FILE_2, "any_value");
        azureBlobAcceptUnprocessedFilesListFilter = new AzureBlobAcceptUnprocessedFilesListFilter(metadataStore, PREFIX, inProcessingMetadataStore, HOTFOLDER_REMOTE_PROCESSING_PATH, HOTFOLDER_REMOTE_PATH, maxUnprocessedFileCheckCount);
    }

    @Test
    public void testUnproceedFileAccepted() {
        Mockito.when(cloudBlob.getBlobName()).thenReturn(PROCESSING_FILE_REMOTE_PATH);
        boolean fileAccepted = testUnprocessedFileInternal(maxUnprocessedFileCheckCount + 1);
        Assert.assertTrue(fileAccepted);
    }

    @Test
    public void testUnproceedFileNotAccepted() {
        Mockito.when(cloudBlob.getBlobName()).thenReturn(PROCESSING_FILE_REMOTE_PATH);
        boolean fileAccepted = testUnprocessedFileInternal(maxUnprocessedFileCheckCount);
        Assert.assertFalse(fileAccepted);
    }

    @Test
    public void testUnproceedFileNotAcceptedIfAlreadyInProcessing() {
        Mockito.when(cloudBlob.getBlobName()).thenReturn(PROCESSING_FILE_2_REMOTE_PATH);
        boolean fileAccepted = testUnprocessedFileInternal(maxUnprocessedFileCheckCount);
        Assert.assertFalse(fileAccepted);
    }

    private boolean testUnprocessedFileInternal(int checkCounter) {
        for (int i = 0; i < checkCounter; i++) {
            if (azureBlobAcceptUnprocessedFilesListFilter.accept(cloudBlob)) {
                return true;
            }
        }
        return false;
    }

    @After
    public void cleanUp() {
        azureBlobAcceptUnprocessedFilesListFilter = null;
        inProcessingMetadataStore = null;
    }
}
