-- Replace placeholders before executing.
-- Target table: monitor-platform registry database.registry_client_config

SET @client_id = '__CLIENT_ID__';
SET @service_name = 'info-publish-client';
SET @config_content = '{
  "monitorPlatformUrl": "http://192.168.1.233:8063",
  "serverId": "__VAUTH_SERVER_ID__",
  "serverCertPath": "__VAUTH_SERVER_CERT_PATH__",
  "clientCertPath": "__VAUTH_CLIENT_CERT_PATH__",
  "authId": "__VAUTH_AUTH_ID__",
  "password": "__VAUTH_PASSWORD__",
  "mockMode": false,
  "spring": {
    "datasource": {
      "driverClassName": "com.mysql.jdbc.Driver",
      "url": "jdbc:mysql://__DB_HOST__:__DB_PORT__/info_publish_client?useUnicode=true&characterEncoding=utf8&useSSL=false",
      "username": "__DB_USERNAME__",
      "password": "__DB_PASSWORD__"
    }
  },
  "monitorPlatform": {
    "url": "http://192.168.1.233:8063"
  },
  "security": {
    "gatewaySourceValidationEnabled": true
  },
  "processBind": {
    "enabled": true,
    "endpointSourceValidationEnabled": true,
    "periodicIntegrityRecheckEnabled": false,
    "targetProcessName": "Sigma Play.exe",
    "targetProcessSearchRoots": "D:/SecurePublish/runtime",
    "targetProcessPath": "",
    "targetProcessMd5": "",
    "autoStartEnabled": false,
    "startWaitMillis": 15000,
    "startPollMillis": 500,
    "guardEnabled": true,
    "guardMode": "hybrid",
    "guardCheckIntervalMillis": 2000,
    "pidAliveCacheMillis": 2000,
    "udpTableCacheMillis": 200,
    "integrityCacheMillis": 1000,
    "integrityHashCacheMillis": 30000
  },
  "traffic": {
    "etwAccounting": {
      "enabled": true,
      "windowMs": 10000,
      "retentionMs": 120000,
      "maxRecords": 20000
    },
    "etwCollector": {
      "enabled": true,
      "autoStart": true,
      "sessionName": "InfoPublishClientUdpEtw",
      "workDir": "",
      "rotateIntervalMs": 10000,
      "commandTimeoutMs": 10000,
      "deleteEtlAfterParse": true
    }
  },
  "windivert": {
    "enabled": true,
    "mode": "proxy",
    "filter": "udp and outbound",
    "dllPath": "windivert",
    "queueLength": 4096,
    "queueTimeMs": 2000,
    "cacheTtlMs": 10000,
    "deleteRetainMs": 2000,
    "fallbackToUdpTable": true,
    "shadow": {
      "enabled": true,
      "packetFilter": "udp and outbound",
      "maxPacketSize": 65535,
      "eventRetention": 500,
      "payloadSampleBytes": 0,
      "logUnknownProcess": true,
      "pidLookupRetryCount": 3,
      "pidLookupRetryDelayMs": 30
    }
  },
  "processPolicy": {
    "enabled": true,
    "whitelist": [
      "Sigma Play.exe"
    ],
    "blockUnknownProcess": false,
    "processInfoCacheMs": 5000
  },
  "transparentProxy": {
    "enabled": true,
    "filter": "udp and outbound and !impostor and udp.DstPort == 9520",
    "relayHost": "192.168.1.233",
    "relayPort": 18092,
    "targetPorts": [
      9520
    ],
    "failOpen": false,
    "maxPacketSize": 65535,
    "pidLookupRetryCount": 5,
    "pidLookupRetryDelayMs": 30,
    "signPacket": false,
    "signatureSecret": "",
    "requireUkeyAuthentication": true
  },
  "relayFileSignature": {
    "enabled": false
  },
  "contentPreAudit": {
    "enabled": false,
    "ackSimulationEnabled": false,
    "ackLearningEnabled": false,
    "tokenRequired": false
  },
  "securePublish": {
    "enabled": true,
    "incomingDir": "D:/SecurePublish/incoming",
    "verifiedDir": "D:/SecurePublish/verified",
    "runtimeDir": "D:/SecurePublish/runtime",
    "rejectedDir": "D:/SecurePublish/rejected",
    "tempDir": "D:/SecurePublish/temp",
    "scanIntervalMs": 2000,
    "maxPackageSizeMb": 500,
    "testPackageEnabled": false,
    "signerEnabled": false,
    "signerInputDir": "D:/SecurePublish/signer/incoming",
    "signerOutputDir": "D:/SecurePublish/signer/outgoing",
    "signerArchiveDir": "D:/SecurePublish/signer/archive",
    "signerRejectedDir": "D:/SecurePublish/signer/rejected",
    "publisher": "secure-publish-signer",
    "signerAuditEnabled": true,
    "signerAuditUrl": "http://192.168.1.233:8065/content/detection/pre-audit",
    "signerAuditFailPolicy": "reject",
    "signerAuditTimeoutMs": 120000,
    "signerAuditPolicyVersion": "v1",
    "packageExpireMs": 604800000,
    "requireAuditPass": true,
    "signatureProvider": "rsa-db",
    "signatureKeyId": "default",
    "signerPrivateKeyPath": "",
    "verifierPublicKeyPath": "",
    "allowedExtensions": ""
  },
  "contentReleaseToken": {
    "enabled": false,
    "gatewayIssueUrl": "",
    "tokenTtlMs": 600000,
    "clientId": "",
    "policyVersion": "v1"
  }
}';

INSERT INTO registry_client_config (
  client_id,
  service_name,
  config_content,
  config_version,
  enabled,
  create_time,
  update_time
) VALUES (
  @client_id,
  @service_name,
  @config_content,
  1,
  1,
  NOW(),
  NOW()
) ON DUPLICATE KEY UPDATE
  service_name = VALUES(service_name),
  config_content = VALUES(config_content),
  config_version = IFNULL(config_version, 0) + 1,
  enabled = 1,
  update_time = NOW();
