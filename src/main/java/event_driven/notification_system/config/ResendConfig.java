//package event_driven.notification_system.config;
//
//import com.resend.Resend;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class ResendConfig {
//
//    @Value("${resend.api-key}")
//    private String resendApiKey;
//
//    @Bean
//    public Resend resendClient() {
//        return new Resend(resendApiKey);
//    }
//}