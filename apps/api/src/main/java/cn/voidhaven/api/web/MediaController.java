package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
public class MediaController {
    private final MediaService media;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final cn.voidhaven.api.service.MediaReadService reads;

    public MediaController(MediaService media, IdempotencyService idempotency, AccountRepository accounts, cn.voidhaven.api.service.MediaReadService reads) { this.media = media; this.idempotency = idempotency; this.accounts = accounts; this.reads = reads; }

    @GetMapping("/api/v1/media/{id}/preview")
    //preview的作用是：预览媒体
    //该预览指的是：媒体预览，均属于媒体预览栏目

    //preview的实现：
    //1. 检查操作人是否活跃，如果操作人不活跃则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //2. 根据id查找媒体，如果找不到则抛出ApiException异常(统一404状态码)，并返回指定的错误代码和错误信息。
    //3. 返回媒体预览对象，如果媒体处于待审核状态，则返回媒体预览对象。
    //4. 如果媒体处于待审核状态，则返回媒体预览对象。
    ResponseEntity<byte[]> preview(@PathVariable String id, Authentication auth) {
        //accountId方法用于获取操作人账号id，如果操作人不活跃则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        String accountId = accountId(auth);
        //isAdministrator方法用于检查操作人是否为管理员，如果操作人不为管理员则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        boolean administrator = isAdministrator(auth);
        //loadManaged方法用于加载媒体，如果媒体处于待审核状态，则加载媒体。
        var stored = reads.loadManaged(id, accountId, administrator);
        //返回媒体预览对象，如果媒体处于待审核状态，则返回媒体预览对象。
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mediaType())).cacheControl(CacheControl.noStore()).body(stored.bytes());
    }

    @PostMapping(path = "/api/v1/media/uploads", consumes = "multipart/form-data")
    //upload的作用是：上传媒体
    //该上传指的是：媒体上传，均属于媒体上传栏目

    //upload的实现：
    //1. 检查操作人是否活跃，如果操作人不活跃则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //2. 根据id查找媒体，如果找不到则抛出ApiException异常(统一404状态码)，并返回指定的错误代码和错误信息。
    //3. 返回媒体上传对象，如果媒体处于待审核状态，则返回媒体上传对象。
    //4. 如果媒体处于待审核状态，则返回媒体上传对象。
    ApiResponse<MediaService.Uploaded> upload(@RequestHeader("Idempotency-Key") String key, @RequestParam @NotBlank String contentItemId, @RequestParam @NotBlank @Size(max = 240) String altText, @RequestParam("file") MultipartFile file, Authentication auth, HttpServletRequest request) {
        //accountId方法用于获取操作人账号id，如果操作人不活跃则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        String accountId = accountId(auth);
        //isAdministrator方法用于检查操作人是否为管理员，如果操作人不为管理员则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        boolean administrator = isAdministrator(auth);
        //role方法用于获取操作人角色，如果操作人不为管理员则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        String role = auth.getAuthorities().stream().anyMatch(value -> "ROLE_SUPER_ADMIN".equals(value.getAuthority())) ? "SUPER_ADMIN" : administrator ? "ADMIN" : "PARTNER";
        //bytes方法用于获取媒体字节，如果媒体字节为空则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", "图片无法读取，请重新选择文件。 "); }
        //idempotency方法用于执行幂等性操作，如果幂等性操作失败则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        MediaService.Uploaded uploaded = idempotency.execute(key, new UploadIntent(contentItemId, altText, file.getOriginalFilename(), sha256(bytes)), MediaService.Uploaded.class, () -> media.upload(contentItemId, altText, bytes, accountId, role, administrator, requestId(request)));
        //返回媒体上传对象，如果媒体上传对象为空则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
        return new ApiResponse<>(uploaded, requestId(request));
    }

    @PostMapping(path = "/api/v1/media/action-uploads", consumes = "multipart/form-data")
    ApiResponse<MediaService.Uploaded> uploadAction(@RequestHeader("Idempotency-Key") String key,
            @RequestParam @NotBlank String contentItemId, @RequestParam @NotBlank @Size(max = 240) String altText,
            @RequestParam("file") MultipartFile file, Authentication auth, HttpServletRequest request) {
        String accountId = accountId(auth);
        boolean administrator = isAdministrator(auth);
        String role = auth.getAuthorities().stream().anyMatch(value -> "ROLE_SUPER_ADMIN".equals(value.getAuthority())) ? "SUPER_ADMIN" : administrator ? "ADMIN" : "PARTNER";
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", "图片无法读取，请重新选择文件。"); }
        MediaService.Uploaded uploaded = idempotency.execute(key,
                new UploadIntent(contentItemId, altText, file.getOriginalFilename(), sha256(bytes)),
                MediaService.Uploaded.class,
                () -> media.uploadAction(contentItemId, altText, bytes, accountId, role, administrator, requestId(request)));
        return new ApiResponse<>(uploaded, requestId(request));
    }

    record UploadIntent(String contentItemId, String altText, String originalFilename, String contentHash) { }
    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).filter(account -> account.isActive()).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。")).getId(); }
    private boolean isAdministrator(Authentication auth) { return auth.getAuthorities().stream().anyMatch(value -> "ROLE_ADMIN".equals(value.getAuthority()) || "ROLE_SUPER_ADMIN".equals(value.getAuthority())); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception error) { throw new IllegalStateException("Cannot hash media request", error); } }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
