package com.carepilot.knowledge.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * 知识库解析的 MQ 拓扑：主队列 + 死信队列 + 重试策略。
 * 解析失败会重试 3 次（1s/2s/4s），仍失败进死信，由补偿任务落库。
 */
@Configuration
public class KbRabbitConfig {

    public static final String ROUTING_KEY = "kb.ingest";
    public static final String DLQ_ROUTING_KEY = "kb.ingest.dead";
    public static final String LISTENER_FACTORY = "kbListenerContainerFactory";

    @Value("${carepilot.rag.mq.exchange:carepilot.kb.exchange}")
    private String exchangeName;

    @Value("${carepilot.rag.mq.queue:carepilot.kb.ingest.queue}")
    private String queueName;

    @Value("${carepilot.rag.mq.dlx:carepilot.kb.ingest.dlx}")
    private String dlxName;

    @Value("${carepilot.rag.mq.dlq:carepilot.kb.ingest.dlq}")
    private String dlqName;

    /** 主交换机 */
    @Bean
    public DirectExchange kbExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /** 解析任务队列，绑定死信交换机 */
    @Bean
    public Queue kbIngestQueue() {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    /** 队列与交换机绑定 */
    @Bean
    public Binding kbBinding(Queue kbIngestQueue, DirectExchange kbExchange) {
        return BindingBuilder.bind(kbIngestQueue).to(kbExchange).with(ROUTING_KEY);
    }

    /** 死信交换机 */
    @Bean
    public DirectExchange kbDeadLetterExchange() {
        return new DirectExchange(dlxName, true, false);
    }

    /** 死信队列：重试耗尽的消息落这里，便于排查和补偿 */
    @Bean
    public Queue kbDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    /** 死信绑定 */
    @Bean
    public Binding kbDeadLetterBinding(Queue kbDeadLetterQueue, DirectExchange kbDeadLetterExchange) {
        return BindingBuilder.bind(kbDeadLetterQueue).to(kbDeadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    /** 重试拦截器：指数退避，超过次数不再重投 */
    @Bean
    public RetryOperationsInterceptor kbRetryInterceptor(MessageRecoverer kbMessageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 8000)
                .recoverer(kbMessageRecoverer)
                .build();
    }

    /** 消费者容器工厂，带上重试和 JSON 转换 */
    @Bean(name = LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory kbListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor kbRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(kbRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    /** 消息体用 JSON 序列化，避免 Java 序列化带来的类耦合 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}