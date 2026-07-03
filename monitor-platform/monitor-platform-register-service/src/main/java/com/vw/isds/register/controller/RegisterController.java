package com.vw.isds.register.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.alibaba.fastjson.JSON;
import com.monitorplatform.common.annotation.OperateLog;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.common.util.ExportUtil;
import com.monitorplatform.common.util.RedisUtil;
import com.vw.isds.register.entity.LicenseCheckModel;
import com.vw.isds.register.entity.ProjectInfo;
import com.vw.isds.register.service.*;
import com.vw.isds.register.service.impl.RegisterServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

/**
 * @Description: 注册控制器
 * @Author: zqr
 * @Date: 2024/1/30 11:45:36
 */
@Slf4j
@Api(value = "/regis", tags = {"注册控制器"})
@RestController
@RequestMapping("/regis")
public class RegisterController {

    @Resource
    RegisterService registerService;

    @Resource
    ProjectService projectService;

    @Resource
    ResourceService resourceService;


    @Resource
    RedisUtil redisUtil;

    public static final String KEY = "8B6DorAD67S45177qTzfsA==";


    /**
     * 导出key0
     */
    @ApiOperation(value = "导出key0", notes = "导出key0", httpMethod = "GET")
    @GetMapping("/export")
    public void exportKey0(HttpServletResponse response) {
        String osName = System.getProperty("os.name").toLowerCase();
        AbstractServerInfos abstractServerInfos;
        if (osName.startsWith("windows")) {
            abstractServerInfos = new WindowsServerInfos();
        } else if (osName.startsWith("linux")) {
            abstractServerInfos = new LinuxServerInfos();
        } else {
            abstractServerInfos = new LinuxServerInfos();
        }
        LicenseCheckModel serverInfos = abstractServerInfos.getServerInfos();
        String jsonString = JSON.toJSONString(serverInfos);
        byte[] decode = Base64.getDecoder().decode(KEY);
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, decode);
        String encryptHex = aes.encryptHex(jsonString);
        String fileName = System.currentTimeMillis() + ".key0";
        ExportUtil.export(response, fileName, encryptHex);
    }


    /**
     * 导入授权文件
     *
     * @param publicKey 公钥
     * @param file      授权文件
     * @throws IOException
     */
    @ApiOperation(value = "导入授权文件", notes = "导入授权文件", httpMethod = "POST")
    @PostMapping("/import")
    public Result<?> importKey1(MultipartFile publicKey, MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String str = StrUtil.str(bytes, StandardCharsets.UTF_8);
        byte[] publicKeyBytes = publicKey.getBytes();
        String pubStr = StrUtil.str(publicKeyBytes, StandardCharsets.UTF_8);
        HashMap<String, String> map = new HashMap<>();
        map.put("publicKey", pubStr);
        map.put("key1", str);
        projectService.updateKey(map);
        RSA rsa = new RSA(null, pubStr);
        String key1 = rsa.decryptStr(str, KeyType.PublicKey);
        LicenseCheckModel licenseCheckModel = JSON.parseObject(key1, LicenseCheckModel.class);
        //导入授权数据
        resourceService.updateResource(licenseCheckModel.getAuthorization().getResources());
        redisUtil.del("loginTime");
        if (!registerService.isLogin()) {
            return Result.fail("授权失败" + RegisterServiceImpl.msg);
        }
        return Result.success("success");
    }

    /**
     * 读取授权文件
     *
     */
    @ApiOperation(value = "读取授权文件", notes = "读取授权文件", httpMethod = "POST")
    @PostMapping("/ReadLicense")
    @OperateLog(enable = false)
    public Result<LicenseCheckModel> readLicense() {
        try {
            ProjectInfo info = projectService.info();
            String encryptedData = info.getKey1();
            String publicKeyStr = info.getPublicKey();

            RSA rsa = new RSA(null, publicKeyStr);
            String decryptedData = rsa.decryptStr(encryptedData, KeyType.PublicKey);

            LicenseCheckModel model = JSON.parseObject(decryptedData, LicenseCheckModel.class);
            return Result.data(model);
        } catch (Exception e) {
            log.error("读取授权文件失败: ", e);
            return Result.fail("无法读取授权文件，请检查授权信息是否完整");
        }
    }

}
