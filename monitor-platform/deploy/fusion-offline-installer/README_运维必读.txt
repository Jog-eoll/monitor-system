网关平台一体化离线部署工具

一、推荐部署流程

1. 上传完整部署包到服务器，例如：

   /opt/fusion-offline

2. 进入部署包目录：

   cd /opt/fusion-offline

3. 首次生成配置文件：
   需要加权限 chomod
   sudo ./install.sh --init-config

   如果 config/deploy.conf 已存在，脚本不会覆盖。直接编辑现有文件即可。

4. 填写配置文件：

   vi config/deploy.conf

   重点填写 SERVER_IP、MySQL、Redis、MinIO、管理员账号、UKEY_SETUP_MODE，以及正式 UKey 模式下的 PIN/AuthID。JWT_SECRET 可留空，部署脚本会自动生成并写回配置文件。
   如果密码或密钥包含空格、$、`、' 等特殊字符，请使用单引号包裹，并按 Bash 规则转义。
   JAVA_OPTS 包含空格，必须写成：JAVA_OPTS='-Xms512m -Xmx1024m'
   脚本加载 deploy.conf 时会自动清理配置值中的 Windows 回车符、BOM、零宽字符和首尾空白。若 --validate 出现自动清理提醒，通常无需手工执行 od/sed 排查，继续按校验结果处理即可。

5. 先校验，不部署：

   sudo ./install.sh --validate

   校验会一次性列出配置、端口、MySQL、Redis、MinIO、镜像、SDK、证书、UKey 工具等问题。

6. 校验通过后执行部署：

   sudo ./install.sh --deploy

   --deploy 会先自动执行一次校验。校验失败不会启动容器。

7. 如果失败：

   只修改 config/deploy.conf 或补齐缺失文件，然后重新执行：

   sudo ./install.sh --validate
   sudo ./install.sh --deploy

   不需要重新输入所有配置。

二、UKey 模式

1. 现场暂缺 UKey，先做基础部署：

   UKEY_SETUP_MODE=deferred

   这种模式下：
   - 跳过 USB/UKey 检查
   - 平台写入 VAUTH_SERVER_REQUIRED=false
   - 加密网关写入 VAUTH_MOCK_MODE=true
   - 报告中 UKey、双向认证、国密加密、设备注册会显示“待配置”

2. UKey 到场后切换正式国密链路：

   vi config/deploy.conf

   将 UKEY_SETUP_MODE 改为 required，并填写 PLATFORM_UKEY_PIN、PLATFORM_AUTH_ID、GATEWAY_UKEY_PIN、GATEWAY_AUTH_ID。
   PLATFORM_UKEY_PATH/SN/CER_SN/CER_ID 和 GATEWAY_UKEY_PATH/SN/CER_SN/CER_ID 可留空，脚本会通过 tools/ukey-list 按 AuthID 自动识别并写回配置文件。

   然后执行：

   sudo ./install.sh --validate
   sudo ./install.sh --deploy

   也可以使用交互式 UKey 采集：

   sudo ./install.sh --configure-ukey

三、证书放置规则

证书必须放在部署包目录下：

   publish-gateway/certs/

例如部署包是 /opt/fusion-offline，则目录是：

   /opt/fusion-offline/publish-gateway/certs/

不是运行目录 /opt/publish-gateway/certs/。

正式 UKey 模式下，脚本会兼容以下网关证书命名：

   publish-gateway/certs/${GATEWAY_AUTH_ID}_SIGN.cer
   publish-gateway/certs/${GATEWAY_CERT_SERIAL_NO}_SIGN.cer
   publish-gateway/certs/${GATEWAY_AUTH_ID}_*_SIGN.cer

例如：

   GATEWAY_AUTH_ID=44030000013200000623
   GATEWAY_CERT_SERIAL_NO=44030000013200000623_18530D

可接受：

   publish-gateway/certs/44030000013200000623_SIGN.cer
   publish-gateway/certs/44030000013200000623_18530D_SIGN.cer

四、部署包目录要求

fusion-offline/
  install.sh
  config/
    deploy.conf.example
    deploy.conf
  images/
    monitor-platform-monolith*.tar
    gateway-udp-proxy*.tar
  monitor-platform/
    lib/
      libvauthsdk.so
  publish-gateway/
    lib/
      libvauthsdk.so
    certs/
    trust/
  tools/
    ukey-list
  report/
  logs/

五、常用命令

生成配置：

   sudo ./install.sh --init-config

校验配置：

   sudo ./install.sh --validate

执行部署：

   sudo ./install.sh --deploy

UKey 后置配置：

   sudo ./install.sh --configure-ukey

查看服务状态：

   sudo ./install.sh

   然后选择“查看服务状态”。

六、常见问题

1. MySQL 可访问但数据库校验失败

   原因通常是 MYSQL_USERNAME 没有创建数据库或访问数据库权限。
   处理方式：给该账号授权 monitor_platform 和 udp_proxy_gateway。

2. MinIO 无法访问

   检查 MINIO_HOST 和 MINIO_PORT。MINIO_PORT 必须是 API 端口，不是控制台端口。

3. 提示缺少网关认证证书

   检查证书是否放在 publish-gateway/certs/ 下，并确认文件名是否匹配 GATEWAY_AUTH_ID 或 GATEWAY_CERT_SERIAL_NO。

4. 平台日志提示 UKey 用户名或密码不正确

   先确认平台是否绑定到了正确的服务端 UKey。服务端 UKey 的 cerId 必须匹配 PLATFORM_AUTH_ID，不能绑定到加密网关 UKey。
   例如 PLATFORM_AUTH_ID=44010100003330003024 时，平台日志中“绑定服务端 UKey”的 cerId 应为 44010100003330003024_xxx。
   如果日志中绑定成 GATEWAY_AUTH_ID 对应的 UKey，例如 44030000003330000305_xxx，请使用更新后的 install.sh 重新执行 sudo ./install.sh --validate，让脚本自动按 AuthID 重新识别并回填 PLATFORM_UKEY_* / GATEWAY_UKEY_*。

5. 修改 .env 后服务不生效

   脚本部署时会强制重建容器。不要只执行 docker restart。

6. 容器日志出现 unable to allocate file descriptor table

   脚本生成的 docker-compose.yml 已包含 nofile=65535。使用更新后的 install.sh 重新执行 sudo ./install.sh --deploy，让容器重建后生效。

7. 不要使用相对路径

   PLATFORM_DIR 和 GATEWAY_DIR 建议使用绝对路径，例如：

   PLATFORM_DIR=/opt/monitor-platform-monolith
   GATEWAY_DIR=/opt/publish-gateway
