package event_driven.notification_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "event_driven.notification_system.repository")
public class NotificationSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationSystemApplication.class, args);
        }

}
