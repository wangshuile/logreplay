package io.github.yangziwen.logreplay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.system.ApplicationPidFileWriter;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import com.google.common.eventbus.AsyncEventBus;

@EnableCaching
@ServletComponentScan
@EnableTransactionManagement
@EnableWebSocketMessageBroker
@SpringBootApplication
public class LogreplayApplication extends SpringBootServletInitializer {

	@Bean("executor")
	public ThreadPoolTaskExecutor executor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(20);
		executor.setMaxPoolSize(150);
		executor.setQueueCapacity(1000);
		executor.setKeepAliveSeconds(30);
		return executor;
	}

	@Bean("eventBus")
	public AsyncEventBus eventBus(@Autowired ThreadPoolTaskExecutor executor) {
		return new AsyncEventBus(executor);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(LogreplayApplication.class);
	}

	public static void main(String[] args) {
		System.setProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow","|{}");
		SpringApplication application = new SpringApplication(LogreplayApplication.class);
		application.addListeners(new ApplicationPidFileWriter());
		application.run(args);
	}

}
