package com.kepler.admin.transfer.impl;

import com.kepler.ack.Status;
import com.kepler.admin.transfer.Transfer;
import com.kepler.config.PropertiesUtils;
import com.kepler.host.Host;
import com.kepler.org.apache.commons.lang.builder.ToStringBuilder;

/**
 * @author KimShen
 *
 */
public class DefaultTransfer implements Transfer {

	/**
	 * 冻结状态阀值(127次默认)
	 */
	private static final int FREEZE = PropertiesUtils.get(DefaultTransfer.class.getName().toLowerCase() + ".freeze", Byte.MAX_VALUE);

	private static final long serialVersionUID = 1L;

	private final Host target;

	private final Host local;

	volatile private long rtt;

	volatile private long total;

	volatile private long freeze;

	volatile private long timeout;

	volatile private long exception;

	/**
	 * 实际首次收集时间
	 */
	volatile private long timestamp;

	public DefaultTransfer(Host local, Host target) {
		super();
		this.local = local;
		this.target = target;
		this.timestamp = System.currentTimeMillis();
	}

	/**
	 * 如果连续N次没有请求量则进入冻结状态,用于主机永久性离线后的Transfer清理
	 * 
	 * @return
	 */
	public boolean freezed() {
		if (this.total == 0) {
			return (this.freeze++) > DefaultTransfer.FREEZE;
		} else {
			// 任一一次请求有效则重置计数
			this.freeze = 0;
			return false;
		}
	}

	@Override
	public Host local() {
		return this.local;
	}

	@Override
	public Host target() {
		return this.target;
	}

	public long rtt() {
		return this.rtt;
	}

	public long total() {
		return this.total;
	}

	public long timeout() {
		return this.timeout;
	}

	public long exception() {
		return this.exception;
	}

	public long timestamp() {
		return this.timestamp;
	}

	public boolean actived() {
		// 没有任何请求则标记为非激活
		return this.total != 0;
	}

	public DefaultTransfer touch() {
		this.total++;
		return this;
	}

	public DefaultTransfer rtt(long rtt) {
		this.rtt += rtt;
		return this;
	}

	public DefaultTransfer timeout(Status status) {
		if (status.equals(Status.TIMEOUT)) {
			this.timeout++;
		}
		return this;
	}

	public DefaultTransfer exception(Status status) {
		if (status.equals(Status.EXCEPTION)) {
			this.exception++;
		}
		return this;
	}

	public void reset() {
		this.rtt = 0;
		this.total = 0;
		this.timeout = 0;
		this.exception = 0;
		// 更新时间
		this.timestamp = System.currentTimeMillis();
	}

	public String toString() {
		return ToStringBuilder.reflectionToString(this);
	}
}