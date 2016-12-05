package com.kepler.zookeeper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;

import com.kepler.KeplerLocalException;
import com.kepler.config.PropertiesUtils;

/**
 * @author kim 2015年7月8日
 */
public class ZkFactory implements FactoryBean<ZkClient> {

	private static final int RETRY_TIMES = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".retry_times", Integer.MAX_VALUE);

	private static final int RETRY_INTERVAL = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".retry_interval", 20000);

	private static final int TIMEOUT_SESSION = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".timeout_session", 30000);

	private static final int TIMEOUT_CONNECT = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".timeout_connect", 120000);

	private static final String SCHEME = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".scheme", "digest");

	private static final String AUTH = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".auth", "");

	/**
	 * Using for kepler admin
	 */
	public static final String HOST = PropertiesUtils.get(ZkFactory.class.getName().toLowerCase() + ".host", "");

	private static final Log LOGGER = LogFactory.getLog(ZkFactory.class);

	private final ZkConnection connection = new ZkConnection();

	private final Watcher watcher = new ConnectionWatcher();

	private final ZkClient zoo = new ZkClient();

	private final String address;

	private final String scheme;

	private final String auth;

	public ZkFactory(String address, String scheme, String auth) {
		super();
		Assert.hasText(address, "Please setting the zookeeper address in [kepler.conf] or [system properties (-D)] ... ");
		this.address = address;
		this.scheme = scheme;
		this.auth = auth;
	}

	public ZkFactory(String address) {
		this(address, ZkFactory.SCHEME, ZkFactory.AUTH);
	}

	public ZkFactory() {
		this(ZkFactory.HOST, ZkFactory.SCHEME, ZkFactory.AUTH);
	}

	/**
	 * 创建ZooKeeper物理连接并绑定至当前使用客户端
	 * 
	 * @throws Exception
	 */
	public void init() throws Exception {
		this.connection.reset();
		this.zoo.bind(new ZooKeeper(this.address, ZkFactory.TIMEOUT_SESSION, this.watcher));
		this.zoo.zoo().addAuthInfo(this.scheme, this.auth.getBytes());
		// 堵塞直到激活
		this.connection.await();
	}

	@Override
	public ZkClient getObject() throws Exception {
		return this.zoo;
	}

	@Override
	public Class<?> getObjectType() {
		return ZkClient.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	/**
	 * 最大重试次数内重试
	 */
	public void reset(String operator) {
		for (int times = 0; times < ZkFactory.RETRY_TIMES; times++) {
			try {
				this.reset(operator, times);
				// 成功(无异常)则返回否则继续重试
				return;
			} catch (Throwable e) {
				ZkFactory.LOGGER.error(e.getMessage(), e);
			}
		}
	}

	private void reset(String operator, int times) throws Exception {
		Thread.sleep(ZkFactory.RETRY_INTERVAL);
		ZkFactory.LOGGER.info("ZooKeeper reset " + times + " times [operator=" + operator + "]");
		this.zoo.close();
		this.init();
		this.zoo.reset();
	}

	private class ConnectionWatcher implements Watcher {

		@Override
		public void process(WatchedEvent event) {
			switch (event.getState()) {
			case SyncConnected:
				ZkFactory.this.connection.activate();
				return;
			case Expired:
				ZkFactory.LOGGER.fatal("ZooKeeper Expired: " + event + " ...");
				ZkFactory.this.reset("Connection Watcher");
				return;
			case Disconnected:
				ZkFactory.LOGGER.fatal("ZooKeeper Disconnected: " + event + " ...");
				ZkFactory.this.reset("Connection Watcher");
				return;
			default:
				return;
			}
		}
	}

	private class ZkConnection {

		private long start;

		volatile private boolean valid;

		public void reset() {
			this.start = System.currentTimeMillis();
			this.valid = false;
		}

		public void await() throws Exception {
			if (!this.valid) {
				this.doWait();
			}
		}

		public void activate() {
			synchronized (this) {
				this.valid = true;
				this.notifyAll();
			}
		}

		private void doWait() throws Exception {
			synchronized (this) {
				while (!this.valid) {
					this.wait();
				}
			}
			this.timeout();
		}

		private void timeout() {
			if ((System.currentTimeMillis() - this.start) > ZkFactory.TIMEOUT_CONNECT) {
				throw new KeplerLocalException("ZooKeeper connect timeout ...");
			}
		}
	}
}
