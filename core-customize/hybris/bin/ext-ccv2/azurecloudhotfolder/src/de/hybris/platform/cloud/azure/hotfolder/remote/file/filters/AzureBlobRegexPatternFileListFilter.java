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

import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;

import org.springframework.integration.file.filters.AbstractRegexPatternFileListFilter;

import com.azure.storage.blob.BlobClient;


/**
 * {@link AbstractRegexPatternFileListFilter} implementation which filters for {@link BlobClient}
 * where file names match the given pattern
 *
 */
public class AzureBlobRegexPatternFileListFilter extends AbstractRegexPatternFileListFilter<BlobClient>
{

	public AzureBlobRegexPatternFileListFilter(final String pattern)
	{
		super(pattern);
	}

	@Override
	public boolean accept(final BlobClient file)
	{
		return (file.getBlobName() != null) && super.accept(file);
	}

	@Override
	protected String getFilename(final BlobClient entry)
	{
		return AzureBlobFileInfo.getFilename(entry);
	}

	@Override
	protected boolean isDirectory(final BlobClient file)
	{
		return AzureBlobFileInfo.isDirectory(file);
	}
}
