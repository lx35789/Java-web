package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.PhotoService;
import com.greenthumb.vo.PhotoVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 照片上传。路径与 frontend/src/api/photo.js 对应：
 *   POST   /api/v1/photos            multipart: file, photoType?, userPlantId?
 *   DELETE /api/v1/photos/{id}
 */
@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping
    public ApiResponse<PhotoVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "photoType", required = false) String photoType,
            @RequestParam(name = "userPlantId", required = false) Long userPlantId) {
        return ApiResponse.ok(photoService.save(CurrentUser.id(), file, photoType, userPlantId));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        photoService.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
