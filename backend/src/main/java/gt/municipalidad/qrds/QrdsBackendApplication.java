package gt.municipalidad.qrds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class QrdsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrdsBackendApplication.class, args);
    }
}
