package com.homelab.ringue.cloud.archiver.controller;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.service.FileCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileCatalogController.class)
public class FileCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileCatalogService fileCatalogService;

    @Test
    void getByFileName_shouldReturnListOfFileCatalogItems() throws Exception {
        FileCatalogItem item1 = new FileCatalogItem("path/to/testfile1.txt", "testfile1.txt", "txt", "path/to", false, 100L, new Date(), "crc1", Instant.now());
        FileCatalogItem item2 = new FileCatalogItem("path/to/testfile2.txt", "testfile2.txt", "txt", "path/to", false, 200L, new Date(), "crc2", Instant.now());
        List<FileCatalogItem> expectedItems = Arrays.asList(item1, item2);

        when(fileCatalogService.findByFileNameContains(anyString())).thenReturn(expectedItems);

        mockMvc.perform(get("/file-catalog")
                        .param("fileName", "test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("testfile1.txt"))
                .andExpect(jsonPath("$[1].fileName").value("testfile2.txt"));
    }

    @Test
    void getSimilarByFileName_shouldReturnListOfFileCatalogItems() throws Exception {
        FileCatalogItem item1 = new FileCatalogItem("path/to/similarfile1.txt", "similarfile1.txt", "txt", "path/to", false, 150L, new Date(), "crc3", Instant.now());
        FileCatalogItem item2 = new FileCatalogItem("path/to/similarfile2.txt", "similarfile2.txt", "txt", "path/to", false, 250L, new Date(), "crc4", Instant.now());
        List<FileCatalogItem> expectedItems = Arrays.asList(item1, item2);

        when(fileCatalogService.findByFileNameSimilar(anyString())).thenReturn(expectedItems);

        mockMvc.perform(get("/file-catalog/similar")
                        .param("fileName", "similar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("similarfile1.txt"))
                .andExpect(jsonPath("$[1].fileName").value("similarfile2.txt"));
    }

    @Test
    void getArchivedItemsByDateRange_shouldReturnListOfFileCatalogItems() throws Exception {
        String startDate = "2023-01-01";
        String endDate = "2023-01-31";
        String path = "/test/path";

        FileCatalogItem item1 = new FileCatalogItem("path/to/archivedfile1.txt", "archivedfile1.txt", "txt", "path/to", false, 100L, Date.from(LocalDate.of(2023, 1, 15).atStartOfDay(ZoneId.systemDefault()).toInstant()), "crc5", Instant.now());
        FileCatalogItem item2 = new FileCatalogItem("path/to/archivedfile2.txt", "archivedfile2.txt", "txt", "path/to", false, 200L, Date.from(LocalDate.of(2023, 1, 20).atStartOfDay(ZoneId.systemDefault()).toInstant()), "crc6", Instant.now());
        List<FileCatalogItem> expectedItems = Arrays.asList(item1, item2);

        when(fileCatalogService.findByArchiveDateBetweenAndAbsolutePathStartsWith(anyString(), anyString(), any(Optional.class))).thenReturn(expectedItems);

        mockMvc.perform(get("/file-catalog/archived-range")
                        .param("startDate", startDate)
                        .param("endDate", endDate)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("archivedfile1.txt"))
                .andExpect(jsonPath("$[1].fileName").value("archivedfile2.txt"));
    }

    @Test
    void getArchivedItemsByDateRange_noPath_shouldReturnListOfFileCatalogItems() throws Exception {
        String startDate = "2023-01-01";
        String endDate = "2023-01-31";

        FileCatalogItem item1 = new FileCatalogItem("path/to/archivedfile3.txt", "archivedfile3.txt", "txt", "path/to", false, 100L, Date.from(LocalDate.of(2023, 1, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()), "crc7", Instant.now());
        FileCatalogItem item2 = new FileCatalogItem("path/to/archivedfile4.txt", "archivedfile4.txt", "txt", "path/to", false, 200L, Date.from(LocalDate.of(2023, 1, 25).atStartOfDay(ZoneId.systemDefault()).toInstant()), "crc8", Instant.now());
        List<FileCatalogItem> expectedItems = Arrays.asList(item1, item2);

        when(fileCatalogService.findByArchiveDateBetweenAndAbsolutePathStartsWith(anyString(), anyString(), any(Optional.class))).thenReturn(expectedItems);

        mockMvc.perform(get("/file-catalog/archived-range")
                        .param("startDate", startDate)
                        .param("endDate", endDate)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("archivedfile3.txt"))
                .andExpect(jsonPath("$[1].fileName").value("archivedfile4.txt"));
    }

    @Test
    void getArchivedItemsByDateRange_invalidDateFormat_shouldReturnBadRequest() throws Exception {
        String startDate = "2023/01/01"; // Invalid format
        String endDate = "2023-01-31";

        when(fileCatalogService.findByArchiveDateBetweenAndAbsolutePathStartsWith(anyString(), anyString(), any(Optional.class)))
                .thenThrow(new IllegalArgumentException("Invalid date format. Please use yyyy-MM-dd."));

        mockMvc.perform(get("/file-catalog/archived-range")
                        .param("startDate", startDate)
                        .param("endDate", endDate)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(is("Invalid date format. Please use yyyy-MM-dd.")));
    }
}
