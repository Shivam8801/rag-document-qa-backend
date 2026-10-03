package com.shivam.rag_document_qa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI ragDocumentQaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RAG Document QA API")
                .version("1.0.0")
                .description("Upload PDF documents, manage conversations, and ask questions grounded in indexed content."));
    }
}
