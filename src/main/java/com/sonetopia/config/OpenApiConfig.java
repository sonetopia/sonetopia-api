package com.sonetopia.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;


@Configuration 
@OpenAPIDefinition(
  info = @Info(
    title = "Sonetopia API",
    version = "v1",
    description = "REST API for the Sonetopia audio platform.",
    contact = @Contact(
      name = "Sonetopia"
    )
  )
)
public class OpenApiConfig {

}
