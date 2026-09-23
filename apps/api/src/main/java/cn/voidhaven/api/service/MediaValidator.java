package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;

/** Decodes the real image and re-encodes pixels, removing EXIF and other input metadata. */
@Component
//MediaValidator的作用是：验证媒体
//该验证指的是：媒体验证，均属于媒体验证栏目

public class MediaValidator {
    //MAX_BYTES的作用是：最大字节数
    static final long MAX_BYTES = 5L * 1024 * 1024;
    //MAX_PIXELS的作用是：最大像素数
    static final long MAX_PIXELS = 25_000_000;
    //MAX_EDGE的作用是：最大边缘数
    static final int MAX_EDGE = 6_000;

    //validate的作用是：验证媒体
    //该验证指的是：媒体验证，均属于媒体验证栏目

    //validate的实现：
    //1. 检查媒体是否为空，如果媒体为空则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //2. 检查媒体是否大于5MB，如果媒体大于5MB则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //3. 检查媒体是否为JPEG、PNG或WebP图片，如果媒体不是JPEG、PNG或WebP图片则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //4. 检查媒体是否超过安全限制，如果媒体超过安全限制则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    //5. 检查媒体是否可以安全解析，如果媒体不可以安全解析则抛出ApiException异常(统一403状态码)，并返回指定的错误代码和错误信息。
    public ValidatedImage validate(byte[] input) {
        if (input == null || input.length == 0 || input.length > MAX_BYTES) throw rejected("单张图片必须小于等于 5 MB。 ");
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw rejected("仅支持 JPEG、PNG 或 WebP 图片。 ");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!"JPEG".equals(format) && !"JPG".equals(format) && !"PNG".equals(format) && !"WEBP".equals(format)) throw rejected("仅支持 JPEG、PNG 或 WebP 图片。 ");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE || (long) width * height > MAX_PIXELS) throw rejected("图片尺寸超过安全限制。 ");
                BufferedImage decoded = reader.read(0);
                boolean alpha = decoded.getColorModel().hasAlpha();
                String outputFormat = alpha ? "png" : "jpeg";
                String mediaType = alpha ? "image/png" : "image/jpeg";
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, outputFormat, output)) throw rejected("图片无法安全转换。 ");
                byte[] normalized = output.toByteArray();
                if (normalized.length == 0 || normalized.length > MAX_BYTES) throw rejected("处理后的图片超过 5 MB，请压缩后重试。 ");
                return new ValidatedImage(normalized, mediaType, width, height, alpha ? "png" : "jpg");
            } finally {
                reader.dispose();
            }
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw rejected("图片文件无效或无法安全解析。 ");
        }
    }

    private ApiException rejected(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_REJECTED", message); }
    public record ValidatedImage(byte[] bytes, String mediaType, int width, int height, String extension) { }
}
