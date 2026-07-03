package com.monitorplatform.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 配置redistemplate序列化
 * 
 * @author PC2020JYY
 *
 */
@Configuration
@EnableCaching
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig extends CachingConfigurerSupport {

	/**
	 * 选择redis作为默认缓存工具 cacheManager只针对注解缓存有效,只使用redisTemplate不需要配置
	 * springboot2.x中，RedisCacheManager已经没有了单参数的构造方法 1.x中通过参数redisTemplate配置的方式不可行
	 */
	@Bean
	public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
		Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
		// 解决查询缓存转换异常的问题
		ObjectMapper om = new ObjectMapper();
		om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		om.activateDefaultTyping(
			LaissezFaireSubTypeValidator.instance,
			ObjectMapper.DefaultTyping.NON_FINAL
		);
		jackson2JsonRedisSerializer.setObjectMapper(om);
		RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofHours(1)) // 设置缓存有效期一小时
				.disableCachingNullValues()
				// .computePrefixWith(cacheName ->
				// "ioc".concat(":").concat(cacheName).concat(":"))//此处定义了cache key的前缀,
				// 避免公司不同项目之间的key名称冲突.
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer));

		return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(cacheConfiguration).build();
	}



	/**
	 * retemplate相关配置
	 *
	 * @param factory
	 * @return
	 */
	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

		RedisTemplate<String, Object> template = new RedisTemplate<>();
		// 配置连接工厂
		template.setConnectionFactory(factory);

		// 使用Jackson2JsonRedisSerializer来序列化和反序列化redis的value值（默认使用JDK的序列化方式）
		Jackson2JsonRedisSerializer jacksonSeial = new Jackson2JsonRedisSerializer(Object.class);

		ObjectMapper om = new ObjectMapper();
		// 指定要序列化的域，field,get和set,以及修饰符范围，ANY是都有包括private和public
		om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		// 指定序列化输入的类型，类必须是非final修饰的，final修饰的类，比如String,Integer等会跑出异常
		om.activateDefaultTyping(
			LaissezFaireSubTypeValidator.instance,
			ObjectMapper.DefaultTyping.NON_FINAL
		);
		jacksonSeial.setObjectMapper(om);

		// 值采用json序列化
		template.setValueSerializer(jacksonSeial);
		// 使用StringRedisSerializer来序列化和反序列化redis的key值
		template.setKeySerializer(new StringRedisSerializer());

		// 设置hash key 和value序列化模式
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(jacksonSeial);
		template.afterPropertiesSet();

		return template;
	}
/*	*//**
	 * <p>
	 * SpringBoot配置redis作为默认缓存工具, cacheManager只针对注解缓存有效,只使用redisTemplate不需要配置
	 * </p>
	 * <p>
	 * SpringBoot 2.0 以上版本的配置
	 * </p>
	 *//*
	@Bean
	public CacheManager cacheManager(RedisTemplate<String, Object> redisTemplate) {
		RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
				// 设置key为String
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getStringSerializer()))
				// 设置value 为自动转Json的Object
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getValueSerializer()))
				// 不缓存null
				.disableCachingNullValues()
				// 缓存数据保存1小时
				.entryTtl(Duration.ofHours(1));

		RedisCacheManager redisCacheManager = RedisCacheManagerBuilder
				// Redis 连接工厂
				.fromConnectionFactory(redisTemplate.getConnectionFactory())
				// 缓存配置
				.cacheDefaults(defaultCacheConfiguration)
				// 配置同步修改或删除 put/evict
				.transactionAware().build();
		return redisCacheManager;
	}*/



	/**
	 * 对hash类型的数据操作
	 *
	 * @param redisTemplate
	 * @return
	 */
	@Bean
	public HashOperations<String, String, Object> hashOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForHash();
	}

	/**
	 * 对redis字符串类型数据操作
	 *
	 * @param redisTemplate
	 * @return
	 */
	@Bean
	public ValueOperations<String, Object> valueOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForValue();
	}

	/**
	 * 对链表类型的数据操作
	 *
	 * @param redisTemplate
	 * @return
	 */
	@Bean
	public ListOperations<String, Object> listOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForList();
	}

	/**
	 * 对无序集合类型的数据操作
	 *
	 * @param redisTemplate
	 * @return
	 */
	@Bean
	public SetOperations<String, Object> setOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForSet();
	}

	/**
	 * 对有序集合类型的数据操作
	 *
	 * @param redisTemplate
	 * @return
	 */
	@Bean
	public ZSetOperations<String, Object> zSetOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForZSet();
	}

}
