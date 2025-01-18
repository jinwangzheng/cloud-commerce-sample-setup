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
package de.hybris.platform.cloud.azure.hotfolder.remote.inbound;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;

import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;

import org.assertj.core.util.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.filters.ResettableFileListFilter;
import org.springframework.integration.file.filters.ReversibleFileListFilter;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.file.remote.synchronizer.AbstractInboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.InboundFileSynchronizer;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;


import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;



/**
 * {@link AbstractInboundFileSynchronizer} implementation to allow synchronizing files from a Blob Storage Account.
 * Ensures Remote Directory Expression is set to use the Directory Delimiter of the Client
 * sonar issues suppressed are mostly complexity of the class and methods
 */
@SuppressWarnings({"unused", "squid:S1448", "squid:S3776", "squid:S1541", "squid:S138"})
public class AzureBlobInboundSynchronizer implements InboundFileSynchronizer, BeanFactoryAware, InitializingBean, Closeable
{
	private static final Logger LOG = LoggerFactory.getLogger(AzureBlobInboundSynchronizer.class);

	protected static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

	private final RemoteFileTemplate<BlobClient> remoteFileTemplate;

	private EvaluationContext evaluationContext;

	private String remoteFileSeparator = "/";

	/**
	 * Extension used when downloading files. We change it right after we know it's downloaded.
	 */
	private String temporaryFileSuffix = ".writing";

	private Expression localFilenameGeneratorExpression;

	/**
	 * the path on the remote mount as a String.
	 */
	private Expression remoteDirectoryExpression;

	/**
	 * The current evaluation of the expression.
	 */
	private volatile String evaluatedRemoteDirectory;

	/**
	 * An {@link FileListFilter} that runs against the <em>remote</em> file system view.
	 */
	private FileListFilter<BlobClient> filter;

	/**
	 * Should we <em>delete</em> the remote <b>source</b> files
	 * after copying to the local directory? By default this is false.
	 */
	private boolean deleteRemoteFiles = false;

	/**
	 * the path on the remote mount to move files to as a String.
	 */
	private Expression moveToRemoteDirectoryExpression;

	/**
	 * The current evaluation of the move-to expression.
	 */
	private volatile String evaluatedMoveToRemoteDirectory;

	/**
	 * Should we <em>transfer</em> the remote file <b>timestamp</b>
	 * to the local file? By default this is false.
	 */
	private boolean preserveTimestamp;

	private BeanFactory beanFactory;

	private Comparator<BlobClient> comparator;

	public AzureBlobInboundSynchronizer(final SessionFactory<BlobClient> sessionFactory)
	{
		Assert.notNull(sessionFactory, "sessionFactory must not be null");
		this.remoteFileTemplate = new RemoteFileTemplate<>(sessionFactory);
		final BlobServiceClient client = (BlobServiceClient) sessionFactory.getSession().getClientInstance();
		doSetMoveToRemoteDirectoryExpression(new LiteralExpression(remoteFileSeparator));
	}

	protected Comparator<BlobClient> getComparator()
	{
		return this.comparator;
	}

	/**
	 * Set a comparator to sort the retrieved list of {@code BlobClient} (the Type that represents
	 * the remote file) prior to applying filters and max fetch size.
	 *
	 * @param comparator the comparator.
	 * @since 5.1
	 */
	public void setComparator(final Comparator<BlobClient> comparator)
	{
		this.comparator = comparator;
	}


	/**
	 * @param remoteFileSeparator the remote file separator.
	 * @see RemoteFileTemplate#setRemoteFileSeparator(String)
	 */
	public void setRemoteFileSeparator(final String remoteFileSeparator)
	{
		Assert.notNull(remoteFileSeparator, "'remoteFileSeparator' must not be null");
		this.remoteFileSeparator = remoteFileSeparator;
	}

	/**
	 * Set an expression used to determine the local file name.
	 *
	 * @param localFilenameGeneratorExpression the expression.
	 */
	public void setLocalFilenameGeneratorExpression(final Expression localFilenameGeneratorExpression)
	{
		Assert.notNull(localFilenameGeneratorExpression, "'localFilenameGeneratorExpression' must not be null");
		this.localFilenameGeneratorExpression = localFilenameGeneratorExpression;
	}

	/**
	 * Set an expression used to determine the local file name.
	 *
	 * @param localFilenameGeneratorExpression the expression.
	 * @see #setRemoteDirectoryExpression(Expression)
	 * @since 4.3.13
	 */
	public void setLocalFilenameGeneratorExpressionString(final String localFilenameGeneratorExpression)
	{
		setLocalFilenameGeneratorExpression(EXPRESSION_PARSER.parseExpression(localFilenameGeneratorExpression));
	}

	/**
	 * Set a temporary file suffix to be used while transferring files. Default ".writing".
	 *
	 * @param temporaryFileSuffix the file suffix.
	 */
	@SuppressWarnings("unused")
	public void setTemporaryFileSuffix(final String temporaryFileSuffix)
	{
		this.temporaryFileSuffix = temporaryFileSuffix;
	}

	/**
	 * Specify the full path to the remote directory.
	 *
	 * @param remoteDirectory The remote directory.
	 */
	public void setRemoteDirectory(final String remoteDirectory)
	{
		this.remoteDirectoryExpression = new LiteralExpression(remoteDirectory);
		evaluateRemoteDirectory();
	}

	/**
	 * Specify an expression that evaluates to the full path to the remote directory.
	 *
	 * @param remoteDirectoryExpression The remote directory expression.
	 * @since 4.2
	 */
	public void setRemoteDirectoryExpression(final Expression remoteDirectoryExpression)
	{
		doSetRemoteDirectoryExpression(remoteDirectoryExpression);
	}

	/**
	 * Specify an expression that evaluates to the full path to the remote directory.
	 *
	 * @param remoteDirectoryExpression The remote directory expression.
	 * @see #setRemoteDirectoryExpression(Expression)
	 * @since 4.3.13
	 */
	public void setRemoteDirectoryExpressionString(final String remoteDirectoryExpression)
	{
		doSetRemoteDirectoryExpression(EXPRESSION_PARSER.parseExpression(remoteDirectoryExpression));
	}


	protected final void doSetRemoteDirectoryExpression(final Expression remoteDirectoryExpression)
	{
		Assert.notNull(remoteDirectoryExpression, "'remoteDirectoryExpression' must not be null");
		this.remoteDirectoryExpression = remoteDirectoryExpression;
		evaluateRemoteDirectory();
	}

	/**
	 * Set the filter to be applied to the remote files before transferring.
	 *
	 * @param filter the file list filter.
	 */
	public void setFilter(final FileListFilter<BlobClient> filter)
	{
		doSetFilter(filter);
	}

	protected final void doSetFilter(final FileListFilter<BlobClient> filter)
	{
		this.filter = filter;
	}

	/**
	 * Set to true to enable deletion of remote files after successful transfer.
	 *
	 * @param deleteRemoteFiles true to delete.
	 */
	public void setDeleteRemoteFiles(final boolean deleteRemoteFiles)
	{
		this.deleteRemoteFiles = deleteRemoteFiles;
	}

	/**
	 * Set to true to enable the preservation of the remote file timestamp when
	 * transferring.
	 *
	 * @param preserveTimestamp true to preserve.
	 */
	public void setPreserveTimestamp(final boolean preserveTimestamp)
	{
		this.preserveTimestamp = preserveTimestamp;
	}

	@Override
	public void setBeanFactory(final BeanFactory beanFactory) throws BeansException
	{
		this.beanFactory = beanFactory;
	}

	/**
	 * Specify the full path to the move-to remote directory.
	 *
	 * @param moveToRemoteDirectory The remote directory.
	 */
	public void setMoveToRemoteDirectory(final String moveToRemoteDirectory)
	{
		this.moveToRemoteDirectoryExpression = new LiteralExpression(moveToRemoteDirectory);
		evaluateMoveToRemoteDirectory();
	}

	/**
	 * Specify an expression that evaluates to the full path to the move-to remote directory.
	 *
	 * @param moveToRemoteDirectoryExpression The remote directory expression.
	 * @since 4.2
	 */
	public void setMoveToRemoteDirectoryExpression(final Expression moveToRemoteDirectoryExpression)
	{
		doSetMoveToRemoteDirectoryExpression(moveToRemoteDirectoryExpression);
	}

	/**
	 * Specify an expression that evaluates to the full path to the move-to  remote directory.
	 *
	 * @param moveToRemoteDirectoryExpression The remote directory expression.
	 * @see #setRemoteDirectoryExpression(Expression)
	 * @since 4.3.13
	 */
	public void setMoveToRemoteDirectoryExpressionString(final String moveToRemoteDirectoryExpression)
	{
		doSetMoveToRemoteDirectoryExpression(EXPRESSION_PARSER.parseExpression(moveToRemoteDirectoryExpression));
	}


	protected final void doSetMoveToRemoteDirectoryExpression(final Expression moveToRemoteDirectoryExpression)
	{
		Assert.notNull(moveToRemoteDirectoryExpression, "'moveToRemoteDirectoryExpression' must not be null");
		this.moveToRemoteDirectoryExpression = remoteDirectoryExpression;
		evaluateMoveToRemoteDirectory();
	}


	@Override
	public final void afterPropertiesSet()
	{
		Assert.state(this.remoteDirectoryExpression != null, "'remoteDirectoryExpression' must not be null");
		if (this.evaluationContext == null)
		{
			this.evaluationContext = ExpressionUtils.createSimpleEvaluationContext(this.beanFactory);
		}
		evaluateRemoteDirectory();
		evaluateMoveToRemoteDirectory();
		if (deleteRemoteFiles)
		{
			Assert.state(StringUtils.hasText(this.evaluatedRemoteDirectory),
					"'moveToRemoteDirectory' and 'deleteRemoteFiles' are mutually exclusive.");
		}

	}

	protected final List<BlobClient> filterFiles(final BlobClient[] files)
	{
		return (this.filter != null) ? this.filter.filterFiles(files) : Arrays.asList(files);
	}

	@SuppressWarnings("unchecked")
	public static <F> F[] purgeUnwantedElements(final F[] fileArray, final Predicate<F> predicate, final Comparator<F> comparator)
	{
		if (ObjectUtils.isEmpty(fileArray))
		{
			return fileArray;
		}
		else
		{
			if (comparator == null)
			{
				return Arrays.stream(fileArray).filter(predicate.negate())
						.toArray(size -> (F[]) Array.newInstance(fileArray[0].getClass(), size));
			}
			else
			{
				return Arrays.stream(fileArray).filter(predicate.negate()).sorted(comparator)
						.toArray(size -> (F[]) Array.newInstance(fileArray[0].getClass(), size));
			}
		}
	}

	protected String getTemporaryFileSuffix()
	{
		return this.temporaryFileSuffix;
	}

	@Override
	public void close() throws IOException
	{
		if (this.filter instanceof Closeable)
		{
			((Closeable) this.filter).close();
		}
	}

	@Override
	public void synchronizeToLocalDirectory(final File localDirectory)
	{
		synchronizeToLocalDirectoryAndGetFileInfo(localDirectory, Integer.MIN_VALUE);
	}

	@Override
	public void synchronizeToLocalDirectory(final File localDirectory, final int maxFetchSize)
	{
		synchronizeToLocalDirectoryAndGetFileInfo(localDirectory, maxFetchSize);
	}

	public List<AzureBlobFileInfo> synchronizeToLocalDirectoryAndGetFileInfo(final File localDirectory, final int maxFetchSize)
	{
		if (maxFetchSize == 0)
		{
			if (LOG.isDebugEnabled())
			{
				LOG.debug("Max Fetch Size is zero - fetch to {} ignored", localDirectory.getAbsolutePath());
			}
			return Lists.emptyList();
		}
		if (LOG.isTraceEnabled())
		{
			LOG.trace("Synchronizing {} to {}", this.evaluatedRemoteDirectory, localDirectory);
		}
		try
		{
			final List<AzureBlobFileInfo> azureBlobFileInfos = this.remoteFileTemplate.execute(
					session -> transferFilesFromRemoteToLocal(localDirectory, maxFetchSize, session));
			if (LOG.isDebugEnabled())
			{
				final int count = (azureBlobFileInfos == null) ? 0 : azureBlobFileInfos.size();
				LOG.debug("{} files transferred from '{}'", count, this.evaluatedRemoteDirectory);
			}
			return azureBlobFileInfos;
		}
		catch (final MessagingException e)
		{
			throw new MessagingException(
					String.format("Problem occurred while synchronizing '%s' to local directory",
							this.evaluatedRemoteDirectory), e);
		}
	}

	// Suppress Sonar warnings - this is mostly spring code copied here to extend and fix bugs.
	// I don't want to change it's structure as it will make it much more difficult to compare to the
	// original in the future.
	@SuppressWarnings({ "squid:S3776", "squid:S134" })
	private List<AzureBlobFileInfo> transferFilesFromRemoteToLocal(final File localDirectory, final int maxFetchSize,
			final Session<BlobClient> session) throws IOException
	{

		BlobClient[] files = session.list(this.evaluatedRemoteDirectory);
		if (!ObjectUtils.isEmpty(files))
		{
			files = purgeUnwantedElements(files, e -> !isFile(e), this.comparator);
		}

		if (!ObjectUtils.isEmpty(files))
		{
			List<BlobClient> filteredFiles = filterFiles(files);
			if (maxFetchSize >= 0 && filteredFiles.size() > maxFetchSize)
			{
				rollbackFromFileToListEnd(filteredFiles, filteredFiles.get(maxFetchSize));
				final List<BlobClient> newList = new ArrayList<>(maxFetchSize);
				for (int i = 0; i < maxFetchSize; i++)
				{
					newList.add(filteredFiles.get(i));
				}
				filteredFiles = newList;
			}

			final List<AzureBlobFileInfo> copiedFiles = new ArrayList<>(filteredFiles.size());
			for (final BlobClient file : filteredFiles)
			{
				try
				{
					if (file != null)
					{
						final AzureBlobFileInfo azureBlobFileInfo = copyFileToLocalDirectory(this.evaluatedRemoteDirectory, file,
								localDirectory, session);
						if (azureBlobFileInfo != null)
						{
							copiedFiles.add(azureBlobFileInfo);
						}
					}
				}
				catch (final RuntimeException | IOException e1)
				{
					rollbackFromFileToListEnd(filteredFiles, file);
					throw e1;
				}
			}
			return copiedFiles;
		}
		else
		{
			return Lists.emptyList();
		}
	}

	protected void rollbackFromFileToListEnd(final List<BlobClient> filteredFiles, final BlobClient file)
	{
		if (this.filter instanceof ReversibleFileListFilter)
		{
			((ReversibleFileListFilter<BlobClient>) this.filter).rollback(file, filteredFiles);
		}
	}

	// Suppress Sonar warnings - this is mostly spring code copied here to extend and fix bugs.
	// I don't want to change it's structure as it will make it much more difficult to compare to the
	// original in the future.
	@SuppressWarnings({ "squid:S134" })
	protected AzureBlobFileInfo copyFileToLocalDirectory(final String remoteDirectoryPath, final BlobClient remoteFile,
			final File localDirectory, final Session<BlobClient> session) throws IOException
	{

		final String remoteFileName = getFilename(remoteFile);
		final String localFileName = generateLocalFileName(remoteFileName);
		final String remoteFilePath =
				remoteDirectoryPath != null ? (remoteDirectoryPath + this.remoteFileSeparator + remoteFileName) : remoteFileName;

		if (!isFile(remoteFile))
		{
			if (LOG.isDebugEnabled())
			{
				LOG.debug("cannot copy, not a file: {}", remoteFilePath);
			}
			return null;
		}

		final long modified = getModified(remoteFile);

		final File localFile = new File(localDirectory, localFileName);
		final boolean exists = localFile.exists();

		final AzureBlobFileInfo azureBlobFileInfo = new AzureBlobFileInfo(remoteFile, localDirectory.getCanonicalPath());

		if (!exists || (this.preserveTimestamp && modified != localFile.lastModified()))
		{
			if (!exists &&
					// localFileName.replace("/", Matcher.quoteReplacement(File.separator)).contains(File.separator))
					localFile.getPath().contains(File.separator))
			{
				//noinspection ResultOfMethodCallIgnored
				localFile.getParentFile().mkdirs();
			}

			boolean transfer = true;

			if (exists)
			{
				try
				{
					Files.delete(localFile.toPath());
				}
				catch (final IOException ex)
				{
					transfer = false;
					if (LOG.isInfoEnabled())
					{
						LOG.info(
								"Cannot delete local file '{}' in order to transfer modified remote file '{}'. "
										+ "The local file may be busy in some other process.",
								localFile, remoteFile);
					}
				}
			}

			boolean renamed = false;

			if (transfer)
			{
				renamed = copyRemoteContentToLocalFile(session, remoteFilePath, localFile);
			}

			if (renamed)
			{
				if (StringUtils.hasText(this.evaluatedMoveToRemoteDirectory))
				{
					azureBlobFileInfo.setRemoteDirectory(this.evaluatedMoveToRemoteDirectory);
					final String moveToFilePath = this.evaluatedMoveToRemoteDirectory + this.remoteFileSeparator + remoteFileName;
					session.rename(remoteFilePath, moveToFilePath);
					if (LOG.isDebugEnabled())
					{
						LOG.debug("moved remote file: {} to {}", remoteFilePath, moveToFilePath);
					}
				}
				else if (this.deleteRemoteFiles)
				{
					azureBlobFileInfo.setDeleted(true);
					session.remove(remoteFilePath);
					if (LOG.isDebugEnabled())
					{
						LOG.debug("deleted remote file: {}", remoteFilePath);
					}
				}
				if (this.preserveTimestamp && !localFile.setLastModified(modified))
				{
					throw new IllegalStateException(String.format("Could not set last modified on file: %s", localFile));
				}
				return azureBlobFileInfo;
			}
			else if (this.filter instanceof ResettableFileListFilter)
			{
				if (LOG.isInfoEnabled())
				{
					LOG.info("Reverting the remote file '{}' from the filter for a subsequent transfer attempt", remoteFile);
				}
				((ResettableFileListFilter<BlobClient>) this.filter).remove(remoteFile);
			}
		}
		else if (LOG.isWarnEnabled())
		{
			LOG.warn("The remote file '{}' has not been transferred to the existing local file '{}'."
					+ " Consider removing the local file.", remoteFile, localFile);
		}

		return null;
	}

	// Suppress Sonar warnings - this is mostly spring code copied here to extend and fix bugs.
	// I don't want to change it's structure as it will make it much more difficult to compare to the
	// original in the future.
	@SuppressWarnings({ "squid:S2221" })
	private boolean copyRemoteContentToLocalFile(final Session<BlobClient> session, final String remoteFilePath,
			final File localFile)
	{
		boolean renamed;
		final String tempFileName = localFile.getAbsolutePath() + this.temporaryFileSuffix;
		final File tempFile = new File(tempFileName);

		try (final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile)))
		{
			session.read(remoteFilePath, outputStream);
		}
		catch (final RuntimeException re)
		{
			throw re;
		}
		catch (final Exception e)
		{
			throw new MessagingException(
					String.format("Failure occurred while copying '%s' from the remote to the local directory", remoteFilePath), e);
		}

		renamed = tempFile.renameTo(localFile);

		if (!renamed)
		{
			try
			{
				Files.delete(localFile.toPath());
				renamed = tempFile.renameTo(localFile);
				if (!renamed && LOG.isInfoEnabled())
				{
					LOG.info("Cannot rename '{}' to local file '{}' after deleting. "
							+ "The local file may be busy in some other process.", tempFileName, localFile);
				}
			}
			catch (final IOException ex)
			{
				if (LOG.isInfoEnabled())
				{
					LOG.info("Cannot delete local file '{}'. The local file may be busy in some other process.", localFile, ex);
				}
			}
		}
		return renamed;
	}

	private String generateLocalFileName(final String remoteFileName)
	{
		if (this.localFilenameGeneratorExpression != null)
		{
			final String localFileName = this.localFilenameGeneratorExpression.getValue(this.evaluationContext, remoteFileName,
					String.class);

			Assert.state(StringUtils.hasText(localFileName),
					String.format("'localFilenameGeneratorExpression' for '%s' was evaluated to null.", remoteFileName));
			return localFileName;
		}
		return remoteFileName;
	}

	protected void evaluateRemoteDirectory()
	{
		if (this.evaluationContext != null)
		{
			this.evaluatedRemoteDirectory = this.remoteDirectoryExpression.getValue(this.evaluationContext, String.class);
			this.evaluationContext.setVariable("remoteDirectory", this.evaluatedRemoteDirectory);

		}
	}

	protected void evaluateMoveToRemoteDirectory()
	{
		if (this.evaluationContext != null)
		{
			this.evaluatedMoveToRemoteDirectory = this.moveToRemoteDirectoryExpression.getValue(this.evaluationContext,
					String.class);
			this.evaluationContext.setVariable("moveToRemoteDirectory", this.evaluatedMoveToRemoteDirectory);
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean isFile(final BlobClient entry)
	{
		return entry != null;
	}

	private String getFilename(final BlobClient entry)
	{
		return AzureBlobFileInfo.getFilename(entry);
	}

	private long getModified(final BlobClient entry)
	{
		return AzureBlobFileInfo.getModified(entry);
	}

}
