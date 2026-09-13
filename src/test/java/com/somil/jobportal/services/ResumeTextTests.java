package com.somil.jobportal.services;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.nio.file.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
class ResumeTextTests {
    @TempDir Path directory;
    @Test void extractsTextFromPdfAndRejectsMalformedFiles() throws Exception {
        Path file=directory.resolve("resume.pdf");
        try(var doc=new PDDocument()) {
            var page=new PDPage();doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(40,700);content.showText("Java APIs and AWS deployments");content.endText();
            }
            doc.save(file.toFile());
        }
        assertThat(new ResumeTextService().extract(file)).contains("Java APIs and AWS deployments");
        Files.writeString(file,"not a PDF");
        assertThatThrownBy(() -> new ResumeTextService().extract(file)).isInstanceOf(IOException.class);
    }
    @Test void rejectsTooManyPagesAndOversizedFiles() throws Exception {
        Path file=directory.resolve("large.pdf");
        try(var doc=new PDDocument()) {for(int i=0;i<11;i++)doc.addPage(new PDPage());doc.save(file.toFile());}
        assertThatThrownBy(() -> new ResumeTextService().extract(file)).isInstanceOf(IOException.class);
        Files.write(file,new byte[3*1024*1024+1]);
        assertThatThrownBy(() -> new ResumeTextService().extract(file)).isInstanceOf(IOException.class);
    }
}
