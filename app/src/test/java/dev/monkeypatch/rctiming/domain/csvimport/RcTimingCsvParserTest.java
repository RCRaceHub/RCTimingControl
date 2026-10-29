package dev.monkeypatch.rctiming.domain.csvimport;

import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser.Kind;
import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser.ParsedCsv;
import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser.Row;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading RC-Timing driver CSVs (#39). */
class RcTimingCsvParserTest {

    @Test
    void readsAnyOrderAndSubsetOfColumns() {
        ParsedCsv parsed = RcTimingCsvParser.parse("PT No,Class,Name\r\n7123456,Stock Touring,Fred Blogs\r\n");

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("Fred Blogs");
            assertThat(row.className()).isEqualTo("Stock Touring");
            assertThat(row.primaryTransponder()).isEqualTo(7123456L);
            assertThat(row.brcaNumber()).isNull();
            assertThat(row.kind()).isEqualTo(Kind.ENTRY);
            assertThat(row.line()).isEqualTo(2);
        });
    }

    @Test
    void zeroMeansNone_andInfoColumnsAreKeptForThePreview() {
        ParsedCsv parsed = RcTimingCsvParser.parse("""
                Name,BRCA Number,Class Number,Grade,PT No,PT No 2,Junior,Paid Status,Car Make,Entry Desc
                Fred Blogs,0,2,95,3232323,0,13,1,Schumacher,update
                """);

        Row row = parsed.rows().get(0);
        assertThat(row.brcaNumber()).isNull();
        assertThat(row.classNumber()).isEqualTo(2);
        assertThat(row.secondaryTransponder()).isNull();
        assertThat(row.kind()).isEqualTo(Kind.UPDATE);
        assertThat(row.info()).isEqualTo(Map.of("Grade", "95", "Junior", "13", "Car Make", "Schumacher"));
    }

    @Test
    void headerNamesIgnoreCase_aByteOrderMarkAndBlankLines() {
        ParsedCsv parsed = RcTimingCsvParser.parse("﻿\nname , class\n\nFred  Blogs,Mod\n\n");

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).extracting(Row::name).containsExactly("Fred Blogs");
        assertThat(parsed.rows().get(0).line()).isEqualTo(4);
    }

    @Test
    void anUnknownColumnIsAWarning() {
        ParsedCsv parsed = RcTimingCsvParser.parse("Name,Class,Transponder Colour\nFred Blogs,Mod,Red\n");

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.warnings()).singleElement().asString().contains("Transponder Colour");
    }

    @Test
    void theHeaderNeedsANameAndAClass() {
        assertThat(RcTimingCsvParser.parse("").errors()).singleElement().asString().contains("empty");
        assertThat(RcTimingCsvParser.parse("Class,PT No\nMod,1\n").errors()).anyMatch(e -> e.contains("no Name column"));
        assertThat(RcTimingCsvParser.parse("Name,PT No\nFred,1\n").errors())
                .anyMatch(e -> e.contains("no Class or Class Number column"));
        assertThat(RcTimingCsvParser.parse("Name,Class,Name\nFred,Mod,Fred\n").errors())
                .anyMatch(e -> e.contains("more than once"));
    }

    @Test
    void badValuesAreReportedByLine() {
        ParsedCsv parsed = RcTimingCsvParser.parse("""
                Name,Class,Class Number,PT No,Entry Desc
                Fred Blogs,Mod,0,abc,entry
                ,Mod,0,1,entry
                Frank McDough,,0,2,entry
                Ted D'silver,Mod,0,-3,entry
                Joe-Bloggins,Mod,0,4,remove
                """);

        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.errors()).containsExactly(
                "Line 2: PT No should be a whole number, not \"abc\"",
                "Line 3 has no name",
                "Line 4 (Frank McDough) has no class",
                "Line 5: PT No should be a whole number, not \"-3\"",
                "Line 6: Entry Desc should be entry or update, not \"remove\"");
    }

    @Test
    void readsUtf8_andFallsBackToTheWindowsCodePage() {
        String text = "Name,Class\nRené Müller,Mod\n";

        assertThat(RcTimingCsvParser.decode(text.getBytes(StandardCharsets.UTF_8))).isEqualTo(text);
        assertThat(RcTimingCsvParser.decode(text.getBytes(Charset.forName("windows-1252")))).isEqualTo(text);
    }

    @Test
    void quotesAndCommasInNamesAreRejected() {
        ParsedCsv parsed = RcTimingCsvParser.parse("Name,Class\n\"Blogs, Fred\",Mod\nBlogs, Fred,Mod\nTed D'silver,Mod\n");

        assertThat(parsed.errors()).hasSize(2);
        assertThat(parsed.errors().get(0)).startsWith("Line 2 contains a double quote");
        assertThat(parsed.errors().get(1)).startsWith("Line 3 has 3 values but the header has 2");
        assertThat(parsed.rows()).extracting(Row::name).containsExactly("Ted D'silver");
    }

    @Test
    void readsPictureLinksAndCarColours_andNoneWhenTheCellsAreBlank() {
        ParsedCsv parsed = RcTimingCsvParser.parse("""
                Name,Class,Headshot URL,Car Photo URL,Car Colours
                Fred Blogs,Mod,https://cdn.example.org/fred.png,https://cdn.example.org/car.jpg,#FF0000 #00ff00;#0000ff|#ffffff
                Ada Lovelace,Mod,,,
                """);

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.warnings()).isEmpty();
        Row fred = parsed.rows().get(0);
        assertThat(fred.headshotUrl()).isEqualTo("https://cdn.example.org/fred.png");
        assertThat(fred.carPhotoUrl()).isEqualTo("https://cdn.example.org/car.jpg");
        assertThat(fred.carColours()).containsExactly("#FF0000", "#00ff00", "#0000ff", "#ffffff");
        Row ada = parsed.rows().get(1);
        assertThat(ada.headshotUrl()).isNull();
        assertThat(ada.carPhotoUrl()).isNull();
        assertThat(ada.carColours()).isNull();
    }
}
