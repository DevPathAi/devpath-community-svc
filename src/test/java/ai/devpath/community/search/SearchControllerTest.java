package ai.devpath.community.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.devpath.community.search.dto.SearchItemView;
import ai.devpath.community.search.dto.SearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean PostSearchService searchService;

  @Test
  void searchReturnsItemsTotalPageSize() throws Exception {
    SearchItemView item = new SearchItemView(1L, "QNA", "Riverpod 질문", 10L, false, 0, 0,
        "excerpt", "highlight");
    when(searchService.search(eq("Riverpod"), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new SearchResponse(List.of(item), 1, 0, 20));

    mvc.perform(get("/community/search?q=Riverpod").with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").exists())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void missingQIsRejectedAsBadRequest() throws Exception {
    mvc.perform(get("/community/search").with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void blankQIsRejectedAsBadRequest() throws Exception {
    mvc.perform(get("/community/search").param("q", "  ").with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void filterParamsArePassedThroughToService() throws Exception {
    when(searchService.search(eq("q"), eq("QNA"), eq("spring"), eq(true), eq("latest"), eq(2), eq(5)))
        .thenReturn(new SearchResponse(List.of(), 0, 2, 5));

    mvc.perform(get("/community/search")
            .param("q", "q")
            .param("board", "QNA")
            .param("tag", "spring")
            .param("solved", "true")
            .param("sort", "latest")
            .param("page", "2")
            .param("size", "5")
            .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isOk());

    verify(searchService).search("q", "QNA", "spring", true, "latest", 2, 5);
  }

  @Test
  void esFailureReturns5xxErrorEnvelope() throws Exception {
    when(searchService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("ES 다운"));

    mvc.perform(get("/community/search?q=Riverpod").with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().is5xxServerError())
        .andExpect(jsonPath("$.error.code").exists());
  }
}
