package ai.devpath.community.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 접수 엔드포인트의 상태코드·에러 envelope 매핑을 고정한다. 권한 계약은 AdminReportControllerTest 참조. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean ReportService service;

  @Test
  void createsReportAndReturns201() throws Exception {
    CommunityReport saved = new CommunityReport();
    saved.setStatus("OPEN");
    when(service.create(eq(1L), eq("POST"), eq(5L), eq("SPAM"), any())).thenReturn(saved);

    mvc.perform(post("/community/reports")
            .with(jwt().jwt(j -> j.subject("1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetType\":\"POST\",\"targetId\":5,\"category\":\"SPAM\",\"reason\":\"광고\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void duplicateReportReturns409() throws Exception {
    when(service.create(anyLong(), any(), anyLong(), any(), any()))
        .thenThrow(new ConflictException("이미 신고한 콘텐츠입니다."));

    mvc.perform(post("/community/reports")
            .with(jwt().jwt(j -> j.subject("1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetType\":\"POST\",\"targetId\":5,\"category\":\"SPAM\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void selfReportReturns400() throws Exception {
    when(service.create(anyLong(), any(), anyLong(), any(), any()))
        .thenThrow(new InvalidReportException("본인이 작성한 콘텐츠는 신고할 수 없습니다."));

    mvc.perform(post("/community/reports")
            .with(jwt().jwt(j -> j.subject("1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetType\":\"POST\",\"targetId\":5,\"category\":\"SPAM\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mvc.perform(post("/community/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetType\":\"POST\",\"targetId\":5,\"category\":\"SPAM\"}"))
        .andExpect(status().isUnauthorized());
  }
}
