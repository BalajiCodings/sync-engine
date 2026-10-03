package com.balaji.sync_engine.config;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HLCProvider {

    @Bean
    public HybridLogicalClock serverClock() {
        return new HybridLogicalClock("server");
    }
}