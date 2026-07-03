package com.monitorplatform.device.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令处理器工厂
 */
@Component
public class CommandHandlerFactory {
    private final Map<String, CommandHandler> handlerMap = new HashMap<>();

    @Autowired
    private List<CommandHandler> commandHandlers;

    @PostConstruct
    public void init() {
        // 初始化处理器映射
        for (CommandHandler handler : commandHandlers) {
            handlerMap.put(handler.getCommandType(), handler);
        }
    }

    /**
     * 根据命令类型获取处理器
     * @param commandType 命令类型
     * @return 命令处理器
     */
    public CommandHandler getHandler(String commandType) {
        return handlerMap.get(commandType);
    }
}