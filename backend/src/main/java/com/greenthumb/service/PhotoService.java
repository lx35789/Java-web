package com.greenthumb.service;

import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.mapper.PlantPhotoMapper;
import com.greenthumb.vo.PhotoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 照片存储。
 * <p>
 * 落盘目录：{greenthumb.upload-dir}/{yyyyMMdd}/{uuid}.{ext}
 * 对外 URL：{greenthumb.files-url-prefix}/{yyyyMMdd}/{uuid}.{ext}
 * <p>
 * 为什么用日期分目录：单目录塞几千个文件时，Windows 资源管理器和部分文件系统会明显变慢。
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlantPhotoMapper photoMapper;
    private final Path uploadRoot;
    private final String urlPrefix;

    public PhotoService(PlantPhotoMapper photoMapper,
                        @Value("${greenthumb.upload-dir:./data/uploads}") String uploadDir,
                        @Value("${greenthumb.files-url-prefix:/files}") String urlPrefix) throws IOException {
        this.photoMapper = photoMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        Files.createDirectories(this.uploadRoot);
        log.info("照片存储目录：{}，对外前缀：{}", this.uploadRoot, this.urlPrefix);
    }

    /**
     * 保存上传的照片。
     *
     * @param userId      当前用户（来自 JWT）
     * @param file        上传的文件
     * @param photoType   WHOLE / LEAF / DISEASE
     * @param userPlantId 可选，识别阶段还没关联植物
     */
    public PhotoVO save(Long userId, MultipartFile file, String photoType, Long userPlantId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.IMAGE_UNSUPPORTED, "没有收到文件");
        }
        String ext = extensionOf(file.getOriginalFilename(), file.getContentType());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.IMAGE_UNSUPPORTED);
        }

        String day = LocalDateTime.now().format(DAY);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = uploadRoot.resolve(day).normalize();
            if (!dir.startsWith(uploadRoot)) {
                throw new BizException(ErrorCode.IMAGE_UNSUPPORTED, "非法的存储路径");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            int[] size = readImageSize(target);
            String url = urlPrefix + "/" + day + "/" + filename;

            PlantPhotoEntity entity = new PlantPhotoEntity();
            entity.setUserId(userId);
            entity.setUserPlantId(userPlantId);
            entity.setUrl(url);
            // 暂无缩略图生成器，先与原因共用；接入图像处理后替换
            entity.setThumbUrl(url);
            entity.setStorageType("LOCAL");
            entity.setFileSize((int) file.getSize());
            entity.setWidth(size[0]);
            entity.setHeight(size[1]);
            entity.setPhotoType(photoType == null || photoType.isBlank() ? "WHOLE" : photoType.trim().toUpperCase(Locale.ROOT));
            entity.setTakenAt(LocalDateTime.now());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            photoMapper.insert(entity);

            return new PhotoVO(entity.getId(), url, url, size[0], size[1],
                    entity.getFileSize(), entity.getPhotoType());

        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("保存照片失败", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "照片保存失败：" + e.getMessage());
        }
    }

    /** 取照片并校验归属 */
    public PlantPhotoEntity getOwned(Long userId, Long photoId) {
        if (photoId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 photoId");
        }
        PlantPhotoEntity photo = photoMapper.selectById(photoId);
        if (photo == null || !photo.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "照片不存在");
        }
        return photo;
    }

    /** 把库里的 url 还原成磁盘路径并读出字节（识别用） */
    public byte[] readBytes(PlantPhotoEntity photo) {
        String url = photo.getUrl();
        if (url == null || !url.startsWith(urlPrefix)) {
            throw new BizException(ErrorCode.NOT_FOUND, "照片路径异常");
        }
        String relative = url.substring(urlPrefix.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path path = uploadRoot.resolve(relative).normalize();
        // 防目录穿越：解析后必须仍在 uploadRoot 之下
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw new BizException(ErrorCode.NOT_FOUND, "照片文件不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取照片失败");
        }
    }

    public String filenameOf(PlantPhotoEntity photo) {
        String url = photo.getUrl();
        int i = url == null ? -1 : url.lastIndexOf('/');
        return i < 0 ? "photo.jpg" : url.substring(i + 1);
    }

    public void delete(Long userId, Long photoId) {
        PlantPhotoEntity photo = getOwned(userId, photoId);
        photoMapper.deleteById(photo.getId());   // 逻辑删除
    }

    /* ================= 内部 ================= */

    /** 从文件名或 content-type 推断扩展名 */
    private static String extensionOf(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (ALLOWED_EXT.contains(ext)) {
                return ext;
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("png")) return "png";
            if (ct.contains("webp")) return "webp";
            if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        }
        return "";
    }

    /** 读图片宽高；读不出来也不影响上传，返回 0/0 */
    private static int[] readImageSize(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception e) {
            log.warn("读取图片尺寸失败：{}", path);
        }
        return new int[]{0, 0};
    }
}
