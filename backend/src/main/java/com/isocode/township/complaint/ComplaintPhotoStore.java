package com.isocode.township.complaint;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComplaintPhotoStore {

    private final JdbcTemplate jdbc;

    public ComplaintPhotoStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Long complaintId, byte[] photo, ImageType type) {
        jdbc.update("UPDATE complaint SET photo = ?, photo_content_type = ? WHERE id = ?",
                photo, type.contentType(), complaintId);
    }

    @Transactional(readOnly = true)
    public Optional<StoredPhoto> load(Long complaintId) {
        List<StoredPhoto> photos = jdbc.query(
                "SELECT photo, photo_content_type FROM complaint WHERE id = ? AND photo IS NOT NULL",
                (resultSet, rowNum) -> new StoredPhoto(resultSet.getBytes("photo"),
                        resultSet.getString("photo_content_type")),
                complaintId);
        return photos.stream().findFirst();
    }

    public record StoredPhoto(byte[] bytes, String contentType) {
    }
}
