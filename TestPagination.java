import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestPagination {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String jsonPayload = "{\"monthKeys\": [202506]}";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:7070/api/revenue-recognition/settings/run-manual"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer <insert_token_here>") // wait, I don't have token
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
    }
}
