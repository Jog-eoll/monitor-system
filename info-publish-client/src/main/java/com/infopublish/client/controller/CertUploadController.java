package com.infopublish.client.controller;

import com.infopublish.client.common.Result;
import com.infopublish.client.service.CertificateFileService;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.MonitorPlatformClient;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.*;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 证书上传控制器
 * 解析 .cer 文件并同时：
 *   1. 将证书信息 POST 到管控平台 /cert/import 入库（ukey_certificate 表）
 *   2. 将 .cer 文件保存到本地 certs/ 目录供认证握手使用
 */
@Slf4j
@RestController
@RequestMapping("/api/cert")
@CrossOrigin(origins = "*")
public class CertUploadController {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    @Resource
    private CertificateFileService certificateFileService;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 解析并保存证书
     * multipart 参数:
     *   file - .cer 文件
     *   role - "server" | "client"（决定本地保存目录前缀，用于区分显示）
     *   pin  - UKey PIN 码（可选，导入到管控平台时使用）
     *   displayName - 显示名称（可选）
     */
    @PostMapping("/parse-and-save")
    public Result<Map<String, Object>> parseAndSave(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "role", defaultValue = "client") String role,
            @RequestParam(value = "pin", required = false, defaultValue = "") String pin,
            @RequestParam(value = "displayName", required = false, defaultValue = "") String displayName) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("请选择证书文件");
            }

            byte[] fileBytes = file.getBytes();

            // ===== 1. 解析 X.509 证书 =====
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate x509;
            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                x509 = (X509Certificate) cf.generateCertificate(is);
            }

            String subjectDN    = x509.getSubjectX500Principal().getName();
            String certSerialNo = extractCN(subjectDN);
            String issuerDN     = x509.getIssuerX500Principal().getName();
            String sigAlg       = x509.getSigAlgName();
            String cryptoAlgorithm = (sigAlg != null && sigAlg.toUpperCase().contains("SM"))
                    ? "SM2" : x509.getPublicKey().getAlgorithm();

            LocalDateTime validFrom  = x509.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime validUntil = x509.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            // ===== 2. 将 .cer 文件保存到本地 certs/ 目录（仅 server/client 角色）=====
            boolean saveLocally = "server".equals(role) || "client".equals(role);
            String localPath = "";
            if (saveLocally) {
                String certFileName = certSerialNo + "_SIGN.cer";
                String certsDir = resolveCertsDir();
                Path certFilePath = Paths.get(certsDir, certFileName);
                Path savedPath = certificateFileService.saveLocalCertificate(certFilePath, fileBytes);
                localPath = "certs/" + savedPath.getFileName().toString();
                log.info("[证书上传] 已保存到本地: {}", savedPath.toAbsolutePath());
            } else {
                log.info("[证书上传] role={} 不保存到本地，仅导入到管控平台", role);
            }

            // ===== 3. 将证书导入到管控平台 ukey_certificate 表 =====
            String importResult = importToMonitorPlatform(
                    certSerialNo,
                    displayName.isEmpty() ? certSerialNo : displayName,
                    pin,
                    issuerDN,
                    validFrom,
                    validUntil,
                    fileBytes,
                    role
            );

            // ===== 4. 组装返回结果 =====
            Map<String, Object> data = new HashMap<>();
            data.put("certSerialNo",    certSerialNo);
            data.put("issuer",          issuerDN);
            data.put("subject",         subjectDN);
            data.put("validFrom",       validFrom.toString());
            data.put("validUntil",      validUntil.toString());
            data.put("cryptoAlgorithm", cryptoAlgorithm);
            data.put("localPath",       localPath);
            data.put("role",            role);
            data.put("importResult",    importResult);

            log.info("[证书上传] 解析成功: certSerialNo={}, role={}", certSerialNo, role);
            return Result.ok(data);
        } catch (Exception e) {
            log.error("[证书上传] 失败", e);
            return Result.error("证书处理失败: " + e.getMessage());
        }
    }

    /**
     * 将证书导入到管控平台
     */
    private String importToMonitorPlatform(String certSerialNo, String displayName, String pin,
                                            String issuer, LocalDateTime validFrom, LocalDateTime validUntil,
                                            byte[] certBytes, String role) {
        try {
            // 将证书内容转为 PEM 格式存入 certificateContent
            String pemContent = toPemString(certBytes);

            Map<String, String> params = new HashMap<>();
            params.put("certSerialNo",  certSerialNo);
            params.put("displayName",   displayName);
            params.put("pin",           pin);
            params.put("issuer",        issuer);
            params.put("validFrom",     validFrom.toString());
            params.put("validUntil",    validUntil.toString());
            params.put("remark",        "由客户端导入，角色: " + role);
            // certificateContent 字段：通过 register 接口直接存 PEM
            params.put("certificateContent", pemContent);
            // 仅客户端证书才绑定 clientId，服务端证书不绑定
            if ("client".equals(role)) {
                String clientId = monitorPlatformClient.getClientId();
                if (clientId != null && !clientId.isEmpty()) {
                    params.put("boundClientId", clientId);
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

            // 实时从 ClientAuthService 读取最新地址（支持界面动态保存后立即生效）
            String platformUrl = clientAuthService.getMonitorPlatformUrl();
            if (platformUrl == null || platformUrl.trim().isEmpty()) {
                return "管控平台地址未配置，跳过远程导入（证书已保存到本地）";
            }
            String url = platformUrl + "/cert/import";
            Map<?, ?> response = restTemplate.postForObject(url, entity, Map.class);
            if (response != null) {
                Object msg = response.get("msg");
                log.info("[证书上传] 导入管控平台结果: {}", msg);
                return msg != null ? msg.toString() : "已导入";
            }
            return "导入完成";
        } catch (Exception e) {
            log.warn("[证书上传] 导入管控平台失败（不影响本地保存）: {}", e.getMessage());
            return "管控平台导入失败: " + e.getMessage();
        }
    }

    /**
     * 将 DER 格式证书字节转换为 PEM 字符串
     */
    private String toPemString(byte[] derBytes) {
        String base64 = Base64.getEncoder().encodeToString(derBytes);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        // 每 64 字符换行
        int i = 0;
        while (i < base64.length()) {
            int end = Math.min(i + 64, base64.length());
            sb.append(base64, i, end).append("\n");
            i = end;
        }
        sb.append("-----END CERTIFICATE-----");
        return sb.toString();
    }

    /**
     * 解析运行目录下的 certs/ 绝对路径
     * 优先使用运行目录（dist 环境），其次使用项目根目录
     */
    private String resolveCertsDir() {
        String workDir = System.getProperty("user.dir");
        return workDir + File.separator + "certs";
    }

    /**
     * 从 DN 字符串中提取 CN 字段（_ 之前的部分作为 authId）
     * 例如: "CN=44030000003330000126_1E2D11" → "44030000003330000126"
     * 返回完整 CN（含 _ 后缀），调用方按需截取
     */
    private String extractCN(String dn) {
        if (dn == null) return "";
        String[] parts = dn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        return dn;
    }
}
