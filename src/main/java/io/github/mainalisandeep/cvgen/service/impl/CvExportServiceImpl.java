package io.github.mainalisandeep.cvgen.service.impl;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.mainalisandeep.cvgen.common.exception.InternalServerException;
import io.github.mainalisandeep.cvgen.common.exception.ServiceUnavailableException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.records.BinaryContent;
import io.github.mainalisandeep.cvgen.records.RenderedHtml;
import io.github.mainalisandeep.cvgen.service.CvExportService;
import io.github.mainalisandeep.cvgen.service.CvRenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTML to PDF with openhtmltopdf.
 * <p>
 * Two things matter more than the output:
 * <ul>
 *   <li><b>No external resources, ever.</b> The page is built from user data. Every stylesheet,
 *   image, font and embed request is refused before and after URI resolution, so a document that
 *   somehow smuggled in {@code <img src="file:///etc/passwd">} still reads nothing.</li>
 *   <li><b>A time budget.</b> Rendering runs on the async executor and the request waits at most
 *   {@code app.cv.export-timeout}, then gets a 503 rather than holding a web worker.</li>
 * </ul>
 * Fonts are embedded from the classpath (Noto Sans, OFL) because the PDF base-14 fonts cannot
 * draw most non-ASCII names.
 */
@Service
public class CvExportServiceImpl implements CvExportService {

    private static final Logger log = LoggerFactory.getLogger(CvExportServiceImpl.class);

    private static final String FONT_FAMILY = "Noto Sans";

    private final CvRenderService cvRenderService;
    private final CvProperties cvProperties;
    private final Executor executor;

    private final byte[] regular;
    private final byte[] bold;
    private final byte[] italic;

    public CvExportServiceImpl(CvRenderService cvRenderService,
                               CvProperties cvProperties,
                               @Qualifier("applicationTaskExecutor") Executor executor) {
        this.cvRenderService = cvRenderService;
        this.cvProperties = cvProperties;
        this.executor = executor;
        this.regular = font("fonts/NotoSans-Regular.ttf");
        this.bold = font("fonts/NotoSans-Bold.ttf");
        this.italic = font("fonts/NotoSans-Italic.ttf");
    }

    @Override
    public BinaryContent exportPdf(UUID userId, UUID cvId) {
        // Loading and templating happen here, on the request thread and inside the read-only
        // transaction; only the CPU-bound conversion moves to the executor.
        RenderedHtml page = cvRenderService.render(userId, cvId);

        CompletableFuture<byte[]> pdf;
        try {
            pdf = CompletableFuture.supplyAsync(() -> toPdf(page.html()), executor);
        } catch (RejectedExecutionException e) {
            throw new ServiceUnavailableException(ErrorConstantValue.CV_EXPORT_TIMEOUT);
        }

        try {
            byte[] bytes = pdf.get(cvProperties.getExportTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return new BinaryContent(bytes, MediaType.APPLICATION_PDF_VALUE, page.fileName());
        } catch (TimeoutException e) {
            pdf.cancel(true);
            log.warn("PDF export of CV {} exceeded {}", cvId, cvProperties.getExportTimeout());
            throw new ServiceUnavailableException(ErrorConstantValue.CV_EXPORT_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(ErrorConstantValue.CV_EXPORT_TIMEOUT);
        } catch (ExecutionException e) {
            log.error("PDF export of CV {} failed", cvId, e.getCause());
            throw new InternalServerException(ErrorConstantValue.CV_EXPORT_FAILED);
        }
    }

    private byte[] toPdf(String html) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useExternalResourceAccessControl((uri, type) -> false,
                    ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl((uri, type) -> false,
                    ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.useFont(() -> stream(regular), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> stream(bold), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.useFont(() -> stream(italic), FONT_FAMILY, 400, FontStyle.ITALIC, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    /** Read once at startup: a missing font is a packaging bug and should stop the boot. */
    private static byte[] font(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing bundled font " + path, e);
        }
    }
}
