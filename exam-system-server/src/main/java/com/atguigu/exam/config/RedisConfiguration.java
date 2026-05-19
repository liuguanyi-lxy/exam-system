package com.atguigu.exam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * projectName: day23_exam-system-server
 *
 * description: redis的配置类，进行redis的序列化器定制
 */
@Configuration
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory factory) {
/**
 * 创建并配置RedisTemplate实例
 * @return 配置好的RedisTemplate实例
 */
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate<>();
// 设置Redis连接工厂
        redisTemplate.setConnectionFactory(factory);
// 创建UTF-8编码的字符串序列化器
        StringRedisSerializer stringRedisSerializer = StringRedisSerializer.UTF_8;
// 创建Jackson2 JSON格式的序列化器
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
// 设置key的序列化方式为字符串
        redisTemplate.setKeySerializer(stringRedisSerializer);
// 设置hash key的序列化方式为字符串
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
// 设置value的序列化方式为JSON
        redisTemplate.setValueSerializer(jsonRedisSerializer);
// 设置hash value的序列化方式为JSON
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);
// 返回配置好的RedisTemplate实例
        return redisTemplate;
    }
}
