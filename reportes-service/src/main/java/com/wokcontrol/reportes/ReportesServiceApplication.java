package com.wokcontrol.reportes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.wokcontrol.common.mongo.SecuenciaService;

/**
 * Panel de control y reportes. No tiene base de datos: agrega datos de los demás servicios vía REST
 * (patrón de composición de API), por eso se excluye la configuración de MongoDB.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, DataMongoAutoConfiguration.class,
        DataMongoRepositoriesAutoConfiguration.class })
@ComponentScan(basePackages = { "com.wokcontrol.reportes", "com.wokcontrol.common" },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecuenciaService.class))
public class ReportesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportesServiceApplication.class, args);
    }
}
