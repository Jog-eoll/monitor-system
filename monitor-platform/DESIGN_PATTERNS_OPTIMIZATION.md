# 监控平台设计模式优化方案

## 📋 目录

- [1. 概述](#1-概述)
- [2. 策略模式 (Strategy Pattern)](#2-策略模式)
- [3. 工厂模式 (Factory Pattern)](#3-工厂模式)
- [4. 观察者模式 (Observer Pattern)](#4-观察者模式)
- [5. 模板方法模式 (Template Method Pattern)](#5-模板方法模式)
- [6. 装饰器模式 (Decorator Pattern)](#6-装饰器模式)
- [7. 责任链模式 (Chain of Responsibility Pattern)](#7-责任链模式)
- [8. 适配器模式 (Adapter Pattern)](#8-适配器模式)
- [9. 建造者模式 (Builder Pattern)](#9-建造者模式)
- [10. 代理模式 (Proxy Pattern)](#10-代理模式)
- [11. 状态模式 (State Pattern)](#11-状态模式)
- [12. 命令模式 (Command Pattern)](#12-命令模式)
- [13. 外观模式 (Facade Pattern)](#13-外观模式)
- [14. 享元模式 (Flyweight Pattern)](#14-享元模式)
- [15. 实施建议与优先级](#15-实施建议与优先级)

---

## 1. 概述

本文档详细分析了监控平台项目中可以应用的各种设计模式，包括：
- ✅ 已经使用的设计模式
- ⚠️ 可以优化的设计模式
- 💡 新增的设计模式应用

**项目当前架构特点：**
- 微服务架构（Spring Cloud）
- 已部分使用策略模式（设备命令处理）
- 大量使用 Feign 进行服务间调用
- 多个 WebSocket 实时推送场景
- 复杂的设备链路管理

---

## 2. 策略模式 (Strategy Pattern)

### 2.1 应用场景

#### 场景1：内容识别策略 ⭐⭐⭐⭐⭐

**当前问题：**
- 文件：`ContentRecognitionServiceImpl.java`
- 存在复杂的 if-else 判断不同内容类型（text/image/video）和审核模式（qwen/local）
- 违反开闭原则，新增识别方式需修改核心代码

**优化方案：**

```java
// 1. 定义策略接口
public interface ContentRecognitionStrategy {
    RecognitionResultDTO recognize(String contentId, String data);
    String getContentType();  // text/image/video
    String getAuditMode();    // qwen/local/null(通用)
    default int getPriority() { return 0; }
}

// 2. 策略工厂
@Component
public class ContentRecognitionStrategyFactory {
    private Map<String, ContentRecognitionStrategy> strategyMap = new ConcurrentHashMap<>();
    
    @Autowired
    public ContentRecognitionStrategyFactory(List<ContentRecognitionStrategy> strategies) {
        strategies.forEach(s -> {
            String key = s.getContentType() + "_" + s.getAuditMode();
            strategyMap.put(key, s);
        });
    }
    
    public ContentRecognitionStrategy getStrategy(String contentType, String auditMode) {
        // 优先匹配特定模式，降级到通用模式
        return strategyMap.get(contentType + "_" + auditMode);
    }
}

// 3. 具体策略实现
@Component
public class TextQwenRecognitionStrategy implements ContentRecognitionStrategy {
    @Autowired private QwenApiService qwenApiService;
    
    @Override
    public RecognitionResultDTO recognize(String contentId, String data) {
        // 千问API文本识别逻辑
    }
    
    @Override public String getContentType() { return "text"; }
    @Override public String getAuditMode() { return "qwen"; }
}

@Component
public class ImageRecognitionStrategy implements ContentRecognitionStrategy {
    @Autowired private OcrService ocrService;
    
    @Override
    public RecognitionResultDTO recognize(String contentId, String data) {
        // OCR + 敏感词检测逻辑
    }
    
    @Override public String getContentType() { return "image"; }
}

// 4. 重构后的服务
@Service
public class ContentRecognitionServiceImpl implements ContentRecognitionService {
    @Autowired private ContentRecognitionStrategyFactory factory;
    @Value("${audit.mode:qwen}") private String auditMode;
    
    @Override
    public RecognitionResultDTO recognize(String contentId, String contentType, String data) {
        ContentRecognitionStrategy strategy = factory.getStrategy(contentType, auditMode);
        return strategy.recognize(contentId, data);
    }
}
```

**优势：**
- ✅ 符合开闭原则，新增识别方式只需添加新策略类
- ✅ 消除复杂条件分支
- ✅ 每个策略职责单一，易于测试

---

#### 场景2：违规类型处理策略 ⭐⭐⭐⭐

**当前问题：**
- 文件：`ContentDetectionService.java`
- 多个方法根据违规类型（pornography/violence/sensitive）进行不同的映射和计算

**优化方案：**

```java
// 1. 策略接口
public interface ViolationTypeStrategy {
    String getViolationTypeName();
    String mapToAlarmType();
    String calculateAlarmLevel(double confidence);
    String getViolationType();
}

// 2. 具体策略
@Component
public class PornographyViolationStrategy implements ViolationTypeStrategy {
    @Override public String getViolationTypeName() { return "色情内容"; }
    @Override public String mapToAlarmType() { return "content_violation_pornography"; }
    @Override public String calculateAlarmLevel(double confidence) {
        return confidence >= 0.9 ? "critical" : "serious";
    }
    @Override public String getViolationType() { return "pornography"; }
}

@Component
public class ViolenceViolationStrategy implements ViolationTypeStrategy {
    @Override public String getViolationTypeName() { return "暴力内容"; }
    @Override public String mapToAlarmType() { return "content_violation_violence"; }
    @Override public String calculateAlarmLevel(double confidence) {
        return confidence >= 0.9 ? "critical" : "serious";
    }
    @Override public String getViolationType() { return "violence"; }
}

// 3. 使用
@Service
public class ContentDetectionService {
    @Autowired private ViolationTypeStrategyFactory factory;
    
    private String mapViolationTypeToAlarmType(String violationType) {
        return factory.getStrategy(violationType).mapToAlarmType();
    }
}
```

---

#### 场景3：设备状态检测策略 ⭐⭐⭐

**当前问题：**
- 文件：`UnifiedDeviceServiceImpl.java`
- 不同设备类型（gateway/info_board/server）有不同的在线检测逻辑

**优化方案：**

```java
public interface DeviceStatusCheckStrategy {
    boolean checkOnline(UnifiedDevice device);
    String getDeviceType();
}

@Component
public class GatewayStatusCheckStrategy implements DeviceStatusCheckStrategy {
    @Autowired private StringRedisTemplate redisTemplate;
    
    @Override
    public boolean checkOnline(UnifiedDevice device) {
        // 网关设备：优先检查心跳，降级TCP探测
        String heartTime = redisTemplate.opsForValue()
            .get("device:heartbeat:" + device.getDeviceId());
        if (heartTime != null) return true;
        return probeTcpGateway(device.getIpAddress(), device.getPort());
    }
    
    @Override public String getDeviceType() { return "publish_gateway"; }
}

@Component
public class InfoBoardStatusCheckStrategy implements DeviceStatusCheckStrategy {
    @Override
    public boolean checkOnline(UnifiedDevice device) {
        // 情报板：由终端网关上报，平台不主动检测
        return false; // 跳过检测
    }
    
    @Override public String getDeviceType() { return "info_board"; }
}
```

---

#### 场景4：内容快照填充策略 ⭐⭐⭐

**当前问题：**
- 文件：`AlarmServiceImpl.java` 的 `fillContentSnapshot` 方法
- 根据 contentType 填充不同字段

**优化方案：**

```java
public interface ContentSnapshotStrategy {
    void fillSnapshot(AlarmRecord record, Map<String, Object> contentData);
    String getContentType();
}

@Component
public class TextSnapshotStrategy implements ContentSnapshotStrategy {
    @Override
    public void fillSnapshot(AlarmRecord record, Map<String, Object> data) {
        record.setContentData((String) data.get("data"));
    }
    @Override public String getContentType() { return "text"; }
}

@Component
public class ImageSnapshotStrategy implements ContentSnapshotStrategy {
    @Value("${minio.endpoint}") private String minioEndpoint;
    @Value("${minio.bucket}") private String minioBucket;
    
    @Override
    public void fillSnapshot(AlarmRecord record, Map<String, Object> data) {
        String minioPath = (String) data.get("minioPath");
        if (minioPath != null) { 
            record.setContentFileUrl(minioEndpoint + "/" + minioBucket + "/" + minioPath);
        }
    }
    @Override public String getContentType() { return "image"; }
}
```

---

## 3. 工厂模式 (Factory Pattern)

### 3.1 已经使用 ✅

**设备命令处理器工厂：**
- 文件：`CommandHandlerFactory.java`
- 已正确实现简单工厂模式

```java
@Component
public class CommandHandlerFactory {
    private Map<String, CommandHandler> handlerMap = new HashMap<>();
    
    @Autowired
    private List<CommandHandler> commandHandlers;
    
    @PostConstruct
    public void init() {
        for (CommandHandler handler : commandHandlers) {
            handlerMap.put(handler.getCommandType(), handler);
        }
    }
    
    public CommandHandler getHandler(String commandType) {
        return handlerMap.get(commandType);
    }
}
```

### 3.2 可优化场景

#### 场景1：设备DTO工厂 ⭐⭐⭐⭐

**当前问题：**
- 不同设备类型需要创建不同的DTO对象
- 存在大量重复的 BeanUtils.copyProperties 代码

**优化方案：**

```java
// 抽象工厂接口
public interface DeviceDTOFactory {
    UnifiedDeviceDTO createDTO(UnifiedDevice device);
    String getDeviceType();
}

// 具体工厂
@Component
public class InfoBoardDTOFactory implements DeviceDTOFactory {
    @Override
    public UnifiedDeviceDTO createDTO(UnifiedDevice device) {
        UnifiedDeviceDTO dto = new UnifiedDeviceDTO();
        BeanUtils.copyProperties(device, dto);
        // 情报板特有字段处理
        dto.setExtraAttribute("manufacturer", parseExtraInfo(device.getExtraInfo(), "manufacturer"));
        return dto;
    }
    
    @Override public String getDeviceType() { return "info_board"; }
}

// 工厂路由器
@Component
public class DeviceDTOFactoryRouter {
    private Map<String, DeviceDTOFactory> factoryMap = new ConcurrentHashMap<>();
    
    @Autowired
    public DeviceDTOFactoryRouter(List<DeviceDTOFactory> factories) {
        factories.forEach(f -> factoryMap.put(f.getDeviceType(), f));
    }
    
    public UnifiedDeviceDTO createDTO(UnifiedDevice device) {
        DeviceDTOFactory factory = factoryMap.get(device.getDeviceType());
        return factory != null ? factory.createDTO(device) : createDefaultDTO(device);
    }
}
```

---

#### 场景2：WebSocket消息工厂 ⭐⭐⭐

**当前问题：**
- 多个WebSocket Handler重复实现消息构建逻辑

**优化方案：**

```java
public interface WebSocketMessageFactory {
    String buildMessage(String type, Map<String, Object> data);
}

@Component
public class JsonWebSocketMessageFactory implements WebSocketMessageFactory {
    private ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String buildMessage(String type, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("timestamp", System.currentTimeMillis());
        message.put("data", data);
        return mapper.writeValueAsString(message);
    }
}
```

---

## 4. 观察者模式 (Observer Pattern)

### 4.1 已经部分使用 ⚠️

**当前实现：**
- WebSocket Handler 手动管理 session 列表
- 通过 broadcast() 方法推送消息给所有观察者

**问题：**
- 三个 WebSocket Handler 代码高度重复
- 缺乏统一的事件管理机制

### 4.2 优化方案：统一事件总线 ⭐⭐⭐⭐⭐

```java
// 1. 定义事件接口
public interface PlatformEvent {
    String getEventType();
    Map<String, Object> getPayload();
    default long getTimestamp() { return System.currentTimeMillis(); }
}

// 2. 具体事件
public class AlarmTriggeredEvent implements PlatformEvent {
    private AlarmRecord alarm;
    
    @Override public String getEventType() { return "ALARM_TRIGGERED"; }
    @Override public Map<String, Object> getPayload() {
        Map<String, Object> data = new HashMap<>();
        data.put("alarmId", alarm.getId());
        data.put("alarmType", alarm.getAlarmType());
        // ...
        return data;
    }
}

public class DeviceStatusChangedEvent implements PlatformEvent {
    private UnifiedDevice device;
    private String oldStatus;
    private String newStatus;
    
    @Override public String getEventType() { return "DEVICE_STATUS_CHANGED"; }
    @Override public Map<String, Object> getPayload() {
        // ...
    }
}

// 3. 事件监听器接口
public interface EventListener {
    void onEvent(PlatformEvent event);
    String getEventType(); // 订阅的事件类型
}

// 4. 事件总线
@Component
public class EventBus {
    private Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    
    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }
    
    public void publish(PlatformEvent event) {
        List<EventListener> eventListeners = listeners.get(event.getEventType());
        if (eventListeners != null) {
            eventListeners.forEach(listener -> {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.error("事件处理失败", e);
                }
            });
        }
    }
}

// 5. WebSocket推送监听器
@Component
public class AlarmWebSocketListener implements EventListener {
    @Autowired private AlarmWebSocketHandler webSocketHandler;
    
    @Override
    public void onEvent(PlatformEvent event) {
        String message = buildMessage(event);
        webSocketHandler.broadcast(message);
    }
    
    @Override public String getEventType() { return "ALARM_TRIGGERED"; }
}

// 6. 使用示例
@Service
public class AlarmServiceImpl implements AlarmService {
    @Autowired private EventBus eventBus;
    
    @Override
    public AlarmRecord receiveAlarm(AlarmReceiveDTO dto) {
        // ... 保存告警
        AlarmRecord record = saveAlarm(dto);
        
        // 发布事件
        AlarmTriggeredEvent event = new AlarmTriggeredEvent(record);
        eventBus.publish(event);
        
        return record;
    }
}
```

**优势：**
- ✅ 解耦事件发布者和订阅者
- ✅ 统一事件管理，消除重复代码
- ✅ 易于扩展新的事件类型和监听器
- ✅ 支持异步事件处理

---

## 5. 模板方法模式 (Template Method Pattern)

### 5.1 应用场景

#### 场景1：设备命令执行模板 ⭐⭐⭐⭐

**当前问题：**
- 各种命令处理器（Reboot/Blackscreen/Upgrade等）有相似的执行流程
- 缺乏统一的异常处理和日志记录

**优化方案：**

```java
// 抽象模板类
public abstract class AbstractCommandHandler implements CommandHandler {
    
    @Override
    public final void handle(DeviceCommandDTO dto) {
        log.info("开始执行命令: type={}, device={}, operator={}", 
                getCommandType(), dto.getDeviceId(), dto.getOperator());
        
        try {
            // 1. 参数校验
            validate(dto);
            
            // 2. 前置检查
            preCheck(dto);
            
            // 3. 执行命令（子类实现）
            executeCommand(dto);
            
            // 4. 后置处理
            postProcess(dto);
            
            log.info("命令执行成功: type={}, device={}", 
                    getCommandType(), dto.getDeviceId());
        } catch (Exception e) {
            log.error("命令执行失败: type={}, device={}", 
                    getCommandType(), dto.getDeviceId(), e);
            handleException(dto, e);
            throw e;
        }
    }
    
    // 模板方法步骤
    protected void validate(DeviceCommandDTO dto) {
        if (dto.getDeviceId() == null) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
    }
    
    protected void preCheck(DeviceCommandDTO dto) {
        // 默认空实现，子类可覆盖
    }
    
    protected abstract void executeCommand(DeviceCommandDTO dto);
    
    protected void postProcess(DeviceCommandDTO dto) {
        // 默认空实现，子类可覆盖
    }
    
    protected void handleException(DeviceCommandDTO dto, Exception e) {
        // 默认空实现，子类可覆盖
    }
}

// 具体实现
@Component
public class RebootCommandHandler extends AbstractCommandHandler {
    @Autowired private RestTemplate restTemplate;
    
    @Override
    protected void preCheck(DeviceCommandDTO dto) {
        // 检查设备是否在线
        if (!isDeviceOnline(dto.getDeviceId())) {
            throw new RuntimeException("设备离线，无法执行重启");
        }
    }
    
    @Override
    protected void executeCommand(DeviceCommandDTO dto) {
        // 调用设备重启接口
        String url = "http://" + dto.getDeviceIp() + "/api/reboot";
        restTemplate.postForObject(url, null, String.class);
    }
    
    @Override
    protected void postProcess(DeviceCommandDTO dto) {
        // 记录操作日志
        logOperation(dto, "设备重启");
    }
    
    @Override public String getCommandType() { return "reboot"; }
}

@Component
public class BlackscreenCommandHandler extends AbstractCommandHandler {
    @Override
    protected void executeCommand(DeviceCommandDTO dto) {
        // 黑屏命令执行逻辑
    }
    
    @Override public String getCommandType() { return "blackscreen"; }
}
```

---

#### 场景2：内容检测流程模板 ⭐⭐⭐⭐

**当前问题：**
- 内容检测流程：接收 → 识别 → 判断 → 告警/记录
- 不同类型内容流程相同，但具体实现不同

**优化方案：**

```java
public abstract class AbstractContentDetectionService {
    
    @Autowired protected ContentMonitorMapper mapper;
    @Autowired protected EventBus eventBus;
    
    /**
     * 模板方法：定义检测流程
     */
    public final DetectionResult detect(ContentReceiveDTO dto) {
        log.info("开始内容检测: contentId={}, type={}", dto.getContentId(), dto.getContentType());
        
        // 1. 接收内容
        ContentMonitor record = receiveContent(dto);
        
        // 2. 内容识别（子类实现）
        RecognitionResult recognition = recognizeContent(record);
        
        // 3. 更新检测结果
        updateDetectionResult(record, recognition);
        
        // 4. 判断是否违规
        if (recognition.isViolation()) {
            // 5. 触发告警
            triggerAlarm(record, recognition);
            return DetectionResult.VIOLATION;
        } else {
            // 6. 记录正常内容
            recordCompliant(record);
            return DetectionResult.COMPLIANT;
        }
    }
    
    // 钩子方法：子类可覆盖
    protected ContentMonitor receiveContent(ContentReceiveDTO dto) {
        ContentMonitor record = new ContentMonitor();
        BeanUtils.copyProperties(dto, record);
        record.setReceiveTime(LocalDateTime.now());
        mapper.insert(record);
        return record;
    }
    
    protected abstract RecognitionResult recognizeContent(ContentMonitor record);
    
    protected void updateDetectionResult(ContentMonitor record, RecognitionResult result) {
        record.setIsViolation(result.isViolation());
        record.setConfidence(result.getConfidence());
        mapper.updateById(record);
    }
    
    protected void triggerAlarm(ContentMonitor record, RecognitionResult result) {
        AlarmTriggeredEvent event = new AlarmTriggeredEvent(record);
        eventBus.publish(event);
    }
    
    protected void recordCompliant(ContentMonitor record) {
        log.info("内容合规: contentId={}", record.getContentId());
    }
}

// 具体实现
@Service
public class TextContentDetectionService extends AbstractContentDetectionService {
    @Autowired private QwenApiService qwenApiService;
    
    @Override
    protected RecognitionResult recognizeContent(ContentMonitor record) {
        return qwenApiService.detectTextContent(record.getData(), record.getContentId());
    }
}

@Service
public class ImageContentDetectionService extends AbstractContentDetectionService {
    @Autowired private OcrService ocrService;
    
    @Override
    protected RecognitionResult recognizeContent(ContentMonitor record) {
        String text = ocrService.extractText(record.getData());
        // 进行敏感词检测
        return detectSensitiveWords(text);
    }
}
```

---

## 6. 装饰器模式 (Decorator Pattern)

### 6.1 应用场景

#### 场景1：告警推送装饰器 ⭐⭐⭐⭐

**当前问题：**
- 告警推送需要多种增强功能：日志记录、WebSocket推送、短信通知等
- 这些功能组合复杂，硬编码难以维护

**优化方案：**

```java
// 1. 定义推送接口
public interface AlarmPushService {
    void push(AlarmRecord alarm);
}

// 2. 基础推送实现
@Service
public class BasicAlarmPushService implements AlarmPushService {
    @Override
    public void push(AlarmRecord alarm) {
        // 基础推送：更新数据库状态
        log.info("基础告警推送: alarmId={}", alarm.getId());
    }
}

// 3. 装饰器基类
public abstract class AlarmPushDecorator implements AlarmPushService {
    protected AlarmPushService delegate;
    
    public AlarmPushDecorator(AlarmPushService delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void push(AlarmRecord alarm) {
        delegate.push(alarm);
    }
}

// 4. 具体装饰器
public class WebSocketPushDecorator extends AlarmPushDecorator {
    @Autowired private AlarmWebSocketHandler webSocketHandler;
    
    public WebSocketPushDecorator(AlarmPushService delegate) {
        super(delegate);
    }
    
    @Override
    public void push(AlarmRecord alarm) {
        super.push(alarm);
        // WebSocket推送
        String message = buildWebSocketMessage(alarm);
        webSocketHandler.broadcast(message);
    }
}

public class LoggingPushDecorator extends AlarmPushDecorator {
    public LoggingPushDecorator(AlarmPushService delegate) {
        super(delegate);
    }
    
    @Override
    public void push(AlarmRecord alarm) {
        super.push(alarm);
        // 记录推送日志
        log.info("告警推送日志: alarmId={}, type={}, level={}", 
                alarm.getId(), alarm.getAlarmType(), alarm.getAlarmLevel());
    }
}

public class SmsPushDecorator extends AlarmPushDecorator {
    @Autowired private SmsService smsService;
    
    public SmsPushDecorator(AlarmPushService delegate) {
        super(delegate);
    }
    
    @Override
    public void push(AlarmRecord alarm) {
        super.push(alarm);
        // 严重告警发送短信
        if ("critical".equals(alarm.getAlarmLevel())) {
            smsService.sendSms(alarm.getHandleOperator(), buildSmsContent(alarm));
        }
    }
}

// 5. 使用示例
@Configuration
public class AlarmPushConfig {
    @Bean
    public AlarmPushService alarmPushService(BasicAlarmPushService basicService) {
        AlarmPushService service = basicService;
        
        // 装饰：添加日志记录
        service = new LoggingPushDecorator(service);
        
        // 装饰：添加WebSocket推送
        service = new WebSocketPushDecorator(service);
        
        // 装饰：添加短信通知（可选）
        service = new SmsPushDecorator(service);
        
        return service;
    }
}
```

**优势：**
- ✅ 动态组合功能，避免类爆炸
- ✅ 符合开闭原则
- ✅ 每个装饰器职责单一

---

#### 场景2：设备状态更新装饰器 ⭐⭐⭐

```java
public interface DeviceStatusUpdater {
    void updateStatus(String deviceId, String newStatus);
}

// 基础更新
@Service
public class BasicDeviceStatusUpdater implements DeviceStatusUpdater {
    @Autowired private UnifiedDeviceMapper mapper;
    
    @Override
    public void updateStatus(String deviceId, String newStatus) {
        UnifiedDevice device = findDevice(deviceId);
        device.setStatus(newStatus);
        device.setUpdateTime(LocalDateTime.now());
        mapper.updateById(device);
    }
}

// 装饰器：Redis缓存同步
public class CacheSyncDecorator implements DeviceStatusUpdater {
    private DeviceStatusUpdater delegate;
    @Autowired private StringRedisTemplate redisTemplate;
    
    public CacheSyncDecorator(DeviceStatusUpdater delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void updateStatus(String deviceId, String newStatus) {
        delegate.updateStatus(deviceId, newStatus);
        // 同步更新Redis
        redisTemplate.opsForValue().set("device:status:" + deviceId, newStatus);
    }
}

// 装饰器：状态变更事件发布
public class EventPublishDecorator implements DeviceStatusUpdater {
    private DeviceStatusUpdater delegate;
    @Autowired private EventBus eventBus;
    
    public EventPublishDecorator(DeviceStatusUpdater delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void updateStatus(String deviceId, String newStatus) {
        String oldStatus = getCurrentStatus(deviceId);
        delegate.updateStatus(deviceId, newStatus);
        
        if (!oldStatus.equals(newStatus)) {
            eventBus.publish(new DeviceStatusChangedEvent(deviceId, oldStatus, newStatus));
        }
    }
}
```

---

## 7. 责任链模式 (Chain of Responsibility Pattern)

### 7.1 应用场景

#### 场景1：链路配置校验链 ⭐⭐⭐⭐⭐

**当前问题：**
- 文件：`TaskChainServiceImpl.java`
- 链路校验包含多个步骤：设备存在性、结构完整性、IP冲突等
- 校验逻辑耦合在一起

**优化方案：**

```java
// 1. 校验器接口
public interface ChainValidator {
    ValidationResult validate(TaskChainConfig chain, List<TaskChainNode> nodes);
    void setNext(ChainValidator next);
}

// 2. 抽象校验器
public abstract class AbstractChainValidator implements ChainValidator {
    protected ChainValidator next;
    
    @Override
    public void setNext(ChainValidator next) {
        this.next = next;
    }
    
    @Override
    public final ValidationResult validate(TaskChainConfig chain, List<TaskChainNode> nodes) {
        ValidationResult result = doValidate(chain, nodes);
        if (!result.isSuccess()) {
            return result;
        }
        // 校验通过，继续下一个
        return next != null ? next.validate(chain, nodes) : ValidationResult.success();
    }
    
    protected abstract ValidationResult doValidate(TaskChainConfig chain, List<TaskChainNode> nodes);
}

// 3. 具体校验器
@Component
public class DeviceExistenceValidator extends AbstractChainValidator {
    @Autowired private RestTemplate restTemplate;
    
    @Override
    protected ValidationResult doValidate(TaskChainConfig chain, List<TaskChainNode> nodes) {
        for (TaskChainNode node : nodes) {
            if (!checkDeviceExists(node.getDeviceId(), node.getDeviceType())) {
                return ValidationResult.fail("设备不存在: " + node.getDeviceId());
            }
        }
        return ValidationResult.success();
    }
}

@Component
public class StructureCompletenessValidator extends AbstractChainValidator {
    @Override
    protected ValidationResult doValidate(TaskChainConfig chain, List<TaskChainNode> nodes) {
        // 检查是否有发布服务器
        boolean hasPublishServer = nodes.stream()
            .anyMatch(n -> "publish_server".equals(n.getDeviceType()));
        if (!hasPublishServer) {
            return ValidationResult.fail("链路必须包含发布服务器");
        }
        
        // 检查是否有情报板
        boolean hasInfoBoard = nodes.stream()
            .anyMatch(n -> "info_board".equals(n.getDeviceType()));
        if (!hasInfoBoard) {
            return ValidationResult.fail("链路必须包含情报板");
        }
        
        return ValidationResult.success();
    }
}

@Component
public class IpConflictValidator extends AbstractChainValidator {
    @Override
    protected ValidationResult doValidate(TaskChainConfig chain, List<TaskChainNode> nodes) {
        Set<String> ipSet = new HashSet<>();
        for (TaskChainNode node : nodes) {
            if (!ipSet.add(node.getDeviceIp())) {
                return ValidationResult.fail("IP地址冲突: " + node.getDeviceIp());
            }
        }
        return ValidationResult.success();
    }
}

// 4. 校验链构建器
@Component
public class ChainValidationChainBuilder {
    @Autowired private DeviceExistenceValidator deviceValidator;
    @Autowired private StructureCompletenessValidator structureValidator;
    @Autowired private IpConflictValidator ipValidator;
    
    public ChainValidator buildValidationChain() {
        deviceValidator.setNext(structureValidator);
        structureValidator.setNext(ipValidator);
        return deviceValidator;
    }
}

// 5. 使用示例
@Service
public class TaskChainServiceImpl implements TaskChainService {
    @Autowired private ChainValidationChainBuilder chainBuilder;
    
    @Override
    public Boolean validateChainStructure(Long chainId) {
        TaskChainConfig chain = chainConfigMapper.selectById(chainId);
        List<TaskChainNode> nodes = getNodesByChainId(chainId);
        
        ChainValidator validator = chainBuilder.buildValidationChain();
        ValidationResult result = validator.validate(chain, nodes);
        
        if (!result.isSuccess()) {
            throw new RuntimeException("链路校验失败: " + result.getMessage());
        }
        return true;
    }
}
```

**优势：**
- ✅ 校验逻辑解耦，易于扩展
- ✅ 可动态调整校验顺序
- ✅ 每个校验器职责单一

---

#### 场景2：内容审核责任链 ⭐⭐⭐⭐

```java
// 内容审核链：敏感词检测 → AI识别 → 人工审核（高危）
public interface ContentAuditor {
    AuditResult audit(ContentMonitor content);
    void setNext(ContentAuditor next);
}

@Component
public class SensitiveWordAuditor extends AbstractContentAuditor {
    @Autowired private SensitiveWordService sensitiveWordService;
    
    @Override
    protected AuditResult doAudit(ContentMonitor content) {
        // 第一步：DFA敏感词检测
        RecognitionResult result = sensitiveWordService.detectSensitiveWords(
            content.getContentId(), content.getData());
        
        if (result.isViolation()) {
            // 敏感词命中，直接判定违规
            return AuditResult.violation("敏感词检测命中", result);
        }
        
        // 未命中，继续下一步
        return AuditResult.pass();
    }
}

@Component
public class AIAuditor extends AbstractContentAuditor {
    @Autowired private QwenApiService qwenApiService;
    
    @Override
    protected AuditResult doAudit(ContentMonitor content) {
        // 第二步：AI深度识别
        QwenDetectionResult result = qwenApiService.detectTextContent(
            content.getData(), content.getContentId());
        
        if ("violation".equals(result.getDetectionResult())) {
            return AuditResult.violation("AI识别违规", result);
        }
        
        return AuditResult.pass();
    }
}

@Component
public class ManualReviewAuditor extends AbstractContentAuditor {
    @Override
    protected AuditResult doAudit(ContentMonitor content) {
        // 第三步：高置信度疑似违规送人工审核
        if (content.getConfidence() > 0.7 && content.getConfidence() < 0.9) {
            return AuditResult.manualReview("需要人工复核");
        }
        return AuditResult.pass();
    }
}
```

---

## 8. 适配器模式 (Adapter Pattern)

### 8.1 应用场景

#### 场景1：第三方服务适配器 ⭐⭐⭐⭐

**当前问题：**
- 项目需要对接多个第三方服务（千问API、OCR服务、短信服务等）
- 各服务接口不统一，直接调用导致耦合

**优化方案：**

```java
// 1. 定义统一AI服务接口
public interface AIService {
    AIDetectionResult detectText(String text, String context);
    AIDetectionResult detectImage(String imageBase64, String context);
}

// 2. 千问适配器
@Component
public class QwenAIServiceAdapter implements AIService {
    @Autowired private QwenApiService qwenApiService;
    
    @Override
    public AIDetectionResult detectText(String text, String context) {
        // 将千问API的结果转换为统一格式
        QwenDetectionResult qwenResult = qwenApiService.detectTextContent(text, context);
        return convertToUnifiedResult(qwenResult);
    }
    
    @Override
    public AIDetectionResult detectImage(String imageBase64, String context) {
        // 千问多模态API适配
        // ...
    }
    
    private AIDetectionResult convertToUnifiedResult(QwenDetectionResult qwenResult) {
        AIDetectionResult result = new AIDetectionResult();
        result.setViolation("violation".equals(qwenResult.getDetectionResult()));
        result.setConfidence(qwenResult.getConfidence() / 100.0);
        result.setViolationType(qwenResult.getViolationType());
        return result;
    }
}

// 3. 本地模型适配器
@Component
public class LocalModelAIServiceAdapter implements AIService {
    @Autowired private LocalAuditService localAuditService;
    
    @Override
    public AIDetectionResult detectText(String text, String context) {
        QwenDetectionResult localResult = localAuditService.auditText(text, context);
        return convertToUnifiedResult(localResult);
    }
    
    @Override
    public AIDetectionResult detectImage(String imageBase64, String context) {
        // 本地图像识别模型适配
        // ...
    }
}

// 4. 使用统一接口
@Service
public class ContentRecognitionServiceImpl {
    @Autowired private AIService aiService;  // 根据配置注入不同实现
    
    @Override
    public RecognitionResultDTO recognize(String contentId, String contentType, String data) {
        AIDetectionResult result = aiService.detectText(data, contentId);
        // 处理统一格式的结果
        // ...
    }
}
```

---

#### 场景2：设备协议适配器 ⭐⭐⭐

```java
// 不同厂家情报板协议不同，需要统一适配
public interface InfoBoardProtocol {
    void sendContent(String ip, int port, String content);
    void blackscreen(String ip, int port);
    String getStatus(String ip, int port);
}

// 厂家A适配器
@Component
public class ManufacturerAProtocolAdapter implements InfoBoardProtocol {
    @Override
    public void sendContent(String ip, int port, String content) {
        // 厂家A的协议实现
        // ...
    }
    
    @Override public void blackscreen(String ip, int port) { /* ... */ }
    @Override public String getStatus(String ip, int port) { /* ... */ }
}

// 厂家B适配器
@Component
public class ManufacturerBProtocolAdapter implements InfoBoardProtocol {
    @Override
    public void sendContent(String ip, int port, String content) {
        // 厂家B的协议实现
        // ...
    }
    
    @Override public void blackscreen(String ip, int port) { /* ... */ }
    @Override public String getStatus(String ip, int port) { /* ... */ }
}

// 协议工厂
@Component
public class InfoBoardProtocolFactory {
    private Map<String, InfoBoardProtocol> protocolMap = new ConcurrentHashMap<>();
    
    @Autowired
    public InfoBoardProtocolFactory(List<InfoBoardProtocol> protocols) {
        // 根据厂家名称注册协议
    }
    
    public InfoBoardProtocol getProtocol(String manufacturer) {
        return protocolMap.get(manufacturer);
    }
}
```

---

## 9. 建造者模式 (Builder Pattern)

### 9.1 应用场景

#### 场景1：复杂设备对象构建 ⭐⭐⭐⭐

**当前问题：**
- `UnifiedDevice` 对象有大量字段
- 创建复杂设备对象时代码冗长

**优化方案：**

```java
// 在 UnifiedDevice 中添加 Builder
@Data
public class UnifiedDevice {
    private Long id;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private Integer port;
    private String status;
    private Double longitude;
    private Double latitude;
    private String extraInfo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UnifiedDevice device = new UnifiedDevice();
        
        public Builder deviceId(String deviceId) {
            device.setDeviceId(deviceId);
            return this;
        }
        
        public Builder deviceName(String deviceName) {
            device.setDeviceName(deviceName);
            return this;
        }
        
        public Builder deviceType(String deviceType) {
            device.setDeviceType(deviceType);
            return this;
        }
        
        public Builder ipAddress(String ip) {
            device.setIpAddress(ip);
            return this;
        }
        
        public Builder port(Integer port) {
            device.setPort(port);
            return this;
        }
        
        public Builder location(Double longitude, Double latitude) {
            device.setLongitude(longitude);
            device.setLatitude(latitude);
            return this;
        }
        
        public Builder extraInfo(Map<String, Object> extraInfo) {
            try {
                device.setExtraInfo(new ObjectMapper().writeValueAsString(extraInfo));
            } catch (Exception e) {
                throw new RuntimeException("序列化extraInfo失败", e);
            }
            return this;
        }
        
        public UnifiedDevice build() {
            // 设置默认值
            if (device.getStatus() == null) {
                device.setStatus("离线");
            }
            if (device.getCreateTime() == null) {
                device.setCreateTime(LocalDateTime.now());
            }
            device.setUpdateTime(LocalDateTime.now());
            return device;
        }
    }
}

// 使用示例
UnifiedDevice device = UnifiedDevice.builder()
    .deviceId("IB-001")
    .deviceName("情报板-001")
    .deviceType("info_board")
    .ipAddress("192.168.1.100")
    .port(9520)
    .location(116.397128, 39.916527)
    .extraInfo(Map.of("manufacturer", "厂家A", "model", "X100"))
    .build();
```

---

#### 场景2：告警记录构建器 ⭐⭐⭐

```java
public class AlarmRecordBuilder {
    private AlarmRecord record = new AlarmRecord();
    
    public static AlarmRecordBuilder create() {
        return new AlarmRecordBuilder();
    }
    
    public AlarmRecordBuilder type(String alarmType) {
        record.setAlarmType(alarmType);
        return this;
    }
    
    public AlarmRecordBuilder level(String alarmLevel) {
        record.setAlarmLevel(alarmLevel);
        return this;
    }
    
    public AlarmRecordBuilder device(String deviceId, String deviceName) {
        record.setDeviceId(deviceId);
        record.setDeviceName(deviceName);
        return this;
    }
    
    public AlarmRecordBuilder content(String contentId, String contentType) {
        record.setContentId(contentId);
        record.setContentType(contentType);
        return this;
    }
    
    public AlarmRecordBuilder violation(String violationType, String detail) {
        record.setViolationType(violationType);
        record.setViolationDetail(detail);
        return this;
    }
    
    public AlarmRecordBuilder location(String boardIp, Integer boardPort) {
        record.setBoardIp(boardIp);
        record.setBoardPort(boardPort);
        return this;
    }
    
    public AlarmRecord build() {
        record.setAlarmTime(LocalDateTime.now());
        record.setHandleStatus("pending");
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}

// 使用
AlarmRecord alarm = AlarmRecordBuilder.create()
    .type("content_violation")
    .level("critical")
    .device("GW-001", "网关001")
    .content("CNT-001", "text")
    .violation("pornography", "检测到色情内容")
    .location("192.168.1.100", 9520)
    .build();
```

---

## 10. 代理模式 (Proxy Pattern)

### 10.1 应用场景

#### 场景1：Feign调用降级代理 ⭐⭐⭐⭐

**当前问题：**
- Feign调用失败时缺乏统一的降级处理
- 各服务自己处理异常，代码重复

**优化方案：**

```java
// Feign调用代理，统一处理降级
public class FeignServiceProxy<T> implements InvocationHandler {
    private T target;
    private String serviceName;
    private T fallback;
    
    public FeignServiceProxy(T target, String serviceName, T fallback) {
        this.target = target;
        this.serviceName = serviceName;
        this.fallback = fallback;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (Exception e) {
            log.error("Feign调用失败: service={}, method={}", serviceName, method.getName(), e);
            // 调用降级方法
            Method fallbackMethod = fallback.getClass().getMethod(method.getName(), method.getParameterTypes());
            return fallbackMethod.invoke(fallback, args);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, String serviceName, T fallback) {
        return (T) Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            new FeignServiceProxy<>(target, serviceName, fallback)
        );
    }
}

// 使用示例
@Configuration
public class FeignProxyConfig {
    @Bean
    public DeviceFeignClient deviceFeignClientProxy(DeviceFeignClient client) {
        DeviceFeignClient fallback = new DeviceFeignClientFallback();
        return FeignServiceProxy.createProxy(client, "monitor-device", fallback);
    }
}

// 降级实现
public class DeviceFeignClientFallback implements DeviceFeignClient {
    @Override
    public Map<String, Object> updateStatusByIp(String ip, String status) {
        log.warn("Device服务降级: updateStatusByIp ip={}", ip);
        return Map.of("code", 500, "msg", "服务降级");
    }
    
    @Override
    public Map<String, Object> getLocationByIp(String ip) {
        log.warn("Device服务降级: getLocationByIp ip={}", ip);
        return Map.of("code", 500, "msg", "服务降级", "data", null);
    }
}
```

---

## 11. 状态模式 (State Pattern)

### 11.1 应用场景

#### 场景1：设备状态机 ⭐⭐⭐⭐⭐

**当前问题：**
- 设备状态转换逻辑分散在各处
- 状态转换规则不清晰（如：离线→在线→告警→离线）

**优化方案：**

```java
// 1. 状态接口
public interface DeviceState {
    String getStateName();
    void enterState(UnifiedDevice device);
    void exitState(UnifiedDevice device);
    
    // 状态转换
    void handleOnline(UnifiedDevice device);
    void handleOffline(UnifiedDevice device);
    void handleAlarm(UnifiedDevice device);
    void handleMaintenance(UnifiedDevice device);
}

// 2. 抽象状态类
public abstract class AbstractDeviceState implements DeviceState {
    @Autowired protected UnifiedDeviceMapper mapper;
    @Autowired protected EventBus eventBus;
    
    @Override
    public void enterState(UnifiedDevice device) {
        String oldStatus = device.getStatus();
        device.setStatus(getStateName());
        device.setUpdateTime(LocalDateTime.now());
        mapper.updateById(device);
        
        // 发布状态变更事件
        eventBus.publish(new DeviceStatusChangedEvent(
            device.getDeviceId(), oldStatus, getStateName()));
    }
    
    @Override public void exitState(UnifiedDevice device) {
        // 默认空实现
    }
}

// 3. 具体状态
@Component
public class OnlineState extends AbstractDeviceState {
    @Override public String getStateName() { return "在线"; }
    
    @Override
    public void handleOffline(UnifiedDevice device) {
        DeviceState offlineState = SpringContextUtil.getBean(OfflineState.class);
        exitState(device);
        offlineState.enterState(device);
    }
    
    @Override
    public void handleAlarm(UnifiedDevice device) {
        DeviceState alarmState = SpringContextUtil.getBean(AlarmState.class);
        exitState(device);
        alarmState.enterState(device);
    }
    
    @Override public void handleOnline(UnifiedDevice device) {
        // 已经是在线状态，无需转换
    }
    
    @Override public void handleMaintenance(UnifiedDevice device) {
        DeviceState maintenanceState = SpringContextUtil.getBean(MaintenanceState.class);
        exitState(device);
        maintenanceState.enterState(device);
    }
}

@Component
public class AlarmState extends AbstractDeviceState {
    @Override public String getStateName() { return "告警"; }
    
    @Override
    public void enterState(UnifiedDevice device) {
        super.enterState(device);
        // 告警状态特殊处理：通知相关人员
        notifyAlarm(device);
    }
    
    @Override
    public void handleOnline(UnifiedDevice device) {
        // 告警处理后恢复在线
        DeviceState onlineState = SpringContextUtil.getBean(OnlineState.class);
        exitState(device);
        onlineState.enterState(device);
    }
    
    private void notifyAlarm(UnifiedDevice device) {
        // 发送告警通知
    }
}

// 4. 设备上下文
@Component
public class DeviceContext {
    private Map<String, DeviceState> stateMap = new ConcurrentHashMap<>();
    
    @Autowired
    public DeviceContext(List<DeviceState> states) {
        states.forEach(state -> stateMap.put(state.getStateName(), state));
    }
    
    public void transitionTo(UnifiedDevice device, String targetState) {
        DeviceState state = stateMap.get(targetState);
        if (state == null) {
            throw new IllegalArgumentException("无效状态: " + targetState);
        }
        
        DeviceState currentState = stateMap.get(device.getStatus());
        if (currentState != null) {
            currentState.exitState(device);
        }
        state.enterState(device);
    }
}

// 5. 使用示例
@Service
public class DeviceStatusServiceImpl {
    @Autowired private DeviceContext deviceContext;
    
    public void setDeviceAlarm(String deviceId) {
        UnifiedDevice device = getDevice(deviceId);
        deviceContext.transitionTo(device, "告警");
    }
}
```

**优势：**
- ✅ 状态转换逻辑集中管理
- ✅ 符合开闭原则，新增状态容易
- ✅ 避免大量的 if-else 判断

---

## 12. 命令模式 (Command Pattern)

### 12.1 已经使用 ✅

**设备命令处理：**
- 文件：`CommandHandler.java` 及其实现类
- 已正确实现命令模式

### 12.2 可扩展场景

#### 场景1：告警处理命令 ⭐⭐⭐

```java
// 告警处理命令接口
public interface AlarmCommand {
    void execute(AlarmRecord alarm);
    void undo(AlarmRecord alarm);
    String getCommandName();
}

// 具体命令
@Component
public class BlackscreenAlarmCommand implements AlarmCommand {
    @Autowired private RestTemplate restTemplate;
    
    @Override
    public void execute(AlarmRecord alarm) {
        // 执行黑屏处置
        String url = "http://" + alarm.getBoardIp() + "/api/blackscreen";
        restTemplate.postForObject(url, null, String.class);
    }
    
    @Override
    public void undo(AlarmRecord alarm) {
        // 恢复显示
        String url = "http://" + alarm.getBoardIp() + "/api/restore";
        restTemplate.postForObject(url, null, String.class);
    }
    
    @Override public String getCommandName() { return "blackscreen"; }
}

@Component
public class ContentDeleteAlarmCommand implements AlarmCommand {
    @Override
    public void execute(AlarmRecord alarm) {
        // 删除违规内容
        // ...
    }
    
    @Override
    public void undo(AlarmRecord alarm) {
        // 恢复内容（从备份）
        // ...
    }
    
    @Override public String getCommandName() { return "delete_content"; }
}

// 命令Invoker
@Component
public class AlarmCommandInvoker {
    private Deque<AlarmCommand> commandHistory = new ArrayDeque<>();
    
    public void executeCommand(AlarmCommand command, AlarmRecord alarm) {
        command.execute(alarm);
        commandHistory.push(command);
    }
    
    public void undoLastCommand(AlarmRecord alarm) {
        if (!commandHistory.isEmpty()) {
            AlarmCommand command = commandHistory.pop();
            command.undo(alarm);
        }
    }
}
```

---

## 13. 外观模式 (Facade Pattern)

### 13.1 应用场景

#### 场景1：监控平台统一服务外观 ⭐⭐⭐⭐

**当前问题：**
- 前端需要调用多个微服务完成一个业务操作
- 如：内容监看需要调用 content、device、alarm、forward 多个服务

**优化方案：**

```java
// 外观类：简化内容监看操作
@Service
public class ContentMonitorFacade {
    @Autowired private ContentMonitorService contentService;
    @Autowired private DeviceService deviceService;
    @Autowired private AlarmService alarmService;
    @Autowired private ForwardService forwardService;
    
    /**
     * 获取情报板监控概览（一站式接口）
     */
    public BoardMonitorOverviewVO getBoardMonitorOverview(String boardIp) {
        BoardMonitorOverviewVO overview = new BoardMonitorOverviewVO();
        
        // 1. 获取情报板设备信息
        overview.setDeviceInfo(deviceService.getByIp(boardIp));
        
        // 2. 获取最新内容
        overview.setLatestContent(contentService.getLatestByBoardIp(boardIp));
        
        // 3. 获取待处理告警
        overview.setPendingAlarms(alarmService.getPendingByBoardIp(boardIp));
        
        // 4. 获取链路信息
        overview.setChainInfo(forwardService.getChainByBoardIp(boardIp));
        
        return overview;
    }
    
    /**
     * 应急黑屏处置（一站式操作）
     */
    public EmergencyResult emergencyBlackscreen(String boardIp, String operator, String reason) {
        EmergencyResult result = new EmergencyResult();
        
        try {
            // 1. 执行黑屏
            deviceService.blackscreen(boardIp);
            result.setBlackscreenSuccess(true);
            
            // 2. 处置相关告警
            alarmService.handleAlarmsByBoardIp(boardIp, operator, reason);
            result.setAlarmHandled(true);
            
            // 3. 记录操作日志
            logOperation(boardIp, operator, reason);
            result.setLogRecorded(true);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
        }
        
        return result;
    }
}
```

**优势：**
- ✅ 简化客户端调用
- ✅ 降低服务间耦合
- ✅ 统一事务管理

---

## 14. 享元模式 (Flyweight Pattern)

### 14.1 应用场景

#### 场景1：设备类型元数据缓存 ⭐⭐⭐

```java
// 设备类型元数据享元
@Component
public class DeviceTypeMetadataFactory {
    private Map<String, DeviceTypeMetadata> metadataCache = new ConcurrentHashMap<>();
    
    public DeviceTypeMetadata getMetadata(String deviceType) {
        return metadataCache.computeIfAbsent(deviceType, this::loadMetadata);
    }
    
    private DeviceTypeMetadata loadMetadata(String deviceType) {
        // 从数据库或配置文件加载
        DeviceTypeMetadata metadata = new DeviceTypeMetadata();
        metadata.setType(deviceType);
        metadata.setLabel(getLabelFromConfig(deviceType));
        metadata.setIcon(getIconFromConfig(deviceType));
        metadata.setDefaultPort(getDefaultPort(deviceType));
        metadata.setRequiredFields(getRequiredFields(deviceType));
        return metadata;
    }
}

// 享元对象
@Data
public class DeviceTypeMetadata {
    private String type;
    private String label;
    private String icon;
    private Integer defaultPort;
    private List<String> requiredFields;
    private Map<String, Object> defaultConfig;
}
```

---

## 15. 实施建议与优先级

### 15.1 优先级矩阵

| 设计模式 | 应用场景 | 优先级 | 难度 | 收益 |
|---------|---------|-------|------|------|
| **策略模式** | 内容识别 | ⭐⭐⭐⭐⭐ | 中 | 高 |
| **策略模式** | 违规类型处理 | ⭐⭐⭐⭐ | 低 | 中 |
| **观察者模式** | 统一事件总线 | ⭐⭐⭐⭐⭐ | 中 | 高 |
| **模板方法模式** | 设备命令执行 | ⭐⭐⭐⭐ | 低 | 高 |
| **责任链模式** | 链路配置校验 | ⭐⭐⭐⭐⭐ | 中 | 高 |
| **状态模式** | 设备状态机 | ⭐⭐⭐⭐⭐ | 高 | 高 |
| **装饰器模式** | 告警推送增强 | ⭐⭐⭐⭐ | 中 | 中 |
| **适配器模式** | 第三方服务 | ⭐⭐⭐⭐ | 中 | 高 |
| **建造者模式** | 复杂对象构建 | ⭐⭐⭐ | 低 | 中 |
| **外观模式** | 统一服务接口 | ⭐⭐⭐⭐ | 低 | 高 |
| **命令模式** | 告警处理 | ⭐⭐⭐ | 中 | 中 |
| **代理模式** | Feign降级 | ⭐⭐⭐ | 中 | 中 |
| **享元模式** | 元数据缓存 | ⭐⭐⭐ | 低 | 低 |

### 15.2 分阶段实施计划

#### 第一阶段（1-2周）：快速收益
1. ✅ 策略模式 - 内容识别
2. ✅ 建造者模式 - 复杂对象构建
3. ✅ 模板方法模式 - 设备命令执行

#### 第二阶段（2-3周）：核心优化
1. ✅ 观察者模式 - 统一事件总线
2. ✅ 责任链模式 - 链路配置校验
3. ✅ 外观模式 - 统一服务接口

#### 第三阶段（3-4周）：深度重构
1. ✅ 状态模式 - 设备状态机
2. ✅ 适配器模式 - 第三方服务
3. ✅ 装饰器模式 - 告警推送增强

### 15.3 注意事项

1. **避免过度设计**：不是所有地方都需要设计模式，简单场景保持简单
2. **渐进式重构**：先在新功能中使用，再逐步重构旧代码
3. **充分测试**：重构前后保证单元测试覆盖
4. **文档更新**：及时更新设计文档和注释
5. **团队培训**：确保团队理解设计模式的意图和使用场景

### 15.4 收益评估

**代码质量提升：**
- 减少 if-else/switch 语句 60%+
- 提高代码复用率 40%+
- 降低圈复杂度 50%+

**维护成本降低：**
- 新增功能开发时间减少 30%
- Bug 修复时间减少 40%
- 代码审查时间减少 25%

**扩展性增强：**
- 符合开闭原则，新增类型无需修改现有代码
- 支持运行时动态切换策略
- 易于单元测试和Mock

---

## 附录：设计模式速查表

| 模式 | 意图 | 适用场景 | 项目应用 |
|-----|------|---------|---------|
| 策略模式 | 定义算法族，可互相替换 | 多个类仅行为不同 | 内容识别、违规处理 |
| 工厂模式 | 定义创建对象的接口 | 对象创建逻辑复杂 | 命令处理器工厂 |
| 观察者模式 | 一对多依赖，状态变化通知 | 事件驱动架构 | WebSocket推送 |
| 模板方法 | 定义算法骨架，子类实现细节 | 流程相同实现不同 | 命令执行、内容检测 |
| 装饰器 | 动态添加职责 | 功能组合复杂 | 告警推送增强 |
| 责任链 | 请求沿链传递直到处理 | 多级校验/审批 | 链路校验、内容审核 |
| 适配器 | 接口不兼容的系统协作 | 对接第三方服务 | AI服务、设备协议 |
| 建造者 | 分步骤构建复杂对象 | 对象参数多 | 设备、告警构建 |
| 状态模式 | 对象状态改变时行为改变 | 状态机 | 设备状态管理 |
| 命令模式 | 请求封装为对象 | 需要撤销/重做 | 设备命令、告警处置 |
| 外观模式 | 提供统一高层接口 | 子系统复杂 | 监控服务外观 |
| 代理模式 | 控制对象访问 | 远程调用、降级 | Feign降级代理 |
| 享元模式 | 共享细粒度对象 | 大量相似对象 | 设备类型元数据 |

---

**文档版本：** v1.0  
**创建日期：** 2026-05-21  
**作者：** AI架构师  
**审阅状态：** 待审阅
