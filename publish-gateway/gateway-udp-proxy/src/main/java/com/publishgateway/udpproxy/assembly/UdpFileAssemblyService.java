package com.publishgateway.udpproxy.assembly;

/**
 * UDP 文件重组服务。
 *
 * 该接口只观察原始 UDP payload 并输出完整文件，不负责拦截、阻塞或转发数据。
 */
public interface UdpFileAssemblyService {

    /**
     * 接收一个原始 UDP payload，尝试按厂商协议重组完整文件。
     *
     * @param request 重组入参
     * @return 重组状态；实现不得向外抛出业务异常
     */
    FileAssemblyResult accept(FileAssemblyRequest request);
}
