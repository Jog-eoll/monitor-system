package com.monitorplatform.common.util;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件操作工具类
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    private MinioClient minioClient;

    /**
     * 创建存储桶（如果不存在）并设置为公开只读
     *
     * @param bucketName 存储桶名称
     */
    public void createBucket(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("存储桶 {} 创建成功", bucketName);
                // 设置桶为公开只读
                setBucketPublicReadOnly(bucketName);
            }
        } catch (Exception e) {
            log.error("创建存储桶 {} 失败", bucketName, e);
            throw new RuntimeException("创建存储桶失败", e);
        }
    }

    /**
     * 设置存储桶为公开只读（允许通过 URL 直接访问文件）
     *
     * @param bucketName 存储桶名称
     */
    public void setBucketPublicReadOnly(String bucketName) {
        try {
            String policy = "{\n" +
                    "  \"Version\": \"2012-10-17\",\n" +
                    "  \"Statement\": [\n" +
                    "    {\n" +
                    "      \"Effect\": \"Allow\",\n" +
                    "      \"Principal\": \"*\",\n" +
                    "      \"Action\": [\"s3:GetObject\"],\n" +
                    "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );
            log.info("存储桶 {} 已设置为公开只读", bucketName);
        } catch (Exception e) {
            log.error("设置存储桶 {} 访问策略失败", bucketName, e);
            throw new RuntimeException("设置存储桶访问策略失败", e);
        }
    }

//    /**
//     * 上传文件
//     *
//     * @param bucketName 存储桶名称
//     * @param objectName 对象名称（文件路径）
//     * @param file       文件
//     * @return 文件访问 URL
//     */
//    public String uploadFile(String bucketName, String objectName, MultipartFile file) {
//        try {
//            createBucket(bucketName);
//            minioClient.putObject(
//                    PutObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectName)
//                            .stream(file.getInputStream(), file.getSize(), -1)
//                            .contentType(file.getContentType())
//                            .build()
//            );
//            log.info("文件上传成功: {}/{}" , bucketName, objectName);
//            return getFileUrl(bucketName, objectName);
//        } catch (Exception e) {
//            log.error("文件上传失败: {}/{}" , bucketName, objectName, e);
//            throw new RuntimeException("文件上传失败", e);
//        }
//    }

//    /**
//     * 上传文件（使用字节数组）
//     *
//     * @param bucketName  存储桶名称
//     * @param objectName  对象名称
//     * @param data        文件字节数组
//     * @param contentType 内容类型
//     * @return 文件访问 URL
//     */
//    public String uploadFile(String bucketName, String objectName, byte[] data, String contentType) {
//        try {
//            createBucket(bucketName);
//            minioClient.putObject(
//                    PutObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectName)
//                            .stream(new ByteArrayInputStream(data), data.length, -1)
//                            .contentType(contentType)
//                            .build()
//            );
//            log.info("文件上传成功: {}/{}" , bucketName, objectName);
//            return getFileUrl(bucketName, objectName);
//        } catch (Exception e) {
//            log.error("文件上传失败: {}/{}" , bucketName, objectName, e);
//            throw new RuntimeException("文件上传失败", e);
//        }
//    }

//    /**
//     * 上传文件（使用输入流）
//     *
//     * @param bucketName  存储桶名称
//     * @param objectName  对象名称
//     * @param inputStream 输入流
//     * @param size        文件大小
//     * @param contentType 内容类型
//     * @return 文件访问 URL
//     */
//    public String uploadFile(String bucketName, String objectName, InputStream inputStream, long size, String contentType) {
//        try {
//            createBucket(bucketName);
//            minioClient.putObject(
//                    PutObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectName)
//                            .stream(inputStream, size, -1)
//                            .contentType(contentType)
//                            .build()
//            );
//            log.info("文件上传成功: {}/{}" , bucketName, objectName);
//            return getFileUrl(bucketName, objectName);
//        } catch (Exception e) {
//            log.error("文件上传失败: {}/{}" , bucketName, objectName, e);
//            throw new RuntimeException("文件上传失败", e);
//        }
//    }

    /**
     * 下载文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 输入流
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: {}/{}" , bucketName, objectName, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 删除文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}/{}" , bucketName, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}/{}" , bucketName, objectName, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

//    /**
//     * 获取文件访问 URL（永久访问，需要存储桶设置为 public）
//     *
//     * @param bucketName 存储桶名称
//     * @param objectName 对象名称
//     * @return 文件 URL
//     */
//    public String getFileUrl(String bucketName, String objectName) {
//        return String.format("%s/%s/%s", minioClient.getObjectUrl(bucketName, objectName).split("/")[0] + "/" + minioClient.getObjectUrl(bucketName, objectName).split("/")[2], bucketName, objectName);
//    }

    /**
     * 获取预签名 URL（临时访问链接）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param expiry     过期时间（分钟）
     * @return 预签名 URL
     */
    public String getPresignedUrl(String bucketName, String objectName, int expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiry, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: {}/{}" , bucketName, objectName, e);
            throw new RuntimeException("获取预签名 URL 失败", e);
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean fileExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}/{}" , bucketName, objectName, e);
            throw new RuntimeException("检查文件存在性失败", e);
        }
    }
}
