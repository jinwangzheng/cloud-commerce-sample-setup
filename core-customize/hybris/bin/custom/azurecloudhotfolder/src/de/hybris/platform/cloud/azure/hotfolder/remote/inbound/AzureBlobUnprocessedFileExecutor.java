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

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobFileInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.assertj.core.util.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Fetches unprocessed file from Remote Processing Directory.
 */
@SuppressWarnings("unused")
public class AzureBlobUnprocessedFileExecutor implements BeanFactoryAware, InitializingBean, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobUnprocessedFileExecutor.class);

    private final RemoteFileTemplate<BlobClient> remoteFileTemplate;

    private EvaluationContext evaluationContext;


    /**
     * An {@link FileListFilter} that runs against the <em>remote</em> file system view.
     */
    private FileListFilter<BlobClient> filter;
    private Expression processingRemoteDirectoryExpression;
    private volatile String evaluatedProcessingRemoteDirectory;

    private BeanFactory beanFactory;

    private Comparator<BlobClient> comparator;

    private String directoryDelimiter = "/";

    public AzureBlobUnprocessedFileExecutor(final SessionFactory<BlobClient> sessionFactory) {
        Assert.notNull(sessionFactory, "sessionFactory must not be null");
        this.remoteFileTemplate = new RemoteFileTemplate<>(sessionFactory);
        final BlobServiceClient client = (BlobServiceClient) sessionFactory.getSession().getClientInstance();
        doSetRemoteDirectoryExpression(new LiteralExpression(directoryDelimiter));
    }

    protected Comparator<BlobClient> getComparator() {
        return this.comparator;
    }

    /**
     * Set a comparator to sort the retrieved list of {@code BlobClient} (the Type that represents
     * the remote file) prior to applying filters and max fetch size.
     *
     * @param comparator the comparator.
     */
    public void setComparator(final Comparator<BlobClient> comparator) {
        this.comparator = comparator;
    }


    protected final void doSetRemoteDirectoryExpression(final Expression remoteDirectoryExpression) {
        Assert.notNull(remoteDirectoryExpression, "'remoteDirectoryExpression' must not be null");
        this.processingRemoteDirectoryExpression = remoteDirectoryExpression;
        evaluateProcessingRemoteDirectory();
    }

    /**
     * Set the filter to be applied to the remote files before re-processing.
     *
     * @param filter the file list filter.
     */
    public void setFilter(final FileListFilter<BlobClient> filter) {
        doSetFilter(filter);
    }

    protected final void doSetFilter(final FileListFilter<BlobClient> filter) {
        this.filter = filter;
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * Specify the full path to the processing remote directory.
     *
     * @param processingRemoteDirectory The remote directory.
     */
    public void setProcessingRemoteDirectory(final String processingRemoteDirectory) {
        this.processingRemoteDirectoryExpression = new LiteralExpression(processingRemoteDirectory);
        evaluateProcessingRemoteDirectory();
    }


    @Override
    public final void afterPropertiesSet() {
        Assert.state(this.processingRemoteDirectoryExpression != null, "'processingRemoteDirectoryExpression' must not be null");
        if (this.evaluationContext == null) {
            this.evaluationContext = ExpressionUtils.createStandardEvaluationContext(this.beanFactory);
        }
        evaluateProcessingRemoteDirectory();
    }

    protected final List<BlobClient> filterFiles(final BlobClient[] files) {
        return (this.filter != null) ? this.filter.filterFiles(files) : Arrays.asList(files);
    }

    @SuppressWarnings("unchecked")
    public static <F> F[] purgeUnwantedElements(final F[] fileArray,
          final Predicate<F> predicate,
          final Comparator<F> comparator) {
        if (ObjectUtils.isEmpty(fileArray)) {
            return fileArray;
        } else {
            if (comparator == null) {
                return Arrays.stream(fileArray)
                      .filter(predicate.negate())
                      .toArray(size -> (F[]) Array.newInstance(fileArray[0].getClass(), size));
            } else {
                return Arrays.stream(fileArray)
                      .filter(predicate.negate())
                      .sorted(comparator)
                      .toArray(size -> (F[]) Array.newInstance(fileArray[0].getClass(), size));
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (this.filter instanceof Closeable closeable) {
            closeable.close();
        }
    }

    public List<AzureBlobFileInfo> fetchUnprocessedFilesInfo(final int maxFetchSize) {
        if (maxFetchSize == 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Max Fetch Size is zero - Checking unprocessed files is disabled.");
            }
            return Lists.emptyList();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Checking files in processing : {}", this.evaluatedProcessingRemoteDirectory);
        }
        try {
            final List<AzureBlobFileInfo> azureBlobFileInfos = this.remoteFileTemplate.execute(session ->
                  fetchUnprocessedFilesInfo(maxFetchSize, session));
            if (LOG.isDebugEnabled()) {
                final int count = (azureBlobFileInfos == null) ? 0 : azureBlobFileInfos.size();
                LOG.debug("{} files fetched from '{}'.", count, this.evaluatedProcessingRemoteDirectory);
            }
            return azureBlobFileInfos;
        } catch (MessagingException e) {
            throw new MessagingException("Problem occurred while fetching unprocessed files in '"
                  + this.evaluatedProcessingRemoteDirectory, e);
        }
    }

    // Suppress Sonar warnings - this is mostly spring code copied here to extend and fix bugs.
    // I don't want to change it's structure as it will make it much more difficult to compare to the
    // original in the future.
    @SuppressWarnings({"squid:S3776", "squid:S134"})
    private List<AzureBlobFileInfo> fetchUnprocessedFilesInfo(final int maxFetchSize,
          final Session<BlobClient> session)
          throws IOException {

        BlobClient[] files = session.list(this.evaluatedProcessingRemoteDirectory);
        if (!ObjectUtils.isEmpty(files)) {
            files = purgeUnwantedElements(files, Objects::isNull, this.comparator);
        }

        if (!ObjectUtils.isEmpty(files)) {
            List<BlobClient> filteredFiles = filterFiles(files);
            if (CollectionUtils.isNotEmpty(filteredFiles)) {
                final List<AzureBlobFileInfo> filesForReprocessing = new ArrayList<>(maxFetchSize);
                for (BlobClient filteredFile : filteredFiles) {
                    AzureBlobFileInfo fileInfo = new AzureBlobFileInfo(filteredFile);
                    LOG.info("Will take action on file {}", fileInfo.getFilename());
                    filesForReprocessing.add(fileInfo);
                    //We are re-processing maxFetchSize number of files, meaning, if fetched files count is greater than maxfetchSize then some fetched files will not be re-processed at this moment, and they will again go through filtering process again.
                    if (filesForReprocessing.size() == maxFetchSize) {
                        break;
                    }
                }
                return filesForReprocessing;
            }
        }
        return Lists.emptyList();
    }

    protected void evaluateProcessingRemoteDirectory() {
        if (this.evaluationContext != null) {
            this.evaluatedProcessingRemoteDirectory = this.processingRemoteDirectoryExpression.getValue(this.evaluationContext,
                  String.class);
            this.evaluationContext.setVariable("processingRemoteDirectory", this.evaluatedProcessingRemoteDirectory);
        }
    }

    private String getFilename(final BlobClient entry) {
        return AzureBlobFileInfo.getFilename(entry);
    }

    private long getModified(final BlobClient entry) {
        return AzureBlobFileInfo.getModified(entry);
    }

}
