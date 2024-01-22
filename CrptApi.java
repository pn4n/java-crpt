import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.URI;
import java.net.URL;

import java.io.IOException;

public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final AtomicInteger requestCount;
    private final Lock lock;
    private final Condition limitReachedCondition;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestCount = new AtomicInteger(0);
        this.lock = new ReentrantLock();
        this.limitReachedCondition = lock.newCondition();

        scheduleLimitReset();
    }

    private void scheduleLimitReset() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable resetTask = () -> {
            lock.lock();
            try {
                requestCount.set(0);
                limitReachedCondition.signalAll();
            } finally {
                lock.unlock();
            }
        };
        scheduler.scheduleAtFixedRate(resetTask, 0, 1, timeUnit);
    }
    private void callAPI() {
        String apiurl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        String item = """
        {"description":
{ "participantInn": "string" }, "doc_id": "string", "doc_status": "string",
"doc_type": "LP_INTRODUCE_GOODS", "importRequest": true,
"owner_inn": "string", "participant_inn": "string", "producer_inn":
"string", "production_date": "2020-01-23", "production_type": "string",
"products": [ { "certificate_document": "string",
"certificate_document_date": "2020-01-23",
"certificate_document_number": "string", "owner_inn": "string",
"producer_inn": "string", "production_date": "2020-01-23",
"tnved_code": "string", "uit_code": "string", "uitu_code": "string" } ],
"reg_date": "2020-01-23", "reg_number": "string"}""";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiurl))
            .header("Content-Type", "text/plain; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(item))
            .build();
        HttpResponse<String> response = null;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(response.body());
    }

    public void makeAPICall() throws InterruptedException {
        lock.lock();
        try {
            if (requestCount.get() >= requestLimit) {
                throw new IllegalStateException("Request limit reached. Try again later.");
            }
            requestCount.incrementAndGet();
            callAPI();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println("Current working directory: " + workingDirectory);

        if (args.length != 2) {
            System.err.println("Usage: CrptApi <TimeUnit> <RequestLimit>");
            System.exit(1);
        }
        try {
            TimeUnit timeUnit = TimeUnit.valueOf(args[0]);
            int requestLimit = Integer.parseInt(args[1]);

            CrptApi client = new CrptApi(timeUnit, requestLimit);

            for (int i = 0; i < 20; i++) {
                System.out.println("\nAPI call #" + (i + 1));
                client.makeAPICall();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
