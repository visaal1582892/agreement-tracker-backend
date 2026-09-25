import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TriggerBatch {
    public static void main(String[] args) throws Exception {
        String json = "{\"startMonth\":4, \"startYear\":2025, \"endMonth\":4, \"endYear\":2025}";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:7070/api/admin/revenue-scheduler/manual"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic YWRtaW46YWRtaW4=") // assuming basic auth or it might fail
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }
}
