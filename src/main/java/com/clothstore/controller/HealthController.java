package com.clothstore.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/health")
public class HealthController {
    public String getHealth(){
        return "OK";
    }
}
