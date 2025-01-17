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

import com.azure.storage.blob.BlobClient;

import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

/**
 * {@link SessionFactory} implementation to provide a {@link Session} to allow communication with Azure Blob Storage
 *
 */
public class AzureBlobSessionFactory implements SessionFactory<BlobClient>
{
	private final Session<BlobClient> blobSession;

	public AzureBlobSessionFactory(final Session<BlobClient> blobSession)
	{
		this.blobSession = blobSession;
	}

	@Override
	public Session<BlobClient> getSession()
	{
		return blobSession;
	}
}
