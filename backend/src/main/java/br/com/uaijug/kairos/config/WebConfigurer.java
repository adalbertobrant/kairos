package br.com.uaijug.kairos.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.CharacterEncodingFilter;

import br.com.uaijug.kairos.web.filter.CachingHttpHeadersFilter;
import br.com.uaijug.kairos.web.filter.StaticResourcesProductionFilter;
import br.com.uaijug.kairos.web.filter.gzip.GZipServletFilter;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlet.InstrumentedFilter;
import com.codahale.metrics.servlets.MetricsServlet;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Configuration
@AutoConfigureAfter(CacheConfiguration.class)
public class WebConfigurer implements ServletContextInitializer {

	private final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

	@Inject
	private Environment env;

	@Inject
	private MetricRegistry metricRegistry;

	@Override
	public void onStartup(ServletContext servletContext)
			throws ServletException {
		this.log.info("Web application configuration, using profiles: {}",
				Arrays.toString(this.env.getActiveProfiles()));

		EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST,
				DispatcherType.FORWARD, DispatcherType.ASYNC);

		this.initUtf8pFilter(servletContext, disps);
		this.initMetrics(servletContext, disps);
		if (this.env.acceptsProfiles(Constants.SPRING_PROFILE_PRODUCTION)) {
			this.initCachingHttpHeadersFilter(servletContext, disps);
			this.initStaticResourcesProductionFilter(servletContext, disps);
		}
		this.initGzipFilter(servletContext, disps);
		if (this.env.acceptsProfiles(Constants.SPRING_PROFILE_DEVELOPMENT)) {
			this.initH2Console(servletContext);
		}

		this.log.info("Web application fully configured");
	}

	private void initUtf8pFilter(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {
		this.log.debug("Registering UTF-8 Filter");
		FilterRegistration.Dynamic utf8Filter = servletContext.addFilter(
				"characterEncodingFilter", new CharacterEncodingFilter());
		Map<String, String> parameters = new HashMap<>();
		parameters.put("encoding", "UTF-8");
		parameters.put("forceEncoding", "true");
		utf8Filter.setInitParameters(parameters);

		utf8Filter.addMappingForUrlPatterns(disps, true, "/*");
	}

	/**
	 * Initializes the GZip filter.
	 */
	private void initGzipFilter(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {
		this.log.debug("Registering GZip Filter");
		FilterRegistration.Dynamic compressingFilter = servletContext
				.addFilter("gzipFilter", new GZipServletFilter());
		Map<String, String> parameters = new HashMap<>();
		compressingFilter.setInitParameters(parameters);
		compressingFilter.addMappingForUrlPatterns(disps, true, "*.css");
		compressingFilter.addMappingForUrlPatterns(disps, true, "*.json");
		compressingFilter.addMappingForUrlPatterns(disps, true, "*.html");
		compressingFilter.addMappingForUrlPatterns(disps, true, "*.js");
		compressingFilter.addMappingForUrlPatterns(disps, true, "/app/rest/*");
		compressingFilter.addMappingForUrlPatterns(disps, true, "/metrics/*");
		compressingFilter.setAsyncSupported(true);
	}

	/**
	 * Initializes the static resources production Filter.
	 */
	private void initStaticResourcesProductionFilter(
			ServletContext servletContext, EnumSet<DispatcherType> disps) {

		this.log.debug("Registering static resources production Filter");
		FilterRegistration.Dynamic staticResourcesProductionFilter = servletContext
				.addFilter("staticResourcesProductionFilter",
						new StaticResourcesProductionFilter());

		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/index.html");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/images/*");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/fonts/*");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/scripts/*");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/styles/*");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true,
				"/views/*");
		staticResourcesProductionFilter.setAsyncSupported(true);
	}

	/**
	 * Initializes the cachig HTTP Headers Filter.
	 */
	private void initCachingHttpHeadersFilter(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {
		this.log.debug("Registering Cachig HTTP Headers Filter");
		FilterRegistration.Dynamic cachingHttpHeadersFilter = servletContext
				.addFilter("cachingHttpHeadersFilter",
						new CachingHttpHeadersFilter());

		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true,
				"/images/*");
		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true,
				"/fonts/*");
		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true,
				"/scripts/*");
		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true,
				"/styles/*");
		cachingHttpHeadersFilter.setAsyncSupported(true);
	}

	/**
	 * Initializes Metrics.
	 */
	private void initMetrics(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {
		this.log.debug("Initializing Metrics registries");
		servletContext.setAttribute(InstrumentedFilter.REGISTRY_ATTRIBUTE,
				this.metricRegistry);
		servletContext.setAttribute(MetricsServlet.METRICS_REGISTRY,
				this.metricRegistry);

		this.log.debug("Registering Metrics Filter");
		FilterRegistration.Dynamic metricsFilter = servletContext.addFilter(
				"webappMetricsFilter", new InstrumentedFilter());

		metricsFilter.addMappingForUrlPatterns(disps, true, "/*");
		metricsFilter.setAsyncSupported(true);

		this.log.debug("Registering Metrics Servlet");
		ServletRegistration.Dynamic metricsAdminServlet = servletContext
				.addServlet("metricsServlet", new MetricsServlet());

		metricsAdminServlet.addMapping("/metrics/metrics/*");
		metricsAdminServlet.setAsyncSupported(true);
		metricsAdminServlet.setLoadOnStartup(2);
	}

	/**
	 * Initializes H2 console
	 */
	private void initH2Console(ServletContext servletContext) {
		this.log.debug("Initialize H2 console");
		ServletRegistration.Dynamic h2ConsoleServlet = servletContext
				.addServlet("H2Console", new org.h2.server.web.WebServlet());
		h2ConsoleServlet.addMapping("/console/*");
		h2ConsoleServlet.setLoadOnStartup(1);
	}
}