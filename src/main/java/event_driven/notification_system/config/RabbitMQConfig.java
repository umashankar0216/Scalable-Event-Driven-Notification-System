package event_driven.notification_system.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "user_events_exchange";
    public static final String EMAIL_QUEUE = "email_queue";
    public static final String SMS_QUEUE = "sms_queue";
    public static final String IN_APP_QUEUE = "in_app_queue";
    public static final String DLX_EXCHANGE = "notification_dlx";
    public static final String DLQ_QUEUE = "notification_dlq";

    // 1. Topic Exchange
    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // 2. Dead-Letter Exchange & Dead-Letter Queue (Shared DLQ for all notification failures)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("notification_dlq_key");
    }

    // 3. Email Queue with DLQ arguments
    @Bean
    public Queue emailQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "notification_dlq_key");
        return QueueBuilder.durable(EMAIL_QUEUE).withArguments(args).build();
    }

    // 4. SMS Queue with DLQ arguments
    @Bean
    public Queue smsQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "notification_dlq_key");
        return QueueBuilder.durable(SMS_QUEUE).withArguments(args).build();
    }

    // 5. In-App Queue
    @Bean
    public Queue inAppQueue() {
        return QueueBuilder.durable(IN_APP_QUEUE).build();
    }

    // 6. Bindings
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(userEventsExchange()).with("notification.email.#");
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue()).to(userEventsExchange()).with("notification.sms.#");
    }

    @Bean
    public Binding inAppBinding() {
        return BindingBuilder.bind(inAppQueue()).to(userEventsExchange()).with("notification.in_app.#");
    }

    // 7. Message Converter for JSON Serialization/Deserialization
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}