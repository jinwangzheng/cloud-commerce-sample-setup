/*
 * [y] hybris Platform
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company. All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package de.hybris.platform.cloud.azure.hotfolder.remote.session;

import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.Assert;

import java.io.*;
import java.time.ZoneOffset;
import java.util.*;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.slf4j.LoggerFactory.getLogger;

public class TestBlobSession implements ExtendedSession<BlobClient>
{
    private static final Logger LOG = getLogger(TestBlobSession.class);

    private final String remoteDirectory;
    private final BlobServiceClient client;
    private String remoteProcessingDirectory;
    private final Map<String, File> files = new HashMap<>();
    private final List<String> filesRead = new LinkedList<>();

    public TestBlobSession(final String connectionString, final String remoteDirectory)
    {
        client = new BlobServiceClientBuilder()
              .connectionString(connectionString)
              .buildClient();
        Assert.notNull(remoteDirectory, "remoteDirectory must not be null");
        this.remoteDirectory = remoteDirectory;
        clean();
    }

    //<editor-fold desc="Test helper methods">

    public void setFiles(final List<File> files)
    {
        for (final File file : files)
        {
            this.files.put(remoteDirectory + File.separator + file.getName(), file);
        }
        LOG.debug("Setting session to return files {}", this.files);
    }

    public void setFilesInProcessing(final List<File> files)
    {
        for (final File file : files)
        {
            this.files.put(remoteProcessingDirectory + File.separator + file.getName(), file);
        }
        LOG.debug("Setting session to return files {}", this.files);
    }

    public boolean fileRead(final String path)
    {
        LOG.debug("Checking if path [{}] was logged as read in {}", path, this.filesRead);
        return this.filesRead.stream().anyMatch(read -> read.endsWith(path));
    }

    public void clean()
    {
        this.files.clear();
        this.filesRead.clear();
    }

    //</editor-fold>

    //<editor-fold description="Methods implemented for test class">

    @Override
    public BlobClient[] list(final String path)
    {
        LOG.debug("Polling for [{}]", path);
        return files.values().stream()
                .map(this::createBlockBlob)
                .toArray(BlobClient[]::new);
    }

    @Override
    public void read(final String path, final OutputStream out) throws IOException
    {
        LOG.debug("Reading [{}]", path);
        final File file = files.get(path);
        if (file == null)
        {
            throw new FileNotFoundException("File not found: " + path);
        }
        FileUtils.copyFile(file, out);
        this.filesRead.add(path);
    }

    @Override
    public BlobClient get(final String path) throws IOException
    {
        LOG.debug("Get [{}]", path);
        final File file = files.get(path);
        if (file == null)
        {
            throw new FileNotFoundException("File not found: " + path);
        }
        return createBlockBlob(file);
    }


    public BlobClient createBlockBlob(final File file)
    {
        try
        {
            final BlobProperties entryProperties = mock(BlobProperties.class);
            final BlobClient cloudBlockBlob = mock(BlobClient.class);

            Instant instant = Instant.ofEpochMilli(file.lastModified());
            OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.UTC);

            given(cloudBlockBlob.getProperties()).willReturn(entryProperties);
            given(cloudBlockBlob.getProperties().getLastModified()).willReturn(offsetDateTime);
            given(cloudBlockBlob.getProperties().getBlobSize()).willReturn(200L);
            given(cloudBlockBlob.getBlobName()).willReturn("junit/hotfolder/" + file.getName());

            return cloudBlockBlob;
        }
        catch (final BlobStorageException ex)
        {
            return null;
        }
    }

    @Override
    public boolean remove(final String path)
    {
        LOG.debug("Removing [{}]", path);
        return true;
    }

    @Override
    public Object getClientInstance()
    {
        return client;
    }

    //</editor-fold>

    //<editor-fold description="Stuff don't need to worry about implementing for test">
    @Override
    public void write(final InputStream inputStream, final String destination)
    {

    }

    @Override
    public void append(final InputStream inputStream, final String destination)
    {

    }

    @Override
    public boolean mkdir(final String directory)
    {
        return false;
    }

    @Override
    public boolean rmdir(final String directory)
    {
        return false;
    }

    @Override
    public void rename(final String pathFrom, final String pathTo)
    {

    }

    @Override
    public void close()
    {

    }

    @Override
    public boolean isOpen()
    {
        return false;
    }

    @Override
    public boolean exists(final String path)
    {
        return false;
    }

    @Override
    public String[] listNames(final String path)
    {
        return new String[0];
    }

    @Override
    public InputStream readRaw(final String source)
    {
        return null;
    }

    @Override
    public boolean finalizeRaw()
    {
        return false;
    }

    @Override
    public String getHostPort() {
        return "localhost:8080";
    }

    //</editor-fold>

    public void setRemoteProcessingDirectory(String remoteProcessingDirectory) {
        this.remoteProcessingDirectory = remoteProcessingDirectory;
    }
}
