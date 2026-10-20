package com.wokcontrol.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Llena @CreatedDate y @LastModifiedDate en los servicios con base de datos propia. reportes-service
 * no tiene base de datos (no define spring.mongodb.database), así que allí no se activa.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.mongodb.database")
@EnableMongoAuditing
public class MongoAuditoriaConfig {
}
