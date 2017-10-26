package com.kepler.header.impl;

import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

import com.kepler.config.PropertiesUtils;
import com.kepler.header.Headers;
import com.kepler.org.apache.commons.lang.reflect.MethodUtils;
import com.kepler.trace.Trace;

/**
 * Trace上下文
 * 
 * @author KimShen
 *
 */
public class TraceContext {

	/**
	 * 是否开启Log4j.MDC
	 */
	private static final boolean MDC = PropertiesUtils.get(TraceContext.class.getName().toLowerCase() + ".log4j_mdc", false);

	private static final Log LOGGER = LogFactory.getLog(TraceContext.class);

	/**
	 * Log4j.MDC
	 */
	private static Class<?> CLASS;

	static {
		// 加载Log4j.MDC
		if (TraceContext.MDC) {
			try {
				TraceContext.CLASS = Class.forName("org.apache.log4j.MDC");
			} catch (Exception e) {
				TraceContext.LOGGER.warn(e.getMessage(), e);
			}
		}
	}

	/**
	 * 创建Trace
	 * 
	 * @param headers 当前上下文
	 * @return 创建后的Trace
	 */
	private static String trace(String trace) {
		Headers headers = TraceContext.getHeaders();
		headers.put(Trace.TRACE_COVER, trace);
		headers.put(Trace.TRACE_COVER + "_orig", trace);
		try {
			if (TraceContext.CLASS != null) {
				MethodUtils.invokeStaticMethod(TraceContext.CLASS, "put", new Object[] { Trace.TRACE, trace });
			}
		} catch (Exception e) {
			TraceContext.LOGGER.error(e.getMessage(), e);
		}
		return trace;
	}

	/**
	 * 尝试从上下文获取Headers
	 * 
	 * @return
	 */
	private static Headers getHeaders() {
		// 如果作为服务并且上游系统未开启Header则Header可能为Null
		Headers headers = ThreadHeaders.HEADERS.get();
		if (headers == null) {
			ThreadHeaders.HEADERS.set(headers = new LazyHeaders());
		}
		return headers;
	}

	public static String getSpan() {
		Headers headers = TraceContext.getHeaders();
		String span_curr = headers.get(Trace.SPAN);
		String span_orig = headers.get(Trace.SPAN + "_orig");
		// 如果Span-Curr不为空则返回果Span-Curr
		return !StringUtils.isEmpty(span_curr) ? span_curr : span_orig;
	}

	public static String getParent() {
		Headers headers = TraceContext.getHeaders();
		String parent_curr = headers.get(Trace.SPAN_PARENT);
		String parent_orig = headers.get(Trace.SPAN_PARENT + "_orig");
		// 如果Parent-Curr不为空则返回果Parent-Curr
		return !StringUtils.isEmpty(parent_curr) ? parent_curr : parent_orig;
	}

	public static String getTrace() {
		Headers headers = TraceContext.getHeaders();
		String trace_curr = headers.get(Trace.TRACE_COVER);
		String trace_orig = headers.get(Trace.TRACE_COVER + "_orig");
		// 如果Trace-Curr不为空则返回Trace-Curr
		return !StringUtils.isEmpty(trace_curr) ? trace_curr : trace_orig;
	}

	public static String getTraceOnCreate() {
		return TraceContext.getTraceOnCreate(null);
	}

	public static String getTraceOnCreate(String trace) {
		String trace_selected = TraceContext.getTrace();
		// 已存在Trace则返回
		if (!StringUtils.isEmpty(trace_selected)) {
			return trace_selected;
		}
		// 不存在Trace则使用指定Trace或UUID
		return TraceContext.trace(!StringUtils.isEmpty(trace) ? trace : UUID.randomUUID().toString());
	}

	/**
	 * 释放上下文相关Trace(将导致之后的调用Trace中断)
	 * 
	 * @return
	 */
	public static void release() {
		// 如果从服务端传递的Header并且未开启则可能为Null
		Headers headers = ThreadHeaders.HEADERS.get();
		if (headers != null) {
			headers.delete(Trace.TRACE_COVER);
		}
	}
}
