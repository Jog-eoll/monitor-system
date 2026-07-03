package com.monitorplatform.common.swagger;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author LIQIU
 * @date 2018-4-3
 **/
@Data
@ConfigurationProperties(prefix = "isds.swagger2")
public class Swagger2Properties {

	/**
	 * 是否启用Swagger
	 */
	private boolean enable;
    private String basePackage;
    private String pathMapping;
    private String title;
    private String description;
    private String version;
    private String apiName;
    private String apiKeyName;
    private String termsOfServiceUrl;
    
    private String contactName;
	private String contactUrl;	
	private String contactEmail;

}
