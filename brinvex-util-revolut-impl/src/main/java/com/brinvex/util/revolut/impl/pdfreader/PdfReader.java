/*
 * Copyright © 2023 Brinvex (dev@brinvex.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.brinvex.util.revolut.impl.pdfreader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

public class PdfReader {

    public List<String> readPdfLines(InputStream pdfInputStream) {

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfInputStream))) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Cannot read encrypted pdf");
            }

            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);

            PDFTextStripper tStripper = new PDFTextStripper();

            String text = tStripper.getText(document);

            return Arrays.asList(text.split("\\r?\\n"));

        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Cannot read encrypted pdf", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
