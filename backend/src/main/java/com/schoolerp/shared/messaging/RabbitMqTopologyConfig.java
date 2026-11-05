package com.schoolerp.shared.messaging;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarative RabbitMQ topology for tenant-routed asynchronous events:
 * - Primary topic exchange {@code erp.events.topic}
 * - Dead-letter exchange {@code erp.events.dlx}
 * - Priority/domain queues ({@code erp.notifications.high}, {@code erp.notifications.bulk},
 *   {@code erp.documents.generate}, {@code erp.imports.process})
 * - Exponential backoff retry queues ({@code erp.retry.5s}, {@code erp.retry.30s}, {@code erp.retry.5m})
 * - Terminal dead-letter queue ({@code erp.dlq})
 */
@Configuration
public class RabbitMqTopologyConfig {

    public static final String EXCHANGE_EVENTS_TOPIC = "erp.events.topic";
    public static final String EXCHANGE_DLX = "erp.events.dlx";

    public static final String QUEUE_NOTIFICATIONS_HIGH = "erp.notifications.high";
    public static final String QUEUE_NOTIFICATIONS_BULK = "erp.notifications.bulk";
    public static final String QUEUE_DOCUMENTS_GENERATE = "erp.documents.generate";
    public static final String QUEUE_IMPORTS_PROCESS = "erp.imports.process";

    public static final String QUEUE_RETRY_5S = "erp.retry.5s";
    public static final String QUEUE_RETRY_30S = "erp.retry.30s";
    public static final String QUEUE_RETRY_5M = "erp.retry.5m";
    public static final String QUEUE_DLQ = "erp.dlq";

    public static final String ROUTING_RETRY_5S = "retry.5s";
    public static final String ROUTING_RETRY_30S = "retry.30s";
    public static final String ROUTING_RETRY_5M = "retry.5m";
    public static final String ROUTING_DLQ = "dlq";

    @Bean
    public TopicExchange erpEventsTopicExchange() {
        return new TopicExchange(EXCHANGE_EVENTS_TOPIC, true, false);
    }

    @Bean
    public DirectExchange erpEventsDlxExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue notificationsHighQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS_HIGH)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE_DLX,
                        "x-dead-letter-routing-key", ROUTING_RETRY_5S,
                        "x-max-priority", 10))
                .build();
    }

    @Bean
    public Queue notificationsBulkQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS_BULK)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE_DLX,
                        "x-dead-letter-routing-key", ROUTING_RETRY_5S))
                .build();
    }

    @Bean
    public Queue documentsGenerateQueue() {
        return QueueBuilder.durable(QUEUE_DOCUMENTS_GENERATE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE_DLX,
                        "x-dead-letter-routing-key", ROUTING_RETRY_5S))
                .build();
    }

    @Bean
    public Queue importsProcessQueue() {
        return QueueBuilder.durable(QUEUE_IMPORTS_PROCESS)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE_DLX,
                        "x-dead-letter-routing-key", ROUTING_RETRY_5S))
                .build();
    }

    @Bean
    public Queue retry5sQueue() {
        return QueueBuilder.durable(QUEUE_RETRY_5S)
                .withArguments(Map.of(
                        "x-message-ttl", 5_000,
                        "x-dead-letter-exchange", EXCHANGE_EVENTS_TOPIC))
                .build();
    }

    @Bean
    public Queue retry30sQueue() {
        return QueueBuilder.durable(QUEUE_RETRY_30S)
                .withArguments(Map.of(
                        "x-message-ttl", 30_000,
                        "x-dead-letter-exchange", EXCHANGE_EVENTS_TOPIC))
                .build();
    }

    @Bean
    public Queue retry5mQueue() {
        return QueueBuilder.durable(QUEUE_RETRY_5M)
                .withArguments(Map.of(
                        "x-message-ttl", 300_000,
                        "x-dead-letter-exchange", EXCHANGE_EVENTS_TOPIC))
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding bindNotificationsHighIam(Queue notificationsHighQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsHighQueue).to(erpEventsTopicExchange).with("iam.#");
    }

    @Bean
    public Binding bindNotificationsHighFees(Queue notificationsHighQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsHighQueue).to(erpEventsTopicExchange).with("fees.payment.#");
    }

    @Bean
    public Binding bindNotificationsHighUrgent(Queue notificationsHighQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsHighQueue).to(erpEventsTopicExchange).with("notification.high.#");
    }

    @Bean
    public Binding bindNotificationsBulkAttendance(Queue notificationsBulkQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsBulkQueue).to(erpEventsTopicExchange).with("attendance.#");
    }

    @Bean
    public Binding bindNotificationsBulkNotices(Queue notificationsBulkQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsBulkQueue).to(erpEventsTopicExchange).with("notice.#");
    }

    @Bean
    public Binding bindNotificationsBulkReminders(Queue notificationsBulkQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsBulkQueue).to(erpEventsTopicExchange).with("fees.reminder.#");
    }

    @Bean
    public Binding bindNotificationsBulkGeneral(Queue notificationsBulkQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(notificationsBulkQueue).to(erpEventsTopicExchange).with("notification.bulk.#");
    }

    @Bean
    public Binding bindDocumentsGenerate(Queue documentsGenerateQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(documentsGenerateQueue).to(erpEventsTopicExchange).with("document.#");
    }

    @Bean
    public Binding bindImportsProcess(Queue importsProcessQueue, TopicExchange erpEventsTopicExchange) {
        return BindingBuilder.bind(importsProcessQueue).to(erpEventsTopicExchange).with("import.#");
    }

    @Bean
    public Binding bindRetry5s(Queue retry5sQueue, DirectExchange erpEventsDlxExchange) {
        return BindingBuilder.bind(retry5sQueue).to(erpEventsDlxExchange).with(ROUTING_RETRY_5S);
    }

    @Bean
    public Binding bindRetry30s(Queue retry30sQueue, DirectExchange erpEventsDlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(erpEventsDlxExchange).with(ROUTING_RETRY_30S);
    }

    @Bean
    public Binding bindRetry5m(Queue retry5mQueue, DirectExchange erpEventsDlxExchange) {
        return BindingBuilder.bind(retry5mQueue).to(erpEventsDlxExchange).with(ROUTING_RETRY_5M);
    }

    @Bean
    public Binding bindDlq(Queue dlqQueue, DirectExchange erpEventsDlxExchange) {
        return BindingBuilder.bind(dlqQueue).to(erpEventsDlxExchange).with(ROUTING_DLQ);
    }
}

