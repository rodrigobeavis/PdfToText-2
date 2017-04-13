package com.borovyksv.util;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.util.SmartPdfSplitter;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PDFConverter {
    public static final int N_THREADS = 4;
    private static final Logger LOGGER = Logger.getLogger(PDFConverter.class.getName());
    private int IMAGE_DPI = 300;
    private float IMAGE_COMPRESSION = 0.7f;
    private String IMAGE_FORMAT = "jpg";
    private int DIMENSIONS_DIVIDER = IMAGE_DPI / 100;
    private boolean isScanned = true;


    private String pdfFileDirectory;
    private String pdfFileName;
    private File file;
    private String resultFolder;
    private String resultFolderPDF;
    private String resultFolderIMG;
    private Map<Integer, String> textPages = new HashMap<>();

    public PDFConverter(String fileDirectory) {
        this.pdfFileDirectory = fileDirectory;
        this.file = new File(fileDirectory);
        this.pdfFileName = this.file.getName();
        this.resultFolder = file.getParent() + File.separator + file.getName().substring(0, file.getName().length() - 4) + "_parsed" + File.separator;
        this.resultFolderPDF = resultFolder + "PDF" + File.separator;
        this.resultFolderIMG = resultFolder + "IMG" + File.separator;
//        this.resultFolderTXT = resultFolder + "TXT" + File.separator;
        new File(resultFolder).mkdir();
        new File(resultFolderPDF).mkdir();
        new File(resultFolderIMG).mkdir();
//        new File(resultFolderTXT).mkdir();
        LOGGER.log(Level.INFO, String.format("PDFmanager for %s file initialized", fileDirectory));
    }

    public String getPdfFileName() {
        return pdfFileName;
    }

    public String getResultFolderIMG() {
        return resultFolderIMG;
    }

    public String getResultFolderPDF() {
        return resultFolderPDF;
    }

    private String textFilter(String input) {
        String group = "[=;,_\\-/\\.\\\\\\\"\\'@~]";
        String pattern = String.format("(\\D)\\1{2,}?|[^\\u0000-\\u007F\\u00b0\\n\\r\\tВ]|\\s{3,}?|%1$s{3,}|%1$s+ %1$s+|( .{1,2} .{1,2} )+", group);
        //delete garbage from whole text
        input = input.replaceAll(pattern, "");

        //add document mark
        String documentMark = "Parent document: " + pdfFileName.substring(0, pdfFileName.indexOf(".pdf"));
        //Filter short lines from garbage
        StringBuilder sb = new StringBuilder();
        sb.append(documentMark).append("\n");
        for (String s : input.split("\n")) {
            Matcher m = Pattern.compile("[=;:_\\-/\\\\\"'@~!+,\\|\\.1%\\*\\$]").matcher(s);
            int matches = 0;
            while (m.find()) matches++;
            if (s.matches("^\\w{4,}[:\\.,]?$") || (matches < 3 && s.length() > 6) || s.length() > 20) {
                sb.append(s).append("\n");
            }
        }
        return sb.toString();
    }

    public String getResultFolder() {
        return resultFolder;
    }

    public void convert() {

        saveBookmarks();
        savePages();
        saveImagesAndText();
    }

    public void saveImagesAndText() {
        isScanned = isScannedPDF();
        LOGGER.log(Level.INFO, String.format("%s is %s pdf", pdfFileName, isScanned?"scanned":"text format"));
        if (isScanned) {
            saveImagesAndTextFromScannedPDF();
        } else {
            saveImages();
            saveText();
        }
    }


    public boolean isScannedPDF() {
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            pdfTextStripper.setEndPage(3);
            return pdfTextStripper.getText(doc).trim().length() == 0;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exception occur", e);
        }
        return true;
    }

    public void savePages() {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFileDirectory);
            SmartPdfSplitter splitter = new SmartPdfSplitter(reader);
            int pageNumber = 1;
            while (splitter.hasMorePages()) {
                splitter.split(new FileOutputStream(resultFolderPDF + pageNumber + ".pdf"), 200000);
                LOGGER.log(Level.INFO, String.format("%d.pdf saved", pageNumber));

                pageNumber++;
            }
        } catch (IOException | DocumentException e) {
            LOGGER.log(Level.SEVERE, "Exception occur", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }


    public void saveText() {
        try (PDDocument doc = PDDocument.load(file)) {
            Splitter splitter = new Splitter();
            java.util.List<PDDocument> split = splitter.split(doc);

            int counter = 1;

            for (PDDocument document : split) {
//                executorService.execute(() -> {

                    try {
                        int pageNumber = counter++;



                        // TODO: 13.04.2017 ADD MODEL AND YEAR
                        textPages.put(pageNumber, new PDFTextStripper().getText(document));

                        LOGGER.log(Level.INFO, String.format("%d.txt saved", pageNumber));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (document!=null) document.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
//                });
//                executorService.shutdown();
//                executorService.awaitTermination(30, TimeUnit.MINUTES);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void saveImages() {

        int numberOfPages = getNumberOfPages();

        for (int startPage = 1; startPage <= numberOfPages; startPage += 100) {

            // document must be reloaded every 100 pages to prevent memory leaks
            try (PDDocument document = PDDocument.load(file)) {
                int endPage = (startPage + 99) < numberOfPages ? startPage + 99 : numberOfPages;


                PDFRenderer pdfRenderer = new PDFRenderer(document);

                AtomicInteger counter = new AtomicInteger(startPage);

                ExecutorService executorService = Executors.newFixedThreadPool(N_THREADS);

                for (int i = startPage; i <= endPage; i++) {
                    executorService.execute(() -> {
                        try {

                            int currentPage = counter.getAndIncrement();

                            BufferedImage image = getImage(pdfRenderer, currentPage);
// TODO: 14.04.2017 uncomment
                            saveImage(currentPage, image);

                            image.flush();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                executorService.shutdown();
                executorService.awaitTermination(30, TimeUnit.MINUTES);
                System.gc();

            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Exception occur", e);
            }
        }
    }


    /*
    * This method combines two processes within to save resources (BufferedImage.class) needed for execution
    * **/
    private void saveImagesAndTextFromScannedPDF() {

        int numberOfPages = getNumberOfPages();

        for (int startPage = 1; startPage <= numberOfPages; startPage += 100) {

            // document must be reloaded every 100 pages to prevent memory leaks
            try (PDDocument document = PDDocument.load(file)) {
                int endPage = (startPage + 99) < numberOfPages ? startPage + 99 : numberOfPages;


                PDFRenderer pdfRenderer = new PDFRenderer(document);

                AtomicInteger counter = new AtomicInteger(startPage);

                ExecutorService executorService = Executors.newFixedThreadPool(N_THREADS);

                for (int i = startPage; i <= endPage; i++) {
                    executorService.execute(() -> {
                        try {

                            int currentPage = counter.getAndIncrement();

                            BufferedImage image = getImage(pdfRenderer, currentPage);

                            saveTextFromScannedPDF(currentPage, image);

                            saveImage(currentPage, image);

                            image.flush();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                executorService.shutdown();
                executorService.awaitTermination(30, TimeUnit.MINUTES);
                System.gc();

            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Exception occur", e);
            }
        }
    }



    public int getNumberOfPages() {
        int docPagesSize = 0;
        try (PDDocument document = PDDocument.load(file)) {
            docPagesSize = document.getNumberOfPages();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exception occur", e);
        }
        return docPagesSize;
    }

//    private void saveTextToFile(int pageNumber, BufferedImage bufferedImage) {
//        Tesseract tessInst = new Tesseract();
//
//        try (PrintWriter out = new PrintWriter(resultFolderTXT + pageNumber + ".txt")) {
//            String result = tessInst.doOCR(bufferedImage);
//            String filteredResult = textFilter(result);
//            out.println(filteredResult);
//            LOGGER.log(Level.INFO, String.format("%d.txt saved", pageNumber));
//
//        } catch (TesseractException | FileNotFoundException e) {
//            LOGGER.log(Level.SEVERE, "Exception occur", e);
//        }
//    }

    private void saveTextFromScannedPDF(int pageNumber, BufferedImage bufferedImage) {
        Tesseract tessInst = new Tesseract();

        try {
            String result = tessInst.doOCR(bufferedImage);
            String filteredResult = textFilter(result);

            textPages.put(pageNumber, filteredResult);

            LOGGER.log(Level.INFO, String.format("%d.txt saved", pageNumber));
        } catch (TesseractException e) {
            e.printStackTrace();
        }

    }


    private BufferedImage getImage(PDFRenderer renderer, int page) throws IOException {

        return renderer.renderImageWithDPI(page - 1, IMAGE_DPI, ImageType.RGB);

    }

    private void saveImage(int pageNumber, BufferedImage bim) throws IOException {
        File file = new File(resultFolderIMG + pageNumber + "." + IMAGE_FORMAT);
        FileOutputStream output = new FileOutputStream(file);

        Image tmp = bim.getScaledInstance(bim.getWidth() / DIMENSIONS_DIVIDER, bim.getHeight() / DIMENSIONS_DIVIDER, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(bim.getWidth() / DIMENSIONS_DIVIDER, bim.getHeight() / DIMENSIONS_DIVIDER, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);


        ImageIOUtil.writeImage(dimg, IMAGE_FORMAT, output, IMAGE_DPI, IMAGE_COMPRESSION);
        g2d.dispose();
        LOGGER.log(Level.INFO, String.format("%d%s saved", pageNumber, "." + IMAGE_FORMAT));

    }

    public Map<Integer, String> getTextPages() {
        return Collections.unmodifiableMap(textPages);
    }


    public boolean saveBookmarks() {
        try (PDDocument document = PDDocument.load(file)) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null) {
                LOGGER.log(Level.INFO, "PDF file does not have Bookmarks");
                return false;
            } else {
                try (PrintWriter bookmarkWriter = new PrintWriter(resultFolder + "Bookmarks.html")) {
                    LOGGER.log(Level.INFO, String.format("%sBookmarks.html created", resultFolder));


                    //writing to HTML
                    initializeBookmarkHTMLDocument(bookmarkWriter);

                    LOGGER.log(Level.INFO, "printing bookmarks");
                    printBookmark(outline, "", document, bookmarkWriter);

                    terminateBookmarkHTMLDocument(bookmarkWriter);


                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Exception occur", e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occur", e);
        }
        LOGGER.log(Level.INFO, "Bookmarks saved");
        return true;
    }


    private void initializeBookmarkHTMLDocument(PrintWriter bookmarkWriter) {
        bookmarkWriter.print("<HTML>\n" +
                "<HEAD>\n" +
                "<TITLE>Bookmarks</TITLE>\n" +
                "<style>\n" +
                "        table {\n" +
                "            border-collapse: collapse;\n" +
                "            margin-left:auto; \n" +
                "            margin-right:auto;" +
                "        }\n" +
                "        th, td {\n" +
                "            padding: 0.25rem;\n" +
                "            text-align: left;\n" +
                "            border: 1px solid #ccc;\n" +
                "        }\n" +
                "        tbody tr:nth-child(odd) {\n" +
                "            background: #eee;\n" +
                "        }\n" +
                "    </style>" +
                "</HEAD>\n" +
                "\n" +
                "<HR>\n<table>");
    }

    private void terminateBookmarkHTMLDocument(PrintWriter bookmarkWriter) {
        bookmarkWriter.print("</table>\n<HR>\n" +
                "</BODY>\n" +
                "</HTML>");
    }

    public void printBookmark(PDOutlineNode bookmark, String indentation, PDDocument document, PrintWriter bookmarkWriter) throws IOException {
        PDOutlineItem current = bookmark.getFirstChild();
        while (current != null) {
            PDPage currentPage = current.findDestinationPage(document);
            Integer pageNumber = document.getDocumentCatalog().getPages().indexOf(currentPage) + 1;

            bookmarkWriter.print("<tr><td>" + indentation + current.getTitle() + ": ");
            bookmarkWriter.print(pageNumber + " </td>");
            bookmarkWriter.print("<td><a href=\"PDF\\" + pageNumber + ".pdf\">PDF</a>&ensp;");
            bookmarkWriter.print("<a href=\"IMG\\" + pageNumber + "." + IMAGE_FORMAT + "\">IMG</a>&ensp;");

            printBookmark(current, indentation + "&ensp;&ensp;&ensp;", document, bookmarkWriter);
            current = current.getNextSibling();
        }
    }


}