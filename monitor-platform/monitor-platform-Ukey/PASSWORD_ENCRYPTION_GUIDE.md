# 密码加密解密功能使用说明

## 功能概述

为了提升系统安全性,实现了密码在传输过程中的加密保护:
- **前端**: 使用AES-256-GCM算法加密密码后传输
- **后端**: 接收加密密码后解密,再进行哈希验证
- **数据库**: 仍然存储SHA-256加盐哈希值(不变)

## 架构说明

```
前端明文密码 → AES加密 → 网络传输 → 后端AES解密 → SHA-256加盐哈希 → 数据库比对
```

## 配置说明

### 1. 生成加密密钥

使用以下任一方法生成32字节的Base64编码密钥:

**方法1: 使用OpenSSL**
```bash
openssl rand -base64 32
```

**方法2: 使用Java代码**
```java
String key = EncryptUtil.generateKey();
System.out.println(key);
```

### 2. 配置密钥

在 `bootstrap.yml` 中配置(或通过环境变量):

```yaml
ukey:
  password:
    encrypt-key: ${UKEY_PASSWORD_ENCRYPT_KEY:your-base64-key-here}
```

**推荐方式**: 使用环境变量 `UKEY_PASSWORD_ENCRYPT_KEY`

## 后端接口说明

### 1. UKey登录接口 (`POST /cert/login`)

**请求示例(加密后)**:
```json
{
  "certSerialNo": "44030000003330000126",
  "pin": "Base64EncodedEncryptedPIN"
}
```

**兼容性**: 
- 如果未配置 `encrypt-key`,后端接受明文PIN(向后兼容)
- 如果配置了 `encrypt-key`,后端会尝试解密,解密失败则返回错误

### 2. 管理员登录接口 (`POST /cert/admin-login`)

**请求示例(加密后)**:
```json
{
  "username": "admin",
  "password": "Base64EncodedEncryptedPassword"
}
```

### 3. 获取加密密钥接口 (`GET /cert/encrypt-key`)

**用途**: 前端可以调用此接口获取加密密钥

**响应示例**:
```json
{
  "code": 200,
  "msg": "获取加密密钥成功",
  "data": {
    "encryptKey": "generated-base64-key",
    "algorithm": "AES-256-GCM"
  }
}
```

**⚠️ 安全警告**: 
- 此接口必须通过HTTPS调用
- 建议在前端初始化时调用一次,缓存密钥
- 生产环境建议固定配置密钥,不依赖动态获取

## 前端实现示例 (JavaScript)

### 使用 CryptoJS 库加密

```javascript
import CryptoJS from 'crypto-js';

/**
 * AES-GCM 加密 (需要 Web Crypto API)
 * @param {string} text - 明文密码
 * @param {string} keyBase64 - Base64编码的密钥
 * @returns {string} Base64编码的密文(包含IV)
 */
async function encryptPassword(text, keyBase64) {
  // 将Base64密钥转换为字节数组
  const keyBytes = Uint8Array.from(atob(keyBase64), c => c.charCodeAt(0));
  
  // 生成随机IV (12字节)
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  
  // 导入密钥
  const cryptoKey = await window.crypto.subtle.importKey(
    'raw',
    keyBytes,
    'AES-GCM',
    false,
    ['encrypt']
  );
  
  // 加密
  const encodedText = new TextEncoder().encode(text);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv },
    cryptoKey,
    encodedText
  );
  
  // 合并IV和密文: [IV(12字节)][密文]
  const encryptedBytes = new Uint8Array(encrypted);
  const combined = new Uint8Array(iv.length + encryptedBytes.length);
  combined.set(iv);
  combined.set(encryptedBytes, iv.length);
  
  // 转换为Base64
  return btoa(String.fromCharCode(...combined));
}

// 使用示例
async function login() {
  // 1. 获取加密密钥(或从配置中读取)
  const response = await fetch('/cert/encrypt-key');
  const { encryptKey } = await response.json().then(r => r.data);
  
  // 2. 加密密码
  const pin = '123456';
  const encryptedPin = await encryptPassword(pin, encryptKey);
  
  // 3. 发送登录请求
  const loginResult = await fetch('/cert/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      certSerialNo: '44030000003330000126',
      pin: encryptedPin
    })
  });
  
  console.log(await loginResult.json());
}
```

## 测试加密解密

### 使用Postman测试

1. **生成密钥** (使用在线工具或OpenSSL)
   ```
   openssl rand -base64 32
   # 输出示例: YmFzZTY0ZW5jb2RlZGtleWhlcmVleGFtcGxlMTIzNDU2Nzg5MA==
   ```

2. **加密密码** (使用在线AES-GCM加密工具)
   - 明文: `123456`
   - 密钥: `YmFzZTY0ZW5jb2RlZGtleWhlcmVleGFtcGxlMTIzNDU2Nzg5MA==`
   - 模式: `GCM`
   - 输出Base64密文

3. **发送请求**
   ```
   POST http://localhost:port/cert/login
   Content-Type: application/json
   
   {
     "certSerialNo": "44030000003330000126",
     "pin": "加密后的Base64密文"
   }
   ```

## 安全建议

1. **生产环境必须配置加密密钥**
   - 通过环境变量设置: `export UKEY_PASSWORD_ENCRYPT_KEY=your-key`
   - 不要将密钥硬编码在配置文件中

2. **使用HTTPS**
   - 所有密码相关接口必须通过HTTPS调用
   - 防止中间人攻击

3. **密钥管理**
   - 定期更换加密密钥
   - 使用密钥管理服务(KMS)存储密钥
   - 不同环境使用不同密钥

4. **前端安全**
   - 加密后清除明文密码变量
   - 防止XSS攻击窃取密钥
   - 使用Content-Security-Policy

## 向后兼容性

系统支持平滑升级:
- **未配置密钥时**: 后端接受明文密码(兼容旧版前端)
- **配置密钥后**: 后端期望接收加密密码

**升级步骤**:
1. 先部署后端代码(不配置密钥)
2. 前端升级加密功能
3. 配置后端加密密钥
4. 完成升级

## 技术细节

### 加密算法
- **算法**: AES-256-GCM
- **密钥长度**: 256位(32字节)
- **IV长度**: 12字节(随机生成)
- **GCM Tag**: 128位

### 数据格式
```
Base64( [IV(12字节)] + [密文] )
```

### 密码存储
```
数据库存储 = SHA-256( salt + 明文PIN )
```

## 常见问题

### Q1: 解密失败怎么办?
**A**: 检查以下几点:
1. 前后端使用的密钥是否一致
2. 前端是否正确实现了AES-GCM加密
3. 密文格式是否正确(必须包含IV)
4. 查看后端日志: `[UKeyLogin] PIN码解密失败`

### Q2: 可以不加密吗?
**A**: 可以,但不推荐。如果不配置 `encrypt-key`,后端会接受明文密码。但生产环境强烈建议启用加密。

### Q3: 如何更换密钥?
**A**: 
1. 生成新密钥
2. 更新后端配置 `UKEY_PASSWORD_ENCRYPT_KEY`
3. 更新前端使用的密钥
4. 重启服务

### Q4: 数据库中的PIN码需要改吗?
**A**: 不需要。加密只用于传输过程,数据库中仍然存储SHA-256加盐哈希值,无需修改。

## 相关文件

- 加密工具类: `monitor-platform-common/src/main/java/com/monitorplatform/common/util/EncryptUtil.java`
- 控制器: `monitor-platform-Ukey/src/main/java/com/monitorplatform/ukey/controller/UkeyCertificateController.java`
- 配置文件: `monitor-platform-Ukey/src/main/resources/bootstrap.yml`

## 更新日志

- **2026-05-14**: 初始版本,实现AES-256-GCM加密解密功能
  - 添加 `EncryptUtil` 工具类
  - 修改 `ukeyLogin` 和 `adminLogin` 接口支持解密
  - 添加 `/cert/encrypt-key` 接口获取密钥
  - 更新配置文件说明
