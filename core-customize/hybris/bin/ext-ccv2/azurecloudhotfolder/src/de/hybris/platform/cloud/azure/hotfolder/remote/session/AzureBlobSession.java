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

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobBeginCopyOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Implementation of {@link Session} for Azure Blob Storage.
 *
 */
public class AzureBlobSession implements ExtendedSession<BlobClient>
{
	private static final Logger LOG = LoggerFactory.getLogger(AzureBlobSession.class);

	private static final int UNKNOWN_LENGTH = -1;
	private static final String DIRECTORY_DELIMITER = "/";

	private final BlobServiceClient client;
	private final String containerName;
	private final boolean createContainerIfNotExists;

	public AzureBlobSession(final String containerName,
			final boolean createContainerIfNotExists,final String connectionString)
	{
		BlobServiceClient buildClient = new BlobServiceClientBuilder()
				.connectionString(connectionString)
				.buildClient();
		Assert.notNull(buildClient, "Client must not be null");
		Assert.isTrue(!containerName.isEmpty(), "Container name must not be blank.");
		this.client = buildClient;
		this.containerName = containerName;
		this.createContainerIfNotExists = createContainerIfNotExists;
	}

	/**
	 * List all the paths of the {@link PagedIterable} contained within the given path
	 *
	 * @param path to be queried
	 * @return {@link String String[]} of full paths
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public String[] listNames(final String path) throws IOException
	{
		return Arrays.stream(list(path))
				.map(BlobClient::getBlobName)
				.toArray(String[]::new);
	}

	/**
	 * List all the {@link PagedIterable} contained within the given path
	 *
	 * @param path to be queried
	 * @return {@link BlobClient BlobClient[]} of blobs contained
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public BlobClient[] list(final String path) throws IOException
	{
		LOG.debug("Listing contents of container [{}] on path [{}].", containerName, path);
		final StringBuilder directoryStringBuilder = new StringBuilder();
		try
		{
			final BlobContainerClient container = getContainer(containerName);

			final PagedIterable<BlobItem> blobItems;

			if (path.isEmpty())
			{
				blobItems = container.listBlobs();
			}
			else
			{
				blobItems = container.listBlobsByHierarchy(directoryStringBuilder.append(path).append(DIRECTORY_DELIMITER).toString());
			}
			List<BlobItem> blobItemList = new ArrayList<>();

			//filter out the folders in the directory
			blobItems.streamByPage().iterator().next().getValue().forEach(blobItem -> {
				if (!Boolean.TRUE.equals(blobItem.isPrefix()))
				{
					blobItemList.add(blobItem);
				}
			});

			return StreamSupport.stream(blobItemList.spliterator(), false)
					.map(blobItem -> container.getBlobClient(blobItem.getName())).toArray(BlobClient[]::new);
		}
		catch (BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	/**
	 * Write the {@link InputStream} to the given path.
	 * Appends to the current blob when already exists, else creates new blob
	 *
	 * @param inputStream to be written
	 * @param path        where to be written to
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public void write(final InputStream inputStream, final String path) throws IOException
	{
		LOG.debug("Writing to container [{}] on path [{}].", containerName, path);
		try
		{
			final BlockBlobClient blob = getBlockBlob(path);
			blob.upload(inputStream, UNKNOWN_LENGTH);
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	/**
	 * Read the blob located in <code>path</code> into given {@link OutputStream}
	 *
	 * @param path         where to be read from
	 * @param outputStream stream to write to
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public void read(final String path, final OutputStream outputStream) throws IOException
	{
		LOG.debug("Reading from container [{}] on path [{}].", containerName, path);
		try
		{
			getBlockBlob(path).downloadStream(outputStream);
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	/**
	 * Read the blob located in <code>path</code>
	 *
	 * @param path where to be read from
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public InputStream readRaw(final String path) throws IOException
	{
		LOG.debug("Reading raw data from container [{}] on path [{}].", containerName, path);
		try
		{
			return getBlockBlob(path).openInputStream();
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	@Override
	public boolean finalizeRaw()
	{
		return true;
	}

	/**
	 * Append the {@link InputStream} to the given path. Only appends when the blob exists
	 *
	 * @param inputStream to be written
	 * @param path        where to be written to
	 */
	@Override
	public void append(final InputStream inputStream, final String path)
	{
		throw new UnsupportedOperationException("Append operations not supported by block blob type.");
	}


	/**
	 * Remove the Blob at the given path.
	 *
	 * @param path where to be written to
	 * @return true when blob exists and is successfully deleted
	 * @throws IOException when path results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public boolean remove(final String path) throws IOException
	{
		LOG.debug("Removing from container [{}] on path [{}].", containerName, path);
		try
		{
			final BlockBlobClient blob = getBlockBlob(path);
			return !blob.exists() || blob.deleteIfExists();
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	/**
	 * Azure Cloud Storage only makes a directory once a blob is written to it
	 * so writing a blob called containerName/folder/blob.txt will create the directory
	 * if needed within the containerName
	 *
	 * @param path where to be written to
	 * @return true
	 */
	@Override
	public boolean mkdir(final String path)
	{
		return true;
	}

	/**
	 * Azure API does not support removing directories, only containers
	 *
	 * @param path where to be written to
	 * @return true
	 */
	@Override
	public boolean rmdir(final String path)
	{
		return true;
	}

	/**
	 * Rename/move a blob from a given path to another
	 *
	 * @param from where the blob currently is
	 * @param to   where the blob should be moved to, or should be renamed as
	 * @throws IOException when either from/to results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public void rename(final String from, final String to) throws IOException
	{
		LOG.debug("Rename path [{}], to [{}] in container [{}].", from, to, containerName);
		try
		{
			final BlockBlobClient fromBlob = getBlockBlob(from);
			final BlockBlobClient toBlob = getBlockBlob(to);

			toBlob.beginCopy(new BlobBeginCopyOptions(fromBlob.getBlobUrl()));
			fromBlob.deleteIfExists();
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	@Override
	public void close()
	{
		//Nothing to do here
	}

	@Override
	public boolean isOpen()
	{
		try
		{
			return this.client.getStatistics() != null;
		}
		catch (final BlobStorageException e)
		{
			LOG.error("Unable to retrieve service stats", e);
			return false;
		}
	}

	/**
	 * Checks to see if a file in <code>path</code> exists e.g. folder/file.txt exists within containerName
	 *
	 * @param path e.g. containerName/folder/file.txt
	 * @return true if blob exists
	 * @throws IOException when an exception occurs communicating with Storage Service
	 */
	@Override
	public boolean exists(final String path) throws IOException
	{
		LOG.debug("Querying container for [{}]", path);
		try
		{
			final boolean exists = getBlockBlob(path).exists();
			LOG.debug("Path [{}] does{} exist in container [{}].",
					path, exists ? "" : "not ", containerName);
			return exists;
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	@Override
	public BlobServiceClient getClientInstance()
	{
		return this.client;
	}

	/**
	 * Get reference to blob from server.  Blob will allow retrieve of properties e.g. size, last modified etc...
	 *
	 * @param path e.g. containerName/folder/file.txt
	 * @return {@link BlobClient} of blob found
	 * @throws IOException when either <code>path</code> results in invalid URI or exception occurs communicating with Storage Service
	 */
	@Override
	public BlobClient get(final String path) throws IOException
	{
		final BlobContainerClient container = getContainer(containerName);
		try
		{
			return container.getBlobClient(path);
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	@Override
	public String getHostPort() {
		String accountUrl = this.client.getAccountUrl();
		URI uri = URI.create(accountUrl);
		final String host = uri.getHost();
		final int port = uri.getPort();
		return host + ":" + port;
	}

	protected BlobContainerClient getContainer(final String containerName) throws IOException
	{
		try
		{
			final BlobContainerClient containerReference = client.getBlobContainerClient(containerName);
			if (createContainerIfNotExists)
			{
				if (containerReference.createIfNotExists() && LOG.isDebugEnabled())
				{
					LOG.debug("Created container with name [ {}]", containerName);
				}
			}
			else if (!containerReference.exists())
			{
				throw new IOException(String.format("Unable to find container with name [%s]", containerName));
			}
			return containerReference;
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

	protected BlockBlobClient getBlockBlob(final String path) throws IOException
	{
		try
		{
			final BlobContainerClient container = getContainer(containerName);
			return container.getBlobClient(path).getBlockBlobClient();
		}
		catch (final BlobStorageException e)
		{
			throw new IOException(e);
		}
	}

}
