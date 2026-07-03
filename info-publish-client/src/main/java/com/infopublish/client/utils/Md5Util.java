package com.infopublish.client.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5工具类
 * @Description: 提供获取文件MD5值和MD5校验功能
 * @Author: zqr
 * @Date: 2026/5/12 10:14:52
 */
public class Md5Util {

    /**
     * 计算文件的MD5值
     * @param file 文件对象
     * @return 文件的MD5字符串（32位小写）
     * @throws IOException 如果读取文件时发生IO异常
     * @throws NoSuchAlgorithmException 如果MD5算法不可用
     */
    public static String getFileMd5(File file) throws IOException, NoSuchAlgorithmException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件");
        }

        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int length;
            while ((length = fis.read(buffer)) != -1) {
                md.update(buffer, 0, length);
            }
        }
        
        return bytesToHex(md.digest());
    }

    /**
     * 计算指定路径文件的MD5值
     * @param filePath 文件路径
     * @return 文件的MD5字符串（32位小写）
     * @throws IOException 如果读取文件时发生IO异常
     * @throws NoSuchAlgorithmException 如果MD5算法不可用
     */
    public static String getFileMd5(String filePath) throws IOException, NoSuchAlgorithmException {
        File file = new File(filePath);
        return getFileMd5(file);
    }

    /**
     * 校验文件的MD5值是否与期望值匹配
     * @param file 文件对象
     * @param expectedMd5 期望的MD5值（不区分大小写）
     * @return 如果匹配返回true，否则返回false
     * @throws IOException 如果读取文件时发生IO异常
     * @throws NoSuchAlgorithmException 如果MD5算法不可用
     */
    public static boolean verifyFileMd5(File file, String expectedMd5) throws IOException, NoSuchAlgorithmException {
        String actualMd5 = getFileMd5(file);
        return actualMd5.equalsIgnoreCase(expectedMd5);
    }

    /**
     * 校验指定路径文件的MD5值是否与期望值匹配
     * @param filePath 文件路径
     * @param expectedMd5 期望的MD5值（不区分大小写）
     * @return 如果匹配返回true，否则返回false
     * @throws IOException 如果读取文件时发生IO异常
     * @throws NoSuchAlgorithmException 如果MD5算法不可用
     */
    public static boolean verifyFileMd5(String filePath, String expectedMd5) throws IOException, NoSuchAlgorithmException {
        File file = new File(filePath);
        return verifyFileMd5(file, expectedMd5);
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串（小写）
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
