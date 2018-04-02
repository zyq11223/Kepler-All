package com.kepler.service.exported;

import java.util.Collection;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;

import com.kepler.KeplerLocalException;
import com.kepler.advised.AdvisedFinder;
import com.kepler.annotation.Autowired;
import com.kepler.annotation.Service;
import com.kepler.config.Profile;
import com.kepler.org.apache.commons.lang.StringUtils;
import com.kepler.service.Exported;
import com.kepler.service.ExportedGetter;

/**
 * @Service scan
 * 
 * @author kim 2015年8月19日
 */
public class ExportedDiscovery implements BeanPostProcessor, ApplicationContextAware {

	private static final Log LOGGER = LogFactory.getLog(ExportedDiscovery.class);

	private static final ExportedGetter NONE = new NoneActual();

	private final Exported exported;

	private final Profile profile;

	private ApplicationContext context;

	private ExportedGetter get;

	public ExportedDiscovery(Profile profile, Exported exported) {
		super();
		this.get = ExportedDiscovery.NONE;
		this.exported = exported;
		this.profile = profile;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		ExportedGetter actual = (this.context = context).getBean(ExportedGetter.class);
		if (actual != null) {
			ExportedDiscovery.LOGGER.info("[exported-get][class=" + actual.getClass() + "]");
			this.get = actual;
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		Autowired autowired = AdvisedFinder.get(bean, Autowired.class);
		// 标记@Autowired表示自动发布
		if (autowired != null) {
			this.exported(this.get.get(bean, beanName, this.context), bean, autowired.catalog(), autowired.profile(), autowired.version());
		}
		return bean;
	}

	// 如果@Autowire定义了Version则覆盖@Service
	private void exported(Object proxy, Object bean, String catalog, String profile, String version[]) {
		// 迭代所有定义@Service的接口
		for (Class<?> each : this.services(new HashSet<Class<?>>(), bean.getClass())) {
			try {
				Service exported = AnnotationUtils.findAnnotation(each, Service.class);
				// Autowired.Catalog覆盖Service.Catalog
				String catalog4exported = StringUtils.isEmpty(catalog) ? exported.catalog() : catalog;
				// Version.length=1并且Version[0]为空则表示使用没有指定Autowired.Version
				String[] version4exported = (version.length == 1 && StringUtils.isEmpty(version[0])) ? new String[] { exported.version() } : version;
				this.exported(each, proxy, profile, catalog4exported, version4exported);
			} catch (Exception e) {
				throw new KeplerLocalException(e);
			}
		}
	}

	/**
	 * @param clazz 实际发布Clazz
	 * @param bean 
	 * @param profile
	 * @param catalog
	 * @param versions 需要发布的版本集合
	 * @throws Exception
	 */
	private void exported(Class<?> clazz, Object bean, String profile, String catalog, String... versions) throws Exception {
		for (String version : versions) {
			com.kepler.service.Service service = new com.kepler.service.Service(clazz.getName(), version, catalog);
			this.profile.add(service, profile);
			this.exported.export(service, bean);
		}
	}

	private Collection<Class<?>> services(Collection<Class<?>> exported, Class<?> clazz) {
		this.recursive(exported, clazz);
		this.interfaces(exported, clazz);
		return exported;
	}

	/**
	 * 向上查找父类
	 * 
	 * @param exported
	 * @param clazz
	 */
	private void recursive(Collection<Class<?>> exported, Class<?> clazz) {
		if (clazz.getSuperclass() != null) {
			this.services(exported, clazz.getSuperclass());
		}
	}

	/**
	 * 获取所有标记@Service的接口
	 * 
	 * @param exported
	 * @param clazz
	 */
	private void interfaces(Collection<Class<?>> exported, Class<?> clazz) {
		for (Class<?> each : clazz.getInterfaces()) {
			if (AnnotationUtils.findAnnotation(each, Service.class) != null) {
				exported.add(each);
			}
		}
	}

	private static class NoneActual implements ExportedGetter {

		@Override
		public Object get(Object bean, Object name, ApplicationContext context) {
			return bean;
		}
	}
}
