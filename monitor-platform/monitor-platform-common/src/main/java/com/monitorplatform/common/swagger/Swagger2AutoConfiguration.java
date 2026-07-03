package com.monitorplatform.common.swagger;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


import java.util.ArrayList;
import java.util.List;
//import com.google.common.net.HttpHeaders;
//import static com.google.common.collect.Lists.newArrayList;

/**
 * 通用Swagger配置 使用方法注意："Authorization: Bearer
 * 4866934a-1fed-42a4-b695-f9591e80edaf",token需要有Bearer和空隔
 * 
 * @author swm
 * @date 2018-3-14
 **/
@Configuration
@EnableSwagger2
@EnableKnife4j
//@EnableSwagger2WebMvc

//@Profile("!prod") //@Profile:指定组件在哪个环境的情况下才能被注册到容器中，不指定，任何环境下都能注册这个组件  开发环境develop、测试环境test、生产环境prod
@ConditionalOnProperty(name = "isds.swagger2.enable", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({ Swagger2Properties.class })
public class Swagger2AutoConfiguration {

	@Autowired
	private Swagger2Properties swagger2Properties;
	
//	private final OpenApiExtensionResolver openApiExtensionResolver;
	
//	@Bean
//    public PathProvider pathProvider() {
//        return new DefaultPathProvider() {
//            @Override
//            public String getOperationPath(String operationPath) {
//                return super.getOperationPath(operationPath);
//            }
//        };
//    }
	
//	/**
//     * Swagger忽略的参数类型
//     */
//    private final Class[] ignoredParameterTypes = new Class[]{
//            ServletRequest.class,
//            ServletResponse.class,
//            HttpServletRequest.class,
//            HttpServletResponse.class,
//            HttpSession.class,
//            ApiIgnore.class,
//            Principal.class,
//            Map.class
//    };

	@Bean
	public Docket api() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2).useDefaultResponseMessages(false).apiInfo(groupApiInfo()).select()
				.apis(RequestHandlerSelectors.basePackage(swagger2Properties.getBasePackage()))
				.paths(PathSelectors.regex("^(?!auth).*$")).build().securitySchemes(securitySchemes())
				.securityContexts(securityContexts());
		if (isNotBlank(swagger2Properties.getPathMapping())) {
			docket.pathMapping(swagger2Properties.getPathMapping().trim());
		}
		return docket;
		
//		ApiSelectorBuilder apiSelectorBuilder = new Docket(DocumentationType.SWAGGER_2)
//                // 用来创建该API的基本信息，展示在文档的页面中（自定义展示的信息）
//                .apiInfo(groupApiInfo())
//                // 设置哪些接口暴露给Swagger展示
//                .select();
//        if (swagger2Properties.getBasePackage() == null) {
//            // 扫描所有有注解的api，用这种方式更灵活
//            apiSelectorBuilder.apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class));
//        } else {
//            // 扫描指定的包
//            apiSelectorBuilder.apis(RequestHandlerSelectors.basePackage(swagger2Properties.getBasePackage()));
//        }
//        return apiSelectorBuilder.paths(PathSelectors.any())
//                .build()
//                .enable(swagger2Properties.isEnable())
//                .securitySchemes(securitySchemes())
//                .securityContexts(securityContexts())
//                //.pathProvider(pathProvider())
//                .ignoredParameterTypes(ignoredParameterTypes)
//                .pathMapping("/").extensions(openApiExtensionResolver.buildSettingExtensions());
	}

	private ApiInfo groupApiInfo() {
		return new ApiInfoBuilder().title(swagger2Properties.getTitle())
				.description(swagger2Properties.getDescription())
				.termsOfServiceUrl(swagger2Properties.getTermsOfServiceUrl())
				.contact(new Contact(swagger2Properties.getContactName(), 
						swagger2Properties.getContactUrl(),
						swagger2Properties.getContactEmail()))
				.version(swagger2Properties.getVersion()).build();
	}

//	//在请求头部显示 Authorization
//	private List<ApiKey> securitySchemes() {
//		return newArrayList(
//				new ApiKey(HttpHeaders.AUTHORIZATION, HttpHeaders.AUTHORIZATION, ApiKeyVehicle.HEADER.getValue()));
//	}
//
//	//在请求头部显示 Authorization
//	private List<SecurityContext> securityContexts() {
//		return newArrayList(SecurityContext.builder().securityReferences(defaultAuth())
//				.forPaths(PathSelectors.regex("^(?!auth).*$")).build());
//	}
//
//	List<SecurityReference> defaultAuth() {
//		AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
//		AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
//		authorizationScopes[0] = authorizationScope;
//		return newArrayList(new SecurityReference("Authorization", authorizationScopes));
//	}
	
	private List<ApiKey> securitySchemes() {
        List<ApiKey> apiKeyList = new ArrayList<>();
        apiKeyList.add(new ApiKey("Authorization", "Authorization", "header"));
        //apiKeyList.add(new ApiKey("IOC-Auth", "IOC-Auth", "header"));
        return apiKeyList;
    }

    /**
     * swagger2 认证的安全上下文
     */
    private List<SecurityContext> securityContexts() {
        List<SecurityContext> securityContexts = new ArrayList<>();
        securityContexts.add(
                SecurityContext.builder()
                        .securityReferences(defaultAuth())
                        .forPaths(PathSelectors.regex("^(?!auth).*$"))
                        .build());
        return securityContexts;
    }

    List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        List<SecurityReference> securityReferences = new ArrayList<>();
        securityReferences.add(new SecurityReference("Authorization", authorizationScopes));
        return securityReferences;
    }
    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
