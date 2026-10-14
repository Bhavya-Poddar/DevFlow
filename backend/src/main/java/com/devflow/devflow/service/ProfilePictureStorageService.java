package com.devflow.devflow.service;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfilePictureStorageService {
    private final Path directory;
    private final long maxBytes;

    public ProfilePictureStorageService(
            @Value("${devflow.profile-pictures.directory:${user.home}/.devflow/profile-pictures}") String directory,
            @Value("${devflow.profile-pictures.max-bytes:5242880}") long maxBytes) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public String store(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image to upload");
        }
        if (upload.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Profile images must be 5 MB or smaller");
        }

        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image");
        }
        if (bytes.length > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Profile images must be 5 MB or smaller");
        }
        String extension = validatedExtension(upload.getContentType(), bytes);
        String key = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(directory);
            Path destination = resolveKey(key);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the profile image");
        }
    }

    public Resource load(String key) {
        try {
            Path image = resolveKey(key);
            if (!Files.isRegularFile(image)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
            }
            return new FileSystemResource(image);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
        }
    }

    public String contentType(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".jpg")) return "image/jpeg";
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
    }

    public void delete(String key) {
        if (key == null) return;
        try {
            Files.deleteIfExists(resolveKey(key));
        } catch (IOException exception) {
            // The database key is already changed; an orphaned opaque file is safe and can be cleaned later.
        }
    }

    private Path resolveKey(String key) throws IOException {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(png|jpg)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
        }
        Path candidate = directory.resolve(key).normalize();
        if (!candidate.getParent().equals(directory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
        }
        return candidate;
    }

    private String validatedExtension(String contentType, byte[] bytes) {
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        boolean png = mime.equals("image/png") && bytes.length >= 8
            && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
            && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        boolean jpeg = mime.equals("image/jpeg") && bytes.length >= 3
            && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        if (png || jpeg) {
            if (isDecodableImageWithinDimensionLimit(bytes)) return png ? ".png" : ".jpg";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Profile image must be a valid PNG or JPEG image no larger than 4096 by 4096 pixels");
    }

    private boolean isDecodableImageWithinDimensionLimit(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096) return false;
                BufferedImage decoded = reader.read(0);
                return decoded != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }
}
