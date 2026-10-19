package com.wokcontrol.usuarios.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Habilita @CreatedDate / @LastModifiedDate en los documentos. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
