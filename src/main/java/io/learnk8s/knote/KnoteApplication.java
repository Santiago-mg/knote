package io.learnk8s.knote;


import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
@EnableConfigurationProperties(KnoteProperties.class)
public class KnoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnoteApplication.class, args);
    }

}

interface NotesRepository extends MongoRepository<Note, String> {

}

@Document(collection = "notes")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
class Note {
    @Id
    private String id;
    private String description;

    @Override
    public String toString() {
        return description;
    }
}

@ConfigurationProperties(prefix = "knote")
@Setter
@Getter
class KnoteProperties {
    private Minio minio = new Minio();

    @Setter
    @Getter
    static class Minio {
        private String host = "localhost";
        private int port = 9000;
        private String bucket = "image-storage";
        private String accessKey = "mykey";
        private String secretKey = "mysecret";
        private boolean secure = false;
        private boolean reconnectEnabled = true;
    }
}

@Controller
class KNoteController {

    @Autowired
    private NotesRepository notesRepository;
    @Autowired
    private KnoteProperties properties;

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();
    private MinioClient minioClient;

    @PostConstruct
    public void init() throws InterruptedException {
        initMinio();
    }

    private void initMinio() throws InterruptedException {
        boolean success = false;
        while (!success) {
            try {
                KnoteProperties.Minio minio = properties.getMinio();
                String scheme = minio.isSecure() ? "https" : "http";
                String endpoint = scheme + "://" + minio.getHost() + ":" + minio.getPort();
                minioClient = MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(minio.getAccessKey(), minio.getSecretKey())
                        .build();

                boolean bucketExists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(minio.getBucket()).build());
                if (!bucketExists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
                }
                success = true;
                System.out.println("> MinIO initialized!");
            } catch (Exception e) {
                if (!properties.getMinio().isReconnectEnabled()) {
                    throw new IllegalStateException("Could not initialize MinIO", e);
                }
                System.out.println("> MinIO not ready, retrying in 5 seconds: " + e.getMessage());
                Thread.sleep(5000);
            }
        }
    }


    @GetMapping("/")
    public String index(Model model) {
        getAllNotes(model);
        return "index";
    }

    @PostMapping("/note")
    public String saveNotes(@RequestParam(value = "image", required = false) MultipartFile file,
                            @RequestParam String description,
                            @RequestParam(required = false) String publish,
                            @RequestParam(required = false) String upload,
                            Model model) throws Exception {

        if (publish != null && publish.equals("Publish")) {
            saveNote(description, model);
            getAllNotes(model);
            return "redirect:/";
        }
        if (upload != null && upload.equals("Upload")) {
            if (file != null && file.getOriginalFilename() != null &&
                    !file.getOriginalFilename().isEmpty()) {
                uploadImage(file, description, model);
            }
            getAllNotes(model);
            return "index";
        }
        return "index";
    }


    private void getAllNotes(Model model) {
        List<Note> notes = notesRepository.findAll();
        Collections.reverse(notes);
        model.addAttribute("notes", notes);
    }

    private void uploadImage(MultipartFile file, String description, Model model) throws Exception {
        String fileId = UUID.randomUUID().toString();
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (StringUtils.hasText(extension)) {
            fileId += "." + extension.toLowerCase();
        }

        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileId)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build());
        }

        String noteText = StringUtils.hasText(description) ? description.trim() + "\n\n" : "";
        model.addAttribute("description", noteText + "![](/img/" + fileId + ")");
    }

    private void saveNote(String description, Model model) {
        if (description != null && !description.trim().isEmpty()) {
            //We need to translate markup to HTML
            Node document = parser.parse(description.trim());
            String html = renderer.render(document);
            notesRepository.save(new Note(null, html));
            //After publish you need to clean up the textarea
            model.addAttribute("description", "");
        }
    }

    @GetMapping("/img/{name:.+}")
    public ResponseEntity<byte[]> getImageByName(@PathVariable String name) throws Exception {
        try (InputStream imageStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getMinio().getBucket())
                        .object(name)
                        .build())) {
            String contentType = URLConnection.guessContentTypeFromName(name);
            MediaType mediaType = StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(IOUtils.toByteArray(imageStream));
        }
    }

}
