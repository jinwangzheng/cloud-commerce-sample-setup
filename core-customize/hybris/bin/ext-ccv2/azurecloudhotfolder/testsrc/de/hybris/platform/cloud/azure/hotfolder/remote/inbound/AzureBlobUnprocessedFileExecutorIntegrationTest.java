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

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.TestBlobSession;
import de.hybris.platform.cloud.commons.spring.util.NeedsRunningSpringServices;
import de.hybris.platform.servicelayer.ExtendedServicelayerBaseTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.collections4.CollectionUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@IntegrationTest
@NeedsRunningSpringServices(roles = {"integration", "yHotfolderServices"})
public class AzureBlobUnprocessedFileExecutorIntegrationTest extends ExtendedServicelayerBaseTest {
    private static final Logger LOG = getLogger(AzureBlobUnprocessedFileExecutorIntegrationTest.class);

    private static final String FILE_ROOT_DIR = "azurecloudhotfolder/test/processing/";

    @Resource
    private AzureBlobUnprocessedFileExecutor azureBlobUnprocessedFileExecutor;
    @Resource
    private TestBlobSession testBlobSession;

    @Resource
    private ConfigurationService configurationService;

    @Before
    public void setUp() throws Exception {
        createCoreData();
    }

    private File setSessionToReturnFile(final String path) {
        final ClassLoader classLoader = this.getClass().getClassLoader();
        final File file = Optional.of(path)
                .map(classLoader::getResource)
                .map(URL::getFile)
                .map(File::new)
                .filter(File::exists)
                .orElseThrow(() -> new RuntimeException("File not found: " + path));

        testBlobSession.setFilesInProcessing(Collections.singletonList(file));
        return file;
    }

    @Test
    public void testFetchUnprocessedFilesInfo() throws InterruptedException {
        final String testFile = "customer-01.csv";
        File file = setSessionToReturnFile(FILE_ROOT_DIR + testFile);
        List<AzureBlobFileInfo> azureBlobFileInfos = null;
        int retryCount = configurationService.getConfiguration().getInt("cloud.hotfolder.unprocessed.file.metadata.check.max-count", 1) + 5;
        for (int i = 0; i < retryCount; i++) {
            azureBlobFileInfos = azureBlobUnprocessedFileExecutor.fetchUnprocessedFilesInfo(1);
            if (CollectionUtils.isNotEmpty(azureBlobFileInfos)) {
                break;
            }
        }
        Assertions.assertThat(CollectionUtils.isNotEmpty(azureBlobFileInfos)).isTrue();
    }

    @After
    public void cleanUp() {
        testBlobSession.clean();
    }
}
