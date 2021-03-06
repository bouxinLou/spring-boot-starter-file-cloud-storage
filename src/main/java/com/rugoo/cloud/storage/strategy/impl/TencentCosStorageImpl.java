package com.rugoo.cloud.storage.strategy.impl;

import com.obs.services.exception.ObsException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.region.Region;
import com.rugoo.cloud.storage.CloudStorageStrategy;
import com.rugoo.cloud.storage.common.ClientSign;
import com.rugoo.cloud.storage.common.UploadInfo;
import com.rugoo.cloud.storage.config.bean.TencentCosConfig;
import com.rugoo.cloud.storage.exception.CloudStorageException;
import com.rugoo.cloud.storage.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rugoo.cloud.storage.anno.MarkAsCloudStorage;
import com.rugoo.cloud.storage.common.CloudFile;
import com.rugoo.cloud.storage.config.CloudStorageProperties;
import com.rugoo.cloud.storage.enums.CloudType;
import com.rugoo.cloud.storage.exception.CloudStorageConfigurationException;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Description
 *
 * @author boxin
 * @date 2020-12-29
 * @see com.rugoo.cloud.storage.strategy.impl
 */
@MarkAsCloudStorage(type = CloudType.TENCENT)
public class TencentCosStorageImpl implements CloudStorageStrategy {

    private static final Logger log = LoggerFactory.getLogger(TencentCosStorageImpl.class);


    @Resource
    private CloudStorageProperties properties;

    private final TencentCosConfig config;

    public TencentCosStorageImpl() {
        if (properties.getTencentCos() == null) {
            throw new CloudStorageConfigurationException("TencentOSS config unset");
        }
        this.config = properties.getTencentCos();
    }

    @Override
    public ClientSign createPostSignature() {
        return null;
    }

    @Override
    public <T> CloudFile store(UploadInfo<T> uploadInfo) {
        String filename = UUID.randomUUID().toString();
        String fileKey = StringUtil.concat(config.getStorepath(), "/", filename, ".", uploadInfo.getFileExtension());

        COSClient cosClient = cosClient();

        try {
            if (uploadInfo.getContents() instanceof byte[]) {
                cosClient().putObject(config.getBucket(), fileKey, new ByteArrayInputStream((byte[])uploadInfo.getContents()), null);
            } else if (uploadInfo.getContents() instanceof File) {
                cosClient.putObject(config.getBucket(), fileKey, (File) uploadInfo.getContents());
            }
        } catch (ObsException obsEx) {
            // 防止污染存储源
            cosClient.deleteObject(config.getBucket(), fileKey);
            throw new CloudStorageException("Upload failed with unexpected exception", obsEx);
        } finally {
            cosClient.shutdown();
        }

        return CloudFile.createInstance()
                .setAccessUrl(StringUtil.concat(config.getDomain(), "/", fileKey))
                .setObjectId(fileKey)
                .setStorepath(config.getStorepath())
                .setUploadtime(LocalDateTime.now())
                .setCloudType(CloudType.TENCENT.name())
                .setFsize(uploadInfo.getContentLength())
                .setExtension(uploadInfo.getFileExtension())
                .setPrevfilename(filename)
                .setFilename(filename);
    }

    @Override
    public InputStream getFileContents(String objectId) {
        return (InputStream) cosClient().getObject(config.getBucket(), objectId).getObjectContent();
    }

    @Override
    public boolean delete(String objectKey) {
        COSClient client = cosClient();
        try {
            client.deleteObject(config.getBucket(), objectKey);
            return true;
        } catch (CosClientException e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
                log.error("Failed to delete file {}::{}", config.getBucket(), objectKey);
            } else {
                log.info("Failed to delete file {}::{}", config.getBucket(), objectKey);
            }
            return false;
        } finally {
            client.shutdown();
        }
    }

    private COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
        Region region = new Region(config.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        return new COSClient(cred, clientConfig);
    }
}
