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
package de.hybris.platform.cloud.azure.hotfolder.remote.file.comparators;

import com.azure.storage.blob.BlobClient;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;
import de.hybris.platform.cloud.commons.spring.integration.file.comparators.NamedObject;

import java.io.Serializable;
import java.util.Comparator;

/***
 * Adapts a {@link Comparator} of {@link NamedObject}s so that it can compare {@link BlobClient} objects;
 *
 */
public class AzureBlobNameComparatorAdapter implements Comparator<BlobClient>, Serializable
{
    private static final long serialVersionUID = 1L;
    private final transient Comparator<NamedObject> comparator;

    public AzureBlobNameComparatorAdapter(final Comparator<NamedObject> comparator)
    {
        this.comparator = comparator;
    }

    public int compare(final BlobClient o1, final BlobClient o2)
    {
        return comparator.compare(() -> AzureBlobFileInfo.getFilename(o1),
              () -> AzureBlobFileInfo.getFilename(o2));
    }

}

