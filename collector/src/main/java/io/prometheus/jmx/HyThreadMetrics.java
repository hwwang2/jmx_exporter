package io.prometheus.jmx;

import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** 参考 io.prometheus.metrics.instrumentation.jvm.JvmThreadsMetrics 实现 */
public class HyThreadMetrics {
    private final PrometheusProperties config;
    private final ThreadMXBean threadBean;

    private static final String HY_THREAD_STATE = "hy_thread_state";

    private HyThreadMetrics(ThreadMXBean threadBean, PrometheusProperties config) {
        this.config = config;
        this.threadBean = threadBean;
    }

    private void register(PrometheusRegistry registry) {
        GaugeWithCallback.builder(config)
                .name(HY_THREAD_STATE)
                .labelNames("prefix", "state")
                .help("thread state by prefix group")
                .callback(
                        callback -> {
                            Map<String, Map<Thread.State, Integer>> threadStateCounts =
                                    getThreadStateCountMap(threadBean);
                            for (Map.Entry<String, Map<Thread.State, Integer>> entry :
                                    threadStateCounts.entrySet()) {
                                for (Map.Entry<Thread.State, Integer> entry2 :
                                        entry.getValue().entrySet()) {
                                    callback.call(
                                            entry2.getValue(),
                                            entry.getKey(),
                                            entry2.getKey().name());
                                }
                            }
                        })
                .register(registry);
    }

    private Map<String, Map<Thread.State, Integer>> getThreadStateCountMap(
            ThreadMXBean threadBean) {
        long[] threadIds = threadBean.getAllThreadIds();

        // Code to remove any thread id values <= 0
        int writePos = 0;
        for (int i = 0; i < threadIds.length; i++) {
            if (threadIds[i] > 0) {
                threadIds[writePos++] = threadIds[i];
            }
        }

        threadIds = Arrays.copyOf(threadIds, writePos);

        // Get thread information without computing any stack traces
        ThreadInfo[] allThreads = threadBean.getThreadInfo(threadIds, 0);

        Map<String, Map<Thread.State, Integer>> threadStates = new HashMap<>();
        for (ThreadInfo x : allThreads) {
            String name = x.getThreadName();
            String prefix = name;
            if (name.indexOf("-") > 0) {
                prefix = name.substring(0, name.lastIndexOf("-"));
            }
            threadStates.computeIfAbsent(prefix, k -> new HashMap<>());
            Thread.State state = x.getThreadState();
            threadStates.get(prefix).merge(state, 1, Integer::sum);
        }
        return threadStates;
    }

    public static Builder builder() {
        return new Builder(PrometheusProperties.get());
    }

    public static Builder builder(PrometheusProperties config) {
        return new Builder(config);
    }

    public static class Builder {

        private final PrometheusProperties config;
        private ThreadMXBean threadBean;

        private Builder(PrometheusProperties config) {
            this.config = config;
        }

        /** Package private. For testing only. */
        Builder threadBean(ThreadMXBean threadBean) {
            this.threadBean = threadBean;
            return this;
        }

        public void register() {
            register(PrometheusRegistry.defaultRegistry);
        }

        public void register(PrometheusRegistry registry) {
            ThreadMXBean threadBean =
                    this.threadBean != null ? this.threadBean : ManagementFactory.getThreadMXBean();
            new HyThreadMetrics(threadBean, config).register(registry);
        }
    }
}
