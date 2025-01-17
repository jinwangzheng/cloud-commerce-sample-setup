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
package de.hybris.platform.cloud.azure.hotfolder.remote.outbound;

import com.azure.storage.blob.BlobClient;
import de.hybris.platform.cloud.azure.hotfolder.remote.file.RemoteFileHeaders;
import de.hybris.platform.cloud.azure.hotfolder.remote.session.AzureBlobSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;


/**
 * An Azure Blob Storage specific {@link AbstractMessageHandler} extension
 * that moves a remote file based on the headers in the message provided.
 * Based on the {@link AzureBlobSession}.
 *
 * @see AzureBlobSession
 */
public class AzureBlobRemoteMoveMessageHandler extends AbstractMessageHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobRemoteMoveMessageHandler.class);

    private final RemoteFileTemplate<BlobClient> remoteFileTemplate;

    private static final String CLIENT_DIRECTORY_DELIMITER = "/";

    private StandardEvaluationContext evaluationContext;

    private FileNameGenerator fileNameGenerator;

    /**
     * the path on the remote mount as a String.
     */
    private Expression remoteDirectoryExpression;

    /**
     * The current evaluation of the expression.
     */
    private volatile String evaluatedRemoteDirectory;

    public AzureBlobRemoteMoveMessageHandler(final SessionFactory<BlobClient> sessionFactory)
    {
        Assert.notNull(sessionFactory, "sessionFactory must not be null");
        this.remoteFileTemplate = new RemoteFileTemplate<>(sessionFactory);
        setRemoteDirectoryExpression(new LiteralExpression(CLIENT_DIRECTORY_DELIMITER));
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
     */
    private void setRemoteDirectoryExpression(final Expression remoteDirectoryExpression)
    {
        doSetRemoteDirectoryExpression(remoteDirectoryExpression);
    }

    /**
     * Specify an expression that evaluates to the full path to the remote directory.
     *
     * @param remoteDirectoryExpression The remote directory expression.
     * @see #setRemoteDirectoryExpression(Expression)
     */
    public void setRemoteDirectoryExpressionString(final String remoteDirectoryExpression)
    {
        doSetRemoteDirectoryExpression(EXPRESSION_PARSER.parseExpression(remoteDirectoryExpression));
    }

    protected final void doSetRemoteDirectoryExpression(final Expression remoteDirectoryExpression)
    {
        Assert.notNull(remoteDirectoryExpression, "'remoteDirectoryExpression' must not be null");
        this.remoteDirectoryExpression = remoteDirectoryExpression;
    }

    public void setFileNameGenerator(final FileNameGenerator fileNameGenerator)
    {
        this.fileNameGenerator = fileNameGenerator;
        this.remoteFileTemplate.setFileNameGenerator(fileNameGenerator);
    }

    @Override
    protected void onInit()

    {
        Assert.state(this.remoteDirectoryExpression != null, "'remoteDirectoryExpression' must not be null");
        if (this.evaluationContext == null)
        {
            this.evaluationContext = ExpressionUtils.createStandardEvaluationContext(this.getBeanFactory());
        }
        evaluateRemoteDirectory();
    }

    @Override
    protected void handleMessageInternal(final Message<?> message)
    {
        if (shouldHandle(message))
        {

            final MessageHeaders headers = message.getHeaders();
            final String sourceDirectory = headers.get(FileHeaders.REMOTE_DIRECTORY, String.class);
            final String sourceFilename = headers.get(FileHeaders.REMOTE_FILE, String.class);

            // If the remote directory and filename values look reasonable...
            if ((sourceDirectory != null && !sourceDirectory.isEmpty()) || (sourceFilename != null && sourceFilename.isEmpty()))
            {
                // Generate the remote target filename...
                String targetFilename = sourceFilename;
                if (this.fileNameGenerator != null)
                {
                    targetFilename = this.fileNameGenerator.generateFileName(message);
                }

                // Generate the complete source and target paths...
                final String sourcePath = sourceDirectory + CLIENT_DIRECTORY_DELIMITER + sourceFilename;
                final String targetPath = evaluatedRemoteDirectory + CLIENT_DIRECTORY_DELIMITER + targetFilename;

                moveFile(sourcePath, targetPath);

            }
        }
    }

    protected boolean shouldHandle(final Message<?> message)
    {
        if (message != null)
        {
            final MessageHeaders headers = message.getHeaders();

            // If the details of the remote file have been provided and the remote file
            // has not been deleted...
            return ((Boolean.TRUE != headers.get(RemoteFileHeaders.REMOTE_FILE_DELETED, Boolean.class)) &&
                  headers.containsKey(FileHeaders.REMOTE_DIRECTORY) &&
                  headers.containsKey(FileHeaders.REMOTE_FILE));
        }
        return false;
    }

    protected void moveFile(final String sourcePath, final String targetPath)
    {
        // Attempt to move the remote file
        try
        {
            remoteFileTemplate.rename(sourcePath, targetPath);
        }
        catch (final MessagingException ex)
        {
            LOG.error("Unable to rename remote file [{}] to [{}].", sourcePath, targetPath);
            LOG.debug("Exception whilst moving remote file.", ex);
        }
    }

    protected void evaluateRemoteDirectory()
    {
        if (this.evaluationContext != null)
        {
            this.evaluatedRemoteDirectory = this.remoteDirectoryExpression.getValue(this.evaluationContext,
                  String.class);
            this.evaluationContext.setVariable("remoteDirectory", this.evaluatedRemoteDirectory);
        }
    }

}
