package io.datapulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.datapulse")
public class DatapulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatapulseApplication.class, args);
    }
}
