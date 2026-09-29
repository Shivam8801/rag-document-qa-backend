package com.shivam.rag_document_qa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class DocumentController {
    @GetMapping
    public String healthCheck()
    {
        return "Application is Running!";
    }


}
