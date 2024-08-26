package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.ImageRenderer;
import uk.anbu.devnotes.util.FileUtil;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ImageController {
    private final ImageRenderer imageRenderer;

    @GetMapping("/image")
    public ResponseEntity<Resource> image(@RequestParam(name = "path", required = false) String path,
                                          @RequestParam(name = "filename") String filename) {
        try {
            var resource = imageRenderer.image(path, filename);

            if (resource.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String contentType = "image/" + FileUtil.getFileExtension(filename).toLowerCase();

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(resource.get());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    static boolean isImage(String fileExtension) {
        return "png".equals(fileExtension) || "jpg".equals(fileExtension) || "jpeg".equals(fileExtension)
                || "gif".equals(fileExtension) || "svg".equals(fileExtension) || "bmp".equals(fileExtension)
                || "webp".equals(fileExtension) || "tiff".equals(fileExtension) || "tif".equals(fileExtension)
                || "ico".equals(fileExtension);
    }

}
