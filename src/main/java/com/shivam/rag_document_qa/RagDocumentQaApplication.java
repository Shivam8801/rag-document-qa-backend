package com.shivam.rag_document_qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagDocumentQaApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagDocumentQaApplication.class, args);
	}

}
