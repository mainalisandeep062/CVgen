package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Plain text out of an uploaded CV, PDF or DOCX.
 * <p>
 * The format is decided by the bytes, never by the file name or the {@code Content-Type} the
 * browser sent - both are the client's to lie about. Every limit here is a guard against a
 * hostile file rather than a tuning knob:
 * <ul>
 *   <li>PDF: page cap, and an encrypted document is refused rather than cracked.</li>
 *   <li>DOCX: it is a zip, so the uncompressed size of {@code word/document.xml} is capped while
 *   reading (a zip bomb declares nothing honest up front), and the XML parser has DTDs and
 *   external entities switched off.</li>
 * </ul>
 * DOCX is read with the JDK's own zip and StAX rather than Apache POI: one XML part, a few
 * element names, no dependency the size of a small application.
 */
@Component
@RequiredArgsConstructor
public class CvFileTextExtractor {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final String DOCX_BODY = "word/document.xml";
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** Multiple of the upload cap that document.xml may inflate to. Real CVs sit far below it. */
    private static final int MAX_INFLATION = 20;

    private final CvProperties cvProperties;

    public String extract(byte[] bytes) {
        if (startsWith(bytes, PDF_MAGIC)) {
            return fromPdf(bytes);
        }
        if (startsWith(bytes, ZIP_MAGIC)) {
            return fromDocx(bytes);
        }
        throw new BadRequestException(ErrorConstantValue.CV_IMPORT_UNSUPPORTED_TYPE);
    }

    private String fromPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > cvProperties.getImportMaxPages()) {
                throw new BadRequestException(ErrorConstantValue.CV_IMPORT_TOO_MANY_PAGES, cvProperties.getImportMaxPages());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new BadRequestException(ErrorConstantValue.CV_IMPORT_ENCRYPTED);
        } catch (IOException e) {
            throw new BadRequestException(ErrorConstantValue.CV_IMPORT_UNREADABLE);
        }
    }

    private String fromDocx(byte[] bytes) {
        long limit = cvProperties.getImportMaxBytes() * MAX_INFLATION;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DOCX_BODY.equals(entry.getName())) {
                    return paragraphs(readAtMost(zip, limit));
                }
            }
        } catch (IOException | XMLStreamException e) {
            throw new BadRequestException(ErrorConstantValue.CV_IMPORT_UNREADABLE);
        }
        // A zip without a Word body: an .xlsx, a .jar, or a renamed archive.
        throw new BadRequestException(ErrorConstantValue.CV_IMPORT_UNSUPPORTED_TYPE);
    }

    /**
     * One line per {@code w:p}; {@code w:tab} becomes a tab and {@code w:br} a line break, which is
     * enough for the section parser to see the same lines a reader does.
     */
    private String paragraphs(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        StringBuilder text = new StringBuilder();
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            boolean inText = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    switch (reader.getLocalName()) {
                        case "t" -> inText = true;
                        case "tab" -> text.append('\t');
                        case "br", "cr" -> text.append('\n');
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    if ("t".equals(reader.getLocalName())) {
                        inText = false;
                    } else if ("p".equals(reader.getLocalName())) {
                        text.append('\n');
                    }
                } else if (inText && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                    text.append(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    private static byte[] readAtMost(InputStream stream, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = stream.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BadRequestException(ErrorConstantValue.CV_IMPORT_UNREADABLE);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
