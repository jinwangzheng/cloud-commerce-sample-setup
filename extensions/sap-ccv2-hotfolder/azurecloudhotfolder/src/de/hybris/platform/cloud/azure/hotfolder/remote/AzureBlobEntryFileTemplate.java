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
package de.hybris.platform.cloud.azure.hotfolder.remote;

import com.azure.storage.blob.BlobClient;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.SessionFactory;

/**
 * Azure Blob Storage version of {@link RemoteFileTemplate} providing type-safe access to
 * the underlying {@link BlobClient}BlobClient object.
 *
 */
public class AzureBlobEntryFileTemplate extends RemoteFileTemplate<BlobClient>
{
    public AzureBlobEntryFileTemplate(final SessionFactory<BlobClient> sessionFactory)
    {
        super(sessionFactory);
    }
}
