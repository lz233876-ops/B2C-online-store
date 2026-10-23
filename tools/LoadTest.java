import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 秒杀抢购接口压测器（纯 JDK，无第三方依赖）。
 * <p>
 * <b>为什么不用 JMeter：</b>本机下载 JMeter（85MB）的实测速度只有约 43KB/s，
 * 需要 30 分钟以上，而压测要测的是「接口在并发下的真实表现」，
 * 与用哪个工具无关。这个类只依赖 JDK 自带的 {@link HttpClient}，
 * 用 JDK 17 的<b>单文件源码启动</b>就能跑：{@code java LoadTest.java ...}
 * <p>
 * <b>它测什么（和 JMeter 的聚合报告口径一致）：</b>
 * <ul>
 *   <li>总请求数、成功/业务失败/网络失败 数量与错误率</li>
 *   <li>总耗时、QPS（吞吐 = 请求数 / 墙钟时间）</li>
 *   <li>响应时间分位数：P50 / P90 / P95 / P99 / P99.9 / MAX</li>
 * </ul>
 * 分位数用<b>全量延迟数组排序</b>计算（不是近似算法），结果精确。
 * <p>
 * <b>用法：</b>
 * <pre>
 *   java LoadTest.java --url http://127.0.0.1:8080/api/app/seckill/grab/90011 \
 *        --tokens tokens.txt --concurrency 200
 * </pre>
 * {@code tokens.txt} 每行一个 JWT；每个 token 只打一次请求
 * （秒杀是「一人一单」，一个用户重复打会全部返回 5104，测不出库存争抢）。
 */
public final class LoadTest {

    /** 把一个请求的延迟（纳秒）和结果码收集起来 */
    private record Result(long nanos, int businessCode, boolean networkError) { }

    public static void main(String[] args) throws Exception {
        String url = arg(args, "--url", null);
        String tokenFile = arg(args, "--tokens", null);
        int concurrency = Integer.parseInt(arg(args, "--concurrency", "100"));
        int warmup = Integer.parseInt(arg(args, "--warmup", "0"));

        if (url == null || tokenFile == null) {
            System.err.println("用法: java LoadTest.java --url <URL> --tokens <文件> [--concurrency 200] [--warmup 5]");
            System.exit(2);
        }

        List<String> tokens = Files.readAllLines(Path.of(tokenFile), StandardCharsets.UTF_8).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (tokens.isEmpty()) {
            System.err.println("token 文件为空：" + tokenFile);
            System.exit(2);
        }
        System.out.printf("压测目标 : %s%n", url);
        System.out.printf("并发线程 : %d%n", concurrency);
        System.out.printf("请求总数 : %d（每 token 一次）%n", tokens.size());

        // 预热：先把连接建立起来、JIT 预热，避免把 TCP 握手/类加载算进前面的请求
        if (warmup > 0) {
            System.out.printf("预热请求 : %d%n", warmup);
            HttpClient warmClient = newHttpClient(concurrency);
            for (int i = 0; i < Math.min(warmup, tokens.size()); i++) {
                send(warmClient, url, tokens.get(i));
            }
        }

        java.util.concurrent.ExecutorService httpPool = newPool(concurrency);
        HttpClient client = newHttpClient(concurrency, httpPool);
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>(tokens);
        List<Result> results = Collections.synchronizedList(new ArrayList<>(tokens.size()));
        AtomicInteger consumed = new AtomicInteger();

        long startNanos = System.nanoTime();
        Thread[] threads = new Thread[concurrency];
        for (int i = 0; i < concurrency; i++) {
            threads[i] = new Thread(() -> {
                String token;
                while ((token = queue.poll()) != null) {
                    consumed.incrementAndGet();
                    results.add(send(client, url, token));
                }
            }, "load-" + i);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        // ⚠️ Java 17 的 HttpClient 【没有】close()（那是 Java 21 才加的），
        //    所以这里只关自己的线程池。用 daemon 线程 + shutdownNow 双保险，
        //    否则 JVM 会挂在非 daemon 的 HTTP 线程上不退出。
        httpPool.shutdownNow();

        report(results, elapsedNanos);
    }

    /** daemon 线程工厂：主线程结束后 JVM 能直接退出，不会被 HTTP 线程池挂住 */
    private static java.util.concurrent.ExecutorService newPool(int concurrency) {
        return java.util.concurrent.Executors.newFixedThreadPool(
                Math.max(concurrency, 8), r -> {
                    Thread t = new Thread(r, "http-client");
                    t.setDaemon(true);
                    return t;
                });
    }

    private static HttpClient newHttpClient(int concurrency) {
        return newHttpClient(concurrency, newPool(concurrency));
    }

    private static HttpClient newHttpClient(int concurrency, java.util.concurrent.ExecutorService pool) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                // 连接池要够：并发 200 时如果只有少量连接，会变成排队而不是并发
                .connectTimeout(Duration.ofSeconds(10))
                .executor(pool)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 发一个抢购请求，返回延迟与业务码 */
    private static Result send(HttpClient client, String url, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        long t0 = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long nanos = System.nanoTime() - t0;
            // 只从响应体里抠出 "code" 字段，避免引入 JSON 库
            int code = extractCode(response.body());
            return new Result(nanos, code, false);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result(System.nanoTime() - t0, -1, true);
        }
    }

    /** 极简 JSON 取值：找 "code": 后的整数。失败返回 -2 */
    private static int extractCode(String body) {
        if (body == null) {
            return -2;
        }
        int idx = body.indexOf("\"code\"");
        if (idx < 0) {
            return -2;
        }
        int colon = body.indexOf(':', idx);
        if (colon < 0) {
            return -2;
        }
        int i = colon + 1;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t')) {
            i++;
        }
        int start = i;
        while (i < body.length() && (body.charAt(i) == '-' || Character.isDigit(body.charAt(i)))) {
            i++;
        }
        if (i == start) {
            return -2;
        }
        try {
            return Integer.parseInt(body.substring(start, i));
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    private static void report(List<Result> results, long elapsedNanos) {
        int total = results.size();
        AtomicLong networkErrors = new AtomicLong();
        AtomicLong businessFailures = new AtomicLong();
        AtomicLong successes = new AtomicLong();
        long[] latencies = new long[total];
        int i = 0;
        for (Result r : results) {
            latencies[i++] = r.nanos();
            if (r.networkError()) {
                networkErrors.incrementAndGet();
            } else if (r.businessCode() == 200) {
                successes.incrementAndGet();
            } else {
                businessFailures.incrementAndGet();
            }
        }
        Arrays.sort(latencies);

        double elapsedSec = elapsedNanos / 1_000_000_000.0;
        double qps = total / elapsedSec;

        System.out.println();
        System.out.println("================ 压测结果 ================");
        System.out.printf("总请求数      : %d%n", total);
        System.out.printf("成功(200)     : %d%n", successes.get());
        System.out.printf("业务失败(非200): %d  (抢光/已参与等，属正常竞争结果)%n", businessFailures.get());
        System.out.printf("网络失败      : %d%n", networkErrors.get());
        System.out.printf("错误率        : %.3f %%  (网络失败 / 总数)%n", total == 0 ? 0 : networkErrors.get() * 100.0 / total);
        System.out.printf("总耗时        : %.3f s%n", elapsedSec);
        System.out.printf("吞吐 QPS      : %.1f%n", qps);
        System.out.println("---------------- 响应时间 ----------------");
        System.out.printf("P50  : %8.2f ms%n", pct(latencies, 50));
        System.out.printf("P90  : %8.2f ms%n", pct(latencies, 90));
        System.out.printf("P95  : %8.2f ms%n", pct(latencies, 95));
        System.out.printf("P99  : %8.2f ms%n", pct(latencies, 99));
        System.out.printf("P999 : %8.2f ms%n", pct(latencies, 99.9));
        System.out.printf("MAX  : %8.2f ms%n", total == 0 ? 0 : latencies[total - 1] / 1_000_000.0);
        System.out.println("------------------------------------------");
        System.out.printf("MACHINE_READABLE total=%d success=%d bizfail=%d neterr=%d qps=%.1f p50=%.2f p90=%.2f p95=%.2f p99=%.2f max=%.2f%n",
                total, successes.get(), businessFailures.get(), networkErrors.get(), qps,
                pct(latencies, 50), pct(latencies, 90), pct(latencies, 95), pct(latencies, 99),
                total == 0 ? 0 : latencies[total - 1] / 1_000_000.0);
    }

    private static double pct(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(idx, sorted.length - 1));
        return sorted[idx] / 1_000_000.0;
    }

    private static String arg(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
